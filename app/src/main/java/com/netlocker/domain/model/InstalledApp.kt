package com.netlocker.domain.model

import android.graphics.drawable.Drawable

/**
 * A single installed application as shown in the app list / details screen.
 *
 * Note: this intentionally carries an Android [Drawable] even though it lives in the
 * `domain` package. NetLocker only ever targets Android, so the usual "keep domain
 * platform-free" rule buys us nothing here and would just add a mapping layer with
 * no real benefit — a deliberate, scoped exception, not an accident.
 */
data class InstalledApp(
    val packageName: String,
    val uid: Int,
    val label: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val icon: Drawable?,
)
