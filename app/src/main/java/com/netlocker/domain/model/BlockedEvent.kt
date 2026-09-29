package com.netlocker.domain.model

/** One blocked connection attempt's destination and when it happened — see
 *  [com.netlocker.domain.repository.BlockedEventsRepository]. */
data class BlockedEvent(
    val packageName: String,
    val destination: String,
    val atMillis: Long,
)
