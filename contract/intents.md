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

## BRB Stage Names

Initial BRB-first stage names:

```text
demographics
post_condition:pictographic
post_condition:presence_questionnaire
post_condition:lost_opportunity
complete:export_summary
```
