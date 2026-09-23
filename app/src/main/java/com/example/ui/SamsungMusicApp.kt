package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Song
import com.example.ui.components.DownloadTabContent
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NowPlayingSheet
import com.example.ui.components.PlaylistsTabContent
import com.example.ui.components.SamsungTabs
import com.example.ui.components.SamsungTopAppBar
import com.example.ui.components.SongGridItem
import com.example.ui.components.SongItemRow
import com.example.ui.components.SoundAliveDialog
import com.example.ui.components.StatsDialog

@Composable
fun SamsungMusicApp(viewModel: SamsungMusicViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val selectedTab by viewModel.selectedTab.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val playlistsWithSongs by viewModel.playlistsWithSongs.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val downloadHistory by viewModel.downloadHistory.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val statsData by viewModel.statsDialogData.collectAsState()
    val showNowPlaying by viewModel.showNowPlayingSheet.collectAsState()
    val showSoundAlive by viewModel.showSoundAliveDialog.collectAsState()
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsState()
    val songSortOrder by viewModel.songSortOrder.collectAsState()
    val songViewMode by viewModel.songViewMode.collectAsState()
    val youtubeSearchQuery by viewModel.youtubeSearchQuery.collectAsState()
    val isSearchingYouTube by viewModel.isSearchingYouTube.collectAsState()
    val youtubeSearchResults by viewModel.youtubeSearchResults.collectAsState()
    val localSearchResults by viewModel.localSearchResults.collectAsState()

    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val currentPositionMs by viewModel.playerManager.currentPositionMs.collectAsState()
    val durationMs by viewModel.playerManager.durationMs.collectAsState()
    val isShuffle by viewModel.playerManager.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsState()
    val lyrics by viewModel.playerManager.currentLyrics.collectAsState()
    val activeLyricIndex by viewModel.playerManager.activeLyricIndex.collectAsState()

    Scaffold(
        topBar = {
            SamsungTopAppBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onToggleSearch = { viewModel.toggleSearchActive(it) },
                onOpenSoundAlive = { viewModel.openSoundAliveDialog() },
                onShowStats = { viewModel.showStatistics() },
                onCleanFolder = { viewModel.cleanMusicFolder() },
                onEnrichAll = { viewModel.enrichAllSongs(force = true) },
                onExportCsv = { viewModel.exportToCsv(context) }
            )
        },
        bottomBar = {
            MiniPlayerBar(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                onSkipNext = { viewModel.playerManager.skipToNext() },
                onSkipPrevious = { viewModel.playerManager.skipToPrevious() },
                onOpenPlayer = { viewModel.openNowPlayingSheet() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SamsungTabs(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            when (selectedTab) {
                SamsungTab.TRACKS -> {
                    SongsTabContent(
                        songs = filteredSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        sortOrder = songSortOrder,
                        viewMode = songViewMode,
                        onSortChange = { viewModel.setSongSortOrder(it) },
                        onToggleSortDirection = { viewModel.toggleSongSortDirection() },
                        onToggleViewMode = { viewModel.toggleSongViewMode() },
                        onShuffleAll = { viewModel.playAllShuffled(filteredSongs) },
                        onSongClick = { song -> viewModel.playSong(song, filteredSongs) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNext(listOf(it)) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                        onDeleteSong = { viewModel.deleteSong(it) }
                    )
                }

                SamsungTab.FAVORITES -> {
                    SongsTabContent(
                        songs = favoriteSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        sortOrder = songSortOrder,
                        viewMode = songViewMode,
                        onSortChange = { viewModel.setSongSortOrder(it) },
                        onToggleSortDirection = { viewModel.toggleSongSortDirection() },
                        onToggleViewMode = { viewModel.toggleSongViewMode() },
                        onShuffleAll = { viewModel.playAllShuffled(favoriteSongs) },
                        onSongClick = { song -> viewModel.playSong(song, favoriteSongs) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNext(listOf(it)) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                        onDeleteSong = { viewModel.deleteSong(it) },
                        emptyMessage = "No tienes canciones marcadas como favoritas."
                    )
                }

                SamsungTab.PLAYLISTS -> {
                    PlaylistsTabContent(
                        playlistsWithSongs = playlistsWithSongs,
                        selectedPlaylistId = selectedPlaylistId,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onPlaySong = { song, queue -> viewModel.playSong(song, queue) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRemoveSongFromPlaylist = { pId, sId -> viewModel.removeSongFromPlaylist(pId, sId) },
                        onDeleteSong = { viewModel.deleteSong(it) },
                        onPlayNext = { viewModel.playNext(it) }
                    )
                }

                SamsungTab.DOWNLOAD -> {
                    DownloadTabContent(
                        downloadProgress = downloadProgress,
                        isDownloading = isDownloading,
                        downloadHistory = downloadHistory,
                        youtubeSearchQuery = youtubeSearchQuery,
                        isSearchingYouTube = isSearchingYouTube,
                        localSearchResults = localSearchResults,
                        youtubeSearchResults = youtubeSearchResults,
                        onYouTubeSearchQueryChange = { viewModel.setYouTubeSearchQuery(it) },
                        onSearchYouTube = { viewModel.searchYouTube(it) },
                        onPlayLocalSearchResult = { viewModel.playLocalSearchResult(it) },
                        onSelectPlaylistItem = { item -> viewModel.downloadPlaylistItem(item) },
                        onDownloadUrl = { url, enrich -> viewModel.downloadFromUrl(url, enrich) },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
            }
        }
    }

    // Now Playing Modal Sheet
    if (showNowPlaying) {
        NowPlayingSheet(
            currentSong = currentSong,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            lyrics = lyrics,
            activeLyricIndex = activeLyricIndex,
            onDismiss = { viewModel.closeNowPlayingSheet() },
            onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
            onSkipNext = { viewModel.playerManager.skipToNext() },
            onSkipPrevious = { viewModel.playerManager.skipToPrevious() },
            onSeek = { viewModel.playerManager.seekTo(it) },
            onSeekLyric = { viewModel.playerManager.seekToLyricLine(it) },
            onToggleShuffle = { viewModel.toggleShuffleWithFeedback() },
            onCycleRepeat = { viewModel.cycleRepeatModeWithFeedback() },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onOpenSoundAlive = { viewModel.openSoundAliveDialog() }
        )
    }

    // SoundAlive Equalizer Dialog
    if (showSoundAlive) {
        SoundAliveDialog(onDismiss = { viewModel.closeSoundAliveDialog() })
    }

    // Stats Dialog
    statsData?.let { stats ->
        StatsDialog(stats = stats, onDismiss = { viewModel.dismissStatsDialog() })
    }

    // Add To Playlist Dialog
    songToAddToPlaylist?.let { song ->
        val playlists = playlistsWithSongs.map { it.playlist }
        AlertDialog(
            onDismissRequest = { viewModel.closeAddToPlaylistDialog() },
            title = { Text("Añadir a lista de reproducción") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No hay listas de reproducción disponibles.")
                } else {
                    LazyColumn {
                        items(playlists) { playlist ->
                            Text(
                                text = playlist.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addSongToPlaylist(playlist.id, song.id)
                                        viewModel.closeAddToPlaylistDialog()
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddToPlaylistDialog() }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SongsTabContent(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    sortOrder: SongSortOrder,
    viewMode: SongViewMode,
    onSortChange: (SongSortOrder) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleViewMode: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    emptyMessage: String = "No hay canciones disponibles."
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar: shuffle | count | sort chip + direction | view mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffleAll, enabled = songs.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Reproducción aleatoria",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "${songs.size} canciones",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            // Sort field chip with menu (field + quick direction toggle inside)
            FilterChip(
                selected = true,
                onClick = { showSortMenu = true },
                label = { Text(sortOrder.field.displayName, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Ascending / descending quick toggle
            IconButton(onClick = onToggleSortDirection) {
                Icon(
                    imageVector = if (sortOrder.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (sortOrder.ascending) "Orden ascendente" else "Orden descendente",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // View mode toggle
            IconButton(onClick = onToggleViewMode) {
                val icon = if (viewMode == SongViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList
                Icon(
                    imageVector = icon,
                    contentDescription = if (viewMode == SongViewMode.LIST) "Ver como cuadrícula" else "Ver como lista",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                SongSortField.values().forEach { field ->
                    val isSelected = sortOrder.field == field
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = field.displayName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = if (sortOrder.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        trailingIcon = if (!isSelected) {
                            {
                                Text(
                                    text = "↑↓",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            onSortChange(
                                if (isSelected) sortOrder else SongSortOrder(field = field, ascending = sortOrder.ascending)
                            )
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (viewMode == SongViewMode.LIST) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    SongItemRow(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        isPlaying = isPlaying && song.id == currentSong?.id,
                        onSongClick = { onSongClick(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onDelete = { onDeleteSong(song) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongGridItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        isPlaying = isPlaying,
                        onSongClick = { onSongClick(song) },
                        onToggleFavorite = { onToggleFavorite(song) }
                    )
                }
            }
        }
    }
}
