package io.github.mesmerprism.questquestionnaire.panel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireLaunchValidatorTest {
    @Test
    fun acceptsCompleteLaunchSpec() {
        val validation = QuestionnaireLaunchValidator.validate(validSpec())

        assertTrue(validation is QuestionnaireLaunchValidation.Valid)
        assertEquals(
            RequestId,
            (validation as QuestionnaireLaunchValidation.Valid).request.requestId
        )
    }

    @Test
    fun rejectsMissingWriteGrant() {
        val validation = QuestionnaireLaunchValidator.validate(
            validSpec().copy(hasWriteGrant = false)
        )

        assertInvalid("missing_write_grant", validation)
    }

    @Test
    fun rejectsFileResultUri() {
        val validation = QuestionnaireLaunchValidator.validate(
            validSpec().copy(
                dataUri = "file:///tmp/result.json",
                resultUri = "file:///tmp/result.json"
            )
        )

        assertInvalid("invalid_result_uri", validation)
    }

    @Test
    fun rejectsMismatchedDataAndResultUri() {
        val validation = QuestionnaireLaunchValidator.validate(
            validSpec().copy(dataUri = "content://caller.example/other/result.json")
        )

        assertInvalid("result_uri_mismatch", validation)
    }

    @Test
    fun rejectsMissingCallback() {
        val validation = QuestionnaireLaunchValidator.validate(
            validSpec().copy(hasReturnToCaller = false)
        )

        assertInvalid("missing_return_to_caller", validation)
    }

    private fun assertInvalid(
        expectedCode: String,
        validation: QuestionnaireLaunchValidation
    ) {
        assertTrue(validation is QuestionnaireLaunchValidation.Invalid)
        assertEquals(
            expectedCode,
            (validation as QuestionnaireLaunchValidation.Invalid).code
        )
    }

    private fun validSpec(): QuestionnaireLaunchSpec =
        QuestionnaireLaunchSpec(
            action = QuestionnaireContract.ActionStart,
            mimeType = QuestionnaireContract.RequestMimeType,
            hasWriteGrant = true,
            dataUri = ResultUri,
            resultUri = ResultUri,
            hasReturnToCaller = true,
            requestJson = validRequestJson(),
            sessionIdExtra = SessionId,
            requestIdExtra = RequestId,
            nonceExtra = Nonce
        )

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
        const val ResultUri = "content://caller.example/questionnaire-results/result.json"
    }
}
