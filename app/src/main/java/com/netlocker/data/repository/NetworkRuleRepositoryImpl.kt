package com.netlocker.data.repository

import com.netlocker.data.local.dao.AppRuleDao
import com.netlocker.data.local.entity.AppRuleEntity
import com.netlocker.domain.model.ALL_DAYS_MASK
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NetworkRuleRepositoryImpl(private val dao: AppRuleDao) : NetworkRuleRepository {

    override fun observeRules(): Flow<Map<String, NetworkRule>> =
        dao.observeAll().map { rows -> rows.associate { it.packageName to it.toDomain() } }

    override fun observeRule(packageName: String): Flow<NetworkRule> =
        dao.observeOne(packageName).map { it?.toDomain() ?: NetworkRule.default(packageName) }

    override suspend fun getRuleOnce(packageName: String): NetworkRule =
        dao.getOne(packageName)?.toDomain() ?: NetworkRule.default(packageName)

    override suspend fun setRule(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        val now = System.currentTimeMillis()
        val existing = dao.getOne(packageName)
        dao.upsert(
            AppRuleEntity(
                packageName = packageName,
                wifiAllowed = wifiAllowed,
                mobileDataAllowed = mobileDataAllowed,
                updatedAt = now,
                isEnabled = true,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
                // Editing Wi-Fi/Mobile Data is unrelated to the schedule — keep whatever
                // was there rather than silently wiping it on every edit.
                scheduleEnabled = existing?.scheduleEnabled ?: false,
                scheduleStartMinute = existing?.scheduleStartMinute ?: 0,
                scheduleEndMinute = existing?.scheduleEndMinute ?: 0,
                scheduleDays = existing?.scheduleDays ?: ALL_DAYS_MASK,
            ),
        )
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        dao.setEnabled(packageName, enabled, System.currentTimeMillis())
    }

    override suspend fun setSchedule(packageName: String, enabled: Boolean, startMinute: Int, endMinute: Int, days: Int) {
        // An UPDATE-only query would silently do nothing for an app with no rule row yet
        // (0 rows affected, no error) — a "saved" schedule that was never actually
        // persisted. Upsert instead, exactly like setRule, so this always really works.
        val now = System.currentTimeMillis()
        val existing = dao.getOne(packageName)
        dao.upsert(
            AppRuleEntity(
                packageName = packageName,
                wifiAllowed = existing?.wifiAllowed ?: true,
                mobileDataAllowed = existing?.mobileDataAllowed ?: true,
                updatedAt = now,
                isEnabled = existing?.isEnabled ?: true,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
                scheduleEnabled = enabled,
                scheduleStartMinute = startMinute,
                scheduleEndMinute = endMinute,
                scheduleDays = days,
            ),
        )
    }

    override fun observeScheduledRules(): Flow<List<NetworkRule>> =
        dao.observeScheduled().map { rows -> rows.map { it.toDomain() } }

    override suspend fun deleteRule(packageName: String) {
        dao.delete(packageName)
    }

    private fun AppRuleEntity.toDomain() = NetworkRule(
        packageName = packageName,
        wifiAllowed = wifiAllowed,
        mobileDataAllowed = mobileDataAllowed,
        isEnabled = isEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        scheduleEnabled = scheduleEnabled,
        scheduleStartMinute = scheduleStartMinute,
        scheduleEndMinute = scheduleEndMinute,
        scheduleDays = scheduleDays,
    )
}
