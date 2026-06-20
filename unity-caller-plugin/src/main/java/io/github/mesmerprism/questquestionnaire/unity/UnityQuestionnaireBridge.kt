package io.github.mesmerprism.questquestionnaire.unity

import android.app.Activity
import android.content.BroadcastReceiver
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireCallerMetadata
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireLaunchPreflight
import io.github.mesmerprism.questquestionnaire.sdk.QuestionnaireLaunchRequestSpec
import io.github.mesmerprism.questquestionnaire.sdk.QuestQuestionnaireConfig
import io.github.mesmerprism.questquestionnaire.sdk.QuestQuestionnaireLauncher
import org.json.JSONArray
import org.json.JSONObject

object UnityQuestionnaireBridge {
    @JvmStatic
    fun launch(
        activity: Activity,
        resultAuthority: String,
        callbackReceiverClassName: String,
        sessionId: String,
        studyId: String,
        questionnaireId: String,
        openStage: String,
        screenSequenceJson: String,
        conditionNumber: Int,
        participantRef: String?,
        callerPackageName: String?,
        callerAppVersion: String?,
        questionnaireStateJson: String?,
        debugAutoSubmit: Boolean,
        debugCommandScript: String?,
        debugCommandIntervalMs: Int
    ): String {
        return try {
            val callbackReceiverClass = Class.forName(callbackReceiverClassName)
                .asSubclass(BroadcastReceiver::class.java)
            val launcher = QuestQuestionnaireLauncher(
                QuestQuestionnaireConfig(
                    resultAuthority = resultAuthority,
                    callbackReceiverClass = callbackReceiverClass
                )
            )

            when (launcher.preflight(activity)) {
                QuestionnaireLaunchPreflight.Ready -> Unit
                QuestionnaireLaunchPreflight.PanelNotInstalled ->
                    return status("panel_not_installed")
                QuestionnaireLaunchPreflight.PanelActivityUnavailable ->
                    return status("panel_activity_unavailable")
            }

            val prepared = launcher.prepare(
                context = activity,
                request = QuestionnaireLaunchRequestSpec(
                    sessionId = sessionId,
                    studyId = studyId,
                    questionnaireId = questionnaireId,
                    openStage = openStage,
                    screenSequence = screenSequenceJson.toStringList(),
                    conditionNumber = conditionNumber.takeIf { it >= 0 },
                    participantRef = participantRef.nullIfBlank(),
                    questionnaireState = questionnaireStateJson.nullIfBlank()?.let { JSONObject(it) },
                    caller = QuestionnaireCallerMetadata(
                        packageName = callerPackageName.nullIfBlank(),
                        appVersion = callerAppVersion.nullIfBlank(),
                        engine = "unity"
                    )
                ),
                debugAutoSubmit = debugAutoSubmit,
                debugCommandScript = debugCommandScript.nullIfBlank(),
                debugCommandIntervalMs = debugCommandIntervalMs.takeIf { it > 0 }
            )

            launcher.launch(activity, prepared)
            JSONObject()
                .put("status", "launched")
                .put("request_id", prepared.requestId)
                .put("nonce", prepared.nonce)
                .put("session_id", prepared.sessionId)
                .put("questionnaire_id", prepared.questionnaireId)
                .put("stage", prepared.stage)
                .toString()
        } catch (error: Exception) {
            JSONObject()
                .put("status", "error")
                .put("code", error.javaClass.simpleName.ifBlank { "BridgeError" })
                .put("message", error.message ?: "Unity questionnaire bridge failed.")
                .toString()
        }
    }

    private fun String.toStringList(): List<String> {
        val array = JSONArray(this)
        return (0 until array.length()).map { index ->
            val item = array.getString(index).trim()
            require(item.isNotEmpty()) { "screen_sequence_item_blank" }
            item
        }
    }

    private fun status(value: String): String =
        JSONObject().put("status", value).toString()

    private fun String?.nullIfBlank(): String? =
        this?.trim()?.ifEmpty { null }
}
