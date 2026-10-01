package com.netlocker.domain.model

/** A newer release found on GitHub, ready to offer the user. */
data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val releaseUrl: String,
)

/** Result of asking "is there a newer version?" — never assumed, always the real answer
 *  from GitHub (spec's "no fake success" principle applies here too). */
sealed interface UpdateCheckResult {
    data class UpdateAvailable(val update: AppUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/** Result of fetching the latest published release's own notes — used for "What's new"
 *  (what does the version I already have include), not "is something newer available"
 *  (see [UpdateCheckResult] for that). */
sealed interface ReleaseNotesResult {
    data class Available(val versionName: String, val notes: String, val releaseUrl: String) : ReleaseNotesResult
    data object Unavailable : ReleaseNotesResult
}

/**
 * Compares two dot-separated version strings (a leading "v" is ignored) numerically,
 * component by component — "1.10.0" is newer than "1.9.0", unlike a plain string
 * comparison. Falls back to a straight string inequality only if either string has a
 * non-numeric component, so it never throws on an unexpected tag format.
 */
fun isNewerVersion(current: String, candidate: String): Boolean {
    val currentParts = current.removePrefix("v").split(".")
    val candidateParts = candidate.removePrefix("v").split(".")
    val currentNums = currentParts.map { it.toIntOrNull() }
    val candidateNums = candidateParts.map { it.toIntOrNull() }

    if (currentNums.any { it == null } || candidateNums.any { it == null }) {
        return candidate != current && candidate > current
    }

    val length = maxOf(currentNums.size, candidateNums.size)
    for (i in 0 until length) {
        val c = currentNums.getOrElse(i) { 0 } ?: 0
        val n = candidateNums.getOrElse(i) { 0 } ?: 0
        if (n != c) return n > c
    }
    return false
}
