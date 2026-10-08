package com.neonbear.cave

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Source(val label: String) {
    YouTubeMusic("YouTube Music"),
    Spotify("Spotify"),
    AppleMusic("Apple Music"),
}

data class Track(val title: String, val artist: String, val youtubeId: String? = null) {
    val display: String get() = if (artist.isBlank()) title else "$artist - $title"
}

data class PlaylistInfo(val name: String, val tracks: List<Track>)

enum class Status(val label: String) {
    Waiting("Waiting"),
    Working("Downloading..."),
    Done("Done"),
    Skipped("Already there"),
    Failed("Failed"),
}

class TrackUi(val track: Track) {
    var status by mutableStateOf(Status.Waiting)
}

data class Recent(
    val url: String,
    val name: String,
    val service: String,
    val count: Int,
    val syncedAt: Long,
)
