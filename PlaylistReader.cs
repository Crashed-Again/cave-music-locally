using System.Diagnostics;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Cave;

/// <summary>Turns a playlist URL into a list of tracks.</summary>
public static class PlaylistReader
{
    public static Source? Detect(string url)
    {
        if (!Uri.TryCreate(url.Trim(), UriKind.Absolute, out var uri)) return null;
        var host = uri.Host.ToLowerInvariant();
        if (host.Contains("spotify.com")) return Source.Spotify;
        if (host.Contains("music.apple.com")) return Source.AppleMusic;
        if (host.Contains("youtube.com") || host.Contains("youtu.be")) return Source.YouTubeMusic;
        return null;
    }

    public static Task<PlaylistInfo> ReadAsync(Source source, string url, CancellationToken ct) => source switch
    {
        Source.Spotify => ReadSpotifyAsync(url, ct),
        Source.AppleMusic => ReadAppleAsync(url, ct),
        _ => ReadYouTubeAsync(url, ct),
    };

    // ---------------------------------------------------------------- Spotify
    // Uses the public embed page, which needs no login or API key.
    // Note: the embed page only exposes the first ~100 tracks of a playlist.
    static async Task<PlaylistInfo> ReadSpotifyAsync(string url, CancellationToken ct)
    {
        var m = Regex.Match(url, @"open\.spotify\.com/(?:intl-[a-z\-]+/)?(playlist|album)/([A-Za-z0-9]+)");
        if (!m.Success) throw new InvalidOperationException("That doesn't look like a Spotify playlist or album link.");

        var html = await ToolManager.Client.GetStringAsync($"https://open.spotify.com/embed/{m.Groups[1].Value}/{m.Groups[2].Value}", ct);
        var json = Regex.Match(html, "<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOptions.Singleline);
        if (!json.Success) throw new InvalidOperationException("Couldn't read the Spotify page. Is the playlist public?");

        using var doc = JsonDocument.Parse(json.Groups[1].Value);
        var entity = Find(doc.RootElement, "entity") ?? doc.RootElement;
        var name = Str(entity, "name") ?? Str(entity, "title") ?? "Spotify playlist";

        var list = Find(entity, "trackList");
        var tracks = new List<Track>();
        if (list is { ValueKind: JsonValueKind.Array } arr)
        {
            foreach (var t in arr.EnumerateArray())
            {
                var title = Str(t, "title");
                var artist = Str(t, "subtitle") ?? "";
                if (!string.IsNullOrWhiteSpace(title))
                    tracks.Add(new Track(Clean(title), Clean(artist)));
            }
        }
        if (tracks.Count == 0) throw new InvalidOperationException("No tracks found. The playlist may be private or empty.");
        return new PlaylistInfo(Clean(name), tracks);
    }

    // ------------------------------------------------------------ Apple Music
    // Reads the JSON blob Apple embeds in the public playlist page.
    static async Task<PlaylistInfo> ReadAppleAsync(string url, CancellationToken ct)
    {
        var html = await ToolManager.Client.GetStringAsync(url, ct);

        var name = "Apple Music playlist";
        var og = Regex.Match(html, "<meta property=\"og:title\" content=\"([^\"]+)\"");
        if (og.Success) name = Clean(og.Groups[1].Value);

        var blob = Regex.Match(html, "<script[^>]*id=\"serialized-server-data\"[^>]*>(.*?)</script>", RegexOptions.Singleline);
        if (!blob.Success) throw new InvalidOperationException("Couldn't read the Apple Music page. Is the playlist public?");

        using var doc = JsonDocument.Parse(blob.Groups[1].Value);
        var found = new List<Track>();
        var seen = new HashSet<string>();
        Collect(doc.RootElement, found, seen);

        if (found.Count == 0) throw new InvalidOperationException("No tracks found on that Apple Music page.");
        return new PlaylistInfo(name, found);
    }

    static void Collect(JsonElement el, List<Track> into, HashSet<string> seen)
    {
        switch (el.ValueKind)
        {
            case JsonValueKind.Object:
                var title = Str(el, "title");
                var artist = Str(el, "artistName");
                if (!string.IsNullOrWhiteSpace(title) && !string.IsNullOrWhiteSpace(artist))
                {
                    if (seen.Add($"{artist}|{title}"))
                        into.Add(new Track(Clean(title!), Clean(artist!)));
                }
                foreach (var p in el.EnumerateObject()) Collect(p.Value, into, seen);
                break;
            case JsonValueKind.Array:
                foreach (var i in el.EnumerateArray()) Collect(i, into, seen);
                break;
        }
    }

    // ---------------------------------------------------------- YouTube Music
    static async Task<PlaylistInfo> ReadYouTubeAsync(string url, CancellationToken ct)
    {
        var m = Regex.Match(url, @"[?&]list=([\w\-]+)");
        if (!m.Success) throw new InvalidOperationException("That YouTube link has no playlist in it (no list= part).");

        var psi = new ProcessStartInfo(ToolManager.YtDlp)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };
        foreach (var a in new[]
        {
            "--flat-playlist", "--ignore-errors", "--no-warnings",
            "--print", "%(id)s\t%(title)s\t%(playlist_title)s",
            $"https://www.youtube.com/playlist?list={m.Groups[1].Value}",
        }) psi.ArgumentList.Add(a);

        using var p = Process.Start(psi)!;
        using var reg = ct.Register(() => { try { p.Kill(true); } catch { } });
        var outTask = p.StandardOutput.ReadToEndAsync();
        var errTask = p.StandardError.ReadToEndAsync();
        await p.WaitForExitAsync();
        ct.ThrowIfCancellationRequested();
        var output = await outTask;
        var error = await errTask;

        var tracks = new List<Track>();
        var name = "YouTube playlist";
        foreach (var line in output.Split('\n', StringSplitOptions.RemoveEmptyEntries))
        {
            var parts = line.TrimEnd('\r').Split('\t');
            if (parts.Length < 2) continue;
            if (parts[1].StartsWith("[Private") || parts[1].StartsWith("[Deleted")) continue;
            tracks.Add(new Track(parts[1], "", parts[0]));
            if (parts.Length >= 3 && parts[2] != "NA" && parts[2].Length > 0) name = parts[2];
        }

        if (tracks.Count == 0)
            throw new InvalidOperationException("No tracks found. Is the playlist public? " + error.Trim());
        return new PlaylistInfo(name, tracks);
    }

    // ---------------------------------------------------------------- helpers
    static string Clean(string s) => WebUtility.HtmlDecode(s).Replace('\u00A0', ' ').Trim();

    static string? Str(JsonElement el, string prop) =>
        el.ValueKind == JsonValueKind.Object && el.TryGetProperty(prop, out var v) && v.ValueKind == JsonValueKind.String
            ? v.GetString() : null;

    static JsonElement? Find(JsonElement el, string name)
    {
        if (el.ValueKind == JsonValueKind.Object)
        {
            foreach (var p in el.EnumerateObject())
            {
                if (p.Name == name) return p.Value;
                var r = Find(p.Value, name);
                if (r != null) return r;
            }
        }
        else if (el.ValueKind == JsonValueKind.Array)
        {
            foreach (var i in el.EnumerateArray())
            {
                var r = Find(i, name);
                if (r != null) return r;
            }
        }
        return null;
    }
}
