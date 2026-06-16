package io.github.mesmerprism.questquestionnaire.panel.maiaspatial

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mesmerprism.questquestionnaire.maiaspatial.MaiaSpatialQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.maiaspatial.scoreMaia2
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRenderer
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererCallbacks
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererConfig
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererFactory
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererResult
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRendererTerminal
import io.github.mesmerprism.questquestionnaire.panel.QuestionnaireRequest
import java.time.Instant
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

object MaiaSpatialQuestionnaireRendererFactory : QuestionnaireRendererFactory {
    override fun canRender(request: QuestionnaireRequest): Boolean =
        request.schemaId == MaiaSpatialQuestionnaireContract.QuestionnaireId &&
            request.openStage in MaiaSpatialQuestionnaireContract.SupportedStages &&
            request.screenSequence.isNotEmpty() &&
            request.screenSequence.all { it in MaiaSpatialQuestionnaireContract.SupportedStages } &&
            request.openStage in request.screenSequence

    override fun create(request: QuestionnaireRequest): QuestionnaireRenderer =
        MaiaSpatialQuestionnaireRenderer
}

private object MaiaSpatialQuestionnaireRenderer : QuestionnaireRenderer {
    @Composable
    override fun Render(
        request: QuestionnaireRequest,
        config: QuestionnaireRendererConfig,
        callbacks: QuestionnaireRendererCallbacks
    ) {
        MaiaSpatialQuestionnairePanel(
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
private fun MaiaSpatialQuestionnairePanel(
    request: QuestionnaireRequest,
    autoSubmit: Boolean,
    debugCommandScript: String?,
    debugCommandIntervalMs: Int,
    onSubmit: (JSONObject, String, Int) -> Unit,
    onCancel: (String, Int) -> Unit
) {
    val content = rememberMaiaSpatialContent()
    val sequence = request.screenSequence
    val saveKey = "${request.requestId}:${request.nonce}"
    var currentIndex by rememberSaveable(saveKey) {
        mutableStateOf(sequence.indexOf(request.openStage).coerceAtLeast(0))
    }
    var languageCode by rememberSaveable(saveKey) {
        mutableStateOf(request.initialLanguageCode())
    }
    var participantName by rememberSaveable(saveKey) { mutableStateOf("") }
    var ageText by rememberSaveable(saveKey) { mutableStateOf("") }
    var gender by rememberSaveable(saveKey) { mutableStateOf("") }
    var handedness by rememberSaveable(saveKey) { mutableStateOf("") }
    var consent by rememberSaveable(saveKey) { mutableStateOf(false) }
    var signature by rememberSaveable(saveKey) { mutableStateOf("") }
    var maiaScores by rememberSaveable(saveKey) { mutableStateOf(List(37) { MissingScore }) }
    var spatialChoice1 by rememberSaveable(saveKey) { mutableStateOf("") }
    var spatialChoice2 by rememberSaveable(saveKey) { mutableStateOf("") }
    var autoSubmitted by rememberSaveable(saveKey) { mutableStateOf(false) }

    fun activeStage(): String = sequence[currentIndex]

    fun canProceed(stage: String): Boolean =
        when (stage) {
            MaiaSpatialQuestionnaireContract.StageLanguageSelection ->
                languageCode in MaiaSpatialQuestionnaireContract.SupportedLanguages
            MaiaSpatialQuestionnaireContract.StageDemographics ->
                participantName.isNotBlank() &&
                    ageText.toIntOrNull() in 0..120 &&
                    gender in MaiaSpatialQuestionnaireContract.GenderChoices &&
                    handedness in MaiaSpatialQuestionnaireContract.HandednessChoices &&
                    consent &&
                    signature.isNotBlank()
            MaiaSpatialQuestionnaireContract.StageMaia2 ->
                maiaScores.all { it in 0..5 }
            MaiaSpatialQuestionnaireContract.StageSpatialFrameReference1 ->
                spatialChoice1 in MaiaSpatialQuestionnaireContract.SpatialFrameChoices
            MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2 ->
                spatialChoice2 in MaiaSpatialQuestionnaireContract.SpatialFrameChoices
            MaiaSpatialQuestionnaireContract.StageCompletion -> true
            else -> false
        }

    fun answerState(completedStage: String): JSONObject =
        answersJson(
            request = request,
            completedStage = completedStage,
            languageCode = languageCode,
            participantName = participantName,
            age = ageText.toIntOrNull(),
            gender = gender,
            handedness = handedness,
            consent = consent,
            signature = signature,
            maiaScores = maiaScores,
            spatialChoice1 = spatialChoice1,
            spatialChoice2 = spatialChoice2,
            content = content
        )

    fun applyDebugDefaults() {
        languageCode = languageCode.takeIf {
            it in MaiaSpatialQuestionnaireContract.SupportedLanguages
        } ?: "en"
        participantName = "Debug Participant"
        ageText = "30"
        gender = "prefer_not_to_say"
        handedness = "right"
        consent = true
        signature = "Debug Participant"
        maiaScores = List(37) { 3 }
        spatialChoice1 = "D"
        spatialChoice2 = "E"
    }

    fun goBack() {
        if (currentIndex > 0) {
            currentIndex -= 1
        }
    }

    fun submitCurrent() {
        val stage = activeStage()
        if (canProceed(stage)) {
            onSubmit(answerState(stage), stage, currentIndex)
        }
    }

    fun goNext() {
        val stage = activeStage()
        if (!canProceed(stage)) {
            return
        }
        if (currentIndex == sequence.lastIndex) {
            onSubmit(answerState(stage), stage, currentIndex)
        } else {
            currentIndex += 1
        }
    }

    LaunchedEffect(autoSubmit, request.requestId) {
        if (autoSubmit && !autoSubmitted) {
            autoSubmitted = true
            applyDebugDefaults()
            onSubmit(answerState(sequence.last()), sequence.last(), sequence.lastIndex)
        }
    }

    LaunchedEffect(debugCommandScript, request.requestId) {
        val commands = debugCommandScript.toMaiaSpatialDebugCommands()
        if (commands.isEmpty()) {
            return@LaunchedEffect
        }

        val delayMs = debugCommandIntervalMs.coerceIn(0, 10_000).toLong()
        commands.forEach { command ->
            when (val action = parseDebugCommand(command)) {
                DebugAction.Back -> goBack()
                DebugAction.Cancel -> onCancel(activeStage(), currentIndex)
                DebugAction.Defaults -> applyDebugDefaults()
                DebugAction.Next -> goNext()
                DebugAction.Submit -> submitCurrent()
                is DebugAction.Update -> {
                    when (action.key) {
                        "language" -> languageCode = normalizeLanguage(action.value, languageCode)
                        "name" -> participantName = action.value.take(80)
                        "age" -> ageText = action.value.filter(Char::isDigit).take(3)
                        "gender" -> gender = action.value.takeIf {
                            it in MaiaSpatialQuestionnaireContract.GenderChoices
                        } ?: gender
                        "handedness" -> handedness = action.value.takeIf {
                            it in MaiaSpatialQuestionnaireContract.HandednessChoices
                        } ?: handedness
                        "consent" -> consent = true
                        "signature" -> signature = action.value.take(80)
                        "maia_all" -> maiaScores = List(37) {
                            action.value.toIntOrNull()?.coerceIn(0, 5) ?: 3
                        }
                        "choice" -> {
                            val choice = action.value.uppercase()
                            if (choice in MaiaSpatialQuestionnaireContract.SpatialFrameChoices) {
                                if (activeStage() ==
                                    MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2
                                ) {
                                    spatialChoice2 = choice
                                } else {
                                    spatialChoice1 = choice
                                }
                            }
                        }
                    }
                }
                null -> Unit
            }
            if (delayMs > 0L) {
                delay(delayMs)
            }
        }
    }

    val currentStage = activeStage()
    val strings = content.stringsFor(languageCode)
    MaiaSpatialScaffold(
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
            MaiaSpatialQuestionnaireContract.StageLanguageSelection ->
                LanguageSelectionScreen(
                    languageCode = languageCode,
                    strings = strings,
                    onLanguageChange = { languageCode = it }
                )
            MaiaSpatialQuestionnaireContract.StageDemographics ->
                DemographicsScreen(
                    strings = strings,
                    participantName = participantName,
                    ageText = ageText,
                    gender = gender,
                    handedness = handedness,
                    consent = consent,
                    signature = signature,
                    onParticipantNameChange = { participantName = it.take(80) },
                    onAgeTextChange = { ageText = it.filter(Char::isDigit).take(3) },
                    onGenderChange = { gender = it },
                    onHandednessChange = { handedness = it },
                    onConsentChange = { consent = it },
                    onSignatureChange = { signature = it.take(80) }
                )
            MaiaSpatialQuestionnaireContract.StageMaia2 ->
                Maia2Screen(
                    strings = strings,
                    items = content.maiaItemsFor(languageCode),
                    scores = maiaScores,
                    onScoreChange = { itemId, score ->
                        maiaScores = maiaScores.toMutableList().also {
                            it[itemId - 1] = score
                        }
                    }
                )
            MaiaSpatialQuestionnaireContract.StageSpatialFrameReference1 ->
                SpatialFrameReferenceScreen(
                    strings = strings,
                    administrationIndex = 1,
                    selectedChoice = spatialChoice1,
                    onChoiceChange = { spatialChoice1 = it }
                )
            MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2 ->
                SpatialFrameReferenceScreen(
                    strings = strings,
                    administrationIndex = 2,
                    selectedChoice = spatialChoice2,
                    onChoiceChange = { spatialChoice2 = it }
                )
            MaiaSpatialQuestionnaireContract.StageCompletion ->
                CompletionScreen(strings)
            else ->
                Text("Unsupported stage: $currentStage")
        }
    }
}

@Composable
private fun MaiaSpatialScaffold(
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
        Text("MAIA-2 Spatial Frame", style = MaterialTheme.typography.headlineMedium)
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
private fun LanguageSelectionScreen(
    languageCode: String,
    strings: MaiaSpatialStrings,
    onLanguageChange: (String) -> Unit
) {
    Text(strings.languageTitle, style = MaterialTheme.typography.headlineSmall)
    Text(strings.languagePrompt)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChip(
            label = strings.englishLanguage,
            selected = languageCode == "en",
            onClick = { onLanguageChange("en") }
        )
        ChoiceChip(
            label = strings.germanLanguage,
            selected = languageCode == "de",
            onClick = { onLanguageChange("de") }
        )
    }
}

@Composable
private fun DemographicsScreen(
    strings: MaiaSpatialStrings,
    participantName: String,
    ageText: String,
    gender: String,
    handedness: String,
    consent: Boolean,
    signature: String,
    onParticipantNameChange: (String) -> Unit,
    onAgeTextChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onHandednessChange: (String) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onSignatureChange: (String) -> Unit
) {
    Text(strings.demographicsTitle, style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = participantName,
        onValueChange = onParticipantNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(strings.nameLabel) },
        placeholder = { Text(strings.namePlaceholder) },
        singleLine = true
    )
    OutlinedTextField(
        value = ageText,
        onValueChange = onAgeTextChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(strings.ageLabel) },
        placeholder = { Text(strings.agePlaceholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Text(strings.genderLabel, style = MaterialTheme.typography.labelLarge)
    ChoiceRow(
        choices = strings.genderChoices,
        selected = gender,
        onSelected = onGenderChange
    )
    Text(strings.handednessLabel, style = MaterialTheme.typography.labelLarge)
    ChoiceRow(
        choices = strings.handednessChoices,
        selected = handedness,
        onSelected = onHandednessChange
    )
    ChoiceChip(
        label = strings.consentText,
        selected = consent,
        onClick = { onConsentChange(!consent) }
    )
    OutlinedTextField(
        value = signature,
        onValueChange = onSignatureChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(strings.signatureLabel) },
        placeholder = { Text(strings.signaturePrompt) },
        singleLine = true
    )
}

@Composable
private fun Maia2Screen(
    strings: MaiaSpatialStrings,
    items: List<Maia2Item>,
    scores: List<Int>,
    onScoreChange: (Int, Int) -> Unit
) {
    Text(strings.maiaTitle, style = MaterialTheme.typography.headlineSmall)
    Text(strings.maiaInstructions)
    Text("${strings.maiaLeftAnchor} 0 / 5 ${strings.maiaRightAnchor}")
    items.forEach { item ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${item.id}. ${item.text}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..5).forEach { value ->
                    ChoiceChip(
                        label = value.toString(),
                        selected = scores.getOrNull(item.id - 1) == value,
                        onClick = { onScoreChange(item.id, value) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SpatialFrameReferenceScreen(
    strings: MaiaSpatialStrings,
    administrationIndex: Int,
    selectedChoice: String,
    onChoiceChange: (String) -> Unit
) {
    Text(strings.spatialTitle, style = MaterialTheme.typography.headlineSmall)
    Text(strings.spatialInstructions)
    Text("Administration $administrationIndex", style = MaterialTheme.typography.labelLarge)

    val image = rememberAssetImage(PictographAssetPath)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = strings.spatialTitle,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            contentScale = ContentScale.Fit
        )
    }

    Text(strings.spatialChoicePrompt)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MaiaSpatialQuestionnaireContract.SpatialFrameChoices.forEach { choice ->
            ChoiceChip(
                label = choice,
                selected = selectedChoice == choice,
                onClick = { onChoiceChange(choice) }
            )
        }
    }
}

@Composable
private fun CompletionScreen(strings: MaiaSpatialStrings) {
    Text(strings.completionTitle, style = MaterialTheme.typography.headlineSmall)
    Text(strings.completionMessage)
}

@Composable
private fun ChoiceRow(
    choices: Map<String, String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (id, label) ->
            ChoiceChip(
                label = label,
                selected = selected == id,
                onClick = { onSelected(id) }
            )
        }
    }
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

@Composable
private fun rememberMaiaSpatialContent(): MaiaSpatialContent {
    val context = LocalContext.current
    return remember(context) {
        MaiaSpatialContent.load(context.applicationContext)
    }
}

@Composable
private fun rememberAssetImage(path: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) {
        runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it).asImageBitmap() }
        }.getOrNull()
    }
}

