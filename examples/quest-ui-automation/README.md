# Quest UI Automation Example

This example builds a small host APK plus an Android instrumentation APK that
uses UIAutomator to inspect Quest Android panels. It is intended for development
sweeps of system dialogs and 2D panel surfaces, not as a runtime dependency of
the questionnaire panel.

Build:

```powershell
gradle :examples:quest-ui-automation:assembleDebug :examples:quest-ui-automation:assembleDebugAndroidTest
```

Install:

```powershell
adb install -r examples\quest-ui-automation\build\outputs\apk\debug\quest-ui-automation-debug.apk
adb install -r examples\quest-ui-automation\build\outputs\apk\androidTest\debug\quest-ui-automation-debug-androidTest.apk
```

Run the passive Metacam sharing-panel sweep:

```powershell
adb shell am instrument -w `
  -e scenario metacamPanel `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run a passive surface map. This records the UIAutomator XML hierarchy,
accessibility-window summaries, display/window state, scrollable nodes,
checked switches, and node action lists:

```powershell
adb shell am instrument -w `
  -e scenario surfaceMap `
  -e surface metacamDeepSettings `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run a passive system-surface reachability sweep. This attempts each named
surface through the same safe entry points as `surfaceMap`, records structural
counts, dumps accessibility windows, and keeps only redacted counts in the
exporter summary:

```powershell
adb shell am instrument -w `
  -e scenario systemSurfaceReachability `
  -e surfaces current,quickSettings,notifications,androidSettings,metacamPanel `
  -e waitAfterSurfaceMs 1000 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run a scroll strategy comparison on the deeper Metacam settings panel:

```powershell
adb shell am instrument -w `
  -e scenario scrollProbe `
  -e surface metacamDeepSettings `
  -e strategies uiScrollable,uiObject2,accessibilityAction,shellScroll,keyScroll `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run a Quest Settings side-nav sweep. This opens `android.settings.SETTINGS`,
clicks named top-level settings sections, scrolls the side nav when needed, and
dumps each reached section:

```powershell
adb shell am instrument -w `
  -e scenario settingsNavProbe `
  -e navTargets general,notifications,display_brightness,audio,camera,accessibility,developer,help `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run a non-mutating Quest Settings section crawler. This uses the same side-nav
routes, then scrolls the main settings content recycler and records each page's
visible labels, checked nodes, clickable nodes, route inventory candidates, and
XML dump:

```powershell
adb shell am instrument -w `
  -e scenario settingsSectionCrawler `
  -e navTargets display_brightness,audio,camera,developer,help `
  -e maxSectionScrolls 4 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run an allowlisted child-page probe. This opens a settings section, searches the
main content pane for a named row, clicks only a non-toggle row target, dumps the
child page or selector surface, then presses Back:

```powershell
adb shell am instrument -w `
  -e scenario settingsChildPageProbe `
  -e childTargets "camera:Bit rate,camera:Frame rate,camera:Image stabilization,camera:Eye perspective,help:Help & Tips app,privacy_safety:Device permissions" `
  -e clickModes coordinate `
  -e childTargetRole row `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run a MediaProjection consent-prompt probe. The cancel path records the prompt
and returns without granting result data:

```powershell
adb shell am instrument -w `
  -e scenario mediaProjectionPrompt `
  -e temporaryAppOpMode default `
  -e restoreAppOp true `
  -e tapChoice cancel `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

The positive path selects the full-view target first, then taps Share. The
probe Activity records whether `onActivityResult` returned `RESULT_OK` and
result data, but it does not call `getMediaProjection()` or create a virtual
display:

