# Quest UIAutomator Capability Map

Last updated: 2026-06-14

This document is the running map for using Android UIAutomator and related ADB
input paths to inspect and drive Meta Quest system UI surfaces. The immediate
use case is capture/onboarding work around the built-in recorder and media
projection prompts, but the broader goal is to build a reusable command
database for headset settings navigation.

Machine-readable command records live in
`docs/quest-uiautomator-command-database.jsonl`. Keep that JSONL in sync when a
sequence changes from planned to working, unreliable, unsupported, or unsafe.

The working assumption is conservative: UIAutomator can only see Android views
exported through the accessibility tree. It cannot inspect Unity/OpenXR scene
internals, compositor-only controller UI, or protected surfaces that Meta keeps
outside Android accessibility. Whenever a command changes headset state, record
the before/after UI dump and keep media, screenshots, serials, and private logs
out of this public repo.

## Source Map

Android UIAutomator sources:

- [Modern UIAutomator guide](https://developer.android.com/training/testing/other-components/ui-automator):
  UIAutomator 2.4 adds a Kotlin-oriented API with predicate-based element
  queries, explicit app/window state waits, multi-window access, screenshots,
  and results reporting.
- [Legacy UIAutomator guide](https://developer.android.com/training/testing/other-components/ui-automator-legacy):
  the stable AndroidJUnitRunner path can interact with visible system and app
  UI outside the target app process. It covers `UiDevice`, `UiObject2`,
  `BySelector`, system settings, notification shade, quick settings, wait
  helpers, and `UiObject2.scrollUntil`.
- [UiDevice API](https://developer.android.com/reference/androidx/test/uiautomator/UiDevice):
  supports hierarchy dumps, shell commands, screenshots, hardware-key style
  input, drag, and coordinate swipes on the default display.
- [UiObject2 API](https://developer.android.com/reference/androidx/test/uiautomator/UiObject2):
  exposes center clicks, point clicks, click-and-wait helpers, drag, swipe, and
  scroll operations on matched accessibility-backed UI objects.
- [UiScrollable API](https://developer.android.com/reference/androidx/test/uiautomator/UiScrollable):
  supports vertical/horizontal list scrolling, forward/backward scrolls,
  flings, scroll-to-end/beginning, and scroll-into-view helpers.
- [Configurator API](https://developer.android.com/reference/androidx/test/uiautomator/Configurator):
  exposes motion-event tool type configuration, scroll/action timeouts, wait
  timeouts, and UIAutomator 2.4 default display ID selection.
- [AccessibilityAction API](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction):
  Android views can expose click, expand/collapse, page, and scroll actions
  such as `ACTION_SCROLL_FORWARD` and `ACTION_SCROLL_DOWN`.
- [AOSP input shell command](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/input/InputShellCommand.java):
  `adb shell input` accepts input source, `-d DISPLAY_ID`, tap, swipe, drag,
  motionevent, scroll, and keycombination commands. This is important on Quest
  because panels often live on non-default display IDs.
- [AOSP uiautomator dump command](https://android.googlesource.com/platform/frameworks/testing/+/jb-mr2-release/uiautomator/cmds/uiautomator/src/com/android/commands/uiautomator/DumpCommand.java):
  the shell `uiautomator dump` path writes an XML accessibility hierarchy and
  can be compared with instrumentation `dumpWindowHierarchy`.
- [AOSP AccessibilityNodeInfo dumper](https://android.googlesource.com/platform/frameworks/testing/+/2b6d1da16a4e38a9704c2c67b33aadf44a85b1d2/uiautomator/library/core-src/com/android/uiautomator/core/AccessibilityNodeInfoDumper.java):
  confirms the XML dump is generated from accessibility nodes, not view-private
  app state.

Meta Quest sources:

- [Screenshots and Video Capture](https://developers.meta.com/horizon/documentation/native/android/mobile-testing-capture/):
  the headset sharing menu records video until the user stops recording, the
  headset is unmounted, or protected content blocks capture.
- [Capture MR and VR Apps for Publishing](https://developers.meta.com/horizon/resources/video-capture-mr-vr/):
  documents headset Camera app settings such as aspect ratio, frame rate,
  bitrate, image stabilization, and eye preference.
- [Configure Android System Properties on Meta Quest](https://developers.meta.com/horizon/documentation/unity/ts-systemproperties/):
  documents development-only capture properties including
  `debug.oculus.enableVideoCapture`, `debug.oculus.capture.width`,
  `debug.oculus.capture.height`, `debug.oculus.capture.bitrate`, and
  `debug.oculus.fullRateCapture`.
- [Meta Quest scriptable testing services](https://developers.meta.com/horizon/documentation/android-apps/ts-scriptable-testing/):
  exposes ADB-backed developer testing toggles for some blocking dialogs,
  boundary, auto-sleep, reset, Wi-Fi, and test-user setup. Do not use reset or
  disruptive toggles unless explicitly requested. The public provider route is
  `content://com.oculus.rc`; `GET_PROPERTY` is read-only, while
  `SET_PROPERTY`, `WIPE_DEVICE`, and `SETUP_FOR_TEST` change device state.
- [MQDH media tools](https://developers.meta.com/horizon/documentation/unity/ts-mqdh-media/):
  records headset video for up to three minutes, without audio, and stores the
  capture on the host machine.

## Current Quest Findings

Observed with the development-only `examples:quest-ui-automation` module:

- The Metacam sharing panel can be launched directly:
  `am start -W -n com.oculus.metacam/com.oculus.panelapp.sharing.SharingPanelActivity`.
- UIAutomator can see and click
  `com.oculus.metacam:id/screenrecording_button`.
- Tapping that button starts the built-in recorder; relaunching the sharing
  panel and tapping the same exposed button stops it.
- While recording, the tile text changes to `Recording` and an active indicator
  node appears as `com.oculus.metacam:id/octile_active_indicator_view`.
- `dumpsys media_projection` stayed empty before, during, and after the built-in
  recorder probe, so the built-in recorder does not look like a normal
  app-visible MediaProjection session.
- The Metacam settings path exposed mic-audio, camera view, aspect-ratio, red
  recording indicator, hide-controls, and capture-marker settings. Resolution,
  bitrate, frame-rate, and eye preference were not yet found through the first
  settings sweeps.
- Scrolling is partly solved:
  - `UiObject2.scroll(Direction.DOWN, ...)` on
    `com.oculus.panelapp.settings:id/settings_recycler_view` works.
  - Direct accessibility `ACTION_SCROLL_FORWARD` on the same node works.
  - Both paths revealed advanced capture settings including `Format and
    quality`, `Bit rate`, `Frame rate`, `Image stabilization`, and `Eye
    perspective`.
  - Legacy `UiScrollable` remained unreliable on the same resource ID and
    threw `UiObjectNotFoundException` in one focused probe.
  - Plain `UiDevice.swipe` and `Configurator.setToolType(...)+swipe` reported
    success but did not reliably change visible settings content.
- This Quest OS build exposes an older `adb shell input` surface: `tap`,
  `swipe`, `draganddrop`, `motionevent`, `keyevent`, `roll`, and
  `keycombination` are present, but `scroll` is not listed and `rotaryencoder`
  is not an accepted input source.
- Direct host `adb shell uiautomator dump --compressed /data/local/tmp/...xml`
  works and writes a compressed XML hierarchy. Running shell `uiautomator dump`
  from inside an active instrumentation did not create the expected file in the
  tested Quest run, so keep shell-dump parity as a host-side command.
- Display-targeted shell swipes are partially useful. In single-display probes,
  `input touchscreen -d 0 swipe 900 720 900 240 500` moved the active settings
  list and revealed advanced capture settings. `-d 43` changed visible content
  too, but the new nodes appeared to come from the background Store panel in
  that run. `-d 25`, `-d 45`, and `-d 9` did not change the target state.
- `am start -W -a android.settings.SETTINGS` opens Meta's Quest settings panel
  (`com.oculus.panelapp.settings`) with side-nav entries including Link,
  General, Action button, Notifications, Environment setup, Movement, Tracking,
  Accessibility, and Display & brightness. The active settings window exposes
  scroll actions on both the side nav and the main settings recycler.
- The `settingsNavProbe` scenario can open Quest settings and click top-level
  side-nav sections by resource ID without toggling controls. It reached Link,
  General, Action button, Notifications, Environment setup, Movement, Tracking,
  Accessibility, Display & brightness, Audio, Camera, Privacy & safety,
  Passcode & security, Experimental, Developer, and Help in 2026-06-14 sweeps.
- Side-nav scrolling has mixed behavior. AndroidX `UiObject2.scroll` works for
  some side-nav positions, but bottom items such as Camera, Developer, and Help
  required a side-nav coordinate-swipe fallback on the tested state. Resource-id
  matching is required; generic text matching can accidentally click section
  headers rather than side-nav items.
- Environment setup opened but reported that Space setup is unavailable while
  the current apps are open. Treat that as a reachable UI surface, not proof
  that boundary/space setup can be changed safely from automation.
- The `settingsSectionCrawler` scenario opens a side-nav section, records the
  main settings content pane, and scrolls `settings_recycler_view` without
  toggling controls. It filters page summaries to
  `com.oculus.panelapp.settings` content nodes so background Store/Metacam
  panels do not dominate the report.
- Main-content crawling found multi-page sections for Camera and Experimental:
  - Camera page 0 shows recording indicator, hidden captured controls, capture
    markers, auto-hide, aspect ratio, and audio headers.
  - Camera page 1 adds microphone audio and bit-rate controls.
  - Camera page 2 adds frame rate, image stabilization, eye perspective, and
    video coding format controls.
  - Experimental page 0 shows reset, hidden camera/call controls, external
    microphone, screen reader, adaptive brightness, and temporal dimming.
  - Experimental page 1 adds positional time warp, lying-down apps, Wi-Fi QR,
    seamless multitasking, and surface-keyboard entries.
- Developer, Help, Privacy & safety, Passcode & security, Tracking, and Link
  fit on one main-content page in the tested state. They are reachable and
  dumpable, but security/privacy/developer sections should remain open/dump only
  unless a future test explicitly scopes a setting change.
- The `settingsChildPageProbe` scenario can open allowlisted drill-down rows and
  return with Back. It only clicks non-checkable main-content rows and skips
  toggles/reset controls.
- Privacy & safety child pages are reachable: `Device permissions` opens a
  child page with hand/body tracking, location services, spatial data, enhanced
  spatial services, and spatial-data deletion controls; `App permissions` opens
  app permission categories such as audio files, connected cameras, headset
  cameras, location, microphone, nearby devices, and photos/videos.
- Camera selector rows such as `Bit rate`, `Frame rate`, `Image stabilization`,
  and `Eye perspective` have two distinct target levels. Clicking the broad
  `settings_list_item` row activates successfully through `coordinate`,
  `uiObject2`, and `accessibilityClick`, but does not expose a distinct options
  surface. Targeting the row's `dropdown_button`/`android.widget.Spinner`
  control with `childTargetRole=dropdown` does expose
  `com.oculus.panelapp.settings:id/context_menu_list` options:
  bit rate `3 mbps (Default)`, `6 mbps`, `9 mbps`, `14 mbps`; frame rate
  `30 fps (Default)`, `60 fps`; image stabilization `Off (Default)`, `Low`,
  `Medium`, `High`; eye perspective `Left eye (Default)`, `Right eye`.
  Coordinate, `UiObject2.click()`, and accessibility `ACTION_CLICK` all opened
  the dropdowns in the 2026-06-14 focused sweep.
- Dropdown surfaces now emit compact `settingsDropdownOptions` summaries in
  each `settings_child_surface.summary`. The verifier records one row per
  `context_menu_item`, using `item_title` text plus bounds, `selected`,
  `checked`, and `hasDefaultMarker` fields. A compact 2026-06-14 verification
  sweep produced clean option rows for all four camera dropdowns.
- The child-page probe now supports guarded dropdown option targeting. Pass a
  single `optionTarget` or a per-row `optionTargets` map to identify an option
  by text after the dropdown opens. The default is a dry run:
  `allowOptionSelect=false` records the option row, bounds, selected/checked
  state, and refusal reason without clicking it. A 2026-06-14 sweep verified
  dry-run matches for bit rate `9 mbps`, frame rate `60 fps`, image
  stabilization `High`, and eye perspective `Right eye`; none were selected.
- A full non-mutating section crawler pass reached endpoints for all known
  top-level Quest Settings side-nav sections with object scrolling only.
  Most sections fit on one main-content page. Multi-page sections observed in
  the tested state were Camera (3 pages), Movement (3 pages), Experimental
  (2 pages), and Notifications (6 pages). The focused Notifications rerun
  reached a true no-move endpoint after five successful main-content scrolls.
  Do not commit per-app notification names from raw local reports; summarize
  that surface as categories plus per-app notification rows.
- `examples/quest-ui-automation/tools/summarize_report.py` converts pulled
  `report.jsonl` files into public-safe Markdown or JSON. It emits event
  counts, settings page counts, scroll endpoint status, allowlisted settings
  labels, dropdown option rows, and redaction counts while omitting raw XML
  paths, local paths, package/resource IDs, and unknown labels such as installed
  app names.
- Section crawler pages now emit a passive `settings_section_route_inventory`
  event. A 2026-06-14 focused sweep verified the cleaned classifier against
  General, Camera, Privacy & safety, and Help: General exposed 8 `child_page`
  candidates, Camera exposed 10 `dropdown` candidates across 3 pages, Privacy &
  safety exposed 8 `child_page` candidates marked `sensitive_open_dump_only`,
  and Help exposed 3 `child_page` candidates marked `external_surface`. The
  exporter summarized the route inventory with safe labels and redaction counts
  without emitting raw XML paths or unknown labels.
- The report exporter can now convert route inventory into a comma-separated
  `settingsChildPageProbe` target list with `--format child-targets`. The
  default planner keeps only public-safe `child_page` routes in the
  `open_dump_only` bucket and excludes default-blocked General rows such as
  Software update and Cloud backup. A 2026-06-14 constrained General-section
  probe opened Quick controls, Storage, and Ongoing activities with coordinate
  row clicks, compact dumps, and Back return; all three reached distinct child
  content and the public summary emitted only safe labels plus redaction counts.
- A second 2026-06-14 low-risk route-plan pass over Link, Action button,
  Environment setup, Movement, Tracking, Accessibility, Display & brightness,
  and Audio produced six default child targets: Boundary, Travel mode, Vision,
  Mobility, Hearing, and Spatial audio for windows. The follow-up compact
  child-page probe opened all six with coordinate row clicks and Back return;
  each reached distinct child content, with only safe labels and redaction
  counts carried into public notes.
- Help rows have side effects beyond an in-panel child page. `Help & Tips app`
  opened `com.oculus.helpcenter` in the tested action-mode sweep. `Support`
  also brought Help Center content into the UI dump and exposed SystemUX
  support/report-dialog text. Treat Help rows as active external surfaces, not
  passive settings child pages.
- The child-page probe supports alternate action modes for inconclusive rows:
  `coordinate`, `uiObject2`, `accessibilityClick`, and `accessibilityExpand`.
  Broad multi-mode sweeps should keep full accessibility-state dumping off and
  use compact XML/UI summaries, because full accessibility dumps made the first
  combined action-mode run very large.
- `UiDevice.openQuickSettings()` and `UiDevice.openNotification()` did not
  reveal a distinct Quest quick-settings or notification surface in the current
  panel state; dumps remained dominated by the existing Metacam/settings/store
  windows. Treat those APIs as not yet proven on Quest.
- The Meta Quest scriptable testing provider is present as
  `content://com.oculus.rc`, implemented by
  `oculus.platform/oculus.internal.rc.RemoteControlProvider` on the tested
  headset. `GET_PROPERTY` returned a bundle with the supported state keys
  `disable_guardian`, `set_proximity_close`, `disable_dialogs`, and
  `disable_autosleep`. An unknown method returned a structured failure bundle
  rather than changing state. Mutating methods from the official docs were not
  run in this pass.
- The `currentWindow` and `surfaceMap` scenarios now have a redacted exporter
  path for baseline/window/action-map evidence. A 2026-06-14 passive current
  sweep recorded 308 XML nodes, 68 clickable nodes, 2 scrollable nodes, and 5
  display IDs in the instrumentation hierarchy. The accessibility window map
  found 6 application windows on display 0; the active root had 101 nodes, 1
  scrollable, and action lists on 101 nodes. A host-side compressed
  `uiautomator dump` wrote and parsed successfully with 57 XML nodes, confirming
  the shell route works but has different coverage from instrumentation.
- Key-event scrolling is mapped as a fallback navigation signal, not a
  reliable settings-list scroll primitive. In a 2026-06-14 focused
  `metacamDeepSettings` run, six `KEYCODE_DPAD_DOWN` events and six
  `KEYCODE_SPACE` events changed the visible-text hash, but the resulting
  surfaces looked like focus/search state changes rather than clean content
  scrolling. Six `KEYCODE_PAGE_DOWN` events and six `KEYCODE_TAB` events did
  not change the visible hash.

## Capability Taxonomy

Discovery:

- `adb shell uiautomator dump [--compressed] /sdcard/...xml`.
- Instrumentation `UiDevice.dumpWindowHierarchy(File)`.
- Instrumentation `UiAutomation.getRootInActiveWindow()`.
- Instrumentation `UiAutomation.getWindows()` for window bounds, layer, type,
  display ID, and root node coverage.
- XML parsing for `text`, `content-desc`, `resource-id`, `class`, `package`,
  `bounds`, `clickable`, `enabled`, `scrollable`, `selected`, `checked`, and
  `focusable`.
- Accessibility-node action lists for click, long-click, expand/collapse,
  scroll, page, and custom actions.

Selection:

- Resource ID selectors are most stable when Meta exposes them.
- Text and content descriptions are useful but likely to drift with headset OS
  releases, locale, and A/B UI changes.
- Class/package/bounds filters are useful fallback selectors.
- Display ID needs explicit capture because Quest panels can appear on multiple
  Android displays.
- Checked/enabled/selected flags should be recorded for switches and segmented
  controls before any toggle test.
- For Quest settings rows, distinguish the broad row container from compact
  row controls. `settings_list_item` can be safe for child pages, but
  `dropdown_button`/`android.widget.Spinner` is the working target for selector
  option popovers.

Input/action routes:

- UIAutomator `UiObject2.click`, `longClick`, `drag`, `swipe`, and
  `scrollUntil`.
- Legacy `UiScrollable.scrollForward`, `scrollBackward`, `flingForward`,
  `flingBackward`, `flingToEnd`, `flingToBeginning`, `scrollTextIntoView`, and
  `scrollIntoView`.
- Accessibility `performAction` on scrollable nodes, especially
  `ACTION_SCROLL_FORWARD`, `ACTION_SCROLL_DOWN`, `ACTION_PAGE_DOWN`, and the
  reverse actions.
- `UiDevice.click`, `drag`, and coordinate `swipe` on the default display.
- `adb shell input` with source and display ID, especially:
  `input -d <display> touchscreen swipe ...`,
  `input -d <display> touchpad swipe ...`,
  `input -d <display> rotaryencoder scroll --axis VSCROLL,<value>`, and
  `input -d <display> motionevent ...`.
- Hardware-key style input: Back, Home, Menu, Enter, D-pad, Tab, Space, and
  key combinations where safe.

Wait/stability:

- Always dump before and after active commands.
- Prefer click-and-wait or wait-for-window-change where possible.
- Record focus summaries before and after.
- If a command returns success without visual movement, treat it as an input
  injection success, not as a UI behavior success.

## Test Matrix

| ID | Area | Command or scenario | Evidence | Risk | Status |
| --- | --- | --- | --- | --- | --- |
| R-001 | Baseline | `currentWindow` dump of current headset state | XML, JSONL, focus | Passive | Working |
| R-002 | Shell dump parity | Compare `uiautomator dump --compressed` and instrumentation XML | XML diff summary | Passive | Working host-side; coverage differs from instrumentation |
| R-003 | Window map | Dump `UiAutomation.getWindows()` with display IDs, types, layers, bounds | JSONL | Passive | Working |
| R-004 | Action map | Walk active/window roots and record each node's accessibility action list | JSONL | Passive | Working |
| R-005 | Metacam launch | Launch sharing panel and dump candidates | XML, JSONL | Passive | Working |
| R-006 | Recorder tile | Tap `screenrecording_button`, wait, relaunch, tap stop | JSONL, media file listing only | Active recording | Working |
| S-001 | UiScrollable settings | Use resource-id `settings_recycler_view` with scroll/fling variants | Before/after XML | Low | Partial, unreliable |
| S-002 | UiObject2 scroll | Find `By.scrollable(true)` and call `scroll(Direction.DOWN, ...)` | Before/after XML | Low | Works for settings recycler |
| S-003 | Accessibility scroll | `performAction` scroll/page actions on nodes exposing those actions | Before/after XML | Low | Works for settings recycler |
| S-004 | Display-target input scroll | Try `adb shell input -d <display> ... scroll/swipe` using observed display IDs | Before/after XML | Low | Swipe partly works; shell `scroll` absent |
| S-005 | Tool type | Try UIAutomator `Configurator.setToolType` finger/stylus/mouse for swipes | Before/after XML | Low | No visible improvement yet |
| S-006 | D-pad/key scroll | Try safe D-pad, Tab, Space, and Page-like key events on settings panels | Before/after XML | Low | Mapped; useful as navigation/focus signal, not reliable scroll |
| M-001 | Camera settings | Navigate sharing panel -> settings dropdown -> more settings | XML, node DB | Low | Working |
| M-002 | Advanced video settings | Search for frame rate, bitrate, stabilization, eye preference | XML, node DB | Passive | Found after scroll |
| M-003 | Red dot setting | Locate red recording indicator toggle but do not toggle by default | XML, checked state | Passive | Found |
| Q-001 | Quick settings | Use UIAutomator `openQuickSettings` and/or Home/Sharing path, dump nodes | XML, node DB | Low | API did not reveal Quest quick settings |
| Q-002 | Notification shade | Try `openNotification`, dump nodes | XML, node DB | Low | API did not reveal distinct notification surface |
| Q-003 | Android settings intents | Start `android.settings.SETTINGS` and Quest-specific settings activities found by package query | XML, node DB | Low | Generic settings intent opens Quest settings |
| Q-004 | Permission dialogs | Use MediaProjection consent as known prompt and record selector/tap behavior | XML, JSONL | Consent UI | Planned |
| Q-005 | Scriptable testing services | Query supported Meta testing properties, read only unless explicitly changing | Command output | Passive for `GET_PROPERTY`; mutating for `SET_PROPERTY`/reset/setup | Working read-only; mutating methods documented but not run |
| Q-006 | Settings side-nav map | Open Quest settings and click top-level side-nav entries by resource ID | XML, JSONL | Low, privacy-sensitive labels | Working for 16 sections |
| Q-007 | Settings section crawler | Open settings sections and scroll main `settings_recycler_view` without toggles | XML, JSONL | Low, privacy-sensitive labels | Working for selected sections |
| Q-008 | Settings child-page probe | Click allowlisted non-toggle rows and dump child pages/selectors | XML, JSONL | Low to sensitive, allowlist only | Privacy child pages work; camera/help inconclusive |
| Q-009 | Settings child-row action modes | Re-run allowlisted rows with `coordinate`, `uiObject2`, `accessibilityClick`, and `accessibilityExpand` | XML, JSONL action outcomes | Low to sensitive, allowlist only | Partial: camera rows still no selector; Help rows open external surfaces |
| Q-010 | Settings dropdown targets | Target `dropdown_button`/`Spinner` controls with `childTargetRole=dropdown` | XML, JSONL option texts | Low, do not select options | Working for camera bit rate, frame rate, stabilization, eye perspective |
| Q-011 | Dropdown option inspector | Record `context_menu_item` option text, bounds, selected, checked, and default-marker state | JSONL summary rows | Low, passive after open | Working for camera dropdowns |
| Q-012 | Dropdown option dry-run guard | Target a named dropdown option and refuse selection unless `allowOptionSelect=true` | JSONL option row, bounds, selected/checked, guard reason | Low by default; mutation only when explicitly enabled | Working for camera capture dropdowns |
| Q-013 | Redacted report summary exporter | Convert raw JSONL sweep reports into public-safe Markdown/JSON summaries | Summary table, redaction counts, event counts | Passive host-side | Working for section crawler and dropdown reports |
| Q-014 | Settings route inventory | Classify route-like controls exposed on section crawler pages without clicking them | JSONL route candidates, risk buckets, redacted exporter summary | Passive; raw reports may contain sensitive labels | Working for child-page/dropdown routes in focused section sweep |
| Q-015 | Generated low-risk child-page plan | Generate `childTargets` from route inventory and run a compact `settingsChildPageProbe` | JSONL child-surface summaries, redacted exporter summary | Open/dump only; excludes sensitive/external/mutation buckets by default | Working for General, Environment setup, Accessibility, and Audio child pages |

## Command Sequence Database

Each sequence should be stable enough to retry, with an explicit status and
known rollback/stop step.

| Sequence ID | Purpose | Steps | Effect | Status |
| --- | --- | --- | --- | --- |
| `metacam.open` | Open the Quest sharing panel | `am start -W -n com.oculus.metacam/com.oculus.panelapp.sharing.SharingPanelActivity`; wait idle; dump hierarchy | Shows the sharing panel when the activity is available | Working |
| `metacam.record.start_stop` | Exercise built-in recording through visible UI | Run `metacam.open`; click `com.oculus.metacam:id/screenrecording_button`; wait; run `metacam.open`; click same resource ID | Starts and stops built-in recorder; creates headset video file | Working, active |
| `metacam.settings.basic` | Open first-level camera settings menu | Run `metacam.open`; click `com.oculus.metacam:id/camera_settings_dropdown_button`; dump | Reveals settings such as mic audio, view, aspect ratio, more settings | Working |
| `metacam.settings.deep` | Open deeper camera settings panel | Run `metacam.settings.basic`; click `com.oculus.metacam:id/camera_settings_menu_more_settings`; dump | Shows settings panel with red-dot, hide-controls, capture markers, aspect ratio | Working |
| `quest.baseline.current_window` | Dump the current UI/accessibility baseline | `currentWindow` instrumentation scenario; summarize with `summarize_report.py` | Emits structural XML node counts, display IDs, clickable/scrollable counts, accessibility window counts, and action-node counts without raw text or paths | Working |
| `quest.surface.surface_map_current` | Dump shell display/window state plus accessibility windows | `surfaceMap` with `surface=current`; summarize with `summarize_report.py` | Captures display/window shell command witnesses, an instrumentation hierarchy snapshot, accessibility windows, and a host-shell-dump hint | Working |
| `settings.scroll.uiscrollable` | Scroll settings recycler view | Find `com.oculus.panelapp.settings:id/settings_recycler_view`; call `UiScrollable.scrollForward` | Returned failure or no visible new content in first sweep | Needs variants |
| `settings.scroll.uiobject2` | Scroll settings recycler view through AndroidX object API | Find `By.scrollable(true)` with resource `settings_recycler_view`; call `scroll(Direction.DOWN, 0.75f, 1000)` | Reveals `Format and quality`, `Bit rate`, `Frame rate`, `Image stabilization`, and `Eye perspective` | Working |
| `settings.scroll.accessibility_action` | Scroll settings recycler view through raw accessibility action | Find scrollable node with resource `settings_recycler_view`; call `ACTION_SCROLL_FORWARD` | Reveals the advanced capture settings and returns true | Working |
| `settings.scroll.coordinate` | Coordinate swipe on visible settings panel | `UiDevice.swipe(900,720,900,240,50)` after deep settings open | Input reported success but did not reliably reveal deeper settings | Unreliable |
| `settings.scroll.shell_swipe_display_0` | Display-targeted shell swipe | `input touchscreen -d 0 swipe 900 720 900 240 500` | Moved the active Quest settings recycler in a single-display probe | Working |
| `settings.scroll.shell_swipe_display_43` | Probe a non-default Quest display ID | `input touchscreen -d 43 swipe 900 720 900 240 500` | Changed visible content, but the new nodes appeared to come from the Store/background panel, not the target settings list | Ambiguous |
| `settings.scroll.shell_scroll` | Try Android shell wheel/rotary scroll | `input rotaryencoder -d 0 scroll --axis VSCROLL,3.0` | Fails on this Quest build: `rotaryencoder` is not an accepted source and `scroll` is not listed in `input` usage | Unsupported on current OS |
| `settings.scroll.key_scroll` | Try keyboard/D-pad scroll events | `scrollProbe` with `strategy=keyScroll`, scoped `keyCode`, and before/after hierarchy dumps | `DPAD_DOWN` and `SPACE` changed visible state; `PAGE_DOWN` and `TAB` did not. Treat as focus/search/navigation behavior, not as a clean list scroll primitive. | Mapped, not reliable scroll |
| `quest.settings.open` | Open Meta Quest settings through Android settings intent | `am start -W -a android.settings.SETTINGS`; wait; dump | Opens `com.oculus.panelapp.settings` with side nav and scrollable settings grids | Working |
| `quest.settings.nav.default_probe` | Map safe Quest settings sections | `settingsNavProbe` with `general,notifications,display_brightness,audio,camera,accessibility,developer,help` | Opens and dumps each section; no toggles | Working |
| `quest.settings.nav.extended_probe` | Map remaining Quest settings side-nav sections | `settingsNavProbe` with `quest_link,action_button,space_setup,world_movement,movement_tracking,privacy_safety,passcode_security,experimental` | Opens and dumps each section; no toggles | Working, privacy-sensitive |
| `quest.settings.nav.side_nav_scroll` | Reach lower side-nav entries | Resource-id match first; if absent, scroll `ocsidenav_recycler_view`; if AndroidX scroll fails, side-nav lane swipe at x=170 | Reaches Camera, Developer, and Help from retained/mid side-nav states | Working but coordinate-sensitive |
| `quest.settings.section_crawler` | Crawl section content without toggles | Open each target with `settingsNavProbe` route; dump page; scroll main `settings_recycler_view`; repeat until no movement or `maxSectionScrolls` | Produces per-page visible labels, checked-node summaries, clickable-node summaries, XML dumps, and scroll outcomes | Working |
| `quest.settings.section.camera_crawl` | Crawl Camera section content | `settingsSectionCrawler` target `camera` with object scrolling | Finds recorder indicator/hide controls/capture markers/aspect ratio, then mic audio/bit rate, then frame rate/image stabilization/eye perspective/video coding format | Working |
| `quest.settings.section.experimental_crawl` | Crawl Experimental section content | `settingsSectionCrawler` target `experimental` with object scrolling | Finds reset/hidden controls/external mic/screen reader/adaptive brightness/temporal dimming, then positional time warp/lying down/Wi-Fi QR/seamless multitasking/surface keyboard | Working |
| `quest.settings.section.full_crawl` | Crawl all known top-level Quest Settings sections | `settingsSectionCrawler` targets all known side-nav IDs with object scrolling and `mainCoordinateFallback=false` | Reaches no-move endpoints across all top-level sections. Multi-page sections observed: Camera, Movement, Experimental, and Notifications. | Working |
| `quest.settings.section.notifications_crawl` | Crawl Notifications to its endpoint | `settingsSectionCrawler` target `notifications` with a higher scroll cap | Reaches six pages: global notification controls, notification position/device categories, and per-app notification rows. Raw app names are local evidence only and should not be committed. | Working, privacy-sensitive |
| `quest.settings.section.route_inventory` | Inventory safe route candidates on settings pages | Run `settingsSectionCrawler`; read `settings_section_route_inventory` events or summarize with `summarize_report.py` | Classifies route-like controls as `child_page`, `dropdown`, `button`, or `dropdown_option`, with conservative risk buckets and recommended follow-up probe type. Verified focused sweep found General child pages, Camera dropdowns, Privacy child pages, and Help external child pages. | Working |
| `quest.settings.child.route_plan_probe` | Generate and run low-risk child probes from route inventory | Run `summarize_report.py <section-report> --format child-targets`; pass the result to `settingsChildPageProbe` with `childTargetRole=row`, `clickModes=coordinate`, compact dumps, and bounded content/nav scrolls | Converts passive route inventory into a focused child-page sweep. Default plans opened General child pages plus Environment setup, Accessibility, and Audio child pages without public raw labels. | Working |
| `quest.settings.child_probe` | Probe allowlisted child pages without toggles | Open a section, locate a literal/regex row label in main settings content, click a non-checkable same-row target, dump the result, press Back | Produces child-surface summaries and flags whether content differs from the clicked page | Working |
| `quest.settings.child.action_modes` | Compare child-row activation routes | Run `settingsChildPageProbe` with `clickModes=coordinate,uiObject2,accessibilityClick,accessibilityExpand` against the same allowlisted rows | Separates coordinate-tap failures from live `UiObject2.click()` and raw accessibility-action behavior | Partial |
| `quest.settings.child.dropdown_targets` | Open selector option popovers | Run `settingsChildPageProbe` with `childTargetRole=dropdown` and `clickModes=coordinate,uiObject2,accessibilityClick` for camera bit rate, frame rate, image stabilization, and eye perspective | Exposes `context_menu_list` options without selecting a value | Working |
| `quest.settings.child.dropdown_option_summary` | Summarize opened selector options | Inspect `settings_child_surface.summary.settingsDropdownOptions` after a dropdown opens | Records clean option labels, row bounds, selected flags, checked flags, and default markers | Working |
| `quest.settings.child.dropdown_option_dry_run` | Target a selector option without selecting it | Run `settingsChildPageProbe` with `childTargetRole=dropdown`, `optionTarget` or `optionTargets`, and default `allowOptionSelect=false` | Records the matched option row and returns `allowOptionSelect=false dry run`; verified for non-default camera capture options without changing settings | Working |
| `quest.uiautomator.report_summary` | Summarize raw sweep reports for public notes | `python examples/quest-ui-automation/tools/summarize_report.py <report.jsonl> --format markdown` | Emits page counts, scroll endpoint status, allowlisted labels, dropdown option evidence, and redaction counts without raw paths or unknown labels | Working |
| `quest.settings.child.privacy_device_permissions` | Open Privacy & safety -> Device permissions | `settingsChildPageProbe` target `privacy_safety:Device permissions` | Opens child page with hand/body tracking, location services, spatial data, enhanced spatial services, and deletion controls | Working, sensitive |
| `quest.settings.child.privacy_app_permissions` | Open Privacy & safety -> App permissions | `settingsChildPageProbe` target `privacy_safety:App permissions` | Opens child page with permission categories including audio files, connected cameras, headset cameras, location, microphone, nearby devices, photos/videos | Working, sensitive |
| `quest.settings.child.camera_selectors` | Try Camera selector rows | Broad-row target with coordinate, `UiObject2.click()`, and `ACTION_CLICK`; dropdown target with `childTargetRole=dropdown` | Broad rows activate without exposing options; compact dropdown targets expose bit-rate, frame-rate, stabilization, and eye-perspective options | Working via dropdown target |
| `quest.settings.child.help_rows` | Try Help rows | `settingsChildPageProbe` targets `help:Support`, `help:Help & Tips app` | `Help & Tips app` opens Help Center; `Support` can surface Help Center and SystemUX support/report UI. These are active external surfaces with side effects | Working but not passive |
| `quest.quick_settings.open_api` | Try Android quick-settings helper | `UiDevice.openQuickSettings()`; wait; dump | Did not expose a distinct Quest quick-settings surface in current probes | Not proven |
| `quest.notifications.open_api` | Try Android notification helper | `UiDevice.openNotification()`; wait; dump | Did not expose a distinct notification shade in current probes | Not proven |
| `quest.scriptable.get_property` | Read Meta scriptable-testing property state | `content call --uri content://com.oculus.rc --method GET_PROPERTY` | Returns supported state keys for boundary/Guardian, proximity-close, blocking dialogs, and auto-sleep without changing them | Working read-only |
| `quest.scriptable.mutating_methods` | Track documented scriptable-testing mutators | Official docs describe `SET_PROPERTY`, `WIPE_DEVICE`, and `SETUP_FOR_TEST` on `content://com.oculus.rc` | Can disable blocking dialogs, boundary/Guardian, and auto-sleep, or wipe/setup a test device. Requires scoped approval, PIN/credentials where applicable, and rollback/reset plan. | Documented, not run |
| `capture.media_projection.state` | Compare built-in recorder to app-visible MediaProjection | `dumpsys media_projection` before/during/after recorder probe | Stayed empty during built-in recording | Working passive check |
| `dump.shell.host` | Capture compressed shell UI hierarchy | `adb shell uiautomator dump --compressed /data/local/tmp/<name>.xml` | Direct host command writes XML successfully | Working host-side |
| `dump.shell.instrumentation_nested` | Try shell UI hierarchy from inside instrumentation | `UiDevice.executeShellCommand("uiautomator dump ...")` | Did not create a file reliably during the Quest instrumentation run | Avoid |

## Next Instrumentation Work

The `examples:quest-ui-automation` test app should grow in small slices:

1. Add a `surfaceMap` scenario that dumps shell display/focus state, hierarchy
   XML, window summaries, display IDs, scrollable nodes, checked switches, and
   accessibility action lists without tapping. Done.
2. Add a `scrollProbe` scenario with named scroll strategies:
   `uiScrollable`, `uiObject2`, `accessibilityAction`, `deviceSwipe`,
   `deviceDrag`, `shellSwipe`, `shellScroll`, and `keyScroll`. Done.
3. Parameterize the scroll target by resource-id regex, text regex, class regex,
   display ID, source, step count, axis value, and direction.
4. Emit one JSONL row per attempted command:
   command, target node summary, return value, exception, focus before/after,
   node count before/after, visible-text hash before/after, and whether new
   nodes appeared.
5. Add a `settingsNavProbe` scenario for resource-id driven Quest Settings
   side-nav exploration. Done.
6. Add a non-mutating `settingsSectionCrawler` scenario that scrolls main
   settings content and records per-page node summaries. Done.
7. Add an allowlisted `settingsChildPageProbe` for non-toggle child pages and
   selector surfaces. Done.
8. Keep active mutation behind explicit extras such as `tapRegex`, `toggleRegex`,
   or `recordProbe=true`.
9. Add an explicit option-selection dry-run/guard that can target a specific
   option by text but refuses to click unless a mutation extra such as
   `allowOptionSelect=true` is present. Done.
10. Add a section summary exporter that emits redacted, low-cardinality
    findings directly from JSONL reports so raw private labels, installed app
    names, and full XML paths do not need manual handling. Done.
11. Next useful slice: add an on-device or host-side route inventory for
    Quest Settings child pages that are safe to open but not mutate, using the
    exporter as the default path from raw report to public notes. Done.
12. Next useful slice: use the route inventory to drive a constrained
    allowlisted child-page probe over low-risk routes, keeping privacy,
    security, Help/external, reset, and button routes open/dump-only until
    explicitly scoped. Done for default General-section child targets.
13. Next useful slice: expand the generated child-target planner across more
    low-risk sections as the safe-label allowlist grows.

## Open Questions

- Which display ID receives touch and scroll input for the active Quest panel?
  Current best answer: display 0 for the active Android settings panel. XML may
  include other display IDs for background/panel surfaces, and display 43 can
  affect visible background content.
- Do Meta settings recycler nodes expose accessibility scroll actions even when
  `UiScrollable` cannot locate them reliably? Yes for the settings recycler in
  the camera/settings path.
- Does `adb shell input -d <display> rotaryencoder scroll --axis VSCROLL,...`
  move Quest settings surfaces where coordinate swipes do not? No on the
  current Quest OS build because shell `input` lacks `scroll` and
  `rotaryencoder`.
- Does changing UIAutomator motion tool type from finger to mouse or stylus
  alter Quest panel behavior? No improvement observed for the first mouse-tool
  swipe probe.
- Are advanced video settings present behind a different activity, tab, or
  settings section than the first Metacam settings panel reached so far? They
  are present lower in the same settings recycler and reachable by object or
  accessibility scrolling.
- Can Meta Horizon MCP `ui_select` and `ui_tap` cover the same successful
  resource/text-driven paths as the local instrumentation app?
- Which system settings panes are fully accessible by intent, and which require
  Home/menu navigation?
