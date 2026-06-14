# Work Plan

Goal: create a standalone native Quest 2D questionnaire panel app that can be
called by any cooperating 3D/XR app, plus a minimal native caller tester that
proves the communication contract before Unity integration.

## Phase 0: Repo Baseline

Status: scaffolded; BRB split-app questionnaire port is in progress.

Deliverables:

- Root Gradle project with `app` and `examples:native-caller` modules.
- Versioned `quest.questionnaire.v1` contract files.
- README, AGENTS instructions, validation matrix, and handoff prompt.

Acceptance:

- Repo commits cleanly.
- Remote exists.
- A new development thread can start from the repository root.

## Phase 1: Make The Native Panel Build

Deliverables:

- Buildable `:app` minimal and lab-updater debug APKs.
- Quest 2D panel manifest with exported launch activity and 2D category.
- Request parser for `quest.questionnaire.v1`.
- Result writer for caller-owned `content://` URI.
- Broadcast `PendingIntent` completion callback.
- Basic error handling when required extras or URI grants are missing.

Acceptance:

- `gradle :app:assembleMinimalDebug :app:assembleLabUpdaterDebug` succeeds.
- Unit or instrumentation-level parser tests cover valid and invalid request
  JSON.
- No participant data is written to logs, filenames, Intent extras, or public
  storage.

## Phase 2: Make The Native Caller Tester Build

Deliverables:

- Buildable `:examples:native-caller` debug APK.
- Caller-owned private result file and narrow `FileProvider` path.
- Explicit launch of the questionnaire panel.
- `FLAG_GRANT_WRITE_URI_PERMISSION` only for result URI.
- Private broadcast receiver for completion callback.
- Result readback on callback, resume, and cold start.

Acceptance:

- `gradle :examples:native-caller:assembleDebug` succeeds.
- Caller validates request id, nonce, schema, status, and answer shape.
- Duplicate callbacks are idempotent.

## Phase 3: Prove Cross-App Handoff On Quest

Deliverables:

- Install both APKs on a Quest.
- Launch native caller tester as a 2D panel.
- Open questionnaire panel from caller.
- Submit structured BRB questionnaire answers.
- Verify caller reads valid result JSON from its own private result file.

Acceptance:

- No ADB, Termux, public shared storage, force-stop, package killing, or Meta
  menu navigation is needed in the product path.
- Foreground readback shows caller -> questionnaire -> caller-visible recovery.
- Logcat evidence has no answer payloads.
- Manual grant cleanup or transient-file cleanup policy is documented.

## Phase 4: Replace Placeholder Screens With BRB Screens

Deliverables:

- BRB demographics screen.
- BRB language and prior-experience screens.
- Post-condition pictographic screen.
- Post-condition presence questionnaire screen.
- Post-condition lost opportunity screen.
- Final confirmation and final-return prompt screens.
- Complete/export summary screen.
- Screen sequence runner driven by `open_stage` and `screen_sequence`.
- Localized generated audio assets for panel-owned questionnaire prompts.

Acceptance:

- Each BRB-first stage can be opened directly by request JSON.
- Results conform to `quest.questionnaire.v1.result`.
- Missing/invalid required answers produce validation errors before submit.
- Unity remains the owner of condition sessions and physical button press
  counting.

## Phase 5: Caller SDK Extraction

Deliverables:

- `android-caller-sdk` AAR or source module.
- Helper that creates result file, URI, request JSON, and callback
  `PendingIntent`.
- Helper that validates result JSON.

Acceptance:

- Native caller tester uses the SDK instead of handwritten launch code.
- SDK API is small enough to wrap from Unity.

## Phase 6: Unity Plugin

Prerequisite: the Unity BRB project is available locally and its package/app id
is known.

Deliverables:

- Unity Android plugin wrapper.
- C# facade for launch request and async result status.
- Unity sample scene or BRB integration branch.

Acceptance:

- Unity caller can open the same panel APK and read validated result JSON.
- Native and Unity callers use the same request/result schemas.

## Open Decisions

- Release signing and distribution policy for public/internal builds.
- Whether activity-return `PendingIntent` is needed or broadcast completion is
  enough for BRB.
- Retention/export policy for participant-sensitive result JSON.
- Exact BRB questionnaire wording and answer encoding.
- Whether result schemas stay JSON-schema-only or also generate Kotlin/C#
  types.
