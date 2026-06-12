#!/usr/bin/env python3
"""UltiCode sandbox harness — Python entry point.

CR fixes applied:
- ``os._exit`` and ``sys.exit`` are blocked BEFORE importing user code so a
  malicious ``Solution`` cannot terminate the interpreter and skip envelope
  emission. ``SystemExit`` raised mid-case is also caught and surfaced as
  Runtime Error for that case.
- ``import solution`` happens INSIDE a try/except so import-time errors
  become a harness panic (exit 2 + stderr stack) instead of taking the
  whole process down silently.
- The reflective dispatch requires the Solution class to expose exactly
  ONE public method (or you must pass ``method_name`` in input.json).
- ``adapt_arg`` honors the input spec's ``type`` field (which the backend
  populates from the problem signature) so unannotated user code still
  receives a real ``ListNode`` / ``TreeNode`` instead of a raw list.
- ``json.dumps(envelope, allow_nan=False)`` so a per-case NaN/Infinity
  result is rejected by the per-case path (not corrupting the envelope).
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

HARNESS_VERSION = "1.0"
LANGUAGE = "python"
DEFAULT_INPUT_PATH = "/job/input.json"
DEFAULT_PER_CASE_TIMEOUT_MS = 1000
MAX_USER_STDOUT_BYTES = 64 * 1024
USER_SOLUTION_MODULE = "solution"

# ── Process-control guard (CR fix #1, Python equivalent) ──────────────────
#
# Block exit primitives BEFORE any user code can run. We replace the
# functions on the modules themselves so even ``import os; os._exit(0)``
# from inside user code raises. The harness still uses ``sys.exit(rc)``
# at the very bottom of main() — by that point user code is no longer
# running, so we re-allow it via the captured originals.

_REAL_OS_EXIT = os._exit
_REAL_SYS_EXIT = sys.exit


def _blocked_exit(code: int = 0):  # noqa: D401
    raise RuntimeError(
        f"User code attempted to terminate the harness process (exit {code})"
    )


def _install_exit_guard() -> None:
    os._exit = _blocked_exit  # type: ignore[assignment]
    sys.exit = _blocked_exit  # type: ignore[assignment]
    import builtins
    builtins.exit = _blocked_exit  # type: ignore[assignment]
    builtins.quit = _blocked_exit  # type: ignore[assignment]


def _harness_exit(code: int) -> None:
    """The only legal exit path for the Python harness."""
    os._exit = _REAL_OS_EXIT  # type: ignore[assignment]
    sys.exit = _REAL_SYS_EXIT  # type: ignore[assignment]
    _REAL_SYS_EXIT(code)


import harness as H  # noqa: E402  (after the guard module-level constants are set)


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


def _run_case(solution_cls: Any, method_hint: str | None,
              testcase: Dict[str, Any], per_case_timeout_ms: int) -> Dict[str, Any]:
    case_id = str(testcase.get("case_id", ""))
    label = str(testcase.get("label", case_id))
    inputs = testcase.get("inputs", []) or []
    expected_output = testcase.get("expected_output")

    result: Dict[str, Any] = {"case_id": case_id, "label": label}
    user_buf = io.StringIO()

    # Instantiate + resolve method (could raise on bad Solution shape)
    try:
        instance = solution_cls()
        method, _method_name, hints = H.resolve_method(instance, method_hint)
    except Exception as e:  # noqa: BLE001
        return {**result, "elapsed_ms": 0, "status": "Runtime Error",
                "result": None, "error": _error_obj(e),
                "user_stdout": "", "user_stderr": ""}

    # Adapt args using input spec's 'type' field first, annotation as fallback.
    args = []
    try:
        for i, spec in enumerate(inputs):
            raw = H.parse_input_value(spec)
            type_override = H.input_type_hint(spec)
            ann_hint = hints[i] if i < len(hints) else None
            args.append(H.adapt_arg(raw, ann_hint, type_override))
    except Exception as e:  # noqa: BLE001
        return {**result, "elapsed_ms": 0, "status": "Runtime Error",
                "result": None, "error": _error_obj(e),
                "user_stdout": "", "user_stderr": ""}

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
    except SystemExit as se:
        # CR fix: user code called sys.exit / exit / quit (now blocked, but
        # SystemExit could still be raised explicitly). Treat as RE.
        user_exc = RuntimeError(f"User code raised SystemExit({se.code})")
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

    # CR fix #7/#8: jsonable() now raises on cycles, depth, node-count,
    # non-finite floats. Convert to per-case RE rather than envelope panic.
    try:
        jsonable_result = H.jsonable(method_result)
    except Exception as e:  # noqa: BLE001
        return {**result, "elapsed_ms": elapsed_ms, "status": "Runtime Error",
                "result": None, "error": _error_obj(e),
                "user_stdout": user_stdout, "user_stderr": ""}

    try:
        actual_json = json.dumps(jsonable_result, separators=(",", ":"), allow_nan=False)
    except (ValueError, TypeError) as e:
        return {**result, "elapsed_ms": elapsed_ms, "status": "Runtime Error",
                "result": None, "error": _error_obj(e),
                "user_stdout": user_stdout, "user_stderr": ""}

    if expected_output is None:
        passed = False
    else:
        try:
            passed = H.normalize_json_str(actual_json) == H.normalize_json_str(str(expected_output))
        except Exception:  # noqa: BLE001
            passed = False

    return {**result, "elapsed_ms": elapsed_ms,
            "status": "Accepted" if passed else "Wrong Answer",
            "result": jsonable_result, "user_stdout": user_stdout, "user_stderr": ""}


def run(solution_module: Any, input_path: str) -> int:
    with open(input_path, encoding="utf-8") as f:
        envelope_in = json.load(f)
    per_case_timeout = int(envelope_in.get("per_case_timeout_ms", DEFAULT_PER_CASE_TIMEOUT_MS))
    cases = envelope_in.get("cases", []) or []
    method_hint = envelope_in.get("method_name") or None

    if not hasattr(solution_module, "Solution"):
        raise RuntimeError(
            "User code must define a class named 'Solution'. "
            "Detected names: " + ", ".join(n for n in dir(solution_module) if not n.startswith("_")))
    solution_cls = getattr(solution_module, "Solution")

    results = []
    total_start = time.monotonic()
    for tc in cases:
        results.append(_run_case(solution_cls, method_hint, tc, per_case_timeout))
    total_elapsed_ms = int((time.monotonic() - total_start) * 1000)

    envelope_out = {
        "harness_version": HARNESS_VERSION,
        "language": LANGUAGE,
        "exit_code": 0,
        "total_elapsed_ms": total_elapsed_ms,
        "results": results,
    }
    # allow_nan=False guards the envelope itself; per-case results have
    # already been filtered for non-finite values above.
    sys.stdout.write(json.dumps(envelope_out, ensure_ascii=False,
                                separators=(",", ":"), allow_nan=False))
    sys.stdout.flush()
    return 0


def main() -> int:
    _install_exit_guard()
    try:
        input_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_INPUT_PATH
        sys.path.insert(0, os.environ.get("SOLUTION_DIR", "/tmp"))
        try:
            import solution  # type: ignore  # noqa: WPS433
        except BaseException:  # noqa: BLE001
            # Import-time errors are harness panics — emit stderr + exit 2.
            raise
        return run(solution, input_path)
    except SystemExit:
        # If somehow SystemExit escapes the per-case path, treat as panic.
        traceback.print_exc(file=sys.stderr)
        return 2
    except BaseException:  # noqa: BLE001
        traceback.print_exc(file=sys.stderr)
        return 2


if __name__ == "__main__":
    _harness_exit(main())
