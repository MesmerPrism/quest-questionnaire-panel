package io.github.mesmerprism.questquestionnaire.panel

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

data class QuestionnaireResult(
    val request: QuestionnaireRequest,
    val status: String,
    val answers: JSONObject,
    val startedAt: Instant,
    val submittedAt: Instant,
    val error: QuestionnaireResultError? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("protocol_version", request.protocolVersion)
            .put("schema", QuestionnaireContract.ResultSchema)
            .put("request_id", request.requestId)
            .put("nonce", request.nonce)
            .put("status", status)
            .put("questionnaire", JSONObject().put("id", request.schemaId).put("version", 1))
            .put("stage", request.openStage)
            .put("condition_number", request.conditionNumber ?: JSONObject.NULL)
            .put("screen_sequence", JSONArray(request.screenSequence))
            .put("answers", answers)
            .put("started_at", startedAt.toString())
            .put("submitted_at", submittedAt.toString())
            .put("error", error?.toJson() ?: JSONObject.NULL)

    companion object {
        fun from(
            request: QuestionnaireRequest,
            status: String,
            answers: JSONObject,
            startedAt: Instant,
            submittedAt: Instant = Instant.now()
        ) = QuestionnaireResult(
            request = request,
            status = status,
            answers = answers,
            startedAt = startedAt,
            submittedAt = submittedAt
        )
    }
}

data class QuestionnaireResultError(
    val code: String,
    val message: String
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("code", code)
            .put("message", message)
}
