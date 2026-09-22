package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.Song
import com.example.player.RepeatMode
import com.example.ui.components.AddToPlaylistSelectionDialog
import com.example.ui.components.CreatePlaylistDialog
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
fun SamsungMusicApp(
    viewModel: SamsungMusicViewModel = viewModel()
) {
    val context = LocalContext.current

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val songSortOrder by viewModel.songSortOrder.collectAsStateWithLifecycle()
    val songViewMode by viewModel.songViewMode.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val statsData by viewModel.statsDialogData.collectAsStateWithLifecycle()
    val enrichProgress by viewModel.enrichProgress.collectAsStateWithLifecycle()
    val showNowPlayingSheet by viewModel.showNowPlayingSheet.collectAsStateWithLifecycle()
    val showSoundAliveDialog by viewModel.showSoundAliveDialog.collectAsStateWithLifecycle()

    val currentSong by viewModel.playerManager.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.playerManager.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.playerManager.durationMs.collectAsStateWithLifecycle()
    val isShuffle by viewModel.playerManager.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsStateWithLifecycle()
    val lyrics by viewModel.playerManager.currentLyrics.collectAsStateWithLifecycle()
    val activeLyricIndex by viewModel.playerManager.activeLyricIndex.collectAsStateWithLifecycle()
    val queue by viewModel.playerManager.queue.collectAsStateWithLifecycle()

    val playlistsWithSongs by viewModel.playlistsWithSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsStateWithLifecycle()
    var showCreatePlaylistFromAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SamsungTopAppBar(
                currentTab = selectedTab,
                songCount = filteredSongs.size,
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onToggleSearch = { viewModel.toggleSearchActive(it) },
                onShowStats = { viewModel.showStatistics() },
                onEnrichAll = { force -> viewModel.enrichAllSongs(force) },
                onCleanFolder = { viewModel.cleanMusicFolder() },
                onExportCsv = { viewModel.exportToCsv(context) },
                onOpenSoundAlive = { viewModel.openSoundAliveDialog() }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                val displaySong = currentSong ?: filteredSongs.firstOrNull()
                if (displaySong != null) {
                    MiniPlayerBar(
                        currentSong = displaySong,
                        isPlaying = isPlaying && currentSong != null,
                        currentPositionMs = if (currentSong != null) currentPositionMs else 0L,
                        durationMs = if (currentSong != null) durationMs else displaySong.durationMs,
                        onTogglePlayPause = {
                            if (currentSong == null) {
                                viewModel.playSong(displaySong, filteredSongs)
                            } else {
                                viewModel.playerManager.togglePlayPause()
                            }
                        },
                        onSkipNext = { viewModel.playerManager.skipToNext() },
                        onSkipPrevious = { viewModel.playerManager.skipToPrevious() },
                        onOpenFullPlayer = {
                            if (currentSong == null) {
                                viewModel.playSong(displaySong, filteredSongs)
                            }
                            viewModel.openNowPlayingSheet()
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Samsung Music Tabs
            SamsungTabs(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            // Content Area based on selected tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    SamsungTab.CANCIONES -> {
                        SongsTabContent(
                            songs = filteredSongs,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            sortOrder = songSortOrder,
                            onSortOrderChange = { viewModel.setSongSortOrder(it) },
                            viewMode = songViewMode,
                            onToggleViewMode = { viewModel.toggleSongViewMode() },
                            onPlaySong = { song -> viewModel.playSong(song, filteredSongs) },
                            onPlayAll = {
                                if (filteredSongs.isNotEmpty()) {
                                    viewModel.playSong(filteredSongs.first(), filteredSongs)
                                }
                            },
                            onShuffleAll = {
                                if (filteredSongs.isNotEmpty()) {
                                    if (!isShuffle) viewModel.playerManager.toggleShuffle()
                                    val shuffled = filteredSongs.shuffled()
                                    viewModel.playSong(shuffled.first(), shuffled)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDeleteSong = { viewModel.deleteSong(it) },
                            onNavigateToDownloader = { viewModel.selectTab(SamsungTab.DESCARGAS_YT) },
                            onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onPlayShuffle = { song -> viewModel.playWithShuffle(song, filteredSongs) },
                            onPlayLoop = { song -> viewModel.playWithLoop(song) },
                            onToggleLoopAll = { viewModel.cycleRepeatModeWithFeedback() },
                            repeatMode = repeatMode
                        )
                    }

                    SamsungTab.PLAYLISTS -> {
                        PlaylistsTabContent(
                            playlistsWithSongs = playlistsWithSongs,
                            allSongs = filteredSongs,
                            selectedPlaylistId = selectedPlaylistId,
                            onSelectPlaylist = { viewModel.selectPlaylist(it) },
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                            onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                            onAddSongsToPlaylist = { id, songIds -> viewModel.addSongsToPlaylist(id, songIds) },
                            onRemoveSongFromPlaylist = { pId, sId -> viewModel.removeSongFromPlaylist(pId, sId) },
                            onPlaySong = { song, list -> viewModel.playSong(song, list) },
                            onPlayPlaylist = { list ->
                                if (list.isNotEmpty()) viewModel.playSong(list.first(), list)
                            },
                            onShufflePlaylist = { list ->
                                if (list.isNotEmpty()) {
                                    viewModel.playWithShuffle(list.first(), list)
                                }
                            },
                            onOpenAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                            onPlayNextSong = { viewModel.playNext(it) },
                            onPlayNextPlaylist = { viewModel.playNext(it) },
                            onLoopPlaylist = { list ->
                                if (list.isNotEmpty()) {
                                    viewModel.playerManager.setRepeatMode(RepeatMode.ALL)
                                    viewModel.playSong(list.first(), list)
                                    viewModel.showFeedbackToast("Bucle activado para toda la lista")
                                }
                            },
                            onPlayShuffleSong = { song -> viewModel.playWithShuffle(song, filteredSongs) },
                            onPlayLoopSong = { song -> viewModel.playWithLoop(song) }
                        )
                    }

                    SamsungTab.FAVORITOS -> {
                        SongsTabContent(
                            songs = favoriteSongs,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            sortOrder = songSortOrder,
                            onSortOrderChange = { viewModel.setSongSortOrder(it) },
                            viewMode = songViewMode,
                            onToggleViewMode = { viewModel.toggleSongViewMode() },
                            onPlaySong = { song -> viewModel.playSong(song, favoriteSongs) },
                            onPlayAll = {
                                if (favoriteSongs.isNotEmpty()) {
                                    viewModel.playSong(favoriteSongs.first(), favoriteSongs)
                                }
                            },
                            onShuffleAll = {
                                if (favoriteSongs.isNotEmpty()) {
                                    if (!isShuffle) viewModel.playerManager.toggleShuffle()
                                    val shuffled = favoriteSongs.shuffled()
                                    viewModel.playSong(shuffled.first(), shuffled)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDeleteSong = { viewModel.deleteSong(it) },
                            onNavigateToDownloader = { viewModel.selectTab(SamsungTab.DESCARGAS_YT) },
                            onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onPlayShuffle = { song -> viewModel.playWithShuffle(song, favoriteSongs) },
                            onPlayLoop = { song -> viewModel.playWithLoop(song) },
                            onToggleLoopAll = { viewModel.cycleRepeatModeWithFeedback() },
                            repeatMode = repeatMode
                        )
                    }

                    SamsungTab.ALBUMES -> {
                        AlbumsTabContent(
                            albums = albums,
                            onAlbumClick = { album ->
                                val albumSongs = filteredSongs.filter { it.album == album.name }
                                if (albumSongs.isNotEmpty()) {
                                    viewModel.playSong(albumSongs.first(), albumSongs)
                                }
                            }
                        )
                    }

                    SamsungTab.ARTISTAS -> {
                        ArtistsTabContent(
                            artists = artists,
                            onArtistClick = { artist ->
                                val artistSongs = filteredSongs.filter { it.artist == artist.name }
                                if (artistSongs.isNotEmpty()) {
                                    viewModel.playSong(artistSongs.first(), artistSongs)
                                }
                            }
                        )
                    }

                    SamsungTab.DESCARGAS_YT -> {
                        DownloadTabContent(
                            downloadProgress = downloadProgress,
                            isDownloading = isDownloading,
                            downloadHistory = downloadHistory,
                            onDownloadUrl = { url, autoEnrich ->
                                viewModel.downloadFromUrl(url, autoEnrich)
                            },
                            onDownloadAllPlaylists = { autoEnrich ->
                                viewModel.downloadAllPlaylists(autoEnrich)
                            },
                            onEnrichAll = {
                                viewModel.enrichAllSongs(false)
                            }
                        )
                    }
                }
            }
        }
    }

    // Full Screen Now Playing Sheet
    if (showNowPlayingSheet && currentSong != null) {
        NowPlayingSheet(
            song = currentSong,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            lyrics = lyrics,
            activeLyricIndex = activeLyricIndex,
            queue = queue,
            onDismiss = { viewModel.closeNowPlayingSheet() },
            onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
            onSeek = { viewModel.playerManager.seekTo(it) },
            onSeekToLyricLine = { viewModel.playerManager.seekToLyricLine(it) },
            onSkipNext = { viewModel.playerManager.skipToNext() },
            onSkipPrevious = { viewModel.playerManager.skipToPrevious() },
            onToggleShuffle = { viewModel.playerManager.toggleShuffle() },
            onCycleRepeat = { viewModel.playerManager.cycleRepeatMode() },
            onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
            onOpenSoundAlive = { viewModel.openSoundAliveDialog() },
            onPlayFromQueue = { song -> viewModel.playSong(song, queue) },
            onRemoveFromQueue = { songId -> viewModel.removeFromQueue(songId) }
        )
    }

    // Statistics Dialog
    if (statsData != null) {
        StatsDialog(
            stats = statsData!!,
            onDismiss = { viewModel.dismissStatsDialog() }
        )
    }

    // SoundAlive Dialog
    if (showSoundAliveDialog) {
        SoundAliveDialog(
            onDismiss = { viewModel.closeSoundAliveDialog() }
        )
    }

    // Metadata Enrichment Progress Dialog
    if (enrichProgress != null) {
        val (cur, tot) = enrichProgress!!
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss while processing */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Enriqueciendo metadata...")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Consultando MusicBrainz y Cover Art Archive para las canciones de la biblioteca ($cur de $tot)...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (tot > 0) cur.toFloat() / tot.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Add to Playlist Dialog
    if (songToAddToPlaylist != null) {
        AddToPlaylistSelectionDialog(
            song = songToAddToPlaylist!!,
            playlists = playlists,
            onDismiss = { viewModel.closeAddToPlaylistDialog() },
            onSelectPlaylist = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, songToAddToPlaylist!!.id)
                viewModel.closeAddToPlaylistDialog()
            },
            onCreateNewPlaylist = {
                showCreatePlaylistFromAddDialog = true
            }
        )
    }

    if (showCreatePlaylistFromAddDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistFromAddDialog = false },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showCreatePlaylistFromAddDialog = false
            }
        )
    }
}

