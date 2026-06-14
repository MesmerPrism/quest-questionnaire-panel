package io.github.mesmerprism.questquestionnaire.contract

object QuestQuestionnaireProtocol {
    const val Version = "quest.questionnaire.v1"
    const val RequestSchema = "quest.questionnaire.v1.request"
    const val ResultSchema = "quest.questionnaire.v1.result"
    const val StartAction = "io.github.mesmerprism.questquestionnaire.action.START"
    const val RequestMimeType = "application/vnd.quest-questionnaire.request+json"
}

enum class QuestionnaireTerminalStatus(val wireValue: String) {
    Completed("completed"),
    Cancelled("cancelled"),
    Error("error");

    companion object {
        fun fromWireValue(value: String): QuestionnaireTerminalStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

