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

import json
import os
import sys
import time
import traceback
from typing import Any, Dict

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


def _fallback_verdict(tc: Dict[str, Any], *, status: str, elapsed_ms: int,
                      error: Dict[str, Any] | None = None, interrupted: bool = False,
                      stdout: str = "", stderr: str = "") -> Dict[str, Any]:
    """Canonical verdict for a process-level failure of the case subprocess.

    The parent is retained only as a process adapter: it cannot run user
    code, so it owns just this fallback shape. The live
    ``_case_runner.run_one_case`` owns verdicts for cases that actually
    executed (with real ``peak_memory_bytes`` / ``cpu_ms`` measurements).
    This helper keeps the fallback shape aligned with the child's verdict
    keys (including ``peak_memory_bytes``) so downstream consumers see one
    verdict shape; an unmeasurable killed or panicked subprocess reports
    zero peak memory.
    """
    verdict: Dict[str, Any] = {
        "case_id": str(tc.get("id", tc.get("case_id", ""))),
        "label": str(tc.get("label", "")),
        "elapsed_ms": elapsed_ms,
        "elapsed_us": elapsed_ms * 1000,
        "cpu_ms": 0,
        "status": status,
        "interrupted": interrupted,
        "result": None,
        "peak_memory_bytes": 0,
        "user_stdout": stdout[:H.MAX_USER_STDOUT_BYTES],
        "user_stderr": stderr[:H.MAX_USER_STDOUT_BYTES],
    }
    if error is not None:
        verdict["error"] = error
    return verdict


def run(solution_module: Any, input_path: str) -> int:
    with open(input_path, encoding="utf-8") as f:
        envelope_in = json.load(f)
    per_case_timeout = int(envelope_in.get("per_case_timeout_ms", DEFAULT_PER_CASE_TIMEOUT_MS))
    # ADR-002 §8 (P0-2): per-run memory ceiling forwarded to each per-case
    # subprocess so it can self-report Memory Limit Exceeded.
    memory_limit_bytes = int(envelope_in.get("memory_limit_bytes", 0) or 0)
    cases = envelope_in.get("cases", []) or []
    method_hint = envelope_in.get("method_name") or None

    if not hasattr(solution_module, "Solution"):
        raise RuntimeError(
            "User code must define a class named 'Solution'. "
            "Detected names: " + ", ".join(n for n in dir(solution_module) if not n.startswith("_")))
    solution_cls = getattr(solution_module, "Solution")

    # CR fix (Phase 5.5 #4): dispatch each case in a per-case subprocess.
    # The previous inline per-case loop was the root cause of "infinite
    # loop in case 1 marks the entire batch as TLE". Now case 1's infinite
    # loop is SIGKILLed by the parent's subprocess.run timeout and the
    # remaining cases proceed normally.
    #
    # We use the same Python interpreter that's running this harness,
    # pointing it at the sibling _case_runner.py module. The subprocess
    # is invoked once per case, with stdin=piped a JSON of that single
    # case and stdout=piped the verdict JSON.
    results = []
    total_start = time.monotonic()
    case_runner = os.path.join(os.path.dirname(__file__), "_case_runner.py")
    timeout_s = per_case_timeout / 1000.0
    # Add a small overhead so a case that barely hits the soft timeout
    # gets a chance to write its verdict before the parent kills it.
    hard_case_timeout_s = timeout_s + 1.0

    for tc in cases:
        verdict = _run_case_in_subprocess(
            case_runner, solution_cls, method_hint, tc, per_case_timeout, hard_case_timeout_s,
            memory_limit_bytes)
        results.append(verdict)
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


def _run_case_in_subprocess(case_runner: str, solution_cls: Any,
                             method_hint: str | None,
                             tc: Dict[str, Any], per_case_timeout_ms: int,
                             hard_timeout_s: float,
                             memory_limit_bytes: int = 0) -> Dict[str, Any]:
    """Spawn _case_runner.py for one case, enforce per-case timeout,
    return the verdict dict. On any failure (timeout, non-zero exit,
    unparseable JSON), synthesize a sensible per-case verdict rather
    than poisoning the whole batch.
    """
    import subprocess  # local import keeps top-level imports stable

    # We can't pass the solution_cls directly across processes, so the
    # subprocess re-imports solution from /job. To do that, the parent
    # writes nothing extra — the subprocess argv tells it which method
    # to call, and the per-case JSON on stdin carries the input data.
    argv = [sys.executable, case_runner, method_hint or "", str(per_case_timeout_ms),
            str(memory_limit_bytes)]
    case_payload = json.dumps(tc, ensure_ascii=False,
                              separators=(",", ":"), allow_nan=False)
    try:
        proc = subprocess.run(
            argv,
            input=case_payload.encode("utf-8"),
            capture_output=True,
            timeout=hard_timeout_s,
            check=False,
        )
    except subprocess.TimeoutExpired as te:
        # The case runner was still running when the parent killed it.
        return _fallback_verdict(
            tc, status="Time Limit Exceeded",
            elapsed_ms=int(hard_timeout_s * 1000), interrupted=True,
            stdout=(te.stdout or b"").decode("utf-8", errors="ignore"),
            stderr=(te.stderr or b"").decode("utf-8", errors="ignore"),
        )

    stdout = proc.stdout.decode("utf-8", errors="ignore") if proc.stdout else ""
    stderr = proc.stderr.decode("utf-8", errors="ignore") if proc.stderr else ""
    if proc.returncode != 0:
        # The case runner panicked (e.g. import error, JSON dump failed).
        # Surface as a Runtime Error for this case only.
        return _fallback_verdict(
            tc, status="Runtime Error", elapsed_ms=0,
            error={
                "type": "CaseRunnerPanic",
                "message": f"case runner exited with code {proc.returncode}",
                "stack": [line for line in stderr.splitlines() if line.strip()],
            },
            stdout=stdout, stderr=stderr,
        )
    try:
        verdict = json.loads(stdout)
    except Exception:  # noqa: BLE001
        return _fallback_verdict(
            tc, status="Runtime Error", elapsed_ms=0,
            error={
                "type": "CaseRunnerPanic",
                "message": "case runner emitted unparseable JSON",
                "stack": [],
            },
            stdout=stdout, stderr=stderr,
        )
    return verdict


def main() -> int:
    _install_exit_guard()
    try:
        input_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_INPUT_PATH
        solution_dir = os.environ.get("SOLUTION_DIR", "/tmp")
        sys.path.insert(0, solution_dir)
        try:
            # Load via H.load_solution_module so the LeetCode preamble
            # (typing names + ListNode/TreeNode) is injected into the user
            # module before its code runs — otherwise bare annotations raise
            # NameError at import time on the sandbox's Python 3.11 base.
            solution = H.load_solution_module(
                os.path.join(solution_dir, USER_SOLUTION_MODULE + ".py")
            )
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