@Composable
private fun SongsTabContent(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    sortOrder: SongSortOrder = SongSortOrder.ALPHABETICAL,
    onSortOrderChange: (SongSortOrder) -> Unit = {},
    viewMode: SongViewMode = SongViewMode.LIST,
    onToggleViewMode: () -> Unit = {},
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onNavigateToDownloader: () -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onPlayNext: ((Song) -> Unit)? = null,
    onPlayShuffle: ((Song) -> Unit)? = null,
    onPlayLoop: ((Song) -> Unit)? = null,
    onToggleLoopAll: (() -> Unit)? = null,
    repeatMode: RepeatMode = RepeatMode.OFF
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    if (songs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay canciones disponibles",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Usa el descargador de YouTube para bajar pistas o playlists completas con metadata.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onNavigateToDownloader,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ir a Descargar Música")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Quick play header row (Reproducir, Aleatorio, Bucle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reproducir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onShuffleAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aleatorio", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                if (onToggleLoopAll != null) {
                    FilledTonalIconButton(
                        onClick = onToggleLoopAll,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("btn_loop_all"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Modo bucle",
                            tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Toolbar: Sort order & Grid/List view mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { sortMenuExpanded = true }
                            .testTag("btn_sort_order"),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sortOrder.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SongSortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.title,
                                        fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                        color = if (order == sortOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (order == sortOrder) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSortOrderChange(order)
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${songs.size} pistas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("toggle_view_mode")
                    ) {
                        Icon(
                            imageVector = if (viewMode == SongViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = if (viewMode == SongViewMode.LIST) "Ver en cuadrícula" else "Ver en lista",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Display songs in List or Grid mode
            if (viewMode == SongViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongItemRow(
                            song = song,
                            isPlaying = isPlaying,
                            isCurrent = currentSong?.id == song.id,
                            onClick = { onPlaySong(song) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onDelete = { onDeleteSong(song) },
                            onAddToPlaylist = { onAddToPlaylist?.invoke(song) },
                            onPlayNext = if (onPlayNext != null) { { onPlayNext(song) } } else null,
                            onPlayShuffle = if (onPlayShuffle != null) { { onPlayShuffle(song) } } else null,
                            onPlayLoop = if (onPlayLoop != null) { { onPlayLoop(song) } } else null
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongGridItem(
                            song = song,
                            isPlaying = isPlaying,
                            isCurrent = currentSong?.id == song.id,
                            onClick = { onPlaySong(song) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onDelete = { onDeleteSong(song) },
                            onAddToPlaylist = { onAddToPlaylist?.invoke(song) },
                            onPlayNext = if (onPlayNext != null) { { onPlayNext(song) } } else null,
                            onPlayShuffle = if (onPlayShuffle != null) { { onPlayShuffle(song) } } else null,
                            onPlayLoop = if (onPlayLoop != null) { { onPlayLoop(song) } } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit
) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron álbumes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums) { album ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!album.coverArtUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = album.coverArtUrl,
                                    contentDescription = album.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${album.artist} • ${album.songCount} pistas",
                                style = MaterialTheme.typography.bodySmall,
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
}

@Composable
private fun ArtistsTabContent(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit
) {
    if (artists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron artistas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(artists) { artist ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!artist.coverArtUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artist.coverArtUrl,
                                    contentDescription = artist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${artist.songCount} canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