```powershell
adb shell am instrument -w `
  -e scenario mediaProjectionPrompt `
  -e temporaryAppOpMode default `
  -e restoreAppOp true `
  -e selectionChoice entire `
  -e tapChoice share `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Useful scroll-probe extras:

```text
surface=current|metacamPanel|metacamSettings|metacamDeepSettings|metacamAdvancedSettings|quickSettings|notifications|androidSettings
strategies=all|uiScrollable|uiScrollableFling|uiObject2|uiObject2UntilEnd|accessibilityAction|deviceSwipe|deviceDrag|toolTypeSwipe|shellSwipe|shellScroll|keyScroll
targetRegex=<text/resource/class regex>
targetResRegex=<resource-id regex>
direction=down|up|left|right
displayIds=<comma-separated Android display IDs for shell input>
inputSource=touchscreen|touchpad|rotaryencoder|mouse
axisValues=-3.0;3.0
```

Useful system-surface reachability extras:

```text
surfaces=current|quickSettings|notifications|androidSettings|metacamPanel|metacamSettings|metacamDeepSettings|metacamAdvancedSettings[, ...]
waitAfterSurfaceMs=<milliseconds>
```

The default surface list avoids deeper Metacam menu taps. Add
`metacamSettings`, `metacamDeepSettings`, or `metacamAdvancedSettings` only for
a scoped capture-settings reachability pass.

Useful settings-nav extras:

```text
navTargets=quest_link,general,action_button,notifications,space_setup,world_movement,movement_tracking,accessibility,display_brightness,audio,camera,privacy_safety,passcode_security,experimental,developer,help
maxNavScrolls=8
resetSettingsEachTarget=true|false
```

Useful section-crawler extras:

```text
navTargets=<same target names as settingsNavProbe>
maxNavScrolls=10
maxSectionScrolls=4
mainCoordinateFallback=false|true
```

Keep `mainCoordinateFallback=false` unless you intentionally want to test
coordinate swipes inside the main settings content area. The default crawler
uses object scrolling and does not toggle controls.

Useful MediaProjection prompt extras:

```text
temporaryAppOpMode=default|allow|ignore
restoreAppOp=true|false
selectionChoice=none|entire|app|first
tapChoice=none|cancel|share|first
waitForPromptMs=<milliseconds>
waitAfterTapMs=<milliseconds>
```

Use `temporaryAppOpMode=default` when you need to force the visible consent
prompt after a development pregrant. With `restoreAppOp=true`, the probe
restores the prior app-op mode after the run.

Each crawler page emits a `settings_section_route_inventory` event. It
classifies exposed route-like controls as `child_page`, `dropdown`, `button`,
or `dropdown_option`, records the nearby row texts and compact node evidence,
and assigns a conservative risk bucket such as `open_dump_only`,
`sensitive_open_dump_only`, `open_selector_only`, `possible_mutation`,
`mutation_or_security_sensitive`, or `external_surface`. The event is passive;
it does not click the candidates.

Useful child-page extras:

```text
childTargets=<section>:<label>[,<section>:<label>...]
clickModes=coordinate|uiObject2|accessibilityClick|accessibilityExpand[, ...]
childTargetRole=row|dropdown
maxNavScrolls=10
maxContentScrolls=4
dumpChildAccessibility=false|true
optionTarget=<literal or regex:...>
optionTargets=<label=option;section:label=option;...>
allowOptionSelect=false|true
optionClickMode=coordinate|uiObject2|accessibilityClick
```

Labels are treated as literal text unless prefixed with `regex:`. The probe
intentionally skips checkable/toggle nodes and `Reset all to default`.
`coordinate` clicks the XML target bounds through `UiDevice.click`. `uiObject2`
matches the same row back to a live `UiObject2` and calls `click()`.
`accessibilityClick` and `accessibilityExpand` match a live
`AccessibilityNodeInfo` and call `ACTION_CLICK` or `ACTION_EXPAND`. Run
multiple modes in one command when a row is visible but does not open a
distinct child or selector surface through coordinate tapping. Keep
`dumpChildAccessibility=false` for broad multi-mode sweeps; set it to `true`
only for a focused row when the compact XML/UI summaries are not enough.

Use `childTargetRole=dropdown` for settings rows with a visible
`dropdown_button` or `Spinner`. This targets the selector control on the same
row instead of the broader `settings_list_item` container.

Focused camera dropdown sweep:

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

The 2026-06-14 Quest sweep exposed `context_menu_list` options for those
camera dropdowns without selecting a different value. Verified options include
aspect ratio `Square 1:1`, `Landscape 16:9 (Default)`, and `Portrait 9:16`;
bit rate `3 mbps (Default)`, `6 mbps`, `9 mbps`, and `14 mbps`; frame rate
`30 fps (Default)` and `60 fps`; image stabilization `Off (Default)`, `Low`,
`Medium`, and `High`; and eye perspective `Left eye (Default)` and
`Right eye`.
Each `settings_child_surface.summary` includes `settingsDropdownOptions` when a
dropdown is open. The option rows include `texts`, bounds, `selected`,
`checked`, and `hasDefaultMarker` fields.

Run a guarded option-target dry run. This opens each dropdown, finds the named
option row, records the option bounds/state, and refuses to click while
`allowOptionSelect=false`:

```powershell
$instrument = "am instrument -w " +
  "-e scenario settingsChildPageProbe " +
  "-e childTargets 'camera:Aspect ratio,camera:Bit rate,camera:Frame rate,camera:Image stabilization,camera:Eye perspective' " +
  "-e childTargetRole dropdown " +
  "-e clickModes coordinate " +
  "-e optionTargets 'Aspect ratio=Portrait 9:16;Bit rate=9 mbps;Frame rate=60 fps;Image stabilization=High;Eye perspective=Right eye' " +
  "-e allowOptionSelect false " +
  "-e maxContentScrolls 4 -e maxNavScrolls 10 " +
  "io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner"
