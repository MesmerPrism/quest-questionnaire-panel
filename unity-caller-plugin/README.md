# Unity Caller Plugin

This package is the Unity-facing wrapper for the Quest Questionnaire Panel. It
keeps Unity code small and routes Android launch mechanics through
`android-caller-sdk`.

## What Is Included

- `Runtime/QuestQuestionnairePanel.cs` is the Unity C# facade.
- `:unity-caller-plugin` is an Android bridge module that calls
  `QuestQuestionnaireLauncher` from `android-caller-sdk`.
- BRB and generic demo stage constants are exposed in C# for caller code.

## Unity App Requirements

The Unity app still owns its Android result provider and completion receiver:

- a narrow `FileProvider` rooted only at the private questionnaire result
  directory;
- a private broadcast receiver class for the one-shot completion callback;
- result validation after callback, resume, or cold start.

The recommended result authority is:

```text
${applicationId}.questionnaire.results
```

The callback receiver class name is app-specific, for example:

```text
org.example.xr.QuestionnaireReturnReceiver
```

## Build The Android Bridge

```powershell
.\gradlew.bat :unity-caller-plugin:assembleDebug
```

Unity projects that consume plain AARs need this bridge AAR plus the
`android-caller-sdk` and `questionnaire-contract-core` Android/JVM artifacts.
Projects with a Gradle-based Unity export can depend on the bridge module and
let Gradle resolve the SDK dependency.

## C# Launch Example

```csharp
var request = new QuestQuestionnaireRequest
{
    SessionId = sessionId,
    StudyId = "brb",
    QuestionnaireId = BrbQuestionnaire.QuestionnaireId,
    OpenStage = BrbQuestionnaire.InitialSequence[0],
    ScreenSequence = BrbQuestionnaire.InitialSequence,
    ParticipantRef = participantRef,
    CallerPackageName = Application.identifier,
    CallerAppVersion = Application.version
};

var statusJson = QuestQuestionnairePanel.Launch(
    request,
    resultAuthority: Application.identifier + ".questionnaire.results",
    callbackReceiverClassName: "org.example.xr.QuestionnaireReturnReceiver"
);
```

For a non-BRB app, use `GenericQuestionnaire.QuestionnaireId` and
`GenericQuestionnaire.DemoSequence` or your own questionnaire id and sequence.
The panel will render only questionnaire ids that it has a registered renderer
for.
