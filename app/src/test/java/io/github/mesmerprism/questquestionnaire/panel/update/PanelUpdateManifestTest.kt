package io.github.mesmerprism.questquestionnaire.panel.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelUpdateManifestTest {
    @Test
    fun parseValidManifest() {
        val result = PanelUpdateManifest.parse(validManifest(versionCode = 2))

        assertTrue(result is UpdateManifestParseResult.Valid)
        val manifest = (result as UpdateManifestParseResult.Valid).manifest
        assertEquals(PanelUpdateManifest.Schema, manifest.schema)
        assertEquals("io.github.mesmerprism.questquestionnaire.panel", manifest.packageName)
        assertEquals(2L, manifest.versionCode)
        assertEquals("0.2.0", manifest.versionName)
    }

    @Test
    fun validateRejectsWrongPackage() {
        val manifest = parse(validManifest(packageName = "example.other"))

        val validation = manifest.validateFor(
            currentPackageName = "io.github.mesmerprism.questquestionnaire.panel",
            currentVersionCode = 1
        )

        assertEquals(
            UpdateManifestValidation.Invalid("package_mismatch"),
            validation
        )
    }

    @Test
    fun validateRejectsHttpApkUrl() {
        val manifest = parse(validManifest(apkUrl = "http://example.com/panel.apk"))

        val validation = manifest.validateFor(
            currentPackageName = "io.github.mesmerprism.questquestionnaire.panel",
            currentVersionCode = 1
        )

        assertEquals(
            UpdateManifestValidation.Invalid("apk_url_not_https"),
            validation
        )
    }

    @Test
    fun validateRejectsMalformedApkUrl() {
        val manifest = parse(validManifest(apkUrl = "https://exa mple.com/panel.apk"))

        val validation = manifest.validateFor(
            currentPackageName = "io.github.mesmerprism.questquestionnaire.panel",
            currentVersionCode = 1
        )

        assertEquals(
            UpdateManifestValidation.Invalid("apk_url_not_https"),
            validation
        )
    }

    @Test
    fun validateReportsNoUpdateForSameVersion() {
        val manifest = parse(validManifest(versionCode = 1))

        val validation = manifest.validateFor(
            currentPackageName = "io.github.mesmerprism.questquestionnaire.panel",
            currentVersionCode = 1
        )

        assertEquals(UpdateManifestValidation.NoUpdate, validation)
    }

    @Test
    fun validateAcceptsNewerVersion() {
        val manifest = parse(validManifest(versionCode = 2))

        val validation = manifest.validateFor(
            currentPackageName = "io.github.mesmerprism.questquestionnaire.panel",
            currentVersionCode = 1
        )

        assertTrue(validation is UpdateManifestValidation.Available)
    }

    @Test
    fun parseRejectsBadSha256() {
        val result = PanelUpdateManifest.parse(validManifest(sha256 = "not-a-hash"))
        val manifest = (result as UpdateManifestParseResult.Valid).manifest

        val validation = manifest.validateFor(
            currentPackageName = "io.github.mesmerprism.questquestionnaire.panel",
            currentVersionCode = 1
        )

        assertEquals(UpdateManifestValidation.Invalid("sha256_invalid"), validation)
    }

    private fun parse(json: String): PanelUpdateManifest {
        val result = PanelUpdateManifest.parse(json)
        assertTrue(result is UpdateManifestParseResult.Valid)
        return (result as UpdateManifestParseResult.Valid).manifest
    }

    private fun validManifest(
        packageName: String = "io.github.mesmerprism.questquestionnaire.panel",
        versionCode: Long = 2,
        apkUrl: String = "https://example.com/panel.apk",
        sha256: String = "a".repeat(64)
    ): String =
        """
        {
          "schema": "${PanelUpdateManifest.Schema}",
          "packageName": "$packageName",
          "versionCode": $versionCode,
          "versionName": "0.2.0",
          "apkUrl": "$apkUrl",
          "sha256": "$sha256",
          "releaseNotes": "Test build",
          "publishedAt": "2026-06-11T00:00:00Z"
        }
        """.trimIndent()
}
