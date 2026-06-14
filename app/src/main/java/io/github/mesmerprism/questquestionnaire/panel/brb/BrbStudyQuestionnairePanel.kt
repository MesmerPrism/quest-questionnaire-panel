package io.github.mesmerprism.questquestionnaire.panel.brb

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireDraftSnapshot
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireDraftStore
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRequest
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireResultTiming
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireScreenTiming
import java.time.Instant
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun BrbStudyQuestionnairePanel(
    request: QuestionnaireRequest,
    autoSubmit: Boolean,
    debugCommandScript: String?,
    debugCommandIntervalMs: Int,
    draftStore: QuestionnaireDraftStore,
    onSubmit: (JSONObject, String, Int, QuestionnaireResultTiming) -> Unit,
    onCancel: (String, Int, QuestionnaireResultTiming) -> Unit
) {
    val viewModel: BrbQuestionnaireViewModel = viewModel()
    viewModel.bind(request, draftStore)

    val sequence = request.screenSequence
    val currentIndex = viewModel.currentIndex
    val answers = viewModel.answers
    val context = LocalContext.current
    val audioPlayer = viewModel.audioPlayerFor(context.applicationContext)
    val currentStage = sequence[currentIndex]

    DisposableEffect(audioPlayer) {
        onDispose { audioPlayer.release() }
    }

    LaunchedEffect(autoSubmit, request) {
        if (autoSubmit) {
            val completedAnswers = answers.withDebugCompletedDefaults(request)
            onSubmit(
                completedAnswers.toJson(request, completedStage = sequence.last()),
                sequence.last(),
                sequence.lastIndex,
                viewModel.timingSnapshot(sequence.lastIndex)
            )
        }
    }

    LaunchedEffect(currentStage, answers.language, request.conditionNumber) {
        audioPathFor(currentStage, answers.language, request.conditionNumber)?.let {
            audioPlayer.play(it)
        }
    }

    val isFirst = currentIndex == 0
    val isLast = currentIndex == sequence.lastIndex
    fun activeStage(): String = sequence[viewModel.currentIndex]

    fun replayAudio() {
        audioPathFor(activeStage(), viewModel.answers.language, request.conditionNumber)
            ?.let { audioPlayer.play(it) }
    }

    fun goBack() {
        viewModel.goBack()
    }

    fun goNext() {
        val stage = activeStage()
        if (!canProceed(stage, viewModel.answers)) {
            viewModel.recordValidationFailure()
            return
        }
        var handled = false
        if (stage == BrbQuestionnaireContract.StagePriorExperience) {
            priorExperienceFeedbackAudio(viewModel.answers.language, viewModel.answers.priorExperience)
                ?.let { audioPlayer.play(it) }
        }
        if (
            stage == BrbQuestionnaireContract.StageFinalEndConfirmation &&
            viewModel.answers.finalEndConfirmationRating == 10
        ) {
            audioPlayer.play(finalTenFeedbackAudio(viewModel.answers.language))
            val exportIndex = sequence.indexOf(BrbQuestionnaireContract.StageCompleteExportSummary)
            if (exportIndex > viewModel.currentIndex) {
                viewModel.goTo(exportIndex)
                handled = true
            }
        }

        if (!handled) {
            if (viewModel.currentIndex == sequence.lastIndex) {
                onSubmit(
                    viewModel.answers.toJson(request, completedStage = stage),
                    stage,
                    viewModel.currentIndex,
                    viewModel.timingSnapshot(viewModel.currentIndex)
                )
            } else {
                viewModel.goNext()
            }
        }
    }

    LaunchedEffect(debugCommandScript, request) {
        val commands = debugCommandScript.toDebugCommands()
        if (commands.isEmpty()) {
            return@LaunchedEffect
        }

        val delayMs = debugCommandIntervalMs.coerceIn(0, 10_000).toLong()
        commands.forEach { command ->
            when (val action = parsePanelDebugCommand(command)) {
                PanelDebugAction.Back -> goBack()
                PanelDebugAction.Cancel ->
                    onCancel(
                        activeStage(),
                        viewModel.currentIndex,
                        viewModel.timingSnapshot(viewModel.currentIndex)
                    )
                PanelDebugAction.Next -> goNext()
                PanelDebugAction.ReplayAudio -> replayAudio()
                PanelDebugAction.Submit ->
                    onSubmit(
                        viewModel.answers.toJson(request, completedStage = activeStage()),
                        activeStage(),
                        viewModel.currentIndex,
                        viewModel.timingSnapshot(viewModel.currentIndex)
                    )
                is PanelDebugAction.UpdateAnswers ->
                    viewModel.updateAnswers(action.transform(viewModel.answers))
                null -> Unit
            }
            if (delayMs > 0L) {
                delay(delayMs)
            }
        }
    }

    StudyPanelScaffold(
        request = request,
        currentStage = currentStage,
        currentIndex = currentIndex,
        totalScreens = sequence.size,
        canGoBack = !isFirst,
        canProceed = canProceed(currentStage, answers),
        nextLabel = if (isLast) "Submit" else "Next",
        onBack = ::goBack,
        onNext = ::goNext,
        onCancel = {
            onCancel(
                activeStage(),
                viewModel.currentIndex,
                viewModel.timingSnapshot(viewModel.currentIndex)
            )
        },
        onReplayAudio = ::replayAudio
    ) {
        when (currentStage) {
            BrbQuestionnaireContract.StageLanguageSelect ->
                LanguageScreen(answers, viewModel::updateAnswers)
            BrbQuestionnaireContract.StageDemographics ->
                DemographicsScreen(answers, viewModel::updateAnswers)
            BrbQuestionnaireContract.StagePriorExperience ->
                PriorExperienceScreen(answers, viewModel::updateAnswers)
            BrbQuestionnaireContract.StagePostConditionPictographic ->
                PictographicScreen(request, answers, viewModel::updateAnswers)
            BrbQuestionnaireContract.StagePostConditionPresence ->
                PresenceScreen(request, answers, viewModel::updateAnswers)
            BrbQuestionnaireContract.StagePostConditionLostOpportunity ->
                LostOpportunityScreen(answers, viewModel::updateAnswers)
            BrbQuestionnaireContract.StageFinalEndConfirmation ->
                FinalConfirmationScreen(answers, viewModel::updateAnswers)
            BrbQuestionnaireContract.StageFinalExtraPressesPrompt ->
                FinalExtraPressesPromptScreen()
            BrbQuestionnaireContract.StageCompleteExportSummary ->
                ExportSummaryScreen(request, answers)
            else ->
                UnknownStageScreen(currentStage)
        }
    }
}

