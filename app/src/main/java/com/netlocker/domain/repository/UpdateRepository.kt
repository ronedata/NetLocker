package com.netlocker.domain.repository

import com.netlocker.domain.model.ReleaseNotesResult
import com.netlocker.domain.model.UpdateCheckResult

/** Checks GitHub Releases for a newer version than the one currently installed. */
interface UpdateRepository {
    suspend fun checkForUpdate(): UpdateCheckResult

    /** The latest published release's own notes — for "What's new", not update-checking. */
    suspend fun fetchLatestReleaseNotes(): ReleaseNotesResult
}
