package com.netlocker.data.repository

import com.netlocker.domain.model.AppUpdate
import com.netlocker.domain.model.UpdateCheckResult
import com.netlocker.domain.model.isNewerVersion
import com.netlocker.domain.repository.UpdateRepository
import com.netlocker.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub's public "latest release" API for a newer version. Deliberately uses
 * only [HttpURLConnection] + the built-in [org.json] parser rather than adding an HTTP
 * client / JSON library — this is one simple GET request, so a new dependency would be
 * exactly the "unnecessary dependency" spec §26 asks NetLocker to avoid.
 *
 * No auth token is used (the repo and its releases are public); GitHub's unauthenticated
 * rate limit (60 requests/hour per IP) is more than enough for a user tapping "Check for
 * updates" occasionally.
 */
class GithubUpdateRepositoryImpl(
    private val currentVersionName: String,
    private val repoOwner: String = "ronedata",
    private val repoName: String = "NetLocker",
) : UpdateRepository {

    override suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // GitHub's API rejects requests with no User-Agent.
            connection.setRequestProperty("User-Agent", "NetLocker-App")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                // 404 = nothing has been published, i.e. nothing newer than what's installed.
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext UpdateCheckResult.UpToDate
                return@withContext UpdateCheckResult.Error(
                    if (responseCode == 403) MESSAGE_TRY_LATER else MESSAGE_GENERIC_FAILURE,
                )
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            val json = JSONObject(body)

            val tagName = json.optString("tag_name").ifBlank {
                return@withContext UpdateCheckResult.Error(MESSAGE_GENERIC_FAILURE)
            }
            val latestVersion = tagName.removePrefix("v")

            if (!isNewerVersion(currentVersionName, latestVersion)) {
                return@withContext UpdateCheckResult.UpToDate
            }

            val assets = json.optJSONArray("assets")
            val apkAsset = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: return@withContext UpdateCheckResult.Error(
                    "Version $latestVersion is available but can't be downloaded yet. Please try again later.",
                )

            UpdateCheckResult.UpdateAvailable(
                AppUpdate(
                    versionName = latestVersion,
                    downloadUrl = apkAsset.getString("browser_download_url"),
                    releaseNotes = json.optString("body"),
                    releaseUrl = json.optString("html_url"),
                ),
            )
        } catch (e: Exception) {
            Logger.w(TAG, "update check failed", e)
            UpdateCheckResult.Error(friendlyUpdateError(e))
        }
    }

    companion object {
        private const val TAG = "GithubUpdateRepository"
        const val MESSAGE_NO_CONNECTION = "Couldn't check for updates. Please check your internet connection and try again."
        const val MESSAGE_TRY_LATER = "Couldn't check for updates right now. Please try again in a little while."
        const val MESSAGE_GENERIC_FAILURE = "Couldn't check for updates. Please try again later."

        /** Plain-language reason for a failed check — never a raw exception message
         *  ("Unable to resolve host …") and never a mention of where updates come from. */
        fun friendlyUpdateError(e: Exception): String = when (e) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.net.NoRouteToHostException,
            is javax.net.ssl.SSLException,
            -> MESSAGE_NO_CONNECTION
            else -> MESSAGE_GENERIC_FAILURE
        }
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
