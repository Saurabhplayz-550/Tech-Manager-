package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SortField
import com.example.model.SortOrder
import com.example.model.ViewMode
import com.example.ui.theme.TechBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    title: String,
    canNavigateBack: Boolean,
    isSelectionMode: Boolean,
    selectedCount: Int,
    viewMode: ViewMode,
    onNavigateBack: () -> Unit,
    onClearSelection: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSortSelected: (SortField, SortOrder) -> Unit,
    onSelectAll: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onMenuClick: () -> Unit,
    onStartSelection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    if (isSelectionMode) {
        TopAppBar(
            title = {
                Text(
                    text = "Selected: $selectedCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = onCopySelected) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onCutSelected) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Cut",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onShareSelected) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onCompressSelected) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = "Compress",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "Select All",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TechBluePrimary),
            modifier = modifier.testTag("selection_top_bar")
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (canNavigateBack) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                } else {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.testTag("search_action_button")
                    )
                }
                IconButton(onClick = onToggleViewMode) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.LIST) Icons.Default.ViewModule else Icons.Default.ViewList,
                        contentDescription = "Toggle view mode"
                    )
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Sort options"
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by Name") },
                            onClick = {
                                showSortMenu = false
                                onSortSelected(SortField.NAME, SortOrder.ASCENDING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Added date (Newest)") },
                            onClick = {
                                showSortMenu = false
                                onSortSelected(SortField.ADDED_DATE, SortOrder.DESCENDING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Date modified (Newest)") },
                            onClick = {
                                showSortMenu = false
                                onSortSelected(SortField.MODIFIED_DATE, SortOrder.DESCENDING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Size (Largest)") },
                            onClick = {
                                showSortMenu = false
                                onSortSelected(SortField.SIZE, SortOrder.DESCENDING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Type") },
                            onClick = {
                                showSortMenu = false
                                onSortSelected(SortField.TYPE, SortOrder.ASCENDING)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Select items / Compress") },
                            leadingIcon = {
                                Icon(Icons.Default.Checklist, contentDescription = null)
                            },
                            onClick = {
                                showSortMenu = false
                                onStartSelection()
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = modifier.testTag("main_top_bar")
        )
    }
}
