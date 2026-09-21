package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTrackingDao {
    @Query("SELECT * FROM file_tracking WHERE path = :path LIMIT 1")
    suspend fun getTracking(path: String): FileTrackingEntity?

    @Query("SELECT * FROM file_tracking")
    suspend fun getAllTracking(): List<FileTrackingEntity>

    @Query("SELECT * FROM file_tracking WHERE isBookmarked = 1")
    fun getBookmarkedFiles(): Flow<List<FileTrackingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: FileTrackingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<FileTrackingEntity>)

    @Query("UPDATE file_tracking SET isBookmarked = :isBookmarked WHERE path = :path")
    suspend fun setBookmarked(path: String, isBookmarked: Boolean)

    @Query("DELETE FROM file_tracking WHERE path = :path")
    suspend fun deleteTracking(path: String)

    @Query("DELETE FROM file_tracking WHERE path LIKE :pathPrefix || '%'")
    suspend fun deleteTrackingByPrefix(pathPrefix: String)
}
