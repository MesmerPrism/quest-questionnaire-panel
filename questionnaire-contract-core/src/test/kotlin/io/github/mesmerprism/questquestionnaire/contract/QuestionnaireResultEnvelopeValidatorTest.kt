package io.github.mesmerprism.questquestionnaire.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireResultEnvelopeValidatorTest {
    @Test
    fun acceptsCompletedFixture() {
        val validation = validateFixture("result.brb.completed.valid.json")

        assertValid(QuestionnaireTerminalStatus.Completed, validation)
    }

    @Test
    fun acceptsCancelledFixture() {
        val validation = validateFixture("result.cancelled.valid.json")

        assertValid(QuestionnaireTerminalStatus.Cancelled, validation)
    }

    @Test
    fun acceptsErrorFixture() {
        val validation = validateFixture("result.error.valid.json")

        assertValid(QuestionnaireTerminalStatus.Error, validation)
    }

    @Test
    fun rejectsNonceMismatchFixture() {
        val validation = validateFixture("result.invalid.nonce_mismatch.json")

        assertInvalid("nonce_mismatch", validation)
    }

    @Test
    fun rejectsSchemaMismatchFixture() {
        val validation = validateFixture("result.invalid.schema.json")

        assertInvalid("unsupported_schema", validation)
    }

    @Test
    fun rejectsScreenSequenceMismatchFixture() {
        val validation = validateFixture("result.invalid.screen_sequence_mismatch.json")

        assertInvalid("screen_sequence_mismatch", validation)
    }

    private fun validateFixture(name: String): QuestionnaireResultValidation =
        QuestionnaireResultEnvelopeValidator.validate(fixture(name), Expected)

    private fun assertValid(
        expectedStatus: QuestionnaireTerminalStatus,
        validation: QuestionnaireResultValidation
    ) {
        assertTrue(validation is QuestionnaireResultValidation.Valid)
        assertEquals(
            expectedStatus,
            (validation as QuestionnaireResultValidation.Valid).status
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
}

