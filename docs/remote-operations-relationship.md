# Remote Operations Relationship

This repository owns the questionnaire panel product path. The related Quest
workflow and Termux lab repositories help build, test, update, and observe that
path, but they are not dependencies of the normal questionnaire experience.

## Product Path

The production contract is app-to-app Android IPC:

```text
foreground XR app
  -> creates request id, nonce, request JSON, and caller-owned result URI
  -> launches the questionnaire panel with an explicit intent
questionnaire panel
  -> renders the requested questionnaire stage or sequence
  -> writes result JSON to the granted content:// URI
  -> sends the caller PendingIntent callback
  -> finishes only the panel activity
foreground XR app
  -> reads and validates the result before resuming study state
```

This path must not require ADB, Termux, public shared storage, MediaStore,
Meta menu navigation, force-stop, package killing, or a remote controller. The
XR app owns immersive study state. The panel owns questionnaire rendering,
validation, result writing, and callback.

Core implementation docs:

- [Handoff contract](handoff-contract.md)
- [Contract versioning](contract-versioning.md)
- [Validation matrix](validation-matrix.md)
- [Research data safety](research-data-safety.md)

## Workflow Path

The public
[meta-quest-agent-workflow](https://github.com/MesmerPrism/meta-quest-agent-workflow)
repo and local `meta-quest-workflow` skill own headset-facing discipline:

- ADB and Meta VR CLI / `hzdb` provider selection;
- APK install/launch evidence;
- screenshot, logcat, recorder, and MediaProjection capture labeling;
- protected prompt and headset readiness notes;
- public/private artifact boundaries.

Use that workflow before live Quest runs. It should record how the device was
operated; it should not change the questionnaire result contract.

Current compatibility note: new manual Meta MCP setup examples use Meta VR CLI
(`npx -y metavr`), while MQDH/editor bundles may still expose `hzdb`. Record
the selected route and version in run notes. On Horizon OS 2.x, validation
notes should also record exact OS and PTC state, Navigator/Home state, restored
or snapped panels, privacy indicators, and any Meta system UI that appears
during launch/return. Those surfaces can affect screenshot and foreground
evidence, but they do not change the product path: answers still return through
the caller-owned `content://` URI and caller callback.

## Termux Lab Path

The public
[quest-termux-lab](https://github.com/MesmerPrism/quest-termux-lab) repo owns
Termux/Linux sidecar experiments and outbound fleet-control prototypes:

- Termux-local ADB only after `adb shell id` reports `uid=2000(shell)`;
- verified APK update simulation through `apk.update_verified`;
- active remote-session leases for non-passive remote commands;
- allowlisted launch, foreground snapshot, bounded logcat, ADB lease checks,
  helper status checks, and an allowlisted UIAutomator scenario bridge;
- consent-gated MediaProjection preview placeholders that require a separate
  app-owned helper before they can stream pixels;
- synthetic fixtures and public-boundary scanning.

Termux is useful for lab updates, recovery, and remote observation. It is not
the panel product channel and must not receive questionnaire answers through
file drops or public storage.

## UIAutomator Bridge

This repo also contains `examples:quest-ui-automation`, an Android
instrumentation APK for mapping Quest system UI surfaces such as Settings and
the built-in recorder panel. Its current public runbook is
[Quest UIAutomator Sweep Runbook](quest-uiautomator-runbook.md).

The remote bridge is implemented in `quest-termux-lab` as a gated command:

```text
quest-termux-lab command
  kind = uiautomator.run_allowlisted_scenario
  remote_session_lease_id = active scoped lease
  payload.scenario = named scenario such as settingsSectionCrawler
  payload.extras = small typed values
  evidence_mode = summary_only
```

The agent should then check the active lease, check the local ADB shell gate,
run only the named allowlisted instrumentation scenario, and default to a
redacted command summary. Raw instrumentation output, XML, screenshots, videos,
logcat bundles, device serials, local paths, installed app names, and private
package IDs stay out of public repos unless a private live-run config
explicitly keeps them in local evidence.

The current low-risk remote scenarios are:

- `settingsRecoveryProbe` for invisible or zero-node Settings relaunch
  diagnostics without force-stop/package-kill recovery;
- `systemSurfaceReachability` for passive structural comparison of current,
  quick-settings, notification, Android settings, and Metacam entry surfaces.

## Decision Rule

If a change affects participant questionnaire behavior, request/result JSON,
Unity/Android caller APIs, or BRB/MAIA screen logic, it belongs in this
repository. Unity callers should verify their project pin against the current
Meta XR SDK line before treating a Quest workflow issue as a questionnaire
contract issue; Meta XR SDK 203.0 and Spatial SDK 0.13.1 introduce current
Unity/SDK compatibility expectations outside this panel contract.

If a change affects how a developer operates a Quest, captures evidence,
pregrants MediaProjection for lab capture, or interprets Meta system UI, it
belongs in `meta-quest-agent-workflow` or the local skill.

If a change affects off-LAN Termux agents, remote update commands, helper
restart, leases, command queues, or sidecar evidence fixtures, it belongs in
`quest-termux-lab`.
