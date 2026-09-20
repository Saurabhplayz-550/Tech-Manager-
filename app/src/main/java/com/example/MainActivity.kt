package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FileItem
import com.example.ui.components.AddCreateSheet
import com.example.ui.components.ArchiveBrowserDialog
import com.example.ui.components.ArchiveDetailsDialog
import com.example.ui.components.CreateArchiveDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.ExtractDialog
import com.example.ui.components.FilePreviewDialog
import com.example.ui.components.FilePropertiesDialog
import com.example.ui.components.MainTopBar
import com.example.ui.components.NewFileDialog
import com.example.ui.components.NewFolderDialog
import com.example.ui.components.OperationProgressDialog
import com.example.ui.components.OperationSuccessDialog
import com.example.ui.components.RenameDialog
import com.example.ui.components.SearchOverlay
import com.example.ui.screens.ArchivesScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.StorageAnalysisScreen
import com.example.ui.theme.TechBlueLight
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.TechManagerTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.NavTab
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TechManagerTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                var hasStoragePermission by remember {
                    mutableStateOf(checkStoragePermission())
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasStoragePermission = checkStoragePermission()
                }

                // Document Picker for Import
                val importFileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    uris.forEach { uri ->
                        viewModel.importFile(uri)
                    }
                }

                // Handle system back button
                BackHandler(enabled = true) {
                    when {
                        uiState.isSearchOpen -> viewModel.closeSearch()
                        uiState.showStorageAnalysisScreen -> viewModel.showStorageAnalysis(false)
                        uiState.showBookmarksScreen -> viewModel.showBookmarks(false)
                        uiState.isSelectionMode -> viewModel.clearSelection()
                        uiState.selectedCategory != null -> viewModel.closeCategory()
                        uiState.currentTab == NavTab.STORAGE && (uiState.breadcrumbs.size > 1) -> {
                            if (!viewModel.navigateUp()) {
                                viewModel.setTab(NavTab.FILES)
                            }
                        }
                        uiState.currentTab != NavTab.FILES -> viewModel.setTab(NavTab.FILES)
                        else -> finish()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (!uiState.showStorageAnalysisScreen && !uiState.isSearchOpen) {
                            val title = when (uiState.currentTab) {
                                NavTab.FILES -> "Tech Manager"
                                NavTab.STORAGE -> uiState.currentDirectory?.name ?: "Internal Storage"
                                NavTab.ARCHIVES -> "Archives"
                                NavTab.CATEGORIES -> if (uiState.selectedCategory != null) uiState.selectedCategory!!.title else "Categories"
                                NavTab.MORE -> "More"
                            }
                            val canBack = (uiState.currentTab == NavTab.STORAGE && (uiState.breadcrumbs.size > 1)) ||
                                    (uiState.currentTab == NavTab.CATEGORIES && uiState.selectedCategory != null)

                            MainTopBar(
                                title = title,
                                canNavigateBack = canBack,
                                isSelectionMode = uiState.isSelectionMode,
                                selectedCount = uiState.selectedFilePaths.size,
                                viewMode = uiState.viewMode,
                                onNavigateBack = {
                                    if (uiState.currentTab == NavTab.CATEGORIES && uiState.selectedCategory != null) {
                                        viewModel.closeCategory()
                                    } else if (uiState.currentTab == NavTab.STORAGE) {
                                        if (!viewModel.navigateUp()) {
                                            viewModel.setTab(NavTab.FILES)
                                        }
                                    }
                                },
                                onClearSelection = { viewModel.clearSelection() },
                                onSearchClick = { viewModel.search("") },
                                onToggleViewMode = { viewModel.toggleViewMode() },
                                onSortSelected = { field, order -> viewModel.setSort(field, order) },
                                onSelectAll = { viewModel.selectAll() },
                                onCopySelected = { viewModel.copySelected() },
                                onCutSelected = { viewModel.cutSelected() },
                                onDeleteSelected = {
                                    val files = uiState.currentFiles.filter { uiState.selectedFilePaths.contains(it.path) }
                                    viewModel.showDeleteDialog(files)
                                },
                                onShareSelected = {
                                    val firstFile = uiState.currentFiles.firstOrNull { uiState.selectedFilePaths.contains(it.path) }
                                    if (firstFile != null) viewModel.shareFile(firstFile)
                                },
                                onCompressSelected = {
                                    val files = uiState.currentFiles.filter { uiState.selectedFilePaths.contains(it.path) }
                                    viewModel.showCompressDialog(files)
                                },
                                onMenuClick = {
                                    viewModel.setTab(NavTab.MORE)
                                },
                                onStartSelection = {
                                    viewModel.startSelectionMode()
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (!uiState.showStorageAnalysisScreen && !uiState.isSearchOpen && !uiState.isSelectionMode) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp,
                                modifier = Modifier.testTag("bottom_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = uiState.currentTab == NavTab.FILES,
                                    onClick = { viewModel.setTab(NavTab.FILES) },
                                    icon = { Icon(imageVector = Icons.Default.Folder, contentDescription = "Files") },
                                    label = { Text("Files") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TechBluePrimary,
                                        selectedTextColor = TechBluePrimary,
                                        indicatorColor = TechBlueLight
                                    ),
                                    modifier = Modifier.testTag("nav_tab_files")
                                )
                                NavigationBarItem(
                                    selected = uiState.currentTab == NavTab.STORAGE,
                                    onClick = { viewModel.setTab(NavTab.STORAGE) },
                                    icon = { Icon(imageVector = Icons.Default.SdStorage, contentDescription = "Internal Storage") },
                                    label = { Text("Storage") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TechBluePrimary,
                                        selectedTextColor = TechBluePrimary,
                                        indicatorColor = TechBlueLight
                                    ),
                                    modifier = Modifier.testTag("nav_tab_storage")
                                )
                                NavigationBarItem(
                                    selected = uiState.currentTab == NavTab.ARCHIVES,
                                    onClick = { viewModel.setTab(NavTab.ARCHIVES) },
                                    icon = { Icon(imageVector = Icons.Default.FolderZip, contentDescription = "Archives") },
                                    label = { Text("Archives") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TechBluePrimary,
                                        selectedTextColor = TechBluePrimary,
                                        indicatorColor = TechBlueLight
                                    ),
                                    modifier = Modifier.testTag("nav_tab_archives")
                                )
                                NavigationBarItem(
                                    selected = uiState.currentTab == NavTab.CATEGORIES,
                                    onClick = { viewModel.setTab(NavTab.CATEGORIES) },
                                    icon = { Icon(imageVector = Icons.Default.Category, contentDescription = "Categories") },
                                    label = { Text("Categories") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TechBluePrimary,
                                        selectedTextColor = TechBluePrimary,
                                        indicatorColor = TechBlueLight
                                    ),
                                    modifier = Modifier.testTag("nav_tab_categories")
                                )
                                NavigationBarItem(
                                    selected = uiState.currentTab == NavTab.MORE,
                                    onClick = { viewModel.setTab(NavTab.MORE) },
                                    icon = { Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "More") },
                                    label = { Text("More") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TechBluePrimary,
                                        selectedTextColor = TechBluePrimary,
                                        indicatorColor = TechBlueLight
                                    ),
                                    modifier = Modifier.testTag("nav_tab_more")
                                )
                            }
                        } else if (uiState.isSelectionMode) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth().testTag("selection_bottom_bar")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            val files = uiState.currentFiles.filter { uiState.selectedFilePaths.contains(it.path) }
                                            if (files.isNotEmpty()) {
                                                viewModel.showCompressDialog(files)
                                            } else {
                                                Toast.makeText(this@MainActivity, "Select at least 1 file to compress", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("selection_compress_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val count = uiState.selectedFilePaths.size
                                        Text(
                                            text = if (count > 0) "Compress to .ZIP ($count)" else "Compress to .ZIP",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { viewModel.copySelected() }) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                                        }
                                        IconButton(onClick = { viewModel.cutSelected() }) {
                                            Icon(imageVector = Icons.Default.ContentCut, contentDescription = "Cut")
                                        }
                                        IconButton(onClick = {
                                            val files = uiState.currentFiles.filter { uiState.selectedFilePaths.contains(it.path) }
                                            if (files.isNotEmpty()) viewModel.showDeleteDialog(files)
                                        }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        val canShowFab = !uiState.showStorageAnalysisScreen &&
                                !uiState.isSearchOpen &&
                                !uiState.isSelectionMode &&
                                (uiState.currentTab == NavTab.FILES || uiState.currentTab == NavTab.STORAGE || uiState.currentTab == NavTab.ARCHIVES)

                        if (canShowFab) {
                            FloatingActionButton(
                                onClick = { viewModel.showAddCreateMenu(true) },
                                containerColor = TechBluePrimary,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                                modifier = Modifier
                                    .testTag("fab_add_create")
                                    .navigationBarsPadding()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add or create"
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Main Tab Views
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Storage Permission Banner if not yet granted
                            if (!hasStoragePermission) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = TechBlueLight
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
                                            tint = TechBluePrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Storage Permission",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Grant permission to manage device storage and archives.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                requestStoragePermission(permissionLauncher)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("GRANT")
                                        }
                                    }
                                }
                            }

                            // Switch between tabs
                            when (uiState.currentTab) {
                                NavTab.FILES -> {
                                    HomeScreen(
                                        uiState = uiState,
                                        onStorageCardClick = {
                                            viewModel.setTab(NavTab.STORAGE)
                                        },
                                        onCategoryClick = { cat ->
                                            viewModel.openCategory(cat)
                                        },
                                        onFileClick = { item -> handleFileClick(item, viewModel) },
                                        onFileLongClick = { item ->
                                            viewModel.setTab(NavTab.STORAGE)
                                            viewModel.toggleSelection(item.path)
                                        },
                                        onShareFile = { item -> viewModel.shareFile(item) },
                                        onRenameFile = { item -> viewModel.showRenameDialog(item) },
                                        onDeleteFile = { item -> viewModel.showDeleteDialog(listOf(item)) },
                                        onPropertiesFile = { item -> viewModel.showPropertiesDialog(item) },
                                        onToggleBookmark = { item -> viewModel.toggleBookmark(item) },
                                        onExtractFile = { item -> viewModel.showExtractDialog(item) },
                                        onCompressFile = { item -> viewModel.showCompressDialog(listOf(item)) }
                                    )
                                }
                                NavTab.STORAGE -> {
                                    FileBrowserScreen(
                                        uiState = uiState,
                                        onNavigateTo = { dir -> viewModel.navigateTo(dir) },
                                        onFileClick = { item -> handleFileClick(item, viewModel) },
                                        onFileLongClick = { item -> viewModel.toggleSelection(item.path) },
                                        onShareFile = { item -> viewModel.shareFile(item) },
                                        onRenameFile = { item -> viewModel.showRenameDialog(item) },
                                        onDeleteFile = { item -> viewModel.showDeleteDialog(listOf(item)) },
                                        onPropertiesFile = { item -> viewModel.showPropertiesDialog(item) },
                                        onToggleBookmark = { item -> viewModel.toggleBookmark(item) },
                                        onExtractFile = { item -> viewModel.showExtractDialog(item) },
                                        onCompressFile = { item -> viewModel.showCompressDialog(listOf(item)) },
                                        onPasteClipboard = { viewModel.pasteClipboard() }
                                    )
                                }
                                NavTab.ARCHIVES -> {
                                    ArchivesScreen(
                                        uiState = uiState,
                                        onArchiveClick = { archive ->
                                            viewModel.showArchiveDetailsDialog(archive)
                                        },
                                        onArchiveLongClick = { archive ->
                                            viewModel.toggleSelection(archive.path)
                                        },
                                        onExtract = { archive ->
                                            viewModel.showExtractDialog(archive)
                                        },
                                        onShare = { archive ->
                                            viewModel.shareFile(archive)
                                        },
                                        onRename = { archive ->
                                            viewModel.showRenameDialog(archive)
                                        },
                                        onDelete = { archive ->
                                            viewModel.showDeleteDialog(listOf(archive))
                                        },
                                        onProperties = { archive ->
                                            viewModel.showPropertiesDialog(archive)
                                        },
                                        onToggleBookmark = { archive ->
                                            viewModel.toggleBookmark(archive)
                                        },
                                        onCreateArchiveClick = {
                                            viewModel.showCompressDialog(emptyList())
                                        }
                                    )
                                }
                                NavTab.CATEGORIES -> {
                                    CategoriesScreen(
                                        uiState = uiState,
                                        selectedCategory = uiState.selectedCategory,
                                        categoryFiles = uiState.categoryFiles,
                                        onSelectCategory = { cat ->
                                            if (cat != null) viewModel.openCategory(cat) else viewModel.closeCategory()
                                        },
                                        onFileClick = { item -> handleFileClick(item, viewModel) },
                                        onFileLongClick = { item -> viewModel.toggleSelection(item.path) },
                                        onShareFile = { item -> viewModel.shareFile(item) },
                                        onRenameFile = { item -> viewModel.showRenameDialog(item) },
                                        onDeleteFile = { item -> viewModel.showDeleteDialog(listOf(item)) },
                                        onPropertiesFile = { item -> viewModel.showPropertiesDialog(item) },
                                        onToggleBookmark = { item -> viewModel.toggleBookmark(item) },
                                        onExtractFile = { item -> viewModel.showExtractDialog(item) },
                                        onCompressFile = { item -> viewModel.showCompressDialog(listOf(item)) }
                                    )
                                }
                                NavTab.MORE -> {
                                    MoreScreen(
                                        uiState = uiState,
                                        onNavigateToStorageAnalysis = { viewModel.showStorageAnalysis(true) },
                                        onNavigateToBookmarks = {
                                            viewModel.showBookmarks(true)
                                        },
                                        onToggleShowHidden = { viewModel.toggleHiddenFiles(!uiState.showHiddenFiles) },
                                        onToggleConfirmDelete = { viewModel.toggleConfirmBeforeDelete(!uiState.confirmBeforeDelete) },
                                        onToggleShowAddedDate = { viewModel.toggleShowAddedDate(!uiState.showAddedDate) },
                                        onToggleShowModifiedDate = { viewModel.toggleShowModifiedDate(!uiState.showModifiedDate) }
                                    )
                                }
                            }
                        }

                        // Storage Analysis Screen Overlay
                        if (uiState.showStorageAnalysisScreen) {
                            StorageAnalysisScreen(
                                storageBreakdown = uiState.storageBreakdown,
                                largestFiles = uiState.storageBreakdown.largestFiles,
                                onNavigateBack = { viewModel.showStorageAnalysis(false) },
                                onFileClick = { item -> handleFileClick(item, viewModel) },
                                onShareFile = { item -> viewModel.shareFile(item) },
                                onRenameFile = { item -> viewModel.showRenameDialog(item) },
                                onDeleteFile = { item -> viewModel.showDeleteDialog(listOf(item)) },
                                onPropertiesFile = { item -> viewModel.showPropertiesDialog(item) },
                                onToggleBookmark = { item -> viewModel.toggleBookmark(item) }
                            )
                        }

                        // Search Overlay
                        if (uiState.isSearchOpen) {
                            SearchOverlay(
                                searchQuery = uiState.searchQuery,
                                selectedCategory = uiState.searchCategoryFilter,
                                searchResults = uiState.searchResults,
                                onQueryChange = { query -> viewModel.search(query, uiState.searchCategoryFilter) },
                                onCategorySelected = { cat -> viewModel.search(uiState.searchQuery, cat) },
                                onClose = { viewModel.closeSearch() },
                                onItemClick = { item ->
                                    viewModel.closeSearch()
                                    handleFileClick(item, viewModel)
                                }
                            )
                        }
                    }
                }

                // Add / Create Modal Sheet
                if (uiState.showAddCreateMenu) {
                    AddCreateSheet(
                        onDismiss = { viewModel.showAddCreateMenu(false) },
                        onNewFolder = {
                            viewModel.showNewFolderDialog(true)
                        },
                        onNewFile = {
                            viewModel.showNewFileDialog(true)
                        },
                        onImportFile = {
                            viewModel.showAddCreateMenu(false)
                            try {
                                importFileLauncher.launch(arrayOf("*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(this, "Cannot launch file picker", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCompress = {
                            val filesToCompress = if (uiState.selectedFilePaths.isNotEmpty()) {
                                uiState.currentFiles.filter { uiState.selectedFilePaths.contains(it.path) }
                            } else {
                                uiState.currentFiles.take(5)
                            }
                            viewModel.showCompressDialog(filesToCompress)
                        },
                        onExtract = {
                            val archive = uiState.currentFiles.firstOrNull { it.isArchive } ?: uiState.allArchives.firstOrNull()
                            if (archive != null) {
                                viewModel.showExtractDialog(archive)
                            } else {
                                Toast.makeText(this, "No archives found in this folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // Create Archive Dialog
                uiState.filesToCompress?.let { files ->
                    CreateArchiveDialog(
                        sourceFiles = files,
                        onDismiss = { viewModel.showCompressDialog(null) },
                        onCreateArchive = { name, format, level, keepOriginals ->
                            viewModel.createArchive(name, format, level, keepOriginals, files)
                        }
                    )
                }

                // Archive Details Dialog
                uiState.activeArchiveDetails?.let { archive ->
                    ArchiveDetailsDialog(
                        archiveItem = archive,
                        onDismiss = { viewModel.showArchiveDetailsDialog(null) },
                        onExtract = {
                            viewModel.showArchiveDetailsDialog(null)
                            viewModel.showExtractDialog(archive)
                        },
                        onBrowseContents = {
                            viewModel.showArchiveDetailsDialog(null)
                            viewModel.openArchiveForBrowsing(archive)
                        },
                        onShare = {
                            viewModel.shareFile(archive)
                        },
                        onDelete = {
                            viewModel.showArchiveDetailsDialog(null)
                            viewModel.showDeleteDialog(listOf(archive))
                        },
                        onRename = {
                            viewModel.showArchiveDetailsDialog(null)
                            viewModel.showRenameDialog(archive)
                        },
                        onProperties = {
                            viewModel.showArchiveDetailsDialog(null)
                            viewModel.showPropertiesDialog(archive)
                        },
                        onTestArchive = {
                            viewModel.testArchive(archive)
                        },
                        onOpenWith = {
                            viewModel.openFileWith(archive)
                        }
                    )
                }

                // Extract Dialog
                uiState.fileToExtract?.let { archive ->
                    ExtractDialog(
                        archiveItem = archive,
                        onDismiss = { viewModel.showExtractDialog(null) },
                        onExtract = { destDir, openAfterExtract ->
                            viewModel.extractArchive(archive, destDir, openFolderOnComplete = openAfterExtract)
                        }
                    )
                }

                // In-Archive Browser Dialog
                uiState.activeArchiveForBrowsing?.let { archive ->
                    ArchiveBrowserDialog(
                        archiveItem = archive,
                        entries = uiState.archiveEntries,
                        isLoading = uiState.isArchiveLoading,
                        onDismiss = { viewModel.closeArchiveBrowser() },
                        onExtractAll = {
                            viewModel.closeArchiveBrowser()
                            viewModel.showExtractDialog(archive)
                        },
                        onExtractSelected = { selectedPaths ->
                            val dest = File(File(archive.path).parentFile ?: getExternalFilesDir(null)!!, "${File(archive.path).nameWithoutExtension}_extracted")
                            viewModel.extractArchive(archive, dest, selectedPaths)
                        }
                    )
                }

                // File Properties Dialog
                uiState.fileProperties?.let { item ->
                    FilePropertiesDialog(
                        item = item,
                        onDismiss = { viewModel.showPropertiesDialog(null) },
                        onShare = {
                            viewModel.shareFile(item)
                        }
                    )
                }

                // File Preview Dialog
                uiState.fileToPreview?.let { item ->
                    FilePreviewDialog(
                        item = item,
                        onDismiss = { viewModel.showPreviewDialog(null) },
                        onOpenWith = { viewModel.openFileWith(item) },
                        onShare = { viewModel.shareFile(item) }
                    )
                }

                // New Folder Dialog
                if (uiState.showNewFolderDialog) {
                    NewFolderDialog(
                        onDismiss = { viewModel.showNewFolderDialog(false) },
                        onConfirm = { name -> viewModel.createFolder(name) }
                    )
                }

                // New File Dialog
                if (uiState.showNewFileDialog) {
                    NewFileDialog(
                        onDismiss = { viewModel.showNewFileDialog(false) },
                        onConfirm = { name, content -> viewModel.createTextFile(name, content) }
                    )
                }

                // Rename Dialog
                uiState.fileToRename?.let { item ->
                    RenameDialog(
                        item = item,
                        onDismiss = { viewModel.showRenameDialog(null) },
                        onConfirm = { newName -> viewModel.renameFile(item, newName) }
                    )
                }

                // Delete Confirmation Dialog
                uiState.filesToDelete?.let { items ->
                    DeleteConfirmDialog(
                        itemCount = items.size,
                        singleItemName = if (items.size == 1) items.first().name else null,
                        onDismiss = { viewModel.showDeleteDialog(null) },
                        onConfirm = { viewModel.deleteFiles(items) }
                    )
                }

                // Operation Progress Dialog
                if (uiState.isOperationInProgress) {
                    OperationProgressDialog(
                        title = uiState.operationTitle,
                        subtitle = uiState.operationSubtitle,
                        progress = uiState.operationProgress,
                        onCancel = { viewModel.cancelOperation() }
                    )
                }

                // Operation Success Dialog
                uiState.operationSuccessMessage?.let { msg ->
                    OperationSuccessDialog(
                        message = msg,
                        actionButtonText = "OK",
                        onAction = { viewModel.dismissSuccessMessage() },
                        onDismiss = { viewModel.dismissSuccessMessage() }
                    )
                }
            }
        }
    }

    private fun handleFileClick(item: FileItem, viewModel: MainViewModel) {
        when {
            item.isDirectory -> {
                viewModel.navigateTo(File(item.path))
                viewModel.setTab(NavTab.STORAGE)
            }
            item.isArchive -> {
                viewModel.showArchiveDetailsDialog(item)
            }
            else -> {
                viewModel.showPreviewDialog(item)
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission(permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}
