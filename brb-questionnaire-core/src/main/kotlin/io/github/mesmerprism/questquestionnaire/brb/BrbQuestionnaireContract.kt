package io.github.mesmerprism.questquestionnaire.brb

object BrbQuestionnaireContract {
    const val QuestionnaireId = "brb-questionnaire-v1"
    const val QuestionnaireVersion = 1

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
