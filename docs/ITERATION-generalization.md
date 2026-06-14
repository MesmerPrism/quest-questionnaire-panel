# Panel Generalization Iteration

Started: 2026-06-14

Goal: turn the Quest Questionnaire Panel from a BRB-first proof into reusable
Quest questionnaire infrastructure that native Android, Unity, and other
Quest/OpenXR-style callers can adopt without learning BRB internals.

This document is the working iteration record. Keep it updated as the build
progresses: decisions, implementation notes, test evidence, blockers, and
scope changes should be added here instead of scattered through chat.

## Core Framing

The roadmap is organized around three separations:

1. Contract/core vs. Android launch mechanics.
2. Generic panel runtime vs. BRB questionnaire implementation.
3. Result envelope validation vs. questionnaire-specific answer validation.

These separations protect the reusable protocol from app-specific details:

- `questionnaire-contract-core` should own constants, models, status handling,
  envelope parsing, envelope validation, answer-validator interfaces, and
  golden fixtures.
- `android-caller-sdk` should own Android-specific launch mechanics:
  `FileProvider` result files, explicit panel Intents, one-shot immutable
  callback `PendingIntent`s, pending request storage, readback, recovery, and
  Android wrappers around contract validation.
- `app` should host a generic Quest 2D panel runtime and register BRB as one
  questionnaire implementation, not hard-code BRB as the panel itself.

## Non-Negotiable Contract Rules

- The foreground caller launches the 2D panel with an explicit component.
- Request identity and small metadata travel in extras and request JSON.
- The caller owns the result file and grants the panel write access only to
  that `content://` URI.
- The callback is a one-shot immutable broadcast `PendingIntent` by default.
- Answers live in result JSON, never callback extras.
- Avoid public shared storage, MediaStore answer exchange, Termux file drops,
  force-stop cleanup, package killing, overlays, and product-path ADB.
- Keep raw participant data, device serials, APKs, screenshots, logcat bundles,
  signing keys, local machine paths, and private evidence artifacts out of
  committed files.

## Target Repo Shape

```text
contract/
  JSON schemas, examples, and compatibility notes

questionnaire-contract-core/
  Protocol constants
  Request/result envelope models
  Envelope parser and validator
  Terminal status model: completed/cancelled/error
  Schema-aware answer validator interface
  Golden JSON fixtures and tests

android-caller-sdk/
  QuestQuestionnaireLauncher
  Result file creation
  FileProvider URI helper
  Explicit panel Intent builder
  One-shot immutable PendingIntent callback helper
  Pending request store
  Callback/resume/cold-start recovery
  Android wrapper around contract-core validation

app/
  Quest 2D panel host
  Generic panel runtime
  Questionnaire renderer registry
  Terminal result writer
  BRB renderer module/package

examples/native-caller/
  Thin demo app using android-caller-sdk

unity-caller-plugin/
  Unity wrapper around android-caller-sdk
  C# facade for BRB and generic requests
```

## Priority Roadmap

| Priority | Work item | Why it matters | Status |
| ---: | --- | --- | --- |
| 1 | Extract `questionnaire-contract-core` | Prevent validator/schema drift before more callers exist. | Initial skeleton complete |
| 2 | Extract `android-caller-sdk` | Unlock native callers and make the Unity wrapper smaller. | Planned |
| 3 | Refactor terminal result writing | Make `cancelled` and `error` outcomes scientifically usable. | Planned |
| 4 | Make validation stage/schema-aware | Required for generic reuse beyond BRB. | Planned |
| 5 | Introduce renderer registry and move BRB behind it | Separate generic panel runtime from first questionnaire. | Planned |
| 6 | Add ViewModel/draft recovery | Protect in-progress study sessions. | Planned |
| 7 | Add per-screen timing metadata | High research value without changing IPC. | Planned |
| 8 | Split minimal vs. updater build flavors | Cleaner permissions and trust story. | Planned |
| 9 | Build Unity plugin on top of SDK | Avoid Unity-specific duplication. | Planned |
| 10 | Add generic questionnaire demo | Prove the panel is not BRB-only. | Planned |

## Implementation Slices

### Slice 1: Contract Core Skeleton

Status: Initial skeleton complete

Deliverables:

- Create `:questionnaire-contract-core`.
- Move or copy protocol constants into the core module.
- Add request/result envelope models.
- Add terminal status model for `completed`, `cancelled`, and `error`.
- Add a generic result envelope validator.
- Add schema-aware answer-validator interface.
- Add golden JSON fixtures.

Acceptance:

- Core tests cover valid completed, valid cancelled, valid error, bad nonce,
  bad schema, bad status, and screen-sequence mismatch.
