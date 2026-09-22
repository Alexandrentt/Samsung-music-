package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.SamsungTab

@Composable
fun SamsungTopAppBar(
    currentTab: SamsungTab,
    songCount: Int,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onShowStats: () -> Unit,
    onEnrichAll: (force: Boolean) -> Unit,
    onCleanFolder: () -> Unit,
    onExportCsv: () -> Unit,
    onOpenSoundAlive: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isSearchActive) {
                // One UI Header Branding
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Samsung Music Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Samsung Music",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = when (currentTab) {
                                SamsungTab.CANCIONES -> "$songCount canciones en el dispositivo"
                                SamsungTab.PLAYLISTS -> "Listas de reproducción personalizadas"
                                SamsungTab.ALBUMES -> "Organizado por álbumes"
                                SamsungTab.ARTISTAS -> "Colección por artistas"
                                SamsungTab.FAVORITOS -> "Tus pistas favoritas"
                                SamsungTab.DESCARGAS_YT -> "Gestor de YouTube y Playlists"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Search Icon
                IconButton(
                    onClick = { onToggleSearch(true) },
                    modifier = Modifier.testTag("search_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar canciones",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                // SoundAlive EQ shortcut
                IconButton(
                    onClick = onOpenSoundAlive,
                    modifier = Modifier.testTag("soundalive_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "SoundAlive Efectos",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // More Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("menu_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones avanzadas",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Estadísticas de música") },
                            leadingIcon = {
                                Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                onShowStats()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Enriquecer metadata (MusicBrainz)") },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                onEnrichAll(false)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Forzar re-enriquecimiento total") },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            },
                            onClick = {
                                menuExpanded = false
                                onEnrichAll(true)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Limpiar carpeta (nombres / duplicados)") },
                            leadingIcon = {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                onCleanFolder()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Exportar lista a CSV") },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                onExportCsv()
                            }
                        )
                    }
                }
            } else {
                // Expanded Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_text_field"),
                    placeholder = { Text("Buscar por título, artista o álbum...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            } else {
                                onToggleSearch(false)
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}
