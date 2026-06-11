package io.github.mesmerprism.questquestionnaire.nativecaller

import android.content.Context
import java.io.File

object QuestionnaireResultStore {
    private const val Prefs = "questionnaire-caller"
    private const val LatestRequestId = "latest_request_id"
    private const val LatestNonce = "latest_nonce"
    private const val LatestQuestionnaireId = "latest_questionnaire_id"
    private const val LatestStage = "latest_stage"
    private const val LatestScreenSequence = "latest_screen_sequence"
    private const val LatestResultPath = "latest_result_path"
    private const val LatestCallbackRequestId = "latest_callback_request_id"
    private const val LastResumeCheckAt = "last_resume_check_at"

    fun rememberPending(
        context: Context,
        requestId: String,
        nonce: String,
        questionnaireId: String,
        stage: String,
        screenSequence: List<String>,
        resultPath: String
    ) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .edit()
            .putString(LatestRequestId, requestId)
            .putString(LatestNonce, nonce)
            .putString(LatestQuestionnaireId, questionnaireId)
            .putString(LatestStage, stage)
            .putString(LatestScreenSequence, screenSequence.joinToString(separator = "\n"))
            .putString(LatestResultPath, resultPath)
            .remove(LatestCallbackRequestId)
            .apply()
    }

    fun markCallback(context: Context, requestId: String) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
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
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .edit()
            .putLong(LastResumeCheckAt, System.currentTimeMillis())
            .apply()
    }

    fun readLatestResultSummary(context: Context): String {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val requestId = prefs.getString(LatestRequestId, null) ?: return "No request launched yet."
        val nonce = prefs.getString(LatestNonce, null).orEmpty()
        val questionnaireId = prefs.getString(
            LatestQuestionnaireId,
            QuestionnaireContract.QuestionnaireId
        ).orEmpty()
        val stage = prefs.getString(LatestStage, QuestionnaireContract.DefaultStage).orEmpty()
        val screenSequence = prefs.getString(LatestScreenSequence, stage)
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }
        val path = prefs.getString(LatestResultPath, null).orEmpty()
        val callbackRequestId = prefs.getString(LatestCallbackRequestId, null).orEmpty()
        val file = File(path)
        val callbackState = if (callbackRequestId == requestId) "received" else "pending"

        if (!file.exists()) {
            return "Pending request. No result file yet. Callback: $callbackState"
        }

        val expected = ExpectedQuestionnaireResult(
            requestId = requestId,
            nonce = nonce,
            questionnaireId = questionnaireId,
            stage = stage,
            screenSequence = screenSequence
        )
        val validation = QuestionnaireResultValidator.validate(file.readText(Charsets.UTF_8), expected)
        return if (validation.valid) {
            "Result status=${validation.status} valid=true callback=$callbackState"
        } else {
            "Result invalid reason=${validation.reason} callback=$callbackState"
        }
    }
}