- `:app` and `:examples:native-caller` still build without behavior changes.
- Contract constants do not drift between panel and caller.

Notes:

- Keep this pure Kotlin/JVM if possible. Do not pull Android APIs into core.
- Core should validate the result envelope first, then delegate answer payload
  checks to a questionnaire-specific validator.

### Slice 2: Native Caller Uses Contract Core

Status: Planned

Deliverables:

- Replace native caller validator internals with `questionnaire-contract-core`.
- Keep current BRB answer validation behavior initially.
- Preserve existing callback/resume/cold-start readback behavior.

Acceptance:

- Native caller tests still pass.
- Result validation reasons remain stable unless intentionally renamed.
- No panel behavior changes.

### Slice 3: Android Caller SDK

Status: Planned

Deliverables:

- Create `:android-caller-sdk` as an Android library module.
- Add `QuestQuestionnaireLauncher.prepare()`.
- Add result file and `FileProvider` URI helpers.
- Add explicit panel Intent builder.
- Add one-shot immutable callback helper.
- Add pending request store.
- Add readback/recovery APIs for callback, resume, and cold start.
- Make native caller demo use the SDK.

Acceptance:

- Native caller app becomes a thin demo.
- SDK exposes clear states: ready, panel missing, launched, result written,
  callback missing, cancelled, error, invalid result, duplicate callback.
- SDK does not own participant pseudonym strategy, retention/export policy,
  study state machine, or science-specific interpretation.

### Slice 4: Structured Cancelled/Error Results

Status: Planned

Deliverables:

- Add `TerminalResultWriter` in the panel app.
- Write `cancelled` results for user cancellation after valid launch context.
- Write `error` results for valid-launch renderer/runtime errors where the
  result URI is still trustworthy.
- Keep invalid launch and result-write failure local-only when necessary.
- Ensure the result stream closes before callback send.

Acceptance:

- `cancelled` result includes current stage/screen index and a non-sensitive
  terminal reason.
- `error` result includes non-sensitive error code/message.
- Callback failure after result write is represented differently from result
  write failure.
- Caller recovery on resume/cold start can still read a written result.

### Slice 5: Stage-Aware BRB Validator

Status: Planned

Deliverables:

- Add `BrbAnswerValidator`.
- Require only answer buckets relevant to the requested sequence.
- Keep full-BRB validation for full sequences.
- Update docs/examples/fixtures to stop implying every result always includes
  every BRB bucket.

Acceptance:

- Initial sequence requires language/demographics/prior-experience answers.
- Post-condition sequence requires post-condition answers.
- Final sequence requires final answers.
- Cancelled/error results do not require completed-answer buckets.

### Slice 6: Renderer Registry And BRB Package

Status: Planned

Deliverables:

- Introduce `QuestionnaireRendererFactory` and `QuestionnaireRenderer`.
- Add `QuestionnaireRendererRegistry`.
- Move BRB answer state, screens, audio routing, branching, and result mapping
  behind a BRB renderer implementation.
- Keep `QuestionnaireActivity` focused on launch, runtime hosting, terminal
  result writing, and error/cancel handling.

Acceptance:

- BRB behavior remains equivalent.
- The generic runtime does not import BRB-specific stage constants except via
  the registered BRB renderer.
- A second renderer can be added without modifying `QuestionnaireActivity`
  control flow.

### Slice 7: ViewModel And Draft Recovery

Status: Planned

Deliverables:

- Hoist runtime state out of Compose `remember` state.
- Use `ViewModel` plus `SavedStateHandle` or `rememberSaveable` for small
  recreation-safe state.
- Persist important app-private drafts keyed by request id/nonce.
- Clear drafts after terminal result write.

Acceptance:

- Activity recreation preserves current screen and answers.
- Draft files are app-private only.
- Draft filenames never contain raw participant data or answers.
- Stale drafts are rejected when request id/nonce do not match.

### Slice 8: Per-Screen Timing Metadata

Status: Planned

Deliverables:

- Add optional timing object to result schema and examples.
- Record top-level wall-clock and monotonic elapsed duration.
- Record per-screen entered, first interaction, left, duration,
  interaction count, and validation failures.

Acceptance:

- Existing callers that ignore unknown optional fields remain compatible.
- Timing does not include high-frequency gaze, hand pose, controller pose, or
  raw interaction traces.
- Timing semantics are documented in contract docs.

### Slice 9: Minimal vs. Lab-Updater Build Flavors

Status: Planned

Deliverables:

- Add a minimal research flavor with questionnaire IPC only.
- Add a lab updater flavor with update UI and install permissions.
- Keep debug smoke extras debug-only.

Acceptance:

- Minimal build manifest has no `INTERNET`.
- Minimal build manifest has no `REQUEST_INSTALL_PACKAGES`.
- Lab updater build retains updater behavior.
- Public docs state which flavor is the default recommendation.

### Slice 10: Unity Plugin And Generic Demo

Status: Planned

Deliverables:

- Build Unity wrapper on top of the Android caller SDK.
- Add C# facade for BRB and generic requests.
- Add a minimal generic questionnaire renderer/demo request.

Acceptance:

- Unity bridge does not duplicate contract validation rules.
- Generic demo proves the panel runtime is no longer BRB-only.
- BRB remains the richer reference integration.

## Cross-Cutting Documents To Add Or Update

- `docs/contract-versioning.md`
  - v1 compatibility rules.
  - Optional fields policy.
  - Status enum policy.
  - Questionnaire-specific answer schema versioning.
- `docs/research-data-safety.md`
  - Data classes.
  - Request metadata vs. answer payloads.
  - Pseudonymous participant/session references.
  - Draft/result retention rules.
  - Log/callback/filename prohibitions.
- `contract/examples/*.json`
  - Valid request/result fixtures.
  - Cancelled and error fixtures.
  - Invalid mismatch fixtures.

## Result Lifecycle Target

```text
Idle
  -> LaunchValidated
  -> Rendering
  -> UserSubmitted
  -> WritingResult
  -> ResultWritten
  -> CallbackSent
  -> Finished

Error and terminal paths:
  LaunchRejected
  UserCancelled
  RenderError
  WriteFailed
  CallbackFailedAfterWrite
```

Important distinction:

- `WriteFailed` means the caller cannot rely on result-file recovery.
- `CallbackFailedAfterWrite` means the result file was written and closed, so
  the caller can still recover on resume or cold start.

## Timing Metadata Target

Future result example:

```json
{
  "timing": {
    "started_at": "2026-06-13T10:15:00Z",
    "submitted_at": "2026-06-13T10:17:04Z",
    "duration_ms": 124000,
    "screens": [
      {
        "screen_id": "post_condition:pictographic",
        "ordinal": 0,
        "entered_at": "2026-06-13T10:15:02Z",
        "entered_elapsed_ms": 2000,
        "first_interaction_at": "2026-06-13T10:15:08Z",
        "first_interaction_elapsed_ms": 8000,
        "left_at": "2026-06-13T10:15:40Z",
        "left_elapsed_ms": 40000,
        "duration_ms": 38000,
        "interaction_count": 3,
        "validation_failures": 0
      }
    ]
  }
}
```

Field semantics:

| Field | Meaning |
| --- | --- |
| `entered_at` | Screen became current. |
| `first_interaction_at` | First answer-changing action on that screen. |
| `left_at` | User navigated away, submitted, cancelled, or hit a terminal error. |
| `interaction_count` | Answer-changing interactions only. |
| `validation_failures` | Blocked Next/Submit attempts or validation errors. |

## Living Notes

### 2026-06-14

- Created this iteration document from the expanded roadmap.
- Active implementation goal set in Codex for this thread.
- Initial implementation order: contract core, native caller adoption, Android
  SDK, structured terminal results, stage-aware BRB validation.
- Started Slice 1 by adding the `:questionnaire-contract-core` module,
  protocol constants, request/result envelope models, generic envelope
  validation, and golden JSON fixtures.
- Verified Slice 1 skeleton with `:questionnaire-contract-core:test`,
  `:app:assembleDebug`, and `:examples:native-caller:assembleDebug`.
- Next implementation step: Slice 2, make the native caller use contract-core
  while preserving existing BRB answer validation behavior.

## Decision Log

Add decisions here as they become real implementation constraints.

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-06-14 | Extract pure contract core before Android SDK. | Keeps protocol validation reusable by Android callers, Unity wrappers, and tests. |

## Open Questions

- Should `terminal_reason` be added as a new neutral result field in v1, or
  should `error` continue to carry cancellation reasons until v2?
- Should `questionnaire-contract-core` use handwritten JSON parsing first, or
  introduce serialization/code generation later?
- What is the default public flavor name: `minimalResearch`, `research`, or
  `minimal`?
- How much draft recovery should the panel own before the caller SDK can
  relaunch/resume the same request id?

## Verification Checklist

Before committing Android implementation changes:

- `git diff --check`
- `gradle :app:assembleDebug` when Gradle is available
- `gradle :examples:native-caller:assembleDebug` when Gradle is available
- Relevant unit tests for changed modules
- No raw participant data, device serials, APKs, screenshots, logcat bundles,
  signing keys, local machine paths, or private evidence artifacts committed