private fun answersJson(
    request: QuestionnaireRequest,
    completedStage: String,
    languageCode: String,
    participantName: String,
    age: Int?,
    gender: String,
    handedness: String,
    consent: Boolean,
    signature: String,
    maiaScores: List<Int>,
    spatialChoice1: String,
    spatialChoice2: String,
    content: MaiaSpatialContent
): JSONObject {
    val sequence = request.screenSequence
    return JSONObject()
        .put("open_stage", request.openStage)
        .put("completed_stage", completedStage)
        .put("screen_sequence", JSONArray(sequence))
        .put("program_id", MaiaSpatialQuestionnaireContract.ProgramId)
        .put("content_version", MaiaSpatialQuestionnaireContract.ContentVersion)
        .put("language", content.languageJson(languageCode))
        .apply {
            if (sequence.any { it in BlockOneStages }) {
                put(
                    "demographics",
                    JSONObject()
                        .put("name", participantName)
                        .put("age", age ?: JSONObject.NULL)
                        .put("gender", gender)
                        .put("handedness", handedness)
                        .put("consent", consent)
                        .put("signature", signature)
                )
                put("maia2", maia2Json(maiaScores, languageCode))
            }
            if (MaiaSpatialQuestionnaireContract.StageSpatialFrameReference1 in sequence) {
                put(
                    "spatial_frame_reference_administration_1",
                    spatialFrameJson(
                        blockId = MaiaSpatialQuestionnaireContract.BlockTwoId,
                        administrationIndex = 1,
                        choice = spatialChoice1
                    )
                )
            }
            if (MaiaSpatialQuestionnaireContract.StageSpatialFrameReference2 in sequence) {
                put(
                    "spatial_frame_reference_administration_2",
                    spatialFrameJson(
                        blockId = MaiaSpatialQuestionnaireContract.BlockThreeId,
                        administrationIndex = 2,
                        choice = spatialChoice2
                    )
                )
            }
        }
}

