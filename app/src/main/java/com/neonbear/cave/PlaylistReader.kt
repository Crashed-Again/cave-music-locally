package com.neonbear.cave

import android.text.Html
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Turns a playlist URL into a list of tracks. */
object PlaylistReader {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun detect(url: String): Source? {
        val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull() ?: return null
        return when {
            host.contains("spotify.com") -> Source.Spotify
            host.contains("music.apple.com") -> Source.AppleMusic
            host.contains("youtube.com") || host.contains("youtu.be") -> Source.YouTubeMusic
            else -> null
        }
    }

    suspend fun read(source: Source, url: String): PlaylistInfo = withContext(Dispatchers.IO) {
        when (source) {
            Source.Spotify -> readSpotify(url)
            Source.AppleMusic -> readApple(url)
            Source.YouTubeMusic -> readYouTube(url)
        }
    }

    // Spotify: public embed page, no login needed. Only exposes roughly the first 100 tracks.
    private fun readSpotify(url: String): PlaylistInfo {
        val m = Regex("open\\.spotify\\.com/(?:intl-[a-z\\-]+/)?(playlist|album)/([A-Za-z0-9]+)").find(url)
            ?: error("That doesn't look like a Spotify playlist or album link.")
        val html = httpGet("https://open.spotify.com/embed/${m.groupValues[1]}/${m.groupValues[2]}")
        val json = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: error("Couldn't read the Spotify page. Is the playlist public?")

        val root = JSONObject(json)
        val entity = findKey(root, "entity") as? JSONObject ?: root
        val name = entity.optString("name").ifBlank { entity.optString("title") }.ifBlank { "Spotify playlist" }

        val tracks = mutableListOf<Track>()
        val list = findKey(entity, "trackList") as? JSONArray
        if (list != null) {
            for (i in 0 until list.length()) {
                val t = list.optJSONObject(i) ?: continue
                val title = t.optString("title")
                if (title.isNotBlank()) tracks.add(Track(clean(title), clean(t.optString("subtitle"))))
            }
        }
        if (tracks.isEmpty()) error("No tracks found. The playlist may be private or empty.")
        return PlaylistInfo(clean(name), tracks)
    }

    // Apple Music: reads the JSON blob embedded in the public playlist page.
    private fun readApple(url: String): PlaylistInfo {
        val html = httpGet(url)
        val name = Regex("<meta property=\"og:title\" content=\"([^\"]+)\"").find(html)
            ?.groupValues?.get(1)?.let { clean(it) } ?: "Apple Music playlist"

        val blob = Regex("<script[^>]*id=\"serialized-server-data\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: error("Couldn't read the Apple Music page. Is the playlist public?")

        val found = mutableListOf<Track>()
        val seen = HashSet<String>()
        collect(JSONTokener(blob).nextValue(), found, seen)
        if (found.isEmpty()) error("No tracks found on that Apple Music page.")
        return PlaylistInfo(name, found)
    }

    private fun collect(node: Any?, into: MutableList<Track>, seen: HashSet<String>) {
        when (node) {
            is JSONObject -> {
                val title = node.optString("title")
                val artist = node.optString("artistName")
                if (title.isNotBlank() && artist.isNotBlank() && seen.add("$artist|$title")) {
                    into.add(Track(clean(title), clean(artist)))
                }
                for (k in node.keys()) collect(node.opt(k), into, seen)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), into, seen)
        }
    }

    // YouTube Music: let yt-dlp list the playlist.
    private fun readYouTube(url: String): PlaylistInfo {
        val m = Regex("[?&]list=([\\w\\-]+)").find(url)
            ?: error("That YouTube link has no playlist in it (no list= part).")
        val req = YoutubeDLRequest("https://www.youtube.com/playlist?list=${m.groupValues[1]}")
        req.addOption("--flat-playlist")
        req.addOption("--ignore-errors")
        req.addOption("--no-warnings")
        req.addOption("--print", "%(id)s\t%(title)s\t%(playlist_title)s")
        val out = YoutubeDL.getInstance().execute(req).out

        val tracks = mutableListOf<Track>()
        var name = "YouTube playlist"
        for (line in out.lines()) {
            val parts = line.trimEnd('\r').split('\t')
            if (parts.size < 2 || parts[0].isBlank()) continue
            if (parts[1].startsWith("[Private") || parts[1].startsWith("[Deleted")) continue
            tracks.add(Track(parts[1], "", parts[0]))
            if (parts.size >= 3 && parts[2] != "NA" && parts[2].isNotBlank()) name = parts[2]
        }
        if (tracks.isEmpty()) error("No tracks found. Is the playlist public?")
        return PlaylistInfo(name, tracks)
    }

    // helpers
    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00A0', ' ').trim()

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun findKey(node: Any?, key: String): Any? {
        when (node) {
            is JSONObject -> {
                if (node.has(key)) return node.get(key)
                for (k in node.keys()) findKey(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findKey(node.opt(i), key)?.let { return it }
        }
        return null
    }
}
