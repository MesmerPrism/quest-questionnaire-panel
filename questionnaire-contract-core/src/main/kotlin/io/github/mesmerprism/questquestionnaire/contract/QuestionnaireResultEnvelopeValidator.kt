package io.github.mesmerprism.questquestionnaire.contract

import org.json.JSONObject

sealed class QuestionnaireResultValidation {
    data class Valid(
        val status: QuestionnaireTerminalStatus,
        val envelope: QuestionnaireResultEnvelope
    ) : QuestionnaireResultValidation()

    data class Invalid(val reason: String) : QuestionnaireResultValidation()
}

sealed class AnswerValidationResult {
    object Valid : AnswerValidationResult()
    data class Invalid(val reason: String) : AnswerValidationResult()
}

interface QuestionnaireAnswerValidator {
    fun validateCompletedAnswers(
        expected: QuestionnaireExpectedResult,
        envelope: QuestionnaireResultEnvelope,
        answers: JSONObject
    ): AnswerValidationResult = AnswerValidationResult.Valid

    fun validateCancelledAnswers(
        expected: QuestionnaireExpectedResult,
        envelope: QuestionnaireResultEnvelope,
        answers: JSONObject
    ): AnswerValidationResult = AnswerValidationResult.Valid

    fun validateErrorAnswers(
        expected: QuestionnaireExpectedResult,
        envelope: QuestionnaireResultEnvelope,
        answers: JSONObject
    ): AnswerValidationResult = AnswerValidationResult.Valid
}

object AcceptAnyQuestionnaireAnswerValidator : QuestionnaireAnswerValidator

object QuestionnaireResultEnvelopeValidator {
    fun validate(
        resultJson: String,
        expected: QuestionnaireExpectedResult,
        answerValidator: QuestionnaireAnswerValidator = AcceptAnyQuestionnaireAnswerValidator
    ): QuestionnaireResultValidation {
        val envelope = try {
            QuestionnaireResultEnvelope.parse(resultJson)
        } catch (exception: QuestionnaireContractException) {
            return QuestionnaireResultValidation.Invalid(exception.code)
        }

        return when {
            envelope.protocolVersion != QuestQuestionnaireProtocol.Version ->
                invalid("unsupported_protocol")
            envelope.schema != QuestQuestionnaireProtocol.ResultSchema ->
                invalid("unsupported_schema")
            envelope.requestId != expected.requestId ->
                invalid("request_id_mismatch")
            envelope.nonce != expected.nonce ->
                invalid("nonce_mismatch")
            envelope.questionnaire.id != expected.questionnaireId ->
                invalid("questionnaire_mismatch")
            envelope.questionnaire.version < expected.minimumQuestionnaireVersion ->
                invalid("questionnaire_mismatch")
            envelope.stage != expected.stage ->
                invalid("stage_mismatch")
            envelope.screenSequence != expected.screenSequence ->
                invalid("screen_sequence_mismatch")
            else -> validateAnswers(expected, envelope, answerValidator)
        }
    }

    private fun validateAnswers(
        expected: QuestionnaireExpectedResult,
        envelope: QuestionnaireResultEnvelope,
        answerValidator: QuestionnaireAnswerValidator
    ): QuestionnaireResultValidation {
        val result = when (envelope.status) {
            QuestionnaireTerminalStatus.Completed ->
                answerValidator.validateCompletedAnswers(expected, envelope, envelope.answers)
            QuestionnaireTerminalStatus.Cancelled ->
                answerValidator.validateCancelledAnswers(expected, envelope, envelope.answers)
            QuestionnaireTerminalStatus.Error ->
                answerValidator.validateErrorAnswers(expected, envelope, envelope.answers)
        }

        return when (result) {
            AnswerValidationResult.Valid ->
                QuestionnaireResultValidation.Valid(envelope.status, envelope)
            is AnswerValidationResult.Invalid ->
                QuestionnaireResultValidation.Invalid(result.reason)
        }
    }

    private fun invalid(reason: String): QuestionnaireResultValidation.Invalid =
        QuestionnaireResultValidation.Invalid(reason)
}

