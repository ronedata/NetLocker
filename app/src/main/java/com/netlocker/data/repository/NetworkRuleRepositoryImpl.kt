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
        dao.upsert(
            AppRuleEntity(
                packageName = packageName,
                wifiAllowed = wifiAllowed,
                mobileDataAllowed = mobileDataAllowed,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun AppRuleEntity.toDomain() = NetworkRule(packageName, wifiAllowed, mobileDataAllowed)
}
