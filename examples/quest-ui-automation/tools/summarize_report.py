#!/usr/bin/env python3
"""Summarize Quest UIAutomator JSONL reports without leaking raw UI dumps.

The instrumentation report contains useful evidence but can also include local
paths, installed app names, and full UI text. This host-side tool emits a small
public-safe summary from known event shapes and counts the labels it withholds.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, OrderedDict, defaultdict
from pathlib import Path
from typing import Any, Iterable


TARGET_TITLES = {
    "quest_link": "Link",
    "general": "General",
    "action_button": "Action button",
    "notifications": "Notifications",
    "space_setup": "Environment setup",
    "world_movement": "Movement",
    "movement_tracking": "Tracking",
    "accessibility": "Accessibility",
    "display_brightness": "Display & brightness",
    "audio": "Audio",
    "camera": "Camera",
    "privacy_safety": "Privacy & safety",
    "passcode_security": "Passcode & security",
    "experimental": "Experimental",
    "developer": "Developer",
    "help": "Help",
}

GLOBAL_SAFE_LABELS = {
    "Navigate to next screen",
    "On",
    "Off",
    "Low",
    "Medium",
    "High",
    "None",
    "Create",
    "Reset all to default",
}

DEFAULT_CHILD_PROBE_EXCLUDED_LABELS = {
    "Cloud backup",
    "Software update",
}

TARGET_SAFE_LABELS = {
    "quest_link": {
        "Link",
        "PC VR",
        "Turn on Link",
        "Open Dash",
        "Remote Desktop",
    },
    "general": {
        "General",
        "Software update",
        "Quick controls",
        "Storage",
        "Ongoing activities",
        "Cloud backup",
    },
    "action_button": {
        "Action button",
        "Short press",
        "Long press",
        "Passthrough",
    },
    "notifications": {
        "Notifications",
        "Do Not Disturb",
        "Position of notifications",
        "Available device notifications",
        "Meta Horizon",
        "Supervision",
        "Scoreboards",
        "Device",
        "Social",
        "Account",
        "Applications",
        "Top right",
    },
    "space_setup": {
        "Environment setup",
        "Boundary",
        "Travel mode",
        "Space setup",
        "Space setup is unavailable while the current app(s) are open. To manage your space setup, quit the app(s) first.",
    },
    "world_movement": {
        "Movement",
        "Movement control visual guide",
        "Movement style",
        "Movement direction",
        "Slide direction lock",
        "Acceleration style",
        "Rotation",
        "Slide",
        "Facing",
    },
    "movement_tracking": {
        "Tracking",
        "Show Virtual Hands",
        "Automatically switch between controllers and hands",
        "Expanded quick actions",
        "Hand tracking",
        "Body tracking",
        "Headset tracking",
        "Tracking frequency",
    },
    "accessibility": {
        "Accessibility",
        "Vision",
        "Mobility",
        "Hearing",
    },
    "display_brightness": {
        "Display & brightness",
        "Appearance",
        "Light & dark theme",
        "Light theme preview",
        "Dark theme preview",
        "Brightness",
        "Display brightness",
        "Night display",
        "Adaptive lighting",
    },
    "audio": {
        "Audio",
        "Mute microphone",
        "Volume",
        "App and media volume, 0%",
        "Do Not Disturb",
        "Notification sound",
        "Spatial audio for windows",
        "Worlds audio",
    },
    "camera": {
        "Camera",
        "Camera controls",
        "Casting and recording indicator",
        "Hide camera and call controls in captured or shared view",
        "Photo and video capture",
        "Capture markers",
        "Auto hide",
        "Aspect ratio",
        "Landscape 16:9",
        "Video recording",
        "Audio",
        "Include microphone audio in video recording",
        "Automatically adjust microphone and game audio balance",
        "Format and quality",
        "Bit rate",
        "Frame rate",
        "Image stabilization",
        "Eye perspective",
        "Video codec",
        "HEVC / H265",
    },
    "privacy_safety": {
        "Privacy & safety",
        "Safety Center",
        "Audience & visibility",
        "Social connections",
        "Device permissions",
        "App permissions",
        "Installed apps",
        "Data and analytics",
        "Permissions history",
    },
    "passcode_security": {
        "Passcode & security",
        "Passcode",
        "Require passcode for:",
        "Unlock headset",
        "Saved passwords",
    },
    "experimental": {
        "Experimental",
        "Reset experimental features",
        "Hide camera and call controls in apps",
        "External microphone",
        "Screen reader",
        "Content adaptive brightness",
        "Temporal dimming",
        "Positional time warp",
        "Lying down apps",
        "Wi-Fi QR code",
        "Seamless multitasking",
        "Surface keyboard",
    },
    "developer": {
        "Developer",
        "MTP Notification",
        "Scan apps for malware",
        "Physical space features",
        "Link Auto-Connect",
        "Hand tracking frequency override",
    },
    "help": {
        "Help",
        "Help & Tips app",
        "Support",
        "Report a problem",
    },
}

PATH_OR_RESOURCE_RE = re.compile(
    r"(^[A-Za-z]:\\|^/storage/|^/sdcard/|^/data/|:id/|^android:id/|^com[.])"
)
PACKAGE_OR_CLASS_RE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-zA-Z0-9_]+){2,}(:id/.*)?$")
NOISE_RE = re.compile(r"^[0-9\s%:._/-]+$")


def normalize_text(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def is_noise_text(text: str) -> bool:
    if not text:
        return True
    if PATH_OR_RESOURCE_RE.search(text) or PACKAGE_OR_CLASS_RE.match(text):
        return True
    if "/Android/data/" in text or "report.jsonl" in text:
        return True
    if NOISE_RE.match(text):
        return True
    return False


def allowed_labels_for_target(target: str) -> set[str]:
    labels = set(GLOBAL_SAFE_LABELS)
    labels.update(TARGET_SAFE_LABELS.get(target, set()))
    title = TARGET_TITLES.get(target)
    if title:
        labels.add(title)
    return labels


def public_label(text: Any, target: str) -> tuple[str | None, bool]:
    normalized = normalize_text(text)
    if is_noise_text(normalized):
        return None, False
    if normalized in allowed_labels_for_target(target):
        return normalized, False
    return None, True


def option_texts(option: dict[str, Any]) -> list[str]:
    texts = []
    for value in option.get("texts", []) or []:
        text = normalize_text(value)
        if text and not is_noise_text(text) and len(text) <= 80:
            texts.append(text)
    return texts


def int_value(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def summarize_events(
    lines: Iterable[str],
    source_name: str,
    max_labels: int,
    child_probe_risks: set[str],
    excluded_child_labels: set[str],
) -> dict[str, Any]:
    event_counts: Counter[str] = Counter()
    sections: OrderedDict[str, dict[str, Any]] = OrderedDict()
    section_scrolls: defaultdict[str, list[dict[str, Any]]] = defaultdict(list)
    route_inventory: OrderedDict[str, dict[str, Any]] = OrderedDict()
    child_probe_targets: OrderedDict[str, dict[str, str]] = OrderedDict()
    ui_dumps: list[dict[str, Any]] = []
    surface_snapshots: list[dict[str, Any]] = []
    accessibility_states: list[dict[str, Any]] = []
    shell_dump_hints: list[dict[str, Any]] = []
    scroll_probe_start: dict[str, Any] | None = None
    scroll_probe_strategies: list[dict[str, Any]] = []
    scroll_probe_deltas: list[dict[str, Any]] = []
    media_projection_prompt: dict[str, list[dict[str, Any]]] = {
        "starts": [],
        "buttons": [],
        "selectionOptions": [],
        "selectionTaps": [],
        "taps": [],
        "results": [],
        "traces": [],
        "appOpRestores": [],
    }
    settings_recovery: dict[str, list[dict[str, Any]]] = {
        "starts": [],
        "attempts": [],
        "zeroNodes": [],
        "passiveBaselines": [],
        "results": [],
    }
    system_surfaces: dict[str, list[dict[str, Any]]] = {
        "starts": [],
        "attempts": [],
        "errors": [],
    }
    child_page_surfaces: list[dict[str, Any]] = []
    child_page_skips: list[dict[str, Any]] = []
    dropdown_targets: list[dict[str, Any]] = []
    dropdown_surfaces: list[dict[str, Any]] = []
    parse_errors = []

    for line_number, line in enumerate(lines, start=1):
        stripped = line.strip()
        if not stripped:
            continue
        try:
            event = json.loads(stripped)
        except json.JSONDecodeError as exc:
            parse_errors.append({"line": line_number, "error": str(exc)})
            continue

        event_type = normalize_text(event.get("type"))
        data = event.get("data") or {}
        event_counts[event_type] += 1

        if event_type == "ui_dump":
            ui_dumps.append(
                {
                    "name": normalize_text(data.get("name")),
                    "nodeCount": int_value(data.get("nodeCount")),
                    "clickableCount": int_value(data.get("clickableCount")),
                    "enabledCount": int_value(data.get("enabledCount")),
                    "scrollableCount": int_value(data.get("scrollableCount")),
                    "checkedCount": int_value(data.get("checkedCount")),
                    "candidateCount": int_value(data.get("candidateCount")),
                    "displayIds": sorted(normalize_text(value) for value in data.get("displayIds", []) or []),
                    "packageCount": len(data.get("packages", []) or []),
                }
            )

        elif event_type == "surface_map_snapshot":
            surface_snapshots.append(
                {
                    "surface": normalize_text(data.get("surface")),
                    "nodeCount": int_value(data.get("nodeCount")),
                }
            )

        elif event_type == "shell_uiautomator_dump_external":
            shell_dump_hints.append(
                {
                    "hasCommand": bool(data.get("command")),
                    "hasPath": bool(data.get("path")),
                    "note": normalize_text(data.get("note")),
                }
            )

        elif event_type == "accessibility_state":
            active_root = data.get("activeRoot") or {}
            windows = []
            for window in data.get("windows", []) or []:
                windows.append(
                    {
                        "index": int_value(window.get("index"), -1),
                        "type": normalize_text(window.get("type")),
                        "layer": int_value(window.get("layer"), -1),
                        "active": bool(window.get("active", False)),
                        "focused": bool(window.get("focused", False)),
                        "accessibilityFocused": bool(window.get("accessibilityFocused", False)),
                        "displayId": int_value(window.get("displayId"), -1),
                        "nodeCount": int_value(window.get("nodeCount")),
                        "scrollableCount": int_value(window.get("scrollableCount")),
                        "actionNodeCount": int_value(window.get("actionNodeCount")),
                        "nodesCapped": bool(window.get("nodesCapped", False)),
                    }
                )
            accessibility_states.append(
                {
                    "name": normalize_text(data.get("name")),
                    "windowCount": int_value(data.get("windowCount")),
                    "activeRootNodeCount": int_value(active_root.get("nodeCount")),
                    "activeRootScrollableCount": int_value(active_root.get("scrollableCount")),
                    "activeRootActionNodeCount": int_value(active_root.get("actionNodeCount")),
                    "activeRootNodesCapped": bool(active_root.get("nodesCapped", False)),
                    "windows": windows,
                }
            )

        elif event_type == "scroll_probe_start":
            scroll_probe_start = {
                "surface": normalize_text(data.get("surface")),
                "initialNodeCount": int_value(data.get("initialNodeCount")),
                "strategies": [normalize_text(value) for value in data.get("strategies", []) or []],
            }

        elif event_type == "scroll_probe_strategy":
            attempts = data.get("attempts", []) or []
            scroll_probe_strategies.append(
                {
                    "strategy": normalize_text(data.get("strategy")),
                    "key": normalize_text(data.get("key")),
                    "attemptCount": len(attempts),
                    "hasError": bool(data.get("error") or data.get("errorClass")),
                    "errorClass": normalize_text(data.get("errorClass")),
                    "found": data.get("found") if isinstance(data.get("found"), bool) else None,
                    "performed": data.get("performed") if isinstance(data.get("performed"), bool) else None,
                }
            )

        elif event_type == "scroll_probe_delta":
            before_hash = normalize_text(data.get("beforeVisibleTextHash"))
            after_hash = normalize_text(data.get("afterVisibleTextHash"))
            scroll_probe_deltas.append(
                {
                    "strategy": normalize_text(data.get("strategy")),
                    "beforeNodeCount": int_value(data.get("beforeNodeCount")),
                    "afterNodeCount": int_value(data.get("afterNodeCount")),
                    "visibleTextChanged": before_hash != after_hash,
                    "newVisibleTextCount": len(data.get("newVisibleTexts", []) or []),
                }
            )

        elif event_type == "media_projection_prompt_start":
            media_projection_prompt["starts"].append(
                {
                    "temporaryAppOpMode": normalize_text(data.get("temporaryAppOpMode")) or "(unchanged)",
                    "previousAppOpMode": normalize_text(data.get("previousAppOpMode")) or "default",
                    "selectionChoice": normalize_text(data.get("selectionChoice")),
                    "tapChoice": normalize_text(data.get("tapChoice")),
                    "restoreAppOp": bool(data.get("restoreAppOp", False)),
                }
            )

        elif event_type == "media_projection_prompt_buttons":
            roles: Counter[str] = Counter()
            enabled_roles: Counter[str] = Counter()
            disabled_roles: Counter[str] = Counter()
            for button in data.get("buttons", []) or []:
                role = normalize_text(button.get("role")) or "unknown"
                roles[role] += 1
                if button.get("enabled", False):
                    enabled_roles[role] += 1
                else:
                    disabled_roles[role] += 1
            media_projection_prompt["buttons"].append(
                {
                    "buttonCount": int_value(data.get("buttonCount")),
                    "roles": dict(sorted(roles.items())),
                    "enabledRoles": dict(sorted(enabled_roles.items())),
                    "disabledRoles": dict(sorted(disabled_roles.items())),
                }
            )

        elif event_type == "media_projection_prompt_selection_options":
            roles: Counter[str] = Counter()
            for option in data.get("options", []) or []:
                role = normalize_text(option.get("role")) or "unknown"
                roles[role] += 1
            media_projection_prompt["selectionOptions"].append(
                {
                    "optionCount": int_value(data.get("optionCount")),
                    "roles": dict(sorted(roles.items())),
                }
            )

        elif event_type == "media_projection_prompt_selection_tap":
            media_projection_prompt["selectionTaps"].append(
                {
                    "selectionChoice": normalize_text(data.get("selectionChoice")),
                    "tapped": bool(data.get("tapped", False)),
                    "matchedRole": normalize_text(data.get("matchedRole")),
                }
            )

        elif event_type == "media_projection_prompt_tap":
            media_projection_prompt["taps"].append(
                {
                    "tapChoice": normalize_text(data.get("tapChoice")),
                    "tapped": bool(data.get("tapped", False)),
                    "matchedRole": normalize_text(data.get("matchedRole")),
                }
            )

        elif event_type == "media_projection_prompt_result":
            media_projection_prompt["results"].append(
                {
                    "hasResultFile": bool(data.get("hasResultFile", False)),
                    "resultCode": int_value(data.get("resultCode")),
                    "resultOk": bool(data.get("resultOk", False)),
                    "resultCanceled": bool(data.get("resultCanceled", False)),
                    "hasData": bool(data.get("hasData", False)),
                    "dataExtraCount": int_value(data.get("dataExtraCount")),
                    "hasError": bool(data.get("hasError", False)),
                }
            )

        elif event_type == "media_projection_prompt_trace":
            raw_events = [
                normalize_text(value)
                for value in data.get("events", []) or []
                if normalize_text(value)
            ]
            media_projection_prompt["traces"].append(
                {
                    "hasTraceFile": bool(data.get("hasTraceFile", False)),
                    "eventCount": int_value(data.get("eventCount")),
                    "events": raw_events,
                }
            )

        elif event_type == "media_projection_prompt_appop_restore":
            media_projection_prompt["appOpRestores"].append(
                {
                    "restoredMode": normalize_text(data.get("restoredMode")) or "default",
                }
            )

        elif event_type == "settings_recovery_start":
            settings_recovery["starts"].append(
                {
                    "retryCount": int_value(data.get("retryCount")),
                    "retryWaitMs": int_value(data.get("retryWaitMs")),
                    "dumpPassiveBaselines": bool(data.get("dumpPassiveBaselines", False)),
                }
            )

        elif event_type == "settings_recovery_attempt":
            settings_recovery["attempts"].append(
                {
                    "phase": normalize_text(data.get("phase")),
                    "attempt": int_value(data.get("attempt")),
                    "nodeCount": int_value(data.get("nodeCount")),
                    "settingsNodeCount": int_value(data.get("settingsNodeCount")),
                    "scrollableCount": int_value(data.get("scrollableCount")),
                    "empty": bool(data.get("empty", False)),
                }
            )

        elif event_type == "settings_recovery_zero_node":
            settings_recovery["zeroNodes"].append(
                {
                    "phase": normalize_text(data.get("phase")),
                    "attempt": int_value(data.get("attempt")),
                    "target": normalize_text(data.get("target")),
                    "diagnostic": normalize_text(data.get("diagnostic")),
                }
            )

        elif event_type == "settings_recovery_passive_baseline":
            settings_recovery["passiveBaselines"].append(
                {
                    "phase": normalize_text(data.get("phase")),
                    "nodeCount": int_value(data.get("nodeCount")),
                    "settingsNodeCount": int_value(data.get("settingsNodeCount")),
                    "scrollableCount": int_value(data.get("scrollableCount")),
                    "empty": bool(data.get("empty", False)),
                }
            )

        elif event_type == "settings_recovery_result":
            settings_recovery["results"].append(
                {
                    "initialNodeCount": int_value(data.get("initialNodeCount")),
                    "finalNodeCount": int_value(data.get("finalNodeCount")),
                    "initialZeroNode": bool(data.get("initialZeroNode", False)),
                    "settingsVisible": bool(data.get("settingsVisible", False)),
                    "recovered": bool(data.get("recovered", False)),
                    "retryCount": int_value(data.get("retryCount")),
                    "passiveOnly": bool(data.get("passiveOnly", False)),
                }
            )

        elif event_type == "system_surface_probe_start":
            system_surfaces["starts"].append(
                {
                    "surfaces": [normalize_text(value) for value in data.get("surfaces", []) or []],
                    "waitAfterSurfaceMs": int_value(data.get("waitAfterSurfaceMs")),
                }
            )

        elif event_type == "system_surface_attempt":
            system_surfaces["attempts"].append(
                {
                    "surface": normalize_text(data.get("surface")),
                    "index": int_value(data.get("index"), -1),
                    "nodeCount": int_value(data.get("nodeCount")),
                    "clickableCount": int_value(data.get("clickableCount")),
                    "enabledCount": int_value(data.get("enabledCount")),
                    "scrollableCount": int_value(data.get("scrollableCount")),
                    "checkedCount": int_value(data.get("checkedCount")),
                    "packageCount": int_value(data.get("packageCount")),
                    "displayIds": sorted(normalize_text(value) for value in data.get("displayIds", []) or []),
                    "changedFromPrevious": bool(data.get("changedFromPrevious", False)),
                    "empty": bool(data.get("empty", False)),
                }
            )

        elif event_type == "system_surface_error":
            system_surfaces["errors"].append(
                {
                    "surface": normalize_text(data.get("surface")),
                    "index": int_value(data.get("index"), -1),
                    "errorClass": normalize_text(data.get("errorClass")),
                    "hasMessage": bool(normalize_text(data.get("message"))),
                }
            )

        elif event_type == "settings_section_page":
            target = normalize_text(data.get("target"))
            section = sections.setdefault(
                target,
                {
                    "target": target,
                    "title": TARGET_TITLES.get(target, target),
                    "pages": 0,
                    "pageHashes": [],
                    "safeLabels": [],
                    "redactedTextCount": 0,
                },
            )
            section["pages"] += 1
            page_hash = normalize_text(data.get("visibleTextHash"))
            if page_hash:
                section["pageHashes"].append(page_hash)
            labels_seen = set(section["safeLabels"])
            for raw_text in data.get("settingsContentTexts", []) or []:
                label, redacted = public_label(raw_text, target)
                if label and label not in labels_seen and len(section["safeLabels"]) < max_labels:
                    section["safeLabels"].append(label)
                    labels_seen.add(label)
                if redacted:
                    section["redactedTextCount"] += 1

        elif event_type == "settings_section_scroll":
            target = normalize_text(data.get("target"))
            section_scrolls[target].append(
                {
                    "moved": bool(data.get("moved", False)),
                    "objectScrolled": bool(data.get("objectScrolled", False)),
                    "coordinateSwiped": bool(data.get("coordinateSwiped", False)),
                }
            )

        elif event_type == "settings_section_route_inventory":
            target = normalize_text(data.get("target"))
            inventory = route_inventory.setdefault(
                target,
                {
                    "target": target,
                    "title": TARGET_TITLES.get(target, target),
                    "pages": 0,
                    "candidateCount": 0,
                    "countsByType": Counter(),
                    "countsByRisk": Counter(),
                    "safeLabels": [],
                    "redactedLabelCount": 0,
                },
            )
            inventory["pages"] += 1
            inventory["candidateCount"] += int(data.get("candidateCount") or 0)
            for route_type, count in (data.get("countsByType") or {}).items():
                inventory["countsByType"][normalize_text(route_type)] += int(count or 0)
            labels_seen = set(inventory["safeLabels"])
            for candidate in data.get("candidates", []) or []:
                risk = normalize_text(candidate.get("risk"))
                route_type = normalize_text(candidate.get("routeType"))
                if risk:
                    inventory["countsByRisk"][risk] += 1
                if route_type and route_type not in inventory["countsByType"]:
                    inventory["countsByType"][route_type] += 0
                label, redacted = public_label(candidate.get("label"), target)
                if label and label not in labels_seen and len(inventory["safeLabels"]) < max_labels:
                    inventory["safeLabels"].append(label)
                    labels_seen.add(label)
                if redacted:
                    inventory["redactedLabelCount"] += 1
                if (
                    route_type == "child_page"
                    and risk in child_probe_risks
                    and label
                    and label not in excluded_child_labels
                ):
                    child_key = f"{target}:{label}"
                    child_probe_targets.setdefault(
                        child_key,
                        {
                            "section": target,
                            "label": label,
                            "risk": risk,
                            "childTarget": child_key,
                        },
                    )

        elif event_type == "settings_dropdown_option_target":
            outcome = data.get("outcome") or {}
            option = outcome.get("option") or {}
            dropdown_targets.append(
                {
                    "section": normalize_text(data.get("section")),
                    "label": normalize_text(data.get("label")),
                    "optionTarget": normalize_text(outcome.get("optionTarget")),
                    "found": bool(outcome.get("found", False)),
                    "clicked": bool(outcome.get("clicked", False)),
                    "reason": normalize_text(outcome.get("reason")),
                    "selected": bool(option.get("selected", False)),
                    "checked": bool(option.get("checked", False)),
                    "texts": option_texts(option),
                }
            )

        elif event_type == "settings_child_skip":
            child_page_skips.append(
                {
                    "section": normalize_text(data.get("section")),
                    "label": normalize_text(data.get("label")),
                    "childTargetRole": normalize_text(data.get("childTargetRole")),
                    "clickMode": normalize_text(data.get("clickMode")),
                    "reason": normalize_text(data.get("reason")),
                }
            )

        elif event_type == "settings_child_surface":
            section = normalize_text(data.get("section"))
            label = normalize_text(data.get("label"))
            summary = data.get("summary") or {}
            options = []
            for option in summary.get("settingsDropdownOptions", []) or []:
                texts = option_texts(option)
                if texts:
                    options.append(
                        {
                            "texts": texts,
                            "selected": bool(option.get("selected", False)),
                            "checked": bool(option.get("checked", False)),
                            "hasDefaultMarker": bool(option.get("hasDefaultMarker", False)),
                        }
                    )
            if options:
                dropdown_surfaces.append(
                    {
                        "section": section,
                        "label": label,
                        "clickMode": normalize_text(data.get("clickMode")),
                        "optionCount": len(options),
                        "options": options,
                    }
                )
            else:
                safe_labels = []
                redacted_count = 0
                seen_labels = set()
                for raw_text in summary.get("settingsContentTexts", []) or []:
                    public_text, redacted = public_label(raw_text, section)
                    if public_text and public_text not in seen_labels and len(safe_labels) < max_labels:
                        safe_labels.append(public_text)
                        seen_labels.add(public_text)
                    if redacted:
                        redacted_count += 1
                child_page_surfaces.append(
                    {
                        "section": section,
                        "label": label,
                        "clickMode": normalize_text(data.get("clickMode")),
                        "differsFromClickedPage": bool(data.get("differsFromClickedPage", False)),
                        "visibleTextHash": normalize_text(summary.get("visibleTextHash")),
                        "safeLabels": safe_labels,
                        "redactedTextCount": redacted_count,
                    }
                )

    section_summaries = []
    for target, section in sections.items():
        scrolls = section_scrolls.get(target, [])
        moved = sum(1 for scroll in scrolls if scroll["moved"])
        stopped = sum(1 for scroll in scrolls if not scroll["moved"])
        section_summaries.append(
            {
                **section,
                "movedScrolls": moved,
                "stoppedScrolls": stopped,
                "endpointReached": stopped > 0,
            }
        )

    return {
        "source": source_name,
        "eventCounts": dict(sorted(event_counts.items())),
        "parseErrors": parse_errors,
        "uiDumps": ui_dumps,
        "surfaceSnapshots": surface_snapshots,
        "accessibilityStates": accessibility_states,
        "shellDumpHints": shell_dump_hints,
        "scrollProbeStart": scroll_probe_start,
        "scrollProbeStrategies": scroll_probe_strategies,
        "scrollProbeDeltas": scroll_probe_deltas,
        "mediaProjectionPrompt": media_projection_prompt,
        "settingsRecovery": settings_recovery,
        "systemSurfaces": system_surfaces,
        "settingsSections": section_summaries,
        "routeInventory": [
            {
                **inventory,
                "countsByType": dict(sorted(inventory["countsByType"].items())),
                "countsByRisk": dict(sorted(inventory["countsByRisk"].items())),
            }
            for inventory in route_inventory.values()
        ],
        "plannedChildProbeTargets": list(child_probe_targets.values()),
        "childPageSurfaces": child_page_surfaces,
        "childPageSkips": child_page_skips,
        "dropdownOptionTargets": dropdown_targets,
        "dropdownSurfaces": dropdown_surfaces,
        "redactionPolicy": {
            "sourcePaths": "Only the input basename and immediate sweep directory are emitted.",
            "labels": "Only allowlisted low-cardinality settings labels are emitted.",
            "notifications": "Unknown notification labels, including installed app names, are counted but not emitted.",
            "rawXml": "Raw XML paths and full UI dumps are never emitted.",
            "surfaceMaps": "Surface-map summaries omit package names, raw node text, window titles, XML paths, and shell command output.",
            "scrollProbes": "Scroll-probe summaries omit raw visible text and command output; only counts, keys, strategy names, and changed/not-changed status are emitted.",
            "mediaProjectionPrompt": "MediaProjection prompt summaries omit raw prompt text, package names, token/resultData contents, command output, and coordinates; only button roles and result-state booleans are emitted.",
            "settingsRecovery": "Settings recovery summaries omit focus/window command output and raw UI text; only node counts, retry status, and zero-node diagnostics are emitted.",
            "systemSurfaces": "System-surface reachability summaries omit raw UI text, package names, window titles, and XML paths; only counts, display IDs, and changed/empty status are emitted.",
            "childProbeDefaults": "Generated childTargets include public-safe child_page routes in allowed risk buckets and exclude default-blocked labels.",
        },
    }


def markdown_table_row(cells: list[Any]) -> str:
    return "| " + " | ".join(str(cell).replace("\n", " ") for cell in cells) + " |"


def render_markdown(summaries: list[dict[str, Any]]) -> str:
    lines = ["# Quest UIAutomator Report Summary", ""]
    for summary in summaries:
        lines.append(f"## {summary['source']}")
        lines.append("")
        total_events = sum(summary["eventCounts"].values())
        lines.append(f"- Events parsed: {total_events}")
        lines.append(f"- Parse errors: {len(summary['parseErrors'])}")
        lines.append("- Redaction: raw XML paths, local paths, package/resource IDs, and non-allowlisted labels are omitted.")
        lines.append("")

        if summary["uiDumps"]:
            lines.append("### UI Dumps")
            lines.append(markdown_table_row(["Name", "Nodes", "Clickable", "Enabled", "Scrollable", "Checked", "Candidates", "Displays", "Packages"]))
            lines.append(markdown_table_row(["---", "---:", "---:", "---:", "---:", "---:", "---:", "---", "---:"]))
            for dump in summary["uiDumps"]:
                lines.append(
                    markdown_table_row(
                        [
                            dump["name"],
                            dump["nodeCount"],
                            dump["clickableCount"],
                            dump["enabledCount"],
                            dump["scrollableCount"],
                            dump["checkedCount"],
                            dump["candidateCount"],
                            ", ".join(dump["displayIds"]) if dump["displayIds"] else "(none)",
                            dump["packageCount"],
                        ]
                    )
                )
            lines.append("")

        if summary["surfaceSnapshots"]:
            lines.append("### Surface Map Snapshots")
            lines.append(markdown_table_row(["Surface", "Nodes"]))
            lines.append(markdown_table_row(["---", "---:"]))
            for snapshot in summary["surfaceSnapshots"]:
                lines.append(markdown_table_row([snapshot["surface"], snapshot["nodeCount"]]))
            lines.append("")

        if summary["accessibilityStates"]:
            lines.append("### Accessibility Windows")
            lines.append(markdown_table_row(["Name", "Windows", "Active root nodes", "Active scrollables", "Active action nodes", "Capped"]))
            lines.append(markdown_table_row(["---", "---:", "---:", "---:", "---:", "---"]))
            for state in summary["accessibilityStates"]:
                lines.append(
                    markdown_table_row(
                        [
                            state["name"],
                            state["windowCount"],
                            state["activeRootNodeCount"],
                            state["activeRootScrollableCount"],
                            state["activeRootActionNodeCount"],
                            "yes" if state["activeRootNodesCapped"] else "no",
                        ]
                    )
                )
            lines.append("")
            lines.append(markdown_table_row(["Name", "Index", "Type", "Layer", "Display", "Active", "Focused", "Nodes", "Scrollables", "Action nodes", "Capped"]))
            lines.append(markdown_table_row(["---", "---:", "---", "---:", "---:", "---", "---", "---:", "---:", "---:", "---"]))
            for state in summary["accessibilityStates"]:
                for window in state["windows"]:
                    lines.append(
                        markdown_table_row(
                            [
                                state["name"],
                                window["index"],
                                window["type"],
                                window["layer"],
                                window["displayId"],
                                "yes" if window["active"] else "no",
                                "yes" if window["focused"] else "no",
                                window["nodeCount"],
                                window["scrollableCount"],
                                window["actionNodeCount"],
                                "yes" if window["nodesCapped"] else "no",
                            ]
                        )
                    )
            lines.append("")

        if summary["shellDumpHints"]:
            lines.append("### Shell Dump Hints")
            lines.append(markdown_table_row(["External command provided", "External path provided", "Note"]))
            lines.append(markdown_table_row(["---", "---", "---"]))
            for hint in summary["shellDumpHints"]:
                lines.append(
                    markdown_table_row(
                        [
                            "yes" if hint["hasCommand"] else "no",
                            "yes" if hint["hasPath"] else "no",
                            hint["note"],
                        ]
                    )
                )
            lines.append("")

        if summary["scrollProbeStart"] or summary["scrollProbeStrategies"] or summary["scrollProbeDeltas"]:
            lines.append("### Scroll Probe")
            start = summary["scrollProbeStart"] or {}
            if start:
                strategies = ", ".join(start["strategies"]) if start["strategies"] else "(none)"
                lines.append(f"- Surface: `{start['surface']}`")
                lines.append(f"- Initial nodes: {start['initialNodeCount']}")
                lines.append(f"- Requested strategies: {strategies}")
                lines.append("")
            if summary["scrollProbeStrategies"]:
                lines.append(markdown_table_row(["Strategy", "Key", "Attempts", "Found", "Performed", "Error"]))
                lines.append(markdown_table_row(["---", "---", "---:", "---", "---", "---"]))
                for strategy in summary["scrollProbeStrategies"]:
                    found = "" if strategy["found"] is None else ("yes" if strategy["found"] else "no")
                    performed = "" if strategy["performed"] is None else ("yes" if strategy["performed"] else "no")
                    error = strategy["errorClass"] if strategy["hasError"] else "no"
                    lines.append(
                        markdown_table_row(
                            [
                                strategy["strategy"],
                                strategy["key"],
                                strategy["attemptCount"],
                                found,
                                performed,
                                error,
                            ]
                        )
                    )
                lines.append("")
            if summary["scrollProbeDeltas"]:
                lines.append(markdown_table_row(["Strategy", "Changed", "Before nodes", "After nodes", "New text count"]))
                lines.append(markdown_table_row(["---", "---", "---:", "---:", "---:"]))
                for delta in summary["scrollProbeDeltas"]:
                    lines.append(
                        markdown_table_row(
                            [
                                delta["strategy"],
                                "yes" if delta["visibleTextChanged"] else "no",
                                delta["beforeNodeCount"],
                                delta["afterNodeCount"],
                                delta["newVisibleTextCount"],
                            ]
                        )
                    )
                lines.append("")

        recovery = summary["settingsRecovery"]
        if any(recovery.values()):
            lines.append("### Settings Recovery")
            if recovery["starts"]:
                lines.append(markdown_table_row(["Retries", "Retry wait ms", "Passive baselines"]))
                lines.append(markdown_table_row(["---:", "---:", "---"]))
                for start in recovery["starts"]:
                    lines.append(
                        markdown_table_row(
                            [
                                start["retryCount"],
                                start["retryWaitMs"],
                                "yes" if start["dumpPassiveBaselines"] else "no",
                            ]
                        )
                    )
                lines.append("")
            if recovery["attempts"]:
                lines.append(markdown_table_row(["Phase", "Attempt", "Nodes", "Settings nodes", "Scrollables", "Empty"]))
                lines.append(markdown_table_row(["---", "---:", "---:", "---:", "---:", "---"]))
                for attempt in recovery["attempts"]:
                    lines.append(
                        markdown_table_row(
                            [
                                attempt["phase"],
                                attempt["attempt"],
                                attempt["nodeCount"],
                                attempt["settingsNodeCount"],
                                attempt["scrollableCount"],
                                "yes" if attempt["empty"] else "no",
                            ]
                        )
                    )
                lines.append("")
            if recovery["passiveBaselines"]:
                lines.append(markdown_table_row(["Passive baseline", "Nodes", "Settings nodes", "Scrollables", "Empty"]))
                lines.append(markdown_table_row(["---", "---:", "---:", "---:", "---"]))
                for baseline in recovery["passiveBaselines"]:
                    lines.append(
                        markdown_table_row(
                            [
                                baseline["phase"],
                                baseline["nodeCount"],
                                baseline["settingsNodeCount"],
                                baseline["scrollableCount"],
                                "yes" if baseline["empty"] else "no",
                            ]
                        )
                    )
                lines.append("")
            if recovery["zeroNodes"]:
                lines.append(markdown_table_row(["Phase", "Attempt", "Diagnostic"]))
                lines.append(markdown_table_row(["---", "---:", "---"]))
                for zero_node in recovery["zeroNodes"]:
                    lines.append(
                        markdown_table_row(
                            [
                                zero_node["phase"],
                                zero_node["attempt"],
                                zero_node["diagnostic"],
                            ]
                        )
                    )
                lines.append("")
            if recovery["results"]:
                lines.append(markdown_table_row(["Initial nodes", "Final nodes", "Initially zero", "Visible", "Recovered", "Passive only"]))
                lines.append(markdown_table_row(["---:", "---:", "---", "---", "---", "---"]))
                for result in recovery["results"]:
                    lines.append(
                        markdown_table_row(
                            [
                                result["initialNodeCount"],
                                result["finalNodeCount"],
                                "yes" if result["initialZeroNode"] else "no",
                                "yes" if result["settingsVisible"] else "no",
                                "yes" if result["recovered"] else "no",
                                "yes" if result["passiveOnly"] else "no",
                            ]
                        )
                    )
                lines.append("")

        system_surfaces = summary["systemSurfaces"]
        if any(system_surfaces.values()):
            lines.append("### System Surface Reachability")
            surface_accessibility = {
                state["name"][len("system_surface_") :]: state
                for state in summary["accessibilityStates"]
                if state["name"].startswith("system_surface_")
            }
            if system_surfaces["starts"]:
                lines.append(markdown_table_row(["Surfaces", "Wait after surface ms"]))
                lines.append(markdown_table_row(["---", "---:"]))
                for start in system_surfaces["starts"]:
                    surfaces = ", ".join(start["surfaces"]) if start["surfaces"] else "(none)"
                    lines.append(markdown_table_row([surfaces, start["waitAfterSurfaceMs"]]))
                lines.append("")
            if system_surfaces["attempts"]:
                lines.append(
                    markdown_table_row(
                        [
                            "Index",
                            "Surface",
                            "Nodes",
                            "Clickable",
                            "Enabled",
                            "Scrollable",
                            "Checked",
                            "Displays",
                            "Packages",
                            "Active root nodes",
                            "Active scrollables",
                            "Windows",
                            "Changed",
                            "Empty",
                        ]
                    )
                )
                lines.append(markdown_table_row(["---:", "---", "---:", "---:", "---:", "---:", "---:", "---", "---:", "---:", "---:", "---:", "---", "---"]))
                for attempt in system_surfaces["attempts"]:
                    active_state = surface_accessibility.get(attempt["surface"], {})
                    lines.append(
                        markdown_table_row(
                            [
                                attempt["index"],
                                attempt["surface"],
                                attempt["nodeCount"],
                                attempt["clickableCount"],
                                attempt["enabledCount"],
                                attempt["scrollableCount"],
                                attempt["checkedCount"],
                                ", ".join(attempt["displayIds"]) if attempt["displayIds"] else "(none)",
                                attempt["packageCount"],
                                active_state.get("activeRootNodeCount", "(missing)"),
                                active_state.get("activeRootScrollableCount", "(missing)"),
                                active_state.get("windowCount", "(missing)"),
                                "yes" if attempt["changedFromPrevious"] else "no",
                                "yes" if attempt["empty"] else "no",
                            ]
                        )
                    )
                lines.append("")
            if system_surfaces["errors"]:
                lines.append(markdown_table_row(["Index", "Surface", "Error class", "Message present"]))
                lines.append(markdown_table_row(["---:", "---", "---", "---"]))
                for error in system_surfaces["errors"]:
                    lines.append(
                        markdown_table_row(
                            [
                                error["index"],
                                error["surface"],
                                error["errorClass"],
                                "yes" if error["hasMessage"] else "no",
                            ]
                        )
                    )
                lines.append("")

        media_prompt = summary["mediaProjectionPrompt"]
        if any(media_prompt.values()):
            lines.append("### MediaProjection Prompt")
            if media_prompt["starts"]:
                lines.append(markdown_table_row(["Temporary app-op", "Previous app-op", "Selection", "Tap choice", "Restore app-op"]))
                lines.append(markdown_table_row(["---", "---", "---", "---", "---"]))
                for start in media_prompt["starts"]:
                    lines.append(
                        markdown_table_row(
                            [
                                start["temporaryAppOpMode"],
                                start["previousAppOpMode"],
                                start["selectionChoice"] or "(none)",
                                start["tapChoice"],
                                "yes" if start["restoreAppOp"] else "no",
                            ]
                        )
                    )
                lines.append("")
            if media_prompt["selectionOptions"]:
                lines.append(markdown_table_row(["Selection options", "Roles"]))
                lines.append(markdown_table_row(["---:", "---"]))
                for options in media_prompt["selectionOptions"]:
                    roles = ", ".join(f"{role}:{count}" for role, count in options["roles"].items())
                    lines.append(markdown_table_row([options["optionCount"], roles or "(none)"]))
                lines.append("")
            if media_prompt["selectionTaps"]:
                lines.append(markdown_table_row(["Selection choice", "Tapped", "Matched role"]))
                lines.append(markdown_table_row(["---", "---", "---"]))
                for tap in media_prompt["selectionTaps"]:
                    lines.append(
                        markdown_table_row(
                            [
                                tap["selectionChoice"] or "(none)",
                                "yes" if tap["tapped"] else "no",
                                tap["matchedRole"] or "(none)",
                            ]
                        )
                    )
                lines.append("")
            if media_prompt["buttons"]:
                lines.append(markdown_table_row(["Buttons", "Roles", "Enabled roles", "Disabled roles"]))
                lines.append(markdown_table_row(["---:", "---", "---", "---"]))
                for buttons in media_prompt["buttons"]:
                    roles = ", ".join(f"{role}:{count}" for role, count in buttons["roles"].items())
                    enabled_roles = ", ".join(f"{role}:{count}" for role, count in buttons["enabledRoles"].items())
                    disabled_roles = ", ".join(f"{role}:{count}" for role, count in buttons["disabledRoles"].items())
                    lines.append(
                        markdown_table_row(
                            [
                                buttons["buttonCount"],
                                roles or "(none)",
                                enabled_roles or "(none)",
                                disabled_roles or "(none)",
                            ]
                        )
                    )
                lines.append("")
            if media_prompt["taps"]:
                lines.append(markdown_table_row(["Tap choice", "Tapped", "Matched role"]))
                lines.append(markdown_table_row(["---", "---", "---"]))
                for tap in media_prompt["taps"]:
                    lines.append(
                        markdown_table_row(
                            [
                                tap["tapChoice"],
                                "yes" if tap["tapped"] else "no",
                                tap["matchedRole"] or "(none)",
                            ]
                        )
                    )
                lines.append("")
            if media_prompt["results"]:
                lines.append(markdown_table_row(["Result file", "OK", "Canceled", "Has data", "Data extras", "Error"]))
                lines.append(markdown_table_row(["---", "---", "---", "---", "---:", "---"]))
                for result in media_prompt["results"]:
                    lines.append(
                        markdown_table_row(
                            [
                                "yes" if result["hasResultFile"] else "no",
                                "yes" if result["resultOk"] else "no",
                                "yes" if result["resultCanceled"] else "no",
                                "yes" if result["hasData"] else "no",
                                result["dataExtraCount"],
                                "yes" if result["hasError"] else "no",
                            ]
                        )
                    )
                lines.append("")
            if media_prompt["traces"]:
                lines.append(markdown_table_row(["Trace file", "Events"]))
                lines.append(markdown_table_row(["---", "---"]))
                for trace in media_prompt["traces"]:
                    events = ", ".join(trace["events"]) if trace["events"] else f"{trace['eventCount']} event(s)"
                    lines.append(markdown_table_row(["yes" if trace["hasTraceFile"] else "no", events]))
                lines.append("")
            if media_prompt["appOpRestores"]:
                restored = ", ".join(restore["restoredMode"] for restore in media_prompt["appOpRestores"])
                lines.append(f"- Restored app-op modes: {restored}")
                lines.append("")

        sections = summary["settingsSections"]
        if sections:
            lines.append("### Settings Sections")
            lines.append(markdown_table_row(["Target", "Pages", "Scrolls", "Endpoint", "Safe labels", "Redacted labels"]))
            lines.append(markdown_table_row(["---", "---:", "---:", "---", "---", "---:"]))
            for section in sections:
                scrolls = f"{section['movedScrolls']} moved, {section['stoppedScrolls']} stopped"
                labels = ", ".join(section["safeLabels"]) if section["safeLabels"] else "(none)"
                lines.append(
                    markdown_table_row(
                        [
                            section["title"],
                            section["pages"],
                            scrolls,
                            "yes" if section["endpointReached"] else "not proven",
                            labels,
                            section["redactedTextCount"],
                        ]
                    )
                )
            lines.append("")

        if summary["routeInventory"]:
            lines.append("### Route Inventory")
            lines.append(markdown_table_row(["Target", "Pages", "Candidates", "Types", "Risks", "Safe labels", "Redacted labels"]))
            lines.append(markdown_table_row(["---", "---:", "---:", "---", "---", "---", "---:"]))
            for inventory in summary["routeInventory"]:
                type_counts = ", ".join(
                    f"{route_type}: {count}" for route_type, count in inventory["countsByType"].items()
                )
                risk_counts = ", ".join(
                    f"{risk}: {count}" for risk, count in inventory["countsByRisk"].items()
                )
                labels = ", ".join(inventory["safeLabels"]) if inventory["safeLabels"] else "(none)"
                lines.append(
                    markdown_table_row(
                        [
                            inventory["title"],
                            inventory["pages"],
                            inventory["candidateCount"],
                            type_counts or "(none)",
                            risk_counts or "(none)",
                            labels,
                            inventory["redactedLabelCount"],
                        ]
                    )
                )
            lines.append("")

        if summary["plannedChildProbeTargets"]:
            child_targets = ",".join(target["childTarget"] for target in summary["plannedChildProbeTargets"])
            lines.append("### Planned Child-Page Probe Targets")
            lines.append("")
            lines.append(
                "Default filter: `routeType=child_page`, public-safe label, allowed risk bucket, "
                "and not a default-blocked label."
            )
            lines.append("")
            lines.append("```text")
            lines.append(child_targets)
            lines.append("```")
            lines.append("")
            lines.append(markdown_table_row(["Section", "Label", "Risk"]))
            lines.append(markdown_table_row(["---", "---", "---"]))
            for target in summary["plannedChildProbeTargets"]:
                lines.append(
                    markdown_table_row(
                        [
                            TARGET_TITLES.get(target["section"], target["section"]),
                            target["label"],
                            target["risk"],
                        ]
                    )
                )
            lines.append("")

        if summary["childPageSurfaces"]:
            lines.append("### Child Page Surfaces")
            lines.append(markdown_table_row(["Section", "Label", "Click mode", "Changed", "Safe labels", "Redacted labels"]))
            lines.append(markdown_table_row(["---", "---", "---", "---", "---", "---:"]))
            for surface in summary["childPageSurfaces"]:
                labels = ", ".join(surface["safeLabels"]) if surface["safeLabels"] else "(none)"
                lines.append(
                    markdown_table_row(
                        [
                            TARGET_TITLES.get(surface["section"], surface["section"]),
                            surface["label"],
                            surface["clickMode"],
                            "yes" if surface["differsFromClickedPage"] else "not proven",
                            labels,
                            surface["redactedTextCount"],
                        ]
                    )
                )
            lines.append("")

        if summary["childPageSkips"]:
            lines.append("### Child Page Skips")
            lines.append(markdown_table_row(["Section", "Label", "Role", "Click mode", "Reason"]))
            lines.append(markdown_table_row(["---", "---", "---", "---", "---"]))
            for skip in summary["childPageSkips"]:
                lines.append(
                    markdown_table_row(
                        [
                            TARGET_TITLES.get(skip["section"], skip["section"]),
                            skip["label"],
                            skip["childTargetRole"],
                            skip["clickMode"],
                            skip["reason"],
                        ]
                    )
                )
            lines.append("")

        if summary["dropdownOptionTargets"]:
            lines.append("### Dropdown Option Targets")
            lines.append(markdown_table_row(["Section", "Label", "Requested option", "Found", "Clicked", "Reason"]))
            lines.append(markdown_table_row(["---", "---", "---", "---", "---", "---"]))
            for target in summary["dropdownOptionTargets"]:
                lines.append(
                    markdown_table_row(
                        [
                            TARGET_TITLES.get(target["section"], target["section"]),
                            target["label"],
                            target["optionTarget"],
                            "yes" if target["found"] else "no",
                            "yes" if target["clicked"] else "no",
                            target["reason"],
                        ]
                    )
                )
            lines.append("")

        if summary["dropdownSurfaces"]:
            lines.append("### Dropdown Surfaces")
            lines.append(markdown_table_row(["Section", "Label", "Click mode", "Options"]))
            lines.append(markdown_table_row(["---", "---", "---", "---"]))
            for surface in summary["dropdownSurfaces"]:
                option_labels = []
                for option in surface["options"]:
                    label = " / ".join(option["texts"])
                    if option["selected"]:
                        label += " [selected]"
                    if option["hasDefaultMarker"]:
                        label += " [default]"
                    option_labels.append(label)
                lines.append(
                    markdown_table_row(
                        [
                            TARGET_TITLES.get(surface["section"], surface["section"]),
                            surface["label"],
                            surface["clickMode"],
                            "; ".join(option_labels),
                        ]
                    )
                )
            lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def render_child_targets(summaries: list[dict[str, Any]]) -> str:
    targets = OrderedDict()
    for summary in summaries:
        for target in summary["plannedChildProbeTargets"]:
            targets.setdefault(target["childTarget"], True)
    return ",".join(targets.keys()) + ("\n" if targets else "")


def open_lines(path: str) -> tuple[str, Iterable[str]]:
    if path == "-":
        return "stdin", sys.stdin
    file_path = Path(path)
    source_name = file_path.name
    if file_path.parent.name:
        source_name = f"{file_path.parent.name}/{file_path.name}"
    return source_name, file_path.open("r", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", nargs="*", help="Path(s) to report.jsonl. Use '-' or omit for stdin.")
    parser.add_argument("--format", choices=("markdown", "json", "child-targets"), default="markdown")
    parser.add_argument("--max-labels", type=int, default=18)
    parser.add_argument(
        "--child-risk",
        action="append",
        default=None,
        help="Risk bucket allowed in generated childTargets. Defaults to open_dump_only. Repeat to include more.",
    )
    parser.add_argument(
        "--include-default-blocked-child-labels",
        action="store_true",
        help="Include default-blocked labels such as Software update and Cloud backup in generated childTargets.",
    )
    args = parser.parse_args()

    child_probe_risks = set(args.child_risk or ["open_dump_only"])
    excluded_child_labels = set() if args.include_default_blocked_child_labels else DEFAULT_CHILD_PROBE_EXCLUDED_LABELS
    report_paths = args.reports or ["-"]
    summaries = []
    for report_path in report_paths:
        source_name, lines = open_lines(report_path)
        try:
            summaries.append(
                summarize_events(
                    lines,
                    source_name,
                    max(args.max_labels, 1),
                    child_probe_risks,
                    excluded_child_labels,
                )
            )
        finally:
            close = getattr(lines, "close", None)
            if callable(close) and lines is not sys.stdin:
                close()

    if args.format == "json":
        print(json.dumps({"reports": summaries}, indent=2, sort_keys=True))
    elif args.format == "child-targets":
        print(render_child_targets(summaries), end="")
    else:
        print(render_markdown(summaries), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
