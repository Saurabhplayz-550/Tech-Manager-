package com.example.util

import com.example.model.ArchiveEntryItem
import com.example.model.CompressionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveManager {

    suspend fun listZipEntries(zipFile: File): List<ArchiveEntryItem> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ArchiveEntryItem>()
        if (!zipFile.exists() || !zipFile.canRead()) return@withContext entries

        try {
            ZipFile(zipFile).use { zf ->
                val enumEntries = zf.entries()
                while (enumEntries.hasMoreElements()) {
                    val entry = enumEntries.nextElement()
                    val normalizedName = entry.name.trimEnd('/')
                    val displayName = normalizedName.substringAfterLast('/')
                    entries.add(
                        ArchiveEntryItem(
                            name = if (displayName.isBlank()) normalizedName else displayName,
                            path = entry.name,
                            isDirectory = entry.isDirectory,
                            size = if (entry.size >= 0) entry.size else 0L,
                            compressedSize = if (entry.compressedSize >= 0) entry.compressedSize else 0L,
                            time = entry.time
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun compressFiles(
        sourceFiles: List<File>,
        destinationZip: File,
        compressionLevel: CompressionLevel,
        keepOriginals: Boolean = true,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationZip.parentFile?.mkdirs()

            // Count total files recursively
            val allFiles = mutableListOf<File>()
            fun collectFiles(file: File) {
                if (file.isDirectory) {
                    val children = file.listFiles() ?: emptyArray()
                    for (child in children) {
                        collectFiles(child)
                    }
                } else {
                    allFiles.add(file)
                }
            }
            for (f in sourceFiles) {
                collectFiles(f)
            }

            val total = allFiles.size.coerceAtLeast(1)
            var current = 0

            val deflaterLevel = when (compressionLevel) {
                CompressionLevel.STORE -> Deflater.NO_COMPRESSION
                CompressionLevel.FAST -> Deflater.BEST_SPEED
                CompressionLevel.NORMAL -> Deflater.DEFAULT_COMPRESSION
                CompressionLevel.MAXIMUM -> Deflater.BEST_COMPRESSION
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationZip))).use { zos ->
                zos.setLevel(deflaterLevel)
                val buffer = ByteArray(8192)

                fun addFileToZip(file: File, basePath: String) {
                    if (file.canonicalPath == destinationZip.canonicalPath) {
                        return // Avoid archiving the archive itself
                    }
                    val cleanBase = basePath.replace('\\', '/').trimStart('/')
                    val entryName = if (cleanBase.isEmpty()) file.name else "$cleanBase/${file.name}"
                    if (file.isDirectory) {
                        val folderEntry = ZipEntry("$entryName/")
                        folderEntry.time = file.lastModified()
                        zos.putNextEntry(folderEntry)
                        zos.closeEntry()
                        file.listFiles()?.forEach { child ->
                            addFileToZip(child, entryName)
                        }
                    } else {
                        current++
                        onProgress(current, total, file.name)
                        FileInputStream(file).use { fis ->
                            BufferedInputStream(fis).use { bis ->
                                val entry = ZipEntry(entryName)
                                entry.time = file.lastModified()
                                zos.putNextEntry(entry)
                                var count: Int
                                while (bis.read(buffer).also { count = it } != -1) {
                                    zos.write(buffer, 0, count)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                for (f in sourceFiles) {
                    addFileToZip(f, "")
                }
            }

            if (!keepOriginals) {
                for (f in sourceFiles) {
                    f.deleteRecursively()
                }
            }

            Result.success(destinationZip)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun extractZip(
        zipFile: File,
        targetDir: File,
        selectedPaths: Set<String>? = null,
        onProgress: (extractedCount: Int, totalCount: Int, currentEntry: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            targetDir.mkdirs()

            var totalCount = 0
            // Pre-count
            ZipFile(zipFile).use { zf ->
                val e = zf.entries()
                while (e.hasMoreElements()) {
                    val item = e.nextElement()
                    if (!item.isDirectory) {
                        if (selectedPaths == null || selectedPaths.contains(item.name)) {
                            totalCount++
                        }
                    }
                }
            }

            totalCount = totalCount.coerceAtLeast(1)
            var extracted = 0

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                val buffer = ByteArray(8192)
                var entry: ZipEntry? = zis.nextEntry

                while (entry != null) {
                    val entryName = entry.name
                    val shouldExtract = selectedPaths == null || 
                        selectedPaths.contains(entryName) ||
                        selectedPaths.any { entryName.startsWith("$it/") }

                    if (shouldExtract) {
                        val destinationFile = File(targetDir, entryName)
                        // Security check: Strict Zip Slip vulnerability prevention
                        val canonicalDest = destinationFile.canonicalPath
                        val canonicalTarget = targetDir.canonicalPath
                        if (!canonicalDest.startsWith(canonicalTarget + File.separator) && canonicalDest != canonicalTarget) {
                            throw SecurityException("Zip entry points outside destination folder: $entryName")
                        }

                        if (entry.isDirectory) {
                            destinationFile.mkdirs()
                        } else {
                            destinationFile.parentFile?.mkdirs()
                            extracted++
                            onProgress(extracted, totalCount, destinationFile.name)
                            FileOutputStream(destinationFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } != -1) {
                                        bos.write(buffer, 0, len)
                                    }
                                }
                            }
                            if (entry.time > 0) {
                                destinationFile.setLastModified(entry.time)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            Result.success(extracted)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun testArchive(zipFile: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            ZipFile(zipFile).use { zf ->
                val enumEntries = zf.entries()
                val buffer = ByteArray(4096)
                while (enumEntries.hasMoreElements()) {
                    val entry = enumEntries.nextElement()
                    count++
                    if (!entry.isDirectory) {
                        zf.getInputStream(entry).use { inputStream ->
                            while (inputStream.read(buffer) != -1) {
                                // verifying integrity
                            }
                        }
                    }
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
