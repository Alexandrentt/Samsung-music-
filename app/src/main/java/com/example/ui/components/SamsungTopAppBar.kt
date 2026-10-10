package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamsungTopAppBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onOpenSoundAlive: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    sleepTimerRemainingMs: Long? = null,
    onShowStats: () -> Unit,
    onCleanFolder: () -> Unit,
    onRescanSongs: () -> Unit,
    onSyncMissingSongs: () -> Unit,
    onEnrichAll: () -> Unit,
    onExportCsv: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Buscar en la biblioteca...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )
            }
            // Sin letrero: el título queda vacío cuando no hay búsqueda activa.
        },
        actions = {
            if (isSearchActive) {
                IconButton(onClick = { onToggleSearch(false) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar búsqueda",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                IconButton(onClick = { onToggleSearch(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar canciones",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Acción directa: reescaneo visible sin tener que abrir el menú.
                IconButton(onClick = onSyncMissingSongs) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Buscar y descargar canciones faltantes",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = onRescanSongs) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reescanear biblioteca de canciones",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = onOpenSleepTimer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Temporizador de apagado",
                            tint = if (sleepTimerRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                        if (sleepTimerRemainingMs != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                IconButton(onClick = onOpenSoundAlive) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_equalizer),
                        contentDescription = "SoundAlive Ecualizador",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(if (sleepTimerRemainingMs != null) "Temporizador (activo)" else "Temporizador de apagado")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (sleepTimerRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenSleepTimer()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Estadísticas de la biblioteca") },
                        onClick = {
                            showMenu = false
                            onShowStats()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Enriquecer metadatos (MusicBrainz)") },
                        onClick = {
                            showMenu = false
                            onEnrichAll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reescanear biblioteca ahora") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRescanSongs()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Limpiar archivos y nombres") },
                        onClick = {
                            showMenu = false
                            onCleanFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar historial a CSV") },
                        onClick = {
                            showMenu = false
                            onExportCsv()
                        }
                    )
                }
            }
        }
    )
}
