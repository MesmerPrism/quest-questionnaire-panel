package io.github.mesmerprism.questquestionnaire.panel

import androidx.compose.runtime.Composable
import org.json.JSONObject

data class QuestionnaireRendererConfig(
    val autoSubmit: Boolean,
    val debugCommandScript: String?,
    val debugCommandIntervalMs: Int
)

data class QuestionnaireRendererCallbacks(
    val onCompleted: (QuestionnaireRendererResult) -> Unit,
    val onCancelled: (QuestionnaireRendererTerminal) -> Unit,
    val onError: (QuestionnaireRendererError) -> Unit
)

data class QuestionnaireRendererResult(
    val answers: JSONObject,
    val currentStage: String,
    val screenIndex: Int
)

data class QuestionnaireRendererTerminal(
    val currentStage: String,
    val screenIndex: Int
)

data class QuestionnaireRendererError(
    val code: String,
    val message: String,
    val currentStage: String,
    val screenIndex: Int
)

interface QuestionnaireRenderer {
    @Composable
    fun Render(
        request: QuestionnaireRequest,
        config: QuestionnaireRendererConfig,
        callbacks: QuestionnaireRendererCallbacks
    )
}

interface QuestionnaireRendererFactory {
    fun canRender(request: QuestionnaireRequest): Boolean
    fun create(request: QuestionnaireRequest): QuestionnaireRenderer
}

class QuestionnaireRendererRegistry(
    private val factories: List<QuestionnaireRendererFactory>
) {
    fun rendererFor(request: QuestionnaireRequest): QuestionnaireRenderer? =
        factories.firstOrNull { it.canRender(request) }?.create(request)
}
