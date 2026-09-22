package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Song
import com.example.engine.LyricLine
import com.example.player.RepeatMode

enum class PlayerViewMode {
    COVER, LYRICS, QUEUE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    song: Song?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    lyrics: List<LyricLine>,
    activeLyricIndex: Int,
    queue: List<Song> = emptyList(),
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekToLyricLine: (Int) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenSoundAlive: () -> Unit,
    onPlayFromQueue: ((Song) -> Unit)? = null,
    onRemoveFromQueue: ((String) -> Unit)? = null
) {
    if (song == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewMode by remember { mutableStateOf(PlayerViewMode.COVER) }
    val lyricsListState = rememberLazyListState()

    // Auto-scroll lyrics smoothly to active line
    LaunchedEffect(activeLyricIndex, viewMode) {
        if (viewMode == PlayerViewMode.LYRICS && activeLyricIndex >= 0 && activeLyricIndex < lyrics.size) {
            val targetScroll = (activeLyricIndex - 2).coerceAtLeast(0)
            lyricsListState.animateScrollToItem(targetScroll)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("now_playing_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimizar",
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Mode Selector Pills: [Carátula] [Letra (.lrc)]
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (viewMode == PlayerViewMode.COVER) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { viewMode = PlayerViewMode.COVER }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = if (viewMode == PlayerViewMode.COVER) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Carátula",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (viewMode == PlayerViewMode.COVER) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (viewMode == PlayerViewMode.LYRICS) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { viewMode = PlayerViewMode.LYRICS }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = if (viewMode == PlayerViewMode.LYRICS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Letra .LRC",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (viewMode == PlayerViewMode.LYRICS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (viewMode == PlayerViewMode.QUEUE) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { viewMode = PlayerViewMode.QUEUE }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = if (viewMode == PlayerViewMode.QUEUE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Cola",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (viewMode == PlayerViewMode.QUEUE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSoundAlive) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "SoundAlive",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("now_playing_fav_button")
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (song.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Central View (Cover Art or Synced Lyrics)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "now_playing_mode_transition"
                ) { mode ->
                    when (mode) {
                        PlayerViewMode.COVER -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(0.82f)
                                        .aspectRatio(1f)
                                        .shadow(18.dp, RoundedCornerShape(26.dp))
                                        .clip(RoundedCornerShape(26.dp))
                                        .testTag("now_playing_album_art"),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!song.coverArtUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = song.coverArtUrl,
                                                contentDescription = "Carátula",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Minimal badges
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "SoundAlive 3D",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (song.lyricsLrc != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "✓ Letra Sincronizada",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF059669),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        PlayerViewMode.LYRICS -> {
                            // Synchronized Lyrics View
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                if (lyrics.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No hay letra sincronizada disponible.\nDescárgala junto a la pista desde YouTube.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        state = lyricsListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 48.dp, horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(lyrics) { index, lyricLine ->
                                            val isActive = index == activeLyricIndex
                                            val textColor by animateColorAsState(
                                                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                label = "lyric_text_color"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { onSeekToLyricLine(index) }
                                                    .background(
                                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                        else Color.Transparent
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = lyricLine.text,
                                                    style = if (isActive) {
                                                        MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 19.sp,
                                                            lineHeight = 26.sp
                                                        )
                                                    } else {
                                                        MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Normal,
                                                            fontSize = 15.sp,
                                                            lineHeight = 22.sp
                                                        )
                                                    },
                                                    color = textColor,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        PlayerViewMode.QUEUE -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Cola de reproducción",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${queue.size} pistas",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalIconButton(
                                                onClick = onToggleShuffle,
                                                modifier = Modifier.size(36.dp),
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = if (isShuffle) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shuffle,
                                                    contentDescription = "Aleatorio",
                                                    tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            FilledTonalIconButton(
                                                onClick = onCycleRepeat,
                                                modifier = Modifier.size(36.dp),
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                                    contentDescription = "Bucle",
                                                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (queue.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "La cola está vacía.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        val currentIdx = queue.indexOfFirst { it.id == song.id }
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            itemsIndexed(queue, key = { index, item -> "${item.id}_$index" }) { index, item ->
                                                val isCurrent = item.id == song.id
                                                val isNext = (index == currentIdx + 1)

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            when {
                                                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                                isNext -> Color(0xFF2563EB).copy(alpha = 0.08f)
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .clickable { onPlayFromQueue?.invoke(item) }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Thumbnail
                                                    Box(
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (!item.coverArtUrl.isNullOrBlank()) {
                                                            AsyncImage(
                                                                model = item.coverArtUrl,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.size(42.dp)
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.MusicNote,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = item.title,
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    fontWeight = if (isCurrent || isNext) FontWeight.Bold else FontWeight.Normal
                                                                ),
                                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                            if (isCurrent) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "EN CURSO",
                                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                                                        color = Color.White
                                                                    )
                                                                }
                                                            } else if (isNext) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(Color(0xFF2563EB).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "A CONTINUACIÓN",
                                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                                                        color = Color(0xFF1D4ED8)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Text(
                                                            text = item.artist,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    if (onRemoveFromQueue != null) {
                                                        IconButton(
                                                            onClick = { onRemoveFromQueue(item.id) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Quitar de la cola",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Song Title & Artist (Clean minimal typography)
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Seek Slider & Timestamps
            val safeDuration = if (durationMs > 0) durationMs else 1L
            val sliderValue = currentPositionMs.toFloat().coerceIn(0f, safeDuration.toFloat())

            Slider(
                value = sliderValue,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..safeDuration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_seek_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(safeDuration),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Minimalist Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.testTag("now_playing_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = if (isShuffle) "Aleatorio activado" else "Aleatorio desactivado",
                            tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (isShuffle) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp).testTag("now_playing_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Circle
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .shadow(6.dp, CircleShape)
                        .testTag("now_playing_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp).testTag("now_playing_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier.testTag("now_playing_repeat_button")
                    ) {
                        Icon(
                            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = when (repeatMode) {
                                RepeatMode.ALL -> "Bucle activado (todas las canciones)"
                                RepeatMode.ONE -> "Bucle activado (canción actual)"
                                RepeatMode.OFF -> "Bucle desactivado"
                            },
                            tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (repeatMode != RepeatMode.OFF) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
