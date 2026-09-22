package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Playlist
import com.example.data.PlaylistWithSongs
import com.example.data.Song

@Composable
fun PlaylistsTabContent(
    playlistsWithSongs: List<PlaylistWithSongs>,
    allSongs: List<Song>,
    selectedPlaylistId: Long?,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddSongsToPlaylist: (Long, List<String>) -> Unit,
    onRemoveSongFromPlaylist: (Long, String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit,
    onShufflePlaylist: (List<Song>) -> Unit,
    onOpenAddToPlaylist: (Song) -> Unit,
    onPlayNextSong: ((Song) -> Unit)? = null,
    onPlayNextPlaylist: ((List<Song>) -> Unit)? = null,
    onLoopPlaylist: ((List<Song>) -> Unit)? = null,
    onPlayShuffleSong: ((Song) -> Unit)? = null,
    onPlayLoopSong: ((Song) -> Unit)? = null
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showAddSongsDialog by remember { mutableStateOf(false) }

    val currentPlaylistWithSongs = playlistsWithSongs.firstOrNull { it.playlist.id == selectedPlaylistId }

    if (selectedPlaylistId != null && currentPlaylistWithSongs != null) {
        // --- DETAILED PLAYLIST VIEW ---
        PlaylistDetailView(
            playlistWithSongs = currentPlaylistWithSongs,
            onBack = { onSelectPlaylist(null) },
            onPlayAll = { onPlayPlaylist(currentPlaylistWithSongs.songs) },
            onShuffleAll = { onShufflePlaylist(currentPlaylistWithSongs.songs) },
            onOpenAddSongs = { showAddSongsDialog = true },
            onRename = { renameTargetPlaylist = currentPlaylistWithSongs.playlist },
            onDelete = {
                onDeletePlaylist(currentPlaylistWithSongs.playlist.id)
                onSelectPlaylist(null)
            },
            onPlaySong = { song -> onPlaySong(song, currentPlaylistWithSongs.songs) },
            onRemoveSong = { songId -> onRemoveSongFromPlaylist(currentPlaylistWithSongs.playlist.id, songId) },
            onPlayNextSong = onPlayNextSong,
            onPlayNextPlaylist = onPlayNextPlaylist,
            onLoopAll = if (onLoopPlaylist != null) { { onLoopPlaylist(currentPlaylistWithSongs.songs) } } else null,
            onPlayShuffleSong = onPlayShuffleSong,
            onPlayLoopSong = onPlayLoopSong
        )
    } else {
        // --- PLAYLISTS OVERVIEW LIST ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Listas de reproducción",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${playlistsWithSongs.size} listas creadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("create_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crear lista", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (playlistsWithSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No hay listas creadas",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Crea tu primera lista para organizar tus pistas descargadas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                items(playlistsWithSongs, key = { it.playlist.id }) { item ->
                    PlaylistCardItem(
                        item = item,
                        onClick = { onSelectPlaylist(item.playlist.id) },
                        onPlay = { onPlayPlaylist(item.songs) },
                        onRename = { renameTargetPlaylist = item.playlist },
                        onDelete = { onDeletePlaylist(item.playlist.id) },
                        onPlayNext = if (onPlayNextPlaylist != null && item.songs.isNotEmpty()) {
                            { onPlayNextPlaylist(item.songs) }
                        } else null,
                        onShuffle = if (item.songs.isNotEmpty()) {
                            { onShufflePlaylist(item.songs) }
                        } else null,
                        onLoop = if (onLoopPlaylist != null && item.songs.isNotEmpty()) {
                            { onLoopPlaylist(item.songs) }
                        } else null
                    )
                }
            }
        }
    }

    // --- CREATE PLAYLIST DIALOG ---
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreateDialog = false
            }
        )
    }

    // --- RENAME PLAYLIST DIALOG ---
    if (renameTargetPlaylist != null) {
        RenamePlaylistDialog(
            currentName = renameTargetPlaylist!!.name,
            onDismiss = { renameTargetPlaylist = null },
            onConfirm = { newName ->
                onRenamePlaylist(renameTargetPlaylist!!.id, newName)
                renameTargetPlaylist = null
            }
        )
    }

    // --- ADD SONGS TO PLAYLIST DIALOG ---
    if (showAddSongsDialog && currentPlaylistWithSongs != null) {
        AddSongsToPlaylistDialog(
            allSongs = allSongs,
            existingSongIds = currentPlaylistWithSongs.songs.map { it.id }.toSet(),
            onDismiss = { showAddSongsDialog = false },
            onAddSongs = { selectedIds ->
                onAddSongsToPlaylist(currentPlaylistWithSongs.playlist.id, selectedIds)
                showAddSongsDialog = false
            }
        )
    }
}

