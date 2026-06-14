package io.github.mesmerprism.questquestionnaire.brb

import io.github.mesmerprism.questquestionnaire.contract.AnswerValidationResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireAnswerValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireExpectedResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelope
import org.json.JSONObject

object BrbAnswerValidator : QuestionnaireAnswerValidator {
    private val PlaceholderAnswers = setOf("yes", "no", "not_answered")

    override fun validateCompletedAnswers(
        expected: QuestionnaireExpectedResult,
        envelope: QuestionnaireResultEnvelope,
        answers: JSONObject
    ): AnswerValidationResult {
        if (answers.optString("open_stage") != expected.stage) {
            return invalid("answer_stage_mismatch")
        }

        if (answers.has("placeholder_answer")) {
            return validateLegacyPlaceholder(answers, expected)
        }

        return validateStructuredAnswers(expected.screenSequence, answers)
    }

    private fun validateLegacyPlaceholder(
        answers: JSONObject,
        expected: QuestionnaireExpectedResult
    ): AnswerValidationResult {
        if (expected.screenSequence.size != 1 || expected.screenSequence.single() != expected.stage) {
            return invalid("legacy_placeholder_not_allowed_for_sequence")
        }

        val placeholderAnswer = answers.optString("placeholder_answer", "")
        return if (placeholderAnswer in PlaceholderAnswers) {
            AnswerValidationResult.Valid
        } else {
            invalid("invalid_placeholder_answer")
        }
    }

    private fun validateStructuredAnswers(
        screenSequence: List<String>,
        answers: JSONObject
    ): AnswerValidationResult {
        if (BrbQuestionnaireContract.StageLanguageSelect in screenSequence) {
            val language = answers.optString("language", "")
            if (language.isBlank()) {
                return invalid("missing_language")
            }
        }

        if (BrbQuestionnaireContract.StageDemographics in screenSequence) {
            val demographics = answers.optJSONObject("demographics")
                ?: return invalid("missing_demographics")
            if (!demographics.has("age") || demographics.optInt("age", -1) !in 0..120) {
                return invalid("invalid_demographics_age")
            }
        }

        if (BrbQuestionnaireContract.StagePriorExperience in screenSequence) {
            val priorExperience = answers.optJSONObject("prior_button_experience")
                ?: return invalid("missing_prior_button_experience")
            if (priorExperience.optString("answer", "") !in setOf("yes", "no")) {
                return invalid("invalid_prior_button_experience")
            }
        }

        if (screenSequence.any { it in BrbPostConditionStages }) {
            val postCondition = answers.optJSONObject("post_condition")
                ?: return invalid("missing_post_condition")
            validatePostCondition(screenSequence, postCondition)?.let { return it }
        }

        if (screenSequence.any { it in BrbFinalStages }) {
            val final = answers.optJSONObject("final")
                ?: return invalid("missing_final")
            validateFinal(screenSequence, final)?.let { return it }
        }

        return AnswerValidationResult.Valid
    }

    private fun validatePostCondition(
        screenSequence: List<String>,
        postCondition: JSONObject
    ): AnswerValidationResult? {
        if (BrbQuestionnaireContract.StagePostConditionPictographic in screenSequence) {
            listOf("presence_slider", "redness_vas").forEach { key ->
                if (!postCondition.has(key) || postCondition.optInt(key, -1) !in 0..100) {
                    return invalid("invalid_post_condition_$key")
                }
            }
            if (
                !postCondition.has("redness_likert") ||
                postCondition.optInt("redness_likert", -1) !in 1..7
            ) {
                return invalid("invalid_post_condition_redness_likert")
            }
        }

        if (BrbQuestionnaireContract.StagePostConditionPresence in screenSequence) {
            val presence = postCondition.optJSONObject("presence_questionnaire")
                ?: return invalid("missing_post_condition_presence_questionnaire")
            PresenceQuestionnaireItemIds.forEach { key ->
                if (!presence.has(key) || presence.optInt(key, -1) !in 0..6) {
                    return invalid("invalid_post_condition_presence_questionnaire")
                }
            }
        }

        if (BrbQuestionnaireContract.StagePostConditionLostOpportunity in screenSequence) {
            if (!postCondition.has("lost_opportunity_acknowledged")) {
                return invalid("missing_post_condition_lost_opportunity_acknowledged")
            }
        }

        return null
    }

    private fun validateFinal(
        screenSequence: List<String>,
        final: JSONObject
    ): AnswerValidationResult? {
        if (BrbQuestionnaireContract.StageFinalEndConfirmation in screenSequence) {
            if (!final.has("end_confirmation_rating") || final.optInt("end_confirmation_rating", -1) !in 1..10) {
                return invalid("invalid_final_end_confirmation_rating")
            }
            if (!final.has("selected_10")) {
                return invalid("missing_final_selected_10")
            }
        }

        if (
            BrbQuestionnaireContract.StageFinalExtraPressesPrompt in screenSequence &&
            !final.optBoolean("unity_owns_final_physical_presses", false)
        ) {
            return invalid("missing_final_unity_owns_final_physical_presses")
        }

        return null
    }

    private fun invalid(reason: String): AnswerValidationResult.Invalid =
        AnswerValidationResult.Invalid(reason)

    private val BrbPostConditionStages = setOf(
        BrbQuestionnaireContract.StagePostConditionPictographic,
        BrbQuestionnaireContract.StagePostConditionPresence,
        BrbQuestionnaireContract.StagePostConditionLostOpportunity
    )

    private val BrbFinalStages = setOf(
        BrbQuestionnaireContract.StageFinalEndConfirmation,
        BrbQuestionnaireContract.StageFinalExtraPressesPrompt,
        BrbQuestionnaireContract.StageCompleteExportSummary
    )

    private val PresenceQuestionnaireItemIds = listOf(
        "spatial_presence_1",
        "spatial_presence_2",
        "involvement_1",
        "experienced_realism_1",
        "interaction_memory_1"
    )
}