@Composable
private fun StudyPanelScaffold(
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
    onReplayAudio: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Quest Questionnaire", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Study ${request.studyId} | Stage ${currentIndex + 1} of $totalScreens | $currentStage",
            style = MaterialTheme.typography.labelLarge
        )
        request.conditionNumber?.let {
            Text("Condition $it", style = MaterialTheme.typography.labelMedium)
        }

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
            OutlinedButton(onClick = onReplayAudio) {
                Text("Replay Audio")
            }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(onClick = onNext, enabled = canProceed) {
                Text(nextLabel)
            }
        }
    }
}

@Composable
private fun LanguageScreen(
    answers: QuestionnaireAnswerState,
    onChange: (QuestionnaireAnswerState) -> Unit
) {
    Text("Choose language", style = MaterialTheme.typography.headlineSmall)
    Text("This controls panel text and participant-facing voice prompts.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChip(
            label = "English",
            selected = answers.language == LocaleEnglish,
            onClick = { onChange(answers.copy(language = LocaleEnglish)) }
        )
        ChoiceChip(
            label = "Japanese",
            selected = answers.language == LocaleJapanese,
            onClick = { onChange(answers.copy(language = LocaleJapanese)) }
        )
    }
}

@Composable
private fun DemographicsScreen(
    answers: QuestionnaireAnswerState,
    onChange: (QuestionnaireAnswerState) -> Unit
) {
    Text("Participant setup", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = answers.participantCode,
        onValueChange = { onChange(answers.copy(participantCode = it.take(32))) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Participant code") },
        singleLine = true
    )
    Text("Age: ${answers.age.toInt()}")
    Slider(
        value = answers.age,
        onValueChange = { onChange(answers.copy(age = it)) },
        valueRange = 0f..100f,
        steps = 99
    )
}

@Composable
private fun PriorExperienceScreen(
    answers: QuestionnaireAnswerState,
    onChange: (QuestionnaireAnswerState) -> Unit
) {
    Text("One more question", style = MaterialTheme.typography.headlineSmall)
    Text("Do you have any experience with pressing big red buttons?")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChip(
            label = "Yes",
            selected = answers.priorExperience == AnswerYes,
            onClick = { onChange(answers.copy(priorExperience = AnswerYes)) }
        )
        ChoiceChip(
            label = "No",
            selected = answers.priorExperience == AnswerNo,
            onClick = { onChange(answers.copy(priorExperience = AnswerNo)) }
        )
    }
}

@Composable
private fun PictographicScreen(
    request: QuestionnaireRequest,
    answers: QuestionnaireAnswerState,
    onChange: (QuestionnaireAnswerState) -> Unit
) {
    Text("After Button Session ${request.conditionNumber ?: ""}", style = MaterialTheme.typography.headlineSmall)
    Text("Presence slider: ${answers.presenceSlider.toInt()} / 100")
    Slider(
        value = answers.presenceSlider,
        onValueChange = { onChange(answers.copy(presenceSlider = it)) },
        valueRange = 0f..100f
    )
    Text("Redness VAS: ${answers.rednessVas.toInt()} / 100")
    Slider(
        value = answers.rednessVas,
        onValueChange = { onChange(answers.copy(rednessVas = it)) },
        valueRange = 0f..100f
    )
    Text("Redness Likert: ${answers.rednessLikert} / 7")
    Slider(
        value = answers.rednessLikert.toFloat(),
        onValueChange = { onChange(answers.copy(rednessLikert = it.toInt().coerceIn(1, 7))) },
        valueRange = 1f..7f,
        steps = 5
    )
}

