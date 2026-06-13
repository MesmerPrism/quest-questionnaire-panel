package io.github.mesmerprism.questquestionnaire.panel.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.mesmerprism.questquestionnaire.panel.BuildConfig
import java.io.File

object PanelUpdateInstaller {
    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    fun validateArchive(
        context: Context,
        apkFile: File,
        manifest: PanelUpdateManifest
    ): PanelUpdateArchiveValidation {
        val packageInfo = context.packageManager.packageArchiveInfo(apkFile)
            ?: return PanelUpdateArchiveValidation.Invalid("apk_parse_failed")

        if (packageInfo.packageName != context.packageName) {
            return PanelUpdateArchiveValidation.Invalid("apk_package_mismatch")
        }

        val archiveVersionCode = packageInfo.versionCodeLongCompat()
        if (archiveVersionCode != manifest.versionCode) {
            return PanelUpdateArchiveValidation.Invalid("apk_version_mismatch")
        }

        return PanelUpdateArchiveValidation.Valid
    }

    @Suppress("DEPRECATION")
    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.update_files",
            apkFile
        )

        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.packageArchiveInfo(apkFile: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            getPackageArchiveInfo(apkFile.absolutePath, 0)
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeLongCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
}

sealed interface PanelUpdateArchiveValidation {
    data object Valid : PanelUpdateArchiveValidation
    data class Invalid(val code: String) : PanelUpdateArchiveValidation
}
