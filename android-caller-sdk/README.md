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
    screenSequence = listOf("demographics")
)
```
