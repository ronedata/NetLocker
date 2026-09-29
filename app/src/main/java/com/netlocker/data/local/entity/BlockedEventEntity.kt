package com.netlocker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One blocked connection attempt with its destination — only written while the user has
 * turned on Settings' "Show blocked destinations" (off by default; this is sensitive,
 * close to a connection log). Turning that setting back off deletes every row immediately
 * — see [com.netlocker.domain.repository.BlockedEventsRepository.clearAll] — rather than
 * merely hiding them, since the point of the toggle is that this data shouldn't exist
 * once the user says so.
 */
@Entity(tableName = "blocked_events")
data class BlockedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val destination: String,
    val atMillis: Long,
    /** The local day [atMillis] falls on — lets old rows be pruned the same way blocked_stats is. */
    val day: Long,
)
