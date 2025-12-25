package com.example.offlinebrowser.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offlinebrowser.data.model.TrustedServer
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedServerDao {
    @Query("SELECT * FROM trusted_servers WHERE ip = :ip AND port = :port LIMIT 1")
    suspend fun getTrustedServer(ip: String, port: Int): TrustedServer?

    @Query("SELECT * FROM trusted_servers")
    fun getAllTrustedServers(): Flow<List<TrustedServer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedServer(server: TrustedServer)

    @Query("DELETE FROM trusted_servers WHERE ip = :ip AND port = :port")
    suspend fun deleteTrustedServer(ip: String, port: Int)
}
