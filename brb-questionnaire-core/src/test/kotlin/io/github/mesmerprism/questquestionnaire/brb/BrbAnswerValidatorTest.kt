package io.github.mesmerprism.questquestionnaire.brb

import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireExpectedResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelopeValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultValidation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrbAnswerValidatorTest {
    @Test
    fun acceptsPublicInitialFixture() {
        val validation = validateFixture(
            name = "result.brb.initial.completed.valid.json",
            stage = BrbQuestionnaireContract.StageLanguageSelect,
            sequence = BrbQuestionnaireContract.InitialStudySequence
        )

        assertValid(validation)
    }

    @Test
    fun acceptsPublicPostConditionFixture() {
        val validation = validateFixture(
            name = "result.brb.completed.valid.json",
            stage = BrbQuestionnaireContract.StagePostConditionPictographic,
            sequence = BrbQuestionnaireContract.ConditionOnePostSequence
        )

        assertValid(validation)
    }

    @Test
    fun acceptsPublicFinalFixture() {
        val validation = validateFixture(
            name = "result.brb.final.completed.valid.json",
            stage = BrbQuestionnaireContract.StageFinalEndConfirmation,
            sequence = BrbQuestionnaireContract.FinalSequence
        )

        assertValid(validation)
    }

    @Test
    fun initialSequenceRequiresInitialAnswerBucketsOnly() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.InitialStudySequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StageLanguageSelect)
                .put("language", "en-US")
                .put("demographics", JSONObject().put("age", 42))
                .put("prior_button_experience", JSONObject().put("answer", "yes"))
        )

        assertValid(validation)
    }

    @Test
    fun initialSequenceRejectsMissingPriorExperienceBucket() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.InitialStudySequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StageLanguageSelect)
                .put("language", "en-US")
                .put("demographics", JSONObject().put("age", 42))
        )

        assertInvalid("missing_prior_button_experience", validation)
    }

    @Test
    fun postConditionSequenceRequiresPostConditionBucketOnly() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.ConditionOnePostSequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StagePostConditionPictographic)
                .put("post_condition", validPostConditionAnswers(includeLostOpportunity = true))
        )

        assertValid(validation)
    }

    @Test
    fun postConditionSequenceRejectsMissingPostConditionBucket() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.ConditionOnePostSequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StagePostConditionPictographic)
        )

        assertInvalid("missing_post_condition", validation)
    }

    @Test
    fun finalSequenceRequiresFinalBucketOnly() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.FinalSequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StageFinalEndConfirmation)
                .put("final", validFinalAnswers())
        )

        assertValid(validation)
    }

    @Test
    fun finalSequenceRejectsMissingFinalBucket() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.FinalSequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StageFinalEndConfirmation)
        )

        assertInvalid("missing_final", validation)
    }

    @Test
    fun fullBrbSequenceRequiresEveryRelevantBucket() {
        val fullSequence = BrbQuestionnaireContract.InitialStudySequence +
            BrbQuestionnaireContract.ConditionOnePostSequence +
            BrbQuestionnaireContract.FinalSequence

        val validation = validate(
            sequence = fullSequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StageLanguageSelect)
                .put("language", "en-US")
                .put("demographics", JSONObject().put("age", 42))
                .put("prior_button_experience", JSONObject().put("answer", "yes"))
                .put("post_condition", validPostConditionAnswers(includeLostOpportunity = true))
                .put("final", validFinalAnswers())
        )

        assertValid(validation)
    }

    @Test
    fun fullBrbSequenceRejectsLegacyPlaceholder() {
        val fullSequence = BrbQuestionnaireContract.InitialStudySequence +
            BrbQuestionnaireContract.ConditionOnePostSequence +
            BrbQuestionnaireContract.FinalSequence

        val validation = validate(
            sequence = fullSequence,
            answers = JSONObject()
                .put("open_stage", BrbQuestionnaireContract.StageLanguageSelect)
                .put("placeholder_answer", "yes")
        )

        assertInvalid("legacy_placeholder_not_allowed_for_sequence", validation)
    }

    @Test
    fun cancelledResultDoesNotRequireCompletedAnswerBuckets() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.InitialStudySequence,
            status = "cancelled",
            answers = JSONObject()
        )

        assertValid(validation)
    }

    @Test
    fun errorResultDoesNotRequireCompletedAnswerBuckets() {
        val validation = validate(
            sequence = BrbQuestionnaireContract.FinalSequence,
            status = "error",
            answers = JSONObject()
        )

        assertValid(validation)
    }

    private fun validate(
        sequence: List<String>,
        answers: JSONObject,
        status: String = "completed"
    ): QuestionnaireResultValidation {
        val stage = sequence.first()
        return QuestionnaireResultEnvelopeValidator.validate(
            resultJson = resultJson(stage = stage, sequence = sequence, status = status, answers = answers),
            expected = QuestionnaireExpectedResult(
                requestId = RequestId,
                nonce = Nonce,
                questionnaireId = BrbQuestionnaireContract.QuestionnaireId,
                stage = stage,
                screenSequence = sequence
            ),
            answerValidator = BrbAnswerValidator
        )
    }

    private fun validateFixture(
        name: String,
        stage: String,
        sequence: List<String>
    ): QuestionnaireResultValidation =
        QuestionnaireResultEnvelopeValidator.validate(
            resultJson = fixture(name),
            expected = QuestionnaireExpectedResult(
                requestId = FixtureRequestId,
                nonce = FixtureNonce,
                questionnaireId = BrbQuestionnaireContract.QuestionnaireId,
                stage = stage,
                screenSequence = sequence
            ),
            answerValidator = BrbAnswerValidator
        )

    private fun resultJson(
        stage: String,
        sequence: List<String>,
        status: String,
        answers: JSONObject
    ): String =
        JSONObject()
            .put("protocol_version", "quest.questionnaire.v1")
            .put("schema", "quest.questionnaire.v1.result")
            .put("request_id", RequestId)
            .put("nonce", Nonce)
            .put("status", status)
            .put(
                "questionnaire",
                JSONObject()
                    .put("id", BrbQuestionnaireContract.QuestionnaireId)
                    .put("version", BrbQuestionnaireContract.QuestionnaireVersion)
            )
            .put("stage", stage)
            .put("condition_number", JSONObject.NULL)
            .put("screen_sequence", JSONArray(sequence))
            .put("answers", answers)
            .toString()

    private fun validPostConditionAnswers(includeLostOpportunity: Boolean): JSONObject =
        JSONObject()
            .put("presence_slider", 50)
            .put("redness_vas", 50)
            .put("redness_likert", 4)
            .put(
                "presence_questionnaire",
                JSONObject()
                    .put("spatial_presence_1", 3)
                    .put("spatial_presence_2", 3)
                    .put("involvement_1", 3)
                    .put("experienced_realism_1", 3)
                    .put("interaction_memory_1", 3)
            ).apply {
                if (includeLostOpportunity) {
                    put("lost_opportunity_acknowledged", true)
                }
            }

    private fun validFinalAnswers(): JSONObject =
        JSONObject()
            .put("end_confirmation_rating", 10)
            .put("selected_10", true)
            .put("unity_owns_final_physical_presses", true)

    private fun assertValid(validation: QuestionnaireResultValidation) {
        val reason = (validation as? QuestionnaireResultValidation.Invalid)?.reason
        assertTrue(
            "Expected valid result, got invalid reason=$reason",
            validation is QuestionnaireResultValidation.Valid
        )
    }

    private fun assertInvalid(
        expectedReason: String,
        validation: QuestionnaireResultValidation
    ) {
        assertTrue(validation is QuestionnaireResultValidation.Invalid)
        assertEquals(
            expectedReason,
            (validation as QuestionnaireResultValidation.Invalid).reason
        )
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) {
            "Missing fixture resource: $name"
        }.readText()

    private companion object {
        const val RequestId = "request-1"
        const val Nonce = "0123456789abcdef"
        const val FixtureRequestId = "f8d62b0a-77e8-4f7d-a7da-7f95fd9a7024"
        const val FixtureNonce = "b96d9b51c4874db8a4e8f1b4"
    }
}
