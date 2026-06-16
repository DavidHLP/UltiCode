"""ADR-002 §8 resource measurement contract tests for the Python harness.

Exercises the per-case timeout (TLE), per-case memory ceiling (MLE), and
the new measurement fields (elapsed_us / cpu_ms / peak_memory_bytes) via
real subprocess invocations of main.py. Kept in a separate file from
test_harness.py so the §8 contract has a focused regression suite.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

HARNESS_DIR = Path(__file__).parent
MAIN_PY = HARNESS_DIR / "main.py"


def _run_flow(solution_src: str, input_payload: dict) -> dict:
    """Drop solution.py + input.json into a temp dir, run main.py, parse stdout."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        (tmp_path / "solution.py").write_text(solution_src, encoding="utf-8")
        input_path = tmp_path / "input.json"
        input_path.write_text(json.dumps(input_payload), encoding="utf-8")

        env = {**os.environ, "SOLUTION_DIR": str(tmp_path), "PYTHONPATH": str(HARNESS_DIR)}
        result = subprocess.run(
            [sys.executable, str(MAIN_PY), str(input_path)],
            env=env, capture_output=True, text=True, timeout=30,
        )
        assert result.returncode == 0, f"main.py exited {result.returncode}\nstderr:\n{result.stderr}"
        return json.loads(result.stdout)


def test_resource_fields_present_on_accepted():
    """Accepted verdicts carry the ADR-002 §8 measurement fields."""
    src = (
        "class Solution:\n"
        "    def add(self, a):\n"
        "        return a + 1\n"
    )
    env = _run_flow(src, {
        "per_case_timeout_ms": 1000,
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "a", "value": "41"}],
             "expected_output": "42"},
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Accepted"
    assert "elapsed_us" in r and r["elapsed_us"] >= 0
    assert "cpu_ms" in r and r["cpu_ms"] >= 0
    # peak RSS of a real Python subprocess is non-zero
    assert r["peak_memory_bytes"] > 0


def test_time_limit_exceeded_infinite_loop():
    """A runaway infinite loop is TLE'd per-case, envelope preserved."""
    src = (
        "class Solution:\n"
        "    def loop(self, n):\n"
        "        while True:\n"
        "            pass\n"
    )
    env = _run_flow(src, {
        "per_case_timeout_ms": 200,  # 200ms soft; parent kills at ~1.2s
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}]},
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Time Limit Exceeded"
    assert "elapsed_us" in r
    assert "cpu_ms" in r


def test_memory_limit_exceeded():
    """Allocating past memory_limit_bytes self-reports MLE (ADR-002 §8 P0-2)."""
    src = (
        "class Solution:\n"
        "    def alloc(self, n):\n"
        "        # ~100MB allocation; ceiling below this -> MLE\n"
        "        buf = bytearray(100 * 1024 * 1024)\n"
        "        return len(buf)\n"
    )
    env = _run_flow(src, {
        "per_case_timeout_ms": 5000,
        "memory_limit_bytes": 30 * 1024 * 1024,  # 30MB ceiling
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}],
             "expected_output": "104857600"},
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Memory Limit Exceeded", r
    assert r["peak_memory_bytes"] > 30 * 1024 * 1024


def test_memory_limit_not_triggered_under_ceiling():
    """Small allocation under the ceiling stays Accepted (MLE not over-eager)."""
    src = (
        "class Solution:\n"
        "    def alloc(self, n):\n"
        "        return len(bytearray(1024))\n"
    )
    env = _run_flow(src, {
        "per_case_timeout_ms": 2000,
        "memory_limit_bytes": 256 * 1024 * 1024,  # 256MB ceiling
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}],
             "expected_output": "1024"},
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Accepted", r
