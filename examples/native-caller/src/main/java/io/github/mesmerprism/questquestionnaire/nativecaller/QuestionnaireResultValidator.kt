package io.github.mesmerprism.questquestionnaire.nativecaller

import io.github.mesmerprism.questquestionnaire.brb.BrbAnswerValidator
import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.contract.AnswerValidationResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireAnswerValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireExpectedResult as CoreExpectedQuestionnaireResult
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelope
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultEnvelopeValidator
import io.github.mesmerprism.questquestionnaire.contract.QuestionnaireResultValidation as CoreQuestionnaireResultValidation
import io.github.mesmerprism.questquestionnaire.maiaspatial.MaiaSpatialAnswerValidator
import io.github.mesmerprism.questquestionnaire.maiaspatial.MaiaSpatialQuestionnaireContract
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
                answerValidator = NativeCallerAnswerValidator
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

object NativeCallerAnswerValidator : QuestionnaireAnswerValidator {
    override fun validateCompletedAnswers(
        expected: CoreExpectedQuestionnaireResult,
        envelope: QuestionnaireResultEnvelope,
        answers: JSONObject
    ): AnswerValidationResult =
        when (envelope.questionnaire.id) {
            BrbQuestionnaireContract.QuestionnaireId ->
                BrbAnswerValidator.validateCompletedAnswers(expected, envelope, answers)
            MaiaSpatialQuestionnaireContract.QuestionnaireId ->
                MaiaSpatialAnswerValidator.validateCompletedAnswers(expected, envelope, answers)
            else -> AnswerValidationResult.Invalid("unsupported_questionnaire")
        }
}
