package io.github.mesmerprism.questquestionnaire.nativecaller

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ExpectedQuestionnaireResult(
    val requestId: String,
    val nonce: String,
    val questionnaireId: String,
    val stage: String,
    val screenSequence: List<String>
)

data class QuestionnaireResultValidation(
    val valid: Boolean,
    val status: String = "",
    val reason: String = ""
)

object QuestionnaireResultValidator {
    private val KnownStatuses = setOf("completed", "cancelled", "error")
    private val PlaceholderAnswers = setOf("yes", "no", "not_answered")
    private val RequiredStructuredAnswerObjects = setOf(
        "demographics",
        "prior_button_experience",
        "post_condition",
        "final"
    )

    fun validate(resultJson: String, expected: ExpectedQuestionnaireResult): QuestionnaireResultValidation {
        val json = try {
            JSONObject(resultJson)
        } catch (_: JSONException) {
            return invalid("malformed_result_json")
        }

        val status = json.optString("status")
        return when {
            json.optString("protocol_version") != QuestionnaireContract.ProtocolVersion ->
                invalid("unsupported_protocol")
            json.optString("schema") != QuestionnaireContract.ResultSchema ->
                invalid("unsupported_schema")
            json.optString("request_id") != expected.requestId ->
                invalid("request_id_mismatch")
            json.optString("nonce") != expected.nonce ->
                invalid("nonce_mismatch")
            status !in KnownStatuses ->
                invalid("unknown_status")
            json.optString("stage") != expected.stage ->
                invalid("stage_mismatch")
            json.optJSONArray("screen_sequence").toStringList() != expected.screenSequence ->
                invalid("screen_sequence_mismatch")
            !json.questionnaireMatches(expected.questionnaireId) ->
                invalid("questionnaire_mismatch")
            else -> validateAnswers(status, expected.stage, json.optJSONObject("answers"))
        }
    }

    private fun validateAnswers(
        status: String,
        expectedStage: String,
        answers: JSONObject?
    ): QuestionnaireResultValidation {
        if (answers == null) {
            return invalid("missing_answers")
        }

        if (status != "completed") {
            return QuestionnaireResultValidation(valid = true, status = status)
        }

        if (answers.optString("open_stage") != expectedStage) {
            return invalid("answer_stage_mismatch")
        }

        val placeholderAnswer = answers.optString("placeholder_answer", "")
        if (placeholderAnswer.isNotBlank()) {
            if (placeholderAnswer !in PlaceholderAnswers) {
                return invalid("invalid_placeholder_answer")
            }
            return QuestionnaireResultValidation(valid = true, status = status)
        }

        val missingObject = RequiredStructuredAnswerObjects.firstOrNull {
            answers.optJSONObject(it) == null
        }
        if (missingObject != null) {
            return invalid("missing_$missingObject")
        }

        return QuestionnaireResultValidation(valid = true, status = status)
    }

    private fun invalid(reason: String): QuestionnaireResultValidation =
        QuestionnaireResultValidation(valid = false, reason = reason)
}

private fun JSONObject.questionnaireMatches(expectedQuestionnaireId: String): Boolean {
    val questionnaire = optJSONObject("questionnaire") ?: return false
    return questionnaire.optString("id") == expectedQuestionnaireId &&
        questionnaire.optInt("version") >= 1
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { index -> optString(index) }
}
