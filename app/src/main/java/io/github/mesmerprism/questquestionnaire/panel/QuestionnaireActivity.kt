package io.github.mesmerprism.questquestionnaire.panel

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalContext
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalStatus
import java.time.Instant
import org.json.JSONObject

class QuestionnaireActivity : ComponentActivity() {
    private var request: QuestionnaireRequest? = null
    private var resultUri: Uri? = null
    private var returnToCaller: PendingIntent? = null
    private var startedAt: Instant = Instant.now()
    private val terminalResultWriter = TerminalResultWriter()
    private val rendererRegistry = DefaultQuestionnaireRendererRegistry.create()
    private val draftStore by lazy { QuestionnaireDraftStore.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launch = readLaunchContext() ?: return
        request = launch.request
        resultUri = launch.resultUri
        returnToCaller = launch.returnToCaller
        startedAt = Instant.now()

        try {
            showQuestionnaire(launch)
        } catch (_: Exception) {
            submitErrorResult(
                code = "renderer_initialization_failed",
                message = "Panel could not initialize the requested questionnaire.",
                currentStage = launch.request.openStage,
                screenIndex = launch.initialScreenIndex
            )
        }
    }

    private fun showQuestionnaire(launch: LaunchContext) {
        val renderer = rendererRegistry.rendererFor(launch.request)
        if (renderer == null) {
            submitErrorResult(
                code = "unsupported_questionnaire",
                message = "Panel has no renderer for the requested questionnaire.",
                currentStage = launch.request.openStage,
                screenIndex = launch.initialScreenIndex
            )
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    renderer.Render(
                        request = launch.request,
                        config = rendererConfig(),
                        callbacks = QuestionnaireRendererCallbacks(
                            onCompleted = { result ->
                                submitCompletedResult(
                                    answers = result.answers,
                                    currentStage = result.currentStage,
                                    screenIndex = result.screenIndex
                                )
                            },
                            onCancelled = { terminal ->
                                submitCancelledResult(
                                    currentStage = terminal.currentStage,
                                    screenIndex = terminal.screenIndex
                                )
                            },
                            onError = { error ->
                                submitErrorResult(
                                    code = error.code,
                                    message = error.message,
                                    currentStage = error.currentStage,
                                    screenIndex = error.screenIndex
                                )
                            }
                        )
                    )
                }
            }
        }
    }

    private fun rendererConfig(): QuestionnaireRendererConfig =
        QuestionnaireRendererConfig(
            autoSubmit = BuildConfig.DEBUG &&
                intent.getBooleanExtra(
                    QuestionnaireContract.ExtraDebugAutoSubmit,
                    false
                ),
            debugCommandScript = if (BuildConfig.DEBUG) {
                intent.getStringExtra(QuestionnaireContract.ExtraDebugCommandScript)
            } else {
                null
            },
            debugCommandIntervalMs = if (BuildConfig.DEBUG) {
                intent.getIntExtra(
                    QuestionnaireContract.ExtraDebugCommandIntervalMs,
                    350
                )
            } else {
                350
            },
            draftStore = draftStore
        )

    private fun readLaunchContext(): LaunchContext? {
        val uri = intent.parcelableExtra<Uri>(QuestionnaireContract.ExtraResultUri)
        val callback = intent.parcelableExtra<PendingIntent>(
            QuestionnaireContract.ExtraReturnToCaller
        )

        val validation = QuestionnaireLaunchValidator.validate(
            QuestionnaireLaunchSpec(
                action = intent.action,
                mimeType = intent.type,
                hasWriteGrant = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
                dataUri = intent.data?.toString(),
                resultUri = uri?.toString(),
                hasReturnToCaller = callback != null,
                requestJson = intent.getStringExtra(QuestionnaireContract.ExtraRequestJson),
                sessionIdExtra = intent.getStringExtra(QuestionnaireContract.ExtraSessionId),
                requestIdExtra = intent.getStringExtra(QuestionnaireContract.ExtraRequestId),
                nonceExtra = intent.getStringExtra(QuestionnaireContract.ExtraNonce)
            )
        )
        if (validation is QuestionnaireLaunchValidation.Invalid) {
            showLaunchError(validation.code)
            return null
        }

        return LaunchContext(
            request = (validation as QuestionnaireLaunchValidation.Valid).request,
            resultUri = requireNotNull(uri),
            returnToCaller = requireNotNull(callback)
        )
    }

    private fun submitCompletedResult(
        answers: JSONObject,
        currentStage: String,
        screenIndex: Int
    ) {
        submitTerminalResult(
            QuestionnaireResult.from(
                request = requireCurrentRequest() ?: return,
                status = QuestionnaireTerminalStatus.Completed,
                answers = answers,
                startedAt = startedAt,
                terminal = terminalContext(
                    reason = "completed",
                    currentStage = currentStage,
                    screenIndex = screenIndex
                )
            )
        )
    }

    private fun submitCancelledResult(currentStage: String, screenIndex: Int) {
        submitTerminalResult(
            QuestionnaireResult.from(
                request = requireCurrentRequest() ?: return,
                status = QuestionnaireTerminalStatus.Cancelled,
                answers = JSONObject(),
                startedAt = startedAt,
                terminal = terminalContext(
                    reason = "user_cancelled",
                    currentStage = currentStage,
                    screenIndex = screenIndex
                )
            )
        )
    }

    private fun submitErrorResult(
        code: String,
        message: String,
        currentStage: String,
        screenIndex: Int
    ) {
        submitTerminalResult(
            QuestionnaireResult.from(
                request = requireCurrentRequest() ?: return,
                status = QuestionnaireTerminalStatus.Error,
                answers = JSONObject(),
                startedAt = startedAt,
                terminal = terminalContext(
                    reason = "renderer_runtime_error",
                    currentStage = currentStage,
                    screenIndex = screenIndex
                ),
                error = QuestionnaireResultError(code = code, message = message)
            )
        )
    }

    private fun requireCurrentRequest(): QuestionnaireRequest? {
        val currentRequest = request
        if (currentRequest == null) {
            showSubmissionError("missing_launch_state")
        }
        return currentRequest
    }

    private fun terminalContext(
        reason: String,
        currentStage: String,
        screenIndex: Int
    ): QuestionnaireTerminalContext =
        QuestionnaireTerminalContext(
            reason = reason,
            currentStage = currentStage,
            screenIndex = screenIndex.coerceAtLeast(0)
        )

    private fun submitTerminalResult(result: QuestionnaireResult) {
        val currentRequest = request
        val uri = resultUri
        val callback = returnToCaller

        if (currentRequest == null || uri == null || callback == null) {
            showSubmissionError("missing_launch_state")
            return
        }

        when (
            terminalResultWriter.write(
                contentResolver = contentResolver,
                resultUri = uri,
                returnToCaller = callback,
                result = result
            )
        ) {
            TerminalResultWriteOutcome.CallbackSent -> {
                draftStore.clear(currentRequest)
                setResult(Activity.RESULT_OK)
                finish()
            }
            TerminalResultWriteOutcome.WriteFailed -> {
                showSubmissionError("result_write_failed")
            }
            TerminalResultWriteOutcome.CallbackFailedAfterWrite -> {
                draftStore.clear(currentRequest)
                showResultWrittenCallbackError()
            }
        }
    }

    private fun showLaunchError(code: String) {
        setResult(Activity.RESULT_CANCELED)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ErrorPanel(
                        title = "Questionnaire launch rejected",
                        code = code,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun showSubmissionError(code: String) {
        setResult(Activity.RESULT_CANCELED)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ErrorPanel(
                        title = "Questionnaire result was not sent",
                        code = code,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun showResultWrittenCallbackError() {
        setResult(Activity.RESULT_OK)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ErrorPanel(
                        title = "Questionnaire result was written",
                        code = "callback_failed_after_write",
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private data class LaunchContext(
        val request: QuestionnaireRequest,
        val resultUri: Uri,
        val returnToCaller: PendingIntent
    ) {
        val initialScreenIndex: Int =
            request.screenSequence.indexOf(request.openStage).coerceAtLeast(0)
    }
}

@Composable
private fun ErrorPanel(
    title: String,
    code: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text("Code: $code")
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}

private inline fun <reified T : Parcelable> Intent.parcelableExtra(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name) as? T
    }
