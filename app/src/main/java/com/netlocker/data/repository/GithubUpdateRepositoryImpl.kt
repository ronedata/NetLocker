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
                val reason = when (responseCode) {
                    HttpURLConnection.HTTP_NOT_FOUND -> "No releases have been published yet."
                    403 -> "GitHub API rate limit reached — try again later."
                    else -> "GitHub returned HTTP $responseCode."
                }
                connection.disconnect()
                return@withContext UpdateCheckResult.Error(reason)
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            val json = JSONObject(body)

            val tagName = json.optString("tag_name").ifBlank {
                return@withContext UpdateCheckResult.Error("Release had no version tag.")
            }
            val latestVersion = tagName.removePrefix("v")

            if (!isNewerVersion(currentVersionName, latestVersion)) {
                return@withContext UpdateCheckResult.UpToDate
            }

            val assets = json.optJSONArray("assets")
            val apkAsset = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: return@withContext UpdateCheckResult.Error("Newer version $latestVersion found, but it has no APK attached.")

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
            UpdateCheckResult.Error(e.message ?: "Could not reach GitHub.")
        }
    }

    companion object {
        private const val TAG = "GithubUpdateRepository"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
