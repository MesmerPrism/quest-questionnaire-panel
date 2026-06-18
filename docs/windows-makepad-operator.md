# Windows Makepad Questionnaire Operator Example

The Windows operator app lives in
`operator/makepad-quest-questionnaire-operator/`. It is a Makepad GUI that
sends commands to an on-Quest Rusty Morphospace bridge, displays foreground
status reported by that bridge, and optionally reads passive Quest readiness
signals through ADB.

This is one reference integration for the same panel architecture used by the
Unity/BRB example. The current Windows operator path targets the MAIA-2 plus
spatial-frame-reference questionnaire through Rusty Morphospace, and also
includes generic target-runtime controls for APKs that expose the same
`GET /v1/status` and `POST /v1/command` bridge shape. The Unity path targets a
Unity-owned Android bridge. The questionnaire content and study-specific stage
maps differ; the panel runtime, request/result contract, caller-owned
`content://` result URI, and callback pattern are shared.

This keeps the Android authority boundary intact:

```text
Windows Makepad operator
  -> HTTP command to Rusty Morphospace bridge on Quest
  -> foreground Rusty Morphospace Android caller
  -> explicit Quest Questionnaire Panel intent
  -> caller-owned content:// result URI
  -> broadcast PendingIntent completion
```

The operator does not use ADB for questionnaire launch, public storage,
overlays, package killing, or a direct panel launch from Windows. Its ADB lane
is limited to tooling/device readiness, panel APK installation, bridge port
forwarding, passive foreground/battery/controller snapshots, and proof
evidence.

## Run

```powershell
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml
```

Set the endpoint field to the bridge base URL. `http://127.0.0.1:8787` is only
the local development default. For headset use, enter the Quest bridge URL.

Build the public minimal panel APK before using the install action:

```powershell
.\gradlew.bat :app:assembleMinimalDebug
```

The GUI's **Panel APK** field defaults to
`app/build/outputs/apk/minimal/debug/app-minimal-debug.apk`. Select a Quest
serial, then use **Install Panel** to run an ADB reinstall of that APK. This is
a setup/update action only; questionnaire foregrounding still belongs to the
on-Quest caller bridge.

## CLI Equivalents

The operator crate also builds `quest-questionnaire-operator-cli`. The CLI
matches the GUI surface:

| GUI control | CLI command |
| --- | --- |
| Connect / Poll | `status --endpoint <url>` |
| Tools | `tooling-status [--json]` |
| Devices | `devices [--json]` |
| Status | `device-status --serial <serial> [--out <snapshot.json>] [--json]` |
| Forward | `bridge-forward --serial <serial> --host-port <port> --device-port <port> [--json]` |
| Panel APK | `install-panel --serial <serial> --apk <apk-path> [--json]` |
| Install Panel | `install-panel --serial <serial> --apk <apk-path> [--json]` |
| Install target APK | `install-target-apk --serial <serial> --apk <apk-path> [--json]` |
| Launch target runtime | `launch-target-runtime --serial <serial> --package <package> [--activity <activity>] [--json]` |
| Pull target session | `pull-target-session --serial <serial> --package <package> --out <folder> [--remote-relative files/runtime_csv] [--verify-bundle] [--bundle-path <folder>] [--write-receipt] [--json]` |
| Write session manifest | `write-session-manifest --out <manifest.json> [--artifact <label=file>] [--json]` |
| Open Block 1 | `open-block --block 1 --session-id <id> --participant-ref <ref> --language-code <en-or-de> --endpoint <url>` |
| Open Block 2 | `open-block --block 2 --session-id <id> --participant-ref <ref> --language-code <en-or-de> --endpoint <url>` |
| Open Block 3 | `open-block --block 3 --session-id <id> --participant-ref <ref> --language-code <en-or-de> --endpoint <url>` |
| Dismiss Panel | `dismiss --session-id <id> --endpoint <url>` |
| Target Runtime fields | `--protocol-version`, `--runtime-kind`, `--runtime-package`, `--study-id`, `--condition-id`, `--questionnaire-id`, `--open-stage`, `--marker-name`, `--remote-relative` |
| Target Start | `start-session --session-id <id> --participant-ref <ref> --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |
| Target Mark | `mark-timing-event --session-id <id> --marker-name <name> --marker-detail <text> --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |
| Target Open Q | `open-questionnaire --session-id <id> --participant-ref <ref> --study-id <id> --questionnaire-id <id> --open-stage <stage> --screen-sequence <stage> --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |
| Target Stop | `stop-session --session-id <id> --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |
| Target Pull | `pull-session --session-id <id> --remote-relative files/runtime_csv --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |

The GUI target-runtime controls and the CLI helpers below are for target apps
that expose the same low-rate bridge route:

| Additional CLI helper | Command |
| --- | --- |
| Post private fixture JSON | `post-command --file <command.json> --endpoint <url> [--audit-dir <dir>]` |

Keep concrete private runtime protocol ids, package names, APK hashes, study
stage maps, participant ids, and local file paths in private fixtures. Do not
promote them into this public operator repository unless a separate
public-boundary review approves a sanitized version.

Target APK install and target runtime launch are setup/foregrounding helpers
only. Questionnaire foregrounding must still happen through the on-Quest caller
bridge and the caller-owned `content://` result URI contract.

