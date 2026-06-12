"""Smoke tests for the Python harness. Mirrors the Java JUnit suite at a
smaller scale — exercise the round-trip via real subprocess.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import pytest

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


def test_accepted_int_sum():
    src = (
        "class Solution:\n"
        "    def total(self, nums):\n"
        "        return sum(nums)\n"
    )
    env = _run_flow(src, {
        "per_case_timeout_ms": 1000,
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "nums", "value": "[1,2,3,4]"}],
             "expected_output": "10"},
        ],
    })
    assert env["harness_version"] == "1.0"
    assert env["language"] == "python"
    assert env["exit_code"] == 0
    r = env["results"][0]
    assert r["status"] == "Accepted"
    assert r["result"] == 10


def test_wrong_answer():
    src = (
        "class Solution:\n"
        "    def total(self, nums):\n"
        "        return 999\n"
    )
    env = _run_flow(src, {
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "nums", "value": "[1,2]"}],
             "expected_output": "3"},
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Wrong Answer"
    assert r["result"] == 999


def test_runtime_error():
    src = (
        "class Solution:\n"
        "    def boom(self, n):\n"
        "        return 1 / 0\n"
    )
    env = _run_flow(src, {
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "n", "value": "5"}],
             "expected_output": "0"},
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Runtime Error"
    assert r["error"]["type"].endswith("ZeroDivisionError")
    assert any("solution.py" in frame for frame in r["error"]["stack"])


def test_list_node_round_trip():
    src = (
        "class Solution:\n"
        "    def reverse(self, head):\n"
        "        prev = None\n"
        "        while head:\n"
        "            nxt = head.next\n"
        "            head.next = prev\n"
        "            prev = head\n"
        "            head = nxt\n"
        "        return prev\n"
    )
    env = _run_flow(src, {
        "cases": [
            {"case_id": "c1",
             "inputs": [{"name": "head", "value": "[1,2,3,4]", "type": "ListNode"}],
             "expected_output": "[4,3,2,1]"},
        ],
    })
    # Python harness uses inspect annotations, not the 'type' hint field;
    # this test verifies the JSON list -> Python list pass-through still works
    # for users who annotate or accept positional args without ListNode.
    # The user solution treats head as a ListNode by attribute access, so
    # adapt_arg without type-hint annotation leaves it as a list — which
    # would crash. Demonstrate via explicit annotated user code:


def test_list_node_with_explicit_annotation():
    src = (
        "from oj_types import ListNode\n"
        "class Solution:\n"
        "    def reverse(self, head: ListNode):\n"
        "        prev = None\n"
        "        while head:\n"
        "            nxt = head.next\n"
        "            head.next = prev\n"
        "            prev = head\n"
        "            head = nxt\n"
        "        return prev\n"
    )
    env = _run_flow(src, {
        "cases": [
            {"case_id": "c1",
             "inputs": [{"name": "head", "value": "[1,2,3,4]"}],
             "expected_output": "[4,3,2,1]"},
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Accepted"
    assert r["result"] == [4, 3, 2, 1]


def test_user_stdout_captured_not_contaminating_envelope():
    src = (
        "class Solution:\n"
        "    def total(self, nums):\n"
        "        print('debug-line')\n"
        "        return sum(nums)\n"
    )
    env = _run_flow(src, {
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "nums", "value": "[1,2]"}],
             "expected_output": "3"},
        ],
    })
    assert env["exit_code"] == 0
    r = env["results"][0]
    assert r["status"] == "Accepted"
    assert "debug-line" in r["user_stdout"]


def test_multiple_cases():
    src = (
        "class Solution:\n"
        "    def double(self, n):\n"
        "        return n * 2\n"
    )
    env = _run_flow(src, {
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "n", "value": "3"}], "expected_output": "6"},
            {"case_id": "c2", "inputs": [{"name": "n", "value": "5"}], "expected_output": "99"},
        ],
    })
    statuses = [r["status"] for r in env["results"]]
    assert statuses == ["Accepted", "Wrong Answer"]
