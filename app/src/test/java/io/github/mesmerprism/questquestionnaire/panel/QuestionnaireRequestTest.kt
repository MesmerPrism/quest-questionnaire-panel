package io.github.mesmerprism.questquestionnaire.panel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuestionnaireRequestTest {
    @Test
    fun parsesValidRequestJsonWithMatchingExtras() {
        val request = QuestionnaireRequest.parse(
            requestJson = validRequestJson(),
            sessionIdExtra = SessionId,
            requestIdExtra = RequestId,
            nonceExtra = Nonce
        )

        assertEquals(QuestionnaireContract.ProtocolVersion, request.protocolVersion)
        assertEquals(SessionId, request.sessionId)
        assertEquals(RequestId, request.requestId)
        assertEquals(Nonce, request.nonce)
        assertEquals("demographics", request.openStage)
        assertEquals(listOf("demographics"), request.screenSequence)
    }

    @Test
    fun rejectsMissingRequestIdInRequestJson() {
        val json = JSONObject(validRequestJson()).apply {
            remove("request_id")
        }

        val exception = assertThrows(QuestionnaireRequestException::class.java) {
            QuestionnaireRequest.parse(
                requestJson = json.toString(),
                sessionIdExtra = SessionId,
                requestIdExtra = RequestId,
                nonceExtra = Nonce
            )
        }

        assertEquals("missing_request_id", exception.code)
    }

    @Test
    fun rejectsMismatchedNonceExtra() {
        val exception = assertThrows(QuestionnaireRequestException::class.java) {
            QuestionnaireRequest.parse(
                requestJson = validRequestJson(),
                sessionIdExtra = SessionId,
                requestIdExtra = RequestId,
                nonceExtra = "fedcba0987654321"
            )
        }

        assertEquals("mismatched_request_nonce", exception.code)
    }

    @Test
    fun rejectsUnsupportedStage() {
        val json = JSONObject(validRequestJson()).apply {
            put("open_stage", "unknown_stage")
            put("screen_sequence", JSONArray(listOf("unknown_stage")))
        }

        val exception = assertThrows(QuestionnaireRequestException::class.java) {
            QuestionnaireRequest.parse(
                requestJson = json.toString(),
                sessionIdExtra = SessionId,
                requestIdExtra = RequestId,
                nonceExtra = Nonce
            )
        }

        assertEquals("unsupported_stage", exception.code)
    }

    @Test
    fun rejectsOpenStageMissingFromScreenSequence() {
        val json = JSONObject(validRequestJson()).apply {
            put("screen_sequence", JSONArray(listOf("post_condition:pictographic")))
        }

        val exception = assertThrows(QuestionnaireRequestException::class.java) {
            QuestionnaireRequest.parse(
                requestJson = json.toString(),
                sessionIdExtra = SessionId,
                requestIdExtra = RequestId,
                nonceExtra = Nonce
            )
        }

        assertEquals("stage_not_in_sequence", exception.code)
    }

    private fun validRequestJson(): String =
        JSONObject()
            .put("protocol_version", QuestionnaireContract.ProtocolVersion)
            .put("session_id", SessionId)
            .put("request_id", RequestId)
            .put("nonce", Nonce)
            .put("study_id", "brb")
            .put("schema_id", "brb-questionnaire-v1")
            .put("open_stage", "demographics")
            .put("condition_number", JSONObject.NULL)
            .put("screen_sequence", JSONArray(listOf("demographics")))
            .toString()

    private companion object {
        const val SessionId = "session-1"
        const val RequestId = "request-1"
        const val Nonce = "0123456789abcdef"
    }
}
