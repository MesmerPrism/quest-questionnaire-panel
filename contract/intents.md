# Intent Contract

Protocol version: `quest.questionnaire.v1`

## Panel Launch

Action:

```text
io.github.mesmerprism.questquestionnaire.action.START
```

Default panel component:

```text
io.github.mesmerprism.questquestionnaire.panel/.QuestionnaireActivity
```

MIME type:

```text
application/vnd.quest-questionnaire.request+json
```

Required extras:

| Extra | Type | Meaning |
| --- | --- | --- |
| `session_id` | string | Caller session id. |
| `request_id` | string | Opaque per-request id. |
| `request_nonce` | string | Random nonce to bind request/result. |
| `request_json` | string | JSON matching `quest.questionnaire.v1.request.schema.json`. |
| `result_uri` | parcelable Uri | Caller-owned result URI. |
| `return_to_caller` | parcelable PendingIntent | Completion callback. |

The `request_json` payload may include optional `questionnaire_state` for
small questionnaire-owned state that a caller carries between split launches,
such as the MAIA/spatial selected language code.

Debug-only optional extras:

| Extra | Type | Meaning |
| --- | --- | --- |
| `io.github.mesmerprism.questquestionnaire.extra.DEBUG_AUTO_SUBMIT` | boolean | Immediately writes a synthetic completed result in debug builds. |
| `io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_SCRIPT` | string | Comma-, semicolon-, or newline-separated UI-equivalent debug commands in debug builds. |
| `io.github.mesmerprism.questquestionnaire.extra.DEBUG_COMMAND_INTERVAL_MS` | int | Delay between debug commands, clamped to 0-10000 ms. |

Debug command scripts are for repeatable Quest validation only. They are ignored
in release builds and must not replace real user interaction evidence. Prefer
comma separators when passing scripts through `adb shell am start`; semicolons
are treated as shell separators unless escaped.

Grant shape:

- Put the caller-owned result URI in Intent data.
- Set `FLAG_GRANT_WRITE_URI_PERMISSION`.
- Use an explicit component.
- Do not place a separate read-only request URI in the same `ClipData` with
  broad read/write flags.

## Completion Callback

The default callback is `PendingIntent.getBroadcast()` to a private caller
receiver.

The questionnaire app sends the callback only after it writes final JSON and
closes the output stream.

The callback is only a signal. Result status and answers live in the result
JSON, not in callback extras.

If result writing fails, the panel keeps the failure local because the caller
cannot rely on result-file recovery. If the result was written and closed but
the callback cannot be sent, the caller can still recover the terminal JSON on
resume or cold start.

## BRB Stage Names

BRB split-app stage names:

```text
language_select
demographics
prior_experience
post_condition:pictographic
post_condition:presence_questionnaire
post_condition:lost_opportunity
final:end_confirmation
final:extra_presses_prompt
complete:export_summary
```

Recommended caller sequences:

```text
Initial panel sequence:
language_select -> demographics -> prior_experience

Post-condition 1 panel sequence:
post_condition:pictographic -> post_condition:presence_questionnaire -> post_condition:lost_opportunity

Post-condition 2 panel sequence:
post_condition:pictographic -> post_condition:presence_questionnaire

Final panel sequence:
final:end_confirmation -> final:extra_presses_prompt -> complete:export_summary
```

The Unity app remains the owner of the 3D Big Red Button, condition sessions,
button press counting, and final physical button interaction. The panel owns
only the foreground 2D questionnaire screens and writes answers to the
caller-owned result URI.

`final:end_confirmation` is a 1-to-10 end-confirmation scale. A selected `10`
is the immediate end path. Any non-10 value continues through
`final:extra_presses_prompt`, after which Unity records the physical button
interaction.

Completed BRB results include only the answer buckets relevant to the requested
sequence:

| Sequence | Required completed-answer buckets |
| --- | --- |
| Initial | `language`, `demographics`, `prior_button_experience` |
| Post-condition | `post_condition` |
| Final | `final` |

Cancelled and error results do not require completed-answer buckets.

## Generic Demo Stage Names

The panel also includes a minimal non-BRB renderer for integration tests and
new-caller onboarding:

```text
generic:intro
generic:rating
generic:comment
generic:complete
```

Recommended generic demo sequence:

```text
generic:intro -> generic:rating -> generic:comment -> generic:complete
```

The generic demo proves that the v1 envelope is not BRB-specific. Production
questionnaires should use their own `schema_id`, stage names, renderer, and
answer validator.

## MAIA-2 Spatial Frame Stage Names

The MAIA-2 plus spatial-frame-reference renderer uses:

```text
schema_id = maia2-spatial-frame-questionnaire-v1
```

Recommended Rusty Morphospace/native caller sequences:

```text
Block 1 setup + MAIA-2:
maia_spatial:language_selection -> maia_spatial:demographics -> maia_spatial:maia2

Block 2 spatial frame:
maia_spatial:spatial_frame_reference_1

Block 3 spatial frame:
maia_spatial:spatial_frame_reference_2
```

Block 2 and Block 3 should include the Block 1 language in request JSON:

```json
{
  "questionnaire_state": {
    "language_code": "en"
  }
}
```

The corresponding completed result answer buckets are
`spatial_frame_reference_administration_1` and
`spatial_frame_reference_administration_2`.
