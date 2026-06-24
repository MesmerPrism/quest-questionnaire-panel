package io.github.mesmerprism.questquestionnaire.sdk

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireAnswerValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireExpectedResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelopeValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultValidation
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalStatus
import io.github.mesmerprism.questquestionnaire.contract.QuestQuestionnaireProtocol
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class QuestQuestionnaireConfig(
    val resultAuthority: String,
    val callbackReceiverClass: Class<out BroadcastReceiver>,
    val panelPackage: String = QuestQuestionnaireIntentContract.PanelPackage,
    val panelActivity: String = QuestQuestionnaireIntentContract.PanelActivity,
    val completeAction: String = QuestQuestionnaireIntentContract.CompleteAction,
    val resultDirectoryName: String = "questionnaire-results"
)

data class QuestionnaireLaunchRequestSpec(
    val sessionId: String,
    val studyId: String,
    val questionnaireId: String,
    val openStage: String,
    val screenSequence: List<String>,
    val conditionNumber: Int? = null,
    val participantRef: String? = null,
    val questionnaireState: JSONObject? = null,
    val caller: QuestionnaireCallerMetadata? = null
)

data class QuestionnaireCallerMetadata(
    val packageName: String? = null,
    val appVersion: String? = null,
    val engine: String? = null
)

data class PreparedQuestionnaireLaunch(
    val requestId: String,
    val nonce: String,
    val sessionId: String,
    val questionnaireId: String,
    val stage: String,
    val screenSequence: List<String>,
    val resultFile: File,
    val resultUri: Uri,
    val requestJson: String,
    val intent: Intent
)