private fun maia2Json(scores: List<Int>, languageCode: String): JSONObject {
    val rawScores = (1..37).associateWith { itemId ->
        scores.getOrNull(itemId - 1)?.coerceIn(0, 5) ?: 0
    }
    val scoreResult = scoreMaia2(rawScores)
    return JSONObject()
        .put("instrument_id", "maia2")
        .put("language", languageCode)
        .put("score_version", MaiaSpatialQuestionnaireContract.Maia2ScoreVersion)
        .put("raw_item_scores", JSONObject(rawScores.mapKeys { it.key.toString() }))
        .put(
            "scored_item_values",
            JSONObject(scoreResult.scoredItemValues.mapKeys { it.key.toString() })
        )
        .put("subscale_means", JSONObject(scoreResult.subscaleMeans))
        .put("completed_at", Instant.now().toString())
}

private fun spatialFrameJson(
    blockId: String,
    administrationIndex: Int,
    choice: String
): JSONObject =
    JSONObject()
        .put("instrument_id", "spatial_frame_reference_pictograph")
        .put("block_id", blockId)
        .put("administration_index", administrationIndex)
        .put("choice", choice)
        .put("asset_sha256", MaiaSpatialQuestionnaireContract.SpatialFramePictographAssetSha256)
        .put("response_timestamp", Instant.now().toString())

