package io.github.mesmerprism.questquestionnaire.nativecaller

import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
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
    const val ExtraDebugCommandScript =
        "io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_SCRIPT"
    const val ExtraDebugCommandIntervalMs =
        "io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_INTERVAL_MS"

    const val QuestionnaireId = BrbQuestionnaireContract.QuestionnaireId
    const val StageLanguageSelect = BrbQuestionnaireContract.StageLanguageSelect
    const val StageDemographics = BrbQuestionnaireContract.StageDemographics
    const val StagePriorExperience = BrbQuestionnaireContract.StagePriorExperience
    const val StagePostConditionPictographic =
        BrbQuestionnaireContract.StagePostConditionPictographic
    const val StagePostConditionPresence = BrbQuestionnaireContract.StagePostConditionPresence
    const val StagePostConditionLostOpportunity =
        BrbQuestionnaireContract.StagePostConditionLostOpportunity
    const val StageFinalEndConfirmation = BrbQuestionnaireContract.StageFinalEndConfirmation
    const val StageFinalExtraPressesPrompt = BrbQuestionnaireContract.StageFinalExtraPressesPrompt
    const val StageCompleteExportSummary = BrbQuestionnaireContract.StageCompleteExportSummary
    const val DefaultStage = StageLanguageSelect

    val InitialStudySequence = BrbQuestionnaireContract.InitialStudySequence
    val PostConditionSequence = BrbQuestionnaireContract.PostConditionSequence
    val ConditionOnePostSequence = BrbQuestionnaireContract.ConditionOnePostSequence
    val FinalSequence = BrbQuestionnaireContract.FinalSequence
}
