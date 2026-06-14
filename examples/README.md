# Examples

`native-caller` is the first required tester. It simulates a foreground XR
caller without depending on Unity or OpenXR. It must prove:

- caller-owned private result file;
- narrow `FileProvider` result URI;
- explicit questionnaire launch;
- write-only URI grant;
- broadcast `PendingIntent` completion callback;
- result readback and nonce/request validation.

The example uses `android-caller-sdk` for launch and recovery mechanics, then
adds BRB-specific answer validation through `brb-questionnaire-core`.

Generic non-BRB fixtures live in `contract/examples/request.generic.valid.json`
and `contract/examples/result.generic.completed.valid.json`.

Unity caller scaffolding lives in `unity-caller-plugin/`.
