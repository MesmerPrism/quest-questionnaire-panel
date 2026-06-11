# Handoff Prompt

Use this prompt to start a new Codex thread in this repo:

```text
We are working in the quest-questionnaire-panel repository root.

Use the meta-quest-workflow skill. Read AGENTS.md, README.md,
docs/WORKPLAN.md, docs/handoff-contract.md, docs/validation-matrix.md, and
contract/intents.md first.

Goal for this thread: implement Phase 1 and Phase 2 far enough that the native
Quest 2D questionnaire panel app and the minimal native caller tester build.

Context:
- This repo is a standalone Quest 2D questionnaire panel project.
- The product communication pattern is caller-owned content:// result URI plus
  one-shot immutable broadcast PendingIntent completion.
- The first app is app/ with package
  io.github.mesmerprism.questquestionnaire.panel.
- The first tester is examples/native-caller with package
  io.github.mesmerprism.questquestionnaire.nativecaller.
- Keep request metadata in extras/request_json.
- Write answers only to the caller-owned result URI.
- Do not use ADB, Termux, public shared storage, force-stop, package killing,
  MediaStore, file://, QUERY_ALL_PACKAGES, SYSTEM_ALERT_WINDOW, or overlay
  tricks as product communication.

Start by checking the Gradle skeleton. Make the smallest code/build fixes
needed for:
1. gradle :app:assembleDebug
2. gradle :examples:native-caller:assembleDebug

Then add focused parser/result validation tests or a small test harness if the
repo setup supports it. Keep edits scoped to getting the panel and caller
tester buildable and contract-correct. Do not start Unity integration yet.
```
