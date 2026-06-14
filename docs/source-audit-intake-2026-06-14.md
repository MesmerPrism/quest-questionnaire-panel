# Source Audit Intake: Quest UI Automation And Capture

Date: 2026-06-14

This note records how the external audit notes were handled. The useful parts
are source-backed risk controls and project-boundary clarifications. The
speculative parts are kept out of the implementation until a local test or a
small schema proves the value.

## Decision

Keep the project as two separate systems:

1. The questionnaire product path stays a normal Android IPC flow: explicit
   launch, versioned request/result JSON, caller-owned `content://` result URI,
   and one-shot `PendingIntent` completion.
2. The Quest UI automation path stays a lab tool: UIAutomator, ADB shell,
   accessibility-node inspection, MediaProjection prompt probes, Metacam panel
   probes, and redacted evidence summaries.

The sources support this split. Android describes UIAutomator as a framework
for UI tests that interact with user apps and system apps, not as an XR scene
or compositor inspection layer:
<https://developer.android.com/training/testing/other-components/ui-automator>.
Android MediaProjection requires user consent before each session and treats a
session as a single `createVirtualDisplay()` call:
<https://developer.android.com/media/grow/media-projection>. Meta's Quest
MediaProjection documentation says Horizon OS populates the capture surface
from the compositor, but still uses the Android MediaProjection consent/token
model:
<https://developers.meta.com/horizon/documentation/native/native-media-projection/>.

## Accepted Now

| Audit point | Disposition | Mitigation |
| --- | --- | --- |
| Public artifact scrubber | Accepted. The repo already documents that raw evidence must stay out; an executable staged-change check reduces accidental leaks. | Added `tools/check_public_artifacts.py`; documented it in `docs/research-data-safety.md`. |
| Curated media publishing lane | Accepted as a checklist, not a new pipeline. The repo intentionally allows `docs/media/*.mp4` for the public GitHub Pages onboarding clips. | Added a review checklist to `docs/research-data-safety.md`; the scrubber blocks other raw recordings. |
| Product/lab separation | Accepted as a standing boundary. The current structure already follows it. | This intake note and `docs/quest-uiautomator-capability-map.md` now point future work back to the boundary. |
| MediaProjection consent limits | Accepted. The audit's conclusion matches Android and Meta documentation and the local prompt probe. | Keep MediaProjection automation in the lab example; do not use it to bypass consent or as part of questionnaire result delivery. |
| Scriptable testing service caution | Accepted. Meta documents useful ADB provider methods, but also mutating reset/setup/toggle operations. | Keep `GET_PROPERTY` as read-only evidence; require scoped approval and rollback for `SET_PROPERTY`, `WIPE_DEVICE`, or `SETUP_FOR_TEST`. |

## Already Covered

| Audit point | Current repo coverage |
| --- | --- |
| MediaProjection prompt scenario | Implemented in the Quest UI automation example and summarized in the capability map. |
| Dropdown option dry-run guard | Implemented with `allowOptionSelect=false` as the default. |
| Redacted report exporter | Implemented in `examples/quest-ui-automation/tools/summarize_report.py`. |
| Raw evidence kept out of public repo | `.gitignore`, `docs/research-data-safety.md`, and the report exporter already cover this; the new scrubber makes it executable for staged changes. |
| UIAutomator boundary | Documented in `docs/quest-uiautomator-capability-map.md`: Android-accessibility-backed panels only, not Unity/OpenXR internals. |

## Deferred

| Audit point | Reason to defer | Next proof needed |
| --- | --- | --- |
| Split the monolithic instrumentation Java test | The pressure is real, but a mechanical split should follow stable helper ownership, not line-count anxiety alone. | Split by surface, settings, capture, report, and safety helpers after the next feature slice or when a change would otherwise add another owner family to the same file. |
| JSON Schema for the command database | Useful, but the JSONL shape is still evolving as we discover settings routes. | Add once the command fields stabilize around gates, rollback, verification date, and evidence class. |
| Generate Markdown from JSONL | Useful for drift prevention, but it should follow the schema rather than precede it. | Create a generator only after schema validation exists. |
| Selector confidence scoring | Good idea, but it needs a compact field model first. | Add `selectorConfidence` only when new command rows begin choosing between resource-id, class/package/bounds, and localized text routes. |
| Versioned Quest compatibility matrix | Valuable for OS drift, but it should not invite committing serials or raw device reports. | Start with model family, Horizon OS version, locale, and scenario status; keep serials and raw dumps private. |
| Recorder dead-man cleanup | Useful for active recording probes, but it changes device state and needs a focused stop/rollback run. | Add only with explicit `allowRecording=true` and a verified stop route. |
| Coordinate calibration probe | Useful for coordinate fallback, but object/accessibility routes should remain preferred. | Add before expanding any coordinate-driven settings routes. |

## Rejected For Now

| Audit point | Rejection reason |
| --- | --- |
| Appium UiAutomator2 as a project dependency | Appium is a useful orchestration reference, but it would not make inaccessible Quest compositor/OpenXR UI visible. No dependency until remote QA infrastructure needs WebDriver. |
| scrcpy as an evidence source | scrcpy is useful operator tooling, but mirrored pixels and host control do not replace selector-backed UIAutomator evidence. Keep it outside the repo's validation path. |
| Meta capture system properties as recorder control | Meta documents capture-related properties, but that does not prove stable built-in recorder control for this app. Treat them as lab observations only until local tests prove effects and rollback. |
| AUTOVR or DriveSimQuest adoption | Both are useful adjacent research references, but they operate at different layers: Unity app analysis or in-app research telemetry. They do not belong in this public questionnaire panel implementation. |

## Source Trailheads

- Android UIAutomator: <https://developer.android.com/training/testing/other-components/ui-automator>
- Android Debug Bridge: <https://developer.android.com/tools/adb>
- Android MediaProjection: <https://developer.android.com/media/grow/media-projection>
- Android MediaProjection foreground service type:
  <https://developer.android.com/develop/background-work/services/fgs/service-types>
- Meta Quest MediaProjection:
  <https://developers.meta.com/horizon/documentation/native/native-media-projection/>
- Meta screenshots and video capture:
  <https://developers.meta.com/horizon/documentation/native/android/mobile-testing-capture/>
- Meta Quest scriptable testing services:
  <https://developers.meta.com/horizon/documentation/android-apps/ts-scriptable-testing/>
- Meta Quest system properties:
  <https://developers.meta.com/horizon/documentation/unity/ts-systemproperties/>
- AOSP input shell command:
  <https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/input/InputShellCommand.java>
- Appium UiAutomator2 driver:
  <https://github.com/appium/appium-uiautomator2-driver>
- scrcpy: <https://github.com/Genymobile/scrcpy>
