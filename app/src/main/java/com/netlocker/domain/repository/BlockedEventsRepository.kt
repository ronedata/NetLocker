package com.netlocker.domain.repository

import com.netlocker.domain.model.BlockedEvent
import kotlinx.coroutines.flow.Flow

/**
 * The optional, off-by-default "Show blocked destinations" log — see
 * [com.netlocker.util.PreferencesManager.showBlockedDestinations]. Nothing is written here
 * unless that setting is on, and turning it back off deletes everything immediately via
 * [clearAll] rather than just hiding it.
 */
interface BlockedEventsRepository {
    /** Most recent blocked destinations for one app, newest first. */
    fun observeRecent(packageName: String, limit: Int = 20): Flow<List<BlockedEvent>>

    suspend fun record(packageName: String, destination: String, atMillis: Long)

    /** Deletes every stored destination for every app — used when the user turns the
     *  Settings toggle back off. */
    suspend fun clearAll()
}
