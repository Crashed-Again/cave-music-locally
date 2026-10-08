using System.Diagnostics;
using System.IO;
using System.Text;

namespace Cave;

/// <summary>Downloads one track as MP3 using yt-dlp + ffmpeg.</summary>
public static class Downloader
{
    public static string SafeName(string s)
    {
        foreach (var c in Path.GetInvalidFileNameChars()) s = s.Replace(c, '_');
        s = s.Trim().TrimEnd('.');
        if (s.Length > 150) s = s[..150];
        return s.Length == 0 ? "track" : s;
    }

    public static async Task<DownloadResult> DownloadAsync(
        Track track, string album, string folder, int bitrate, bool embedCover, bool skipExisting,
        Action<string> log, CancellationToken ct)
    {
        var baseName = SafeName(track.DisplayName);
        var target = Path.Combine(folder, baseName);

        if (skipExisting && File.Exists(target + ".mp3"))
            return DownloadResult.Skipped;

        var args = new List<string>
        {
            "-x", "--audio-format", "mp3", "--audio-quality", $"{bitrate}K",
            "--no-playlist", "--no-warnings", "--newline", "--windows-filenames",
            "--ffmpeg-location", ToolManager.Dir,
            "-o", target + ".%(ext)s",
        };

        if (track.YouTubeId != null)
        {
            // Direct YouTube Music track: keep YouTube's own metadata.
            args.Add("--embed-metadata");
            args.Add($"https://www.youtube.com/watch?v={track.YouTubeId}");
        }
        else
        {
            // Spotify / Apple Music: search YouTube and tag the file with the real title/artist.
            string Q(string s) => s.Replace("\"", "").Replace("\\", "");
            var tags = $"-metadata \"title={Q(track.Title)}\" -metadata \"artist={Q(track.Artist)}\" -metadata \"album={Q(album)}\"";
            args.Add("--postprocessor-args");
            args.Add("ExtractAudio:" + tags);
            args.Add($"ytsearch1:{track.Artist} - {track.Title} audio");
        }

        if (embedCover)
        {
            args.Add("--embed-thumbnail");
            args.Add("--convert-thumbnails");
            args.Add("jpg");
        }

        var psi = new ProcessStartInfo(ToolManager.YtDlp)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };
        foreach (var a in args) psi.ArgumentList.Add(a);

        using var p = Process.Start(psi)!;
        using var reg = ct.Register(() => { try { p.Kill(true); } catch { } });
        var outTask = p.StandardOutput.ReadToEndAsync();
        var errTask = p.StandardError.ReadToEndAsync();
        await p.WaitForExitAsync();
        ct.ThrowIfCancellationRequested();

        var err = (await errTask).Trim();
        await outTask;

        if (p.ExitCode == 0 && File.Exists(target + ".mp3"))
            return DownloadResult.Done;

        log($"FAILED: {track.DisplayName}" + (err.Length > 0 ? $"\n{err}" : ""));
        return DownloadResult.Failed;
    }
}
