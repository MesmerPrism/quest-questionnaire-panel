package io.github.mesmerprism.questquestionnaire.contract

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class QuestionnaireLaunchRequest(
    val protocolVersion: String,
    val sessionId: String,
    val requestId: String,
    val nonce: String,
    val studyId: String,
    val schemaId: String,
    val openStage: String,
    val conditionNumber: Int?,
    val screenSequence: List<String>,
    val participantRef: String?,
    val caller: QuestionnaireCallerInfo?
) {
    companion object {
        fun parse(requestJson: String): QuestionnaireLaunchRequest {
            val json = try {
                JSONObject(requestJson)
            } catch (_: JSONException) {
                throw QuestionnaireContractException("malformed_request_json")
            }

            val protocolVersion = json.requiredString("protocol_version")
            if (protocolVersion != QuestQuestionnaireProtocol.Version) {
                throw QuestionnaireContractException("unsupported_protocol")
            }

            val nonce = json.requiredString("nonce")
            if (nonce.length < 16) {
                throw QuestionnaireContractException("invalid_nonce")
            }

            val screenSequence = json.requiredStringArray("screen_sequence")
            if (screenSequence.isEmpty()) {
                throw QuestionnaireContractException("missing_screen_sequence")
            }

            val openStage = json.requiredString("open_stage")
            if (openStage !in screenSequence) {
                throw QuestionnaireContractException("stage_not_in_sequence")
            }

            return QuestionnaireLaunchRequest(
                protocolVersion = protocolVersion,
                sessionId = json.requiredString("session_id"),
                requestId = json.requiredString("request_id"),
                nonce = nonce,
                studyId = json.requiredString("study_id"),
                schemaId = json.requiredString("schema_id"),
                openStage = openStage,
                conditionNumber = json.optionalNonNegativeInt("condition_number"),
                screenSequence = screenSequence,
                participantRef = json.optionalString("participant_ref"),
                caller = json.optJSONObject("caller")?.let(QuestionnaireCallerInfo::fromJson)
            )
        }
    }
}

data class QuestionnaireCallerInfo(
    val packageName: String?,
    val appVersion: String?,
    val engine: String?
) {
    companion object {
        fun fromJson(json: JSONObject): QuestionnaireCallerInfo =
            QuestionnaireCallerInfo(
                packageName = json.optionalString("package"),
                appVersion = json.optionalString("app_version"),
                engine = json.optionalString("engine")
            )
    }
}

class QuestionnaireContractException(val code: String) :
    IllegalArgumentException("Invalid questionnaire contract payload: $code")

internal fun JSONObject.requiredString(name: String): String {
    if (!has(name) || isNull(name)) {
        throw QuestionnaireContractException("missing_$name")
    }

    val value = get(name)
    if (value !is String || value.isBlank()) {
        throw QuestionnaireContractException("invalid_$name")
    }

    return value
}

internal fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }

    val value = get(name)
    if (value !is String || value.isBlank()) {
        throw QuestionnaireContractException("invalid_$name")
    }

    return value
}

internal fun JSONObject.requiredStringArray(name: String): List<String> {
    if (!has(name) || isNull(name)) {
        throw QuestionnaireContractException("missing_$name")
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

internal fun JSONObject.optionalNonNegativeInt(name: String): Int? {
    if (!has(name) || isNull(name)) {
        return null
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

