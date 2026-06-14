# Recording Comparison Matrix

Last updated: 2026-06-14

This plan captures the same short BRB plus questionnaire-panel stimulus through
each available Quest recording route. The goal is an apples-to-apples evidence
set, not final public clips. Raw MP4s, contact sheets, UIAutomator reports, and
device logs stay in ignored `artifacts/` storage.

## Shared Stimulus

Every route should capture the same sequence inside a roughly 15-second window:

| Time | Action | Command shape |
| ---: | --- | --- |
| Pre-roll | Disable OVR Metrics overlay and center BRB | `DISABLE_OVERLAY*` broadcasts, then `brb.runtimeCommand=center_button` |
| 1s | Center BRB again inside the recording | `brb.runtimeCommand=center_button` |
| 2s | One button blink | `brb.runtimeCommand=blink_button:1` |
| 4s | One button press | `brb.runtimeCommand=press_button` |
| 6s | Foreground the panel | `brb.questionnaireOpen=true`, `brb.questionnaireTrigger=final` |
| 7s | One panel answer/action | `brb.questionnaireCommandScript=final:1,next,submit` |
| 10-15s | Panel closes and BRB is visible again | wait for caller return |

The repeatable runner is:

```powershell
.\tools\Run-BrbRecordingComparison.ps1 -RecordSeconds 15
```

It writes a per-run folder under `artifacts/recording-comparison/` with:

- one MP4 per capture route;
- command logs;
- `results.jsonl` with ffprobe metadata when `ffprobe` is available.

## Capture Routes

| Route | Capture command | Target settings | Expected output | Status |
| --- | --- | --- | --- | --- |
| Built-in Metacam landscape | `metacamRecordProbe` | `Landscape 16:9`, `14 mbps`, `60 fps`, `High` stabilization | Flat spectator MP4 from `/sdcard/Oculus/VideoShots/` | Verified as a recorder route, but back-to-back matrix capture can leave the file active/finalizing |
| Built-in Metacam portrait | `metacamRecordProbe` | `Portrait 9:16`, `14 mbps`, `60 fps`, `High` stabilization | True vertical Metacam MP4 from `/sdcard/Oculus/VideoShots/` | Verified as a recorder route, but the matrix runner must not reuse a prior Metacam file as success |
| ADB `screenrecord` | `adb shell screenrecord --size 3664x1920 --bit-rate 40000000 --time-limit 15` | Native stereo display capture, 40 Mbps target | Raw headset-display MP4 | Diagnostic route; not polished spectator framing |
| App-owned MediaProjection | `ProjectionRecordingActivity` plus `ProjectionRecordingService` | `1920x1080`, `60 fps`, `40 Mbps` target | MP4 under the automation app's external files | Working lab route; requires `PROJECT_MEDIA` app-op or visible consent and Android 14 foreground-service handling |

## 2026-06-14 Matrix Result

The first executed full pass proved the workflow but exposed route-specific
limits. ADB `screenrecord` and app-owned MediaProjection produced usable
short clips with the intended BRB/panel stimulus. Back-to-back Metacam runs did
not produce independent files: the first pull was an early partial copy and
the second Metacam route reused the same headset file after it continued
growing. Treat that as a concrete Metacam-runner limitation, not as two clean
comparison samples.

| Route | Width | Height | FPS | Duration | Bitrate | Panel visible? | BRB visible? | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| Built-in Metacam landscape attempt | 1080 | 1920 | about 60 | 10.0s | about 1.3 Mbps | Yes | Yes | Early partial MP4 copy; ffmpeg reported a corrupt/partial input. Useful as evidence that immediate pull can race Metacam finalization. |
| Built-in Metacam portrait attempt | 1080 | 1920 | about 60 | 235.0s | about 1.0 Mbps | Not a clean row | Yes | Same headset file continued/got reused across Metacam attempts; not apples-to-apples. |
| ADB `screenrecord` | 3664 | 1920 | about 67 | 15.0s | about 9.5 Mbps | Yes | Yes | Clean stereo-eye diagnostic capture. Requested 40 Mbps, device encoder delivered about 9.5 Mbps. |
| App-owned MediaProjection | 1920 | 1080 | about 30 | 14.5s | about 3.25 Mbps | Yes | Yes | Working flat capture after one targeted rerun. Requested 60 FPS/40 Mbps, device encoder delivered about 30 FPS/3.25 Mbps. |

Runner changes made after the pass:

- Metacam routes now fail if a route does not create a new headset MP4 instead
  of falling back to the newest prior file.
- Metacam pulls record whether the headset file stabilized before pull.
- MediaProjection records through a foreground service and retries once if the
  first output is a zero-frame or `MediaRecorder.stop failed` artifact.

## Publication Rules

- Commit only the matrix, commands, and final reviewed public clips.
- Do not commit raw one-take recordings, raw UIAutomator JSONL/XML,
  screenshots, logcat bundles, device serials, local machine paths, or app-op
  state dumps.
- If a route fails, record a public-safe failure class and keep raw evidence in
  ignored artifacts.
