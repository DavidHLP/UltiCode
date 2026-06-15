"""Subprocess worker for a single test case invocation.

CR fix (Phase 5.5 #4): the previous harness ran each case inline in
the parent process; an infinite loop consumed the entire batch's
timeout budget and the parent marked every case as Time Limit
Exceeded. This module is a single-case subprocess: the parent main.py
spawns it per case, enforces a wall-clock timeout via
``subprocess.run(timeout=...)``, and interprets exit / output as a
per-case verdict. A runaway infinite loop in case 2 cannot affect
case 1 or case 3's verdict.
"""

from __future__ import annotations

import builtins
import io
import json
import os
import sys
import time
import traceback
from contextlib import redirect_stdout
from typing import Any, Dict, Optional

import harness as H


# Block process-control primitives so the user's infinite-loop or
# "sys.exit()" attempt does not bypass the parent's timeout. The parent
# has a wall-clock timeout on this subprocess; reaching the timeout
# means SIGKILL from the parent, not a clean exit.
def _blocked_exit(_code: int = 0) -> None:  # noqa: D401
    raise RuntimeError(
        "User code attempted to terminate the case-runner subprocess "
        "(exit blocked; the parent harness enforces per-case timeout)."
    )


os._exit = _blocked_exit  # type: ignore[assignment]
sys.exit = _blocked_exit  # type: ignore[assignment]
builtins.exit = _blocked_exit  # type: ignore[assignment]


def _error_obj(exc: BaseException) -> Dict[str, Any]:
    tb = traceback.extract_tb(exc.__traceback__)
    stack: list[str] = []
    for frame in tb:
        if frame.filename.endswith("/main.py") or frame.filename.endswith("/_case_runner.py"):
            continue
        stack.append(f"{frame.filename}:{frame.lineno} in {frame.name}")
    return {
        "type": f"{type(exc).__module__}.{type(exc).__name__}",
        "message": str(exc),
        "stack": stack,
    }


