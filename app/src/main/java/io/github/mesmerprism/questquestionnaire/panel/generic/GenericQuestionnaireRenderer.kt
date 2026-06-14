package io.github.mesmerprism.questquestionnaire.panel.generic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRenderer
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererCallbacks
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererConfig
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererFactory
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererResult
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererTerminal
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRequest
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

object GenericQuestionnaireContract {
    const val QuestionnaireId = "generic-questionnaire-v1"
    const val QuestionnaireVersion = 1

    const val StageIntro = "generic:intro"
    const val StageRating = "generic:rating"
    const val StageComment = "generic:comment"
    const val StageComplete = "generic:complete"

    val SupportedStages = setOf(
        StageIntro,
        StageRating,
        StageComment,
        StageComplete
    )

    val DemoSequence = listOf(
        StageIntro,
        StageRating,
        StageComment,
        StageComplete
    )
}

object GenericQuestionnaireRendererFactory : QuestionnaireRendererFactory {
    override fun canRender(request: QuestionnaireRequest): Boolean =
        request.schemaId == GenericQuestionnaireContract.QuestionnaireId &&
            request.openStage in GenericQuestionnaireContract.SupportedStages &&
            request.screenSequence.isNotEmpty() &&
            request.screenSequence.all { it in GenericQuestionnaireContract.SupportedStages } &&
            request.openStage in request.screenSequence

    override fun create(request: QuestionnaireRequest): QuestionnaireRenderer =
        GenericQuestionnaireRenderer
}

