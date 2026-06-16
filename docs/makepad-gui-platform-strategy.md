# Makepad GUI Platform Strategy

Snapshot date: 2026-06-15.

This note records the recommended Makepad source and packaging strategy for
Rusty/Morphospace GUI apps, including the Windows questionnaire operator and
future cross-platform app shells.

## Decision

Use Makepad as an app-shell/UI dependency, not as a core runtime dependency.
For Rusty/Morphospace work, standardize on a pinned git Makepad source rather
than the crates.io `makepad-widgets` release.

Recommended baseline:

| Use case | Makepad source | Rationale |
| --- | --- | --- |
| Windows-only prototypes | Upstream `makepad/makepad` `dev`, pinned to a commit | Best fit when the app does not need Quest/XR-specific patches and should track upstream closely. |
| Rusty/Morphospace app shells, Quest-adjacent tools, Android/Quest builds | `MesmerPrism/makepad`, pinned to a commit | Carries the Morphospace patch queue for Android, Quest, packaging, Vulkan, OpenXR, and app-shell integration work. |
| Published desktop installers | Same pinned Makepad source as the app, packaged with `cargo-packager` | Official Makepad docs point desktop packaging to `cargo-packager`; Robius projects show a practical Makepad resource-packaging pattern. |
| Shared questionnaire/operator UI widgets | A small shared widget crate layered on top of the chosen Makepad source | Keeps field alignment, status badges, device rows, command buttons, and proof-run panels consistent across apps. |

Do not make the core questionnaire contract, Rusty Morphospace runtime,
Manifold/Matter/Optics/Lattice authority, or Quest handoff semantics depend on
Makepad. Makepad owns only presentation, controls, and operator workflows.

## Source Findings

Official Makepad:

- The upstream repo is `makepad/makepad`, with `dev` as the default branch.
  It is active as of this snapshot and remains the framework source of truth:
  <https://github.com/makepad/makepad>
- The Makepad book describes native and web support across Windows, Linux,
  macOS, iOS, and Android:
  <https://makepad.rs/guide/start/introduction>
- Official packaging guidance says desktop packaging is not handled by a
  Makepad-specific packager and recommends `cargo-packager`; Android, iOS, and
  WASM use `cargo-makepad`:
  <https://makepad.rs/guide/appendix/packaging-guide>
- The published crates.io `makepad-widgets` release is `1.0.0`, updated on
  2025-05-13. It is usable, but it lags active development and lacks several
  APIs and fixes visible on current git branches:
  <https://crates.io/crates/makepad-widgets>

Morphospace Makepad fork:

- The maintained Morphospace fork is `MesmerPrism/makepad`:
  <https://github.com/MesmerPrism/makepad>
- Its value is not general ownership of Makepad. It is a controlled patch
  queue for app-shell, renderer, Android/Quest, Vulkan, OpenXR, packaging, and
  tooling needs that upstream Makepad does not yet cover for this project.
- As of this snapshot, the fork has meaningful divergence from upstream. Treat
  it as infrastructure that needs explicit versioning, validation, and periodic
  upstream reconciliation.

Robius reference projects:

- `project-robius/robrix` is a serious cross-platform application using
  Makepad, Robius platform crates, and pinned Makepad git dependencies:
  <https://github.com/project-robius/robrix>
- `robius-packaging-commands` provides cargo-packager hook patterns for
  Makepad resource packaging on Windows, macOS, and Linux:
  <https://github.com/project-robius/robius-packaging-commands>
- `makepad_wonderous` is a useful example for Makepad commands across desktop,
  Android, iOS, and WASM:
  <https://github.com/project-robius/makepad_wonderous>

Public fork sweep:

- A broad GitHub fork sweep did not reveal a more authoritative general-purpose
  Makepad fork than upstream plus the existing Morphospace fork.
- The Robius ecosystem is the most useful external reference for real
  cross-platform app packaging and service integration, but it is not a direct
  replacement for our Makepad baseline.

## Platform Guidance

Windows:

- Build native Makepad apps with Cargo during development.
- Package release installers with `cargo-packager`, usually NSIS for Windows.
- Ensure Makepad resources and fonts are included in package metadata or a
  packaging hook. Missing resources are a common failure mode for packaged
  Makepad apps.
- For operator apps, every GUI action must have an equivalent CLI command.

Linux and macOS:

- Treat these as first-class native desktop targets when the app has no
  Windows-only ADB or installer assumptions.
- Use `cargo-packager` for application bundles and packages.
- Keep resource inclusion identical to Windows where possible.

Android and Quest:

- Use `cargo-makepad` from the same pinned Makepad source as the app.
- Quest-specific app shells should use the Morphospace fork unless a given
  feature has been upstreamed and verified.
- Keep Quest questionnaire launching inside the caller-owned Android contract:
  explicit intent, caller-owned `content://` result URI, and completion signal.
  Do not move questionnaire launch authority into desktop tooling.

iOS:

