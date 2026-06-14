package io.github.mesmerprism.questquestionnaire.sdk

import android.content.Context

data class PendingQuestionnaireRequest(
    val requestId: String,
    val nonce: String,
    val questionnaireId: String,
    val stage: String,
    val screenSequence: List<String>,
    val resultPath: String,
    val callbackReceived: Boolean
)

class PendingQuestionnaireStore(
    private val prefsName: String = "questionnaire-caller"
) {
    fun rememberPending(context: Context, prepared: PreparedQuestionnaireLaunch) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(LatestRequestId, prepared.requestId)
            .putString(LatestNonce, prepared.nonce)
            .putString(LatestQuestionnaireId, prepared.questionnaireId)
            .putString(LatestStage, prepared.stage)
            .putString(
                LatestScreenSequence,
                prepared.screenSequence.joinToString(separator = "\n")
            )
            .putString(LatestResultPath, prepared.resultFile.absolutePath)
            .remove(LatestCallbackRequestId)
            .apply()
    }

    fun markCallback(context: Context, requestId: String) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val latestRequestId = prefs.getString(LatestRequestId, null)
        if (requestId.isBlank() || latestRequestId != requestId) {
            return
        }

        prefs
            .edit()
            .putString(LatestCallbackRequestId, requestId)
            .apply()
    }

    fun markResumeCheck(context: Context) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putLong(LastResumeCheckAt, System.currentTimeMillis())
            .apply()
    }

    fun readPending(context: Context): PendingQuestionnaireRequest? {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val requestId = prefs.getString(LatestRequestId, null) ?: return null
        val stage = prefs.getString(LatestStage, null).orEmpty()
        val callbackRequestId = prefs.getString(LatestCallbackRequestId, null).orEmpty()
        return PendingQuestionnaireRequest(
            requestId = requestId,
            nonce = prefs.getString(LatestNonce, null).orEmpty(),
            questionnaireId = prefs.getString(LatestQuestionnaireId, null).orEmpty(),
            stage = stage,
            screenSequence = prefs.getString(LatestScreenSequence, stage)
                .orEmpty()
                .split("\n")
                .filter { it.isNotBlank() },
            resultPath = prefs.getString(LatestResultPath, null).orEmpty(),
            callbackReceived = callbackRequestId == requestId
        )
    }

    private companion object {
        const val LatestRequestId = "latest_request_id"
        const val LatestNonce = "latest_nonce"
        const val LatestQuestionnaireId = "latest_questionnaire_id"
        const val LatestStage = "latest_stage"
        const val LatestScreenSequence = "latest_screen_sequence"
        const val LatestResultPath = "latest_result_path"
        const val LatestCallbackRequestId = "latest_callback_request_id"
        const val LastResumeCheckAt = "last_resume_check_at"
    }
}

