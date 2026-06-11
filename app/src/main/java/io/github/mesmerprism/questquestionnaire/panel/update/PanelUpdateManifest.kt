package io.github.mesmerprism.questquestionnaire.panel.update

import java.net.URI
import org.json.JSONException
import org.json.JSONObject

data class PanelUpdateManifest(
    val schema: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String?,
    val publishedAt: String?
) {
    fun validateFor(currentPackageName: String, currentVersionCode: Long): UpdateManifestValidation =
        when {
            schema != Schema -> UpdateManifestValidation.Invalid("unsupported_schema")
            packageName != currentPackageName -> UpdateManifestValidation.Invalid("package_mismatch")
            versionCode <= currentVersionCode -> UpdateManifestValidation.NoUpdate
            !isHttpsUrl(apkUrl) -> UpdateManifestValidation.Invalid("apk_url_not_https")
            !Sha256Pattern.matches(sha256) -> UpdateManifestValidation.Invalid("sha256_invalid")
            else -> UpdateManifestValidation.Available(this)
        }

    companion object {
        const val Schema = "quest-questionnaire-panel.update.v1"
        private val Sha256Pattern = Regex("^[A-Fa-f0-9]{64}$")

        fun parse(jsonText: String): UpdateManifestParseResult =
            try {
                val json = JSONObject(jsonText)
                val manifest = PanelUpdateManifest(
                    schema = json.requiredString("schema"),
                    packageName = json.requiredString("packageName"),
                    versionCode = json.requiredLong("versionCode"),
                    versionName = json.requiredString("versionName"),
                    apkUrl = json.requiredString("apkUrl"),
                    sha256 = json.requiredString("sha256").lowercase(),
                    releaseNotes = json.optionalString("releaseNotes"),
                    publishedAt = json.optionalString("publishedAt")
                )
                UpdateManifestParseResult.Valid(manifest)
            } catch (_: JSONException) {
                UpdateManifestParseResult.Invalid("invalid_json")
            } catch (error: IllegalArgumentException) {
                UpdateManifestParseResult.Invalid(error.message ?: "invalid_manifest")
            }

        private fun JSONObject.requiredString(name: String): String {
            val value = optString(name, "").trim()
            require(value.isNotEmpty()) { "${name}_missing" }
            return value
        }

        private fun JSONObject.requiredLong(name: String): Long {
            require(has(name)) { "${name}_missing" }
            val value = optLong(name, -1L)
            require(value > 0L) { "${name}_invalid" }
            return value
        }

        private fun JSONObject.optionalString(name: String): String? {
            val value = optString(name, "").trim()
            return value.ifEmpty { null }
        }

        private fun isHttpsUrl(value: String): Boolean =
            try {
                val uri = URI(value)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            } catch (_: Exception) {
                false
            }
    }
}

sealed interface UpdateManifestParseResult {
    data class Valid(val manifest: PanelUpdateManifest) : UpdateManifestParseResult
    data class Invalid(val code: String) : UpdateManifestParseResult
}

sealed interface UpdateManifestValidation {
    data class Available(val manifest: PanelUpdateManifest) : UpdateManifestValidation
    data object NoUpdate : UpdateManifestValidation
    data class Invalid(val code: String) : UpdateManifestValidation
}