def run_one_case(solution_cls: Any, method_hint: Optional[str],
                 case_spec: Dict[str, Any], per_case_timeout_ms: int) -> int:
    """Read one case from stdin, run it, write the verdict to stdout.

    Returns 0 on success, 2 on harness-internal error (parent
    treats non-zero as Runtime Error).
    """
    raw = sys.stdin.read()
    try:
        case = json.loads(raw)
    except Exception as e:  # noqa: BLE001
        verdict = {
            "case_id": "",
            "label": "",
            "elapsed_ms": 0,
            "status": "Runtime Error",
            "result": None,
            "error": _error_obj(e),
            "user_stdout": "",
            "user_stderr": "",
        }
        sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                    separators=(",", ":")))
        sys.stdout.flush()
        return 0

    case_id = str(case.get("case_id", ""))
    label = str(case.get("label", case_id))
    inputs = case.get("inputs", []) or []
    expected_output = case.get("expected_output")

    user_buf = io.StringIO()
    method_result: Any = None
    user_exc: Optional[BaseException] = None

    # Resolve method
    try:
        instance = solution_cls()
        method, _name, hints = H.resolve_method(instance, method_hint)
    except Exception as e:  # noqa: BLE001
        verdict = {
            "case_id": case_id, "label": label, "elapsed_ms": 0,
            "status": "Runtime Error", "result": None,
            "error": _error_obj(e),
            "user_stdout": "", "user_stderr": "",
        }
        sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                    separators=(",", ":")))
        sys.stdout.flush()
        return 0

    # Adapt args
    args = []
    try:
        for i, spec in enumerate(inputs):
            raw_v = H.parse_input_value(spec)
            type_override = H.input_type_hint(spec)
            ann_hint = hints[i] if i < len(hints) else None
            args.append(H.adapt_arg(raw_v, ann_hint, type_override))
    except Exception as e:  # noqa: BLE001
        verdict = {
            "case_id": case_id, "label": label, "elapsed_ms": 0,
            "status": "Runtime Error", "result": None,
            "error": _error_obj(e),
            "user_stdout": "", "user_stderr": "",
        }
        sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                    separators=(",", ":")))
        sys.stdout.flush()
        return 0

    # Run with timeout (defense in depth: we ALSO have the parent's
    # subprocess.run timeout; the inner check is for soft detection).
    timeout_s = per_case_timeout_ms / 1000.0
    start = time.monotonic()
    try:
        with redirect_stdout(user_buf):
            method_result = method(*args)
        elapsed = time.monotonic() - start
        if elapsed > timeout_s:
            # Soft TLE: method returned, but took too long. Mark the
            # case as TLE — better signal than accepting possibly-wrong
            # output that the next test case may be invalidated by.
            verdict = {
                "case_id": case_id, "label": label,
                "elapsed_ms": int(elapsed * 1000),
                "status": "Time Limit Exceeded", "interrupted": True,
                "result": None,
                "user_stdout": user_buf.getvalue()[:H.MAX_USER_STDOUT_BYTES],
                "user_stderr": "",
            }
            sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                        separators=(",", ":")))
            sys.stdout.flush()
            return 0
    except SystemExit as se:
        user_exc = RuntimeError(f"User code raised SystemExit({se.code})")
    except BaseException as e:  # noqa: BLE001
        user_exc = e

    elapsed_ms = int((time.monotonic() - start) * 1000)
    user_stdout = user_buf.getvalue()[:H.MAX_USER_STDOUT_BYTES]

    if user_exc is not None:
        verdict = {
            "case_id": case_id, "label": label, "elapsed_ms": elapsed_ms,
            "status": "Runtime Error", "result": None,
            "error": _error_obj(user_exc),
            "user_stdout": user_stdout, "user_stderr": "",
        }
        sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                    separators=(",", ":")))
        sys.stdout.flush()
        return 0

    # LeetCode convention: a None return on a ListNode/TreeNode-typed method
    # is an empty structure (serializes to '[]', not 'null'). Apply before
    # jsonable so a correct empty-input solution isn't marked Wrong Answer.
    method_result = H.normalize_return_value(method_result, method)

    # CR fix #7/#8: jsonable() now raises on cycles / depth / node-count /
    # non-finite floats. Convert to per-case RE.
    try:
        jsonable_result = H.jsonable(method_result)
    except Exception as e:  # noqa: BLE001
        verdict = {
            "case_id": case_id, "label": label, "elapsed_ms": elapsed_ms,
            "status": "Runtime Error", "result": None,
            "error": _error_obj(e),
            "user_stdout": user_stdout, "user_stderr": "",
        }
        sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                    separators=(",", ":")))
        sys.stdout.flush()
        return 0

    try:
        actual_json = json.dumps(jsonable_result, separators=(",", ":"), allow_nan=False)
    except (ValueError, TypeError) as e:
        verdict = {
            "case_id": case_id, "label": label, "elapsed_ms": elapsed_ms,
            "status": "Runtime Error", "result": None,
            "error": _error_obj(e),
            "user_stdout": user_stdout, "user_stderr": "",
        }
        sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                    separators=(",", ":")))
        sys.stdout.flush()
        return 0

    if expected_output is None:
        passed = False
    else:
        try:
            passed = H.normalize_json_str(actual_json) == H.normalize_json_str(str(expected_output))
        except Exception:  # noqa: BLE001
            passed = False

    verdict = {
        "case_id": case_id, "label": label, "elapsed_ms": elapsed_ms,
        "status": "Accepted" if passed else "Wrong Answer",
        "result": jsonable_result,
        "user_stdout": user_stdout, "user_stderr": "",
    }
    sys.stdout.write(json.dumps(verdict, ensure_ascii=False,
                                separators=(",", ":")))
    sys.stdout.flush()
    return 0


def main() -> int:
    """Entry: read method_hint + per_case_timeout_ms + solution_path from
    argv, import solution, then read one case from stdin and run it.
    """
    # argv: argv[1] = method_hint (or ''), argv[2] = per_case_timeout_ms
    method_hint = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else None
    per_case_timeout_ms = int(sys.argv[2]) if len(sys.argv) > 2 else 1000
    # Match main.py: /job in production, $SOLUTION_DIR in tests.
    solution_dir = os.environ.get("SOLUTION_DIR", "/job")
    sys.path.insert(0, solution_dir)
    # Load via H.load_solution_module so the LeetCode preamble is injected;
    # see main.py for rationale (bare annotations + Python 3.11 base image).
    solution_module = H.load_solution_module(os.path.join(solution_dir, "solution.py"))
    solution_cls = getattr(solution_module, "Solution")
    return run_one_case(solution_cls, method_hint, {}, per_case_timeout_ms)


if __name__ == "__main__":
    # Don't call sys.exit() — the guard installed at module top
    # blocks it. The subprocess returns naturally; the verdict is
    # already on stdout, which is what the parent consumes.
    main()
