package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.StorageBreakdown
import com.example.ui.components.CategoryCard
import com.example.ui.components.FileListItem
import com.example.ui.components.StorageCard
import com.example.ui.theme.TechBluePrimary
import com.example.viewmodel.UiState

@Composable
fun HomeScreen(
    uiState: UiState,
    hasStoragePermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onStorageCardClick: () -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onPropertiesFile: (FileItem) -> Unit,
    onToggleBookmark: (FileItem) -> Unit,
    onExtractFile: (FileItem) -> Unit,
    onCompressFile: (FileItem) -> Unit,
    onSendFilesClick: () -> Unit = {},
    onReceiveFilesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAllRecent by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage Permission Warning Banner if not granted
        if (!hasStoragePermission) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Storage Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Grant file access to browse and manage your real device files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        // Storage Card
        item {
            StorageCard(
                storageBreakdown = uiState.storageBreakdown,
                onClick = onStorageCardClick
            )
        }

        // Fast Share (Wi-Fi / Hotspot) Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TechBluePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = TechBluePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fast Share (Nearby)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Transfer files via Wi-Fi or Hotspot without internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onSendFilesClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onReceiveFilesClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Receive", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Category 2-Column Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryCard(
                            category = FileCategory.IMAGES,
                            itemCount = uiState.storageBreakdown.categoryCounts[FileCategory.IMAGES] ?: 0,
                            onClick = { onCategoryClick(FileCategory.IMAGES) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryCard(
                            category = FileCategory.VIDEOS,
                            itemCount = uiState.storageBreakdown.categoryCounts[FileCategory.VIDEOS] ?: 0,
                            onClick = { onCategoryClick(FileCategory.VIDEOS) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryCard(
                            category = FileCategory.AUDIO,
                            itemCount = uiState.storageBreakdown.categoryCounts[FileCategory.AUDIO] ?: 0,
                            onClick = { onCategoryClick(FileCategory.AUDIO) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryCard(
                            category = FileCategory.DOCUMENTS,
                            itemCount = uiState.storageBreakdown.categoryCounts[FileCategory.DOCUMENTS] ?: 0,
                            onClick = { onCategoryClick(FileCategory.DOCUMENTS) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryCard(
                            category = FileCategory.APKS,
                            itemCount = uiState.storageBreakdown.categoryCounts[FileCategory.APKS] ?: 0,
                            onClick = { onCategoryClick(FileCategory.APKS) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryCard(
                            category = FileCategory.DOWNLOADS,
                            itemCount = uiState.storageBreakdown.categoryCounts[FileCategory.DOWNLOADS] ?: 0,
                            onClick = { onCategoryClick(FileCategory.DOWNLOADS) }
                        )
                    }
                }
            }
        }

        // Section: RECENT FILES (or RECENTLY ADDED)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RECENT FILES",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = { showAllRecent = !showAllRecent }
                ) {
                    Text(
                        text = if (showAllRecent) "Show Less" else "See All",
                        color = TechBluePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        val displayList = if (showAllRecent) uiState.recentFiles else uiState.recentFiles.take(5)

        if (displayList.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "No recent files found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(displayList, key = { "recent_${it.path}" }) { item ->
                FileListItem(
                    item = item,
                    isSelected = false,
                    isSelectionMode = false,
                    showAddedDate = true,
                    showModifiedDate = true,
                    onClick = { onFileClick(item) },
                    onLongClick = { onFileLongClick(item) },
                    onShare = { onShareFile(item) },
                    onRename = { onRenameFile(item) },
                    onDelete = { onDeleteFile(item) },
                    onProperties = { onPropertiesFile(item) },
                    onToggleBookmark = { onToggleBookmark(item) },
                    onExtract = if (item.isArchive) { { onExtractFile(item) } } else null,
                    onCompress = if (!item.isArchive) { { onCompressFile(item) } } else null
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
