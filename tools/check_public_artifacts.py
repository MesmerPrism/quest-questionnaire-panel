#!/usr/bin/env python3
"""Reject private Quest lab artifacts before they enter the public repo.

The repo intentionally keeps generated APKs, raw UI dumps, screenshots,
recordings, log bundles, device serials, local machine paths, and signing
material out of committed files. This check is designed for staged changes by
default so it can be used as a lightweight pre-commit gate.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


BLOCKED_EXTENSIONS = {
    ".aab": "Android build output",
    ".apk": "Android build output",
    ".apks": "Android build output",
    ".jpeg": "raw screenshot/photo artifact",
    ".jpg": "raw screenshot/photo artifact",
    ".log": "raw log artifact",
    ".logcat": "raw logcat artifact",
    ".mkv": "raw recording artifact",
    ".mov": "raw recording artifact",
    ".png": "raw screenshot artifact",
    ".tar": "archive artifact",
    ".tgz": "archive artifact",
    ".webm": "raw recording artifact",
    ".zip": "archive artifact",
}

BINARY_EXTENSIONS = {
    ".gif",
    ".jar",
    ".mp3",
    ".ogg",
    ".pdf",
    ".wav",
}

BLOCKED_PATH_PARTS = {
    "artifacts": "generated evidence directory",
    "captures": "generated capture directory",
    "logs": "generated log directory",
    "screenshots": "generated screenshot directory",
    "sweeps": "raw UIAutomator sweep evidence directory",
}

TEXT_PATTERNS: list[tuple[str, str, re.Pattern[str]]] = [
    (
        "local-absolute-path",
        "Local absolute machine path",
        re.compile(r"(?<![A-Za-z0-9_])[A-Za-z]:[\\/](?:Users|Work|Temp|tmp)[\\/]"),
    ),
    (
        "adb-device-line",
        "Raw adb devices output with a device serial",
        re.compile(r"(?m)^\s*[A-Z0-9][A-Z0-9._:-]{9,31}\s+device\s*$"),
    ),
    (
        "agent-board-serial",
        "Raw Agent Board Quest lease serial",
        re.compile(r"\bquest:[A-Z0-9][A-Z0-9._:-]{9,31}\b"),
    ),
    (
        "private-key",
        "Private key material",
        re.compile(r"-----BEGIN (?:RSA |DSA |EC |OPENSSH )?PRIVATE KEY-----"),
    ),
    (
        "authorization-bearer",
        "Bearer authorization token",
        re.compile(r"(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/\-]+=*"),
    ),
    (
        "api-key",
        "API key header or assignment",
        re.compile(r"(?i)\bx-api-key\s*[:=]\s*\S+"),
    ),
    (
        "client-secret",
        "Client secret assignment",
        re.compile(r"(?i)\bclient_secret\s*[:=]\s*\S+"),
    ),
    (
        "refresh-token",
        "Refresh token assignment",
        re.compile(r"(?i)\brefresh_token\s*[:=]\s*\S+"),
    ),
    (
        "tailscale-authkey",
        "Tailscale auth key",
        re.compile(r"(?i)\b(?:tailscale\s+authkey|tskey-(?:auth|client|api)-[A-Za-z0-9-]+)\b"),
    ),
    (
        "cloudflare-tunnel-token",
        "Cloudflare tunnel token",
        re.compile(r"(?i)\bcloudflare(?:[_ -]?tunnel)?[_ -]?(?:token|secret)\s*[:=]\s*\S+"),
    ),
    (
        "wireguard-private-key",
        "WireGuard private key",
        re.compile(r"(?i)\b(?:wireguard[_-]?private[_-]?key|PrivateKey)\s*[:=]\s*[A-Za-z0-9+/=]{20,}"),
    ),
    (
        "android-keystore-password",
        "Likely signing password field",
        re.compile(r"(?i)\b(?:store|key)Password\s*="),
    ),
    (
        "raw-uiautomator-xml",
        "Raw UIAutomator hierarchy XML",
        re.compile(r"<hierarchy\b[^>]*\brotation="),
    ),
]


@dataclass(frozen=True)
class Violation:
    path: str
    reason: str
    line: int | None = None
    match_id: str | None = None

    def render(self) -> str:
        location = self.path if self.line is None else f"{self.path}:{self.line}"
        suffix = "" if self.match_id is None else f" [{self.match_id}]"
        return f"{location}: {self.reason}{suffix}"


def repo_relative(path: str) -> str:
    return path.replace("\\", "/").lstrip("./")


def run_git(args: list[str]) -> list[str]:
    completed = subprocess.run(
        ["git", *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line.strip() for line in completed.stdout.splitlines() if line.strip()]


def staged_paths() -> list[str]:
    return run_git(["diff", "--cached", "--name-only", "--diff-filter=ACMR"])


def tracked_paths() -> list[str]:
    return run_git(["ls-files"])


def explicit_paths(paths: list[str]) -> list[str]:
    expanded: list[str] = []
    for raw_path in paths:
        path = Path(raw_path)
        if path.is_dir():
            expanded.extend(str(child) for child in path.rglob("*") if child.is_file())
        else:
            expanded.append(raw_path)
    return expanded


def is_curated_public_media(path: str) -> bool:
    return bool(re.fullmatch(r"docs/media/[^/]+\.mp4", repo_relative(path), re.IGNORECASE))


def path_violations(path: str) -> list[Violation]:
    normalized = repo_relative(path)
    lower = normalized.lower()
    violations: list[Violation] = []

    extension = Path(normalized).suffix.lower()
    if extension == ".mp4" and is_curated_public_media(normalized):
        return violations
    if extension in BLOCKED_EXTENSIONS:
        violations.append(Violation(normalized, BLOCKED_EXTENSIONS[extension]))

    parts = [part.lower() for part in normalized.split("/")]
    for part in parts:
        reason = BLOCKED_PATH_PARTS.get(part)
        if reason:
            violations.append(Violation(normalized, reason))
            break

    if any(part.endswith("logcat") for part in parts):
        violations.append(Violation(normalized, "raw logcat directory"))

    if lower.endswith((".jks", ".keystore", "keystore.properties")):
        violations.append(Violation(normalized, "signing key or signing config"))
    if lower.endswith(("adbkey", "adbkey.pub")):
        violations.append(Violation(normalized, "ADB authorization key"))

    return violations


def text_violations(path: str) -> list[Violation]:
    normalized = repo_relative(path)
    if normalized == "tools/check_public_artifacts.py":
        return []

    extension = Path(normalized).suffix.lower()
    if extension in BINARY_EXTENSIONS or (extension == ".mp4" and is_curated_public_media(normalized)):
        return []

    file_path = Path(path)
    if not file_path.exists() or not file_path.is_file():
        return []

    try:
        text = file_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return []

    violations: list[Violation] = []
    for match_id, reason, pattern in TEXT_PATTERNS:
        for match in pattern.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            violations.append(Violation(normalized, reason, line=line, match_id=match_id))
    return violations


def collect_paths(args: argparse.Namespace) -> list[str]:
    if args.paths:
        return explicit_paths(args.paths)
    if args.all:
        return tracked_paths()
    return staged_paths()


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "paths",
        nargs="*",
        help="Specific files or directories to scan. Defaults to staged changes.",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Scan all tracked files instead of only staged changes.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    paths = [path for path in collect_paths(args) if path and Path(path).exists()]

    violations: list[Violation] = []
    for path in paths:
        violations.extend(path_violations(path))
        violations.extend(text_violations(path))

    if violations:
        print("Public artifact check failed:", file=sys.stderr)
        for violation in violations:
            print(f"  - {violation.render()}", file=sys.stderr)
        print(
            "\nKeep raw evidence under ignored local directories. "
            "Only curated public media belongs under docs/media/*.mp4.",
            file=sys.stderr,
        )
        return 1

    scope = "tracked file" if args.all else "staged/explicit file"
    plural = "" if len(paths) == 1 else "s"
    print(f"Public artifact check passed ({len(paths)} {scope}{plural} checked).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
