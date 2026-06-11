# Unity Caller Plugin

Planned Unity Android bridge wrapper.

Do not start here until:

1. The native panel app builds.
2. The native caller tester proves the content URI plus broadcast callback path
   on Quest.
3. The Unity BRB project is cloned on `S:`.

Expected shape:

- Android plugin wraps `android-caller-sdk`.
- C# facade creates BRB stage requests.
- Unity caller receives a validated status/result reference.
- Native and Unity callers share the same JSON schemas in `contract/`.
