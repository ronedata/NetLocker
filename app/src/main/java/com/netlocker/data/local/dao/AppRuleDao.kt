package com.netlocker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.netlocker.data.local.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Query("SELECT * FROM app_network_rules")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_network_rules WHERE packageName = :packageName")
    fun observeOne(packageName: String): Flow<AppRuleEntity?>

    @Query("SELECT * FROM app_network_rules WHERE packageName = :packageName")
    suspend fun getOne(packageName: String): AppRuleEntity?

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Query("UPDATE app_network_rules SET isEnabled = :enabled, updatedAt = :now WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean, now: Long)

    @Query("DELETE FROM app_network_rules WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
