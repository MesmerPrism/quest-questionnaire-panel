# Handoff Contract

The product path is normal Android IPC.

## Caller Responsibilities

- Generate `session_id`, `request_id`, and random `nonce`.
- Persist pending session state before launching the panel.
- Create an app-private result file.
- Expose only that result file through a narrow `FileProvider` or custom
  `ContentProvider`.
- Launch the questionnaire with an explicit component.
- Grant write access only to the result URI.
- Provide a one-shot immutable broadcast `PendingIntent`.
- Read and validate result JSON on callback, resume, and cold start.
- Cleanup transient result files according to retention policy.

## Panel Responsibilities

- Treat every extra and JSON field as untrusted.
- Render only the requested stage or screen sequence.
- Keep questionnaire UI, questionnaire audio prompts, and questionnaire answer
  collection inside the panel app.
- Write result JSON to the granted URI on explicit submit.
- Close the stream before sending the callback.
- Send the caller-provided callback.
- Finish only the panel activity.
- Never store answers in public storage, logs, callback extras, notifications,
  or filenames.

## Unity Split-App Boundary

- Unity owns the 3D scene, Big Red Button, condition instruction sessions,
  button press counting, and physical final button interaction.
- The panel owns `language_select`, `demographics`, `prior_experience`,
  post-condition questionnaire screens, final confirmation, and the return
  prompt.
- Final confirmation is the BRB 1-to-10 end-confirmation scale. Selecting `10`
  skips the extra prompt; any non-10 value advances to the extra-presses prompt.
- The panel must not become a substitute for the 3D button. Stages such as
  `final:extra_presses_prompt` are instructions/return prompts only; Unity
  records the actual physical presses after the panel returns.

## Default Request Extras

```text
session_id
request_id
request_nonce
request_json
result_uri
return_to_caller
```

## Default Result Validation

The caller must verify:

- `protocol_version == quest.questionnaire.v1`
- expected `request_id`
- expected `nonce`
- supported result schema
- supported questionnaire id/version
- expected stage or sequence
- known status
- answer object shape for the requested stage

## Explicit Non-Goals

- No ADB as product communication.
- No Termux file drop.
- No shared public storage.
- No MediaStore answer exchange.
- No `file://` URI.
- No `QUERY_ALL_PACKAGES`.
- No `SYSTEM_ALERT_WINDOW` or overlay-based return flow.
- No force-stop or package killing as normal return behavior.
