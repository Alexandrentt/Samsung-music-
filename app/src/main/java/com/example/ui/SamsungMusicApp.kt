package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.widget.Toast
import com.example.data.Song
import com.example.player.CrashHandler
import com.example.ui.components.CoverArtEditorDialog
import com.example.ui.components.DownloadTabContent
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NowPlayingSheet
import com.example.ui.components.PlaylistsTabContent
import com.example.ui.components.SamsungTabs
import com.example.ui.components.SamsungTopAppBar
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.SongGridItem
import com.example.ui.components.SongItemRow
import com.example.ui.components.SoundAliveDialog
import com.example.ui.components.StatsDialog

@Composable
fun SamsungMusicApp(viewModel: SamsungMusicViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
    val isDuckingEnabled by viewModel.isDuckingEnabled.collectAsState()
    val pauseOnOtherMedia by viewModel.pauseOnOtherMedia.collectAsState()
    val isOtherAppPlaying by viewModel.isOtherAppPlaying.collectAsState()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val sleepTimerPauseAtEndOfSong by viewModel.sleepTimerPauseAtEndOfSong.collectAsState()
    val showSleepTimerDialog by viewModel.showSleepTimerDialog.collectAsState()
    val songForCoverEdit by viewModel.songForCoverEdit.collectAsState()
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsState()
    val songSortOrder by viewModel.songSortOrder.collectAsState()
    val songViewMode by viewModel.songViewMode.collectAsState()
    val youtubeSearchQuery by viewModel.youtubeSearchQuery.collectAsState()
    val isSearchingYouTube by viewModel.isSearchingYouTube.collectAsState()
    val youtubeSearchResults by viewModel.youtubeSearchResults.collectAsState()
    val localSearchResults by viewModel.localSearchResults.collectAsState()

    // Diagnóstico: registro de errores (crash log) y estado del reescaneo
    var showCrashLogDialog by remember { mutableStateOf(false) }
    var crashLogContent by remember { mutableStateOf<String?>(null) }
    var isRescanning by remember { mutableStateOf(false) }
    var rescanResult by remember { mutableStateOf<String?>(null) }

    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val currentPositionMs by viewModel.playerManager.currentPositionMs.collectAsState()
    val durationMs by viewModel.playerManager.durationMs.collectAsState()
    val isShuffle by viewModel.playerManager.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsState()
    val lyrics by viewModel.playerManager.currentLyrics.collectAsState()
    val activeLyricIndex by viewModel.playerManager.activeLyricIndex.collectAsState()
    val currentQueue by viewModel.playerManager.queue.collectAsState()

    // Estado para personalización del orden de canciones al mantener presionada una canción
    var songToReorder by remember { mutableStateOf<Song?>(null) }
    var isQuickReorderingMode by remember { mutableStateOf(false) }

    // Editor de datos (título/artista/álbum) y selector de carpeta de música
    var songToEditInfo by remember { mutableStateOf<Song?>(null) }
    var showFolderPickerHelp by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.setMusicFolderUri(uri.toString())
        }
    }

    // Pager para deslizar entre pestañas con el táctil
    val tabs = remember { SamsungTab.values() }
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(selectedTab).coerceAtLeast(0)
    ) { tabs.size }

    // Sincronización Pager -> ViewModel
    LaunchedEffect(pagerState.currentPage) {
        val currentTab = tabs[pagerState.currentPage]
        if (currentTab != selectedTab) {
            viewModel.selectTab(currentTab)
        }
    }

    // Sincronización ViewModel -> Pager
    LaunchedEffect(selectedTab) {
        val targetIdx = tabs.indexOf(selectedTab)
        if (targetIdx >= 0 && targetIdx != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetIdx)
        }
    }

    Scaffold(
        topBar = {
            SamsungTopAppBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onToggleSearch = { viewModel.toggleSearchActive(it) },
                onOpenSoundAlive = { viewModel.openSoundAliveDialog() },
                onOpenSleepTimer = { viewModel.openSleepTimerDialog() },
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                onShowStats = { viewModel.showStatistics() },
                onCleanFolder = { viewModel.cleanMusicFolder() },
                onEnrichAll = { viewModel.enrichAllSongs(force = true) },
                onExportCsv = { viewModel.exportToCsv(context) },
                onShowCrashLog = { showCrashLogDialog = true },
                onRescanSongs = {
                    if (!isRescanning) {
                        isRescanning = true
                        viewModel.rescanSongs { summary ->
                            isRescanning = false
                            rescanResult = summary
                        }
                    }
                },
                onPickMusicFolder = { showFolderPickerHelp = true }
            )
        },
        bottomBar = {
            val fallbackSong = remember(filteredSongs, favoriteSongs) {
                filteredSongs.firstOrNull() ?: favoriteSongs.firstOrNull()
            }
            MiniPlayerBar(
                currentSong = currentSong,
                defaultSong = fallbackSong,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onTogglePlayPause = {
                    if (currentSong == null && fallbackSong != null) {
                        viewModel.playSong(fallbackSong, filteredSongs.ifEmpty { listOf(fallbackSong) })
                    } else {
                        viewModel.playerManager.togglePlayPause()
                    }
                },
                onSkipNext = {
                    if (currentSong == null && fallbackSong != null) {
                        viewModel.playSong(fallbackSong, filteredSongs)
                    } else {
                        viewModel.playerManager.skipToNext()
                    }
                },
                onSkipPrevious = {
                    if (currentSong == null && fallbackSong != null) {
                        viewModel.playSong(fallbackSong, filteredSongs)
                    } else {
                        viewModel.playerManager.skipToPrevious()
                    }
                },
                onOpenPlayer = {
                    if (currentSong == null && fallbackSong != null) {
                        viewModel.playSong(fallbackSong, filteredSongs)
                    }
                    viewModel.openNowPlayingSheet()
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SamsungTabs(
                selectedTab = tabs[pagerState.currentPage],
                onTabSelected = { tab ->
                    val idx = tabs.indexOf(tab)
                    if (idx >= 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(idx)
                        }
                    }
                }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (tabs[page]) {
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
                            onEditCover = { viewModel.openCoverArtEditor(it) },
                            onEditInfo = { songToEditInfo = it },
                            onDeleteSong = { viewModel.deleteSong(it) },
                            onSongLongClick = { songToReorder = it },
                            onMoveSong = { id, offset -> viewModel.moveSong(id, offset) },
                            isReorderingMode = isQuickReorderingMode,
                            onToggleReorderingMode = { isQuickReorderingMode = !isQuickReorderingMode }
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
                            onEditCover = { viewModel.openCoverArtEditor(it) },
                            onEditInfo = { songToEditInfo = it },
                            onDeleteSong = { viewModel.deleteSong(it) },
                            onSongLongClick = { songToReorder = it },
                            onMoveSong = { id, offset -> viewModel.moveSong(id, offset) },
                            isReorderingMode = isQuickReorderingMode,
                            onToggleReorderingMode = { isQuickReorderingMode = !isQuickReorderingMode },
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
                            onEditCover = { viewModel.openCoverArtEditor(it) },
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
    }

    // Now Playing Modal Sheet con botón para ver la lista de reproducción actual
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
            currentQueue = currentQueue,
            onDismiss = { viewModel.closeNowPlayingSheet() },
            onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
            onSkipNext = { viewModel.playerManager.skipToNext() },
            onSkipPrevious = { viewModel.playerManager.skipToPrevious() },
            onSeek = { viewModel.playerManager.seekTo(it) },
            onSeekLyric = { viewModel.playerManager.seekToLyricLine(it) },
            onToggleShuffle = { viewModel.toggleShuffleWithFeedback() },
            onCycleRepeat = { viewModel.cycleRepeatModeWithFeedback() },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onOpenSoundAlive = { viewModel.openSoundAliveDialog() },
            onOpenSleepTimer = { viewModel.openSleepTimerDialog() },
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            onEditCover = { viewModel.openCoverArtEditor(it) },
            onSelectSongFromQueue = { song -> viewModel.playerManager.playSong(song, currentQueue) },
            onRemoveFromQueue = { songId -> viewModel.removeFromQueue(songId) },
            onReorderQueue = { fromIdx, toIdx -> viewModel.playerManager.reorderQueue(fromIdx, toIdx) }
        )
    }

    // Diálogo del temporizador de apagado
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = sleepTimerRemainingMs,
            pauseAtEndOfSong = sleepTimerPauseAtEndOfSong,
            onSetTimer = { minutes, pauseAtEnd ->
                viewModel.setSleepTimer(minutes, pauseAtEnd)
            },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onAddMinutes = { viewModel.addSleepTimerMinutes(it) },
            onDismiss = { viewModel.closeSleepTimerDialog() }
        )
    }

    // Diálogo del editor de portadas (Navegador web / Galería / URL)
    songForCoverEdit?.let { song ->
        CoverArtEditorDialog(
            song = song,
            onSaveCover = { songId, path ->
                viewModel.updateSongCoverArt(songId, path)
            },
            onDismiss = { viewModel.closeCoverArtEditor() }
        )
    }

    // Diálogo para personalizar orden al mantener una canción presionada
    songToReorder?.let { song ->
        AlertDialog(
            onDismissRequest = { songToReorder = null },
            title = {
                Text(
                    text = "Personalizar orden",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\"${song.title}\" - ${song.artist}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveSong(song.id, -9999)
                                songToReorder = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignTop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Mover al inicio de la lista",
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveSong(song.id, -1)
                                songToReorder = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Subir una posición",
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveSong(song.id, 1)
                                songToReorder = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Bajar una posición",
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveSong(song.id, 9999)
                                songToReorder = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Mover al final de la lista",
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isQuickReorderingMode = true
                                songToReorder = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Activar botones de reorganización rápida",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { songToReorder = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // SoundAlive Equalizer Dialog
    if (showSoundAlive) {
        SoundAliveDialog(
            onDismiss = { viewModel.closeSoundAliveDialog() },
            isDuckingEnabled = isDuckingEnabled,
            onToggleDucking = { viewModel.setDuckingEnabled(it) },
            pauseOnOtherMedia = pauseOnOtherMedia,
            onTogglePauseOnOtherMedia = { viewModel.setPauseOnOtherMedia(it) },
            isOtherAppPlaying = isOtherAppPlaying
        )
    }

    // Stats Dialog
    statsData?.let { stats ->
        StatsDialog(stats = stats, onDismiss = { viewModel.dismissStatsDialog() })
    }

    // Diálogo: Registro de errores (crash log)
    if (showCrashLogDialog) {
        crashLogContent = remember(showCrashLogDialog) { CrashHandler.readLog(context) }
        AlertDialog(
            onDismissRequest = { showCrashLogDialog = false },
            title = { Text("Registro de errores", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    val content = crashLogContent
                    if (content == null) {
                        Text(
                            "No hay errores registrados. \uD83C\uDF89",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Últimos errores detectados (stack traces):",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.small
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = content,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (crashLogContent != null) {
                    TextButton(onClick = {
                        try {
                            CrashHandler.shareIntent(context)?.let { intent ->
                                context.startActivity(Intent.createChooser(intent, "Compartir registro de errores"))
                            }
                        } catch (_: Exception) {
                        }
                    }) {
                        Text("Compartir")
                    }
                }
            },
            dismissButton = {
                Row {
                    if (crashLogContent != null) {
                        TextButton(onClick = {
                            CrashHandler.clearLogFile(context)
                            crashLogContent = null
                        }) {
                            Text("Borrar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showCrashLogDialog = false }) {
                        Text("Cerrar")
                    }
                }
            }
        )
    }

    // Toast de resultado del reescaneo
    rescanResult?.let { summary ->
        LaunchedEffect(summary) {
            try {
                android.widget.Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
            }
            rescanResult = null
        }
    }

    // Diálogo: editar datos de la canción (título/artista/álbum)
    songToEditInfo?.let { song ->
        var editTitle by remember(song.id) { mutableStateOf(song.title) }
        var editArtist by remember(song.id) { mutableStateOf(song.artist) }
        var editAlbum by remember(song.id) { mutableStateOf(song.album) }
        AlertDialog(
            onDismissRequest = { songToEditInfo = null },
            title = { Text("Editar datos", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text("Álbum") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateSongInfo(song.id, editTitle, editArtist, editAlbum)
                    songToEditInfo = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { songToEditInfo = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo: ayuda + selector de carpeta de música (estilo Samsung Music)
    if (showFolderPickerHelp) {
        AlertDialog(
            onDismissRequest = { showFolderPickerHelp = false },
            title = { Text("Carpeta de música", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(
                        "Elige de qué carpeta del teléfono se lee tu música. " +
                                "Se escanean esa carpeta y sus subcarpetas (hasta 2 niveles): " +
                                "Música, Descargas, WhatsApp o la que prefieras.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val current = viewModel.userFolderUri()
                    Text(
                        if (current != null) "Actual: carpeta personalizada (puedes quitarla)."
                        else "Actual: /Music/SamsungMusic (descargas de la app).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFolderPickerHelp = false
                    try {
                        folderPicker.launch(null)
                    } catch (_: Exception) {
                    }
                }) { Text("Elegir carpeta…") }
            },
            dismissButton = {
                Row {
                    if (viewModel.userFolderUri() != null) {
                        TextButton(onClick = {
                            viewModel.clearMusicFolder()
                            showFolderPickerHelp = false
                        }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { showFolderPickerHelp = false }) { Text("Cerrar") }
                }
            }
        )
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

/** Etiqueta corta para el chip de orden ("Fecha de adición" no cabe). */
private fun shortSortLabel(field: SongSortField): String = when (field) {
    SongSortField.CUSTOM -> "Orden"
    SongSortField.TITLE -> "Título"
    SongSortField.ARTIST -> "Artista"
    SongSortField.ALBUM -> "Álbum"
    SongSortField.DATE_ADDED -> "Fecha"
    SongSortField.DURATION -> "Duración"
    SongSortField.PLAY_COUNT -> "Reprod."
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
    onEditCover: (Song) -> Unit = {},
    onEditInfo: ((Song) -> Unit)? = null,
    onDeleteSong: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit = {},
    onMoveSong: (songId: String, offset: Int) -> Unit = { _, _ -> },
    isReorderingMode: Boolean = false,
    onToggleReorderingMode: () -> Unit = {},
    emptyMessage: String = "No hay canciones disponibles."
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar: shuffle | count | sort chip + direction | view mode | reorder toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Reorder toggle button
            IconButton(onClick = onToggleReorderingMode) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Personalizar orden",
                    tint = if (isReorderingMode || sortOrder.field == SongSortField.CUSTOM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Sort field chip with menu (etiqueta corta: "Fecha" no "Fecha de adición")
            FilterChip(
                selected = true,
                onClick = { showSortMenu = true },
                label = {
                    Text(
                        shortSortLabel(sortOrder.field),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                },
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
                        onEditCover = { onEditCover(song) },
                        onEditInfo = onEditInfo?.let { fn -> { fn(song) } },
                        onDelete = { onDeleteSong(song) },
                        onLongClick = { onSongLongClick(song) },
                        onMoveUp = { onMoveSong(song.id, -1) },
                        onMoveDown = { onMoveSong(song.id, 1) },
                        onMoveToTop = { onMoveSong(song.id, -9999) },
                        onMoveToBottom = { onMoveSong(song.id, 9999) },
                        showReorderControls = isReorderingMode
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
                        onToggleFavorite = { onToggleFavorite(song) },
                        onEditCover = { onEditCover(song) }
                    )
                }
            }
        }
    }
}
