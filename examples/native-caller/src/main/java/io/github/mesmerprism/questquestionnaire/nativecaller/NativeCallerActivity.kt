package io.github.mesmerprism.questquestionnaire.nativecaller

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class NativeCallerActivity : ComponentActivity() {
    private var statusState: MutableState<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shouldRunDebugSmoke = BuildConfig.DEBUG &&
            savedInstanceState == null &&
            intent.getBooleanExtra(QuestionnaireContract.ExtraDebugRunSmoke, false)

        setContent {
            val status = remember { mutableStateOf(readLatestResultSummary()) }
            statusState = status
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text("Questionnaire Caller Tester", style = MaterialTheme.typography.headlineMedium)
                        Text(status.value)
                        Button(onClick = {
                            launchQuestionnaire(debugAutoSubmit = false)
                            status.value = "Launched questionnaire panel."
                        }) {
                            Text("Open Questionnaire")
                        }
                        Button(onClick = { status.value = readLatestResultSummary() }) {
                            Text("Refresh Result")
                        }
                    }
                }
            }
        }

        if (shouldRunDebugSmoke) {
            window.decorView.post {
                launchQuestionnaire(debugAutoSubmit = true)
                statusState?.value = "Launched questionnaire panel."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        QuestionnaireResultStore.markResumeCheck(this)
        statusState?.value = readLatestResultSummary()
    }

    override fun onDestroy() {
        statusState = null
        super.onDestroy()
    }

    private fun launchQuestionnaire(debugAutoSubmit: Boolean) {
        val requestId = UUID.randomUUID().toString()
        val nonce = UUID.randomUUID().toString()
        val sessionId = "native-caller-${System.currentTimeMillis()}"
        val stage = QuestionnaireContract.DefaultStage
        val screenSequence = listOf(stage)
        val resultFile = resultFileFor(requestId)
        resultFile.parentFile?.mkdirs()
        if (resultFile.exists()) {
            resultFile.delete()
        }

        val resultUri = FileProvider.getUriForFile(
            this,
            "${packageName}.questionnaire.results",
            resultFile
        )

        val completionIntent = Intent(this, QuestionnaireReturnReceiver::class.java).apply {
            action = QuestionnaireContract.CompleteAction
            data = Uri.parse("app://${packageName}/questionnaire-return/$requestId")
        }

        val returnToCaller = PendingIntent.getBroadcast(
            this,
            requestId.hashCode(),
            completionIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or
                PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val requestJson = JSONObject()
            .put("protocol_version", QuestionnaireContract.ProtocolVersion)
            .put("session_id", sessionId)
            .put("request_id", requestId)
            .put("nonce", nonce)
            .put("study_id", "brb")
            .put("schema_id", QuestionnaireContract.QuestionnaireId)
            .put("open_stage", stage)
            .put("condition_number", JSONObject.NULL)
            .put("screen_sequence", JSONArray(screenSequence))

        QuestionnaireResultStore.rememberPending(
            context = this,
            requestId = requestId,
            nonce = nonce,
            questionnaireId = QuestionnaireContract.QuestionnaireId,
            stage = stage,
            screenSequence = screenSequence,
            resultPath = resultFile.absolutePath
        )

        val intent = Intent(QuestionnaireContract.StartAction).apply {
            component = ComponentName(
                QuestionnaireContract.PanelPackage,
                QuestionnaireContract.PanelActivity
            )
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(resultUri, QuestionnaireContract.RequestMimeType)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra(QuestionnaireContract.ExtraSessionId, sessionId)
            putExtra(QuestionnaireContract.ExtraRequestId, requestId)
            putExtra(QuestionnaireContract.ExtraNonce, nonce)
            putExtra(QuestionnaireContract.ExtraRequestJson, requestJson.toString())
            putExtra(QuestionnaireContract.ExtraResultUri, resultUri)
            putExtra(QuestionnaireContract.ExtraReturnToCaller, returnToCaller)
            if (BuildConfig.DEBUG && debugAutoSubmit) {
                putExtra(QuestionnaireContract.ExtraDebugAutoSubmit, true)
            }
        }

        startActivity(intent)
    }

    private fun resultFileFor(requestId: String): File =
        File(filesDir, "questionnaire-results/$requestId/result.json")

    private fun readLatestResultSummary(): String =
        QuestionnaireResultStore.readLatestResultSummary(this)
}
