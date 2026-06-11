# Contract

The first protocol version is `quest.questionnaire.v1`.

Contract files:

- `intents.md` documents action names, extras, package/component defaults, and
  URI grant shape.
- `quest.questionnaire.v1.request.schema.json` describes launch request JSON.
- `quest.questionnaire.v1.result.schema.json` describes result JSON.

Caller-owned result URI is required for product flows. The result `content://`
URI is carried as Intent data for the write grant and also in `result_uri` for
simple app-side lookup.
