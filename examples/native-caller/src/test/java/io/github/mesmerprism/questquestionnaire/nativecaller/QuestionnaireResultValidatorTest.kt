package io.github.mesmerprism.questquestionnaire.nativecaller

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireResultValidatorTest {
    @Test
    fun acceptsCompletedPlaceholderResult() {
        val validation = QuestionnaireResultValidator.validate(
            resultJson = validResultJson(),
            expected = Expected
        )

        assertTrue(validation.valid)
        assertEquals("completed", validation.status)
    }

    @Test
    fun rejectsMismatchedNonce() {
        val json = JSONObject(validResultJson()).apply {
            put("nonce", "fedcba0987654321")
        }

        val validation = QuestionnaireResultValidator.validate(
            resultJson = json.toString(),
            expected = Expected
        )

        assertFalse(validation.valid)
        assertEquals("nonce_mismatch", validation.reason)
    }

    @Test
    fun rejectsCompletedResultWithoutPlaceholderAnswer() {
        val json = JSONObject(validResultJson()).apply {
            put("answers", JSONObject().put("open_stage", QuestionnaireContract.DefaultStage))
        }

        val validation = QuestionnaireResultValidator.validate(
            resultJson = json.toString(),
            expected = Expected
        )

        assertFalse(validation.valid)
        assertEquals("missing_demographics", validation.reason)
    }

    @Test
    fun acceptsStructuredStudyResult() {
        val json = JSONObject(validResultJson()).apply {
            put(
                "answers",
                JSONObject()
                    .put("open_stage", QuestionnaireContract.DefaultStage)
                    .put("demographics", JSONObject().put("participant_code", "P001").put("age", 42))
                    .put("prior_button_experience", JSONObject().put("answer", "yes"))
                    .put("post_condition", JSONObject())
                    .put("final", JSONObject().put("end_confirmation_rating", 10).put("selected_10", true))
            )
        }

        val validation = QuestionnaireResultValidator.validate(
            resultJson = json.toString(),
            expected = Expected
        )

        assertTrue(validation.valid)
        assertEquals("completed", validation.status)
    }

    @Test
    fun acceptsCancelledResultWithoutAnswers() {
        val json = JSONObject(validResultJson()).apply {
            put("status", "cancelled")
            put("answers", JSONObject())
        }

        val validation = QuestionnaireResultValidator.validate(
            resultJson = json.toString(),
            expected = Expected
        )

        assertTrue(validation.valid)
        assertEquals("cancelled", validation.status)
    }

    private fun validResultJson(): String =
        JSONObject()
            .put("protocol_version", QuestionnaireContract.ProtocolVersion)
            .put("schema", QuestionnaireContract.ResultSchema)
            .put("request_id", RequestId)
            .put("nonce", Nonce)
            .put("status", "completed")
            .put(
                "questionnaire",
                JSONObject()
                    .put("id", QuestionnaireContract.QuestionnaireId)
                    .put("version", 1)
            )
            .put("stage", QuestionnaireContract.DefaultStage)
            .put("condition_number", JSONObject.NULL)
            .put("screen_sequence", JSONArray(listOf(QuestionnaireContract.DefaultStage)))
            .put(
                "answers",
                JSONObject()
                    .put("placeholder_answer", "yes")
                    .put("open_stage", QuestionnaireContract.DefaultStage)
            )
            .toString()

    private companion object {
        const val RequestId = "request-1"
        const val Nonce = "0123456789abcdef"

        val Expected = ExpectedQuestionnaireResult(
            requestId = RequestId,
            nonce = Nonce,
            questionnaireId = QuestionnaireContract.QuestionnaireId,
            stage = QuestionnaireContract.DefaultStage,
            screenSequence = listOf(QuestionnaireContract.DefaultStage)
        )
    }
}
