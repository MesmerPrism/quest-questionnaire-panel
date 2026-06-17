# Windows Makepad Questionnaire Operator Example

The Windows operator app lives in
`operator/makepad-quest-questionnaire-operator/`. It is a Makepad GUI that
sends commands to an on-Quest Rusty Morphospace bridge, displays foreground
status reported by that bridge, and optionally reads passive Quest readiness
signals through ADB.

This is one reference integration for the same panel architecture used by the
Unity/BRB example. The current Windows operator path targets the MAIA-2 plus
spatial-frame-reference questionnaire through Rusty Morphospace. The Unity path
targets the BRB questionnaire sequence through a Unity-owned Android bridge.
The questionnaire content and control surfaces differ; the panel runtime,
request/result contract, caller-owned `content://` result URI, and callback
pattern are shared.

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
| Status | `device-status --serial <serial> [--json]` |
| Forward | `bridge-forward --serial <serial> --host-port <port> --device-port <port> [--json]` |
| Panel APK | `install-panel --serial <serial> --apk <apk-path> [--json]` |
| Install Panel | `install-panel --serial <serial> --apk <apk-path> [--json]` |
| Install target APK | `install-target-apk --serial <serial> --apk <apk-path> [--json]` |
| Launch target runtime | `launch-target-runtime --serial <serial> --package <package> [--activity <activity>] [--json]` |
| Pull target session | `pull-target-session --serial <serial> --package <package> --out <folder> [--remote-relative files/runtime_csv] [--json]` |
| Open Block 1 | `open-block --block 1 --session-id <id> --participant-ref <ref> --language-code <en-or-de> --endpoint <url>` |
| Open Block 2 | `open-block --block 2 --session-id <id> --participant-ref <ref> --language-code <en-or-de> --endpoint <url>` |
| Open Block 3 | `open-block --block 3 --session-id <id> --participant-ref <ref> --language-code <en-or-de> --endpoint <url>` |
| Dismiss Panel | `dismiss --session-id <id> --endpoint <url>` |

The CLI also has downstream-runtime helpers for target apps that expose the
same low-rate bridge route:

| CLI-only helper | Command |
| --- | --- |
| Start target session | `start-session --session-id <id> --participant-ref <ref> --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |
| Mark target timing event | `mark-timing-event --session-id <id> --marker-name <name> --marker-detail <text> --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |
| Stop target session | `stop-session --session-id <id> --protocol-version <runtime-protocol> --runtime-kind <kind> --endpoint <url> [--audit-dir <dir>]` |
| Post private fixture JSON | `post-command --file <command.json> --endpoint <url> [--audit-dir <dir>]` |

Keep concrete private runtime protocol ids, package names, APK hashes, study
stage maps, participant ids, and local file paths in private fixtures. Do not
promote them into this public operator repository unless a separate
public-boundary review approves a sanitized version.

Target APK install and target runtime launch are setup/foregrounding helpers
only. Questionnaire foregrounding must still happen through the on-Quest caller
bridge and the caller-owned `content://` result URI contract.

Target session pull is an explicit export operation. It copies app-specific
Quest files from `/sdcard/Android/data/<package>/...` into a chosen local
folder after the operator asks for it; it should not become an implicit
questionnaire result path or a high-rate sample transport.

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
