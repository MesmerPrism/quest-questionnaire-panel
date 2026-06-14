package io.github.mesmerprism.questquestionnaire.nativecaller

import io.github.mesmerprism.questquestionnaire.contract.QuestQuestionnaireProtocol
import io.github.mesmerprism.questquestionnaire.sdk.QuestQuestionnaireIntentContract

object QuestionnaireContract {
    const val ProtocolVersion = QuestQuestionnaireProtocol.Version
    const val ResultSchema = QuestQuestionnaireProtocol.ResultSchema
    const val StartAction = QuestQuestionnaireIntentContract.StartAction
    const val CompleteAction = QuestQuestionnaireIntentContract.CompleteAction
    const val RequestMimeType = QuestQuestionnaireIntentContract.RequestMimeType

    const val PanelPackage = QuestQuestionnaireIntentContract.PanelPackage
    const val PanelActivity = QuestQuestionnaireIntentContract.PanelActivity

    const val ExtraSessionId = QuestQuestionnaireIntentContract.ExtraSessionId
    const val ExtraRequestId = QuestQuestionnaireIntentContract.ExtraRequestId
    const val ExtraNonce = QuestQuestionnaireIntentContract.ExtraNonce
    const val ExtraRequestJson = QuestQuestionnaireIntentContract.ExtraRequestJson
    const val ExtraResultUri = QuestQuestionnaireIntentContract.ExtraResultUri
    const val ExtraReturnToCaller = QuestQuestionnaireIntentContract.ExtraReturnToCaller
    const val ExtraDebugRunSmoke = "io.github.mesmerprism.questquestionnaire.extra.DEBUG_RUN_SMOKE"
    const val ExtraDebugAutoSubmit = QuestQuestionnaireIntentContract.ExtraDebugAutoSubmit

    const val QuestionnaireId = "brb-questionnaire-v1"
    const val StageLanguageSelect = "language_select"
    const val StageDemographics = "demographics"
    const val StagePriorExperience = "prior_experience"
    const val DefaultStage = StageLanguageSelect

    val InitialStudySequence = listOf(
        StageLanguageSelect,
        StageDemographics,
        StagePriorExperience
    )
}
