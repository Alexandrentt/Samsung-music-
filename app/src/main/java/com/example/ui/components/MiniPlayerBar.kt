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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
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
                // Barra de progreso sutil en la parte superior
                if (currentSong != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Carátula de la canción con affordance para expandir
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (displaySong?.coverArtUrl != null) {
                            AsyncImage(
                                model = remember(displaySong.coverArtUrl) {
                                    displaySong.coverArtUrl?.let { path ->
                                        val localFile = File(path)
                                        if (localFile.isFile) localFile else path
                                    }
                                },
                                contentDescription = displaySong.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp)),
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

                        // Icono sutil de expandir
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(bottomStart = 6.dp, topEnd = 10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expandir reproductor",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
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
                            text = displaySong?.title ?: "Samsung Music",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (displaySong != null) {
                                if (currentSong != null) displaySong.artist else "${displaySong.artist} • Toca para escuchar"
                            } else {
                                "Selecciona una canción para escuchar"
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

