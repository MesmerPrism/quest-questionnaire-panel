# Quest Questionnaire Panel

Standalone native Quest 2D questionnaire panel app and the minimal caller app
needed to prove cross-app communication.

Project website: <https://mesmerprism.com/quest-questionnaire-panel/>.

Contribution guide: [CONTRIBUTING.md](CONTRIBUTING.md).

The first target is not a fully generic survey framework. The first target is
to extract the Big Red Button questionnaire behavior into a reusable 2D Quest
panel app with a versioned Android IPC contract that native XR and Unity XR
callers can share.

## Repo Shape

```text
app/                    # Native Kotlin/Android Quest 2D panel app
contract/               # Versioned JSON schemas and intent names
questionnaire-contract-core/ # Pure Kotlin envelope parsing and validation
brb-questionnaire-core/ # Pure Kotlin BRB stage constants and answer validation
android-caller-sdk/     # Kotlin/AAR helper for Android callers
unity-caller-plugin/    # Unity C# facade plus Android bridge wrapper
examples/
  native-caller/        # Minimal Android caller/tester app
docs/
  WORKPLAN.md
  handoff-contract.md
  contract-versioning.md
  research-data-safety.md
  demo-capture-workflow.md
  recording-comparison-matrix.md
  source-audit-intake-2026-06-14.md
  quest-uiautomator-runbook.md
  remote-operations-relationship.md
  validation-matrix.md
  handoff-prompt.md
tools/
  check_public_artifacts.py # staged guard for private lab artifacts
```

## Current Scope

1. Build the standalone Quest 2D questionnaire panel app.
2. Support `quest.questionnaire.v1` request/result JSON.
3. Keep contract parsing/validation in `questionnaire-contract-core`.
4. Keep BRB stage constants and answer validation in `brb-questionnaire-core`.
5. Provide `android-caller-sdk` for native Android and Unity bridge callers.
6. Register BRB and generic questionnaire renderers behind the panel registry.
7. Provide a Unity C# facade and Android bridge in `unity-caller-plugin`.

## Communication Pattern

The caller owns the result file and grants the panel temporary write access:

```text
caller app
  -> create request id, nonce, request JSON, and private result file
  -> expose result file as content:// URI
  -> launch panel with explicit component and write grant

questionnaire panel
  -> render requested stage/sequence
  -> write final result JSON to granted URI
  -> send caller PendingIntent broadcast
  -> finish only the panel activity

caller app
  -> read and validate result JSON
  -> recover on callback, resume, or cold start
```

No ADB, public shared storage, Termux file drops, force-stop, package killing,
or Meta menu navigation should be part of the product path.

## Build Flavors

The default public recommendation is the minimal questionnaire build. It has
questionnaire IPC only and does not request internet or package-install
permissions:

```powershell
.\gradlew.bat :app:assembleMinimalDebug
```

The lab updater build keeps the internal sideload update UI and install
permissions:

```powershell
.\gradlew.bat :app:assembleLabUpdaterDebug
```

Use the same package name for both flavors so lab updater APKs can replace an
installed panel APK when they are signed with the same certificate.

## Current Status

The native panel app, Android caller SDK, Unity bridge module, and minimal
native caller tester build as debug artifacts. The smoke path proves the
caller-owned `content://` result URI plus one-shot immutable broadcast
`PendingIntent` completion contract.

For off-store update behavior and fleet-operation caveats, see
[`docs/self-update.md`](docs/self-update.md). The app-side updater uses
Android's installer UI; Termux plus loopback WiFi ADB, including any
operator-launched helper that restarts a stopped Termux agent, is an external
lab/fleet operations path, not part of the questionnaire result contract.

For the relationship between this product contract, the Quest workflow repo,
the Termux fleet-control lab, and the UIAutomator settings/recorder sweeps, see
[`docs/remote-operations-relationship.md`](docs/remote-operations-relationship.md).

Before committing public docs or lab automation notes, run:

```powershell
python tools\check_public_artifacts.py
```

To render clean host-side panel UI images for docs review, without a headset,
emulator, screenshot, or video recorder, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\Export-PanelRenderSequence.ps1
```

The generated PNGs are written to `artifacts/panel-render-sequence/` and are
ignored by git.

For the latest source-intake decision on Quest UI automation and capture
references, see
[`docs/source-audit-intake-2026-06-14.md`](docs/source-audit-intake-2026-06-14.md).

For the current public BRB plus panel demo capture plan, including the
one-take headset-recorder workflow and clip map, see
[`docs/demo-capture-workflow.md`](docs/demo-capture-workflow.md).

For the apples-to-apples 15-second capture comparison across built-in
landscape, built-in portrait, ADB `screenrecord`, and MediaProjection, see
[`docs/recording-comparison-matrix.md`](docs/recording-comparison-matrix.md).
