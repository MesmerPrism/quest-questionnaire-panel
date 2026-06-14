package io.github.mesmerprism.questquestionnaire.contract

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class QuestionnaireExpectedResult(
    val requestId: String,
    val nonce: String,
    val questionnaireId: String,
    val stage: String,
    val screenSequence: List<String>,
    val minimumQuestionnaireVersion: Int = 1
)

data class QuestionnaireResultEnvelope(
    val protocolVersion: String,
    val schema: String,
    val requestId: String,
    val nonce: String,
    val status: QuestionnaireTerminalStatus,
    val questionnaire: QuestionnaireIdentity,
    val stage: String,
    val conditionNumber: Int?,
    val screenSequence: List<String>,
    val answers: JSONObject,
    val startedAt: String?,
    val submittedAt: String?,
    val terminal: QuestionnaireTerminalContext?,
    val error: QuestionnaireResultError?
) {
    companion object {
        fun parse(resultJson: String): QuestionnaireResultEnvelope {
            val json = try {
                JSONObject(resultJson)
            } catch (_: JSONException) {
                throw QuestionnaireContractException("malformed_result_json")
            }

            val status = QuestionnaireTerminalStatus.fromWireValue(
                json.requiredString("status")
            ) ?: throw QuestionnaireContractException("unknown_status")

            return QuestionnaireResultEnvelope(
                protocolVersion = json.requiredString("protocol_version"),
                schema = json.requiredString("schema"),
                requestId = json.requiredString("request_id"),
                nonce = json.requiredString("nonce"),
                status = status,
                questionnaire = QuestionnaireIdentity.fromJson(
                    json.optJSONObject("questionnaire")
                        ?: throw QuestionnaireContractException("missing_questionnaire")
                ),
                stage = json.requiredString("stage"),
                conditionNumber = json.optionalNonNegativeInt("condition_number"),
                screenSequence = json.optionalStringArray("screen_sequence"),
                answers = json.optJSONObject("answers")
                    ?: throw QuestionnaireContractException("missing_answers"),
                startedAt = json.optionalString("started_at"),
                submittedAt = json.optionalString("submitted_at"),
                terminal = json.optJSONObject("terminal")?.let(
                    QuestionnaireTerminalContext::fromJson
                ),
                error = json.optJSONObject("error")?.let(QuestionnaireResultError::fromJson)
            )
        }
    }
}

data class QuestionnaireIdentity(
    val id: String,
    val version: Int
) {
    companion object {
        fun fromJson(json: JSONObject): QuestionnaireIdentity =
            QuestionnaireIdentity(
                id = json.requiredString("id"),
                version = json.requiredPositiveInt("version")
            )
    }
}

data class QuestionnaireResultError(
    val code: String?,
    val message: String?
) {
    companion object {
        fun fromJson(json: JSONObject): QuestionnaireResultError =
            QuestionnaireResultError(
                code = json.optionalString("code"),
                message = json.optionalString("message")
            )
    }
}

data class QuestionnaireTerminalContext(
    val reason: String,
    val currentStage: String,
    val screenIndex: Int
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("reason", reason)
            .put("current_stage", currentStage)
            .put("screen_index", screenIndex)

    companion object {
        fun fromJson(json: JSONObject): QuestionnaireTerminalContext =
            QuestionnaireTerminalContext(
                reason = json.requiredString("reason"),
                currentStage = json.requiredString("current_stage"),
                screenIndex = json.requiredNonNegativeInt("screen_index")
            )
    }
}

internal fun JSONObject.optionalStringArray(name: String): List<String> {
    if (!has(name) || isNull(name)) {
        return emptyList()
    }

    val value = get(name)
    if (value !is JSONArray) {
        throw QuestionnaireContractException("invalid_$name")
    }

    return (0 until value.length()).map { index ->
        val item = value.get(index)
        if (item !is String || item.isBlank()) {
            throw QuestionnaireContractException("invalid_$name")
        }
        item
    }
}

internal fun JSONObject.requiredNonNegativeInt(name: String): Int {
    if (!has(name) || isNull(name)) {
        throw QuestionnaireContractException("missing_$name")
    }

    val value = get(name)
    if (value !is Number) {
        throw QuestionnaireContractException("invalid_$name")
    }

    val intValue = value.toInt()
    if (intValue < 0) {
        throw QuestionnaireContractException("invalid_$name")
    }

    return intValue
}

internal fun JSONObject.requiredPositiveInt(name: String): Int {
    if (!has(name) || isNull(name)) {
        throw QuestionnaireContractException("missing_$name")
    }

    val value = get(name)
    if (value !is Number) {
        throw QuestionnaireContractException("invalid_$name")
    }

    val intValue = value.toInt()
    if (intValue < 1) {
        throw QuestionnaireContractException("invalid_$name")
    }

    return intValue
}
