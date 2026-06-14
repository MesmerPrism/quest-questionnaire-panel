# Sideload Self-Update Path

This app supports an off-store update path for privately deployed, sideloaded
builds. The path is intentionally separate from the questionnaire result
contract.

## Constraints

- Normal Android apps cannot silently replace themselves on unmanaged devices.
  The app can download and verify an APK, then hand it to Android's package
  installer. The user should expect an installer confirmation screen.
- The replacement APK must use the same package name and signing certificate as
  the installed app, or Android will reject it as an update.
- This path is for internal/sideload distribution, not Meta Store/App Lab
  distribution.
- Do not treat this as the managed Quest fleet-update path without written
  confirmation from Meta or the MDM vendor. The current managed-device research
  direction is that Quest fleets should use only a vendor-confirmed Quest
  management path. Meta's 2026 update stops selling Horizon managed services
  and commercial Quest SKUs while continuing support through 2030, and app-side
  APK download/install behavior on managed Quest devices still needs policy
  confirmation.
- Do not use public shared storage, `file://`, MediaStore, overlays,
  package-killing, or ADB as the product update path.

## Termux Fleet-Update Boundary

A separate lab/fleet operations path can update this APK through Termux when a
Quest already has developer mode, an operator-authorized WiFi ADB endpoint, and
a running Termux process or operator-launched Termux restart helper. In that
setup, Termux runs an `adb` client back to `127.0.0.1:5555`; if `adb shell id`
reports `uid=2000(shell)`, a bounded operations agent can run `adb install -r`
for a verified APK without the normal Android installer confirmation screen.

Keep that path separate from this app's product behavior:

- the panel app does not create or recover ADB authorization;
- Termux is not Android shell authority unless it is using the active,
  user-approved ADB lease;
- WiFi ADB can disappear after reboot, adbd restart, timeout, or user
  revocation;
- a normal helper APK may restart a stopped Termux agent only after it has been
  installed, launched, granted Termux `RUN_COMMAND`, and Termux allows external
  commands;
- Termux ADB subprocesses need a writable temp directory such as `$PREFIX/tmp`;
- APKs should be downloaded or staged where Termux can read them, preferably
  Termux-private storage rather than public shared storage;
- `/data/local/tmp` can be used as an external ADB lab staging fallback, but it
  is not the panel app's update store.

For public-safe fleet-agent notes, use the Quest Termux Lab documentation. This
repository should keep only the panel-specific contract and updater boundary.

When headsets have internet but are not on the same WiFi as an operator
machine, the trigger should be outbound: a central HTTPS controller queues a
verified-update command, and the Termux agent on each headset polls for it. Do
not design the trigger around inbound ADB from an operator laptop unless this
is a local recovery session. If the agent is stopped, the controller cannot
self-wake it; use a visible/pre-granted helper or external/user recovery, then
confirm fresh heartbeats and `uid=2000(shell)` before update install.

## Build Configuration

The recommended public app flavor is `minimal`. It exposes the questionnaire
IPC activity only and does not declare internet or package-install
permissions:

```powershell
.\gradlew.bat :app:assembleMinimalDebug
```

The internal lab updater flavor declares:

- `android.permission.INTERNET`
- `android.permission.REQUEST_INSTALL_PACKAGES`
- a 2D launcher activity for update checks
- a private `FileProvider` for verified APK handoff to Android's installer

Build a lab updater APK with an HTTPS update manifest URL:

```powershell
.\gradlew.bat :app:assembleLabUpdaterDebug `
  -PquestQuestionnaireUpdateManifestUrl=https://example.com/quest-questionnaire-panel/update.json
```

On non-Windows shells, use `./gradlew`.

For lab update workflow tests, build a newer APK without editing source:

```powershell
.\gradlew.bat :app:assembleLabUpdaterDebug `
  -PquestQuestionnaireVersionCode=2 `
  -PquestQuestionnaireVersionName=0.1.1-lab
```

## Manifest Format

```json
{
  "schema": "quest-questionnaire-panel.update.v1",
  "packageName": "io.github.mesmerprism.questquestionnaire.panel",
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "https://example.com/quest-questionnaire-panel/app-release.apk",
  "sha256": "64 lowercase or uppercase hex characters",
  "releaseNotes": "Optional short release notes.",
  "publishedAt": "2026-06-11T00:00:00Z"
}
```

The app accepts only HTTPS manifest and APK URLs. It validates package name,
version code, SHA-256, and APK archive metadata before starting the installer.

## Runtime Flow

1. Open the questionnaire panel app from Unknown Sources / app launcher.
2. Select `Check`.
3. If a newer manifest is available, select `Download`.
4. After SHA-256 and APK metadata validation, select `Install`.
5. If Android says this source is not trusted to install packages, approve the
   app as an install source in the system settings prompt, then return and
   install again.

## Validation

Run parser tests:

```powershell
.\gradlew.bat :app:testLabUpdaterDebugUnitTest
```

Build both app flavors:

```powershell
.\gradlew.bat :app:assembleMinimalDebug :app:assembleLabUpdaterDebug
```

Headset validation still needs a real signed newer APK and an HTTPS endpoint
serving both the manifest and APK.

For 100+ device fleets, validate an HMS-backed XR MDM or another
vendor-confirmed Quest deployment path first. Use the app-side updater only for
unmanaged/internal lab devices where operator confirmation is acceptable.
