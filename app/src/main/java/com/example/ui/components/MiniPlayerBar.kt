package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.Song

/**
 * Barra superpuesta persistente de reproducción en la parte inferior sobre la barra
 * de navegación del sistema, permitiendo al usuario controlar la reproducción
 * en todo momento mientras navega por la biblioteca.
 */
@Composable
fun MiniPlayerBar(
    currentSong: Song?,
    defaultSong: Song? = null,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val displaySong = currentSong ?: defaultSong
    val isActuallyPlaying = isPlaying && currentSong != null

    val progress = if (durationMs > 0 && currentSong != null) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // "1:23" / "3:45" para el subtítulo mientras suena música
    fun fmtTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    if (displaySong != null) {
                        onOpenPlayer()
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Barra de progreso en la parte superior
                if (currentSong != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Carátula de la canción (limpia, sin badge superpuesto)
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (displaySong?.coverArtUrl != null) {
                            AsyncImage(
                                model = displaySong.coverArtUrl,
                                contentDescription = displaySong.title,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Información de pista
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = displaySong?.title ?: "Música",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                displaySong == null -> "Selecciona una canción para escuchar"
                                currentSong == null -> "${displaySong.artist} • Toca para escuchar"
                                durationMs > 0 -> "${displaySong.artist} • ${fmtTime(currentPositionMs)} / ${fmtTime(durationMs)}"
                                else -> displaySong.artist
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Botón Anterior
                    IconButton(
                        onClick = onSkipPrevious,
                        enabled = displaySong != null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Canción anterior",
                            tint = if (displaySong != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Botón Play / Pause destacado con estilo One UI squircle
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = Color(0xFFFFD8CE),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .clickable(enabled = displaySong != null) { onTogglePlayPause() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isActuallyPlaying) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_notif_pause_dark),
                                    contentDescription = "Pausar",
                                    tint = Color(0xFF371E1B),
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_notif_play_dark),
                                    contentDescription = "Reproducir",
                                    tint = Color(0xFF371E1B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Botón Siguiente
                    IconButton(
                        onClick = onSkipNext,
                        enabled = displaySong != null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente canción",
                            tint = if (displaySong != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

