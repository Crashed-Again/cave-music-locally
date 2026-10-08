using System.Collections.ObjectModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using Microsoft.Win32;

namespace Cave;

public partial class MainWindow : Window
{
    [DllImport("dwmapi.dll")]
    static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

    readonly ObservableCollection<TrackItem> _tracks = new();
    readonly AppSettings _settings = AppSettings.Load();
    CancellationTokenSource? _cts;

    public MainWindow()
    {
        InitializeComponent();
        TrackList.ItemsSource = _tracks;
        ApplySettingsToUi();
        UrlBox.Text = _settings.LastUrl;   // remember the last playlist
        RefreshRecent();
    }

    // Dark title bar, to match the rest of the window.
    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        var hwnd = new WindowInteropHelper(this).Handle;
        int on = 1;
        DwmSetWindowAttribute(hwnd, 20, ref on, sizeof(int));
    }

    // ------------------------------------------------------------ settings
    void ApplySettingsToUi()
    {
        OutputText.Text = _settings.OutputFolder;
        ToggleSubfolder.IsChecked = _settings.Subfolder;
        ToggleCover.IsChecked = _settings.EmbedCover;
        ToggleSkip.IsChecked = _settings.SkipExisting;

        (_settings.Bitrate switch { 128 => Br128, 192 => Br192, 256 => Br256, _ => Br320 }).IsChecked = true;
        (_settings.Parallel switch { 1 => Par1, 3 => Par3, 4 => Par4, _ => Par2 }).IsChecked = true;
    }

    void ReadSettingsFromUi()
    {
        _settings.Subfolder = ToggleSubfolder.IsChecked == true;
        _settings.EmbedCover = ToggleCover.IsChecked == true;
        _settings.SkipExisting = ToggleSkip.IsChecked == true;
        _settings.Bitrate = Br128.IsChecked == true ? 128 : Br192.IsChecked == true ? 192 : Br256.IsChecked == true ? 256 : 320;
        _settings.Parallel = Par1.IsChecked == true ? 1 : Par3.IsChecked == true ? 3 : Par4.IsChecked == true ? 4 : 2;
        _settings.Save();
    }

    // ---------------------------------------------------------- navigation
    void Nav_Checked(object sender, RoutedEventArgs e)
    {
        if (PageLog == null) return; // still loading
        var tag = (string)((RadioButton)sender).Tag;
        PageClone.Visibility = tag == "Clone" ? Visibility.Visible : Visibility.Collapsed;
        PageOptions.Visibility = tag == "Options" ? Visibility.Visible : Visibility.Collapsed;
        PageLog.Visibility = tag == "Log" ? Visibility.Visible : Visibility.Collapsed;
    }

    // ------------------------------------------------------------- inputs
    void UrlBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        SourceText.Text = PlaylistReader.Detect(UrlBox.Text) switch
        {
            Source.Spotify => "Spotify",
            Source.AppleMusic => "Apple Music",
            Source.YouTubeMusic => "YouTube Music",
            _ => "",
        };
    }

    void Choose_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFolderDialog { Title = "Choose where the MP3s go", InitialDirectory = _settings.OutputFolder };
        if (dlg.ShowDialog(this) == true)
        {
            _settings.OutputFolder = dlg.FolderName;
            OutputText.Text = dlg.FolderName;
            _settings.Save();
        }
    }

    async void Update_Click(object sender, RoutedEventArgs e)
    {
        UpdateBtn.IsEnabled = false;
        try
        {
            await ToolManager.EnsureAsync(Log, CancellationToken.None);
            Log("Updating yt-dlp...");
            Log(await ToolManager.UpdateYtDlpAsync(CancellationToken.None));
        }
        catch (Exception ex) { Log("Update failed: " + ex.Message); }
        finally { UpdateBtn.IsEnabled = true; }
    }

    // -------------------------------------------------------------- clone
    void Cancel_Click(object sender, RoutedEventArgs e)
    {
        _cts?.Cancel();
        StatusText.Text = "Cancelling...";
    }

    async void Clone_Click(object sender, RoutedEventArgs e) => await RunCloneAsync(false, null);

    // ---- recent playlists
    void RefreshRecent()
    {
        RecentList.ItemsSource = null;
        RecentList.ItemsSource = _settings.Recent.ToList();
        RecentCard.Visibility = _settings.Recent.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    void RecentUse_Click(object sender, RoutedEventArgs e)
    {
        if (((Button)sender).Tag is RecentPlaylist r) UrlBox.Text = r.Url;
    }

    async void RecentSync_Click(object sender, RoutedEventArgs e)
    {
        if (((Button)sender).Tag is not RecentPlaylist r || !CloneBtn.IsEnabled) return;
        UrlBox.Text = r.Url;
        await RunCloneAsync(true, r.Folder);
    }

    bool _checking;

    // Re-reads a remembered playlist (no downloads) and reports what changed since the last sync.
    async void RecentCheck_Click(object sender, RoutedEventArgs e)
    {
        if (((Button)sender).Tag is not RecentPlaylist r || _checking || !CloneBtn.IsEnabled) return;
        var src = PlaylistReader.Detect(r.Url);
        if (src == null) return;

        _checking = true;
        StatusText.Text = $"Checking \"{r.Name}\"...";
        try
        {
            await ToolManager.EnsureAsync(Log, CancellationToken.None);
            var pl = await PlaylistReader.ReadAsync(src.Value, r.Url, CancellationToken.None);

            if (r.Keys.Count == 0)
            {
                // synced with an older version, so only the track count is known
                r.ChangeNote = pl.Tracks.Count == r.TrackCount
                    ? "Same number of tracks as last sync"
                    : $"Track count changed: {r.TrackCount} -> {pl.Tracks.Count}";
            }
            else
            {
                var now = pl.Tracks.Select(t => t.Key).ToHashSet();
                var before = r.Keys.ToHashSet();
                int added = now.Count(k => !before.Contains(k));
                int removed = before.Count(k => !now.Contains(k));
                r.ChangeNote = added == 0 && removed == 0
                    ? "No changes since last sync"
                    : $"{added} new, {removed} removed since last sync";
            }
            StatusText.Text = $"\"{r.Name}\": {r.ChangeNote}";
        }
        catch (Exception ex)
        {
            r.ChangeNote = "Couldn't check: " + ex.Message;
            StatusText.Text = "Check failed";
        }
        finally { _checking = false; }
        RefreshRecent();
    }

    void Remember(Source source, string url, PlaylistInfo playlist, string folder)
    {
        var existing = _settings.Recent.FirstOrDefault(x => x.Url == url);
        if (existing != null) _settings.Recent.Remove(existing);
        _settings.Recent.Insert(0, new RecentPlaylist
        {
            Url = url,
            Name = playlist.Name,
            Folder = folder,
            TrackCount = playlist.Tracks.Count,
            Keys = playlist.Tracks.Select(t => t.Key).ToList(),
            LastSynced = DateTime.Now,
            Service = source switch { Source.Spotify => "Spotify", Source.AppleMusic => "Apple Music", _ => "YouTube Music" },
        });
        if (_settings.Recent.Count > 10) _settings.Recent.RemoveRange(10, _settings.Recent.Count - 10);
        _settings.LastUrl = url;
        _settings.Save();
        RefreshRecent();
    }

    async Task RunCloneAsync(bool forceSkip, string? folderOverride)
    {
        var url = UrlBox.Text.Trim();
        var source = PlaylistReader.Detect(url);
        if (source == null)
        {
            MessageBox.Show(this, "Paste a YouTube Music, Spotify or Apple Music playlist link first.",
                "Cave", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        ReadSettingsFromUi();
        _settings.LastUrl = url;
        _settings.Save();
        bool skipExisting = forceSkip || _settings.SkipExisting;
        SetBusy(true);
        _tracks.Clear();
        Prog.Value = 0;
        _cts = new CancellationTokenSource();
        var ct = _cts.Token;

        try
        {
            await ToolManager.EnsureAsync(Log, ct);

            StatusText.Text = "Reading playlist...";
            Log($"Reading {source} playlist: {url}");
            var playlist = await PlaylistReader.ReadAsync(source.Value, url, ct);
            Log($"Found \"{playlist.Name}\" with {playlist.Tracks.Count} tracks.");

            var folder = _settings.OutputFolder;
            if (_settings.Subfolder) folder = Path.Combine(folder, Downloader.SafeName(playlist.Name));
            if (!string.IsNullOrEmpty(folderOverride)) folder = folderOverride;   // sync back into the same folder
            Directory.CreateDirectory(folder);
            Remember(source.Value, url, playlist, folder);

            foreach (var t in playlist.Tracks) _tracks.Add(new TrackItem(t));

            _lastPlaylistName = playlist.Name;
            _lastFolder = folder;
            await DownloadAllAsync(_tracks.ToList(), skipExisting, ct);
            Log($"Finished. Saved to {folder}");
        }
        catch (OperationCanceledException)
        {
            StatusText.Text = "Cancelled";
            Log("Cancelled.");
            foreach (var t in _tracks) if (t.Status is "Waiting" or "Downloading...") t.SetWaiting();
        }
        catch (Exception ex)
        {
            StatusText.Text = "Error";
            Log("Error: " + ex.Message);
            MessageBox.Show(this, ex.Message, "Cave", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally
        {
            SetBusy(false);
            _cts?.Dispose();
            _cts = null;
        }
    }

    string _lastPlaylistName = "";
    string _lastFolder = "";

    /// <summary>Downloads the given tracks (respecting the parallel setting) and updates the UI.</summary>
    async Task DownloadAllAsync(List<TrackItem> items, bool skipExisting, CancellationToken ct)
    {
        int total = items.Count, finished = 0, ok = 0, skipped = 0, failed = 0;
        Prog.Value = 0;
        using var gate = new SemaphoreSlim(_settings.Parallel);

        var jobs = items.Select(async item =>
        {
            await gate.WaitAsync(ct);
            try
            {
                ct.ThrowIfCancellationRequested();
                Dispatcher.Invoke(item.SetWorking);

                var result = await Downloader.DownloadAsync(item.Track, _lastPlaylistName, _lastFolder,
                    _settings.Bitrate, _settings.EmbedCover, skipExisting, Log, ct);

                Dispatcher.Invoke(() =>
                {
                    switch (result)
                    {
                        case DownloadResult.Done: item.SetDone(); ok++; break;
                        case DownloadResult.Skipped: item.SetSkipped(); skipped++; break;
                        default: item.SetFailed(); failed++; break;
                    }
                    finished++;
                    Prog.Value = (double)finished / total;
                    StatusText.Text = $"{finished} / {total}  -  {ok} done, {skipped} skipped, {failed} failed";
                });
            }
            finally { gate.Release(); }
        }).ToList();

        await Task.WhenAll(jobs);
        StatusText.Text = $"Finished: {ok} done, {skipped} skipped, {failed} failed";
    }

    async void Retry_Click(object sender, RoutedEventArgs e)
    {
        var failedItems = _tracks.Where(t => t.HasFailed).ToList();
        if (failedItems.Count == 0) return;

        ReadSettingsFromUi();
        SetBusy(true);
        _cts = new CancellationTokenSource();
        var ct = _cts.Token;
        try
        {
            Log($"Retrying {failedItems.Count} failed tracks...");
            foreach (var t in failedItems) t.SetWaiting();
            await DownloadAllAsync(failedItems, true, ct);
        }
        catch (OperationCanceledException)
        {
            StatusText.Text = "Cancelled";
            Log("Cancelled.");
            foreach (var t in failedItems) if (t.Status is "Waiting" or "Downloading...") t.SetFailed();
        }
        catch (Exception ex)
        {
            StatusText.Text = "Error";
            Log("Error: " + ex.Message);
        }
        finally
        {
            SetBusy(false);
            _cts?.Dispose();
            _cts = null;
        }
    }

    void SetBusy(bool busy)
    {
        RetryBtn.IsEnabled = !busy && _tracks.Any(t => t.HasFailed);
        CloneBtn.IsEnabled = !busy;
        CancelBtn.IsEnabled = busy;
        UrlBox.IsEnabled = !busy;
    }

    void Log(string message)
    {
        void Write()
        {
            LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}");
            LogBox.ScrollToEnd();
        }
        if (Dispatcher.CheckAccess()) Write(); else Dispatcher.Invoke(Write);
    }
}
