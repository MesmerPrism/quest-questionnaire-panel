package io.github.mesmerprism.questquestionnaire.panel.brb

import androidx.compose.runtime.Composable
import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.panel.BrbStudyQuestionnairePanel
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRenderer
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererCallbacks
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererConfig
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererFactory
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererResult
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererTerminal
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRequest

object BrbQuestionnaireRendererFactory : QuestionnaireRendererFactory {
    override fun canRender(request: QuestionnaireRequest): Boolean =
        request.schemaId == BrbQuestionnaireContract.QuestionnaireId &&
            request.openStage in BrbQuestionnaireContract.SupportedStages &&
            request.screenSequence.isNotEmpty() &&
            request.screenSequence.all { it in BrbQuestionnaireContract.SupportedStages } &&
            request.openStage in request.screenSequence

    override fun create(request: QuestionnaireRequest): QuestionnaireRenderer =
        BrbQuestionnaireRenderer
}

private object BrbQuestionnaireRenderer : QuestionnaireRenderer {
    @Composable
    override fun Render(
        request: QuestionnaireRequest,
        config: QuestionnaireRendererConfig,
        callbacks: QuestionnaireRendererCallbacks
    ) {
        BrbStudyQuestionnairePanel(
            request = request,
            autoSubmit = config.autoSubmit,
            debugCommandScript = config.debugCommandScript,
            debugCommandIntervalMs = config.debugCommandIntervalMs,
            onSubmit = { answers, currentStage, screenIndex ->
                callbacks.onCompleted(
                    QuestionnaireRendererResult(
                        answers = answers,
                        currentStage = currentStage,
                        screenIndex = screenIndex
                    )
                )
            },
            onCancel = { currentStage, screenIndex ->
                callbacks.onCancelled(
                    QuestionnaireRendererTerminal(
                        currentStage = currentStage,
                        screenIndex = screenIndex
                    )
                )
            }
        )
    }
}
