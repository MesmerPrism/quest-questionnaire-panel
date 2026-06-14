package io.github.mesmerprism.questquestionnaire.panel

import io.github.mesmerprism.questquestionnaire.panel.brb.BrbQuestionnaireRendererFactory

object DefaultQuestionnaireRendererRegistry {
    fun create(): QuestionnaireRendererRegistry =
        QuestionnaireRendererRegistry(
            listOf(BrbQuestionnaireRendererFactory)
        )
}
