package io.github.mesmerprism.questquestionnaire.panel

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class QuestionnaireRequest(
    val protocolVersion: String,
    val sessionId: String,
    val requestId: String,
    val nonce: String,
    val studyId: String,
    val schemaId: String,
    val openStage: String,
    val conditionNumber: Int?,
    val screenSequence: List<String>
) {
    companion object {
        fun parse(
            requestJson: String?,
            sessionIdExtra: String?,
            requestIdExtra: String?,
            nonceExtra: String?
        ): QuestionnaireRequest {
            if (requestJson.isNullOrBlank()) {
                throw QuestionnaireRequestException("missing_request_json")
            }

            val json = try {
                JSONObject(requestJson)
            } catch (_: JSONException) {
                throw QuestionnaireRequestException("malformed_request_json")
            }

            val protocolVersion = json.requiredString("protocol_version")
            if (protocolVersion != QuestionnaireContract.ProtocolVersion) {
                throw QuestionnaireRequestException("unsupported_protocol")
            }

            val sessionId = json.requiredString("session_id")
            val requestId = json.requiredString("request_id")
            val nonce = json.requiredString("nonce")
            if (nonce.length < 16) {
                throw QuestionnaireRequestException("invalid_nonce")
            }

            requireMatchingExtra("session_id", sessionIdExtra, sessionId)
            requireMatchingExtra("request_id", requestIdExtra, requestId)
            requireMatchingExtra("request_nonce", nonceExtra, nonce)

            val openStage = json.requiredString("open_stage")

            val screenSequence = json.requiredStringArray("screen_sequence")
            if (screenSequence.isEmpty()) {
                throw QuestionnaireRequestException("missing_screen_sequence")
            }
            if (openStage !in screenSequence) {
                throw QuestionnaireRequestException("stage_not_in_sequence")
            }

            return QuestionnaireRequest(
                protocolVersion = protocolVersion,
                sessionId = sessionId,
                requestId = requestId,
                nonce = nonce,
                studyId = json.requiredString("study_id"),
                schemaId = json.requiredString("schema_id"),
                openStage = openStage,
                conditionNumber = json.optionalNonNegativeInt("condition_number"),
                screenSequence = screenSequence
            )
        }

        private fun requireMatchingExtra(name: String, actual: String?, expected: String) {
            if (actual.isNullOrBlank()) {
                throw QuestionnaireRequestException("missing_$name")
            }
            if (actual != expected) {
                throw QuestionnaireRequestException("mismatched_$name")
            }
        }
    }
}

class QuestionnaireRequestException(val code: String) :
    IllegalArgumentException("Invalid questionnaire request: $code")

private fun JSONObject.requiredString(name: String): String {
    if (!has(name) || isNull(name)) {
        throw QuestionnaireRequestException("missing_$name")
    }
    val value = get(name)
    if (value !is String || value.isBlank()) {
        throw QuestionnaireRequestException("invalid_$name")
    }
    return value
}

private fun JSONObject.requiredStringArray(name: String): List<String> {
    if (!has(name) || isNull(name)) {
        throw QuestionnaireRequestException("missing_$name")
    }
    val value = get(name)
    if (value !is JSONArray) {
        throw QuestionnaireRequestException("invalid_$name")
    }

    val items = mutableListOf<String>()
    for (index in 0 until value.length()) {
        val item = value.get(index)
        if (item !is String || item.isBlank()) {
            throw QuestionnaireRequestException("invalid_$name")
        }
        items += item
    }
    return items
}

private fun JSONObject.optionalNonNegativeInt(name: String): Int? {
    if (!has(name) || isNull(name)) {
        return null
    }
    val value = get(name)
    if (value !is Number) {
        throw QuestionnaireRequestException("invalid_$name")
    }
    val intValue = value.toInt()
    if (intValue < 0) {
        throw QuestionnaireRequestException("invalid_$name")
    }
    return intValue
}