@Composable
fun PlaylistCardItem(
    item: PlaylistWithSongs,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onLoop: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val coverUrl = item.playlist.coverArtUrl ?: item.songs.firstOrNull { !it.coverArtUrl.isNullOrBlank() }?.coverArtUrl

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("playlist_card_${item.playlist.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Portada de lista",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.playlist.description.isNotBlank()) {
                    Text(
                        text = item.playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${item.songs.size} canciones",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Play action
            if (item.songs.isNotEmpty()) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir lista",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 3-dots Menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Reproducir a continuación") },
                            leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                    }
                    if (onShuffle != null) {
                        DropdownMenuItem(
                            text = { Text("Reproducir aleatoriamente") },
                            leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                menuExpanded = false
                                onShuffle()
                            }
                        )
                    }
                    if (onLoop != null) {
                        DropdownMenuItem(
                            text = { Text("Reproducir en bucle") },
                            leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                menuExpanded = false
                                onLoop()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar lista") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlistWithSongs: PlaylistWithSongs,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onOpenAddSongs: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onPlayNextSong: ((Song) -> Unit)? = null,
    onPlayNextPlaylist: ((List<Song>) -> Unit)? = null,
    onLoopAll: (() -> Unit)? = null,
    onPlayShuffleSong: ((Song) -> Unit)? = null,
    onPlayLoopSong: ((Song) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // Header with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlistWithSongs.playlist.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playlistWithSongs.playlist.description.isNotBlank()) {
                        Text(
                            text = playlistWithSongs.playlist.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${playlistWithSongs.songs.size} pistas asociadas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (onPlayNextPlaylist != null && playlistWithSongs.songs.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Reproducir a continuación") },
                                leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNextPlaylist(playlistWithSongs.songs)
                                }
                            )
                        }
                        if (playlistWithSongs.songs.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Reproducir aleatoriamente") },
                                leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    menuExpanded = false
                                    onShuffleAll()
                                }
                            )
                        }
                        if (onLoopAll != null && playlistWithSongs.songs.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Reproducir en bucle") },
                                leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    menuExpanded = false
                                    onLoopAll()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar lista") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = playlistWithSongs.songs.isNotEmpty()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reproducir", fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = onShuffleAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = playlistWithSongs.songs.isNotEmpty()
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aleatorio", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onOpenAddSongs,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir", fontSize = 13.sp)
                }
            }
        }

        // Songs list
        if (playlistWithSongs.songs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Esta lista aún no tiene canciones",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenAddSongs,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Añadir pistas ahora")
                        }
                    }
                }
            }
        } else {
            items(playlistWithSongs.songs, key = { it.id }) { song ->
                PlaylistSongRow(
                    song = song,
                    onClick = { onPlaySong(song) },
                    onRemove = { onRemoveSong(song.id) },
                    onPlayNext = if (onPlayNextSong != null) { { onPlayNextSong(song) } } else null,
                    onPlayShuffle = if (onPlayShuffleSong != null) { { onPlayShuffleSong(song) } } else null,
                    onPlayLoop = if (onPlayLoopSong != null) { { onPlayLoopSong(song) } } else null
                )
            }
        }
    }
}

@Composable
fun PlaylistSongRow(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onPlayShuffle: (() -> Unit)? = null,
    onPlayLoop: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!song.coverArtUrl.isNullOrBlank()) {
                AsyncImage(
                    model = song.coverArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${formatDuration(song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Reproducir") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    }
                )
                if (onPlayNext != null) {
                    DropdownMenuItem(
                        text = { Text("Reproducir a continuación") },
                        leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                }
                if (onPlayShuffle != null) {
                    DropdownMenuItem(
                        text = { Text("Reproducir aleatoriamente") },
                        leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            menuExpanded = false
                            onPlayShuffle()
                        }
                    )
                }
                if (onPlayLoop != null) {
                    DropdownMenuItem(
                        text = { Text("Reproducir en bucle") },
                        leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            menuExpanded = false
                            onPlayLoop()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Quitar de la lista") },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}

// --- DIALOGS ---

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Nueva lista de reproducción", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la lista") },
                    placeholder = { Text("Ej. Favoritos Rock, Relax...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_playlist")
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar lista", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nuevo nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AddSongsToPlaylistDialog(
    allSongs: List<Song>,
    existingSongIds: Set<String>,
    onDismiss: () -> Unit,
    onAddSongs: (List<String>) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    val availableSongs = remember(allSongs, existingSongIds) {
        allSongs.filter { it.id !in existingSongIds }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir pistas a la lista", fontWeight = FontWeight.Bold) },
        text = {
            if (availableSongs.isEmpty()) {
                Text(
                    text = "Todas las canciones de tu biblioteca ya están en esta lista.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableSongs, key = { it.id }) { song ->
                        val isChecked = selectedIds.contains(song.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isChecked) selectedIds.remove(song.id)
                                    else selectedIds.add(song.id)
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(song.id)
                                    else selectedIds.remove(song.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
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
        },
        confirmButton = {
            Button(
                onClick = { onAddSongs(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Añadir (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AddToPlaylistSelectionDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Añadir a lista",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "«${song.title}»",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text(
                        text = "No tienes listas de reproducción creadas aún.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onSelectPlaylist(playlist.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreateNewPlaylist) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nueva lista")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
