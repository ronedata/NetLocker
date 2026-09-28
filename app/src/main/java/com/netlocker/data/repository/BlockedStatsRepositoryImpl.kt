package com.netlocker.data.repository

import com.netlocker.data.local.dao.BlockedStatDao
import com.netlocker.domain.model.BlockedStat
import com.netlocker.domain.model.dayOf
import com.netlocker.domain.model.millisUntilNextDay
import com.netlocker.domain.repository.BlockedStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class BlockedStatsRepositoryImpl(private val dao: BlockedStatDao) : BlockedStatsRepository {

    /** Emits today's day number now and again at every local midnight. */
    private val dayTicker: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(dayOf(now))
            delay(millisUntilNextDay(now) + 1_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeToday(): Flow<Map<String, BlockedStat>> =
        dayTicker.flatMapLatest { day -> dao.observeDay(day) }.map { rows ->
            rows.associate { it.packageName to BlockedStat(it.packageName, it.count, it.lastBlockedAt) }
        }

    override suspend fun record(packageName: String, delta: Int, atMillis: Long) {
        val day = dayOf(atMillis)
        dao.ensureRow(packageName, day)
        dao.addToRow(packageName, day, delta, atMillis)
        dao.pruneBefore(day - RETENTION_DAYS)
    }

    private companion object {
        const val RETENTION_DAYS = 7L
    }
}
