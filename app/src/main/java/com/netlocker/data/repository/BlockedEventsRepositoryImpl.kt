package com.netlocker.data.repository

import com.netlocker.data.local.dao.BlockedEventDao
import com.netlocker.data.local.entity.BlockedEventEntity
import com.netlocker.domain.model.BlockedEvent
import com.netlocker.domain.model.dayOf
import com.netlocker.domain.repository.BlockedEventsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BlockedEventsRepositoryImpl(private val dao: BlockedEventDao) : BlockedEventsRepository {

    override fun observeRecent(packageName: String, limit: Int): Flow<List<BlockedEvent>> =
        dao.observeRecent(packageName, limit).map { rows ->
            rows.map { BlockedEvent(it.packageName, it.destination, it.atMillis) }
        }

    override suspend fun record(packageName: String, destination: String, atMillis: Long) {
        val day = dayOf(atMillis)
        dao.insert(BlockedEventEntity(packageName = packageName, destination = destination, atMillis = atMillis, day = day))
        dao.trimToLatest(packageName, KEEP_PER_APP)
        dao.pruneBefore(day - RETENTION_DAYS)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }

    private companion object {
        const val KEEP_PER_APP = 50
        const val RETENTION_DAYS = 7L
    }
}