adb shell $instrument
```

Only pass `allowOptionSelect=true` in a scoped mutation run where the current
value, desired value, and rollback step are documented first.

On the Quest 3S OS build tested on 2026-06-14, `adb shell input` did not list a
`scroll` command or a `rotaryencoder` source. `shellScroll` is kept as a probe
for newer Android builds, but the reliable settings scroll paths were
`uiObject2`, `accessibilityAction`, and `shellSwipe` with `displayIds=0`.

For shell `uiautomator dump` parity, run the command printed in the
`shell_uiautomator_dump_external` report row from the host with `adb shell`.
Direct host ADB can write the compressed XML to `/data/local/tmp`; invoking the
shell dumper from inside an active instrumentation did not create a file
reliably on the tested Quest build.

The sweep writes JSONL and XML evidence under the automation app's external
files directory on the headset:

```text
/sdcard/Android/data/io.github.mesmerprism.questquestionnaire.questuiautomation/files/sweeps/
```

Summarize pulled `report.jsonl` files before copying findings into public
docs. The host-side exporter emits only a low-cardinality summary: event
counts, page counts, scroll endpoints, allowlisted settings labels, dropdown
option rows, child-page skip reasons, and redaction counts. It omits raw XML
paths, local paths, package/resource IDs, and non-allowlisted labels such as
installed app names:

```powershell
python examples\quest-ui-automation\tools\summarize_report.py `
  .\artifacts\quest-uiautomator\report.jsonl `
  --format markdown
```

Use `--format json` when another script should ingest the public-safe summary.
Use `--format child-targets` on a route-inventory report to generate a
comma-separated `childTargets` list for a follow-up `settingsChildPageProbe`.
The default planner includes public-safe `child_page` routes with
`open_dump_only` risk and excludes default-blocked rows such as Software update
and Cloud backup. Repeat `--child-risk <risk>` to include another risk bucket;
pass `--include-default-blocked-child-labels` only for a scoped lab run.

Route-inventory-to-child-probe flow:

```powershell
$targets = python examples\quest-ui-automation\tools\summarize_report.py `
  .\artifacts\quest-uiautomator\section-crawl-report.jsonl `
  --format child-targets

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

