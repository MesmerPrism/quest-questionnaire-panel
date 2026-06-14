package io.github.mesmerprism.questquestionnaire.nativecaller

import io.github.mesmerprism.questquestionnaire.contract.AnswerValidationResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireAnswerValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireExpectedResult as CoreExpectedQuestionnaireResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelope
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelopeValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultValidation as CoreQuestionnaireResultValidation
import org.json.JSONObject

data class ExpectedQuestionnaireResult(
    val requestId: String,
    val nonce: String,
    val questionnaireId: String,
    val stage: String,
    val screenSequence: List<String>
)

data class QuestionnaireResultValidation(
    val valid: Boolean,
    val status: String = "",
    val reason: String = ""
)

object QuestionnaireResultValidator {
    fun validate(resultJson: String, expected: ExpectedQuestionnaireResult): QuestionnaireResultValidation {
        val coreExpected = CoreExpectedQuestionnaireResult(
            requestId = expected.requestId,
            nonce = expected.nonce,
            questionnaireId = expected.questionnaireId,
            stage = expected.stage,
            screenSequence = expected.screenSequence
        )

        return when (
            val validation = QuestionnaireResultEnvelopeValidator.validate(
                resultJson = resultJson,
                expected = coreExpected,
                answerValidator = BrbQuestionnaireAnswerValidator
            )
        ) {
            is CoreQuestionnaireResultValidation.Valid ->
                QuestionnaireResultValidation(
                    valid = true,
                    status = validation.status.wireValue
                )
            is CoreQuestionnaireResultValidation.Invalid ->
                invalid(validation.reason)
        }
    }

    private fun invalid(reason: String): QuestionnaireResultValidation =
        QuestionnaireResultValidation(valid = false, reason = reason)
}

internal object BrbQuestionnaireAnswerValidator : QuestionnaireAnswerValidator {
    private val PlaceholderAnswers = setOf("yes", "no", "not_answered")
    private val RequiredStructuredAnswerObjects = setOf(
        "demographics",
        "prior_button_experience",
        "post_condition",
        "final"
    )

    override fun validateCompletedAnswers(
        expected: CoreExpectedQuestionnaireResult,
        envelope: QuestionnaireResultEnvelope,
        answers: JSONObject
    ): AnswerValidationResult {
        if (answers.optString("open_stage") != expected.stage) {
            return AnswerValidationResult.Invalid("answer_stage_mismatch")
        }

        val placeholderAnswer = answers.optString("placeholder_answer", "")
        if (placeholderAnswer.isNotBlank()) {
            if (placeholderAnswer !in PlaceholderAnswers) {
                return AnswerValidationResult.Invalid("invalid_placeholder_answer")
            }
            return AnswerValidationResult.Valid
        }

        val missingObject = RequiredStructuredAnswerObjects.firstOrNull {
            answers.optJSONObject(it) == null
        }
        if (missingObject != null) {
            return AnswerValidationResult.Invalid("missing_$missingObject")
        }

        return AnswerValidationResult.Valid
    }
}
