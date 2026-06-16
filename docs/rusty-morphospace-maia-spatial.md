# Rusty Morphospace MAIA/Spatial Integration

This integration keeps the panel contract unchanged: Rusty Morphospace is the
foreground Android caller, and the panel is launched through the existing
explicit `quest.questionnaire.v1` intent with a caller-owned `content://`
result URI and broadcast completion `PendingIntent`.

LSL should stay upstream of the panel. The expected product path is:

```text
experimenter LSL marker
  -> Rusty Morphospace command/session logic
  -> Android foreground caller launch
  -> Quest Questionnaire Panel
  -> caller-owned result JSON
  -> Rusty Morphospace resumes the XR app
```

Do not add LSL listeners, ADB relaunches, public shared storage, overlays, or
package killing to the panel app.

## Questionnaire Identity

Use:

```text
schema_id = maia2-spatial-frame-questionnaire-v1
study_id = maia-spatial
```

Runtime content is bundled in the panel app under:

```text
app/src/main/assets/maia_spatial_questionnaire/
```

The content comes from the public handoff repository
`GeorgeFejer91/maia2-spatial-frame-questionnaire-assets`: program manifest,
English/German labels, English/German MAIA-2 items, scoring metadata, spatial
frame pictograph metadata, and the A-H pictograph PNG.

## Block Mapping

Rusty Morphospace can map incoming LSL commands to these launch specs:

| LSL/Rusty command | `open_stage` | `screen_sequence` |
| --- | --- | --- |
| `maia_spatial.block1` | `maia_spatial:language_selection` | `maia_spatial:language_selection`, `maia_spatial:demographics`, `maia_spatial:maia2` |
| `maia_spatial.block2` | `maia_spatial:spatial_frame_reference_1` | `maia_spatial:spatial_frame_reference_1` |
| `maia_spatial.block3` | `maia_spatial:spatial_frame_reference_2` | `maia_spatial:spatial_frame_reference_2` |

Block 2 and block 3 intentionally write separate answer buckets:

```text
spatial_frame_reference_administration_1
spatial_frame_reference_administration_2
```

## Language State

Block 1 selects `en` or `de` and returns it in:

```json
"answers": {
  "language": {
    "code": "en",
    "label": "English"
  }
}
```

For block 2 and block 3, Rusty Morphospace should pass the selected language
back in the optional v1 request field:

```json
"questionnaire_state": {
  "language_code": "en"
}
```

The panel defaults to English if no language state is supplied, but study
launchers should always pass the block-1 language so the selected language is
fixed across the session.

## Android SDK Sketch

```kotlin
val launcher = QuestQuestionnaireLauncher(
    QuestQuestionnaireConfig(
        resultAuthority = "${packageName}.questionnaire.results",
        callbackReceiverClass = QuestionnaireReturnReceiver::class.java
    )
)

val request = QuestionnaireLaunchRequestSpec(
    sessionId = sessionId,
    studyId = "maia-spatial",
    questionnaireId = "maia2-spatial-frame-questionnaire-v1",
    openStage = "maia_spatial:spatial_frame_reference_1",
    screenSequence = listOf("maia_spatial:spatial_frame_reference_1"),
    questionnaireState = JSONObject().put("language_code", selectedLanguageCode),
    caller = QuestionnaireCallerMetadata(
        packageName = packageName,
        appVersion = BuildConfig.VERSION_NAME,
        engine = "native"
    )
)

val prepared = launcher.prepare(context = activity, request = request)
launcher.launch(activity, prepared)
```

Validate results with `MaiaSpatialAnswerValidator` from
`:maia-spatial-questionnaire-core`.
