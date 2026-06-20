package io.github.mesmerprism.questquestionnaire.sdk

import io.github.mesmerprism.questquestionnaire.contract.QuestQuestionnaireProtocol

object QuestQuestionnaireIntentContract {
    const val CompleteAction = "io.github.mesmerprism.questquestionnaire.action.COMPLETE"

    const val PanelPackage = "io.github.mesmerprism.questquestionnaire.panel"
    const val PanelActivity =
        "io.github.mesmerprism.questquestionnaire.panel.QuestionnaireActivity"

    const val ExtraSessionId = "session_id"
    const val ExtraRequestId = "request_id"
    const val ExtraNonce = "request_nonce"
    const val ExtraRequestJson = "request_json"
    const val ExtraResultUri = "result_uri"
    const val ExtraReturnToCaller = "return_to_caller"
    const val ExtraDebugAutoSubmit =
        "io.github.mesmerprism.questquestionnaire.extra.DEBUG_AUTO_SUBMIT"
    const val ExtraDebugCommandScript =
        "io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_SCRIPT"
    const val ExtraDebugCommandIntervalMs =
        "io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_INTERVAL_MS"

    const val StartAction = QuestQuestionnaireProtocol.StartAction
    const val RequestMimeType = QuestQuestionnaireProtocol.RequestMimeType
}
