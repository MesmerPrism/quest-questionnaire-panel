package io.github.mesmerprism.questquestionnaire.panel.brb

import androidx.lifecycle.SavedStateHandle
import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireContract
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireDraftStore
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrbQuestionnaireViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savedStateHandleRestoresCurrentScreenAndAnswersAcrossRecreation() {
        val request = request()
        val store = store()
        val savedStateHandle = SavedStateHandle()

        val first = BrbQuestionnaireViewModel(savedStateHandle)
        first.bind(request, store)
        first.updateAnswers(
            first.answers.copy(
                language = "ja-JP",
                participantCode = "participant-1",
                priorExperience = "yes"
            )
        )
        first.goNext()

        val recreated = BrbQuestionnaireViewModel(savedStateHandle)
        recreated.bind(request, store)

        assertEquals(1, recreated.currentIndex)
        assertEquals("ja-JP", recreated.answers.language)
        assertEquals("participant-1", recreated.answers.participantCode)
        assertEquals("yes", recreated.answers.priorExperience)
    }

    @Test
    fun draftFileRestoresCurrentScreenAndAnswersForFreshViewModel() {
        val request = request()
        val store = store()

        val first = BrbQuestionnaireViewModel(SavedStateHandle())
        first.bind(request, store)
        first.updateAnswers(
            first.answers.copy(
                participantCode = "participant-2",
                priorExperience = "no"
            )
        )
        first.goNext()
        first.goNext()

        val restored = BrbQuestionnaireViewModel(SavedStateHandle())
        restored.bind(request, store)

        assertEquals(2, restored.currentIndex)
        assertEquals("participant-2", restored.answers.participantCode)
        assertEquals("no", restored.answers.priorExperience)
    }

    @Test
    fun timingSnapshotTracksScreenVisitsAndInteractions() {
        val request = request()
        val viewModel = BrbQuestionnaireViewModel(SavedStateHandle())
        viewModel.bind(request, store())

        viewModel.updateAnswers(viewModel.answers.copy(language = "ja-JP"))
        viewModel.recordValidationFailure()
        viewModel.goNext()
        viewModel.updateAnswers(viewModel.answers.copy(participantCode = "participant-3"))

        val timing = viewModel.timingSnapshot(viewModel.currentIndex)

        assertEquals(2, timing.screens.size)
        assertEquals(BrbQuestionnaireContract.StageLanguageSelect, timing.screens[0].screenId)
        assertEquals(0, timing.screens[0].ordinal)
        assertEquals(1, timing.screens[0].interactionCount)
        assertEquals(1, timing.screens[0].validationFailures)
        assertEquals(BrbQuestionnaireContract.StageDemographics, timing.screens[1].screenId)
        assertEquals(1, timing.screens[1].interactionCount)
        assertEquals(0, timing.screens[1].validationFailures)
    }

    private fun store(): QuestionnaireDraftStore =
        QuestionnaireDraftStore(temporaryFolder.newFolder("questionnaire-drafts"))

    private fun request(): QuestionnaireRequest =
        QuestionnaireRequest(
            protocolVersion = QuestionnaireContract.ProtocolVersion,
            sessionId = "session-1",
            requestId = "request-1",
            nonce = "0123456789abcdef",
            studyId = "brb",
            schemaId = BrbQuestionnaireContract.QuestionnaireId,
            openStage = BrbQuestionnaireContract.StageLanguageSelect,
            conditionNumber = null,
            screenSequence = BrbQuestionnaireContract.InitialStudySequence,
            questionnaireState = null
        )
}