Use `device-status --serial <quest-serial> --json --out <snapshot.json>` before
and after a run when you need a Quest-side setup snapshot. It writes a
pretty-printed JSON document with a protocol id and host capture timestamp, and
records headset battery/wake/display state, focused app/window when ADB exposes
it, screen brightness, music volume, proximity state, and controller
battery/connection state. This is an explicit operator snapshot; high-rate
pose, breathing, sphere, timing, and performance data stays in the Unity
session CSV bundle. Controller rows are observational setup data: disconnected,
inactive, or absent controllers should be preserved in the snapshot rather than
treated as a failed run.

The generic `open-questionnaire` helper builds the low-rate command envelope
for a target runtime bridge. It does not create result URIs, use ADB for panel
launch, or replace study-specific fixture review.

Target session pull is an explicit export operation. It copies app-specific
Quest files from `/sdcard/Android/data/<package>/...` into a chosen local
folder after the operator asks for it; it should not become an implicit
questionnaire result path or a high-rate sample transport.

The `pull-session` HTTP helper is the low-rate request to the target runtime to
declare or prepare its app-private `files/runtime_csv` bundle. The separate
`pull-target-session` ADB helper copies those files only after that operator
workflow step has been requested.

Pass `--verify-bundle` to `pull-target-session` to run the local verifier right
after the ADB copy. The command infers the pulled session folder as
`<out>\<remote-folder-name>`, so use concrete remote paths such as
`files/runtime_csv/participant-P001/session-001`; pass `--bundle-path` when the
local pull layout is different. You can also run
`verify-session-bundle --path <folder>` separately before accepting the export
as study evidence. The verifier checks the expected additive Unity files,
known CSV headers including runtime-state environment/pose/breathing columns,
settings/snapshot protocol ids, and non-empty questionnaire JSONL rows locally;
it does not contact the headset. Add `--write-receipt` to leave
`operator_verification_receipt.json` in a successfully verified bundle, or
`--receipt-file <path>` to place that JSON receipt in an audit folder. Receipts
include each expected file's byte size and SHA-256 digest so the accepted bundle
can be matched later without reopening the Quest.

Use `write-session-manifest --out <manifest.json>` after local verification to
write a Windows-side session snapshot that points at the operator artifacts for
the run. Pass repeated `--artifact label=<path>` values for device-status
snapshots, verification receipts, pulled session bundle directories, command
audits, and other local evidence. The manifest records its own protocol id and
timestamp, hashes file artifacts that are present, and records a deterministic
file count, byte count, and tree SHA-256 for directory artifacts.

The default expected-file list for target runtime exports includes the additive
Unity session CSV/JSON bundle, including `runtime_state_samples.csv` for wide
Unity/Quest runtime state and `clock_alignment_samples.csv` for LSL clock-probe
echo evidence, plus `legacy_outputs_manifest.json` for metadata-only pointers
to legacy Unity output files. Keep timestamp-sensitive samples in LSL/CSV, not
in JSON HTTP command payloads.

When `--audit-dir` is supplied, runtime HTTP helpers append
`command_audit.jsonl` with the command request, bridge response, timing, and
error status. Treat that output as local session evidence and keep real study
audit folders out of source control.

Install example:

```powershell
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- install-panel --serial <quest-serial> --apk app\build\outputs\apk\minimal\debug\app-minimal-debug.apk --json
```

Additional proof command:

```powershell
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- proof-run --block 2 --session-id maia-spatial-session-001 --participant-ref P001 --language-code en --endpoint http://127.0.0.1:8787 --out artifacts\operator-proof-run
```

Example:

```powershell
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- open-block --block 2 --session-id maia-spatial-session-001 --participant-ref P001 --language-code en --endpoint http://127.0.0.1:8787
```

## Bridge Endpoints

The bridge should expose:

```text
GET  /v1/status
POST /v1/command
```

Sample command and status JSON files are in `contract/operator-bridge/`.

The command payload includes a `panel_request` object containing the same
MAIA/spatial launch values documented in
`docs/rusty-morphospace-maia-spatial.md`:

| Button | `command_name` | `open_stage` |
| --- | --- | --- |
| Open Block 1 | `maia_spatial.block1` | `maia_spatial:language_selection` |
| Open Block 2 | `maia_spatial.block2` | `maia_spatial:spatial_frame_reference_1` |
| Open Block 3 | `maia_spatial.block3` | `maia_spatial:spatial_frame_reference_2` |

Rusty Morphospace should translate an accepted `open_questionnaire` command
into the existing `QuestQuestionnaireLauncher` flow. The bridge must create the
request id, nonce, result URI, and completion `PendingIntent` on Quest.

## Foreground Feedback

The app expects bridge responses to include:

```json
"foreground": {
  "xr_app_foreground": false,
  "panel_foreground": true,
  "foreground_package": "io.github.mesmerprism.questquestionnaire",
  "foreground_activity": ".panel.QuestionnaireActivity",
  "questionnaire_id": "maia2-spatial-frame-questionnaire-v1",
  "open_stage": "maia_spatial:spatial_frame_reference_1"
}
```

The exact foreground detection mechanism belongs in the on-Quest bridge, where
the foreground app and panel launch lifecycle are observable without weakening
the questionnaire app contract.
