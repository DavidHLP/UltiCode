#!/usr/bin/env python3
"""Update a single task's status (and optionally evidence) in TASKS.yaml.

Usage:
  python3 _tools/update_task.py P0-SEC-001 in_progress
  python3 _tools/update_task.py P0-SEC-001 done --evidence-file /tmp/sec001.log
  python3 _tools/update_task.py P0-SEC-001 done --timestamp 2026-07-25T01:00:00+08:00

This script is the canonical way to mutate TASKS.yaml. It loads the YAML,
mutates the dict, and dumps it back so structural validity is preserved.
Run from the migration-execution directory.
"""
from __future__ import annotations

import argparse
import datetime
import json
import sys
from pathlib import Path

import yaml


HERE = Path(__file__).resolve().parent
TASKS_PATH = HERE.parent / "TASKS.yaml"


def find_task(tasks: list, task_id: str) -> dict:
    for t in tasks:
        if t.get("id") == task_id:
            return t
    raise SystemExit(f"task not found: {task_id}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("task_id")
    parser.add_argument("status", choices=["pending", "ready", "in_progress", "blocked", "done", "superseded"])
    parser.add_argument("--timestamp", default=None,
                        help="ISO 8601 timestamp; defaults to now (UTC ISO)")
    parser.add_argument("--evidence-file", default=None,
                        help="Path to a file whose content is appended as an evidence entry (command result).")
    parser.add_argument("--evidence-note", default=None,
                        help="Free-text note for the evidence entry.")
    args = parser.parse_args()

    with TASKS_PATH.open() as f:
        tasks = yaml.safe_load(f)

    task = find_task(tasks, args.task_id)

    if args.status == "done":
        # Evidence is mandatory when marking done.
        if not args.evidence_file and not args.evidence_note:
            print("ERROR: marking done requires --evidence-file or --evidence-note",
                  file=sys.stderr)
            return 2

    task["status"] = args.status
    task["updated_at"] = args.timestamp or datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")

    if args.evidence_file:
        body = Path(args.evidence_file).read_text().rstrip()
        ev = {"command": args.evidence_file, "result": body}
        if args.evidence_note:
            ev["note"] = args.evidence_note
        task.setdefault("evidence", []).append(ev)

    if args.evidence_note and not args.evidence_file:
        ev = {"note": args.evidence_note, "ts": task["updated_at"]}
        task.setdefault("evidence", []).append(ev)

    # Round-trip via ruamel-friendly plain YAML dump so structure is identical.
    with TASKS_PATH.open("w") as f:
        yaml.safe_dump(tasks, f, sort_keys=False, allow_unicode=True, width=120)

    print(f"{args.task_id} -> {args.status} at {task['updated_at']}")
    print(f"  evidence entries: {len(task.get('evidence', []))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())