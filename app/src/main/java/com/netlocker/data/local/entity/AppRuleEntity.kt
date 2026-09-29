package com.netlocker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.netlocker.domain.model.ALL_DAYS_MASK

/**
 * Persisted per-app network rule. [packageName] is the primary key — one rule per app;
 * an app with no row is "default" (fully allowed).
 *
 * [isEnabled] and [createdAt] were added in schema v2 (see MIGRATION_1_2). Their
 * `defaultValue`s must match the migration's `DEFAULT` clauses exactly: Room validates
 * a migrated table against the entity and refuses to open it on any mismatch.
 */
@Entity(tableName = "app_network_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val wifiAllowed: Boolean,
    val mobileDataAllowed: Boolean,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val scheduleEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val scheduleStartMinute: Int = 0,
    @ColumnInfo(defaultValue = "0") val scheduleEndMinute: Int = 0,
    @ColumnInfo(defaultValue = "127") val scheduleDays: Int = ALL_DAYS_MASK,
)
