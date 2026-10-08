using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;

namespace Cave;

/// <summary>Finds (and downloads on first run) yt-dlp and ffmpeg.</summary>
public static class ToolManager
{
    public static string Dir => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Cave", "tools");

    public static string YtDlp => Path.Combine(Dir, "yt-dlp.exe");
    public static string Ffmpeg => Path.Combine(Dir, "ffmpeg.exe");

    const string YtDlpUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    const string FfmpegUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";

    static readonly HttpClient Http = CreateClient();

    static HttpClient CreateClient()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromMinutes(20) };
        c.DefaultRequestHeaders.UserAgent.ParseAdd(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        return c;
    }

    public static HttpClient Client => Http;

    public static async Task EnsureAsync(Action<string> log, CancellationToken ct)
    {
        Directory.CreateDirectory(Dir);

        if (!File.Exists(YtDlp))
        {
            log("First run: downloading yt-dlp...");
            await DownloadFileAsync(YtDlpUrl, YtDlp, ct);
        }

        if (!File.Exists(Ffmpeg))
        {
            log("First run: downloading ffmpeg (about 80 MB)...");
            var zipPath = Path.Combine(Dir, "ffmpeg.zip");
            await DownloadFileAsync(FfmpegUrl, zipPath, ct);
            using (var zip = ZipFile.OpenRead(zipPath))
            {
                var entry = zip.Entries.FirstOrDefault(e =>
                    e.FullName.EndsWith("bin/ffmpeg.exe", StringComparison.OrdinalIgnoreCase))
                    ?? throw new InvalidOperationException("ffmpeg.exe not found in the downloaded archive.");
                entry.ExtractToFile(Ffmpeg, true);
            }
            File.Delete(zipPath);
        }
    }

    static async Task DownloadFileAsync(string url, string dest, CancellationToken ct)
    {
        var tmp = dest + ".part";
        using (var resp = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct))
        {
            resp.EnsureSuccessStatusCode();
            await using var src = await resp.Content.ReadAsStreamAsync(ct);
            await using var dst = File.Create(tmp);
            await src.CopyToAsync(dst, ct);
        }
        File.Move(tmp, dest, true);
    }

    public static async Task<string> UpdateYtDlpAsync(CancellationToken ct)
    {
        var psi = new ProcessStartInfo(YtDlp)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        psi.ArgumentList.Add("-U");
        using var p = Process.Start(psi)!;
        var output = await p.StandardOutput.ReadToEndAsync(ct);
        var error = await p.StandardError.ReadToEndAsync(ct);
        await p.WaitForExitAsync(ct);
        return (output + error).Trim();
    }
}
