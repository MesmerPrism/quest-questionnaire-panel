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
visible labels, checked nodes, clickable nodes, and XML dump:

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

Useful child-page extras:

```text
childTargets=<section>:<label>[,<section>:<label>...]
clickModes=coordinate|uiObject2|accessibilityClick|accessibilityExpand[, ...]
childTargetRole=row|dropdown
maxNavScrolls=10
maxContentScrolls=4
dumpChildAccessibility=false|true
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
  -e childTargets "camera:Bit rate,camera:Frame rate,camera:Image stabilization,camera:Eye perspective" `
  -e childTargetRole dropdown `
  -e clickModes "coordinate,uiObject2,accessibilityClick" `
  -e maxContentScrolls 4 `
  -e maxNavScrolls 10 `
  -e dumpChildAccessibility false `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

The 2026-06-14 Quest sweep exposed `context_menu_list` options for those
camera dropdowns without selecting a different value.
Each `settings_child_surface.summary` includes `settingsDropdownOptions` when a
dropdown is open. The option rows include `texts`, bounds, `selected`,
`checked`, and `hasDefaultMarker` fields.

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
MP4 media into this public repo.
