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
  direction is that Quest fleets should use Meta-managed enrollment, Meta Device
  Manager, or a Quest-capable MDM for APK deployment, while app-side APK
  download/install behavior on managed Quest devices needs policy confirmation.
- Do not use public shared storage, `file://`, MediaStore, overlays,
  package-killing, or ADB as the product update path.

## Build Configuration

The panel app declares:

- `android.permission.INTERNET`
- `android.permission.REQUEST_INSTALL_PACKAGES`
- a 2D launcher activity for update checks
- a private `FileProvider` for verified APK handoff to Android's installer

Build an APK with an HTTPS update manifest URL:

```powershell
.\gradlew.bat :app:assembleDebug `
  -PquestQuestionnaireUpdateManifestUrl=https://example.com/quest-questionnaire-panel/update.json
```

On non-Windows shells, use `./gradlew`.

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
.\gradlew.bat :app:testDebugUnitTest
```

Build the app:

```powershell
.\gradlew.bat :app:assembleDebug
```

Headset validation still needs a real signed newer APK and an HTTPS endpoint
serving both the manifest and APK.

For 100+ device fleets, validate Meta-managed Quest or XR-MDM deployment first.
Use the app-side updater only for unmanaged/internal lab devices where operator
confirmation is acceptable.
