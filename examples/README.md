# Examples

`native-caller` is the first required tester. It simulates a foreground XR
caller without depending on Unity or OpenXR. It must prove:

- caller-owned private result file;
- narrow `FileProvider` result URI;
- explicit questionnaire launch;
- write-only URI grant;
- broadcast `PendingIntent` completion callback;
- result readback and nonce/request validation.

Unity and BRB examples come after this native tester passes on device.
