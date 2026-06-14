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


def summarize_events(lines: Iterable[str], source_name: str, max_labels: int) -> dict[str, Any]:
    event_counts: Counter[str] = Counter()
    sections: OrderedDict[str, dict[str, Any]] = OrderedDict()
    section_scrolls: defaultdict[str, list[dict[str, Any]]] = defaultdict(list)
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

        if event_type == "settings_section_page":
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

        elif event_type == "settings_child_surface":
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
                        "section": normalize_text(data.get("section")),
                        "label": normalize_text(data.get("label")),
                        "clickMode": normalize_text(data.get("clickMode")),
                        "optionCount": len(options),
                        "options": options,
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
        "settingsSections": section_summaries,
        "dropdownOptionTargets": dropdown_targets,
        "dropdownSurfaces": dropdown_surfaces,
        "redactionPolicy": {
            "sourcePaths": "Only the input basename and immediate sweep directory are emitted.",
            "labels": "Only allowlisted low-cardinality settings labels are emitted.",
            "notifications": "Unknown notification labels, including installed app names, are counted but not emitted.",
            "rawXml": "Raw XML paths and full UI dumps are never emitted.",
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
    parser.add_argument("--format", choices=("markdown", "json"), default="markdown")
    parser.add_argument("--max-labels", type=int, default=18)
    args = parser.parse_args()

    report_paths = args.reports or ["-"]
    summaries = []
    for report_path in report_paths:
        source_name, lines = open_lines(report_path)
        try:
            summaries.append(summarize_events(lines, source_name, max(args.max_labels, 1)))
        finally:
            close = getattr(lines, "close", None)
            if callable(close) and lines is not sys.stdin:
                close()

    if args.format == "json":
        print(json.dumps({"reports": summaries}, indent=2, sort_keys=True))
    else:
        print(render_markdown(summaries), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
