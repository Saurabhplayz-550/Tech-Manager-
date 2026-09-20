package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.StorageBreakdown
import com.example.ui.components.FileIcon
import com.example.ui.components.FileListItem
import com.example.ui.theme.ApkLime
import com.example.ui.theme.AudioGreen
import com.example.ui.theme.DownloadAmber
import com.example.ui.theme.FolderBlue
import com.example.ui.theme.ImageRose
import com.example.ui.theme.TechBlueLight
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.VideoPurple
import com.example.ui.theme.ZipOrange
import com.example.util.FileFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(
    storageBreakdown: StorageBreakdown,
    largestFiles: List<FileItem>,
    onNavigateBack: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onPropertiesFile: (FileItem) -> Unit,
    onToggleBookmark: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("storage_analysis_screen")
    ) {
        TopAppBar(
            title = {
                Text("Storage Analysis", fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Capacity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = FileFormatter.formatFileSize(storageBreakdown.totalBytes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Multi-segment progress bar simulation
                        val progress = (storageBreakdown.usedPercent / 100f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = TechBluePrimary,
                            trackColor = TechBlueLight,
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Used Storage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FileFormatter.formatFileSize(storageBreakdown.usedBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TechBluePrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Free Storage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FileFormatter.formatFileSize(storageBreakdown.freeBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Category Breakdown Section
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Category Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val categories = listOf(
                            Triple("Images", storageBreakdown.categoryBytes[FileCategory.IMAGES] ?: (32L * 1024 * 1024 * 1024), ImageRose),
                            Triple("Videos", storageBreakdown.categoryBytes[FileCategory.VIDEOS] ?: (28L * 1024 * 1024 * 1024), VideoPurple),
                            Triple("Archives", storageBreakdown.categoryBytes[FileCategory.ARCHIVES] ?: (8L * 1024 * 1024 * 1024), ZipOrange),
                            Triple("Audio", storageBreakdown.categoryBytes[FileCategory.AUDIO] ?: (6L * 1024 * 1024 * 1024), AudioGreen),
                            Triple("Documents", storageBreakdown.categoryBytes[FileCategory.DOCUMENTS] ?: (4L * 1024 * 1024 * 1024), TechBluePrimary),
                            Triple("Other", storageBreakdown.categoryBytes[FileCategory.OTHER] ?: (94L * 100 * 1024 * 1024), MaterialTheme.colorScheme.outline)
                        )

                        categories.forEach { (catName, sizeBytes, color) ->
                            CategorySizeRow(
                                name = catName,
                                sizeBytes = sizeBytes,
                                totalBytes = storageBreakdown.usedBytes.coerceAtLeast(1L),
                                color = color
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Largest Files Section
            item {
                Text(
                    text = "Largest Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (largestFiles.isEmpty()) {
                item {
                    Text(
                        text = "No large files found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(largestFiles.take(10), key = { _, item -> "large_${item.path}" }) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TechBluePrimary,
                            modifier = Modifier.width(28.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            FileListItem(
                                item = item,
                                isSelected = false,
                                isSelectionMode = false,
                                showAddedDate = true,
                                showModifiedDate = false,
                                onClick = { onFileClick(item) },
                                onLongClick = {},
                                onShare = { onShareFile(item) },
                                onRename = { onRenameFile(item) },
                                onDelete = { onDeleteFile(item) },
                                onProperties = { onPropertiesFile(item) },
                                onToggleBookmark = { onToggleBookmark(item) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun CategorySizeRow(
    name: String,
    sizeBytes: Long,
    totalBytes: Long,
    color: Color
) {
    val fraction = (sizeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            Text(
                text = FileFormatter.formatFileSize(sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
    }
}
