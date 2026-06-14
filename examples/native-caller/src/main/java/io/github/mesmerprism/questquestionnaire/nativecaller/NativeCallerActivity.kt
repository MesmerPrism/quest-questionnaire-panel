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
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireCallbackState
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireCallerMetadata
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireLaunchPreflight
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireLaunchRequestSpec
import io.github.mesmerprism.questquestionnaire.sdk.QuestQuestionnaireConfig
import io.github.mesmerprism.questquestionnaire.sdk.QuestQuestionnaireLauncher
import io.github.mesmerprism.questquestionnaire.sdk.RecoveredQuestionnaireResult

class NativeCallerActivity : ComponentActivity() {
    private var statusState: MutableState<String>? = null
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
                            status.value = launchQuestionnaire(debugAutoSubmit = false)
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
                statusState?.value = launchQuestionnaire(debugAutoSubmit = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launcher.markResumeCheck(this)
        statusState?.value = readLatestResultSummary()
    }

    override fun onDestroy() {
        statusState = null
        super.onDestroy()
    }

    private fun launchQuestionnaire(debugAutoSubmit: Boolean): String {
        when (launcher.preflight(this)) {
            QuestionnaireLaunchPreflight.Ready -> Unit
            QuestionnaireLaunchPreflight.PanelNotInstalled ->
                return "Questionnaire panel is not installed."
            QuestionnaireLaunchPreflight.PanelActivityUnavailable ->
                return "Questionnaire panel activity is unavailable."
        }

        val prepared = launcher.prepare(
            context = this,
            request = QuestionnaireLaunchRequestSpec(
                sessionId = "native-caller-${System.currentTimeMillis()}",
                studyId = "brb",
                questionnaireId = QuestionnaireContract.QuestionnaireId,
                openStage = QuestionnaireContract.DefaultStage,
                screenSequence = QuestionnaireContract.InitialStudySequence,
                caller = QuestionnaireCallerMetadata(
                    packageName = packageName,
                    appVersion = BuildConfig.VERSION_NAME,
                    engine = "native"
                )
            ),
            debugAutoSubmit = BuildConfig.DEBUG && debugAutoSubmit
        )

        launcher.launch(this, prepared)
        return "Launched questionnaire panel."
    }

    private fun readLatestResultSummary(): String =
        when (
            val result = launcher.recoverLatestResult(
                context = this,
                answerValidator = BrbQuestionnaireAnswerValidator
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
