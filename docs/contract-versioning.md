# Contract Versioning

The app-to-app protocol is versioned separately from any one questionnaire.

## Envelope Version

`protocol_version` is the launch/result compatibility boundary. The current
value is:

```text
quest.questionnaire.v1
```

For v1, callers must continue to validate:

- request id;
- nonce;
- result schema;
- terminal status;
- questionnaire id and minimum questionnaire version;
- current stage;
- screen sequence;
- questionnaire-specific answer shape.

## Compatible v1 Changes

The protocol can add optional fields to the request or result envelope without
changing `protocol_version`. Callers must ignore optional fields they do not
understand.

Compatible examples:

- adding optional result metadata such as `timing`;
- adding new non-sensitive `terminal.reason` values;
- adding a new questionnaire renderer with a new `schema_id`;
- adding optional answer fields inside a questionnaire-specific payload.

## Incompatible Changes

Use a new protocol version if a change requires existing callers to change
their envelope parser or terminal-state logic.

Incompatible examples:

- removing or renaming required fields;
- changing nonce/request matching semantics;
- changing the meaning of `completed`, `cancelled`, or `error`;
- moving answers into callback extras;
- replacing the caller-owned `content://` result URI flow.

## Status Enum Policy

The v1 status enum is closed:

```text
completed
cancelled
error
```

Do not add a new terminal status inside v1. Use `terminal.reason` for
non-sensitive details such as `user_cancelled` or `renderer_runtime_error`.

## Questionnaire Versioning

Questionnaire content is identified by:

```json
{
  "questionnaire": {
    "id": "brb-questionnaire-v1",
    "version": 1
  }
}
```

`schema_id` in the request selects the questionnaire renderer. The result
`questionnaire.id` must match the expected renderer id, and
`questionnaire.version` must be at least the caller's required minimum.

Use a new questionnaire id or major version when stage names, answer buckets,
or required answer semantics become incompatible. Keep BRB-specific rules in
BRB validators; keep generic envelope validation in `questionnaire-contract-core`.

## Stage Names

Stage names are questionnaire-owned strings. The v1 request schema requires
non-empty strings and requires `open_stage` to appear in `screen_sequence`, but
it does not globally enumerate all possible stages. Renderer factories and
questionnaire-specific validators own stage support.
