package io.github.mesmerprism.questquestionnaire.panel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.github.mesmerprism.questquestionnaire.brb.BrbQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.maiaspatial.MaiaSpatialQuestionnaireContract
import io.github.mesmerprism.questquestionnaire.panel.brb.BrbQuestionnaireViewModel
import io.github.mesmerprism.questquestionnaire.panel.generic.GenericQuestionnaireContract
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test

class PanelRenderSequenceTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1350,
            screenHeight = 900,
            xdpi = 200,
            ydpi = 200,
            orientation = ScreenOrientation.LANDSCAPE,
            density = Density.create(200),
            softButtons = false
        ),
        showSystemUi = false,
        useDeviceResolution = true,
        theme = "android:style/Theme.Material.Light.NoActionBar"
    )

    private val rendererRegistry = DefaultQuestionnaireRendererRegistry.create()
    private val draftRoot: File = Files.createTempDirectory("panel-render-drafts").toFile()

    @After
    fun deleteDrafts() {
        draftRoot.deleteRecursively()
    }

    @Test
    fun brbInitialLanguage() {
        snapshot(
            name = "brb-01-language-select",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StageLanguageSelect,
                screenSequence = BrbQuestionnaireContract.InitialStudySequence
            )
        )
    }

    @Test
    fun brbInitialDemographics() {
        snapshot(
            name = "brb-02-demographics",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StageDemographics,
                screenSequence = BrbQuestionnaireContract.InitialStudySequence
            )
        )
    }

    @Test
    fun brbInitialPriorExperience() {
        snapshot(
            name = "brb-03-prior-experience",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StagePriorExperience,
                screenSequence = BrbQuestionnaireContract.InitialStudySequence
            )
        )
    }

    @Test
    fun brbPostConditionPictographic() {
        snapshot(
            name = "brb-04-post-pictographic",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StagePostConditionPictographic,
                screenSequence = BrbQuestionnaireContract.ConditionOnePostSequence,
                conditionNumber = 1
            )
        )
    }

    @Test
    fun brbPostConditionPresence() {
        snapshot(
            name = "brb-05-post-presence",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StagePostConditionPresence,
                screenSequence = BrbQuestionnaireContract.ConditionOnePostSequence,
                conditionNumber = 1
            )
        )
    }

    @Test
    fun brbPostConditionLostOpportunity() {
        snapshot(
            name = "brb-06-lost-opportunity",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StagePostConditionLostOpportunity,
                screenSequence = BrbQuestionnaireContract.ConditionOnePostSequence,
                conditionNumber = 1
            )
        )
    }

    @Test
    fun brbFinalConfirmation() {
        snapshot(
            name = "brb-07-final-confirmation",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StageFinalEndConfirmation,
                screenSequence = BrbQuestionnaireContract.FinalSequence
            )
        )
    }

    @Test
    fun brbFinalExtraPressesPrompt() {
        snapshot(
            name = "brb-08-extra-presses-prompt",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StageFinalExtraPressesPrompt,
                screenSequence = BrbQuestionnaireContract.FinalSequence
            )
        )
    }

    @Test
    fun brbFinalExportSummary() {
        snapshot(
            name = "brb-09-export-summary",
            request = brbRequest(
                openStage = BrbQuestionnaireContract.StageCompleteExportSummary,
                screenSequence = BrbQuestionnaireContract.FinalSequence
            )
        )
    }

    @Test
    fun genericIntro() {
        snapshot(
            name = "generic-01-intro",
            request = genericRequest(GenericQuestionnaireContract.StageIntro)
        )
    }

    @Test
    fun genericRating() {
        snapshot(
            name = "generic-02-rating",
            request = genericRequest(GenericQuestionnaireContract.StageRating)
        )
    }

    @Test
    fun genericComment() {
        snapshot(
            name = "generic-03-comment",
            request = genericRequest(GenericQuestionnaireContract.StageComment)
        )
    }

    @Test
    fun genericComplete() {
        snapshot(
            name = "generic-04-complete",
            request = genericRequest(GenericQuestionnaireContract.StageComplete)
        )
    }

    @Test
    fun maiaSpatialLanguage() {
        snapshot(
            name = "maia-spatial-01-language",
            request = maiaSpatialRequest(
                openStage = MaiaSpatialQuestionnaireContract.StageLanguageSelection,
                screenSequence = MaiaSpatialQuestionnaireContract.BlockOneSetupMaia2Sequence
            )
        )
    }

    @Test
    fun maiaSpatialPictographBlockTwo() {
        snapshot(
            name = "maia-spatial-02-pictograph-block-two",
            request = maiaSpatialRequest(
                openStage = MaiaSpatialQuestionnaireContract.StageSpatialFrameReference1,
                screenSequence = MaiaSpatialQuestionnaireContract.BlockTwoSpatialFrameReferenceSequence
            )
        )
    }

    private fun snapshot(name: String, request: QuestionnaireRequest) {
        val renderer = requireNotNull(rendererRegistry.rendererFor(request)) {
            "No renderer for ${request.schemaId} stage ${request.openStage}"
        }
        val draftStore = QuestionnaireDraftStore(File(draftRoot, name))
        val viewModelStoreOwner = RenderViewModelStoreOwner()

        try {
            paparazzi.snapshot(name = name) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    MaterialTheme {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            renderer.Render(
                                request = request,
                                config = QuestionnaireRendererConfig(
                                    autoSubmit = false,
                                    debugCommandScript = null,
                                    debugCommandIntervalMs = 0,
                                    draftStore = draftStore
                                ),
                                callbacks = QuestionnaireRendererCallbacks(
                                    onCompleted = {},
                                    onCancelled = {},
                                    onError = {}
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            viewModelStoreOwner.viewModelStore.clear()
        }
    }

    private class RenderViewModelStoreOwner :
        ViewModelStoreOwner,
        HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelCreationExtras: CreationExtras = CreationExtras.Empty
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
            RenderViewModelFactory
    }

    private object RenderViewModelFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras
        ): T {
            if (modelClass.isAssignableFrom(BrbQuestionnaireViewModel::class.java)) {
                return BrbQuestionnaireViewModel(SavedStateHandle()) as T
            }
            return modelClass.getDeclaredConstructor().newInstance()
        }
    }

    private fun brbRequest(
        openStage: String,
        screenSequence: List<String>,
        conditionNumber: Int? = null
    ): QuestionnaireRequest =
        request(
            schemaId = BrbQuestionnaireContract.QuestionnaireId,
            openStage = openStage,
            screenSequence = screenSequence,
            conditionNumber = conditionNumber
        )

    private fun genericRequest(openStage: String): QuestionnaireRequest =
        request(
            schemaId = GenericQuestionnaireContract.QuestionnaireId,
            openStage = openStage,
            screenSequence = GenericQuestionnaireContract.DemoSequence,
            conditionNumber = null
        )

    private fun maiaSpatialRequest(
        openStage: String,
        screenSequence: List<String>
    ): QuestionnaireRequest =
        request(
            schemaId = MaiaSpatialQuestionnaireContract.QuestionnaireId,
            openStage = openStage,
            screenSequence = screenSequence,
            conditionNumber = null
        )

    private fun request(
        schemaId: String,
        openStage: String,
        screenSequence: List<String>,
        conditionNumber: Int?
    ): QuestionnaireRequest =
        QuestionnaireRequest(
            protocolVersion = QuestionnaireContract.ProtocolVersion,
            sessionId = "render-session",
            requestId = "render-$openStage",
            nonce = "rendernonce000001",
            studyId = if (schemaId == BrbQuestionnaireContract.QuestionnaireId) {
                "brb"
            } else {
                "render-demo"
            },
            schemaId = schemaId,
            openStage = openStage,
            conditionNumber = conditionNumber,
            screenSequence = screenSequence,
            questionnaireState = null
        )
}
