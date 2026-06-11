package io.github.mesmerprism.questquestionnaire.panel

object QuestionnaireContract {
    const val ProtocolVersion = "quest.questionnaire.v1"
    const val ResultSchema = "quest.questionnaire.v1.result"
    const val ActionStart = "io.github.mesmerprism.questquestionnaire.action.START"
    const val RequestMimeType = "application/vnd.quest-questionnaire.request+json"

    const val ExtraSessionId = "session_id"
    const val ExtraRequestId = "request_id"
    const val ExtraNonce = "request_nonce"
    const val ExtraRequestJson = "request_json"
    const val ExtraResultUri = "result_uri"
    const val ExtraReturnToCaller = "return_to_caller"
    const val ExtraDebugAutoSubmit = "io.github.mesmerprism.questquestionnaire.extra.DEBUG_AUTO_SUBMIT"

    val SupportedStages = setOf(
        "demographics",
        "post_condition:pictographic",
        "post_condition:presence_questionnaire",
        "post_condition:lost_opportunity",
        "complete:export_summary"
    )
}
