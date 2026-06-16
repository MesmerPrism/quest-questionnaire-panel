package io.github.mesmerprism.questquestionnaire.maiaspatial

import io.github.mesmerprism.questquestionnaire.contract.AnswerValidationResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireAnswerValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireExpectedResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelope
import kotlin.math.abs
import org.json.JSONObject

object MaiaSpatialAnswerValidator : QuestionnaireAnswerValidator {
    override fun validateCompletedAnswers(
        expected: QuestionnaireExpectedResult,
        envelope: QuestionnaireResultEnvelope,
        answers: JSONObject
    ): AnswerValidationResult {
        if (answers.optString("open_stage") != expected.stage) {
            return invalid("answer_stage_mismatch")
        }
        if (answers.optString("program_id") != MaiaSpatialQuestionnaireContract.ProgramId) {
            return invalid("program_id_mismatch")
        }
        if (answers.optString("content_version") != MaiaSpatialQuestionnaireContract.ContentVersion) {
            return invalid("content_version_mismatch")
        }
        if (expected.screenSequence.any { it !in MaiaSpatialQuestionnaireContract.SupportedStages }) {
            return invalid("unsupported_stage")
        }

        validateLanguage(answers)?.let { return it }

        if (expected.screenSequence.any { it in BlockOneStages }) {
            validateDemographics(answers)?.let { return it }
            validateMaia2(answers)?.let { return it }
        }
        if (MaiaSpatialQuestionnaireContract.StageSpatialFrameReference1 in expected.screenSequence) {
            validateSpatialFrameAdministration(
                answers = answers,
                bucket = "spatial_frame_reference_administration_1",
                expectedBlockId = MaiaSpatialQuestionnaireContract.BlockTwoId,
                expectedAdministrationIndex = 1
            )?.let { return it }
        }
        if (MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2 in expected.screenSequence) {
            validateSpatialFrameAdministration(
                answers = answers,
                bucket = "spatial_frame_reference_administration_2",
                expectedBlockId = MaiaSpatialQuestionnaireContract.BlockThreeId,
                expectedAdministrationIndex = 2
            )?.let { return it }
        }

        return AnswerValidationResult.Valid
    }

    private fun validateLanguage(answers: JSONObject): AnswerValidationResult? {
        val language = answers.optJSONObject("language")
            ?: return invalid("missing_language")
        val code = language.optString("code", "")
        if (code !in MaiaSpatialQuestionnaireContract.SupportedLanguages) {
            return invalid("invalid_language")
        }
        return null
    }

    private fun validateDemographics(answers: JSONObject): AnswerValidationResult? {
        val demographics = answers.optJSONObject("demographics")
            ?: return invalid("missing_demographics")
        if (demographics.optString("name", "").isBlank()) {
            return invalid("missing_demographics_name")
        }
        if (!demographics.has("age") || demographics.optInt("age", -1) !in 0..120) {
            return invalid("invalid_demographics_age")
        }
        if (demographics.optString("gender", "") !in MaiaSpatialQuestionnaireContract.GenderChoices) {
            return invalid("invalid_demographics_gender")
        }
        if (
            demographics.optString("handedness", "") !in
            MaiaSpatialQuestionnaireContract.HandednessChoices
        ) {
            return invalid("invalid_demographics_handedness")
        }
        if (!demographics.optBoolean("consent", false)) {
            return invalid("missing_demographics_consent")
        }
        if (demographics.optString("signature", "").isBlank()) {
            return invalid("missing_demographics_signature")
        }
        return null
    }

    private fun validateMaia2(answers: JSONObject): AnswerValidationResult? {
        val maia2 = answers.optJSONObject("maia2")
            ?: return invalid("missing_maia2")
        if (maia2.optString("instrument_id") != "maia2") {
            return invalid("invalid_maia2_instrument")
        }
        if (maia2.optString("score_version") != MaiaSpatialQuestionnaireContract.Maia2ScoreVersion) {
            return invalid("invalid_maia2_score_version")
        }

        val rawScores = maia2.optJSONObject("raw_item_scores")
            ?: return invalid("missing_maia2_raw_item_scores")
        val parsedRawScores = (1..37).associateWith { itemId ->
            if (!rawScores.has(itemId.toString())) {
                return invalid("missing_maia2_item_$itemId")
            }
            val score = rawScores.optInt(itemId.toString(), -1)
            if (score !in 0..5) {
                return invalid("invalid_maia2_item_$itemId")
            }
            score
        }

        val expectedScores = scoreMaia2(parsedRawScores)
        validateScoredItems(maia2, expectedScores)?.let { return it }
        validateSubscales(maia2, expectedScores)?.let { return it }
        return null
    }

    private fun validateScoredItems(
        maia2: JSONObject,
        expectedScores: Maia2ScoreResult
    ): AnswerValidationResult? {
        val scoredValues = maia2.optJSONObject("scored_item_values")
            ?: return invalid("missing_maia2_scored_item_values")
        expectedScores.scoredItemValues.forEach { (itemId, expectedValue) ->
            if (scoredValues.optInt(itemId.toString(), -1) != expectedValue) {
                return invalid("invalid_maia2_scored_item_$itemId")
            }
        }
        return null
    }

    private fun validateSubscales(
        maia2: JSONObject,
        expectedScores: Maia2ScoreResult
    ): AnswerValidationResult? {
        val subscaleMeans = maia2.optJSONObject("subscale_means")
            ?: return invalid("missing_maia2_subscale_means")
        expectedScores.subscaleMeans.forEach { (subscaleId, expectedMean) ->
            if (!subscaleMeans.has(subscaleId)) {
                return invalid("missing_maia2_subscale_$subscaleId")
            }
            if (abs(subscaleMeans.optDouble(subscaleId) - expectedMean) > 0.0001) {
                return invalid("invalid_maia2_subscale_$subscaleId")
            }
        }
        return null
    }

    private fun validateSpatialFrameAdministration(
        answers: JSONObject,
        bucket: String,
        expectedBlockId: String,
        expectedAdministrationIndex: Int
    ): AnswerValidationResult? {
        val pictograph = answers.optJSONObject(bucket)
            ?: return invalid("missing_$bucket")
        if (pictograph.optString("instrument_id") != "spatial_frame_reference_pictograph") {
            return invalid("invalid_${bucket}_instrument")
        }
        if (pictograph.optString("block_id") != expectedBlockId) {
            return invalid("invalid_${bucket}_block")
        }
        if (pictograph.optInt("administration_index", -1) != expectedAdministrationIndex) {
            return invalid("invalid_${bucket}_administration")
        }
        if (pictograph.optString("choice", "") !in MaiaSpatialQuestionnaireContract.SpatialFrameChoices) {
            return invalid("invalid_${bucket}_choice")
        }
        if (
            pictograph.optString("asset_sha256") !=
            MaiaSpatialQuestionnaireContract.SpatialFramePictographAssetSha256
        ) {
            return invalid("invalid_${bucket}_asset")
        }
        return null
    }

    private fun invalid(reason: String): AnswerValidationResult.Invalid =
        AnswerValidationResult.Invalid(reason)

    private val BlockOneStages = setOf(
        MaiaSpatialQuestionnaireContract.StageLanguageSelection,
        MaiaSpatialQuestionnaireContract.StageDemographics,
        MaiaSpatialQuestionnaireContract.StageMaia2
    )
}
