# Repository Instructions

This is a Quest app repo intended for public source distribution. Keep raw
participant data, device serials, APKs, screenshots, logcat bundles, signing
keys, local machine paths, and private evidence artifacts out of committed
files.

Use the local `meta-quest-workflow` skill for Quest app decisions. For the
cross-app questionnaire pattern, follow the caller-owned `content://` result
URI contract:

- foreground caller launches the 2D panel with an explicit intent;
- request metadata travels in extras or request JSON;
- answers are written to the caller-owned result URI;
- a broadcast `PendingIntent` is the default completion signal;
- activity-return `PendingIntent` is optional and must be validated against
  Android background-activity-launch rules;
- avoid public shared storage, MediaStore, broad FileProvider roots, Termux
  file drops, ADB relaunches, force-stop, package killing, and overlay tricks.

Before committing Android implementation work, run the narrowest available
checks:

```powershell
git diff --check
```

When Gradle is available:

```powershell
gradle :app:assembleDebug
gradle :examples:native-caller:assembleDebug
```
