# Android Caller SDK

Small Android library for native caller apps that launch the Quest
Questionnaire Panel.

Current API shape:

```kotlin
val launcher = QuestQuestionnaireLauncher(
    QuestQuestionnaireConfig(
        resultAuthority = "${packageName}.questionnaire.results",
        callbackReceiverClass = QuestionnaireReturnReceiver::class.java
    )
)

val prepared = launcher.prepare(
    context = activity,
    request = QuestionnaireLaunchRequestSpec(
        sessionId = "...",
        studyId = "brb",
        questionnaireId = "brb-questionnaire-v1",
        openStage = "demographics",
        screenSequence = listOf("demographics")
    )
)

launcher.launch(activity, prepared)
```

For a non-BRB demo request, use:

```kotlin
QuestionnaireLaunchRequestSpec(
    sessionId = "...",
    studyId = "generic-demo",
    questionnaireId = "generic-questionnaire-v1",
    openStage = "generic:intro",
    screenSequence = listOf(
        "generic:intro",
        "generic:rating",
        "generic:comment",
        "generic:complete"
    )
)
```

Callers must declare a narrow `FileProvider` for the result directory and a
private broadcast receiver for the completion callback. A typical result path
configuration is:

```xml
<paths>
    <files-path
        name="questionnaire_results"
        path="questionnaire-results/" />
</paths>
```

The SDK owns:

- request id and nonce generation;
- private result file creation;
- `FileProvider` URI creation;
- panel install/activity preflight;
- explicit panel launch `Intent`;
- immutable one-shot broadcast `PendingIntent`;
- optional one-shot activity `PendingIntent` for foreground caller recovery;
- pending request storage;
- callback/resume/cold-start readback;
- result envelope validation through `questionnaire-contract-core`.

The caller still owns:

- study/session model;
- participant pseudonym strategy;
- retention/export policy;
- when to ask which questionnaire;
- science-specific interpretation of valid answers.

## Request Model

```kotlin
QuestionnaireLaunchRequestSpec(
    sessionId = "...",
    studyId = "brb",
    questionnaireId = "brb-questionnaire-v1",
    openStage = "demographics",
    conditionNumber = null,
    questionnaireState = null,
    screenSequence = listOf("demographics")
)
```

For split questionnaires where the caller must carry small state from one
launch to another, pass `questionnaireState`. For example, the MAIA/spatial
renderer expects Block 2 and Block 3 to receive the language selected in
Block 1:

```kotlin
QuestionnaireLaunchRequestSpec(
    sessionId = "...",
    studyId = "maia-spatial",
    questionnaireId = "maia2-spatial-frame-questionnaire-v1",
    openStage = "maia_spatial:spatial_frame_reference_1",
    screenSequence = listOf("maia_spatial:spatial_frame_reference_1"),
    questionnaireState = JSONObject().put("language_code", "en")
)
```

See `../docs/contract-versioning.md` for compatibility rules and
`../docs/research-data-safety.md` for participant-data boundaries.