private data class MaiaSpatialContent(
    val localizedStrings: Map<String, MaiaSpatialStrings>,
    val localizedMaiaItems: Map<String, List<Maia2Item>>
) {
    fun stringsFor(languageCode: String): MaiaSpatialStrings =
        localizedStrings[languageCode] ?: requireNotNull(localizedStrings["en"])

    fun maiaItemsFor(languageCode: String): List<Maia2Item> =
        localizedMaiaItems[languageCode] ?: requireNotNull(localizedMaiaItems["en"])

    fun languageJson(languageCode: String): JSONObject {
        val strings = stringsFor(languageCode)
        return JSONObject()
            .put("code", strings.languageCode)
            .put("label", strings.languageLabel)
    }

    companion object {
        fun load(context: Context): MaiaSpatialContent =
            MaiaSpatialContent(
                localizedStrings = mapOf(
                    "en" to MaiaSpatialStrings.fromJson(context.assetJson("content/i18n/en.json")),
                    "de" to MaiaSpatialStrings.fromJson(context.assetJson("content/i18n/de.json"))
                ),
                localizedMaiaItems = mapOf(
                    "en" to maiaItemsFromJson(context.assetJson("content/maia2/en.json")),
                    "de" to maiaItemsFromJson(context.assetJson("content/maia2/de.json"))
                )
            )
    }
}

