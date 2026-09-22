package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MusicStatistics

@Composable
fun StatsDialog(
    stats: MusicStatistics,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Estadísticas (musica.py)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Resumen del catálogo y base de datos local",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatItemRow(
                    icon = Icons.Default.MusicNote,
                    label = "Total Canciones",
                    value = "${stats.totalSongs}",
                    color = MaterialTheme.colorScheme.primary
                )

                StatItemRow(
                    icon = Icons.Default.Favorite,
                    label = "Canciones Favoritas",
                    value = "${stats.totalFavorites}",
                    color = Color(0xFFEF4444)
                )

                StatItemRow(
                    icon = Icons.Default.CloudDownload,
                    label = "Descargadas de YouTube",
                    value = "${stats.totalDownloadedYt}",
                    color = MaterialTheme.colorScheme.secondary
                )

                StatItemRow(
                    icon = Icons.Default.AutoAwesome,
                    label = "Enriquecidas (MusicBrainz)",
                    value = "${stats.enrichedCount}",
                    color = Color(0xFF10B981)
                )

                StatItemRow(
                    icon = Icons.Default.Person,
                    label = "Artistas Únicos",
                    value = "${stats.totalArtists}",
                    color = Color(0xFF3B82F6)
                )

                StatItemRow(
                    icon = Icons.Default.Album,
                    label = "Álbumes Únicos",
                    value = "${stats.totalAlbums}",
                    color = Color(0xFF8B5CF6)
                )

                StatItemRow(
                    icon = Icons.Default.Timer,
                    label = "Tiempo Total",
                    value = "${stats.totalDurationSeconds / 60} minutos",
                    color = Color(0xFFF59E0B)
                )

                val mbSize = (stats.totalSizeBytes / (1024 * 1024)).coerceAtLeast(1)
                StatItemRow(
                    icon = Icons.Default.Folder,
                    label = "Almacenamiento Local",
                    value = "$mbSize MB",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Entendido")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun StatItemRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
