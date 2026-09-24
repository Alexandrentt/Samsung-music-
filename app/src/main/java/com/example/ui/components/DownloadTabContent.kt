package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DownloadHistoryItem
import com.example.data.Song
import com.example.engine.DownloadProgress
import com.example.engine.MusicaEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadTabContent(
    downloadProgress: DownloadProgress?,
    isDownloading: Boolean,
    downloadHistory: List<DownloadHistoryItem>,
    youtubeSearchQuery: String,
    isSearchingYouTube: Boolean,
    localSearchResults: List<Song>,
    youtubeSearchResults: List<MusicaEngine.PlaylistItem>,
    onYouTubeSearchQueryChange: (String) -> Unit,
    onSearchYouTube: (String) -> Unit,
    onPlayLocalSearchResult: (Song) -> Unit,
    onSelectPlaylistItem: (MusicaEngine.PlaylistItem) -> Unit,
    onDownloadUrl: (String, Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var autoEnrich by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Material 3 Search Bar for YouTube queries and URLs
        item {
            YouTubeSearchBar(
                query = youtubeSearchQuery,
                onQueryChange = onYouTubeSearchQueryChange,
                onSearch = onSearchYouTube,
                isSearching = isSearchingYouTube,
                localResults = localSearchResults,
                youtubeResults = youtubeSearchResults,
                onPlayLocalSong = onPlayLocalSearchResult,
                onSelectPlaylistItem = { item ->
                    urlInput = "https://www.youtube.com/watch?v=${item.videoId}"
                    onSelectPlaylistItem(item)
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Active Download Progress Card (sticky at top of the list for visibility)
        if (downloadProgress != null) {
            item {
                DownloadStatusCard(downloadProgress)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Descargar Música o Lista",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Pega el enlace de una canción o lista de YouTube, o busca directamente.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL de YouTube") },
                        placeholder = { Text("https://youtube.com/...") },
                        singleLine = true,
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enriquecer con MusicBrainz", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Obtiene carátula en HD, álbum y etiquetas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoEnrich, onCheckedChange = { autoEnrich = it })
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    onDownloadUrl(urlInput.trim(), autoEnrich)
                                }
                            },
                            enabled = !isDownloading && urlInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descargando...")
                            } else {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descargar")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                urlInput = MusicaEngine.PLAYLIST_URL_DEFAULT
                                onDownloadUrl(MusicaEngine.PLAYLIST_URL_DEFAULT, autoEnrich)
                            },
                            enabled = !isDownloading
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sincronizar Lista")
                        }
                    }
                }
            }
        }

        // History Section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historial de Descargas (${downloadHistory.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (downloadHistory.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        }

        if (downloadHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aún no se ha descargado ninguna canción.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(downloadHistory, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(item.channel.ifBlank { "YouTube" })
                                    if (item.downloadedAt > 0) {
                                        append(" • ")
                                        append(formatDate(item.downloadedAt))
                                    }
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("¿Limpiar historial?") },
            text = { Text("Se eliminarán los ${downloadHistory.size} registros del historial de descargas. Las canciones descargadas no se borrarán.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearConfirm = false
                }) {
                    Text("Limpiar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DownloadStatusCard(progress: DownloadProgress) {
    val isDone = progress.isFinished
    val hasError = progress.error != null

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasError -> MaterialTheme.colorScheme.errorContainer
                isDone -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        hasError -> Icons.Default.Error
                        isDone -> Icons.Default.CheckCircle
                        else -> Icons.Default.CloudDownload
                    },
                    contentDescription = null,
                    tint = when {
                        hasError -> MaterialTheme.colorScheme.onErrorContainer
                        isDone -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            hasError -> "Error en la descarga"
                            isDone -> "Descarga completada"
                            else -> "Descargando…"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            hasError -> MaterialTheme.colorScheme.onErrorContainer
                            isDone -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
                if (!isDone && !hasError) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${(progress.percent * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (progress.bytesTotal > 0) {
                            Text(
                                text = "${formatBytes(progress.bytesDownloaded)} / ${formatBytes(progress.bytesTotal)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (progress.currentSongTitle.isNotBlank()) {
                Text(
                    text = progress.currentSongTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        hasError -> MaterialTheme.colorScheme.onErrorContainer
                        isDone -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            val total = progress.totalItems
            if (total > 1) {
                Text(
                    text = "Canción ${progress.currentItemIndex} de $total",
                    fontSize = 12.sp,
                    color = when {
                        hasError -> MaterialTheme.colorScheme.onErrorContainer
                        isDone -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (hasError) {
                Text(
                    text = progress.error ?: "Error desconocido",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            } else if (!isDone) {
                LinearProgressIndicator(
                    progress = { progress.percent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = progress.step,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1_000} kB"
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        SimpleDateFormat("d MMM, HH:mm", Locale("es")).format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}
