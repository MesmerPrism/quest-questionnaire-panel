package io.github.mesmerprism.questquestionnaire.panel

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

    const val StageLanguageSelect = "language_select"
    const val StageDemographics = "demographics"
    const val StagePriorExperience = "prior_experience"
    const val StagePostConditionPictographic = "post_condition:pictographic"
    const val StagePostConditionPresence = "post_condition:presence_questionnaire"
    const val StagePostConditionLostOpportunity = "post_condition:lost_opportunity"
    const val StageFinalEndConfirmation = "final:end_confirmation"
    const val StageFinalExtraPressesPrompt = "final:extra_presses_prompt"
    const val StageCompleteExportSummary = "complete:export_summary"

    val SupportedStages = setOf(
        StageLanguageSelect,
        StageDemographics,
        StagePriorExperience,
        StagePostConditionPictographic,
        StagePostConditionPresence,
        StagePostConditionLostOpportunity,
        StageFinalEndConfirmation,
        StageFinalExtraPressesPrompt,
        StageCompleteExportSummary
    )

    val InitialStudySequence = listOf(
        StageLanguageSelect,
        StageDemographics,
        StagePriorExperience
    )

    val PostConditionSequence = listOf(
        StagePostConditionPictographic,
        StagePostConditionPresence
    )

    val ConditionOnePostSequence = listOf(
        StagePostConditionPictographic,
        StagePostConditionPresence,
        StagePostConditionLostOpportunity
    )

    val FinalSequence = listOf(
        StageFinalEndConfirmation,
        StageFinalExtraPressesPrompt,
        StageCompleteExportSummary
    )
}
