package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.ui.components.CategoryCard
import com.example.ui.components.FileListItem
import com.example.viewmodel.UiState

@Composable
fun CategoriesScreen(
    uiState: UiState,
    selectedCategory: FileCategory?,
    categoryFiles: List<FileItem>,
    onSelectCategory: (FileCategory?) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onPropertiesFile: (FileItem) -> Unit,
    onToggleBookmark: (FileItem) -> Unit,
    onExtractFile: (FileItem) -> Unit,
    onCompressFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("categories_screen")
    ) {
        if (selectedCategory == null) {
            // All Categories Grid
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "File Categories",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                val categories = listOf(
                    FileCategory.IMAGES,
                    FileCategory.VIDEOS,
                    FileCategory.AUDIO,
                    FileCategory.DOCUMENTS,
                    FileCategory.APKS,
                    FileCategory.DOWNLOADS,
                    FileCategory.ARCHIVES
                )

                items(categories.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryCard(
                                category = pair[0],
                                itemCount = uiState.storageBreakdown.categoryCounts[pair[0]] ?: 0,
                                onClick = { onSelectCategory(pair[0]) }
                            )
                        }
                        if (pair.size > 1) {
                            Box(modifier = Modifier.weight(1f)) {
                                CategoryCard(
                                    category = pair[1],
                                    itemCount = uiState.storageBreakdown.categoryCounts[pair[1]] ?: 0,
                                    onClick = { onSelectCategory(pair[1]) }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        } else {
            // Category Details List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSelectCategory(null) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to categories"
                            )
                        }
                        Text(
                            text = "${selectedCategory.title} (${categoryFiles.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (categoryFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No ${selectedCategory.title.lowercase()} files found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(categoryFiles, key = { "cat_${it.path}" }) { item ->
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

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}
