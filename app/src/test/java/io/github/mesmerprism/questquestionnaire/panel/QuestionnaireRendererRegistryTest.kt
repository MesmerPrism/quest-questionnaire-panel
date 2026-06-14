package io.github.mesmerprism.questquestionnaire.panel

import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionnaireRendererRegistryTest {
    @Test
    fun defaultRegistryFindsBrbRenderer() {
        val renderer = DefaultQuestionnaireRendererRegistry.create().rendererFor(
            request(
                schemaId = BrbQuestionnaireContract.QuestionnaireId,
                openStage = BrbQuestionnaireContract.StageLanguageSelect,
                screenSequence = BrbQuestionnaireContract.InitialStudySequence
            )
        )

        assertNotNull(renderer)
    }

    @Test
    fun defaultRegistryRejectsUnsupportedQuestionnaire() {
        val renderer = DefaultQuestionnaireRendererRegistry.create().rendererFor(
            request(
                schemaId = "generic-questionnaire-v1",
                openStage = "screen_one",
                screenSequence = listOf("screen_one")
            )
        )

        assertNull(renderer)
    }

    @Test
    fun defaultRegistryRejectsBrbRequestWithUnsupportedStage() {
        val renderer = DefaultQuestionnaireRendererRegistry.create().rendererFor(
            request(
                schemaId = BrbQuestionnaireContract.QuestionnaireId,
                openStage = "generic:screen",
                screenSequence = listOf("generic:screen")
            )
        )

        assertNull(renderer)
    }

    private fun request(
        schemaId: String,
        openStage: String,
        screenSequence: List<String>
    ): QuestionnaireRequest =
        QuestionnaireRequest(
            protocolVersion = QuestionnaireContract.ProtocolVersion,
            sessionId = "session-1",
            requestId = "request-1",
            nonce = "0123456789abcdef",
            studyId = "study",
            schemaId = schemaId,
            openStage = openStage,
            conditionNumber = null,
            screenSequence = screenSequence
        )
}
