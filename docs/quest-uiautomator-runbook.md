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
  -e childTargets "camera:Bit rate,camera:Frame rate,camera:Image stabilization,camera:Eye perspective" `
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
- restore a visible headset panel manually;
- rerun the same command after the panel is visible;
- do not use force-stop or package killing as the default public path.

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