@Composable
private fun PresenceScreen(
    request: QuestionnaireRequest,
    answers: QuestionnaireAnswerState,
    onChange: (QuestionnaireAnswerState) -> Unit
) {
    Text("Presence questionnaire ${request.conditionNumber ?: ""}", style = MaterialTheme.typography.headlineSmall)
    Text("Select one value for each statement.")
    PresenceItems.forEach { item ->
        Text(item.prompt)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..6).forEach { value ->
                ChoiceChip(
                    label = value.toString(),
                    selected = answers.ipqAnswers[item.id] == value,
                    onClick = {
                        onChange(
                            answers.copy(
                                ipqAnswers = answers.ipqAnswers + (item.id to value)
                            )
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun LostOpportunityScreen(
    answers: QuestionnaireAnswerState,
    onChange: (QuestionnaireAnswerState) -> Unit
) {
    Text("Between sessions", style = MaterialTheme.typography.headlineSmall)
    Text("The next button session will start when you return to the Unity app.")
    ChoiceChip(
        label = "I understand",
        selected = answers.lostOpportunityAcknowledged,
        onClick = {
            onChange(
                answers.copy(lostOpportunityAcknowledged = !answers.lostOpportunityAcknowledged)
            )
        }
    )
}

@Composable
private fun FinalConfirmationScreen(
    answers: QuestionnaireAnswerState,
    onChange: (QuestionnaireAnswerState) -> Unit
) {
    Text("Final question", style = MaterialTheme.typography.headlineSmall)
    Text("How sure are you that you want to end the experiment, on a scale of 1 to 10?")
    Text("Selected: ${answers.finalEndConfirmationRating ?: "-"}")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        (1..10).forEach { value ->
            ChoiceChip(
                label = value.toString(),
                selected = answers.finalEndConfirmationRating == value,
                onClick = { onChange(answers.copy(finalEndConfirmationRating = value)) }
            )
        }
    }
}

@Composable
private fun FinalExtraPressesPromptScreen() {
    Text("Final button interaction", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Return to the Unity app for the extra physical interaction with the 3D Big Red Button. " +
            "The panel records this prompt only; Unity owns the extra button press count."
    )
}

@Composable
private fun ExportSummaryScreen(
    request: QuestionnaireRequest,
    answers: QuestionnaireAnswerState
) {
    Text("Ready to return", style = MaterialTheme.typography.headlineSmall)
    Text("The panel will write results to the caller-owned result URI.")
    Text("Request: ${request.requestId}")
    Text("Language: ${answers.language}")
}

@Composable
private fun UnknownStageScreen(stage: String) {
    Text("Unsupported stage", style = MaterialTheme.typography.headlineSmall)
    Text(stage)
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

private data class PresenceItem(
    val id: String,
    val prompt: String
)

private val PresenceItems = listOf(
    PresenceItem("spatial_presence_1", "I had a sense of being inside the button room."),
    PresenceItem("spatial_presence_2", "The button felt located in the same space as me."),
    PresenceItem("involvement_1", "I was focused on the experience rather than the outside room."),
    PresenceItem("experienced_realism_1", "The situation felt coherent while I was in it."),
    PresenceItem("interaction_memory_1", "I can clearly remember what I chose to do with the button.")
)

internal data class QuestionnaireAnswerState(
    val language: String = LocaleEnglish,
    val participantCode: String = "",
    val age: Float = 25f,
    val priorExperience: String = AnswerNotAnswered,
    val presenceSlider: Float = 50f,
    val rednessVas: Float = 50f,
    val rednessLikert: Int = 4,
    val ipqAnswers: Map<String, Int> = PresenceItems.associate { it.id to 3 },
    val lostOpportunityAcknowledged: Boolean = false,
    val finalEndConfirmationRating: Int? = null
) {
    fun toJson(request: QuestionnaireRequest, completedStage: String): JSONObject {
        val sequence = request.screenSequence
        return JSONObject()
            .put("open_stage", request.openStage)
            .put("completed_stage", completedStage)
            .put("screen_sequence", JSONArray(sequence))
            .put("condition_number", request.conditionNumber ?: JSONObject.NULL)
            .apply {
                if (BrbQuestionnaireContract.StageLanguageSelect in sequence) {
                    put("language", language)
                }
                if (BrbQuestionnaireContract.StageDemographics in sequence) {
                    put(
                        "demographics",
                        JSONObject()
                            .put("participant_code", participantCode)
                            .put("age", age.toInt())
                    )
                }
                if (BrbQuestionnaireContract.StagePriorExperience in sequence) {
                    put(
                        "prior_button_experience",
                        JSONObject().put("answer", priorExperience)
                    )
                }
                if (sequence.any { it in BrbPostConditionAnswerStages }) {
                    put("post_condition", postConditionJson(sequence))
                }
                if (sequence.any { it in BrbFinalAnswerStages }) {
                    put("final", finalJson(sequence))
                }
            }
    }

    private fun postConditionJson(sequence: List<String>): JSONObject =
        JSONObject().apply {
            if (BrbQuestionnaireContract.StagePostConditionPictographic in sequence) {
                put("presence_slider", presenceSlider.toInt())
                put("redness_vas", rednessVas.toInt())
                put("redness_likert", rednessLikert)
            }
            if (BrbQuestionnaireContract.StagePostConditionPresence in sequence) {
                put("presence_questionnaire", JSONObject(ipqAnswers))
            }
            if (BrbQuestionnaireContract.StagePostConditionLostOpportunity in sequence) {
                put("lost_opportunity_acknowledged", lostOpportunityAcknowledged)
            }
        }

    private fun finalJson(sequence: List<String>): JSONObject =
        JSONObject().apply {
            if (BrbQuestionnaireContract.StageFinalEndConfirmation in sequence) {
                put("end_confirmation_rating", finalEndConfirmationRating ?: JSONObject.NULL)
                put("selected_10", finalEndConfirmationRating == 10)
            }
            if (BrbQuestionnaireContract.StageFinalExtraPressesPrompt in sequence) {
                put("unity_owns_final_physical_presses", true)
            }
        }

    fun withDebugCompletedDefaults(request: QuestionnaireRequest): QuestionnaireAnswerState {
        val sequence = request.screenSequence
        return copy(
            priorExperience = if (
                BrbQuestionnaireContract.StagePriorExperience in sequence &&
                priorExperience == AnswerNotAnswered
            ) {
                AnswerNo
            } else {
                priorExperience
            },
            lostOpportunityAcknowledged = if (
                BrbQuestionnaireContract.StagePostConditionLostOpportunity in sequence
            ) {
                true
            } else {
                lostOpportunityAcknowledged
            },
            finalEndConfirmationRating = if (
                BrbQuestionnaireContract.StageFinalEndConfirmation in sequence &&
                finalEndConfirmationRating == null
            ) {
                10
            } else {
                finalEndConfirmationRating
            }
        )
    }

    fun toDraftJson(): JSONObject =
        JSONObject()
            .put("language", language)
            .put("participant_code", participantCode)
            .put("age", age)
            .put("prior_experience", priorExperience)
            .put("presence_slider", presenceSlider)
            .put("redness_vas", rednessVas)
            .put("redness_likert", rednessLikert)
            .put("ipq_answers", JSONObject(ipqAnswers))
            .put("lost_opportunity_acknowledged", lostOpportunityAcknowledged)
            .put("final_end_confirmation_rating", finalEndConfirmationRating ?: JSONObject.NULL)

    companion object {
        fun fromDraftJson(json: JSONObject): QuestionnaireAnswerState =
            QuestionnaireAnswerState(
                language = normalizeDraftLanguage(json.optString("language", LocaleEnglish)),
                participantCode = json.optString("participant_code", "").take(32),
                age = json.optDouble("age", 25.0).toFloat().coerceIn(0f, 100f),
                priorExperience = normalizeDraftYesNo(
                    json.optString("prior_experience", AnswerNotAnswered)
                ),
                presenceSlider = json.optDouble("presence_slider", 50.0)
                    .toFloat()
                    .coerceIn(0f, 100f),
                rednessVas = json.optDouble("redness_vas", 50.0)
                    .toFloat()
                    .coerceIn(0f, 100f),
                rednessLikert = json.optInt("redness_likert", 4).coerceIn(1, 7),
                ipqAnswers = draftIpqAnswers(json.optJSONObject("ipq_answers")),
                lostOpportunityAcknowledged = json.optBoolean(
                    "lost_opportunity_acknowledged",
                    false
                ),
                finalEndConfirmationRating = json.optionalDraftInt(
                    "final_end_confirmation_rating",
                    min = 1,
                    max = 10
                )
            )

        private fun normalizeDraftLanguage(value: String): String =
            if (value == LocaleJapanese) LocaleJapanese else LocaleEnglish

        private fun normalizeDraftYesNo(value: String): String =
            when (value) {
                AnswerYes -> AnswerYes
                AnswerNo -> AnswerNo
                else -> AnswerNotAnswered
            }

        private fun draftIpqAnswers(json: JSONObject?): Map<String, Int> =
            PresenceItems.associate { item ->
                item.id to (json?.optInt(item.id, 3) ?: 3).coerceIn(0, 6)
            }

        private fun JSONObject.optionalDraftInt(name: String, min: Int, max: Int): Int? =
            if (!has(name) || isNull(name)) {
                null
            } else {
                optInt(name).coerceIn(min, max)
            }
    }
}

internal class BrbQuestionnaireViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val currentIndexState = mutableIntStateOf(0)
    private val answersState = mutableStateOf(QuestionnaireAnswerState())
    private var boundStateKey: String? = null
    private var request: QuestionnaireRequest? = null
    private var draftStore: QuestionnaireDraftStore? = null
    private var timingTracker: BrbTimingTracker? = null
    private var audioPlayer: QuestionnaireAudioPlayer? = null

    val currentIndex: Int
        get() = currentIndexState.intValue

    val answers: QuestionnaireAnswerState
        get() = answersState.value

    fun bind(request: QuestionnaireRequest, draftStore: QuestionnaireDraftStore) {
        val stateKey = request.stateKey()
        if (boundStateKey == stateKey) {
            return
        }

        this.boundStateKey = stateKey
        this.request = request
        this.draftStore = draftStore

        val restored = restoreFromSavedState(request)
            ?: draftStore.read(request)?.toBrbState()
        currentIndexState.intValue = restored?.screenIndex
            ?: request.screenSequence.indexOf(request.openStage).coerceAtLeast(0)
        answersState.value = restored?.answers ?: QuestionnaireAnswerState()
        timingTracker = restored?.timingTracker
            ?: BrbTimingTracker.start(request, currentIndexState.intValue)
        saveStateAndDraft()
    }

    fun audioPlayerFor(context: Context): QuestionnaireAudioPlayer =
        audioPlayer ?: QuestionnaireAudioPlayer(context).also {
            audioPlayer = it
        }

    fun updateAnswers(nextAnswers: QuestionnaireAnswerState) {
        timingTracker?.recordInteraction(currentIndex)
        answersState.value = nextAnswers
        saveStateAndDraft()
    }

    fun goBack() {
        goTo(currentIndex - 1)
    }

    fun goNext() {
        goTo(currentIndex + 1)
    }

    fun goTo(index: Int) {
        val sequence = request?.screenSequence ?: return
        val nextIndex = index.coerceIn(sequence.indices)
        if (nextIndex != currentIndex) {
            timingTracker?.moveToScreen(nextIndex)
        }
        currentIndexState.intValue = nextIndex
        saveStateAndDraft()
    }

    fun recordValidationFailure() {
        timingTracker?.recordValidationFailure(currentIndex)
        saveStateAndDraft()
    }

    fun timingSnapshot(terminalScreenIndex: Int): QuestionnaireResultTiming =
        timingTracker?.snapshot(terminalScreenIndex)
            ?: BrbTimingTracker.start(
                requireNotNull(request),
                terminalScreenIndex
            ).snapshot(terminalScreenIndex)

    override fun onCleared() {
        audioPlayer?.release()
        audioPlayer = null
        super.onCleared()
    }

    private fun saveStateAndDraft() {
        val currentRequest = request ?: return
        savedStateHandle[SavedStateKey] = currentRequest.stateKey()
        savedStateHandle[SavedCurrentIndex] = currentIndex
        savedStateHandle[SavedAnswers] = answers.toDraftJson().toString()
        savedStateHandle[SavedTiming] = timingTracker?.toDraftJson()?.toString()
        runCatching {
            draftStore?.write(
                request = currentRequest,
                screenIndex = currentIndex,
                state = JSONObject()
                    .put("answers", answers.toDraftJson())
                    .put("timing", timingTracker?.toDraftJson() ?: JSONObject.NULL)
            )
        }
    }

    private fun restoreFromSavedState(request: QuestionnaireRequest): BrbPanelState? {
        if (savedStateHandle.get<String>(SavedStateKey) != request.stateKey()) {
            return null
        }
        val screenIndex = savedStateHandle.get<Int>(SavedCurrentIndex) ?: return null
        if (screenIndex !in request.screenSequence.indices) {
            return null
        }
        val answersJson = savedStateHandle.get<String>(SavedAnswers) ?: return null
        val timingJson = savedStateHandle.get<String>(SavedTiming)
        return runCatching {
            BrbPanelState(
                screenIndex = screenIndex,
                answers = QuestionnaireAnswerState.fromDraftJson(JSONObject(answersJson)),
                timingTracker = timingJson?.let {
                    BrbTimingTracker.fromDraftJson(JSONObject(it), request)
                }
            )
        }.getOrNull()
    }

    private fun QuestionnaireDraftSnapshot.toBrbState(): BrbPanelState? =
        runCatching {
            val answersJson = state.optJSONObject("answers") ?: state
            BrbPanelState(
                screenIndex = screenIndex,
                answers = QuestionnaireAnswerState.fromDraftJson(answersJson),
                timingTracker = state.optJSONObject("timing")?.let {
                    BrbTimingTracker.fromDraftJson(it, requireNotNull(request))
                }
            )
        }.getOrNull()

    private fun QuestionnaireRequest.stateKey(): String =
        listOf(
            protocolVersion,
            sessionId,
            requestId,
            nonce,
            studyId,
            schemaId,
            openStage,
            screenSequence.joinToString(separator = "\u0000")
        ).joinToString(separator = "\u0001")

    private data class BrbPanelState(
        val screenIndex: Int,
        val answers: QuestionnaireAnswerState,
        val timingTracker: BrbTimingTracker?
    )

    private companion object {
        const val SavedStateKey = "brb_state_key"
        const val SavedCurrentIndex = "brb_current_index"
        const val SavedAnswers = "brb_answers"
        const val SavedTiming = "brb_timing"
    }
}

private class BrbTimingTracker private constructor(
    private val request: QuestionnaireRequest,
    private val startedAt: Instant,
    private val startedNanoTime: Long,
    private val visits: MutableList<BrbScreenVisit>
) {
    fun moveToScreen(nextIndex: Int) {
        val elapsedMs = elapsedMs()
        visits.lastOrNull { it.leftElapsedMs == null }?.leftElapsedMs = elapsedMs
        visits += BrbScreenVisit(
            screenId = request.screenSequence[nextIndex],
            ordinal = nextIndex,
            enteredElapsedMs = elapsedMs
        )
    }

    fun recordInteraction(currentIndex: Int) {
        val visit = currentVisit(currentIndex) ?: return
        val elapsedMs = elapsedMs()
        if (visit.firstInteractionElapsedMs == null) {
            visit.firstInteractionElapsedMs = elapsedMs
        }
        visit.interactionCount += 1
    }

    fun recordValidationFailure(currentIndex: Int) {
        currentVisit(currentIndex)?.let {
            it.validationFailures += 1
        }
    }

    fun snapshot(terminalScreenIndex: Int): QuestionnaireResultTiming {
        val submittedElapsedMs = elapsedMs()
        val submittedAt = wallClockAt(submittedElapsedMs)
        val screenTimings = visits.map { visit ->
            val leftElapsedMs = visit.leftElapsedMs ?: submittedElapsedMs
            QuestionnaireScreenTiming(
                screenId = visit.screenId,
                ordinal = visit.ordinal,
                enteredAt = wallClockAt(visit.enteredElapsedMs),
                enteredElapsedMs = visit.enteredElapsedMs,
                firstInteractionAt = visit.firstInteractionElapsedMs?.let(::wallClockAt),
                firstInteractionElapsedMs = visit.firstInteractionElapsedMs,
                leftAt = wallClockAt(leftElapsedMs),
                leftElapsedMs = leftElapsedMs,
                durationMs = leftElapsedMs - visit.enteredElapsedMs,
                interactionCount = visit.interactionCount,
                validationFailures = visit.validationFailures
            )
        }.ifEmpty {
            val fallbackIndex = terminalScreenIndex.coerceIn(request.screenSequence.indices)
            listOf(
                QuestionnaireScreenTiming(
                    screenId = request.screenSequence[fallbackIndex],
                    ordinal = fallbackIndex,
                    enteredAt = startedAt,
                    enteredElapsedMs = 0L,
                    firstInteractionAt = null,
                    firstInteractionElapsedMs = null,
                    leftAt = submittedAt,
                    leftElapsedMs = submittedElapsedMs,
                    durationMs = submittedElapsedMs,
                    interactionCount = 0,
                    validationFailures = 0
                )
            )
        }

        return QuestionnaireResultTiming(
            startedAt = startedAt,
            submittedAt = submittedAt,
            durationMs = submittedElapsedMs,
            screens = screenTimings
        )
    }

    fun toDraftJson(): JSONObject =
        JSONObject()
            .put("started_at", startedAt.toString())
            .put("elapsed_ms", elapsedMs())
            .put("screens", JSONArray(visits.map { it.toJson() }))

    private fun currentVisit(currentIndex: Int): BrbScreenVisit? =
        visits.lastOrNull { it.leftElapsedMs == null && it.ordinal == currentIndex }

    private fun elapsedMs(): Long =
        ((System.nanoTime() - startedNanoTime) / 1_000_000L).coerceAtLeast(0L)

    private fun wallClockAt(elapsedMs: Long): Instant =
        startedAt.plusMillis(elapsedMs.coerceAtLeast(0L))

    companion object {
        fun start(request: QuestionnaireRequest, initialScreenIndex: Int): BrbTimingTracker {
            val now = System.nanoTime()
            return BrbTimingTracker(
                request = request,
                startedAt = Instant.now(),
                startedNanoTime = now,
                visits = mutableListOf(
                    BrbScreenVisit(
                        screenId = request.screenSequence[initialScreenIndex],
                        ordinal = initialScreenIndex,
                        enteredElapsedMs = 0L
                    )
                )
            )
        }

        fun fromDraftJson(
            json: JSONObject,
            request: QuestionnaireRequest
        ): BrbTimingTracker? =
            runCatching {
                val visits = json.optJSONArray("screens")?.let { screens ->
                    (0 until screens.length()).mapNotNull { index ->
                        screens.optJSONObject(index)?.let { BrbScreenVisit.fromJson(it, request) }
                    }.toMutableList()
                } ?: mutableListOf()
                if (visits.isEmpty()) {
                    return@runCatching null
                }

                val elapsedMs = json.optLong("elapsed_ms", visits.maxElapsedMs())
                BrbTimingTracker(
                    request = request,
                    startedAt = Instant.parse(json.getString("started_at")),
                    startedNanoTime = System.nanoTime() - elapsedMs * 1_000_000L,
                    visits = visits
                )
            }.getOrNull()
    }
}

private data class BrbScreenVisit(
    val screenId: String,
    val ordinal: Int,
    val enteredElapsedMs: Long,
    var firstInteractionElapsedMs: Long? = null,
    var leftElapsedMs: Long? = null,
    var interactionCount: Int = 0,
    var validationFailures: Int = 0
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("screen_id", screenId)
            .put("ordinal", ordinal)
            .put("entered_elapsed_ms", enteredElapsedMs)
            .put("first_interaction_elapsed_ms", firstInteractionElapsedMs ?: JSONObject.NULL)
            .put("left_elapsed_ms", leftElapsedMs ?: JSONObject.NULL)
            .put("interaction_count", interactionCount)
            .put("validation_failures", validationFailures)

    companion object {
        fun fromJson(json: JSONObject, request: QuestionnaireRequest): BrbScreenVisit? {
            val ordinal = json.optInt("ordinal", -1)
            if (ordinal !in request.screenSequence.indices) {
                return null
            }
            return BrbScreenVisit(
                screenId = request.screenSequence[ordinal],
                ordinal = ordinal,
                enteredElapsedMs = json.optLong("entered_elapsed_ms", 0L).coerceAtLeast(0L),
                firstInteractionElapsedMs = json.optionalNonNegativeLong(
                    "first_interaction_elapsed_ms"
                ),
                leftElapsedMs = json.optionalNonNegativeLong("left_elapsed_ms"),
                interactionCount = json.optInt("interaction_count", 0).coerceAtLeast(0),
                validationFailures = json.optInt("validation_failures", 0).coerceAtLeast(0)
            )
        }
    }
}

private fun List<BrbScreenVisit>.maxElapsedMs(): Long =
    maxOfOrNull { visit ->
        listOfNotNull(
            visit.enteredElapsedMs,
            visit.firstInteractionElapsedMs,
            visit.leftElapsedMs
        ).maxOrNull() ?: 0L
    } ?: 0L

private fun JSONObject.optionalNonNegativeLong(name: String): Long? =
    if (!has(name) || isNull(name)) {
        null
    } else {
        optLong(name, 0L).coerceAtLeast(0L)
    }

private val BrbPostConditionAnswerStages = setOf(
    BrbQuestionnaireContract.StagePostConditionPictographic,
    BrbQuestionnaireContract.StagePostConditionPresence,
    BrbQuestionnaireContract.StagePostConditionLostOpportunity
)

private val BrbFinalAnswerStages = setOf(
    BrbQuestionnaireContract.StageFinalEndConfirmation,
    BrbQuestionnaireContract.StageFinalExtraPressesPrompt,
    BrbQuestionnaireContract.StageCompleteExportSummary
)

private fun canProceed(stage: String, answers: QuestionnaireAnswerState): Boolean =
    when (stage) {
        BrbQuestionnaireContract.StagePriorExperience ->
            answers.priorExperience != AnswerNotAnswered
        BrbQuestionnaireContract.StagePostConditionLostOpportunity ->
            answers.lostOpportunityAcknowledged
        BrbQuestionnaireContract.StageFinalEndConfirmation ->
            answers.finalEndConfirmationRating != null
        else -> true
    }

private sealed class PanelDebugAction {
    object Back : PanelDebugAction()
    object Cancel : PanelDebugAction()
    object Next : PanelDebugAction()
    object ReplayAudio : PanelDebugAction()
    object Submit : PanelDebugAction()
    data class UpdateAnswers(
        val transform: (QuestionnaireAnswerState) -> QuestionnaireAnswerState
    ) : PanelDebugAction()
}

private fun String?.toDebugCommands(): List<String> =
    if (isNullOrBlank()) {
        emptyList()
    } else {
        split(';', ',', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

private fun parsePanelDebugCommand(rawCommand: String): PanelDebugAction? {
    val (command, argument) = splitPanelDebugCommand(rawCommand)
    return when (command) {
        "back" -> PanelDebugAction.Back
        "cancel" -> PanelDebugAction.Cancel
        "next", "click_next", "tap_next" -> PanelDebugAction.Next
        "replay", "replay_audio" -> PanelDebugAction.ReplayAudio
        "submit", "click_submit", "tap_submit" -> PanelDebugAction.Submit
        "english", "language_en", "select_language_en" ->
            updateAnswers { it.copy(language = LocaleEnglish) }
        "japanese", "language_ja", "select_language_ja" ->
            updateAnswers { it.copy(language = LocaleJapanese) }
        "language", "select_language" ->
            updateAnswers { it.copy(language = normalizeDebugLanguage(argument)) }
        "participant", "participant_code", "code" ->
            updateAnswers { it.copy(participantCode = argument.take(32)) }
        "age" ->
            updateAnswers { it.copy(age = argument.toFloatOrNull()?.coerceIn(0f, 100f) ?: it.age) }
        "prior_yes", "prior_experience_yes" ->
            updateAnswers { it.copy(priorExperience = AnswerYes) }
        "prior_no", "prior_experience_no" ->
            updateAnswers { it.copy(priorExperience = AnswerNo) }
        "prior", "prior_experience" ->
            updateAnswers { it.copy(priorExperience = normalizeYesNo(argument, it.priorExperience)) }
        "presence", "presence_slider" ->
            updateAnswers { it.copy(presenceSlider = debugPercent(argument, it.presenceSlider)) }
        "redness", "redness_vas" ->
            updateAnswers { it.copy(rednessVas = debugPercent(argument, it.rednessVas)) }
        "redness_likert" ->
            updateAnswers { it.copy(rednessLikert = debugInt(argument, it.rednessLikert, 1, 7)) }
        "ipq_all", "presence_all" ->
            updateAnswers {
                val value = debugInt(argument, 3, 0, 6)
                it.copy(ipqAnswers = PresenceItems.associate { item -> item.id to value })
            }
        "ipq", "presence_item" ->
            updateAnswers { applyIpqDebugCommand(it, argument) }
        "ack", "ack_lost", "lost_opportunity_ack", "lost_opportunity_acknowledged" ->
            updateAnswers { it.copy(lostOpportunityAcknowledged = true) }
        "final_1", "final_rating_1" -> updateAnswers { it.copy(finalEndConfirmationRating = 1) }
        "final_2", "final_rating_2" -> updateAnswers { it.copy(finalEndConfirmationRating = 2) }
        "final_3", "final_rating_3" -> updateAnswers { it.copy(finalEndConfirmationRating = 3) }
        "final_4", "final_rating_4" -> updateAnswers { it.copy(finalEndConfirmationRating = 4) }
        "final_5", "final_rating_5" -> updateAnswers { it.copy(finalEndConfirmationRating = 5) }
        "final_6", "final_rating_6" -> updateAnswers { it.copy(finalEndConfirmationRating = 6) }
        "final_7", "final_rating_7" -> updateAnswers { it.copy(finalEndConfirmationRating = 7) }
        "final_8", "final_rating_8" -> updateAnswers { it.copy(finalEndConfirmationRating = 8) }
        "final_9", "final_rating_9" -> updateAnswers { it.copy(finalEndConfirmationRating = 9) }
        "final_10", "final_rating_10" -> updateAnswers { it.copy(finalEndConfirmationRating = 10) }
        "final", "final_rating", "rating" ->
            updateAnswers {
                it.copy(
                    finalEndConfirmationRating =
                        debugInt(argument, it.finalEndConfirmationRating ?: 1, 1, 10)
                )
            }
        else -> null
    }
}

private fun updateAnswers(
    transform: (QuestionnaireAnswerState) -> QuestionnaireAnswerState
): PanelDebugAction = PanelDebugAction.UpdateAnswers(transform)

private fun splitPanelDebugCommand(rawCommand: String): Pair<String, String> {
    val trimmed = rawCommand.trim()
    val separator = listOf(
        trimmed.indexOf(':'),
        trimmed.indexOf('=')
    ).filter { it >= 0 }.minOrNull() ?: -1
    val command = if (separator >= 0) trimmed.substring(0, separator) else trimmed
    val argument = if (separator >= 0 && separator < trimmed.lastIndex) {
        trimmed.substring(separator + 1)
    } else {
        ""
    }

    return normalizeDebugCommand(command) to argument.trim()
}

private fun normalizeDebugCommand(value: String): String =
    value.trim().lowercase().replace('-', '_').replace(' ', '_')

private fun normalizeDebugLanguage(value: String): String =
    when (normalizeDebugCommand(value)) {
        "ja", "jp", "ja_jp", "ja-jp", "japanese" -> LocaleJapanese
        else -> LocaleEnglish
    }

private fun normalizeYesNo(value: String, fallback: String): String =
    when (normalizeDebugCommand(value)) {
        "yes", "y", "true", "1" -> AnswerYes
        "no", "n", "false", "0" -> AnswerNo
        else -> fallback
    }

private fun debugPercent(value: String, fallback: Float): Float =
    value.toFloatOrNull()?.coerceIn(0f, 100f) ?: fallback

private fun debugInt(value: String, fallback: Int, min: Int, max: Int): Int =
    value.toIntOrNull()?.coerceIn(min, max) ?: fallback

private fun applyIpqDebugCommand(
    answers: QuestionnaireAnswerState,
    argument: String
): QuestionnaireAnswerState {
    val parts = argument.split(':', '=').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) {
        return answers
    }

    val itemId = parts[0]
    val value = debugInt(parts[1], answers.ipqAnswers[itemId] ?: 3, 0, 6)
    if (PresenceItems.none { it.id == itemId }) {
        return answers
    }

    return answers.copy(ipqAnswers = answers.ipqAnswers + (itemId to value))
}

internal class QuestionnaireAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(relativePath: String) {
        stop()
        try {
            val descriptor = context.assets.openFd("BRBStudyAudio/localized/$relativePath")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                setOnCompletionListener {
                    it.release()
                    if (mediaPlayer === it) {
                        mediaPlayer = null
                    }
                }
                prepare()
                start()
            }
            descriptor.close()
        } catch (_: Exception) {
            stop()
        }
    }

    fun release() {
        stop()
    }

    private fun stop() {
        mediaPlayer?.run {
            runCatching {
                if (isPlaying) {
                    stop()
                }
            }
            release()
        }
        mediaPlayer = null
    }
}

private fun audioPathFor(stage: String, language: String, conditionNumber: Int?): String? {
    val locale = if (language == LocaleJapanese) "ja_jp" else "en_us"
    val suffix = if (language == LocaleJapanese) "ja_jp" else "en_us"
    val audioId = when (stage) {
        BrbQuestionnaireContract.StagePriorExperience -> "aud_0200_prior_experience_question"
        BrbQuestionnaireContract.StagePostConditionPictographic ->
            if (conditionNumber == 2) {
                "aud_0310_redness_likert_to_vas_changeover"
            } else {
                "aud_0300_redness_vas_to_likert_changeover"
            }
        BrbQuestionnaireContract.StagePostConditionPresence ->
            if (conditionNumber == 2) {
                "aud_0330_ipq_history_part2"
            } else {
                "aud_0320_ipq_history_part1"
            }
        BrbQuestionnaireContract.StageFinalEndConfirmation -> "aud_0500_final_end_confirmation_question"
        BrbQuestionnaireContract.StageFinalExtraPressesPrompt -> "aud_0600_final_extra_presses_prompt"
        else -> return null
    }
    return "$locale/${audioId}__$suffix.mp3"
}

private fun priorExperienceFeedbackAudio(language: String, answer: String): String? {
    val locale = if (language == LocaleJapanese) "ja_jp" else "en_us"
    val suffix = if (language == LocaleJapanese) "ja_jp" else "en_us"
    val audioId = when (answer) {
        AnswerYes -> "aud_0210_prior_experience_yes_feedback"
        AnswerNo -> "aud_0220_prior_experience_no_feedback"
        else -> return null
    }
    return "$locale/${audioId}__$suffix.mp3"
}

private fun finalTenFeedbackAudio(language: String): String {
    val locale = if (language == LocaleJapanese) "ja_jp" else "en_us"
    val suffix = if (language == LocaleJapanese) "ja_jp" else "en_us"
    return "$locale/aud_0510_final_end_confirmation_10_feedback__$suffix.mp3"
}

private const val LocaleEnglish = "en-US"
private const val LocaleJapanese = "ja-JP"
private const val AnswerYes = "yes"
private const val AnswerNo = "no"
private const val AnswerNotAnswered = "not_answered"

