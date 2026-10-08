package com.neonbear.cave

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * All app state and the download logic. It lives at app level (not in the Activity) so a
 * download keeps going if you leave the app or rotate the phone.
 */
object Engine {
    private lateinit var app: Application
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ready = CompletableDeferred<Unit>()
    private var job: Job? = null
    private var counter = 0

    private var lastDest: DocumentFile? = null
    private var lastAlbum = ""

    // ---- state the UI reads
    var url by mutableStateOf("")
    var folderUri by mutableStateOf<Uri?>(null)
    var folderName by mutableStateOf("Not chosen yet")
    var bitrate by mutableStateOf(320)
    var parallel by mutableStateOf(2)
    var subfolder by mutableStateOf(true)
    var cover by mutableStateOf(false)
    var skipExisting by mutableStateOf(true)

    var busy by mutableStateOf(false)
    var status by mutableStateOf("Ready")
    var progress by mutableStateOf(0f)
    var failedCount by mutableStateOf(0)

    val tracks = mutableStateListOf<TrackUi>()
    val recent = mutableStateListOf<Recent>()
    val logLines = mutableStateListOf<String>()

    // ---------------------------------------------------------------- setup
    fun init(application: Application) {
        app = application
        prefs = app.getSharedPreferences("cave", Context.MODE_PRIVATE)
        load()
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready.complete(Unit)
            } catch (e: Throwable) {
                log("Downloader setup failed: ${e.message}")
                ready.completeExceptionally(e)
            }
        }
    }

    private fun load() {
        url = prefs.getString("url", "") ?: ""            // remember the last playlist
        bitrate = prefs.getInt("bitrate", 320)
        parallel = prefs.getInt("parallel", 2)
        subfolder = prefs.getBoolean("subfolder", true)
        cover = prefs.getBoolean("cover", false)
        skipExisting = prefs.getBoolean("skip", true)
        prefs.getString("folder", null)?.let { setFolderInternal(Uri.parse(it), persist = false) }
        runCatching {
            val arr = JSONArray(prefs.getString("recent", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                recent.add(Recent(o.getString("url"), o.getString("name"), o.getString("service"),
                    o.getInt("count"), o.getLong("at")))
            }
        }
    }

    fun save() {
        val arr = JSONArray()
        recent.forEach {
            arr.put(JSONObject().put("url", it.url).put("name", it.name).put("service", it.service)
                .put("count", it.count).put("at", it.syncedAt))
        }
        prefs.edit()
            .putString("url", url.trim())
            .putInt("bitrate", bitrate)
            .putInt("parallel", parallel)
            .putBoolean("subfolder", subfolder)
            .putBoolean("cover", cover)
            .putBoolean("skip", skipExisting)
            .putString("folder", folderUri?.toString())
            .putString("recent", arr.toString())
            .apply()
    }

    // ---------------------------------------------------------------- folder
    fun setFolder(uri: Uri) {
        setFolderInternal(uri, persist = true)
        save()
    }

    private fun setFolderInternal(uri: Uri, persist: Boolean) {
        if (persist) {
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        folderUri = uri
        folderName = DocumentFile.fromTreeUri(app, uri)?.name ?: uri.lastPathSegment ?: "Chosen folder"
    }

    // ---------------------------------------------------------------- actions
    fun start(forceSkip: Boolean = false) {
        if (busy) return
        val u = url.trim()
        val source = PlaylistReader.detect(u)
        if (source == null) { toast("Paste a YouTube Music, Spotify or Apple Music playlist link first."); return }
        val tree = folderUri
        if (tree == null) { toast("Choose an output folder first."); return }

        save()
        busy = true
        failedCount = 0
        progress = 0f
        tracks.clear()
        DownloadService.start(app)

        job = scope.launch {
            try {
                status = "Preparing downloader..."
                ready.await()

                status = "Reading playlist..."
                log("Reading ${source.label} playlist: $u")
                val playlist = PlaylistReader.read(source, u)
                log("Found \"${playlist.name}\" with ${playlist.tracks.size} tracks.")
                remember(source, u, playlist)

                lastDest = withContext(Dispatchers.IO) { prepareDest(tree, playlist.name) }
                lastAlbum = playlist.name
                playlist.tracks.forEach { tracks.add(TrackUi(it)) }

                runDownloads(tracks.toList(), forceSkip || skipExisting)
                log("Finished.")
            } catch (e: CancellationException) {
                status = "Cancelled"
                log("Cancelled.")
                tracks.filter { it.status == Status.Working }.forEach { it.status = Status.Waiting }
            } catch (e: Exception) {
                status = "Error"
                log("Error: ${e.message}")
                toast(e.message ?: "Something went wrong.")
            } finally {
                finishRun()
            }
        }
    }

    fun retryFailed() {
        if (busy) return
        if (lastDest == null) return
        val items = tracks.filter { it.status == Status.Failed }
        if (items.isEmpty()) return

        busy = true
        DownloadService.start(app)
        job = scope.launch {
            try {
                log("Retrying ${items.size} failed tracks...")
                items.forEach { it.status = Status.Waiting }
                runDownloads(items, true)
            } catch (e: CancellationException) {
                status = "Cancelled"
                items.filter { it.status != Status.Done }.forEach { it.status = Status.Failed }
            } catch (e: Exception) {
                status = "Error"
                log("Error: ${e.message}")
            } finally {
                finishRun()
            }
        }
    }

    fun cancel() {
        status = "Cancelling..."
        Downloader.cancelAll()
        job?.cancel()
    }

    fun useRecent(r: Recent) { url = r.url }

    fun syncRecent(r: Recent) {
        if (busy) return
        url = r.url
        start(forceSkip = true)   // only downloads songs that aren't in the folder yet
    }

    // ---------------------------------------------------------------- internals
    private suspend fun runDownloads(items: List<TrackUi>, skip: Boolean) = coroutineScope {
        val dest = lastDest ?: error("Output folder isn't ready.")
        val total = items.size
        var finished = 0; var ok = 0; var skipped = 0; var failed = 0
        progress = 0f
        val gate = Semaphore(parallel)

        items.map { item ->
            async {
                gate.withPermit {
                    item.status = Status.Working
                    val pid = "cave-${counter++}"
                    val result = withContext(Dispatchers.IO) {
                        Downloader.download(app, item.track, lastAlbum, dest, bitrate, cover, skip, pid, ::log)
                    }
                    when (result) {
                        Downloader.Result.Done -> { item.status = Status.Done; ok++ }
                        Downloader.Result.Skipped -> { item.status = Status.Skipped; skipped++ }
                        Downloader.Result.Failed -> { item.status = Status.Failed; failed++ }
                    }
                    finished++
                    progress = finished.toFloat() / total
                    status = "$finished / $total  -  $ok done, $skipped skipped, $failed failed"
                }
            }
        }.awaitAll()

        status = "Finished: $ok done, $skipped skipped, $failed failed"
    }

    private fun finishRun() {
        busy = false
        failedCount = tracks.count { it.status == Status.Failed }
        job = null
        scope.launch {                       // give the service a moment to start before stopping it
            delay(800)
            DownloadService.stop(app)
        }
    }

    private fun prepareDest(tree: Uri, playlistName: String): DocumentFile {
        val root = DocumentFile.fromTreeUri(app, tree)
            ?: error("Can't open the output folder. Choose it again.")
        if (!subfolder) return root
        val name = Downloader.safeName(playlistName)
        return root.findFile(name)?.takeIf { it.isDirectory }
            ?: root.createDirectory(name)
            ?: error("Couldn't create the playlist folder.")
    }

    private fun remember(source: Source, url: String, playlist: PlaylistInfo) {
        recent.removeAll { it.url == url }
        recent.add(0, Recent(url, playlist.name, source.label, playlist.tracks.size, System.currentTimeMillis()))
        while (recent.size > 10) recent.removeAt(recent.size - 1)
        save()
    }

    fun log(message: String) {
        scope.launch {
            logLines.add(message)
            while (logLines.size > 400) logLines.removeAt(0)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(app, message, Toast.LENGTH_LONG).show()
    }
}
