package io.github.mesmerprism.questquestionnaire.nativecaller

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
import io.github.mesmerprism.questquestionnaire.maiaspatial.MaiaSpatialQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireCallbackState
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireCallerMetadata
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireLaunchPreflight
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireLaunchRequestSpec
import io.github.mesmerprism.questquestionnaire.sdk.QuestQuestionnaireConfig
import io.github.mesmerprism.questquestionnaire.sdk.QuestQuestionnaireLauncher
import io.github.mesmerprism.questquestionnaire.sdk.RecoveredQuestionnaireResult
import org.json.JSONObject

class NativeCallerActivity : ComponentActivity() {
    private var statusState: MutableState<String>? = null
    private var operatorBridge: QuestQuestionnaireOperatorBridge? = null
    private val maiaSpatialSessionId: String by lazy {
        "native-maia-spatial-${System.currentTimeMillis()}"
    }
    private val launcher: QuestQuestionnaireLauncher by lazy {
        QuestQuestionnaireLauncher(
            QuestQuestionnaireConfig(
                resultAuthority = "${packageName}.questionnaire.results",
                callbackReceiverClass = QuestionnaireReturnReceiver::class.java
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        operatorBridge = QuestQuestionnaireOperatorBridge(this).also { it.start() }
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
                            status.value = launchBrbQuestionnaire(debugAutoSubmit = false)
                        }) {
                            Text("Open BRB Questionnaire")
                        }
                        Button(onClick = {
                            status.value = launchMaiaSpatialBlockOne(debugAutoSubmit = false)
                        }) {
                            Text("Open MAIA Setup")
                        }
                        Button(onClick = {
                            status.value = launchSpatialFrameBlock(
                                screenSequence = MaiaSpatialQuestionnaireContract
                                    .BlockTwoSpatialFrameReferenceSequence
                            )
                        }) {
                            Text("Open Spatial Frame Block 2")
                        }
                        Button(onClick = {
                            status.value = launchSpatialFrameBlock(
                                screenSequence = MaiaSpatialQuestionnaireContract
                                    .BlockThreeSpatialFrameReferenceSequence
                            )
                        }) {
                            Text("Open Spatial Frame Block 3")
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
                statusState?.value = launchBrbQuestionnaire(debugAutoSubmit = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launcher.markResumeCheck(this)
        val summary = readLatestResultSummary()
        operatorBridge?.markCallerForeground(summary)
        statusState?.value = summary
    }

    override fun onDestroy() {
        statusState = null
        operatorBridge?.stop()
        operatorBridge = null
        super.onDestroy()
    }

    private fun launchBrbQuestionnaire(debugAutoSubmit: Boolean): String =
        launchQuestionnaire(
            request = QuestionnaireLaunchRequestSpec(
                sessionId = "native-caller-${System.currentTimeMillis()}",
                studyId = "brb",
                questionnaireId = QuestionnaireContract.QuestionnaireId,
                openStage = QuestionnaireContract.DefaultStage,
                screenSequence = QuestionnaireContract.InitialStudySequence,
                caller = callerMetadata()
            ),
            debugAutoSubmit = debugAutoSubmit,
            debugCommandScript = null,
            debugCommandIntervalMs = null
        )

    private fun launchMaiaSpatialBlockOne(debugAutoSubmit: Boolean): String =
        launchQuestionnaire(
            request = QuestionnaireLaunchRequestSpec(
                sessionId = maiaSpatialSessionId,
                studyId = "maia-spatial",
                questionnaireId = MaiaSpatialQuestionnaireContract.QuestionnaireId,
                openStage = MaiaSpatialQuestionnaireContract.StageLanguageSelection,
                screenSequence = MaiaSpatialQuestionnaireContract.BlockOneSetupMaia2Sequence,
                caller = callerMetadata()
            ),
            debugAutoSubmit = debugAutoSubmit,
            debugCommandScript = null,
            debugCommandIntervalMs = null
        )

    private fun launchSpatialFrameBlock(screenSequence: List<String>): String {
        val openStage = screenSequence.first()
        return launchQuestionnaire(
            request = QuestionnaireLaunchRequestSpec(
                sessionId = maiaSpatialSessionId,
                studyId = "maia-spatial",
                questionnaireId = MaiaSpatialQuestionnaireContract.QuestionnaireId,
                openStage = openStage,
                screenSequence = screenSequence,
                questionnaireState = JSONObject().put("language_code", "en"),
                caller = callerMetadata()
            ),
            debugAutoSubmit = false,
            debugCommandScript = null,
            debugCommandIntervalMs = null
        )
    }

    private fun launchQuestionnaire(
        request: QuestionnaireLaunchRequestSpec,
        debugAutoSubmit: Boolean,
        debugCommandScript: String?,
        debugCommandIntervalMs: Int?
    ): String {
        when (launcher.preflight(this)) {
            QuestionnaireLaunchPreflight.Ready -> Unit
            QuestionnaireLaunchPreflight.PanelNotInstalled ->
                return "Questionnaire panel is not installed."
            QuestionnaireLaunchPreflight.PanelActivityUnavailable ->
                return "Questionnaire panel activity is unavailable."
        }

        val prepared = launcher.prepare(
            context = this,
            request = request,
            debugAutoSubmit = BuildConfig.DEBUG && debugAutoSubmit
        )
        if (BuildConfig.DEBUG && !debugCommandScript.isNullOrBlank()) {
            prepared.intent.putExtra(QuestionnaireContract.ExtraDebugCommandScript, debugCommandScript)
            debugCommandIntervalMs?.let {
                prepared.intent.putExtra(QuestionnaireContract.ExtraDebugCommandIntervalMs, it)
            }
        }

        launcher.launch(this, prepared)
        return "Launched questionnaire panel."
    }

    internal fun launchOperatorBridgeQuestionnaire(payload: JSONObject): OperatorBridgeLaunchResult {
        val panelRequest = payload.optJSONObject("panel_request")
            ?: return OperatorBridgeLaunchResult.rejected("Missing panel_request.")
        val screenSequence = panelRequest.optJSONArray("screen_sequence")
            ?.let { array -> (0 until array.length()).map { array.getString(it) } }
            .orEmpty()
        val openStage = panelRequest.optString("open_stage")
        if (openStage.isBlank() || screenSequence.isEmpty()) {
            return OperatorBridgeLaunchResult.rejected("panel_request is missing open_stage or screen_sequence.")
        }
        val callerHint = panelRequest.optJSONObject("caller_hint")
        val request = QuestionnaireLaunchRequestSpec(
            sessionId = panelRequest.optString("session_id"),
            studyId = panelRequest.optString("study_id"),
            questionnaireId = panelRequest.optString("schema_id"),
            openStage = openStage,
            screenSequence = screenSequence,
            participantRef = panelRequest.optString("participant_ref").takeIf { it.isNotBlank() },
            questionnaireState = panelRequest.optJSONObject("questionnaire_state"),
            caller = QuestionnaireCallerMetadata(
                packageName = packageName,
                appVersion = BuildConfig.VERSION_NAME,
                engine = callerHint?.optString("engine")?.takeIf { it.isNotBlank() } ?: "native"
            )
        )
        val message = launchQuestionnaire(
            request = request,
            debugAutoSubmit = BuildConfig.DEBUG && payload.optBoolean("debug_auto_submit", false),
            debugCommandScript = payload.optString("debug_command_script")
                .takeIf { it.isNotBlank() },
            debugCommandIntervalMs = if (payload.has("debug_command_interval_ms")) {
                payload.optInt("debug_command_interval_ms")
            } else {
                null
            }
        )
        return OperatorBridgeLaunchResult(
            accepted = message == "Launched questionnaire panel.",
            message = message,
            sessionId = request.sessionId,
            questionnaireId = request.questionnaireId,
            openStage = request.openStage
        )
    }

    internal fun operatorBridgeStatusSummary(): String = readLatestResultSummary()

    private fun callerMetadata(): QuestionnaireCallerMetadata =
        QuestionnaireCallerMetadata(
            packageName = packageName,
            appVersion = BuildConfig.VERSION_NAME,
            engine = "native"
        )

    private fun readLatestResultSummary(): String =
        when (
            val result = launcher.recoverLatestResult(
                context = this,
                answerValidator = NativeCallerAnswerValidator
            )
        ) {
            RecoveredQuestionnaireResult.NoPending ->
                "No request launched yet."
            is RecoveredQuestionnaireResult.Pending ->
                "Pending request. No result file yet. Callback: ${result.callbackState.summaryText()}"
            is RecoveredQuestionnaireResult.Valid ->
                "Result status=${result.status.wireValue} valid=true " +
                    "callback=${result.callbackState.summaryText()}"
            is RecoveredQuestionnaireResult.Invalid ->
                "Result invalid reason=${result.reason} " +
                    "callback=${result.callbackState.summaryText()}"
        }

    private fun QuestionnaireCallbackState.summaryText(): String =
        when (this) {
            QuestionnaireCallbackState.Pending -> "pending"
            QuestionnaireCallbackState.Received -> "received"
        }
}
