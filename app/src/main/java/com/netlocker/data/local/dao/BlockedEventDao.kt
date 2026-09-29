package com.netlocker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.netlocker.data.local.entity.BlockedEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedEventDao {

    @Insert
    suspend fun insert(event: BlockedEventEntity)

    @Query("SELECT * FROM blocked_events WHERE packageName = :packageName ORDER BY atMillis DESC LIMIT :limit")
    fun observeRecent(packageName: String, limit: Int): Flow<List<BlockedEventEntity>>

    /** Keeps only the newest [keep] rows for [packageName] — a busy blocked app (lots of
     *  DNS retries) shouldn't be allowed to grow this table without bound. */
    @Query(
        "DELETE FROM blocked_events WHERE packageName = :packageName AND id NOT IN " +
            "(SELECT id FROM blocked_events WHERE packageName = :packageName ORDER BY atMillis DESC LIMIT :keep)",
    )
    suspend fun trimToLatest(packageName: String, keep: Int)

    @Query("DELETE FROM blocked_events WHERE day < :day")
    suspend fun pruneBefore(day: Long)

    /** Everything, immediately — what turning "Show blocked destinations" back off does. */
    @Query("DELETE FROM blocked_events")
    suspend fun deleteAll()
}
