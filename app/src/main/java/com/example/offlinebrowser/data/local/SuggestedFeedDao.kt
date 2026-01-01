package com.example.offlinebrowser.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offlinebrowser.data.model.SuggestedFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestedFeedDao {
    @Query("SELECT * FROM suggested_feeds ORDER BY rank ASC")
    fun getAll(): Flow<List<SuggestedFeed>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(feeds: List<SuggestedFeed>)

    @Query("DELETE FROM suggested_feeds")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM suggested_feeds")
    suspend fun count(): Int
}
