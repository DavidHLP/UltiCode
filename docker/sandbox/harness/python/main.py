#!/usr/bin/env python3
"""UltiCode sandbox harness — Python entry point.

Contract (mirrors the Java harness — see Java Main.java javadoc):
  argv[1] = path to input.json (default: /job/input.json)
  stdout  = single JSON envelope (always, even on per-case error)
  stderr  = harness-level panic stack traces
  exit    = 0 normal; 2 harness panic
"""

from __future__ import annotations

import io
import json
import os
import sys
import time
import traceback
from contextlib import redirect_stdout
from typing import Any, Dict, List

import harness as H

HARNESS_VERSION = "1.0"
LANGUAGE = "python"
DEFAULT_INPUT_PATH = "/job/input.json"
DEFAULT_PER_CASE_TIMEOUT_MS = 1000
MAX_USER_STDOUT_BYTES = 64 * 1024
USER_SOLUTION_MODULE = "solution"


def _truncate(s: str) -> str:
    if not s:
        return ""
    raw = s.encode("utf-8")
    if len(raw) <= MAX_USER_STDOUT_BYTES:
        return s
    head = raw[:MAX_USER_STDOUT_BYTES].decode("utf-8", errors="ignore")
    return head + f"\n... [truncated, original={len(raw)} bytes]"


def _error_obj(exc: BaseException) -> Dict[str, Any]:
    tb = traceback.extract_tb(exc.__traceback__)
    stack: List[str] = []
    for frame in tb:
        # Hide harness frames; keep user solution frames.
        if frame.filename.endswith("/main.py") or frame.filename.endswith("/harness.py"):
            continue
        stack.append(f"{frame.filename}:{frame.lineno} in {frame.name}")
    return {
        "type": f"{type(exc).__module__}.{type(exc).__name__}",
        "message": str(exc),
        "stack": stack,
    }


def _run_case(solution_cls: Any, testcase: Dict[str, Any], per_case_timeout_ms: int) -> Dict[str, Any]:
    case_id = str(testcase.get("case_id", ""))
    label = str(testcase.get("label", case_id))
    inputs = testcase.get("inputs", []) or []
    expected_output = testcase.get("expected_output")

    result: Dict[str, Any] = {"case_id": case_id, "label": label}

    user_buf = io.StringIO()
    instance = solution_cls()
    try:
        method, _method_name, hints = H.find_first_public_method(instance)
    except RuntimeError as e:
        return {**result, "elapsed_ms": 0, "status": "Runtime Error",
                "result": None, "error": _error_obj(e),
                "user_stdout": "", "user_stderr": ""}

    # Adapt args using parameter hints when available.
    args = []
    try:
        for i, spec in enumerate(inputs):
            raw = H.parse_input_value(spec)
            hint = hints[i] if i < len(hints) else None
            args.append(H.adapt_arg(raw, hint))
    except Exception as e:
        return {**result, "elapsed_ms": 0, "status": "Runtime Error",
                "result": None, "error": _error_obj(e),
                "user_stdout": "", "user_stderr": ""}

    # Run with per-case wall-clock budget. Python lacks safe thread.stop, so
    # we measure elapsed and rely on the outer backend ProcessBuilder timeout
    # for hard kills on CPU-bound infinite loops.
    timeout_s = per_case_timeout_ms / 1000.0
    start = time.monotonic()
    method_result: Any = None
    user_exc: BaseException | None = None
    timed_out = False
    try:
        with redirect_stdout(user_buf):
            method_result = method(*args)
            elapsed = time.monotonic() - start
            if elapsed > timeout_s:
                timed_out = True
                method_result = None
    except BaseException as e:  # noqa: BLE001
        user_exc = e

    elapsed_ms = int((time.monotonic() - start) * 1000)
    user_stdout = _truncate(user_buf.getvalue())

    if timed_out:
        return {**result, "elapsed_ms": elapsed_ms, "status": "Time Limit Exceeded",
                "result": None, "interrupted": False,
                "user_stdout": user_stdout, "user_stderr": ""}
    if user_exc is not None:
        return {**result, "elapsed_ms": elapsed_ms, "status": "Runtime Error",
                "result": None, "error": _error_obj(user_exc),
                "user_stdout": user_stdout, "user_stderr": ""}

    jsonable = H.jsonable(method_result)
    actual_json = json.dumps(jsonable, separators=(",", ":"))
    if expected_output is None:
        passed = False
    else:
        try:
            passed = H.normalize_json_str(actual_json) == H.normalize_json_str(str(expected_output))
        except Exception:
            passed = False

    return {**result, "elapsed_ms": elapsed_ms,
            "status": "Accepted" if passed else "Wrong Answer",
            "result": jsonable, "user_stdout": user_stdout, "user_stderr": ""}


def run(solution_module: Any, input_path: str) -> int:
    with open(input_path, encoding="utf-8") as f:
        envelope_in = json.load(f)
    per_case_timeout = int(envelope_in.get("per_case_timeout_ms", DEFAULT_PER_CASE_TIMEOUT_MS))
    cases = envelope_in.get("cases", []) or []

    if not hasattr(solution_module, "Solution"):
        raise RuntimeError(
            "User code must define a class named 'Solution'. "
            "Detected names: " + ", ".join(n for n in dir(solution_module) if not n.startswith("_")))
    solution_cls = getattr(solution_module, "Solution")

    results = []
    total_start = time.monotonic()
    for tc in cases:
        results.append(_run_case(solution_cls, tc, per_case_timeout))
    total_elapsed_ms = int((time.monotonic() - total_start) * 1000)

    envelope_out = {
        "harness_version": HARNESS_VERSION,
        "language": LANGUAGE,
        "exit_code": 0,
        "total_elapsed_ms": total_elapsed_ms,
        "results": results,
    }
    sys.stdout.write(json.dumps(envelope_out, ensure_ascii=False, separators=(",", ":")))
    sys.stdout.flush()
    return 0


def main() -> int:
    try:
        input_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_INPUT_PATH
        # Make Solution module importable from the directory containing
        # /tmp/solution.py (or wherever the backend dropped it).
        sys.path.insert(0, os.environ.get("SOLUTION_DIR", "/tmp"))
        import solution  # type: ignore  # noqa: WPS433
        return run(solution, input_path)
    except SystemExit:
        raise
    except BaseException:  # noqa: BLE001
        traceback.print_exc(file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
