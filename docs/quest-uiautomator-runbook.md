# Quest UIAutomator Sweep Runbook

Last updated: 2026-06-14

This runbook is the repeatable test plan for mapping Quest Android UI surfaces
with the `examples:quest-ui-automation` instrumentation APK. The public source
of truth for findings is `docs/quest-uiautomator-capability-map.md`; the
machine-readable command ledger is
`docs/quest-uiautomator-command-database.jsonl`.

The runbook is for lab discovery only. It is not part of the questionnaire
panel product flow.

## Preconditions

- Reserve shared Quest and ADB resources when another agent could be using this
  machine.
- Build and install the host and instrumentation APKs from the current tree:

```powershell
.\gradlew.bat :examples:quest-ui-automation:assembleDebug :examples:quest-ui-automation:assembleDebugAndroidTest
adb install -r examples\quest-ui-automation\build\outputs\apk\debug\quest-ui-automation-debug.apk
adb install -r examples\quest-ui-automation\build\outputs\apk\androidTest\debug\quest-ui-automation-debug-androidTest.apk
```

- Keep raw reports, XML dumps, screenshots, logcat, recordings, device serials,
  and local paths under ignored `artifacts/` or off-repo storage.
- Before copying any result into docs, summarize it through:

```powershell
python examples\quest-ui-automation\tools\summarize_report.py <report.jsonl> --format markdown
```

## Baseline Slot

Run a passive current-window or surface-map pass before broad settings sweeps
when the headset has been through several Meta system panels:

```powershell
adb shell am instrument -w `
  -e scenario currentWindow `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Pass evidence:

- report has a `finish` row;
- the first UI dump has nonzero nodes;
- active accessibility window count is present;
- no raw report is committed.

If the first UI dump has zero nodes, do not run a broad settings crawl. Run a
fresh `currentWindow` or `surfaceMap` pass and restore a visible headset panel
manually if needed.

## System Surface Reachability Slot

Use this passive slot to compare which Android-backed Meta system surfaces are
actually exposed to UIAutomator on the current OS build:

```powershell
adb shell am instrument -w `
  -e scenario systemSurfaceReachability `
  -e surfaces current,quickSettings,notifications,androidSettings,metacamPanel `
  -e waitAfterSurfaceMs 1000 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Pass evidence:

- each requested surface emits either `system_surface_attempt` or
  `system_surface_error`;
- each successful surface has a matching `accessibility_state` row;
- the exporter renders a `System Surface Reachability` table with merged XML
  counts plus active-root node, scrollable, and window counts;
- the summary reports only counts, display IDs, changed/empty status, and
  error class presence, not raw UI text or package names.

Use deeper Metacam surfaces only in a scoped capture-settings pass:

```text
surfaces=current,metacamPanel,metacamSettings,metacamDeepSettings,metacamAdvancedSettings
```

Current 2026-06-14 reachability evidence:

- the default sweep passed and emitted all requested surface rows;
- `quickSettings` and `notifications` did not expose distinct Quest surfaces
  from the already-visible Settings state;
- `androidSettings` changed to/reached the Quest Settings surface;
- shallow Metacam entries can keep identical merged XML counts because
  background panels remain in the hierarchy, so compare the
  `Accessibility Windows` active-root profile as well as the
  `System Surface Reachability` table;
- scoped Metacam deep settings changed the merged hierarchy and active root to
  a settings profile with two scrollables, while advanced settings switched the
  active root again after scrolling.

## BRB Demo Recording Slot

Use this active slot for the public BRB plus questionnaire-panel capture pass,
not for product validation. The detailed public clip map is
[`docs/demo-capture-workflow.md`](demo-capture-workflow.md).
For the short route-by-route comparison pass, use
[`docs/recording-comparison-matrix.md`](recording-comparison-matrix.md) and
the runner:

```powershell
.\tools\Run-BrbRecordingComparison.ps1 -RecordSeconds 15
```

Preflight:

```powershell
foreach ($action in @(
  'DISABLE_OVERLAY',
  'DISABLE_OVERLAY_CAPTURE',
  'DISABLE_GRAPH',
  'DISABLE_STATS',
  'DISABLE_CSV'
)) {
  adb shell am broadcast `
    -a "com.oculus.ovrmonitormetricsservice.$action" `
    -n com.oculus.ovrmonitormetricsservice/.SettingsBroadcastReceiver
}

adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --es brb.runtimeCommand center_button
```

When capture framing needs a portrait source, set and later restore Metacam
recorder settings through the guarded dropdown path:

```powershell
$instrument = "am instrument -w " +
  "-e scenario settingsChildPageProbe " +
  "-e childTargets 'camera:Aspect ratio,camera:Bit rate,camera:Frame rate,camera:Image stabilization' " +
  "-e childTargetRole dropdown " +
  "-e clickModes coordinate " +
  "-e optionTargets 'Aspect ratio=Portrait 9:16;Bit rate=14 mbps;Frame rate=60 fps;Image stabilization=High' " +
  "-e allowOptionSelect true " +
  "-e optionClickMode coordinate " +
  "-e maxContentScrolls 5 -e maxNavScrolls 10 " +
  "-e dumpChildAccessibility false " +
  "io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner"
adb shell $instrument
```

Rollback command:

```powershell
$instrument = "am instrument -w " +
  "-e scenario settingsChildPageProbe " +
  "-e childTargets 'camera:Aspect ratio,camera:Bit rate,camera:Frame rate,camera:Image stabilization' " +
  "-e childTargetRole dropdown " +
  "-e clickModes coordinate " +
  "-e optionTargets 'Aspect ratio=Landscape 16:9;Bit rate=3 mbps;Frame rate=30 fps;Image stabilization=Off' " +
  "-e allowOptionSelect true " +
  "-e optionClickMode coordinate " +
  "-e maxContentScrolls 5 -e maxNavScrolls 10 " +
  "-e dumpChildAccessibility false " +
  "io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner"
adb shell $instrument
```

Run the recorder bracket in one shell:

```powershell
adb shell am instrument -w `
  -e scenario metacamRecordProbe `
  -e recordMs 180000 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

While the probe is sleeping, use a second shell to trigger the BRB Unity
moments and panel routes:

```powershell
adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --es brb.runtimeCommand center_button

adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --es brb.runtimeCommand "blink_button:6"

adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --es brb.runtimeCommand press_button

adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --ez brb.questionnaireOpen true `
  --es brb.questionnaireTrigger initial
