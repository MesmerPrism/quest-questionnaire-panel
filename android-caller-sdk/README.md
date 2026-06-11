# Android Caller SDK

Planned small Kotlin/AAR helper for native caller apps.

Initial API shape:

```kotlin
val request = QuestionnaireLaunchRequest(
    sessionId = "...",
    studyId = "brb",
    schemaId = "brb-questionnaire-v1",
    openStage = "demographics",
    conditionNumber = null,
    screenSequence = listOf("demographics")
)

QuestQuestionnaireLauncher.launch(activity, request)
```

The SDK should own:

- request id and nonce generation;
- private result file creation;
- FileProvider URI creation;
- explicit panel launch Intent;
- immutable one-shot broadcast PendingIntent;
- result JSON validation.

Do not implement this until the native caller tester has proven the handwritten
contract on Quest.
