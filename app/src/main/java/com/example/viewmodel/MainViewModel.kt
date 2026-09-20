package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DuplicateResolution
import com.example.data.FileManagerRepository
import com.example.model.ArchiveEntryItem
import com.example.model.ArchiveType
import com.example.model.CompressionLevel
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.SortField
import com.example.model.SortOrder
import com.example.model.StorageBreakdown
import com.example.model.ViewMode
import com.example.util.ArchiveManager
import com.example.util.FileFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class NavTab {
    FILES,
    STORAGE,
    ARCHIVES,
    CATEGORIES,
    MORE
}

data class UiState(
    val currentTab: NavTab = NavTab.FILES,
    val currentDirectory: File? = null,
    val currentFiles: List<FileItem> = emptyList(),
    val breadcrumbs: List<File> = emptyList(),
    val selectedFilePaths: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val clipboardFiles: List<File> = emptyList(),
    val isClipboardCut: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortField: SortField = SortField.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val showHiddenFiles: Boolean = false,
    val confirmBeforeDelete: Boolean = true,
    val use24HourTime: Boolean = false,
    val showAddedDate: Boolean = true,
    val showModifiedDate: Boolean = true,
    val themeMode: String = "System", // "System", "Light", "Dark"

    // Dashboard & Overview
    val recentFiles: List<FileItem> = emptyList(),
    val recentlyAddedFiles: List<FileItem> = emptyList(),
    val storageBreakdown: StorageBreakdown = StorageBreakdown(),
    val allArchives: List<FileItem> = emptyList(),
    val categoryFiles: List<FileItem> = emptyList(),
    val selectedCategory: FileCategory? = null,

    // Search
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchCategoryFilter: FileCategory? = null,
    val searchResults: List<FileItem> = emptyList(),

    // Archive In-Browser
    val activeArchiveForBrowsing: FileItem? = null,
    val archiveEntries: List<ArchiveEntryItem> = emptyList(),
    val isArchiveLoading: Boolean = false,

    // Active Dialogs & Sheets
    val showAddCreateMenu: Boolean = false,
    val showNewFolderDialog: Boolean = false,
    val showNewFileDialog: Boolean = false,
    val fileToRename: FileItem? = null,
    val filesToDelete: List<FileItem>? = null,
    val fileProperties: FileItem? = null,
    val activeArchiveDetails: FileItem? = null,
    val filesToCompress: List<FileItem>? = null,
    val fileToExtract: FileItem? = null,
    val fileToPreview: FileItem? = null,
    val showStorageAnalysisScreen: Boolean = false,
    val showBookmarksScreen: Boolean = false,
    val bookmarkedFiles: List<FileItem> = emptyList(),

    // Progress
    val isOperationInProgress: Boolean = false,
    val operationTitle: String = "",
    val operationProgress: Float = 0f,
    val operationSubtitle: String = "",
    val operationSuccessMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FileManagerRepository
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FileManagerRepository(application, db.fileTrackingDao())

        viewModelScope.launch {
            repository.initializeSampleFilesIfNeeded()
            val initialDir = File(application.filesDir, "Internal Storage")
            val targetDir = if (initialDir.exists()) initialDir else repository.rootStorageDirectory
            _uiState.update { it.copy(currentDirectory = targetDir) }
            refreshDirectory(targetDir)
            refreshDashboard()
        }
    }

    fun setTab(tab: NavTab) {
        _uiState.update { it.copy(currentTab = tab, selectedCategory = null, showStorageAnalysisScreen = false, showBookmarksScreen = false) }
        when (tab) {
            NavTab.FILES -> refreshDashboard()
            NavTab.STORAGE -> {
                val dir = _uiState.value.currentDirectory ?: repository.rootStorageDirectory
                refreshDirectory(dir)
            }
            NavTab.ARCHIVES -> loadAllArchives()
            NavTab.CATEGORIES -> refreshDashboard()
            NavTab.MORE -> refreshStorageAnalysis()
        }
    }

    fun openRootStorage() {
        val root = repository.rootStorageDirectory
        _uiState.update { it.copy(currentTab = NavTab.STORAGE, selectedCategory = null, showStorageAnalysisScreen = false, showBookmarksScreen = false) }
        refreshDirectory(root)
    }

    fun openCategory(category: FileCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCategory = category, currentTab = NavTab.CATEGORIES) }
            val files = repository.getFilesByCategory(category)
            _uiState.update { it.copy(categoryFiles = files) }
        }
    }

    fun closeCategory() {
        _uiState.update { it.copy(selectedCategory = null) }
        refreshDashboard()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            val breakdown = repository.getStorageBreakdown()
            val recent = repository.getRecentFiles(10)
            val recentlyAdded = repository.getRecentlyAdded(10)
            _uiState.update {
                it.copy(
                    storageBreakdown = breakdown,
                    recentFiles = recent,
                    recentlyAddedFiles = recentlyAdded
                )
            }
        }
    }

    fun refreshDirectory(dir: File) {
        viewModelScope.launch {
            val list = repository.listFiles(
                directory = dir,
                showHidden = _uiState.value.showHiddenFiles,
                sortField = _uiState.value.sortField,
                sortOrder = _uiState.value.sortOrder
            )

            // Build breadcrumbs
            val breadcrumbs = mutableListOf<File>()
            var curr: File? = dir
            while (curr != null) {
                breadcrumbs.add(0, curr)
                if (curr.name == "Internal Storage" || curr == repository.rootStorageDirectory || curr.parentFile == null) {
                    break
                }
                curr = curr.parentFile
            }

            _uiState.update {
                it.copy(
                    currentDirectory = dir,
                    currentFiles = list,
                    breadcrumbs = breadcrumbs,
                    isSelectionMode = false,
                    selectedFilePaths = emptySet()
                )
            }
        }
    }

    fun navigateTo(dir: File) {
        if (dir.isDirectory) {
            refreshDirectory(dir)
        }
    }

    fun navigateUp(): Boolean {
        val current = _uiState.value.currentDirectory ?: return false
        val parent = current.parentFile
        if (current.name == "Internal Storage" || current == repository.rootStorageDirectory || parent == null) {
            return false
        }
        refreshDirectory(parent)
        return true
    }

    fun setSort(field: SortField, order: SortOrder) {
        _uiState.update { it.copy(sortField = field, sortOrder = order) }
        val currentDir = _uiState.value.currentDirectory ?: return
        val sorted = repository.sortItems(_uiState.value.currentFiles, field, order)
        _uiState.update { it.copy(currentFiles = sorted) }
    }

    fun toggleViewMode() {
        val next = if (_uiState.value.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        _uiState.update { it.copy(viewMode = next) }
    }

    // Selection
    fun toggleSelection(path: String) {
        val current = _uiState.value.selectedFilePaths.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _uiState.update {
            it.copy(
                selectedFilePaths = current,
                isSelectionMode = current.isNotEmpty()
            )
        }
    }

    fun startSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = true) }
    }

    fun selectAll() {
        val allPaths = _uiState.value.currentFiles.map { it.path }.toSet()
        _uiState.update {
            it.copy(selectedFilePaths = allPaths, isSelectionMode = true)
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(selectedFilePaths = emptySet(), isSelectionMode = false)
        }
    }

    // Clipboard (Copy / Cut / Paste)
    fun copySelected() {
        val selectedFiles = _uiState.value.selectedFilePaths.map { File(it) }
        _uiState.update {
            it.copy(
                clipboardFiles = selectedFiles,
                isClipboardCut = false,
                isSelectionMode = false,
                selectedFilePaths = emptySet()
            )
        }
        Toast.makeText(getApplication(), "Copied ${selectedFiles.size} item(s) to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun cutSelected() {
        val selectedFiles = _uiState.value.selectedFilePaths.map { File(it) }
        _uiState.update {
            it.copy(
                clipboardFiles = selectedFiles,
                isClipboardCut = true,
                isSelectionMode = false,
                selectedFilePaths = emptySet()
            )
        }
        Toast.makeText(getApplication(), "Cut ${selectedFiles.size} item(s) to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun pasteClipboard(resolution: DuplicateResolution = DuplicateResolution.KEEP_BOTH) {
        val dest = _uiState.value.currentDirectory ?: return
        val files = _uiState.value.clipboardFiles
        val isMove = _uiState.value.isClipboardCut

        if (files.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOperationInProgress = true,
                    operationTitle = if (isMove) "Moving files..." else "Copying files...",
                    operationProgress = 0f,
                    operationSubtitle = "Preparing..."
                )
            }

            var count = 0
            for ((idx, f) in files.withIndex()) {
                val progress = (idx + 1).toFloat() / files.size
                _uiState.update {
                    it.copy(
                        operationProgress = progress,
                        operationSubtitle = "${idx + 1} / ${files.size}: ${f.name}"
                    )
                }
                repository.copyOrMove(f, dest, isMove, resolution)
                count++
            }

            _uiState.update {
                it.copy(
                    isOperationInProgress = false,
                    clipboardFiles = if (isMove) emptyList() else it.clipboardFiles,
                    operationSuccessMessage = "Successfully ${if (isMove) "moved" else "copied"} $count item(s)"
                )
            }
            refreshDirectory(dest)
            refreshDashboard()
        }
    }

    // File Operations
    fun createFolder(name: String) {
        val dir = _uiState.value.currentDirectory ?: return
        viewModelScope.launch {
            val result = repository.createFolder(dir, name)
            if (result.isSuccess) {
                Toast.makeText(getApplication(), "Folder created", Toast.LENGTH_SHORT).show()
                refreshDirectory(dir)
                refreshDashboard()
            } else {
                Toast.makeText(getApplication(), result.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
            }
            _uiState.update { it.copy(showNewFolderDialog = false) }
        }
    }

    fun createTextFile(name: String, content: String = "") {
        val dir = _uiState.value.currentDirectory ?: return
        viewModelScope.launch {
            val result = repository.createTextFile(dir, name, content)
            if (result.isSuccess) {
                Toast.makeText(getApplication(), "File created", Toast.LENGTH_SHORT).show()
                refreshDirectory(dir)
                refreshDashboard()
            } else {
                Toast.makeText(getApplication(), result.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
            }
            _uiState.update { it.copy(showNewFileDialog = false) }
        }
    }

    fun importFile(uri: android.net.Uri) {
        val dir = _uiState.value.currentDirectory ?: return
        viewModelScope.launch {
            val result = repository.importFromUri(uri, dir)
            if (result.isSuccess) {
                Toast.makeText(getApplication(), "File imported successfully", Toast.LENGTH_SHORT).show()
                refreshDirectory(dir)
                refreshDashboard()
            } else {
                Toast.makeText(getApplication(), "Failed to import file: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun renameFile(item: FileItem, newName: String) {
        viewModelScope.launch {
            val file = File(item.path)
            val result = repository.renameFile(file, newName)
            if (result.isSuccess) {
                Toast.makeText(getApplication(), "Renamed successfully", Toast.LENGTH_SHORT).show()
                _uiState.value.currentDirectory?.let { refreshDirectory(it) }
                refreshDashboard()
            } else {
                Toast.makeText(getApplication(), result.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
            }
            _uiState.update { it.copy(fileToRename = null) }
        }
    }

    fun deleteFiles(items: List<FileItem>) {
        viewModelScope.launch {
            var deleted = 0
            for (item in items) {
                val res = repository.deleteFile(File(item.path))
                if (res.isSuccess) deleted++
            }
            Toast.makeText(getApplication(), "Deleted $deleted item(s)", Toast.LENGTH_SHORT).show()
            _uiState.update { it.copy(filesToDelete = null, selectedFilePaths = emptySet(), isSelectionMode = false) }
            _uiState.value.currentDirectory?.let { refreshDirectory(it) }
            refreshDashboard()
            if (_uiState.value.currentTab == NavTab.ARCHIVES) loadAllArchives()
        }
    }

    fun toggleBookmark(item: FileItem) {
        viewModelScope.launch {
            val newStatus = !item.isBookmarked
            repository.toggleBookmark(item.path, newStatus)
            Toast.makeText(getApplication(), if (newStatus) "Added to Bookmarks" else "Removed from Bookmarks", Toast.LENGTH_SHORT).show()
            _uiState.value.currentDirectory?.let { refreshDirectory(it) }
            loadBookmarks()
        }
    }

    fun loadBookmarks() {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val all = db.fileTrackingDao().getAllTracking().filter { it.isBookmarked }
            val items = all.mapNotNull { entity ->
                val f = File(entity.path)
                if (f.exists()) repository.getFileItem(f) else null
            }
            _uiState.update { it.copy(bookmarkedFiles = items) }
        }
    }

    // Archives
    fun loadAllArchives() {
        viewModelScope.launch {
            val archives = repository.getAllArchives()
            _uiState.update { it.copy(allArchives = archives) }
        }
    }

    fun openArchiveForBrowsing(archiveItem: FileItem) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeArchiveForBrowsing = archiveItem,
                    isArchiveLoading = true,
                    archiveEntries = emptyList()
                )
            }
            val entries = ArchiveManager.listZipEntries(File(archiveItem.path))
            _uiState.update {
                it.copy(
                    archiveEntries = entries,
                    isArchiveLoading = false
                )
            }
        }
    }

    fun closeArchiveBrowser() {
        _uiState.update { it.copy(activeArchiveForBrowsing = null, archiveEntries = emptyList()) }
    }

    fun createArchive(
        archiveName: String,
        format: ArchiveType,
        level: CompressionLevel,
        keepOriginals: Boolean,
        sourceFiles: List<FileItem>
    ) {
        val dir = _uiState.value.currentDirectory
            ?: File(sourceFiles.firstOrNull()?.path ?: "").parentFile
            ?: repository.rootStorageDirectory
        val rawName = if (archiveName.isBlank()) "Archive" else archiveName.trim()
        val finalName = if (!rawName.endsWith(".${format.extension}", ignoreCase = true)) {
            "$rawName.${format.extension}"
        } else rawName

        val destZip = File(dir, finalName)
        val files = sourceFiles.map { File(it.path) }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOperationInProgress = true,
                    operationTitle = "Compressing to ${format.extension.uppercase()}...",
                    operationProgress = 0f,
                    operationSubtitle = "Compressing ${files.size} item(s)...",
                    filesToCompress = null
                )
            }

            val result = ArchiveManager.compressFiles(
                sourceFiles = files,
                destinationZip = destZip,
                compressionLevel = level,
                keepOriginals = keepOriginals,
                onProgress = { current, total, name ->
                    val progress = current.toFloat() / total
                    _uiState.update {
                        it.copy(
                            operationProgress = progress,
                            operationSubtitle = "$name ($current/$total)"
                        )
                    }
                }
            )

            if (result.isSuccess) {
                val created = result.getOrNull()
                val sizeStr = if (created != null && created.exists()) " (${FileFormatter.formatFileSize(created.length())})" else ""
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        operationSuccessMessage = "Archive $finalName created successfully$sizeStr"
                    )
                }
                clearSelection()
                refreshDirectory(dir)
                refreshDashboard()
                loadAllArchives()
            } else {
                _uiState.update {
                    it.copy(isOperationInProgress = false)
                }
                Toast.makeText(getApplication(), "Failed to create archive: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun extractArchive(
        archiveItem: FileItem,
        destinationFolder: File,
        selectedEntries: Set<String>? = null,
        openFolderOnComplete: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOperationInProgress = true,
                    operationTitle = "Extracting ${archiveItem.name}...",
                    operationProgress = 0f,
                    operationSubtitle = "Preparing extraction...",
                    fileToExtract = null
                )
            }

            val result = ArchiveManager.extractZip(
                zipFile = File(archiveItem.path),
                targetDir = destinationFolder,
                selectedPaths = selectedEntries,
                onProgress = { extracted, total, currentEntry ->
                    val progress = extracted.toFloat() / total
                    _uiState.update {
                        it.copy(
                            operationProgress = progress,
                            operationSubtitle = "Extracted: $extracted / $total ($currentEntry)"
                        )
                    }
                }
            )

            if (result.isSuccess) {
                val extractedCount = result.getOrNull() ?: 0
                _uiState.update {
                    it.copy(
                        isOperationInProgress = false,
                        operationSuccessMessage = "Extraction completed ($extractedCount files extracted to ${destinationFolder.name})"
                    )
                }
                if (openFolderOnComplete && destinationFolder.exists()) {
                    navigateTo(destinationFolder)
                    setTab(NavTab.STORAGE)
                } else {
                    _uiState.value.currentDirectory?.let { refreshDirectory(it) }
                }
                refreshDashboard()
                loadAllArchives()
            } else {
                _uiState.update { it.copy(isOperationInProgress = false) }
                Toast.makeText(getApplication(), "Extraction error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun testArchive(archiveItem: FileItem) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOperationInProgress = true,
                    operationTitle = "Testing archive...",
                    operationProgress = 0.5f,
                    operationSubtitle = "Verifying CRC integrity..."
                )
            }
            val result = ArchiveManager.testArchive(File(archiveItem.path))
            _uiState.update { it.copy(isOperationInProgress = false) }
            if (result.isSuccess) {
                Toast.makeText(getApplication(), "Archive verified: ${result.getOrNull()} entries intact", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Archive test failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Share File via FileProvider
    fun shareFile(fileItem: FileItem) {
        try {
            val file = File(fileItem.path)
            val uri: Uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share ${fileItem.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "Unable to share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Open file with external viewer
    fun openFileWith(fileItem: FileItem) {
        try {
            val file = File(fileItem.path)
            val uri: Uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open with...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    // Search
    fun search(query: String, category: FileCategory? = null) {
        _uiState.update { it.copy(searchQuery = query, searchCategoryFilter = category, isSearchOpen = true) }
        viewModelScope.launch {
            if (query.isBlank() && category == null) {
                _uiState.update { it.copy(searchResults = emptyList()) }
                return@launch
            }
            val allFiles = repository.getRecentFiles(100)
            val filtered = allFiles.filter { item ->
                val nameMatch = query.isBlank() || item.name.contains(query, ignoreCase = true)
                val catMatch = when (category) {
                    null -> true
                    FileCategory.IMAGES -> item.isImage
                    FileCategory.VIDEOS -> item.isVideo
                    FileCategory.AUDIO -> item.isAudio
                    FileCategory.DOCUMENTS -> item.isDocument
                    FileCategory.APKS -> item.isApk
                    FileCategory.ARCHIVES -> item.isArchive
                    FileCategory.DOWNLOADS -> item.path.contains("Download", ignoreCase = true)
                    FileCategory.OTHER -> !item.isImage && !item.isVideo && !item.isAudio && !item.isDocument && !item.isApk && !item.isArchive
                }
                nameMatch && catMatch
            }
            _uiState.update { it.copy(searchResults = filtered) }
        }
    }

    fun closeSearch() {
        _uiState.update { it.copy(isSearchOpen = false, searchQuery = "", searchResults = emptyList()) }
    }

    // Settings
    fun toggleHiddenFiles(show: Boolean) {
        _uiState.update { it.copy(showHiddenFiles = show) }
        _uiState.value.currentDirectory?.let { refreshDirectory(it) }
    }

    fun toggleConfirmBeforeDelete(confirm: Boolean) {
        _uiState.update { it.copy(confirmBeforeDelete = confirm) }
    }

    fun toggle24HourTime(use24: Boolean) {
        _uiState.update { it.copy(use24HourTime = use24) }
    }

    fun toggleShowAddedDate(show: Boolean) {
        _uiState.update { it.copy(showAddedDate = show) }
    }

    fun toggleShowModifiedDate(show: Boolean) {
        _uiState.update { it.copy(showModifiedDate = show) }
    }

    fun setThemeMode(theme: String) {
        _uiState.update { it.copy(themeMode = theme) }
    }

    fun refreshStorageAnalysis() {
        viewModelScope.launch {
            val breakdown = repository.getStorageBreakdown()
            _uiState.update { it.copy(storageBreakdown = breakdown) }
        }
    }

    // UI Dialog Triggers
    fun showAddCreateMenu(show: Boolean) = _uiState.update { it.copy(showAddCreateMenu = show) }
    fun showNewFolderDialog(show: Boolean) = _uiState.update { it.copy(showNewFolderDialog = show, showAddCreateMenu = false) }
    fun showNewFileDialog(show: Boolean) = _uiState.update { it.copy(showNewFileDialog = show, showAddCreateMenu = false) }
    fun showRenameDialog(fileItem: FileItem?) = _uiState.update { it.copy(fileToRename = fileItem) }
    fun showDeleteDialog(files: List<FileItem>?) = _uiState.update { it.copy(filesToDelete = files) }
    fun showPropertiesDialog(fileItem: FileItem?) = _uiState.update { it.copy(fileProperties = fileItem) }
    fun showArchiveDetailsDialog(fileItem: FileItem?) = _uiState.update { it.copy(activeArchiveDetails = fileItem) }
    fun showCompressDialog(files: List<FileItem>?) = _uiState.update { it.copy(filesToCompress = files, showAddCreateMenu = false) }
    fun showExtractDialog(fileItem: FileItem?) = _uiState.update { it.copy(fileToExtract = fileItem, showAddCreateMenu = false) }
    fun showPreviewDialog(fileItem: FileItem?) = _uiState.update { it.copy(fileToPreview = fileItem) }
    fun showStorageAnalysis(show: Boolean) = _uiState.update { it.copy(showStorageAnalysisScreen = show) }
    fun showBookmarks(show: Boolean) {
        _uiState.update { it.copy(showBookmarksScreen = show) }
        if (show) loadBookmarks()
    }
    fun dismissSuccessMessage() = _uiState.update { it.copy(operationSuccessMessage = null) }
    fun cancelOperation() = _uiState.update { it.copy(isOperationInProgress = false) }
}
