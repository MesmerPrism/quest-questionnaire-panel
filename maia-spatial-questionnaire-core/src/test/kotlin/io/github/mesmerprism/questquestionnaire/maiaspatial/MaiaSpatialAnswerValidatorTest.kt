package io.github.mesmerprism.questquestionnaire.maiaspatial

import io.github.mesmerprism.questquestionnaire.contract.AnswerValidationResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireExpectedResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireIdentity
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelope
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireTerminalStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaiaSpatialAnswerValidatorTest {
    @Test
    fun scoresMaia2WithReverseScoredItems() {
        val raw = (1..37).associateWith { 2 }

        val scores = scoreMaia2(raw)

        assertEquals(3, scores.scoredItemValues.getValue(5))
        assertEquals(2, scores.scoredItemValues.getValue(16))
        assertEquals(8, scores.subscaleMeans.size)
        assertEquals(3.0, scores.subscaleMeans.getValue("not_distracting"), 0.0001)
    }

    @Test
    fun validatesBlockOneAnswers() {
        val validation = MaiaSpatialAnswerValidator.validateCompletedAnswers(
            expected = expected(
                stage = MaiaSpatialQuestionnaireContract.StageLanguageSelection,
                sequence = MaiaSpatialQuestionnaireContract.BlockOneSetupMaia2Sequence
            ),
            envelope = envelope(),
            answers = blockOneAnswers()
        )

        assertEquals(AnswerValidationResult.Valid, validation)
    }

    @Test
    fun rejectsBadMaia2SubscaleMean() {
        val answers = blockOneAnswers()
        answers.getJSONObject("maia2")
            .getJSONObject("subscale_means")
            .put("noticing", 99)

        val validation = MaiaSpatialAnswerValidator.validateCompletedAnswers(
            expected = expected(
                stage = MaiaSpatialQuestionnaireContract.StageLanguageSelection,
                sequence = MaiaSpatialQuestionnaireContract.BlockOneSetupMaia2Sequence
            ),
            envelope = envelope(),
            answers = answers
        )

        assertInvalid("invalid_maia2_subscale_noticing", validation)
    }

    @Test
    fun validatesSecondSpatialFrameAdministrationSeparately() {
        val answers = commonAnswers(
            openStage = MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2,
            completedStage = MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2
        )
            .put(
                "spatial_frame_reference_administration_2",
                spatialFrameAnswer(
                    blockId = MaiaSpatialQuestionnaireContract.BlockThreeId,
                    administrationIndex = 2,
                    choice = "H"
                )
            )

        val validation = MaiaSpatialAnswerValidator.validateCompletedAnswers(
            expected = expected(
                stage = MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2,
                sequence = MaiaSpatialQuestionnaireContract.BlockThreeSpatialFrameReferenceSequence
            ),
            envelope = envelope(),
            answers = answers
        )

        assertEquals(AnswerValidationResult.Valid, validation)
    }

    private fun blockOneAnswers(): JSONObject {
        val rawScores = (1..37).associateWith { (it % 6) }
        val scores = scoreMaia2(rawScores)
        return commonAnswers()
            .put(
                "demographics",
                JSONObject()
                    .put("name", "Participant")
                    .put("age", 31)
                    .put("gender", "prefer_not_to_say")
                    .put("handedness", "right")
                    .put("consent", true)
                    .put("signature", "Participant")
            )
            .put(
                "maia2",
                JSONObject()
                    .put("instrument_id", "maia2")
                    .put("score_version", MaiaSpatialQuestionnaireContract.Maia2ScoreVersion)
                    .put("raw_item_scores", JSONObject(rawScores.mapKeys { it.key.toString() }))
                    .put(
                        "scored_item_values",
                        JSONObject(scores.scoredItemValues.mapKeys { it.key.toString() })
                    )
                    .put("subscale_means", JSONObject(scores.subscaleMeans))
            )
    }

    private fun commonAnswers(
        openStage: String = MaiaSpatialQuestionnaireContract.StageLanguageSelection,
        completedStage: String = MaiaSpatialQuestionnaireContract.StageMaia2
    ): JSONObject =
        JSONObject()
            .put("open_stage", openStage)
            .put("completed_stage", completedStage)
            .put("program_id", MaiaSpatialQuestionnaireContract.ProgramId)
            .put("content_version", MaiaSpatialQuestionnaireContract.ContentVersion)
            .put(
                "language",
                JSONObject()
                    .put("code", "en")
                    .put("label", "English")
            )

    private fun spatialFrameAnswer(
        blockId: String,
        administrationIndex: Int,
        choice: String
    ): JSONObject =
        JSONObject()
            .put("instrument_id", "spatial_frame_reference_pictograph")
            .put("block_id", blockId)
            .put("administration_index", administrationIndex)
            .put("choice", choice)
            .put("asset_sha256", MaiaSpatialQuestionnaireContract.SpatialFramePictographAssetSha256)

    private fun expected(
        stage: String,
        sequence: List<String>
    ): QuestionnaireExpectedResult =
        QuestionnaireExpectedResult(
            requestId = "request-1",
            nonce = "0123456789abcdef",
            questionnaireId = MaiaSpatialQuestionnaireContract.QuestionnaireId,
            stage = stage,
            screenSequence = sequence
        )

    private fun envelope(): QuestionnaireResultEnvelope =
        QuestionnaireResultEnvelope(
            protocolVersion = "quest.questionnaire.v1",
            schema = "quest.questionnaire.v1.result",
            requestId = "request-1",
            nonce = "0123456789abcdef",
            status = QuestionnaireTerminalStatus.Completed,
            questionnaire = QuestionnaireIdentity(
                id = MaiaSpatialQuestionnaireContract.QuestionnaireId,
                version = MaiaSpatialQuestionnaireContract.QuestionnaireVersion
            ),
            stage = MaiaSpatialQuestionnaireContract.StageLanguageSelection,
            conditionNumber = null,
            screenSequence = MaiaSpatialQuestionnaireContract.BlockOneSetupMaia2Sequence,
            answers = JSONObject(),
            startedAt = null,
            submittedAt = null,
            timing = null,
            terminal = null,
            error = null
        )

    private fun assertInvalid(
        expectedReason: String,
        validation: AnswerValidationResult
    ) {
        assertTrue(validation is AnswerValidationResult.Invalid)
        assertEquals(expectedReason, (validation as AnswerValidationResult.Invalid).reason)
    }
}
