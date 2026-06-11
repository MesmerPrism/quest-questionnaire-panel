# Validation Matrix

| Area | Test | Evidence |
| --- | --- | --- |
| Build | `:app:assembleDebug` succeeds. | Gradle output. |
| Build | `:examples:native-caller:assembleDebug` succeeds. | Gradle output. |
| Contract | Request parser accepts valid `quest.questionnaire.v1`. | Unit/instrumented test. |
| Contract | Request parser rejects missing request id, nonce, stage, or result URI. | Unit/instrumented test. |
| URI grant | Panel can write caller result URI. | Caller result readback. |
| URI scope | Panel cannot read unrelated caller files. | Negative test/logcat. |
| Callback | Broadcast `PendingIntent` fires after stream close. | Receiver timestamp and result file mtime. |
| Idempotency | Duplicate callback/result read is ignored or stable. | Caller status. |
| Recovery | Caller process death after launch still recovers on resume/cold start. | Manual or scripted test. |
| Panel death | Panel death mid-form leaves request pending/cancelled/relaunchable. | Caller status. |
| Android 11+ | Package visibility uses narrow `<queries>`. | Manifest review and install test. |
| Android 12+ | Exported components and PendingIntent mutability are explicit. | Manifest review/build. |
| Android 14/15+ | Broadcast path works without UI-start permission. | Logcat has no BAL block for default path. |
| Quest panel | Panel opens as 2D Quest surface and accepts controller/hand input. | Headset observation/screenshot. |
| Privacy | Answers absent from logs, filenames, notifications, and public storage. | Logcat/file review. |
| Product path | No ADB, Termux, public storage, force-stop, package killing, or Meta menu needed after install. | Run notes. |

## Debug Smoke Harness

Debug builds support an agent-addressable smoke route for Quest validation when
UIAutomator cannot inspect Quest 2D panel contents. Launch the native caller
with boolean extra
`io.github.mesmerprism.questquestionnaire.extra.DEBUG_RUN_SMOKE=true`; the
caller still creates the caller-owned `content://` result URI and immutable
broadcast `PendingIntent`, then passes
`io.github.mesmerprism.questquestionnaire.extra.DEBUG_AUTO_SUBMIT=true` to the
panel. The panel ignores that extra outside debug builds.

This harness is validation-only. It must not replace the product path, and the
result JSON remains the only carrier for answer status and answer data.
