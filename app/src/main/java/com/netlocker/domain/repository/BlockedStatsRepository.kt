package com.netlocker.domain.repository

import com.netlocker.domain.model.BlockedStat
import kotlinx.coroutines.flow.Flow

/** Today's blocked-connection counts, per app. Rolls over to a fresh day at local midnight. */
interface BlockedStatsRepository {
    fun observeToday(): Flow<Map<String, BlockedStat>>

    /** Adds [delta] blocked attempts for [packageName] to today's total. */
    suspend fun record(packageName: String, delta: Int, atMillis: Long)
}