private data class MaiaSpatialStrings(
    val languageCode: String,
    val languageLabel: String,
    val languageTitle: String,
    val languagePrompt: String,
    val englishLanguage: String,
    val germanLanguage: String,
    val demographicsTitle: String,
    val nameLabel: String,
    val namePlaceholder: String,
    val ageLabel: String,
    val agePlaceholder: String,
    val genderLabel: String,
    val handednessLabel: String,
    val consentText: String,
    val signatureLabel: String,
    val signaturePrompt: String,
    val maiaTitle: String,
    val maiaInstructions: String,
    val maiaLeftAnchor: String,
    val maiaRightAnchor: String,
    val spatialTitle: String,
    val spatialInstructions: String,
    val spatialChoicePrompt: String,
    val completionTitle: String,
    val completionMessage: String,
    val genderChoices: Map<String, String>,
    val handednessChoices: Map<String, String>
) {
    companion object {
        fun fromJson(json: JSONObject): MaiaSpatialStrings {
            val screens = json.getJSONObject("screens")
            val language = json.getJSONObject("language")
            val languageSelection = screens.getJSONObject("languageSelection")
            val demographics = screens.getJSONObject("demographics")
            val maia = screens.getJSONObject("maia2")
            val spatial = screens.getJSONObject("spatialFrameReference")
            val completion = screens.getJSONObject("completion")
            val choices = json.getJSONObject("choices")
            return MaiaSpatialStrings(
                languageCode = language.getString("code"),
                languageLabel = language.getString("label"),
                languageTitle = languageSelection.getString("title"),
                languagePrompt = languageSelection.getString("prompt"),
                englishLanguage = languageSelection.getString("english"),
                germanLanguage = languageSelection.getString("german"),
                demographicsTitle = demographics.getString("title"),
                nameLabel = demographics.getString("nameLabel"),
                namePlaceholder = demographics.getString("namePlaceholder"),
                ageLabel = demographics.getString("ageLabel"),
                agePlaceholder = demographics.getString("agePlaceholder"),
                genderLabel = demographics.getString("genderLabel"),
                handednessLabel = demographics.getString("handednessLabel"),
                consentText = demographics.getString("consentText"),
                signatureLabel = demographics.getString("signatureLabel"),
                signaturePrompt = demographics.getString("signaturePrompt"),
                maiaTitle = maia.getString("title"),
                maiaInstructions = maia.getString("instructions"),
                maiaLeftAnchor = maia.getString("leftAnchor"),
                maiaRightAnchor = maia.getString("rightAnchor"),
                spatialTitle = spatial.getString("title"),
                spatialInstructions = spatial.getString("instructions"),
                spatialChoicePrompt = spatial.getString("choicePrompt"),
                completionTitle = completion.getString("title"),
                completionMessage = completion.getString("message"),
                genderChoices = choices.getJSONObject("gender").stringMap(),
                handednessChoices = choices.getJSONObject("handedness").stringMap()
            )
        }
    }
}

