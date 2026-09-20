package com.example.model

enum class ArchiveType(val extension: String, val displayName: String) {
    ZIP("zip", "ZIP Archive"),
    SEVEN_Z("7z", "7Z Archive"),
    RAR("rar", "RAR Archive"),
    TAR("tar", "TAR Archive"),
    GZ("gz", "GZIP Archive"),
    BZ2("bz2", "BZIP2 Archive"),
    XZ("xz", "XZ Archive");

    companion object {
        fun fromExtension(ext: String): ArchiveType? {
            val lower = ext.lowercase()
            return entries.find { it.extension == lower }
        }
    }
}

enum class FileCategory(val title: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APKS("APKs"),
    ARCHIVES("Archives"),
    DOWNLOADS("Downloads"),
    OTHER("Other")
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val addedDate: Long? = null,
    val createdDate: Long? = null,
    val extension: String = "",
    val itemCount: Int = 0,
    val isArchive: Boolean = false,
    val archiveType: ArchiveType? = null,
    val isBookmarked: Boolean = false,
    val isNewlyDiscovered: Boolean = false
) {
    val isZip: Boolean get() = extension.equals("zip", ignoreCase = true)
    val is7z: Boolean get() = extension.equals("7z", ignoreCase = true)
    val isPdf: Boolean get() = extension.equals("pdf", ignoreCase = true)
    val isApk: Boolean get() = extension.equals("apk", ignoreCase = true) || extension.equals("xapk", ignoreCase = true)
    val isImage: Boolean get() = extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "svg", "bmp")
    val isVideo: Boolean get() = extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
    val isAudio: Boolean get() = extension.lowercase() in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a")
    val isDocument: Boolean get() = extension.lowercase() in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "json", "xml", "csv")
}

data class ArchiveEntryItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long = 0L,
    val time: Long = 0L
)

enum class SortField(val label: String) {
    NAME("Name"),
    ADDED_DATE("Added date"),
    MODIFIED_DATE("Date modified"),
    SIZE("Size"),
    TYPE("Type")
}

enum class SortOrder(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class ViewMode {
    LIST,
    GRID
}

enum class CompressionLevel(val title: String) {
    STORE("Store (No compression)"),
    FAST("Fast"),
    NORMAL("Normal"),
    MAXIMUM("Maximum")
}

data class StorageBreakdown(
    val totalBytes: Long = 128L * 1024 * 1024 * 1024,
    val usedBytes: Long = 87L * 1024 * 1024 * 1024 + 400L * 1024 * 1024,
    val freeBytes: Long = 40L * 1024 * 1024 * 1024 + 600L * 1024 * 1024,
    val categoryBytes: Map<FileCategory, Long> = emptyMap(),
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val largestFiles: List<FileItem> = emptyList()
) {
    val usedPercent: Int
        get() = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0
}
