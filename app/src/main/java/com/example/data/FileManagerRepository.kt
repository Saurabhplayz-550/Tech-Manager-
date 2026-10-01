package com.example.data

import android.content.Context
import android.app.usage.StorageStatsManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.example.model.ArchiveType
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.SortField
import com.example.model.SortOrder
import com.example.model.StorageBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class DuplicateResolution {
    REPLACE,
    KEEP_BOTH,
    SKIP
}

class FileManagerRepository(
    private val context: Context,
    private val fileTrackingDao: FileTrackingDao
) {
    // Root directory for standard device storage browsing
    val rootStorageDirectory: File
        get() {
            val ext = Environment.getExternalStorageDirectory()
            return if (ext.exists()) ext else context.filesDir
        }

    suspend fun initializeSampleFilesIfNeeded() = withContext(Dispatchers.IO) {
        try {
            // Remove any old sample files directory completely so sample files are never shown
            val sampleBaseDir = File(context.filesDir, "Internal Storage")
            if (sampleBaseDir.exists()) {
                sampleBaseDir.deleteRecursively()
            }
            fileTrackingDao.deleteTrackingByPrefix(sampleBaseDir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getFileItem(file: File): FileItem = withContext(Dispatchers.IO) {
        val tracking = fileTrackingDao.getTracking(file.absolutePath)
        val ext = file.extension.lowercase()
        val archiveType = ArchiveType.fromExtension(ext)
        val isDir = file.isDirectory
        val count = if (isDir) (file.listFiles()?.size ?: 0) else 0
        val size = if (isDir) calculateFolderSize(file) else file.length()

        // Distinguish Added vs Modified vs Created
        val (addedDate, createdDate) = resolveArrivalDates(file, tracking)

        FileItem(
            id = file.absolutePath,
            name = file.name,
            path = file.absolutePath,
            isDirectory = isDir,
            size = size,
            lastModified = file.lastModified(),
            addedDate = addedDate,
            createdDate = createdDate,
            extension = ext,
            itemCount = count,
            isArchive = archiveType != null,
            archiveType = archiveType,
            isBookmarked = tracking?.isBookmarked == true,
            isNewlyDiscovered = tracking?.isNewlyDiscovered == true
        )
    }

    private fun resolveArrivalDates(file: File, tracking: FileTrackingEntity?): Pair<Long?, Long?> {
        var createdDate: Long? = null
        var addedDate: Long? = null

        // 1. Check basic file attributes creation time if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val path = file.toPath()
                val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
                val cTime = attrs.creationTime().toMillis()
                if (cTime > 0L && cTime <= System.currentTimeMillis()) {
                    createdDate = cTime
                }
            } catch (_: Exception) {}
        }

        // 2. Check MediaStore DATE_ADDED for media files
        val mediaStoreAdded = queryMediaStoreDateAdded(file)
        if (mediaStoreAdded != null && mediaStoreAdded > 0L) {
            addedDate = mediaStoreAdded
        } else if (tracking != null && tracking.isNewlyDiscovered && tracking.firstSeenTimestamp > 0L) {
            // 3. Tech Manager locally recorded first-seen timestamp for newly added files
            addedDate = tracking.firstSeenTimestamp
        } else if (createdDate != null) {
            addedDate = createdDate
        }

        return Pair(addedDate, createdDate)
    }

    private fun queryMediaStoreDateAdded(file: File): Long? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATE_ADDED)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sec = cursor.getLong(0)
                    if (sec > 0L) sec * 1000L else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun listFiles(
        directory: File,
        showHidden: Boolean = false,
        sortField: SortField = SortField.NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists()) return@withContext emptyList()

        val files = directory.listFiles() ?: return@withContext emptyList()
        val filtered = files.filter { file ->
            if (!showHidden && file.name.startsWith(".")) false else true
        }

        // Update tracking for any untracked files
        val entitiesToInsert = mutableListOf<FileTrackingEntity>()
        val items = filtered.map { file ->
            val tracking = fileTrackingDao.getTracking(file.absolutePath)
            if (tracking == null) {
                // Record first seen
                val entity = FileTrackingEntity(
                    path = file.absolutePath,
                    firstSeenTimestamp = System.currentTimeMillis(),
                    lastModifiedTimestamp = file.lastModified(),
                    isNewlyDiscovered = true
                )
                entitiesToInsert.add(entity)
            }
            val ext = file.extension.lowercase()
            val archiveType = ArchiveType.fromExtension(ext)
            val isDir = file.isDirectory
            val count = if (isDir) (file.listFiles()?.size ?: 0) else 0
            val size = if (isDir) 0L else file.length()
            val (addedDate, createdDate) = resolveArrivalDates(file, tracking)

            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                size = size,
                lastModified = file.lastModified(),
                addedDate = addedDate,
                createdDate = createdDate,
                extension = ext,
                itemCount = count,
                isArchive = archiveType != null,
                archiveType = archiveType,
                isBookmarked = tracking?.isBookmarked == true,
                isNewlyDiscovered = tracking?.isNewlyDiscovered == true
            )
        }

        if (entitiesToInsert.isNotEmpty()) {
            fileTrackingDao.insertAll(entitiesToInsert)
        }

        sortItems(items, sortField, sortOrder)
    }

    fun sortItems(items: List<FileItem>, field: SortField, order: SortOrder): List<FileItem> {
        val comparator = Comparator<FileItem> { a, b ->
            // Keep directories on top
            if (a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }

            val cmp = when (field) {
                SortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                SortField.ADDED_DATE -> {
                    val aAdded = a.addedDate ?: 0L
                    val bAdded = b.addedDate ?: 0L
                    aAdded.compareTo(bAdded)
                }
                SortField.MODIFIED_DATE -> a.lastModified.compareTo(b.lastModified)
                SortField.SIZE -> a.size.compareTo(b.size)
                SortField.TYPE -> {
                    val aType = if (a.isDirectory) "0_folder" else a.extension
                    val bType = if (b.isDirectory) "0_folder" else b.extension
                    aType.compareTo(bType, ignoreCase = true)
                }
            }
            if (order == SortOrder.DESCENDING) -cmp else cmp
        }
        return items.sortedWith(comparator)
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        val target = File(parent, name.trim())
        if (target.exists()) {
            return@withContext Result.failure(Exception("A folder with this name already exists"))
        }
        if (target.mkdirs()) {
            fileTrackingDao.insertOrUpdate(
                FileTrackingEntity(
                    path = target.absolutePath,
                    firstSeenTimestamp = System.currentTimeMillis(),
                    lastModifiedTimestamp = target.lastModified(),
                    isNewlyDiscovered = true
                )
            )
            Result.success(target)
        } else {
            Result.failure(Exception("Failed to create folder"))
        }
    }

    suspend fun createTextFile(parent: File, name: String, content: String = ""): Result<File> = withContext(Dispatchers.IO) {
        val fileName = if (!name.contains(".")) "$name.txt" else name
        val target = File(parent, fileName.trim())
        if (target.exists()) {
            return@withContext Result.failure(Exception("A file with this name already exists"))
        }
        try {
            target.writeText(content)
            fileTrackingDao.insertOrUpdate(
                FileTrackingEntity(
                    path = target.absolutePath,
                    firstSeenTimestamp = System.currentTimeMillis(),
                    lastModifiedTimestamp = target.lastModified(),
                    isNewlyDiscovered = true
                )
            )
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\")) {
            return@withContext Result.failure(Exception("Invalid file name"))
        }
        val target = File(file.parentFile, trimmed)
        if (target.exists() && target.absolutePath != file.absolutePath) {
            return@withContext Result.failure(Exception("A file with this name already exists"))
        }
        val oldPath = file.absolutePath
        val tracking = fileTrackingDao.getTracking(oldPath)
        if (file.renameTo(target)) {
            fileTrackingDao.deleteTracking(oldPath)
            if (tracking != null) {
                fileTrackingDao.insertOrUpdate(tracking.copy(path = target.absolutePath))
            }
            Result.success(target)
        } else {
            Result.failure(Exception("Failed to rename file"))
        }
    }

    suspend fun deleteFile(file: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val path = file.absolutePath
            val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (success) {
                fileTrackingDao.deleteTracking(path)
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete ${file.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyOrMove(
        source: File,
        destinationDir: File,
        isMove: Boolean,
        resolution: DuplicateResolution
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationDir.mkdirs()
            var targetFile = File(destinationDir, source.name)

            if (targetFile.exists()) {
                when (resolution) {
                    DuplicateResolution.SKIP -> return@withContext Result.success(source)
                    DuplicateResolution.REPLACE -> {
                        if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                    }
                    DuplicateResolution.KEEP_BOTH -> {
                        val baseName = source.nameWithoutExtension
                        val ext = if (source.extension.isNotEmpty()) ".${source.extension}" else ""
                        var counter = 1
                        while (targetFile.exists()) {
                            targetFile = File(destinationDir, "$baseName ($counter)$ext")
                            counter++
                        }
                    }
                }
            }

            if (isMove) {
                val oldPath = source.absolutePath
                val tracking = fileTrackingDao.getTracking(oldPath)
                if (source.renameTo(targetFile)) {
                    fileTrackingDao.deleteTracking(oldPath)
                    if (tracking != null) {
                        fileTrackingDao.insertOrUpdate(tracking.copy(path = targetFile.absolutePath))
                    }
                    Result.success(targetFile)
                } else {
                    // Fallback to copy then delete
                    copyRecursive(source, targetFile)
                    source.deleteRecursively()
                    fileTrackingDao.deleteTracking(oldPath)
                    Result.success(targetFile)
                }
            } else {
                copyRecursive(source, targetFile)
                // Record new copy in tracking
                fileTrackingDao.insertOrUpdate(
                    FileTrackingEntity(
                        path = targetFile.absolutePath,
                        firstSeenTimestamp = System.currentTimeMillis(),
                        lastModifiedTimestamp = targetFile.lastModified(),
                        isNewlyDiscovered = true
                    )
                )
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursive(child, File(dest, child.name))
            }
        } else {
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun toggleBookmark(path: String, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        val existing = fileTrackingDao.getTracking(path)
        if (existing != null) {
            fileTrackingDao.setBookmarked(path, isBookmarked)
        } else {
            val file = File(path)
            fileTrackingDao.insertOrUpdate(
                FileTrackingEntity(
                    path = path,
                    firstSeenTimestamp = System.currentTimeMillis(),
                    lastModifiedTimestamp = if (file.exists()) file.lastModified() else 0L,
                    isBookmarked = isBookmarked
                )
            )
        }
    }

    suspend fun getAllArchives(): List<FileItem> = withContext(Dispatchers.IO) {
        val archives = mutableListOf<FileItem>()
        fun scanDir(dir: File, depth: Int = 0) {
            if (depth > 6) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) {
                        scanDir(f, depth + 1)
                    }
                } else {
                    val ext = f.extension.lowercase()
                    val archiveType = ArchiveType.fromExtension(ext)
                    if (archiveType != null) {
                        val (addedDate, createdDate) = resolveArrivalDates(f, null)
                        archives.add(
                            FileItem(
                                id = f.absolutePath,
                                name = f.name,
                                path = f.absolutePath,
                                isDirectory = false,
                                size = f.length(),
                                lastModified = f.lastModified(),
                                addedDate = addedDate,
                                createdDate = createdDate,
                                extension = ext,
                                isArchive = true,
                                archiveType = archiveType
                            )
                        )
                    }
                }
            }
        }
        scanDir(context.filesDir)
        val ext = Environment.getExternalStorageDirectory()
        if (ext.exists()) {
            scanDir(ext)
        }
        archives.sortedByDescending { it.lastModified }
    }

    suspend fun getFilesByCategory(category: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileItem>()
        when (category) {
            FileCategory.IMAGES -> {
                result.addAll(queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
                if (result.isEmpty()) {
                    scanPublicFolderFiles(result, listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    )) { it.isImage }
                }
            }
            FileCategory.VIDEOS -> {
                result.addAll(queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
                if (result.isEmpty()) {
                    scanPublicFolderFiles(result, listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    )) { it.isVideo }
                }
            }
            FileCategory.AUDIO -> {
                result.addAll(queryMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI))
                if (result.isEmpty()) {
                    scanPublicFolderFiles(result, listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
                    )) { it.isAudio }
                }
            }
            FileCategory.DOCUMENTS -> {
                val docSelection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.pdf' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.doc' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.docx' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.txt' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.xls' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.xlsx' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.ppt' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.pptx' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.csv' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.json' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.md'"
                result.addAll(queryMediaStore(MediaStore.Files.getContentUri("external"), selection = docSelection))
                if (result.isEmpty()) {
                    scanPublicFolderFiles(result, listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    )) { it.isDocument }
                }
            }
            FileCategory.APKS -> {
                val apkSelection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.apk' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.xapk'"
                result.addAll(queryMediaStore(MediaStore.Files.getContentUri("external"), selection = apkSelection))
                if (result.isEmpty()) {
                    scanPublicFolderFiles(result, listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    )) { it.isApk }
                }
            }
            FileCategory.DOWNLOADS -> {
                val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadFolder.exists()) {
                    downloadFolder.listFiles()?.filter { it.isFile }?.forEach { f ->
                        result.add(createFastFileItem(f))
                    }
                }
                val msDownloads = queryMediaStore(MediaStore.Files.getContentUri("external"), selection = "${MediaStore.Files.FileColumns.DATA} LIKE '%/Download/%'")
                for (item in msDownloads) {
                    if (result.none { it.path == item.path }) {
                        result.add(item)
                    }
                }
            }
            FileCategory.ARCHIVES -> {
                val archiveSelection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.zip' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.rar' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.7z' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.tar' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.gz'"
                result.addAll(queryMediaStore(MediaStore.Files.getContentUri("external"), selection = archiveSelection))
                if (result.isEmpty()) {
                    scanPublicFolderFiles(result, listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    )) { it.isArchive }
                }
            }
            FileCategory.OTHER -> {
                val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadFolder.exists()) {
                    downloadFolder.listFiles()?.filter { it.isFile }?.forEach { f ->
                        val item = createFastFileItem(f)
                        if (!item.isImage && !item.isVideo && !item.isAudio && !item.isDocument && !item.isApk && !item.isArchive) {
                            result.add(item)
                        }
                    }
                }
            }
        }
        result.distinctBy { it.path }.sortedByDescending { it.lastModified }
    }

    suspend fun getRecentFiles(limit: Int = 15): List<FileItem> = withContext(Dispatchers.IO) {
        val list = queryMediaStore(
            MediaStore.Files.getContentUri("external"),
            sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            limit = limit
        )
        if (list.isNotEmpty()) {
            list
        } else {
            val fallback = mutableListOf<FileItem>()
            val dirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            )
            for (d in dirs) {
                if (d.exists()) {
                    d.listFiles()?.filter { it.isFile }?.forEach { fallback.add(createFastFileItem(it)) }
                }
            }
            fallback.sortedByDescending { it.lastModified }.take(limit)
        }
    }

    suspend fun getRecentlyAdded(limit: Int = 15): List<FileItem> = withContext(Dispatchers.IO) {
        val list = queryMediaStore(
            MediaStore.Files.getContentUri("external"),
            sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            limit = limit
        )
        if (list.isNotEmpty()) {
            list
        } else {
            getRecentFiles(limit)
        }
    }

    suspend fun getStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        var freeBytes = 0L
        var usedBytes = 0L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (storageStatsManager != null) {
                    val total = storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
                    val free = storageStatsManager.getFreeBytes(StorageManager.UUID_DEFAULT)
                    if (total > 0L) {
                        totalBytes = total
                        freeBytes = free
                        usedBytes = (total - free).coerceAtLeast(0L)
                    }
                }
            }
        } catch (_: Exception) {}

        if (totalBytes <= 0L) {
            try {
                val extPath = Environment.getExternalStorageDirectory().path
                val stat = StatFs(extPath)
                val blockSize = stat.blockSizeLong
                totalBytes = stat.blockCountLong * blockSize
                freeBytes = stat.availableBlocksLong * blockSize
                usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            } catch (_: Exception) {
                try {
                    val stat = StatFs(Environment.getDataDirectory().path)
                    val blockSize = stat.blockSizeLong
                    totalBytes = stat.blockCountLong * blockSize
                    freeBytes = stat.availableBlocksLong * blockSize
                    usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
                } catch (_: Exception) {}
            }
        }

        val categoryBytes = mutableMapOf<FileCategory, Long>()
        val categoryCounts = mutableMapOf<FileCategory, Int>()

        // 1. Images
        val imageStats = getCategoryStats(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        categoryCounts[FileCategory.IMAGES] = imageStats.first
        categoryBytes[FileCategory.IMAGES] = imageStats.second

        // 2. Videos
        val videoStats = getCategoryStats(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        categoryCounts[FileCategory.VIDEOS] = videoStats.first
        categoryBytes[FileCategory.VIDEOS] = videoStats.second

        // 3. Audio
        val audioStats = getCategoryStats(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        categoryCounts[FileCategory.AUDIO] = audioStats.first
        categoryBytes[FileCategory.AUDIO] = audioStats.second

        // 4. Documents
        val docSelection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.pdf' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.doc' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.docx' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.txt' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.xls' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.xlsx' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.ppt' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.pptx' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.csv' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.json'"
        val docStats = getCategoryStats(MediaStore.Files.getContentUri("external"), docSelection)
        categoryCounts[FileCategory.DOCUMENTS] = docStats.first
        categoryBytes[FileCategory.DOCUMENTS] = docStats.second

        // 5. APKs
        val apkSelection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.apk' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.xapk'"
        val apkStats = getCategoryStats(MediaStore.Files.getContentUri("external"), apkSelection)
        categoryCounts[FileCategory.APKS] = apkStats.first
        categoryBytes[FileCategory.APKS] = apkStats.second

        // 6. Downloads
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadDirectFiles = if (downloadFolder.exists()) downloadFolder.listFiles()?.filter { it.isFile } ?: emptyList() else emptyList()
        val downloadStats = getCategoryStats(MediaStore.Files.getContentUri("external"), "${MediaStore.Files.FileColumns.DATA} LIKE '%/Download/%'")
        val dlCount = maxOf(downloadDirectFiles.size, downloadStats.first)
        val dlSize = if (downloadDirectFiles.isNotEmpty()) downloadDirectFiles.sumOf { it.length() } else downloadStats.second
        categoryCounts[FileCategory.DOWNLOADS] = dlCount
        categoryBytes[FileCategory.DOWNLOADS] = dlSize

        // 7. Archives
        val archiveSelection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.zip' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.rar' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.7z' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.tar' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.gz'"
        val archiveStats = getCategoryStats(MediaStore.Files.getContentUri("external"), archiveSelection)
        categoryCounts[FileCategory.ARCHIVES] = archiveStats.first
        categoryBytes[FileCategory.ARCHIVES] = archiveStats.second

        // Fallbacks for any folders on disk if MediaStore had 0
        checkPublicDirectoryFallbacks(categoryCounts, categoryBytes)

        StorageBreakdown(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            categoryBytes = categoryBytes,
            categoryCounts = categoryCounts,
            largestFiles = emptyList()
        )
    }

    private fun queryMediaStore(
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        limit: Int = 1000
    ): List<FileItem> {
        val list = mutableListOf<FileItem>()
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATE_ADDED
            )
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder ?: "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )
            cursor?.use { c ->
                val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val addCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

                var count = 0
                while (c.moveToNext() && count < limit) {
                    val path = if (dataCol != -1) c.getString(dataCol) else null
                    if (path.isNullOrEmpty()) continue
                    val file = File(path)
                    if (!file.exists()) continue

                    val name = if (nameCol != -1) c.getString(nameCol) ?: file.name else file.name
                    val size = if (sizeCol != -1) c.getLong(sizeCol) else file.length()
                    val modSec = if (modCol != -1) c.getLong(modCol) else 0L
                    val addSec = if (addCol != -1) c.getLong(addCol) else 0L

                    val modDate = if (modSec > 0) modSec * 1000L else file.lastModified()
                    val addDate = if (addSec > 0) addSec * 1000L else modDate

                    val ext = file.extension.lowercase()
                    val archiveType = ArchiveType.fromExtension(ext)

                    list.add(
                        FileItem(
                            id = file.absolutePath,
                            name = name,
                            path = file.absolutePath,
                            isDirectory = false,
                            size = if (size > 0) size else file.length(),
                            lastModified = modDate,
                            addedDate = addDate,
                            createdDate = addDate,
                            extension = ext,
                            itemCount = 0,
                            isArchive = archiveType != null,
                            archiveType = archiveType
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun getCategoryStats(
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Pair<Int, Long> {
        var count = 0
        var totalSize = 0L
        try {
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                count = cursor.count
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (sizeCol != -1) {
                    var read = 0
                    while (cursor.moveToNext() && read < 200) {
                        totalSize += cursor.getLong(sizeCol)
                        read++
                    }
                    if (count > read && read > 0) {
                        totalSize = (totalSize / read) * count
                    }
                }
            }
        } catch (_: Exception) {}
        return Pair(count, totalSize)
    }

    private fun scanPublicFolderFiles(
        outList: MutableList<FileItem>,
        dirs: List<File>,
        predicate: (FileItem) -> Boolean
    ) {
        for (dir in dirs) {
            if (!dir.exists()) continue
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (f.isFile && !f.name.startsWith(".")) {
                    val item = createFastFileItem(f)
                    if (predicate(item)) {
                        outList.add(item)
                    }
                }
            }
        }
    }

    private fun checkPublicDirectoryFallbacks(
        counts: MutableMap<FileCategory, Int>,
        bytes: MutableMap<FileCategory, Long>
    ) {
        if ((counts[FileCategory.IMAGES] ?: 0) == 0) {
            val imageDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            )
            var c = 0
            var s = 0L
            for (d in imageDirs) {
                d.listFiles()?.filter { it.isFile }?.forEach { f ->
                    val ext = f.extension.lowercase()
                    if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
                        c++
                        s += f.length()
                    }
                }
            }
            if (c > 0) {
                counts[FileCategory.IMAGES] = c
                bytes[FileCategory.IMAGES] = s
            }
        }

        if ((counts[FileCategory.VIDEOS] ?: 0) == 0) {
            val videoDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            )
            var c = 0
            var s = 0L
            for (d in videoDirs) {
                d.listFiles()?.filter { it.isFile }?.forEach { f ->
                    val ext = f.extension.lowercase()
                    if (ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")) {
                        c++
                        s += f.length()
                    }
                }
            }
            if (c > 0) {
                counts[FileCategory.VIDEOS] = c
                bytes[FileCategory.VIDEOS] = s
            }
        }

        if ((counts[FileCategory.AUDIO] ?: 0) == 0) {
            val audioDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
            )
            var c = 0
            var s = 0L
            for (d in audioDirs) {
                d.listFiles()?.filter { it.isFile }?.forEach { f ->
                    val ext = f.extension.lowercase()
                    if (ext in listOf("mp3", "m4a", "wav", "flac", "aac", "ogg")) {
                        c++
                        s += f.length()
                    }
                }
            }
            if (c > 0) {
                counts[FileCategory.AUDIO] = c
                bytes[FileCategory.AUDIO] = s
            }
        }
    }

    private fun createFastFileItem(f: File): FileItem {
        val ext = f.extension.lowercase()
        val archiveType = ArchiveType.fromExtension(ext)
        return FileItem(
            id = f.absolutePath,
            name = f.name,
            path = f.absolutePath,
            isDirectory = f.isDirectory,
            size = if (f.isDirectory) 0L else f.length(),
            lastModified = f.lastModified(),
            addedDate = f.lastModified(),
            createdDate = f.lastModified(),
            extension = ext,
            itemCount = if (f.isDirectory) (f.listFiles()?.size ?: 0) else 0,
            isArchive = archiveType != null,
            archiveType = archiveType
        )
    }

    suspend fun importFromUri(uri: android.net.Uri, targetDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            var fileName = "imported_${System.currentTimeMillis()}"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    val resolvedName = cursor.getString(nameIndex)
                    if (!resolvedName.isNullOrBlank()) {
                        fileName = resolvedName
                    }
                }
            }

            var destFile = File(targetDir, fileName)
            var counter = 1
            val baseName = destFile.nameWithoutExtension
            val ext = destFile.extension
            while (destFile.exists()) {
                val newName = if (ext.isNotEmpty()) "$baseName($counter).$ext" else "$baseName($counter)"
                destFile = File(targetDir, newName)
                counter++
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open stream for Uri"))

            // Record tracking
            fileTrackingDao.insertOrUpdate(
                FileTrackingEntity(
                    path = destFile.absolutePath,
                    firstSeenTimestamp = System.currentTimeMillis(),
                    lastModifiedTimestamp = destFile.lastModified(),
                    isNewlyDiscovered = true
                )
            )

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateFolderSize(dir: File): Long {
        var length = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            length += if (file.isFile) file.length() else calculateFolderSize(file)
        }
        return length
    }

    private suspend fun scanAndRecordTracking(dir: File) {
        val files = dir.listFiles() ?: return
        val list = mutableListOf<FileTrackingEntity>()
        for (f in files) {
            list.add(
                FileTrackingEntity(
                    path = f.absolutePath,
                    firstSeenTimestamp = f.lastModified(),
                    lastModifiedTimestamp = f.lastModified(),
                    isNewlyDiscovered = false
                )
            )
            if (f.isDirectory) {
                scanAndRecordTracking(f)
            }
        }
        if (list.isNotEmpty()) {
            fileTrackingDao.insertAll(list)
        }
    }
}
