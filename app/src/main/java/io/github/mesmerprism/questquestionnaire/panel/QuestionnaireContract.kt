package io.github.mesmerprism.questquestionnaire.panel

import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.contract.QuestQuestionnaireProtocol

object QuestionnaireContract {
    const val ProtocolVersion = QuestQuestionnaireProtocol.Version
    const val ResultSchema = QuestQuestionnaireProtocol.ResultSchema
    const val ActionStart = QuestQuestionnaireProtocol.StartAction
    const val RequestMimeType = QuestQuestionnaireProtocol.RequestMimeType

    const val ExtraSessionId = "session_id"
    const val ExtraRequestId = "request_id"
    const val ExtraNonce = "request_nonce"
    const val ExtraRequestJson = "request_json"
    const val ExtraResultUri = "result_uri"
    const val ExtraReturnToCaller = "return_to_caller"
    const val ExtraDebugAutoSubmit = "io.github.mesmerprism.questquestionnaire.extra.DEBUG_AUTO_SUBMIT"
    const val ExtraDebugCommandScript = "io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_SCRIPT"
    const val ExtraDebugCommandIntervalMs = "io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_INTERVAL_MS"

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

    val SupportedStages = BrbQuestionnaireContract.SupportedStages
    val InitialStudySequence = BrbQuestionnaireContract.InitialStudySequence
    val PostConditionSequence = BrbQuestionnaireContract.PostConditionSequence
    val ConditionOnePostSequence = BrbQuestionnaireContract.ConditionOnePostSequence
    val FinalSequence = BrbQuestionnaireContract.FinalSequence
}
