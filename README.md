# Cave by NeonBear

Paste a YouTube Music, Spotify or Apple Music playlist link, pick an output folder, get MP3s.
Cave remembers your last playlist, and **Sync** re-checks a playlist and only downloads the songs added since last time.

## Build
Needs the .NET 8 SDK on Windows (https://dotnet.microsoft.com/download/dotnet/8.0).

- `build.bat` makes a standalone `dist\Cave\Cave.exe` (no .NET needed to run it).
- `build-msi.bat` runs build.bat, installs the WiX tool once, then makes `dist\Cave-1.0.0.msi`
  (installs to Program Files, adds a Start menu shortcut).

If a build fails, the errors are printed and saved to `build.log`.

**Installing for real:** run `dist\Cave-1.0.0.msi`. It installs Cave into Program Files and adds Start menu and
Desktop shortcuts, so deleting the folder you downloaded changes nothing. (Windows keeps its own copy of the MSI,
and your settings live in `%AppData%\Cave`.) Uninstall it from Settings > Apps.

**No .NET on your PC?** Put this folder in a GitHub repository, open the **Actions** tab, run
**Build Cave for Windows**, and download `Cave-installer` from the finished run.

## How it works
- YouTube Music: yt-dlp lists the playlist, then downloads each track as MP3.
- Spotify / Apple Music: titles and artists are read from the public playlist page (these
  services use DRM, so audio can't be taken from them directly). Each song is searched on
  YouTube, downloaded as MP3 and tagged with the real title/artist/album.
- First run downloads yt-dlp and ffmpeg into %LocalAppData%\Cave\tools.
- Settings and recent playlists live in %AppData%\Cave\settings.json.

## Extras
- `tools\fix-gamebar-popup.reg` stops the "ms-gamingoverlay" popup on PCs where Xbox Game Bar was removed
  (undo with `tools\undo-gamebar-popup-fix.reg`). Cave itself doesn't trigger it.

## Limits
- Playlists must be public.
- Spotify's public embed only exposes about the first 100 tracks.
- Spotify/Apple tracks are matched by search, so an occasional track may be a wrong version.
- Only download music you have the right to copy.
