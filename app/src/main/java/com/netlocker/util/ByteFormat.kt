package com.netlocker.util

import java.util.Locale

/** Human-readable size using 1000-based units, like Android's own data-usage screens:
 *  "0 B", "512 B", "1.5 KB", "12.3 MB", "1.20 GB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    val decimals = if (value >= 100) 0 else if (value >= 10) 1 else 2
    return String.format(Locale.US, "%.${decimals}f %s", value, units[unit])
}