class QuestQuestionnaireLauncher(
    private val config: QuestQuestionnaireConfig,
    private val pendingStore: PendingQuestionnaireStore = PendingQuestionnaireStore()
) {
    fun preflight(context: Context): QuestionnaireLaunchPreflight {
        if (!panelPackageInstalled(context)) {
            return QuestionnaireLaunchPreflight.PanelNotInstalled
        }

        val intent = Intent(QuestQuestionnaireIntentContract.StartAction).apply {
            component = ComponentName(config.panelPackage, config.panelActivity)
            addCategory(Intent.CATEGORY_DEFAULT)
            type = QuestQuestionnaireIntentContract.RequestMimeType
        }

        @Suppress("DEPRECATION")
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return if (resolved == null) {
            QuestionnaireLaunchPreflight.PanelActivityUnavailable
        } else {
            QuestionnaireLaunchPreflight.Ready
        }
    }

    fun prepare(
        context: Context,
        request: QuestionnaireLaunchRequestSpec,
        debugAutoSubmit: Boolean = false,
        debugCommandScript: String? = null,
        debugCommandIntervalMs: Int? = null
    ): PreparedQuestionnaireLaunch {
        val requestId = UUID.randomUUID().toString()
        val nonce = UUID.randomUUID().toString()
        val resultFile = resultFileFor(context, requestId)
        resultFile.parentFile?.mkdirs()
        if (resultFile.exists()) {
            resultFile.delete()
        }

        val resultUri = FileProvider.getUriForFile(
            context,
            config.resultAuthority,
            resultFile
        )
        val requestJson = request.toJson(requestId = requestId, nonce = nonce).toString()
        val returnToCaller = callbackFor(context, requestId)
        val returnToCallerForeground = foregroundReturnFor(context, requestId)
        val intent = Intent(QuestQuestionnaireIntentContract.StartAction).apply {
            component = ComponentName(config.panelPackage, config.panelActivity)
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(resultUri, QuestQuestionnaireIntentContract.RequestMimeType)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra(QuestQuestionnaireIntentContract.ExtraSessionId, request.sessionId)
            putExtra(QuestQuestionnaireIntentContract.ExtraRequestId, requestId)
            putExtra(QuestQuestionnaireIntentContract.ExtraNonce, nonce)
            putExtra(QuestQuestionnaireIntentContract.ExtraRequestJson, requestJson)
            putExtra(QuestQuestionnaireIntentContract.ExtraResultUri, resultUri)
            putExtra(QuestQuestionnaireIntentContract.ExtraReturnToCaller, returnToCaller)
            returnToCallerForeground?.let {
                putExtra(QuestQuestionnaireIntentContract.ExtraReturnToCallerForeground, it)
            }
            if (debugAutoSubmit) {
                putExtra(QuestQuestionnaireIntentContract.ExtraDebugAutoSubmit, true)
            }
            if (!debugCommandScript.isNullOrBlank()) {
                putExtra(
                    QuestQuestionnaireIntentContract.ExtraDebugCommandScript,
                    debugCommandScript
                )
            }
            debugCommandIntervalMs?.let {
                putExtra(
                    QuestQuestionnaireIntentContract.ExtraDebugCommandIntervalMs,
                    it
                )
            }
        }

        val prepared = PreparedQuestionnaireLaunch(
            requestId = requestId,
            nonce = nonce,
            sessionId = request.sessionId,
            questionnaireId = request.questionnaireId,
            stage = request.openStage,
            screenSequence = request.screenSequence,
            resultFile = resultFile,
            resultUri = resultUri,
            requestJson = requestJson,
            intent = intent
        )
        pendingStore.rememberPending(context, prepared)
        return prepared
    }

    fun launch(activity: Activity, prepared: PreparedQuestionnaireLaunch) {
        activity.startActivity(prepared.intent)
    }

    fun markCallback(context: Context, requestId: String) {
        pendingStore.markCallback(context, requestId)
    }

    fun markResumeCheck(context: Context) {
        pendingStore.markResumeCheck(context)
    }

    fun recoverLatestResult(
        context: Context,
        answerValidator: QuestionnaireAnswerValidator
    ): RecoveredQuestionnaireResult {
        val pending = pendingStore.readPending(context)
            ?: return RecoveredQuestionnaireResult.NoPending
        val callbackState = if (pending.callbackReceived) {
            QuestionnaireCallbackState.Received
        } else {
            QuestionnaireCallbackState.Pending
        }
        val resultFile = File(pending.resultPath)

        if (!resultFile.exists()) {
            return RecoveredQuestionnaireResult.Pending(callbackState)
        }

        val expected = QuestionnaireExpectedResult(
            requestId = pending.requestId,
            nonce = pending.nonce,
            questionnaireId = pending.questionnaireId,
            stage = pending.stage,
            screenSequence = pending.screenSequence
        )

        return when (
            val validation = QuestionnaireResultEnvelopeValidator.validate(
                resultJson = resultFile.readText(Charsets.UTF_8),
                expected = expected,
                answerValidator = answerValidator
            )
        ) {
            is QuestionnaireResultValidation.Valid ->
                RecoveredQuestionnaireResult.Valid(validation.status, callbackState)
            is QuestionnaireResultValidation.Invalid ->
                RecoveredQuestionnaireResult.Invalid(validation.reason, callbackState)
        }
    }

    private fun resultFileFor(context: Context, requestId: String): File =
        File(context.filesDir, "${config.resultDirectoryName}/$requestId/result.json")

    private fun panelPackageInstalled(context: Context): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    config.panelPackage,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(config.panelPackage, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun callbackFor(context: Context, requestId: String): PendingIntent {
        val completionIntent = Intent(context, config.callbackReceiverClass).apply {
            action = config.completeAction
            data = Uri.parse("app://${context.packageName}/questionnaire-return/$requestId")
        }

        return PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            completionIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or
                PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun foregroundReturnFor(context: Context, requestId: String): PendingIntent? {
        val activity = context as? Activity ?: return null
        val foregroundIntent = Intent(activity, activity.javaClass).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            data = Uri.parse("app://${context.packageName}/questionnaire-foreground/$requestId")
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        return PendingIntent.getActivity(
            context,
            "foreground:$requestId".hashCode(),
            foregroundIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or
                PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }
}

sealed class QuestionnaireLaunchPreflight {
    object Ready : QuestionnaireLaunchPreflight()
    object PanelNotInstalled : QuestionnaireLaunchPreflight()
    object PanelActivityUnavailable : QuestionnaireLaunchPreflight()
}

enum class QuestionnaireCallbackState {
    Pending,
    Received
}

sealed class RecoveredQuestionnaireResult {
    object NoPending : RecoveredQuestionnaireResult()
    data class Pending(val callbackState: QuestionnaireCallbackState) :
        RecoveredQuestionnaireResult()

    data class Valid(
        val status: QuestionnaireTerminalStatus,
        val callbackState: QuestionnaireCallbackState
    ) : RecoveredQuestionnaireResult()

    data class Invalid(
        val reason: String,
        val callbackState: QuestionnaireCallbackState
    ) : RecoveredQuestionnaireResult()
}

private fun QuestionnaireLaunchRequestSpec.toJson(requestId: String, nonce: String): JSONObject =
    JSONObject()
        .put("protocol_version", QuestQuestionnaireProtocol.Version)
        .put("session_id", sessionId)
        .put("request_id", requestId)
        .put("nonce", nonce)
        .put("study_id", studyId)
        .put("schema_id", questionnaireId)
        .put("open_stage", openStage)
        .put("condition_number", conditionNumber ?: JSONObject.NULL)
        .put("screen_sequence", JSONArray(screenSequence))
        .apply {
            participantRef?.let { put("participant_ref", it) }
            questionnaireState?.let { put("questionnaire_state", JSONObject(it.toString())) }
            caller?.toJson()?.let { put("caller", it) }
        }

private fun QuestionnaireCallerMetadata.toJson(): JSONObject =
    JSONObject().apply {
        packageName?.let { put("package", it) }
        appVersion?.let { put("app_version", it) }
        engine?.let { put("engine", it) }
    }
