package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_tracking")
data class FileTrackingEntity(
    @PrimaryKey val path: String,
    val firstSeenTimestamp: Long,
    val lastModifiedTimestamp: Long,
    val isNewlyDiscovered: Boolean = false,
    val isBookmarked: Boolean = false,
    val tag: String? = null
)
