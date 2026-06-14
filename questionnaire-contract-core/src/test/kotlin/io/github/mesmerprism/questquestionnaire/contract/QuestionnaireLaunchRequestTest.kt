package io.github.mesmerprism.questquestionnaire.contract

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionnaireLaunchRequestTest {
    @Test
    fun parsesValidRequestFixture() {
        val request = QuestionnaireLaunchRequest.parse(fixture("request.brb.post_condition.valid.json"))

        assertEquals(QuestQuestionnaireProtocol.Version, request.protocolVersion)
        assertEquals(Expected.requestId, request.requestId)
        assertEquals(Expected.nonce, request.nonce)
        assertEquals("brb", request.studyId)
        assertEquals(Expected.questionnaireId, request.schemaId)
        assertEquals(Expected.stage, request.openStage)
        assertEquals(1, request.conditionNumber)
        assertEquals(Expected.screenSequence, request.screenSequence)
        assertEquals("P042", request.participantRef)
        assertEquals("unity", request.caller?.engine)
    }

    @Test
    fun parsesGenericRequestFixture() {
        val request = QuestionnaireLaunchRequest.parse(fixture("request.generic.valid.json"))

        assertEquals(QuestQuestionnaireProtocol.Version, request.protocolVersion)
        assertEquals("generic-questionnaire-v1", request.schemaId)
        assertEquals("generic:intro", request.openStage)
        assertEquals(
            listOf("generic:intro", "generic:rating", "generic:comment", "generic:complete"),
            request.screenSequence
        )
        assertEquals("unknown", request.caller?.engine)
    }
}
