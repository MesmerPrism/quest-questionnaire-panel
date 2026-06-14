# Speech Asset Generation And Integration Protocol

Use this protocol when adding participant-facing speech elements to the Quest
questionnaire panel.

## Core Rules

- Keep API keys out of chat and source. Provide ElevenLabs credentials through a
  local ignored file or environment variable only.
- Generate drafts under ignored `artifacts/` folders first. Copy only approved
  final MP3s, scripts, transcript stubs, and back-translations into
  `app/src/main/assets/BRBStudyAudio/localized`.
- Use stable audio IDs before generation. Keep IDs ordered by runtime position,
  for example `aud_0320` and `aud_0330` for the IPQ history narration.
- Participant-facing questionnaire narration can use dry science-nerd sarcasm,
  but must not reveal factor names, subscale names, scoring machinery, internal
  variables, or hypotheses.
- Add about 1000 ms of tail silence to ElevenLabs speech MP3s to avoid clipped
  endings on headset playback.
- Preserve the full imported BRB audio inventory when porting from the Unity
  study branch. The split-app runtime may play only a subset in the panel, but
  condition-session stimuli and stems remain part of the source audio design
  for provenance and parity.

## Generation

1. Write one script file per audio ID and locale.
2. For ElevenLabs, use the approved project voice/model settings and record
   voice ID, model ID, language code, output format, duration, and SHA-256.
3. Put punctuation and pauses directly into the script. Do not rely on a later
   runtime delay to create performance timing.
4. Generate into `artifacts/<task-name>/`, then add tail silence and record the
   final duration plus SHA-256.
5. Listen for clipped endings, missing pauses, wrong language, overlong
   duration, or psychometric spoilers before promoting the file.

## Library Promotion

For each approved speech element, copy files into the localized library:

- English MP3: `app/src/main/assets/BRBStudyAudio/localized/en_us/<audio_id>_<clip_key>__en_us.mp3`
- Japanese MP3: `app/src/main/assets/BRBStudyAudio/localized/ja_jp/<audio_id>_<clip_key>__ja_jp.mp3`
- Script files: `app/src/main/assets/BRBStudyAudio/localized/transcripts/<locale>/<audio_id>_<clip_key>__<locale>.script.txt`
- Transcript JSON stubs: same folder with `.json`
- Japanese back-translation: `app/src/main/assets/BRBStudyAudio/localized/transcripts/ja_jp/<audio_id>_<clip_key>__ja_jp.backtranslation.txt`

Then update `app/src/main/assets/BRBStudyAudio/localized/manifest.json` with
`audioId`, stage, role, participant-facing status, translation policy, runtime
cue, per-locale paths, durations, hashes, generation metadata, and Japanese
back-translation paths.

## Lookup Table

Add one row per audio ID per locale to
`docs/audio/audio-script-lookup-table.csv`.

Each row should include the runtime hook/log marker, package asset path,
library path, SHA-256, duration, generation provider/model/voice, script path,
translation policy, and a short maintenance note.

## Runtime Wiring

For panel-routed speech:

- Add explicit Kotlin constants or manifest-backed mappings for audio IDs and
  asset paths.
- Select English or Japanese by the panel language selection state.
- Use APK asset playback for localized files and provide an English fallback
  for missing localized assets.
- Log a stable marker with condition, cue, audio ID, asset, language, trigger,
  and whether the clip gates participant action.
- Keep long narration non-blocking unless the study design explicitly says the
  participant must wait.

## Validation

Before claiming the integration is ready:

1. Recalculate and review manifest hashes/durations.
2. Confirm all referenced localized audio files exist.
3. Add or update panel tests for new runtime markers or answer shapes.
4. Build the APK with `.\gradlew.bat :app:assembleMinimalDebug`.
5. Run the smallest relevant headset gate. For questionnaire speech that starts
   from a questionnaire transition, use the debug panel smoke or a headset
   smoke that observes the cue marker.

Local validation alone is not final completion for Quest-facing panel changes.
