package io.github.mesmerprism.questquestionnaire.panel

import io.github.mesmerprism.questquestionnaire.panel.brb.BrbQuestionnaireRendererFactory
import io.github.mesmerprism.questquestionnaire.panel.generic.GenericQuestionnaireRendererFactory
import io.github.mesmerprism.questquestionnaire.panel.maiaspatial.MaiaSpatialQuestionnaireRendererFactory

object DefaultQuestionnaireRendererRegistry {
    fun create(): QuestionnaireRendererRegistry =
        QuestionnaireRendererRegistry(
            listOf(
                BrbQuestionnaireRendererFactory,
                MaiaSpatialQuestionnaireRendererFactory,
                GenericQuestionnaireRendererFactory
            )
        )
}
