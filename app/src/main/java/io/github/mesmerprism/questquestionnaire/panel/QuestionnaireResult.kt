package io.github.mesmerprism.questquestionnaire.panel

import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalContext
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalStatus
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

data class QuestionnaireResult(
    val request: QuestionnaireRequest,
    val status: QuestionnaireTerminalStatus,
    val answers: JSONObject,
    val startedAt: Instant,
    val submittedAt: Instant,
    val terminal: QuestionnaireTerminalContext? = null,
    val timing: QuestionnaireResultTiming? = null,
    val error: QuestionnaireResultError? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("protocol_version", request.protocolVersion)
            .put("schema", QuestionnaireContract.ResultSchema)
            .put("request_id", request.requestId)
            .put("nonce", request.nonce)
            .put("status", status.wireValue)
            .put("questionnaire", JSONObject().put("id", request.schemaId).put("version", 1))
            .put("stage", request.openStage)
            .put("condition_number", request.conditionNumber ?: JSONObject.NULL)
            .put("screen_sequence", JSONArray(request.screenSequence))
            .put("answers", answers)
            .put("started_at", startedAt.toString())
            .put("submitted_at", submittedAt.toString())
            .put("terminal", terminal?.toJson() ?: JSONObject.NULL)
            .put("error", error?.toJson() ?: JSONObject.NULL)
        timing?.let { json.put("timing", it.toJson()) }
        return json
    }

    companion object {
        fun from(
            request: QuestionnaireRequest,
            status: QuestionnaireTerminalStatus,
            answers: JSONObject,
            startedAt: Instant,
            submittedAt: Instant = Instant.now(),
            terminal: QuestionnaireTerminalContext? = null,
            timing: QuestionnaireResultTiming? = null,
            error: QuestionnaireResultError? = null
        ) = QuestionnaireResult(
            request = request,
            status = status,
            answers = answers,
            startedAt = startedAt,
            submittedAt = submittedAt,
            terminal = terminal,
            timing = timing,
            error = error
        )
    }
}

data class QuestionnaireResultTiming(
    val startedAt: Instant,
    val submittedAt: Instant,
    val durationMs: Long,
    val screens: List<QuestionnaireScreenTiming>
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("started_at", startedAt.toString())
            .put("submitted_at", submittedAt.toString())
            .put("duration_ms", durationMs.coerceAtLeast(0L))
            .put("screens", JSONArray(screens.map { it.toJson() }))
}

data class QuestionnaireScreenTiming(
    val screenId: String,
    val ordinal: Int,
    val enteredAt: Instant,
    val enteredElapsedMs: Long,
    val firstInteractionAt: Instant?,
    val firstInteractionElapsedMs: Long?,
    val leftAt: Instant,
    val leftElapsedMs: Long,
    val durationMs: Long,
    val interactionCount: Int,
    val validationFailures: Int
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("screen_id", screenId)
            .put("ordinal", ordinal.coerceAtLeast(0))
            .put("entered_at", enteredAt.toString())
            .put("entered_elapsed_ms", enteredElapsedMs.coerceAtLeast(0L))
            .put("first_interaction_at", firstInteractionAt?.toString() ?: JSONObject.NULL)
            .put(
                "first_interaction_elapsed_ms",
                firstInteractionElapsedMs?.coerceAtLeast(0L) ?: JSONObject.NULL
            )
            .put("left_at", leftAt.toString())
            .put("left_elapsed_ms", leftElapsedMs.coerceAtLeast(0L))
            .put("duration_ms", durationMs.coerceAtLeast(0L))
            .put("interaction_count", interactionCount.coerceAtLeast(0))
            .put("validation_failures", validationFailures.coerceAtLeast(0))
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
