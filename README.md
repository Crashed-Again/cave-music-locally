# Cave for Android

Same idea as Cave on Windows: paste a YouTube Music, Spotify or Apple Music playlist link,
pick an output folder, get MP3s. It remembers recent playlists; **Sync** only downloads new songs.

## Build the APK
Needs Android Studio installed once (it brings Java and the Android SDK).

Option A, no command line: open this folder in Android Studio, wait for the sync,
then **Build > Build APK(s)**. The file ends up in `app/build/outputs/apk/debug/`.

Option B: run `build-apk.bat` (needs Gradle for the first run: `winget install Gradle.Gradle`).
The result is `dist\Cave.apk`.

Install: copy the APK to the phone, open it, allow "install unknown apps".

Option C, no Android Studio: put this folder in a GitHub repository (the `.github` folder must come
with it). Open the repo's **Actions** tab, pick **Build Cave APK**, press **Run workflow**, and download
`Cave-apk` from the finished run. The log there also shows any build error.

## How it works
yt-dlp and ffmpeg run inside the app (youtubedl-android library). Spotify/Apple Music tracks are
read from their public pages, then each song is searched on YouTube and converted to MP3 and tagged.
The output folder is picked with Android's folder picker; MP3s are copied into it.
A notification shows while downloading so Android keeps the app running.

## Limits
- Playlists must be public. Spotify's public page only exposes about 100 tracks.
- Search matching can pick a wrong version of a song now and then.
- Cover art is off by default (turn it on in Options); if it fails Cave retries without it.
- If downloads start failing, YouTube probably changed something: update the app's yt-dlp by
  bumping the youtubedl-android version in app/build.gradle.kts and rebuilding.
- Only download music you have the right to copy.
