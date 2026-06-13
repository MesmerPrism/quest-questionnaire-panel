package io.github.mesmerprism.questquestionnaire.panel.update

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class PanelUpdateClient(
    private val context: Context,
    private val manifestUrl: String,
    private val currentPackageName: String,
    private val currentVersionCode: Long
) {
    fun checkForUpdate(): PanelUpdateCheckResult {
        if (manifestUrl.isBlank()) {
            return PanelUpdateCheckResult.NotConfigured
        }
        if (!manifestUrl.isHttpsUrl()) {
            return PanelUpdateCheckResult.Failed("manifest_url_not_https")
        }

        val manifestText = try {
            readHttpsText(manifestUrl)
        } catch (_: Exception) {
            return PanelUpdateCheckResult.Failed("manifest_fetch_failed")
        }

        val parsed = PanelUpdateManifest.parse(manifestText)
        if (parsed is UpdateManifestParseResult.Invalid) {
            return PanelUpdateCheckResult.Failed(parsed.code)
        }

        return when (
            val validation = (parsed as UpdateManifestParseResult.Valid)
                .manifest
                .validateFor(currentPackageName, currentVersionCode)
        ) {
            is UpdateManifestValidation.Available -> PanelUpdateCheckResult.Available(
                validation.manifest
            )
            is UpdateManifestValidation.Invalid -> PanelUpdateCheckResult.Failed(validation.code)
            UpdateManifestValidation.NoUpdate -> PanelUpdateCheckResult.UpToDate
        }
    }

    fun downloadApk(manifest: PanelUpdateManifest): PanelUpdateDownloadResult {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            return PanelUpdateDownloadResult.Failed("cache_create_failed")
        }

        val apkFile = File(updatesDir, "panel-${manifest.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            withHttpsConnection(manifest.apkUrl) { connection ->
                apkFile.outputStream().use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) {
                                break
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            apkFile.delete()
            return PanelUpdateDownloadResult.Failed("apk_download_failed")
        }

        val actualSha256 = digest.digest().joinToString(separator = "") { "%02x".format(it) }
        if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
            apkFile.delete()
            return PanelUpdateDownloadResult.Failed("sha256_mismatch")
        }

        return PanelUpdateDownloadResult.Ready(apkFile)
    }

    private fun readHttpsText(url: String): String =
        withHttpsConnection(url) { connection ->
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

    private fun <T> withHttpsConnection(
        url: String,
        block: (HttpURLConnection) -> T
    ): T {
        val connection = openHttpsConnection(url)
        return try {
            block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttpsConnection(url: String): HttpURLConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        return connection
    }

    private fun String.isHttpsUrl(): Boolean =
        try {
            val uri = URI(this)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
}

sealed interface PanelUpdateCheckResult {
    data object NotConfigured : PanelUpdateCheckResult
    data object UpToDate : PanelUpdateCheckResult
    data class Available(val manifest: PanelUpdateManifest) : PanelUpdateCheckResult
    data class Failed(val code: String) : PanelUpdateCheckResult
}

sealed interface PanelUpdateDownloadResult {
    data class Ready(val apkFile: File) : PanelUpdateDownloadResult
    data class Failed(val code: String) : PanelUpdateDownloadResult
}
