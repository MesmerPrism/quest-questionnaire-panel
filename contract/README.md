# Contract

The first protocol version is `quest.questionnaire.v1`.

Contract files:

- `intents.md` documents action names, extras, package/component defaults, and
  URI grant shape.
- `quest.questionnaire.v1.request.schema.json` describes launch request JSON.
- `quest.questionnaire.v1.result.schema.json` describes result JSON.
- `../docs/contract-versioning.md` documents v1 compatibility rules.
- `../docs/research-data-safety.md` documents public-repo data safety rules.
- `examples/request.maia_spatial.*.valid.json` show the MAIA-2 plus
  spatial-frame-reference block launches for Rusty Morphospace/native callers.

Caller-owned result URI is required for product flows. The result `content://`
URI is carried as Intent data for the write grant and also in `result_uri` for
simple app-side lookup.

Result status is terminal and lives in JSON: `completed`, `cancelled`, or
`error`. Non-completed terminal results include a `terminal` object with the
non-sensitive reason, current stage, and zero-based screen index. Only `error`
results use the `error` object for a safe code/message pair.

Result JSON may include optional `timing` metadata. Timing is screen-level
only: top-level start/submit instants, monotonic duration, and one entry per
screen visit with entered, first answer-changing interaction, left, duration,
answer-changing interaction count, and validation-failure count. Timing must
not include gaze, hand pose, controller pose, high-frequency traces, raw
interaction events, or answer values.

Completed BRB answer payloads are sequence-scoped. Initial, post-condition,
and final launches require different answer buckets; callers should not assume
every BRB result contains demographics, post-condition, and final answers.

Stage names are questionnaire-owned. The v1 request envelope only requires
non-empty stage strings and `open_stage` membership in `screen_sequence`; BRB
stages are enforced by the BRB renderer and BRB answer validator. The generic
demo fixtures show the same envelope with `generic:*` stages.

Split-session questionnaires can use the optional `questionnaire_state` object
for small questionnaire-owned state that the caller copies from an earlier
result into a later launch. The MAIA/spatial renderer uses
`questionnaire_state.language_code` for Block 2 and Block 3 so the Block 1
language selection remains fixed across the session.