private data class Maia2Item(
    val id: Int,
    val text: String
)

private fun maiaItemsFromJson(json: JSONObject): List<Maia2Item> {
    val items = json.getJSONArray("items")
    return (0 until items.length()).map { index ->
        val item = items.getJSONObject(index)
        Maia2Item(
            id = item.getInt("id"),
            text = item.getString("text")
        )
    }
}

private fun Context.assetJson(relativePath: String): JSONObject =
    assets.open("$AssetRoot/$relativePath").bufferedReader().use {
        JSONObject(it.readText())
    }

private fun JSONObject.stringMap(): Map<String, String> =
    keys().asSequence().associateWith { key -> getString(key) }

private fun QuestionnaireRequest.initialLanguageCode(): String =
    normalizeLanguage(
        questionnaireState?.optString("language_code")
            ?: questionnaireState?.optJSONObject("language")?.optString("code").orEmpty(),
        fallback = "en"
    )

private sealed class DebugAction {
    object Back : DebugAction()
    object Cancel : DebugAction()
    object Defaults : DebugAction()
    object Next : DebugAction()
    object Submit : DebugAction()
    data class Update(val key: String, val value: String) : DebugAction()
}

private fun parseDebugCommand(rawCommand: String): DebugAction? {
    val trimmed = rawCommand.trim()
    val separator = listOf(trimmed.indexOf(':'), trimmed.indexOf('='))
        .filter { it >= 0 }
        .minOrNull() ?: -1
    val command = if (separator >= 0) trimmed.substring(0, separator) else trimmed
    val argument = if (separator >= 0 && separator < trimmed.lastIndex) {
        trimmed.substring(separator + 1).trim()
    } else {
        ""
    }
    return when (val normalized = normalizeDebugCommand(command)) {
        "back" -> DebugAction.Back
        "cancel" -> DebugAction.Cancel
        "defaults", "debug_defaults" -> DebugAction.Defaults
        "next" -> DebugAction.Next
        "submit" -> DebugAction.Submit
        "consent" -> DebugAction.Update("consent", "true")
        "english" -> DebugAction.Update("language", "en")
        "german", "deutsch" -> DebugAction.Update("language", "de")
        "language",
        "name",
        "age",
        "gender",
        "handedness",
        "signature",
        "maia_all",
        "choice" -> DebugAction.Update(normalized, argument)
        else -> null
    }
}

private fun String?.toMaiaSpatialDebugCommands(): List<String> =
    if (isNullOrBlank()) {
        emptyList()
    } else {
        split(';', ',', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

private fun normalizeDebugCommand(value: String): String =
    value.trim().lowercase().replace('-', '_').replace(' ', '_')

private fun normalizeLanguage(value: String, fallback: String): String =
    when (normalizeDebugCommand(value)) {
        "de", "de_de", "german", "deutsch" -> "de"
        "en", "en_us", "english" -> "en"
        else -> fallback
    }

private val BlockOneStages = setOf(
    MaiaSpatialQuestionnaireContract.StageLanguageSelection,
    MaiaSpatialQuestionnaireContract.StageDemographics,
    MaiaSpatialQuestionnaireContract.StageMaia2
)

private const val MissingScore = -1
private const val AssetRoot = "maia_spatial_questionnaire"
private const val PictographAssetPath =
    "$AssetRoot/assets/pictographs/spatial-frame-reference-continuum.png"
