package com.neonbear.cave

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF3B82F6)
private val PageBg = Color(0xFF232323)
private val CardBg = Color(0xFF1B1B1B)
private val Dim = Color(0xFF8E8E8E)
private val Pill = Color(0xFF2B2B2B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Blue,
                    background = PageBg,
                    surface = CardBg,
                    onSurface = Color(0xFFF2F2F2),
                    onBackground = Color(0xFFF2F2F2),
                )
            ) { CaveRoot() }
        }
    }
}

@Composable
private fun CaveRoot() {
    var tab by remember { mutableIntStateOf(0) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        containerColor = PageBg,
        bottomBar = {
            Column {
                if (tab == 0) ActionBar()
                NavigationBar(containerColor = Color.Black) {
                    listOf("Clone", "Options", "Log").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(listOf("♫", "⚙", "≡")[i], fontSize = 18.sp) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = Blue,
                                unselectedIconColor = Dim,
                                unselectedTextColor = Dim,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> ClonePage()
                1 -> OptionsPage()
                else -> LogPage()
            }
        }
    }
}

// ------------------------------------------------------------------ pieces
@Composable
private fun Header(title: String, subtitle: String, showLogo: Boolean = false) {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showLogo) {
            Image(painterResource(R.drawable.cave_logo), null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
        }
        Column {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(6.dp))
            .background(CardBg).padding(horizontal = 16.dp, vertical = 14.dp)
    ) { content() }
}

@Composable
private fun SmallButton(text: String, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) Blue else Color(0xFF3A3A3A),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2A2A2A),
            disabledContentColor = Color(0xFF777777),
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(desc, color = Dim, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Blue,
                    uncheckedThumbColor = Color(0xFF9A9A9A),
                    uncheckedTrackColor = Color(0xFF111111),
                    uncheckedBorderColor = Color(0xFF555555),
                ),
            )
        }
    }
}

@Composable
private fun PillRow(title: String, desc: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Card {
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, color = Dim, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { o ->
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(if (o == selected) Blue else Pill)
                            .clickable { onSelect(o) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text(o.toString(), color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ pages
@Composable
private fun ClonePage() {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) Engine.setFolder(uri)
    }
    val source = PlaylistReader.detect(Engine.url)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item { Header("Clone", "Paste a playlist link and turn it into MP3s.", showLogo = true) }

        item {
            Card {
                Column {
                    Row {
                        Text("Playlist link", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(source?.label ?: "", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Public YouTube Music, Spotify or Apple Music playlists.", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = Engine.url,
                        onValueChange = { Engine.url = it },
                        enabled = !Engine.busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue,
                            unfocusedBorderColor = Color(0xFF3A3A3A),
                            cursorColor = Blue,
                        ),
                    )
                }
            }
        }

        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Output folder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(Engine.folderName, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SmallButton("Choose...") { pickFolder.launch(null) }
                }
            }
        }

        if (Engine.recent.isNotEmpty()) {
            item {
                Card {
                    Column {
                        Text("Recent playlists", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Sync only downloads songs added since last time.", color = Dim, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Engine.recent.forEach { r ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { Engine.useRecent(r) }) {
                                    Text(r.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${r.service}  -  ${r.count} tracks", color = Dim, fontSize = 11.sp)
                                }
                                SmallButton("Sync", accent = true, enabled = !Engine.busy) { Engine.syncRecent(r) }
                            }
                        }
                    }
                }
            }
        }

        if (Engine.tracks.isNotEmpty()) {
            item { Text("Tracks", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(4.dp)) }
            items(Engine.tracks.toList()) { t ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp)) {
                    Text(t.track.display, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        t.status.label,
                        Modifier.padding(start = 12.dp),
                        fontSize = 12.sp,
                        color = when (t.status) {
                            Status.Working -> Blue
                            Status.Done -> Color(0xFF4ADE80)
                            Status.Failed -> Color(0xFFF87171)
                            else -> Dim
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ActionBar() {
    Column(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(Engine.status, color = Color(0xFFBDBDBD), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { Engine.progress },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = Blue,
            trackColor = Color(0xFF333333),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallButton("Retry failed", enabled = !Engine.busy && Engine.failedCount > 0) { Engine.retryFailed() }
            if (Engine.busy) SmallButton("Cancel") { Engine.cancel() }
            Spacer(Modifier.weight(1f))
            SmallButton("Clone playlist", accent = true, enabled = !Engine.busy) { Engine.start() }
        }
    }
}

@Composable
private fun OptionsPage() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item { Header("Options", "Quality and download behaviour.") }
        item {
            PillRow("MP3 bitrate", "Higher = bigger files.", listOf(128, 192, 256, 320), Engine.bitrate) {
                Engine.bitrate = it; Engine.save()
            }
        }
        item {
            PillRow("Parallel downloads", "Songs downloading at the same time.", listOf(1, 2, 3, 4), Engine.parallel) {
                Engine.parallel = it; Engine.save()
            }
        }
        item {
            ToggleRow("Playlist subfolder", "Put tracks in a folder named after the playlist.", Engine.subfolder) {
                Engine.subfolder = it; Engine.save()
            }
        }
        item {
            ToggleRow("Embed cover art", "Adds album art to each MP3. Off by default; if it fails Cave retries without it.", Engine.cover) {
                Engine.cover = it; Engine.save()
            }
        }
        item {
            ToggleRow("Skip tracks already downloaded", "Re-run a playlist to pick up new songs only.", Engine.skipExisting) {
                Engine.skipExisting = it; Engine.save()
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Image(
                painterResource(R.drawable.neonbear), null,
                Modifier.width(170.dp).padding(start = 4.dp),
                contentScale = ContentScale.FillWidth,
            )
            Text("Copyright (c) 2026 - NeonBear", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun LogPage() {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Header("Log", "Useful if a track fails.")
        Card(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(Engine.logLines.toList()) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFCFCFCF),
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
