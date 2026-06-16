package io.github.mesmerprism.questquestionnaire.maiaspatial

object MaiaSpatialQuestionnaireContract {
    const val QuestionnaireId = "maia2-spatial-frame-questionnaire-v1"
    const val QuestionnaireVersion = 1
    const val ProgramId = "maia2-spatial-frame-reference-three-block"
    const val ContentVersion = "1.0.0"
    const val Maia2ScoreVersion = "maia2-2018-standard"
    const val SpatialFramePictographAssetSha256 =
        "4D5AA60F1475DC2E75BB859CC6890D7E3A42F7EF41C5BDE2C4CF22B30693E5C3"

    const val StageLanguageSelection = "maia_spatial:language_selection"
    const val StageDemographics = "maia_spatial:demographics"
    const val StageMaia2 = "maia_spatial:maia2"
    const val StageSpatialFrameReference1 = "maia_spatial:spatial_frame_reference_1"
    const val StageSpatialFrameReference2 = "maia_spatial:spatial_frame_reference_2"
    const val StageCompletion = "maia_spatial:completion"

    const val BlockOneId = "block_1_setup_maia2"
    const val BlockTwoId = "block_2_spatial_frame_reference"
    const val BlockThreeId = "block_3_spatial_frame_reference"

    val SupportedStages = setOf(
        StageLanguageSelection,
        StageDemographics,
        StageMaia2,
        StageSpatialFrameReference1,
        StageSpatialFrameReference2,
        StageCompletion
    )

    val BlockOneSetupMaia2Sequence = listOf(
        StageLanguageSelection,
        StageDemographics,
        StageMaia2
    )

    val BlockTwoSpatialFrameReferenceSequence = listOf(StageSpatialFrameReference1)
    val BlockThreeSpatialFrameReferenceSequence = listOf(StageSpatialFrameReference2)

    val FullProgramSequence = BlockOneSetupMaia2Sequence +
        BlockTwoSpatialFrameReferenceSequence +
        BlockThreeSpatialFrameReferenceSequence +
        StageCompletion

    val SupportedLanguages = setOf("en", "de")
    val GenderChoices = setOf("female", "male", "non_binary_or_diverse", "prefer_not_to_say")
    val HandednessChoices = setOf("left", "right", "ambidextrous", "prefer_not_to_say")
    val SpatialFrameChoices = setOf("A", "B", "C", "D", "E", "F", "G", "H")

    val ReverseScoredMaia2Items = setOf(5, 6, 7, 8, 9, 10, 11, 12, 15)

    val Maia2Subscales = listOf(
        Maia2Subscale("noticing", listOf(1, 2, 3, 4)),
        Maia2Subscale("not_distracting", listOf(5, 6, 7, 8, 9, 10)),
        Maia2Subscale("not_worrying", listOf(11, 12, 13, 14, 15)),
        Maia2Subscale("attention_regulation", listOf(16, 17, 18, 19, 20, 21, 22)),
        Maia2Subscale("emotional_awareness", listOf(23, 24, 25, 26, 27)),
        Maia2Subscale("self_regulation", listOf(28, 29, 30, 31)),
        Maia2Subscale("body_listening", listOf(32, 33, 34)),
        Maia2Subscale("trusting", listOf(35, 36, 37))
    )
}

data class Maia2Subscale(
    val id: String,
    val itemIds: List<Int>
)

data class Maia2ScoreResult(
    val scoredItemValues: Map<Int, Int>,
    val subscaleMeans: Map<String, Double>
)

fun scoreMaia2(rawItemScores: Map<Int, Int>): Maia2ScoreResult {
    val scoredValues = (1..37).associateWith { itemId ->
        val raw = rawItemScores[itemId]
            ?: throw IllegalArgumentException("missing_maia2_item_$itemId")
        require(raw in 0..5) { "invalid_maia2_item_$itemId" }
        if (itemId in MaiaSpatialQuestionnaireContract.ReverseScoredMaia2Items) {
            5 - raw
        } else {
            raw
        }
    }

    val subscaleMeans = MaiaSpatialQuestionnaireContract.Maia2Subscales.associate { subscale ->
        val mean = subscale.itemIds.map { requireNotNull(scoredValues[it]) }.average()
        subscale.id to mean
    }

    return Maia2ScoreResult(
        scoredItemValues = scoredValues,
        subscaleMeans = subscaleMeans
    )
}
