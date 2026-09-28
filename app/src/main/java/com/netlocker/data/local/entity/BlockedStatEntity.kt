package com.netlocker.data.local.entity

import androidx.room.Entity

/** One row per (app, local day). Old days are pruned — this is a short-term activity view. */
@Entity(tableName = "blocked_stats", primaryKeys = ["packageName", "day"])
data class BlockedStatEntity(
    val packageName: String,
    val day: Long,
    val count: Int,
    val lastBlockedAt: Long,
)
