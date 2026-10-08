using System.ComponentModel;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Windows.Media;

namespace Cave;

public enum Source { YouTubeMusic, Spotify, AppleMusic }

/// <summary>One song. Either a direct YouTube id (YouTube Music) or a title/artist to search for.</summary>
public record Track(string Title, string Artist, string? YouTubeId = null)
{
    public string DisplayName => string.IsNullOrWhiteSpace(Artist) ? Title : $"{Artist} - {Title}";
    public string Key => YouTubeId ?? $"{Artist}|{Title}";
}

public record PlaylistInfo(string Name, List<Track> Tracks);

public enum DownloadResult { Done, Skipped, Failed }

public class TrackItem : INotifyPropertyChanged
{
    static readonly Brush Dim = Freeze(new SolidColorBrush(Color.FromRgb(0x8E, 0x8E, 0x8E)));
    static readonly Brush Blue = Freeze(new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6)));
    static readonly Brush Green = Freeze(new SolidColorBrush(Color.FromRgb(0x4A, 0xDE, 0x80)));
    static readonly Brush Red = Freeze(new SolidColorBrush(Color.FromRgb(0xF8, 0x71, 0x71)));

    static Brush Freeze(SolidColorBrush b) { b.Freeze(); return b; }

    string _status = "Waiting";
    Brush _brush = Dim;

    public TrackItem(Track track) => Track = track;

    public Track Track { get; }
    public string Name => Track.DisplayName;
    public bool HasFailed => _status == "Failed";

    public string Status { get => _status; private set { _status = value; Raise(nameof(Status)); } }
    public Brush StatusBrush { get => _brush; private set { _brush = value; Raise(nameof(StatusBrush)); } }

    public void SetWaiting() { Status = "Waiting"; StatusBrush = Dim; }
    public void SetWorking() { Status = "Downloading..."; StatusBrush = Blue; }
    public void SetDone() { Status = "Done"; StatusBrush = Green; }
    public void SetSkipped() { Status = "Already there"; StatusBrush = Dim; }
    public void SetFailed() { Status = "Failed"; StatusBrush = Red; }

    public event PropertyChangedEventHandler? PropertyChanged;
    void Raise(string n) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(n));
}

/// <summary>A playlist that was cloned before, so it can be synced again later.</summary>
public class RecentPlaylist
{
    public string Url { get; set; } = "";
    public string Name { get; set; } = "";
    public string Service { get; set; } = "";
    public string Folder { get; set; } = "";
    public int TrackCount { get; set; }
    public List<string> Keys { get; set; } = new();   // snapshot of the tracks at last sync

    [JsonIgnore]
    public string? ChangeNote { get; set; }
    public DateTime LastSynced { get; set; } = DateTime.Now;

    [JsonIgnore]
    public string Subtitle => $"{Service}  -  {TrackCount} tracks  -  last synced {LastSynced:d MMM yyyy, HH:mm}"
        + (ChangeNote == null ? "" : $"\n{ChangeNote}");
}

public class AppSettings
{
    public string OutputFolder { get; set; } = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
    public int Bitrate { get; set; } = 320;
    public int Parallel { get; set; } = 2;
    public bool Subfolder { get; set; } = true;
    public bool EmbedCover { get; set; } = true;
    public bool SkipExisting { get; set; } = true;
    public string LastUrl { get; set; } = "";
    public List<RecentPlaylist> Recent { get; set; } = new();

    static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Cave", "settings.json");

    public static AppSettings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(FilePath)) ?? new AppSettings();
        }
        catch { }
        return new AppSettings();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch { }
    }
}