```

Add the post-condition and final-panel commands from the demo-capture workflow
when the recording needs the full showcase. Let the recorder probe finish so it
can reopen the Metacam sharing panel and tap stop.

Pass evidence:

- report has `record_probe_start_result.tapped=true`;
- report has `record_probe_stop_result.tapped=true`;
- report has a `finish` row;
- if temporary settings were changed, a follow-up selector summary confirms the
  defaults again;
- any headset video listing remains local evidence only;
- the raw one-take MP4 stays outside this repo;
- only reviewed, cut clips under `docs/media/*.mp4` can be committed.

Current 2026-06-14 evidence:

- portrait mode produced a real `1080x1920`, roughly 60 FPS Metacam source;
- OVR Metrics disable broadcasts completed before capture, and review contact
  sheets showed no performance HUD overlay;
- both landscape and portrait modes proved the workflow, but neither fully
  framed the current BRB plus panel composition.

If the stop tap fails, stop recording manually through the visible headset
sharing UI and record the failure in private run notes. Do not commit raw
UIAutomator reports, video listings, local paths, device serials, or the raw
one-take video.

## Section Crawl Slot

Use this to map top-level settings sections, main-content scroll endpoints, and
route inventory without selecting options or toggling settings:

```powershell
adb shell am instrument -w `
  -e scenario settingsSectionCrawler `
  -e navTargets quest_link,general,action_button,space_setup,world_movement,movement_tracking,accessibility,display_brightness,audio,camera,experimental `
  -e maxNavScrolls 10 `
  -e maxSectionScrolls 6 `
  -e mainCoordinateFallback false `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Pass evidence:

- each target either emits pages plus a stopped scroll endpoint or an explicit
  skip reason;
- `mainCoordinateFallback=false` for the default public sweep;
- multi-page sections record moved and stopped scroll events;
- route inventory is present for every crawled page.

Current 2026-06-14 evidence:

- 11 low-risk sections reached explicit endpoints.
- Movement and Camera required two object scrolls before a stopped endpoint.
- Experimental required one object scroll before a stopped endpoint.
- Generated child-page targets were:
  `general:Quick controls`, `general:Storage`, `general:Ongoing activities`,
  `space_setup:Boundary`, `space_setup:Travel mode`, `accessibility:Vision`,
  `accessibility:Mobility`, `accessibility:Hearing`, and
  `audio:Spatial audio for windows`.

## Generated Child-Page Slot

Generate low-risk child-page targets from a section crawl:

```powershell
$targets = python examples\quest-ui-automation\tools\summarize_report.py `
  .\artifacts\quest-uiautomator\section-crawl-report.jsonl `
  --format child-targets
```

Run the child-page probe with a quoted remote command so labels containing
spaces remain a single extra value:

```powershell
$instrument = "am instrument -w " +
  "-e scenario settingsChildPageProbe " +
  "-e childTargets '$targets' " +
  "-e childTargetRole row " +
  "-e clickModes coordinate " +
  "-e maxContentScrolls 2 " +
  "-e maxNavScrolls 10 " +
  "-e dumpChildAccessibility false " +
  "io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner"
adb shell $instrument
```

Pass evidence:

- each route emits `settings_child_surface` or `settings_child_skip`;
- successful routes press Back and emit `settings_child_after_back`;
- skipped routes include a public-safe reason;
- summaries use the `Child Page Surfaces` and `Child Page Skips` sections from
  the exporter.

Current 2026-06-14 evidence:

- Quick controls, Storage, Ongoing activities, Boundary, Travel mode, Vision,
  Mobility, and Hearing opened distinct child content and returned.
- Spatial audio for windows skipped in one broad child run because the Audio
  side-nav target was not found.
- A focused retry reached an invisible/sleeping Settings state where
  `prepare_android_settings` had zero nodes. The patched probe records this as
  `settings_child_skip` instead of entering the nav search path.

## Dropdown Slot

Use `childTargetRole=dropdown` for selector rows. Default to dry-run option
targeting:

```powershell
adb shell am instrument -w `
  -e scenario settingsChildPageProbe `
  -e childTargets "camera:Aspect ratio,camera:Bit rate,camera:Frame rate,camera:Image stabilization,camera:Eye perspective" `
  -e childTargetRole dropdown `
  -e clickModes "coordinate,uiObject2,accessibilityClick" `
  -e maxContentScrolls 4 `
  -e maxNavScrolls 10 `
  -e dumpChildAccessibility false `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Mutation gate:

- `allowOptionSelect=false` is the default.
- Only use `allowOptionSelect=true` with a written current value, desired value,
  rollback step, and stop condition.
- Verified camera options include aspect ratio `Square 1:1`,
  `Landscape 16:9 (Default)`, and `Portrait 9:16`; bit rate
  `3/6/9/14 mbps`; frame rate `30/60 fps`; image stabilization
  `Off/Low/Medium/High`; and eye perspective `Left/Right eye`.

## Zero-Node Settings Surface

Failure signature:

- `am start -W -a android.settings.SETTINGS` returns through
  `com.oculus.vrshell/.intents.AndroidIntentsRelayActivity`;
- `prepare_android_settings` emits zero nodes;
- focus may show the settings task as invisible or sleeping;
- host `adb shell am instrument -w` can outlive the report finish in this
  state.

Expected handling:

- settings nav, section crawler, and child-page scenarios emit an explicit skip
  instead of searching selectors;
- the exporter renders the skip in `Child Page Skips`;
- treat the run as environment-state evidence, not as proof that the route is
  unsupported.

Recovery options:

- run `currentWindow` or `surfaceMap=current` to capture current state;
- run `settingsRecoveryProbe` to record the zero-node state, passive baselines,
  and bounded retries without force-stopping packages:

```powershell
adb shell am instrument -w `
  -e scenario settingsRecoveryProbe `
  -e retryCount 2 `
  -e retryWaitMs 1500 `
  -e dumpPassiveBaselines true `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

- restore a visible headset panel manually;
- rerun the same command after the panel is visible;
- do not use force-stop or package killing as the default public path.

Current 2026-06-14 recovery-probe evidence:

- visible Settings state validated: `settingsRecoveryProbe` saw 363 XML nodes,
  107 Settings-package nodes, two scrollables, and five accessibility windows;
- zero-node behavior was not reproduced in that run, so passive-baseline
  recovery remains pending until the next invisible/sleeping Settings state.

## Remote Bridge Slot

If this automation is triggered through `quest-termux-lab`, use the typed
command `uiautomator.run_allowlisted_scenario`. That command must carry an
active remote-session lease, pass the local ADB shell gate, and map only to
named scenarios in this runbook. Return exporter summaries by default; do not
return raw XML, screenshots, recordings, logcat bundles, device serials, local
paths, installed app names, or private package IDs.

## Publication Gate

Before committing public updates:

```powershell
python tools\check_public_artifacts.py
git diff --check
```

For automation code changes, also run:

```powershell
.\gradlew.bat :examples:quest-ui-automation:assembleDebug :examples:quest-ui-automation:assembleDebugAndroidTest
```

Only publish:

- redacted summaries;
- command IDs and status changes;
- section/page counts;
- route inventory counts;
- public-safe labels from the exporter;
- explicit skip reasons.

Do not publish:

- raw XML;
- raw report paths;
- device serials;
- account names;
- installed app lists;
- screenshots, videos, or logcat bundles from lab runs.
