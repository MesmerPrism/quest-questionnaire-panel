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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.json.JSONObject

class QuestionnaireActivity : ComponentActivity() {
    private var request: QuestionnaireRequest? = null
    private var resultUri: Uri? = null
    private var returnToCaller: PendingIntent? = null
    private var startedAt: Instant = Instant.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launch = readLaunchContext() ?: return
        request = launch.request
        resultUri = launch.resultUri
        returnToCaller = launch.returnToCaller
        startedAt = Instant.now()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuestionnairePanel(
                        request = launch.request,
                        autoSubmit = BuildConfig.DEBUG &&
                            intent.getBooleanExtra(
                                QuestionnaireContract.ExtraDebugAutoSubmit,
                                false
                            ),
                        onSubmit = { answers -> submitResult("completed", answers) },
                        onCancel = { submitResult("cancelled", JSONObject()) }
                    )
                }
            }
        }
    }

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

    private fun submitResult(status: String, answers: JSONObject) {
        val currentRequest = request
        val uri = resultUri
        val callback = returnToCaller

        if (currentRequest == null || uri == null || callback == null) {
            showSubmissionError("missing_launch_state")
            return
        }

        val payload = QuestionnaireResult.from(
            request = currentRequest,
            status = status,
            answers = answers,
            startedAt = startedAt
        ).toJson()

        try {
            contentResolver.openOutputStream(uri, "wt").use { stream ->
                requireNotNull(stream) { "Could not open result URI for writing" }
                    .write(payload.toString(2).toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            showSubmissionError("result_write_failed")
            return
        }

        try {
            callback.send()
        } catch (_: PendingIntent.CanceledException) {
            showSubmissionError("callback_cancelled")
            return
        }

        setResult(Activity.RESULT_OK)
        finish()
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

    private data class LaunchContext(
        val request: QuestionnaireRequest,
        val resultUri: Uri,
        val returnToCaller: PendingIntent
    )
}

@Composable
private fun QuestionnairePanel(
    request: QuestionnaireRequest,
    autoSubmit: Boolean,
    onSubmit: (JSONObject) -> Unit,
    onCancel: () -> Unit
) {
    val selected = remember { mutableStateOf("not_answered") }

    LaunchedEffect(autoSubmit) {
        if (autoSubmit) {
            onSubmit(request.placeholderAnswers("yes"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Quest Questionnaire", style = MaterialTheme.typography.headlineMedium)
        Text("Study: ${request.studyId}")
        Text("Stage: ${request.openStage}")
        Text("Condition: ${request.conditionNumber ?: "none"}")
        Text("Request: ${request.requestId}")

        Text("MVP placeholder screen. Replace with BRB questionnaire components.")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { selected.value = "yes" }) {
                Text("Yes")
            }
            Button(onClick = { selected.value = "no" }) {
                Text("No")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                onSubmit(request.placeholderAnswers(selected.value))
            }) {
                Text("Submit")
            }
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

private fun QuestionnaireRequest.placeholderAnswers(placeholderAnswer: String): JSONObject =
    JSONObject()
        .put("placeholder_answer", placeholderAnswer)
        .put("open_stage", openStage)

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
