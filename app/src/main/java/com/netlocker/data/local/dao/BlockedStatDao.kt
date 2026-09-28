package com.netlocker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.netlocker.data.local.entity.BlockedStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedStatDao {

    @Query("SELECT * FROM blocked_stats WHERE day = :day")
    fun observeDay(day: Long): Flow<List<BlockedStatEntity>>

    @Query("INSERT OR IGNORE INTO blocked_stats (packageName, day, count, lastBlockedAt) VALUES (:packageName, :day, 0, 0)")
    suspend fun ensureRow(packageName: String, day: Long)

    @Query("UPDATE blocked_stats SET count = count + :delta, lastBlockedAt = :at WHERE packageName = :packageName AND day = :day")
    suspend fun addToRow(packageName: String, day: Long, delta: Int, at: Long)

    @Query("DELETE FROM blocked_stats WHERE day < :day")
    suspend fun pruneBefore(day: Long)
}
