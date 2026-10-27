#!/usr/bin/env python3
"""Publish only bounded public exception types/source coordinates from test logs."""
import json
import os
import re
from pathlib import Path

LIMIT = 1_000_000
KINDS = re.compile(r"\b(?:org|com|java|javax|jakarta|io)\.(?:[A-Za-z_$][A-Za-z0-9_$]*\.)*[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error)\b")
FRAMES = re.compile(r"\bat (com\.ulticode\.[A-Za-z0-9_.$]+)\(([A-Za-z0-9_$]+\.java:[0-9]+)\)")
PHASES = {"environment", "sandbox", "fresh-migrations", "backfill-and-cutover", "launch",
          "auth-environment", "fixtures", "browser-base", "browser-business", "complete"}


def summary(paths):
    kinds, frames = set(), []
    for path in paths:
        if path.is_symlink() or not path.is_file():
            continue
        try:
            with path.open("rb") as stream:
                stream.seek(max(0, path.stat().st_size - LIMIT))
                text = stream.read(LIMIT).decode("utf-8", errors="replace")
        except OSError:
            continue
        kinds.update(KINDS.findall(text))
        for symbol, location in FRAMES.findall(text):
            frame = {"symbol": symbol, "location": location}
            if frame not in frames:
                frames.append(frame)
    return {"exception_classes": sorted(kinds)[:20], "public_source_frames": frames[-16:]}


def build_report(root, phase, exit_code, project):
    private = summary((root / ".local/acceptance").glob("private-*.log"))
    services = {}
    for owner in ("app", "submission", "admin", "auth", "judge"):
        item = summary([root / "logs" / f"backend-{owner}.{kind}.log" for kind in ("out", "err")])
        if item["exception_classes"] or item["public_source_frames"]:
            services[owner] = item
    return {
        "evidence": "disposable-real-services",
        "phase": phase if phase in PHASES else "unknown",
        "passed": exit_code == 0,
        "project": project if re.fullmatch(r"ulticode-acceptance-[0-9]{1,20}-[0-9]{1,20}", project) else "unknown",
        "raw_logs_published": False,
        "exception_classes": private["exception_classes"],
        "service_diagnostics": services,
    }


def main():
    try:
        root = Path.cwd()
        report = build_report(root, os.environ.get("ACCEPTANCE_PHASE", "unknown"),
                              int(os.environ.get("ACCEPTANCE_EXIT", "1")),
                              os.environ.get("COMPOSE_PROJECT_NAME", ""))
        output = root / ".local/acceptance/installation.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n")
        if not report["passed"]:
            print(json.dumps({"acceptanceFailureDiagnostics": report}, separators=(",", ":")))
    except (OSError, ValueError):
        print("Acceptance report could not be written; private logs were not printed")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