- Makepad can target iOS, but practical support depends on Apple signing,
  provisioning, and Mac build infrastructure. Treat this as a later packaging
  lane rather than a requirement for the questionnaire operator.

WASM:

- Useful for demos, documentation, and lightweight previews.
- Not a good primary surface for the Windows operator because ADB, local device
  tooling, and bridge-forward workflows need native host access.

## Current Operator Implication

The current Windows questionnaire operator uses crates.io `makepad-widgets`
`1.0.0`. That was reasonable for quickly proving the Makepad operator surface,
but it is not the right long-term dependency for platform work.

Implementation note, 2026-06-15: the operator has been moved from crates.io to
the upstream Makepad `1.0.0` git commit
`0abdc02f2255501062993c489806618a9fc6261b`, recorded in
`operator/makepad-quest-questionnaire-operator/MakepadBaseline.toml`. A direct
move to the current Morphospace fork was tested and found to require the newer
Makepad script API, so that migration should be handled as a separate
app-shell upgrade.

Next dependency moves:

1. Keep the operator on the recorded upstream `makepad/makepad` 1.0.0 git pin
   until the app-shell is ready for Makepad's script API.
2. Move to `MesmerPrism/makepad` when the operator needs shared Morphospace
   widgets, packaging behavior, or cross-target validation from the fork.
3. Record every Makepad source change in the app-local baseline file.

## Shared Widget Layer

Create a small shared Makepad widget crate for Rusty/Morphospace operator apps.
It should contain:

- centered text input behavior;
- labeled fields, compact numeric fields, and disabled placeholders;
- status badges for Quest readiness, foreground state, battery state, and
  controller state;
- command buttons with CLI-equivalent metadata;
- operator logs and proof-run summaries;
- common spacing, typography, and color tokens.

This avoids fixing UI behavior separately in every Makepad app.

## Text Input Alignment Verdict

The observed text input issue is not just a local padding mistake. Current
Makepad text input behavior centers the line/layout box, not necessarily the
visible glyph ink. The crates.io `1.0.0` API also keeps the relevant internals
private, so an app-level wrapper has limited leverage.

Recommended fix:

1. Implement a generic centered single-line text input in the shared widget
   layer or Makepad fork.
2. Prefer a real vertical alignment mechanism based on text metrics or
   Makepad's text layout state.
3. Avoid per-field visual offsets as the default solution.
4. Add screenshot or pixel checks for short text, long text, placeholder text,
   disabled fields, and narrow numeric fields.
5. If the fix is generally useful and small, propose it upstream.

## Baseline Manifest

Each Makepad app should record:

```toml
[makepad]
source = "git"
repository = "https://github.com/makepad/makepad.git"
commit = "<pinned-commit>"
cargo_makepad_commit = "<pinned-commit>"

[targets]
windows = true
linux = true
macos = true
android = false
quest = false
ios = false
wasm = false
```

For apps using the Morphospace fork, the repository should be
`https://github.com/MesmerPrism/makepad.git` and the enabled target list should
match the actual validation lane.

## Validation

For a Makepad app:

```powershell
cargo metadata --manifest-path <app>\Cargo.toml --format-version 1
cargo check --manifest-path <app>\Cargo.toml
cargo build --manifest-path <app>\Cargo.toml
```

For desktop packaging:

```powershell
cargo packager --release --formats nsis
```

For a Makepad fork:

```powershell
python tools\makepad_fork_format.py --changed --check
python tools\check_morphospace_makepad_guards.py
python tools\check_android_generated_output_stability.py
cargo metadata --format-version 1
cargo check -p cargo-makepad
cargo build -p cargo-makepad --release
```

For Quest or Android validation, use the local Quest workflow and reserve
shared device/ADB resources before touching the headset or `adb-server`.

## Risks And Mitigations

| Risk | Mitigation |
| --- | --- |
| crates.io Makepad lags active development | Use pinned git dependencies for serious apps. |
| Morphospace fork drifts too far from upstream | Keep a patch ledger, record baseline commits, and reconcile with upstream on a fixed cadence. |
| App-specific UI fixes multiply | Move reusable widgets into a shared crate and screenshot-test them. |
| Desktop packages miss Makepad resources | Adopt `cargo-packager` resource metadata or Robius-style packaging hooks. |
| Quest authority leaks into desktop tooling | Keep desktop apps as command operators only; Rusty Morphospace remains the Android caller. |
| Makepad reaches into core Rusty runtimes | Enforce dependency boundaries: Makepad only in app-shell/UI crates. |

## Next Slice

1. Decide whether the next Makepad migration is a full script-API app-shell
   migration or a targeted Makepad 1.0 fork patch for text input metrics.
2. Promote the crate-local operator field/button/panel styles into a shared
   Rusty/Morphospace Makepad widget crate once another operator app needs them.
3. Replace `TextInput` internals only in a Makepad fork or deliberate custom
   widget, where glyph metrics and cursor/selection behavior can be validated
   together.
4. Add automated screenshot checks for the operator's main layouts and field
   crops.