The 2026-06-14 focused General-section route plan produced Quick controls,
Storage, and Ongoing activities as default child targets. A second low-risk
route plan over Environment setup, Accessibility, and Audio produced Boundary,
Travel mode, Vision, Mobility, Hearing, and Spatial audio for windows. Compact
`settingsChildPageProbe` runs opened each page and returned, and the exporter
summarized the child surfaces with safe labels plus redaction counts.

Pass generated targets to the remote shell as one quoted command string; labels
such as `Quick controls` and `Spatial audio for windows` contain spaces.

If Quest Settings launches through the VRShell relay but UIAutomator sees no
accessibility nodes, settings nav/section/child probes emit an explicit skip
row such as `prepared settings surface had zero nodes`. Treat that as a
headset visibility or sleeping-surface state, not proof that the route is
unsupported.

Run the passive Settings recovery probe when you need to characterize that
zero-node state. It opens Settings, records whether the accessibility tree is
visible, takes passive current-window/window-display baselines if the tree is
empty, then retries the same Settings intent without force-stopping packages or
changing settings:

```powershell
adb shell am instrument -w `
  -e scenario settingsRecoveryProbe `
  -e retryCount 2 `
  -e retryWaitMs 1500 `
  -e dumpPassiveBaselines true `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

The exporter also summarizes `currentWindow` and `surfaceMap` reports. For
those baseline sweeps it emits structural counts only: XML node counts,
clickable/scrollable counts, display IDs, package counts, accessibility window
counts, and action-node counts. It does not emit package names, raw UI text,
window titles, XML paths, or shell command output.

For `scrollProbe` reports, the exporter emits strategy names, key codes,
attempt counts, before/after node counts, visible-hash changed status, and the
count of newly visible texts without emitting the raw texts or shell output.
Focused key-scroll sweeps on 2026-06-14 showed `KEYCODE_DPAD_DOWN` and
`KEYCODE_SPACE` can change focus/search state, while `KEYCODE_PAGE_DOWN` and
`KEYCODE_TAB` did not change the visible hash. Prefer `uiObject2` or
`accessibilityAction` for actual settings-list scrolling.

For `mediaProjectionPrompt` reports, the exporter emits selection roles,
enabled/disabled approval-button state, tap roles, result-state booleans, and
Activity lifecycle event names. It omits raw prompt text, package/window names,
coordinates, token contents, and shell command output.

Active tapping is disabled by default. To test whether a visible Android button
can be pressed through UIAutomator, pass a specific regex and a small tap limit:

```powershell
adb shell am instrument -w `
  -e scenario metacamPanel `
  -e tapRegex "(?i)record|video" `
  -e tapLimit 1 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Run two sequential taps, for example open the Metacam camera settings menu and
then the deeper settings view:

```powershell
adb shell am instrument -w `
  -e scenario metacamPanel `
  -e tapRegex camera_settings_dropdown_button `
  -e tapLimit 1 `
  -e tapRegex2 camera_settings_menu_more_settings `
  -e tapLimit2 1 `
  -e swipeUpCount 1 `
  -e scrollSettingsForwardCount 1 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

Only use active tapping when you have inspected the passive node dump and know
how to stop or undo the workflow. UIAutomator can drive Android UI nodes that
are exported to the accessibility tree; it cannot inspect Unity/OpenXR-rendered
scene internals or compositor-only VR controls.

Run the guarded built-in recorder start/stop probe:

```powershell
adb shell am instrument -w `
  -e scenario metacamRecordProbe `
  -e recordMs 6000 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

This probe taps the visible `com.oculus.metacam:id/screenrecording_button`,
waits briefly, relaunches the Metacam sharing panel, and taps the same exposed
button again. It records only text/XML sweep evidence; do not pull generated
MP4 media into this public repo. For the BRB plus panel public showcase, use
the one-take bracket and clip map in
[`docs/demo-capture-workflow.md`](../../docs/demo-capture-workflow.md).
