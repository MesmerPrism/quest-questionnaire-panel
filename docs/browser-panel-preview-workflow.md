# Browser Panel Preview Workflow

This workflow describes how to make a local browser-based preview for a Quest
2D panel while keeping the result portable to the native panel app.

The browser preview is a fast iteration surface. It is not the product runtime,
does not replace the Android IPC contract, and must not become a separate
authority for questionnaire state, study data, device control, or panel launch
behavior. Its job is to let developers and designers quickly inspect layout,
grouping, control density, and interaction states before implementing or
finalizing the same surface in the native Quest panel.

## When To Use It

Use a browser preview when the next panel slice needs quick iteration on:

- page structure, tabs, or staged flows;
- foldout groups and nested parameter sections;
- dense labeled control rows;
- validation, disabled states, and completion states;
- selectable option sets, ranges, and default values;
- panel copy, spacing, and responsive behavior inside the Quest panel frame.

Do not use it as the main path for:

- caller-owned `content://` result URI behavior;
- `PendingIntent` completion;
- Android permission behavior;
- headset install, launch, or focus validation;
- high-rate runtime data transport;
- final accessibility or controller/hand input validation.

Those remain native Android, Compose, caller SDK, and headset validation work.

## Required Structure

Every browser preview that is intended to translate into the Quest panel must
have an explicit transfer plan. The minimum shape is:

```text
tools/<panel-name>-browser-preview/
  Start-<PanelName>BrowserPreview.ps1
  index.html
  styles.css
  panel-preview.js
  fixtures/
    default-state.json
    edge-cases.json
```

The preview can be plain static HTML, CSS, and JavaScript. Avoid build tooling
unless it is needed for a specific reason. The preview should open locally from
PowerShell without a server when possible:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\<panel-name>-browser-preview\Start-<PanelName>BrowserPreview.ps1
```

If a server is required, keep it local-only, document the port, and make the
start script fail clearly when the port is occupied.

## Source Of Truth

The browser must not be the only place where product rules live. Any control
that affects panel behavior should be backed by an explicit model that can be
mapped to native code.

For each control, record:

- stable field id;
- display label;
- control type;
- default value;
- valid minimum and maximum, if numeric;
- step size, if numeric;
- option list, if selectable;
- whether the field is editable, read-only, or conditionally editable;
- validation rule and error text;
- result JSON field or native state field it maps to;
- page, group, and foldout path.

Small previews may keep that model in `panel-preview.js`. Larger previews
should put it in JSON fixtures or a shared Kotlin/JSON schema so the browser
and Compose implementation can be compared mechanically.

## Transferable UI Elements

Use elements that have a direct native panel equivalent. The current approved
set is:

| Browser preview | Native panel equivalent |
| --- | --- |
| page tabs or segmented buttons | Compose tab row, segmented buttons, or stage navigation |
| `details` / `summary` foldouts | Compose expandable section |
| nested foldouts | Compose nested expandable section, used sparingly |
| labeled rows | Compose `Row` with stable label/value/control slots |
| number input | Compose numeric text field with validation |
| range input plus number input | Compose slider plus numeric field |
| select dropdown | Compose exposed dropdown or menu |
| checkbox | Compose checkbox or switch |
| read-only value | Compose text/value chip with disabled styling |
| disabled input | Compose disabled control with same reason in state |
| validation message | Compose inline error text or blocked submit state |
| export JSON button | debug-only state export or fixture writer |

Foldouts and multiple pages are allowed and encouraged for dense panels. Use
them to keep the first view scannable, but make sure collapsed sections still
have clear labels and stable default states.

## Avoid Browser-Only Behavior

Do not depend on behavior that will not survive translation to the Quest panel:

- hover-only controls;
- tiny click targets that are not usable by hand or controller rays;
- desktop keyboard shortcuts as required actions;
- browser-native widgets whose look or behavior is essential but hard to match;
- unconstrained CSS layouts that only work at desktop monitor sizes;
- scroll regions nested deeply enough to conflict with panel pointing;
- local storage as product state;
- canvas-only custom editors without a Compose implementation plan;
- CSS animations that hide layout or validation problems.

Canvas previews are acceptable for read-only graphs, curves, meters, or
decorative previews. If a canvas becomes editable, document the native
interaction model before relying on it.

## Layout Constraints

Design against the same panel bounds used by native render checks:

- Quest panel frame: `1080dp x 720dp`;
- current host export target: `1350px x 900px` at `200dpi`;
- landscape orientation;
- no system UI in the preview frame.

The browser preview should constrain the main panel to this aspect ratio. It
may center the panel in the browser window, but the panel content itself should
not rely on extra desktop width or height.

Controls should be usable with hand/controller rays:

- prefer clear row heights over dense desktop tables;
- keep primary controls large enough for stable pointing;
- avoid hover-only disclosure;
- make disabled state visible without relying on tooltip text;
- keep text within its container at the target frame size.

## State And Export

The preview should expose a deterministic state export. At minimum, include a
button that writes the current preview state as formatted JSON to the page and
attempts to copy it to the clipboard.

The exported state should use the same stable ids as the native panel state.
It should include page/group/foldout state only when that state matters for the
native app or for reproducing a layout bug.

Example shape:

```json
{
  "panel_id": "example.panel",
  "schema_version": 1,
  "pages": [
    {
      "id": "setup",
      "groups": [
        {
          "id": "participant",
          "open": true,
          "fields": [
            {
              "id": "language",
              "type": "select",
              "value": "en",
              "options": ["en", "de"]
            }
          ]
        }
      ]
    }
  ]
}
```

Do not put participant data, device serials, private package names, APK hashes,
or local machine paths into committed fixtures.

## Native Implementation Requirements

Before a browser preview is treated as implemented, the native panel must have:

1. A Compose implementation for each approved browser control.
2. The same page/group/foldout organization or a documented reason for a
   native-specific adjustment.
3. The same field ids and validation rules.
4. The same default values and option lists.
5. Disabled/read-only behavior matching the browser preview.
6. Paparazzi render coverage for representative pages and important states.
7. Updated request/result or renderer fixtures when the panel contract changes.

The native app is authoritative. If the browser and Compose implementation
disagree, update the browser preview or document the intentional difference.

## Validation Path

Use the fastest validation that matches the risk:

1. Browser inspection for layout, copy, grouping, and basic interaction.
2. JSON export comparison against known fixtures.
3. Compose unit or view-model tests for state and validation rules.
4. Paparazzi render export:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\tools\Export-PanelRenderSequence.ps1
   ```

5. Headset validation when changing focus, launch behavior, hand/controller
   interaction, text input, scrolling, or anything dependent on Horizon panel
   behavior.

Browser approval alone is not enough for a production panel change.

## Promotion Checklist

Before moving a browser-previewed panel slice into the product path:

- the preview opens from its documented script;
- the preview is constrained to the Quest panel frame;
- all controls have stable ids;
- numeric controls define minimum, maximum, step, and default;
- selectable controls define the full option set;
- conditionally editable fields show disabled state correctly;
- foldouts and tabs have deterministic default state;
- exported JSON is stable and free of private artifacts;
- Compose implements the same controls and validation;
- Paparazzi snapshots cover the changed pages;
- public artifact checks pass before commit.

Run the public artifact guard before committing public docs or fixtures:

```powershell
python tools\check_public_artifacts.py
```

