# Contributing

Thanks for helping improve Quest Questionnaire Panel. This repository is meant
to stay usable as a public reference for Quest questionnaire handoffs, so keep
changes focused, reproducible, and safe to publish.

## Start Here

- Read the project overview in `README.md`.
- Read the public onboarding site at
  <https://mesmerprism.com/quest-questionnaire-panel/>.
- For app-to-app integration work, read `docs/handoff-contract.md`,
  `contract/intents.md`, and the request/result schemas in `contract/`.
- For Unity callers, start with `unity-caller-plugin/README.md`.
- For native Android callers, start with `android-caller-sdk/README.md` and
  `examples/native-caller/`.

## Public Safety Boundary

Do not commit raw participant data, device serials, APKs, screenshots, logcat
bundles, signing keys, local machine paths, private evidence artifacts, or raw
headset recordings. Only reviewed public documentation and curated public media
belong in the repository.

Before committing public docs or media, run:

```powershell
python tools\check_public_artifacts.py
```

## Useful Checks

Run the narrowest check that matches your change.

```powershell
git diff --check
```

For Android implementation work, also run the relevant Gradle build when the
toolchain is available:

```powershell
.\gradlew.bat :app:assembleMinimalDebug
.\gradlew.bat :examples:native-caller:assembleDebug
```

## Contribution Areas

- Panel UI and renderer behavior in `app/`.
- Protocol models and validation in `questionnaire-contract-core/`.
- BRB-specific stage constants and answer validation in
  `brb-questionnaire-core/`.
- Native Android integration helpers in `android-caller-sdk/`.
- Unity caller helpers in `unity-caller-plugin/`.
- Public integration docs in `docs/` and `contract/`.

Open issues or pull requests against
<https://github.com/MesmerPrism/quest-questionnaire-panel>. Include the goal,
changed module, relevant checks, and any Quest/headset validation evidence that
is safe to publish.
