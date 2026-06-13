package io.github.mesmerprism.questquestionnaire.nativecaller

object QuestionnaireContract {
    const val ProtocolVersion = "quest.questionnaire.v1"
    const val ResultSchema = "quest.questionnaire.v1.result"
    const val StartAction = "io.github.mesmerprism.questquestionnaire.action.START"
    const val CompleteAction = "io.github.mesmerprism.questquestionnaire.action.COMPLETE"
    const val RequestMimeType = "application/vnd.quest-questionnaire.request+json"

    const val PanelPackage = "io.github.mesmerprism.questquestionnaire.panel"
    const val PanelActivity =
        "io.github.mesmerprism.questquestionnaire.panel.QuestionnaireActivity"

    const val ExtraSessionId = "session_id"
    const val ExtraRequestId = "request_id"
    const val ExtraNonce = "request_nonce"
    const val ExtraRequestJson = "request_json"
    const val ExtraResultUri = "result_uri"
    const val ExtraReturnToCaller = "return_to_caller"
    const val ExtraDebugRunSmoke = "io.github.mesmerprism.questquestionnaire.extra.DEBUG_RUN_SMOKE"
    const val ExtraDebugAutoSubmit = "io.github.mesmerprism.questquestionnaire.extra.DEBUG_AUTO_SUBMIT"

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
