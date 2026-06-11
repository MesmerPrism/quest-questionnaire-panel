# Quest Questionnaire Panel

Standalone native Quest 2D questionnaire panel app and the minimal caller app
needed to prove cross-app communication.

The first target is not a fully generic survey framework. The first target is
to extract the Big Red Button questionnaire behavior into a reusable 2D Quest
panel app with a versioned Android IPC contract that native XR and Unity XR
callers can share.

## Repo Shape

```text
app/                    # Native Kotlin/Android Quest 2D panel app
contract/               # Versioned JSON schemas and intent names
android-caller-sdk/     # Future tiny Kotlin/AAR helper for Android callers
unity-caller-plugin/    # Future Unity Android bridge wrapper
examples/
  native-caller/        # Minimal Android caller/tester app
docs/
  WORKPLAN.md
  handoff-contract.md
  validation-matrix.md
  handoff-prompt.md
```

## MVP Scope

1. Build the standalone Quest 2D questionnaire panel app.
2. Support `quest.questionnaire.v1` request/result JSON.
3. Implement the BRB-first stages:
   - `demographics`
   - `post_condition:pictographic`
   - `post_condition:presence_questionnaire`
   - `post_condition:lost_opportunity`
   - `complete:export_summary`
4. Build the minimal native caller tester that creates a caller-owned
   `content://` result URI, launches the panel, receives a broadcast
   `PendingIntent`, and reads the result JSON.
5. Defer Unity integration until the native Quest communication path is proven.

## Communication Pattern

The caller owns the result file and grants the panel temporary write access:

```text
caller app
  -> create request id, nonce, request JSON, and private result file
  -> expose result file as content:// URI
  -> launch panel with explicit component and write grant

questionnaire panel
  -> render requested stage/sequence
  -> write final result JSON to granted URI
  -> send caller PendingIntent broadcast
  -> finish only the panel activity

caller app
  -> read and validate result JSON
  -> recover on callback, resume, or cold start
```

No ADB, public shared storage, Termux file drops, force-stop, package killing,
or Meta menu navigation should be part of the product path.

## Current Status

The native panel app and minimal native caller tester build as debug APKs. The
first smoke path proves the caller-owned `content://` result URI plus one-shot
immutable broadcast `PendingIntent` completion contract.

For off-store update behavior and fleet-operation caveats, see
[`docs/self-update.md`](docs/self-update.md). The app-side updater uses
Android's installer UI; Termux plus loopback WiFi ADB, including any
operator-launched helper that restarts a stopped Termux agent, is an external
lab/fleet operations path, not part of the questionnaire result contract.
