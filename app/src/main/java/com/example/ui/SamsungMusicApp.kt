package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val context = LocalContext.current

    val selectedTab by viewModel.selectedTab.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
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

    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val currentPositionMs by viewModel.playerManager.currentPositionMs.collectAsState()
    val durationMs by viewModel.playerManager.durationMs.collectAsState()
    val isShuffle by viewModel.playerManager.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsState()
    val lyrics by viewModel.playerManager.currentLyrics.collectAsState()
    val activeLyricIndex by viewModel.playerManager.activeLyricIndex.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

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
                        onToggleViewMode = { viewModel.toggleSongViewMode() },
                        onShuffleAll = {
                            if (filteredSongs.isNotEmpty()) {
                                viewModel.playSong(filteredSongs.random(), filteredSongs)
                                viewModel.toggleShuffleWithFeedback()
                            }
                        },
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
                        onToggleViewMode = { viewModel.toggleSongViewMode() },
                        onShuffleAll = {
                            if (favoriteSongs.isNotEmpty()) {
                                viewModel.playSong(favoriteSongs.random(), favoriteSongs)
                                viewModel.toggleShuffleWithFeedback()
                            }
                        },
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

                SamsungTab.ALBUMS -> {
                    AlbumsTabContent(
                        albums = albums,
                        currentSong = currentSong,
                        onAlbumClick = { album ->
                            if (album.songs.isNotEmpty()) {
                                viewModel.playSong(album.songs.first(), album.songs)
                            }
                        }
                    )
                }

                SamsungTab.ARTISTS -> {
                    ArtistsTabContent(
                        artists = artists,
                        onArtistClick = { artist ->
                            if (artist.songs.isNotEmpty()) {
                                viewModel.playSong(artist.songs.first(), artist.songs)
                            }
                        }
                    )
                }

                SamsungTab.DOWNLOAD -> {
                    DownloadTabContent(
                        downloadProgress = downloadProgress,
                        isDownloading = isDownloading,
                        downloadHistory = downloadHistory,
                        youtubeSearchQuery = youtubeSearchQuery,
                        isSearchingYouTube = isSearchingYouTube,
                        youtubeSearchResults = youtubeSearchResults,
                        onYouTubeSearchQueryChange = { viewModel.setYouTubeSearchQuery(it) },
                        onSearchYouTube = { viewModel.searchYouTube(it) },
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${songs.size} canciones",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (songs.isNotEmpty()) {
                    OutlinedButton(onClick = onShuffleAll) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aleatorio", fontSize = 12.sp)
                    }
                }

                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Text(sortOrder.displayName, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SongSortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName) },
                                onClick = {
                                    onSortChange(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleViewMode) {
                    val icon = if (viewMode == SongViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList
                    Icon(imageVector = icon, contentDescription = "Cambiar vista")
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
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongGridItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        onSongClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<AlbumItem>,
    currentSong: Song?,
    onAlbumClick: (AlbumItem) -> Unit
) {
    if (albums.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay álbumes disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            items(albums, key = { it.name }) { album ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album) }
                        .padding(8.dp)
                ) {
                    coil.compose.AsyncImage(
                        model = album.coverArtUrl ?: com.example.R.drawable.ic_launcher_foreground,
                        contentDescription = album.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = album.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.artist} • ${album.songs.size} pistas",
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

@Composable
private fun ArtistsTabContent(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit
) {
    if (artists.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay artistas disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(artists, key = { it.name }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    coil.compose.AsyncImage(
                        model = artist.coverArtUrl ?: com.example.R.drawable.ic_launcher_foreground,
                        contentDescription = artist.name,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${artist.songs.size} canciones",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
