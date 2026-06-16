package io.github.mesmerprism.questquestionnaire.panel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuestionnaireDraftStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun restoresMatchingDraft() {
        val store = store()
        val request = request()

        store.write(
            request = request,
            screenIndex = 1,
            state = JSONObject().put("answer", "draft-value")
        )

        val snapshot = store.read(request)

        assertNotNull(snapshot)
        assertEquals(1, snapshot?.screenIndex)
        assertEquals("draft-value", snapshot?.state?.getString("answer"))
    }

    @Test
    fun draftFilenameDoesNotExposeRequestOrAnswerValues() {
        val store = store()
        val request = request(
            requestId = "participant-raw-code-123",
            nonce = "secret-nonce-0123456789"
        )

        store.write(
            request = request,
            screenIndex = 0,
            state = JSONObject()
                .put("participant_code", "participant-raw-code-123")
                .put("free_text_answer", "private answer")
        )

        val fileName = store.draftFileFor(request).name

        assertFalse(fileName.contains(request.requestId))
        assertFalse(fileName.contains(request.nonce))
        assertFalse(fileName.contains("participant"))
        assertFalse(fileName.contains("private"))
        assertTrue(Regex("""draft-v1-[0-9a-f]{64}\.json""").matches(fileName))
    }

    @Test
    fun rejectsDraftWhenStoredRequestIdentityDoesNotMatch() {
        val store = store()
        val request = request()
        store.write(
            request = request,
            screenIndex = 1,
            state = JSONObject().put("answer", "draft-value")
        )

        val file = store.draftFileFor(request)
        val tampered = JSONObject(file.readText())
            .put("nonce", "different-nonce-0123456789")
        file.writeText(tampered.toString())

        assertNull(store.read(request))
    }

    @Test
    fun clearDeletesDraftAndTemporaryFile() {
        val store = store()
        val request = request()
        store.write(
            request = request,
            screenIndex = 0,
            state = JSONObject().put("answer", "draft-value")
        )
        val file = store.draftFileFor(request)
        val tempFile = file.resolveSibling("${file.name}.tmp")
        tempFile.writeText("partial")

        store.clear(request)

        assertFalse(file.exists())
        assertFalse(tempFile.exists())
    }

    private fun store(): QuestionnaireDraftStore =
        QuestionnaireDraftStore(temporaryFolder.newFolder("questionnaire-drafts"))

    private fun request(
        requestId: String = "request-1",
        nonce: String = "0123456789abcdef"
    ): QuestionnaireRequest =
        QuestionnaireRequest(
            protocolVersion = QuestionnaireContract.ProtocolVersion,
            sessionId = "session-1",
            requestId = requestId,
            nonce = nonce,
            studyId = "brb",
            schemaId = "brb-questionnaire-v1",
            openStage = QuestionnaireContract.StagePostConditionPictographic,
            conditionNumber = 1,
            screenSequence = QuestionnaireContract.ConditionOnePostSequence,
            questionnaireState = null
        )
}
