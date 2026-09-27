package com.netlocker.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.delay

/**
 * Downloads and installs an update APK using only standard, permission-respecting
 * Android APIs — [DownloadManager] for the download (no extra storage permission needed
 * on API 29+, since it writes to the public Downloads collection on the app's behalf)
 * and the system Package Installer for the install (triggered via a normal
 * `ACTION_VIEW` intent; Android itself shows the install confirmation screen — there is
 * no way for a non-privileged app to skip that, and NetLocker doesn't try to).
 *
 * Installing from a source other than Play Store requires the user to have granted
 * this app the "Install unknown apps" permission. That is a real, user-visible consent
 * screen ([requestInstallPermissionIntent]) — never silently assumed.
 */
class ApkInstaller(private val context: Context) {

    private val downloadManager by lazy { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    fun hasInstallPermission(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Launch this to take the user to the system screen where they grant (or deny)
     *  permission for NetLocker to install APKs it downloads itself. */
    fun requestInstallPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun enqueueDownload(downloadUrl: String, versionName: String): Long {
        val fileName = "NetLocker-v$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("NetLocker v$versionName")
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")
        return downloadManager.enqueue(request)
    }

    /** Polls [DownloadManager] until [downloadId] finishes. Returns the result; never
     *  assumes success — a failed/paused download is reported as such, not silently
     *  retried or hidden. */
    suspend fun awaitDownload(downloadId: Long): DownloadOutcome {
        while (true) {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            cursor.use {
                if (it.moveToFirst()) {
                    val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    when (it.getInt(statusIndex)) {
                        DownloadManager.STATUS_SUCCESSFUL -> return DownloadOutcome.Success
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            return DownloadOutcome.Failed("Download failed (reason code ${it.getInt(reasonIndex)})")
                        }
                        else -> Unit // still running/pending — keep polling
                    }
                } else {
                    return DownloadOutcome.Failed("Download disappeared from the download queue.")
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Builds the install intent for a completed download — this is what actually shows
     *  the system's "Do you want to install this app?" screen to the user. */
    fun installIntentFor(downloadId: Long): Intent {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
    }
}

sealed interface DownloadOutcome {
    data object Success : DownloadOutcome
    data class Failed(val reason: String) : DownloadOutcome
}
