package com.neonbear.cave

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Downloads one track as MP3 into the chosen folder (a Storage Access Framework folder). */
object Downloader {

    enum class Result { Done, Skipped, Failed }

    /** Process ids of running yt-dlp jobs, so Cancel can kill them. */
    val active: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun safeName(s: String): String {
        var r = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')
        if (r.length > 150) r = r.take(150)
        return r.ifEmpty { "track" }
    }

    fun cancelAll() {
        for (pid in active.toList()) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(pid) }
        }
    }

    fun download(
        ctx: Context,
        track: Track,
        album: String,
        dest: DocumentFile,
        bitrate: Int,
        cover: Boolean,
        skipExisting: Boolean,
        pid: String,
        log: (String) -> Unit,
    ): Result {
        val fileName = safeName(track.display) + ".mp3"
        if (skipExisting && dest.findFile(fileName) != null) return Result.Skipped

        val work = File(ctx.cacheDir, "dl").apply { mkdirs() }
        fun cleanup() {
            work.listFiles()?.filter { it.name.startsWith(pid) }?.forEach { it.delete() }
        }

        cleanup()
        active.add(pid)
        try {
            var ok = runYtDlp(track, album, work, bitrate, cover, pid, log)
            if (!ok && cover) {
                log("Retrying without cover art: ${track.display}")
                cleanup()
                ok = runYtDlp(track, album, work, bitrate, false, pid, log)
            }

            val mp3 = File(work, "$pid.mp3")
            if (!ok || !mp3.exists()) return Result.Failed

            dest.findFile(fileName)?.delete()
            val out = dest.createFile("audio/mpeg", fileName)
            if (out == null) {
                log("Couldn't create $fileName in the output folder.")
                return Result.Failed
            }
            val stream = ctx.contentResolver.openOutputStream(out.uri) ?: return Result.Failed
            stream.use { o -> mp3.inputStream().use { it.copyTo(o) } }
            return Result.Done
        } finally {
            active.remove(pid)
            cleanup()
        }
    }

    private fun runYtDlp(
        track: Track,
        album: String,
        work: File,
        bitrate: Int,
        cover: Boolean,
        pid: String,
        log: (String) -> Unit,
    ): Boolean {
        val target = track.youtubeId?.let { "https://www.youtube.com/watch?v=$it" }
            ?: "ytsearch1:${track.artist} - ${track.title} audio"

        fun q(s: String) = s.replace("\"", "").replace("\\", "")

        val req = YoutubeDLRequest(target)
        req.addOption("-x")
        req.addOption("--audio-format", "mp3")
        req.addOption("--audio-quality", "${bitrate}K")
        req.addOption("--no-playlist")
        req.addOption("--no-warnings")
        req.addOption("-o", File(work, "$pid.%(ext)s").absolutePath)

        if (track.youtubeId != null) {
            req.addOption("--embed-metadata")
        } else {
            // Spotify / Apple Music: tag the file with the real title, artist and album.
            req.addOption(
                "--postprocessor-args",
                "ExtractAudio:-metadata \"title=${q(track.title)}\" -metadata \"artist=${q(track.artist)}\" " +
                    "-metadata \"album=${q(album)}\"",
            )
        }
        if (cover) {
            req.addOption("--embed-thumbnail")
            req.addOption("--convert-thumbnails", "jpg")
        }

        return try {
            YoutubeDL.getInstance().execute(req, pid)
            true
        } catch (e: Exception) {
            log("FAILED: ${track.display}\n${e.message?.take(600) ?: e.javaClass.simpleName}")
            false
        }
    }
}
