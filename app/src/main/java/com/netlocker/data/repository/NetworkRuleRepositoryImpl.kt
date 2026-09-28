package com.netlocker.data.repository

import com.netlocker.data.local.dao.AppRuleDao
import com.netlocker.data.local.entity.AppRuleEntity
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
            ),
        )
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        dao.setEnabled(packageName, enabled, System.currentTimeMillis())
    }

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
    )
}
