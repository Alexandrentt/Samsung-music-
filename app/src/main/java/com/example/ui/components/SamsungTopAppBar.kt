package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
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
    onShowStats: () -> Unit,
    onCleanFolder: () -> Unit,
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
