#!/usr/bin/env python3
"""Fail CI unless every selected gate ran successfully."""

from __future__ import annotations

import json
import os
import sys


def validate(results: dict, selected: dict) -> list[str]:
    errors: list[str] = []

    def result_of(gate: str) -> str:
        value = results.get(gate, "missing")
        return value.get("result", "missing") if isinstance(value, dict) else value

    def selected_flag(gate: str) -> bool:
        value = selected.get(gate)
        if value is True or value == "true":
            return True
        if value is False or value == "false":
            return False
        errors.append(f"selection for {gate} must be true or false (got {value!r})")
        return True

    if result_of("changes") != "success":
        errors.append(f"changes must succeed (got {result_of('changes')})")
    flags = {gate: selected_flag(gate) for gate in ("backend", "console", "management", "contract", "docker", "agent")}
    gates = {"governance": True, "security": True,
             "backend": flags["backend"],
             "frontend": flags["console"] or flags["management"],
             "contract": flags["contract"], "docker": flags["docker"],
             "agent": flags["agent"]}
    for gate, required in gates.items():
        result = result_of(gate)
        if required:
            if result != "success":
                errors.append(f"selected gate {gate} must succeed (got {result})")
        elif result not in ("skipped", "success"):
            errors.append(f"unselected gate {gate} must be skipped or succeed (got {result})")
    return errors


def main() -> int:
    try:
        results = json.loads(os.environ["RESULTS"])
        selected = json.loads(os.environ["SELECTED"])
        if not isinstance(results, dict) or not isinstance(selected, dict):
            raise ValueError("RESULTS and SELECTED must be JSON objects")
    except (KeyError, json.JSONDecodeError, ValueError) as error:
        print(f"::error::Invalid CI gate results: {error}", file=sys.stderr)
        return 2
    errors = validate(results, selected)
    if errors:
        for error in errors:
            print(f"::error::{error}", file=sys.stderr)
        return 1
    print("All selected CI gates succeeded.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
