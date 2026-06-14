# BRB Demo Capture Workflow

Last updated: 2026-06-14

This is the public capture plan for the Big Red Button Unity app and the Quest
Questionnaire Panel. It records the current demo state and the preferred next
capture path now that the UIAutomator lab app can start and stop the headset's
built-in recorder.

## Current Public State

- GitHub Pages entry point: `docs/index.html`.
- Current curated clips: `docs/media/brb-blink.mp4`,
  `docs/media/brb-press.mp4`, `docs/media/panel-open.mp4`,
  `docs/media/panel-navigate.mp4`, and `docs/media/panel-close.mp4`.
- BRB Unity reference branch:
  `MesmerPrism/the-big-red-button-institute` branch
  `codex/brb-questionnaire-panel-bridge`.
- BRB CLI triggers are Android Intent extras on the Unity activity:
  `brb.runtimeCommand`, `brb.runtimeCommandScript`,
  `brb.questionnaireTrigger`, and `brb.questionnaireCommandScript`.
- Recorder control is the development-only UIAutomator scenario
  `metacamRecordProbe` in `examples:quest-ui-automation`.

The existing clips show the Unity-side button behaviors and the panel-side
handoff separately. The next capture refresh should be one continuous headset
recording, then trimmed into the public clips needed by the Pages guide.

The 2026-06-14 lab pass verified the workflow in both landscape and portrait
Metacam modes. Portrait mode produced a true `1080x1920` headset-recorder
source, but neither landscape nor portrait fully framed the current BRB plus
panel composition. Treat this workflow as the repeatable capture bracket; the
final showcase framing may still need app-side camera, panel placement, or a
different capture route.

For a short apples-to-apples capture comparison across built-in landscape,
built-in portrait, ADB `screenrecord`, and app-owned MediaProjection, use
[`recording-comparison-matrix.md`](recording-comparison-matrix.md).

## Preferred Refresh Flow

1. Build and install the current panel APK, the BRB Unity APK, and the
   `examples:quest-ui-automation` host plus instrumentation APKs.
2. If another agent or operator may be using the headset or ADB server, reserve
   the shared resource before the live run.
3. Disable any active OVR Metrics/performance overlay before capture.
4. Launch BRB and send `center_button` before starting the recorder so the
   app is already framed.
5. Optionally set Metacam aspect ratio and quality for the run, with a written
   rollback command.
6. Start one long built-in headset recording through `metacamRecordProbe`.
7. While the recorder probe is sleeping, trigger the BRB Unity and panel moments
   from a second shell with the CLI commands below.
8. Let `metacamRecordProbe` stop the recorder through the visible Metacam
   screen-recording button.
9. Restore any temporary Metacam recorder settings.
10. Keep the raw headset MP4 in ignored/off-repo artifact storage.
11. Cut reviewed exports into the existing `docs/media/*.mp4` clip names.
12. Run `python tools\check_public_artifacts.py` before publishing the updated
   clips or docs.

## Capture Preflight

If OVR Metrics or another performance overlay might be active, clear it before
recording:

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
```

Center BRB before the recorder opens the Metacam panel:

```powershell
adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --es brb.runtimeCommand center_button
```

For a portrait high-quality test pass, use the guarded settings selector path:

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

Rollback after the run:

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

## Recorder Bracket

Run this in terminal A. Pick a `recordMs` value long enough for the complete
showcase pass; the probe opens the Metacam sharing panel, taps the exposed
screen-recording button, waits, reopens the panel, and taps the same button to
stop.

```powershell
adb shell am instrument -w `
  -e scenario metacamRecordProbe `
  -e recordMs 180000 `
  io.github.mesmerprism.questquestionnaire.questuiautomation.test/androidx.test.runner.AndroidJUnitRunner
```

After the recording indicator is visible, run the showcase commands in
terminal B.

Use `center_button` as the first captured BRB command as well:

```powershell
adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --es brb.runtimeCommand center_button
```

## Showcase Commands

Use the BRB Unity activity as the stable entry point for the demo. The
questionnaire product path is still the explicit Intent, caller-owned
`content://` result URI, and completion callback; these CLI commands are only
for repeatable capture and validation.

```powershell
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

adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --ez brb.questionnaireOpen true `
  --es brb.questionnaireTrigger post_condition_1

adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --ez brb.questionnaireOpen true `
  --es brb.questionnaireTrigger final `
  --es brb.questionnaireCommandScript "final:1,next,submit" `
  --ei brb.questionnaireCommandIntervalMs 1400
```

For a tighter Unity-only sequence, BRB also accepts:

```powershell
adb shell am start -W `
  -n org.thebigredbuttoninstitute.app/com.unity3d.player.UnityPlayerGameActivity `
  --es brb.runtimeCommandScript "center_button,blink_button:6,press_button,status" `
  --ei brb.runtimeCommandIntervalMs 700
```

## Clip Map

Trim the one raw headset recording into these public onboarding clips:

| Clip | Source moment |
| --- | --- |
| `docs/media/brb-blink.mp4` | Unity receives `blink_button:6` and blinks the 3D button. |
| `docs/media/brb-press.mp4` | Unity receives `press_button`, plays the press animation, and updates the counter. |
| `docs/media/panel-open.mp4` | Unity launches the panel for the initial questionnaire sequence. |
| `docs/media/panel-navigate.mp4` | The panel advances through requested BRB questionnaire screens. |
| `docs/media/panel-close.mp4` | The panel submits, writes the caller-owned result, sends the callback, and closes. |

Only the final reviewed clips belong in `docs/media/`. Do not commit the raw
one-take recording, UIAutomator XML, screenshots, logcat output, headset
serials, or local artifact paths.
