package com.netlocker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted per-app network rule (spec §16/§6). [packageName] is the primary key —
 * one rule per app, defaulting to fully-allowed for any app with no row here (see
 * NetworkRuleRepositoryImpl). [updatedAt] is kept for diagnostics/debugging only,
 * not read by the enforcement path.
 */
@Entity(tableName = "app_network_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val wifiAllowed: Boolean,
    val mobileDataAllowed: Boolean,
    val updatedAt: Long,
)
