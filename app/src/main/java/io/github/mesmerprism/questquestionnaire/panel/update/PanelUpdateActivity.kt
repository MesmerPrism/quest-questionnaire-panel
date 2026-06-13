package io.github.mesmerprism.questquestionnaire.panel.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mesmerprism.questquestionnaire.panel.BuildConfig
import java.io.File
import kotlin.concurrent.thread

class PanelUpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = remember {
                        mutableStateOf(
                            PanelUpdateScreenState(
                                message = initialMessage(),
                                isBusy = false,
                                manifest = null,
                                apkReady = false
                            )
                        )
                    }

                    PanelUpdateScreen(
                        state = state.value,
                        onCheck = {
                            state.value = state.value.copy(
                                isBusy = true,
                                message = "Checking for updates...",
                                apkReady = false
                            )
                            runInBackground(onComplete = { state.value = it }) {
                                checkForUpdateState()
                            }
                        },
                        onDownload = {
                            val manifest = state.value.manifest ?: return@PanelUpdateScreen
                            state.value = state.value.copy(
                                isBusy = true,
                                message = "Downloading update..."
                            )
                            runInBackground(onComplete = { state.value = it }) {
                                downloadUpdateState(manifest)
                            }
                        },
                        onInstall = {
                            val apkPath = state.value.downloadedApkPath ?: return@PanelUpdateScreen
                            if (!PanelUpdateInstaller.canRequestPackageInstalls(this)) {
                                try {
                                    startActivity(
                                        PanelUpdateInstaller.buildUnknownSourcesSettingsIntent(this)
                                    )
                                } catch (_: Exception) {
                                    state.value = state.value.copy(
                                        message = "Install-source settings are unavailable."
                                    )
                                }
                                return@PanelUpdateScreen
                            }

                            try {
                                startActivity(
                                    PanelUpdateInstaller.buildInstallIntent(
                                        this,
                                        File(apkPath)
                                    )
                                )
                            } catch (_: Exception) {
                                state.value = state.value.copy(
                                    message = "Android installer could not be opened."
                                )
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun initialMessage(): String =
        if (BuildConfig.UPDATE_MANIFEST_URL.isBlank()) {
            "No update manifest URL is configured for this build."
        } else {
            "Ready to check for questionnaire panel updates."
        }

    private fun checkForUpdateState(): PanelUpdateScreenState =
        when (val result = updateClient().checkForUpdate()) {
            is PanelUpdateCheckResult.Available -> PanelUpdateScreenState(
                message = "Update ${result.manifest.versionName} is available.",
                isBusy = false,
                manifest = result.manifest,
                apkReady = false
            )
            is PanelUpdateCheckResult.Failed -> PanelUpdateScreenState(
                message = "Update check failed: ${result.code}",
                isBusy = false,
                manifest = null,
                apkReady = false
            )
            PanelUpdateCheckResult.NotConfigured -> PanelUpdateScreenState(
                message = "No update manifest URL is configured for this build.",
                isBusy = false,
                manifest = null,
                apkReady = false
            )
            PanelUpdateCheckResult.UpToDate -> PanelUpdateScreenState(
                message = "This panel is up to date.",
                isBusy = false,
                manifest = null,
                apkReady = false
            )
        }

    private fun downloadUpdateState(manifest: PanelUpdateManifest): PanelUpdateScreenState =
        when (val result = updateClient().downloadApk(manifest)) {
            is PanelUpdateDownloadResult.Failed -> PanelUpdateScreenState(
                message = "Download failed: ${result.code}",
                isBusy = false,
                manifest = manifest,
                apkReady = false
            )
            is PanelUpdateDownloadResult.Ready -> validateDownloadedApkState(
                manifest = manifest,
                apkFile = result.apkFile
            )
        }

    private fun validateDownloadedApkState(
        manifest: PanelUpdateManifest,
        apkFile: File
    ): PanelUpdateScreenState =
        when (val validation = PanelUpdateInstaller.validateArchive(this, apkFile, manifest)) {
            is PanelUpdateArchiveValidation.Invalid -> {
                apkFile.delete()
                PanelUpdateScreenState(
                    message = "Downloaded APK rejected: ${validation.code}",
                    isBusy = false,
                    manifest = manifest,
                    apkReady = false
                )
            }
            PanelUpdateArchiveValidation.Valid -> PanelUpdateScreenState(
                message = "Update downloaded and verified.",
                isBusy = false,
                manifest = manifest,
                apkReady = true,
                downloadedApkPath = apkFile.absolutePath
            )
        }

    private fun updateClient(): PanelUpdateClient =
        PanelUpdateClient(
            context = this,
            manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
            currentPackageName = packageName,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        )

    private fun runInBackground(
        onComplete: (PanelUpdateScreenState) -> Unit,
        block: () -> PanelUpdateScreenState
    ) {
        thread(name = "panel-update", isDaemon = true) {
            val nextState = try {
                block()
            } catch (_: Exception) {
                PanelUpdateScreenState(
                    message = "Update failed: unexpected_error",
                    isBusy = false,
                    manifest = null,
                    apkReady = false
                )
            }
            runOnUiThread { onComplete(nextState) }
        }
    }
}

@Composable
private fun PanelUpdateScreen(
    state: PanelUpdateScreenState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Questionnaire Panel Updates", style = MaterialTheme.typography.headlineMedium)
        Text("Installed version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(state.message)

        state.manifest?.let { manifest ->
            Text("Available version: ${manifest.versionName} (${manifest.versionCode})")
            manifest.releaseNotes?.let { Text(it) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCheck,
                enabled = !state.isBusy && BuildConfig.UPDATE_MANIFEST_URL.isNotBlank()
            ) {
                Text("Check")
            }
            Button(
                onClick = onDownload,
                enabled = !state.isBusy && state.manifest != null && !state.apkReady
            ) {
                Text("Download")
            }
            Button(
                onClick = onInstall,
                enabled = !state.isBusy && state.apkReady
            ) {
                Text("Install")
            }
            Button(onClick = onClose, enabled = !state.isBusy) {
                Text("Close")
            }
        }
    }
}

private data class PanelUpdateScreenState(
    val message: String,
    val isBusy: Boolean,
    val manifest: PanelUpdateManifest?,
    val apkReady: Boolean,
    val downloadedApkPath: String? = null
)
