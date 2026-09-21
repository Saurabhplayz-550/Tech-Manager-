package com.example.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
        suspend fun scanDir(dir: File, depth: Int = 0) {
            if (depth > 5) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) {
                        scanDir(f, depth + 1)
                    }
                } else {
                    val item = getFileItem(f)
                    val matches = when (category) {
                        FileCategory.IMAGES -> item.isImage
                        FileCategory.VIDEOS -> item.isVideo
                        FileCategory.AUDIO -> item.isAudio
                        FileCategory.DOCUMENTS -> item.isDocument
                        FileCategory.APKS -> item.isApk
                        FileCategory.ARCHIVES -> item.isArchive
                        FileCategory.DOWNLOADS -> f.parentFile?.name.equals("Download", ignoreCase = true)
                        FileCategory.OTHER -> !item.isImage && !item.isVideo && !item.isAudio && !item.isDocument && !item.isApk && !item.isArchive
                    }
                    if (matches) {
                        result.add(item)
                    }
                }
            }
        }
        val ext = Environment.getExternalStorageDirectory()
        if (ext.exists()) {
            scanDir(ext)
        }
        result.sortedByDescending { it.lastModified }
    }

    suspend fun getRecentFiles(limit: Int = 10): List<FileItem> = withContext(Dispatchers.IO) {
        val all = mutableListOf<FileItem>()
        suspend fun scanDir(dir: File, depth: Int = 0) {
            if (depth > 4) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) scanDir(f, depth + 1)
                } else {
                    all.add(getFileItem(f))
                }
            }
        }
        val ext = Environment.getExternalStorageDirectory()
        if (ext.exists()) {
            scanDir(ext)
        }
        all.sortedByDescending { it.lastModified }.take(limit)
    }

    suspend fun getRecentlyAdded(limit: Int = 10): List<FileItem> = withContext(Dispatchers.IO) {
        val all = mutableListOf<FileItem>()
        suspend fun scanDir(dir: File, depth: Int = 0) {
            if (depth > 4) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) scanDir(f, depth + 1)
                } else {
                    val item = getFileItem(f)
                    // Only include if addedDate is genuinely available
                    if (item.addedDate != null && item.addedDate > 0L) {
                        all.add(item)
                    }
                }
            }
        }
        val ext = Environment.getExternalStorageDirectory()
        if (ext.exists()) {
            scanDir(ext)
        }
        all.sortedByDescending { it.addedDate ?: 0L }.take(limit)
    }

    suspend fun getStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        var totalBytes = 128L * 1024 * 1024 * 1024
        var freeBytes = 40L * 1024 * 1024 * 1024
        var usedBytes = 87L * 1024 * 1024 * 1024 + 400L * 1024 * 1024

        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            totalBytes = totalBlocks * blockSize
            freeBytes = availableBlocks * blockSize
            usedBytes = totalBytes - freeBytes
        } catch (_: Exception) {}

        val categoryBytes = mutableMapOf<FileCategory, Long>()
        val categoryCounts = mutableMapOf<FileCategory, Int>()
        val allFiles = mutableListOf<FileItem>()

        suspend fun scanForStats(dir: File, depth: Int = 0) {
            if (depth > 4) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) scanForStats(f, depth + 1)
                } else {
                    val item = getFileItem(f)
                    allFiles.add(item)
                    val cat = when {
                        item.isImage -> FileCategory.IMAGES
                        item.isVideo -> FileCategory.VIDEOS
                        item.isAudio -> FileCategory.AUDIO
                        item.isDocument -> FileCategory.DOCUMENTS
                        item.isApk -> FileCategory.APKS
                        item.isArchive -> FileCategory.ARCHIVES
                        f.parentFile?.name.equals("Download", ignoreCase = true) -> FileCategory.DOWNLOADS
                        else -> FileCategory.OTHER
                    }
                    categoryBytes[cat] = (categoryBytes[cat] ?: 0L) + item.size
                    categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
                }
            }
        }
        val ext = Environment.getExternalStorageDirectory()
        if (ext.exists()) {
            scanForStats(ext)
        }

        val largest = allFiles.sortedByDescending { it.size }.take(10)

        StorageBreakdown(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            categoryBytes = categoryBytes,
            categoryCounts = categoryCounts,
            largestFiles = largest
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