private object GenericQuestionnaireRenderer : QuestionnaireRenderer {
    @Composable
    override fun Render(
        request: QuestionnaireRequest,
        config: QuestionnaireRendererConfig,
        callbacks: QuestionnaireRendererCallbacks
    ) {
        GenericQuestionnairePanel(
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

@Composable
private fun GenericQuestionnairePanel(
    request: QuestionnaireRequest,
    autoSubmit: Boolean,
    debugCommandScript: String?,
    debugCommandIntervalMs: Int,
    onSubmit: (JSONObject, String, Int) -> Unit,
    onCancel: (String, Int) -> Unit
) {
    val sequence = request.screenSequence
    val saveKey = "${request.requestId}:${request.nonce}"
    var currentIndex by rememberSaveable(saveKey) {
        mutableStateOf(sequence.indexOf(request.openStage).coerceAtLeast(0))
    }
    var acknowledged by rememberSaveable(saveKey) { mutableStateOf(false) }
    var rating by rememberSaveable(saveKey) { mutableStateOf(5) }
    var comment by rememberSaveable(saveKey) { mutableStateOf("") }
    var autoSubmitted by rememberSaveable(saveKey) { mutableStateOf(false) }

    fun activeStage(): String = sequence[currentIndex]

    fun answersJson(completedStage: String): JSONObject =
        JSONObject()
            .put("open_stage", request.openStage)
            .put("completed_stage", completedStage)
            .put("screen_sequence", JSONArray(sequence))
            .put(
                "generic",
                JSONObject()
                    .put("acknowledged", acknowledged)
                    .put("rating", rating)
                    .put("comment", if (comment.isBlank()) JSONObject.NULL else comment)
            )

    fun canProceed(stage: String): Boolean =
        when (stage) {
            GenericQuestionnaireContract.StageIntro -> acknowledged
            GenericQuestionnaireContract.StageRating -> rating in 0..10
            GenericQuestionnaireContract.StageComment,
            GenericQuestionnaireContract.StageComplete -> true
            else -> false
        }

    fun goBack() {
        if (currentIndex > 0) {
            currentIndex -= 1
        }
    }

    fun goNext() {
        val stage = activeStage()
        if (!canProceed(stage)) {
            return
        }
        if (currentIndex == sequence.lastIndex) {
            onSubmit(answersJson(stage), stage, currentIndex)
        } else {
            currentIndex += 1
        }
    }

    fun submitCurrent() {
        val stage = activeStage()
        if (canProceed(stage)) {
            onSubmit(answersJson(stage), stage, currentIndex)
        }
    }

    LaunchedEffect(autoSubmit, request.requestId) {
        if (autoSubmit && !autoSubmitted) {
            autoSubmitted = true
            acknowledged = true
            rating = 7
            comment = "Debug generic questionnaire completion."
            onSubmit(
                answersJson(sequence.last()),
                sequence.last(),
                sequence.lastIndex
            )
        }
    }

    LaunchedEffect(debugCommandScript, request.requestId) {
        val commands = debugCommandScript.toGenericDebugCommands()
        if (commands.isEmpty()) {
            return@LaunchedEffect
        }

        val delayMs = debugCommandIntervalMs.coerceIn(0, 10_000).toLong()
        commands.forEach { command ->
            when {
                command == "ack" -> acknowledged = true
                command == "back" -> goBack()
                command == "cancel" -> onCancel(activeStage(), currentIndex)
                command == "next" -> goNext()
                command == "submit" -> submitCurrent()
                command.startsWith("rating:") ->
                    rating = command.substringAfter(":").toIntOrNull()?.coerceIn(0, 10) ?: rating
                command.startsWith("comment:") ->
                    comment = command.substringAfter(":")
            }
            if (delayMs > 0L) {
                delay(delayMs)
            }
        }
    }

    val currentStage = activeStage()
    GenericPanelScaffold(
        request = request,
        currentStage = currentStage,
        currentIndex = currentIndex,
        totalScreens = sequence.size,
        canGoBack = currentIndex > 0,
        canProceed = canProceed(currentStage),
        nextLabel = if (currentIndex == sequence.lastIndex) "Submit" else "Next",
        onBack = ::goBack,
        onNext = ::goNext,
        onCancel = { onCancel(activeStage(), currentIndex) }
    ) {
        when (currentStage) {
            GenericQuestionnaireContract.StageIntro ->
                GenericIntroScreen(
                    acknowledged = acknowledged,
                    onAcknowledgedChange = { acknowledged = it }
                )
            GenericQuestionnaireContract.StageRating ->
                GenericRatingScreen(
                    rating = rating,
                    onRatingChange = { rating = it }
                )
            GenericQuestionnaireContract.StageComment ->
                GenericCommentScreen(
                    comment = comment,
                    onCommentChange = { comment = it }
                )
            GenericQuestionnaireContract.StageComplete ->
                GenericCompleteScreen(
                    rating = rating,
                    comment = comment
                )
            else ->
                Text("Unsupported stage: $currentStage")
        }
    }
}

@Composable
private fun GenericPanelScaffold(
    request: QuestionnaireRequest,
    currentStage: String,
    currentIndex: Int,
    totalScreens: Int,
    canGoBack: Boolean,
    canProceed: Boolean,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Generic Questionnaire", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Study ${request.studyId} | Stage ${currentIndex + 1} of $totalScreens | $currentStage",
            style = MaterialTheme.typography.labelLarge
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, enabled = canGoBack) {
                Text("Back")
            }
            Button(onClick = onNext, enabled = canProceed) {
                Text(nextLabel)
            }
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun GenericIntroScreen(
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit
) {
    Text(
        "This demo questionnaire shows how a non-BRB XR app can request a simple " +
            "panel-owned form and receive a versioned result JSON."
    )
    FilterChip(
        selected = acknowledged,
        onClick = { onAcknowledgedChange(!acknowledged) },
        label = { Text("Ready to continue") }
    )
}

@Composable
private fun GenericRatingScreen(
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    Text("Choose a demo rating.")
    Text("Rating: $rating")
    Slider(
        value = rating.toFloat(),
        onValueChange = { onRatingChange(it.roundToInt().coerceIn(0, 10)) },
        valueRange = 0f..10f,
        steps = 9
    )
}

@Composable
private fun GenericCommentScreen(
    comment: String,
    onCommentChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = comment,
        onValueChange = onCommentChange,
        label = { Text("Optional note") },
        minLines = 4
    )
}

@Composable
private fun GenericCompleteScreen(
    rating: Int,
    comment: String
) {
    Text("Ready to submit.")
    Text("Rating: $rating")
    if (comment.isNotBlank()) {
        Text("Note: $comment")
    }
}

private fun String?.toGenericDebugCommands(): List<String> =
    this
        ?.split(",", ";", "\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
