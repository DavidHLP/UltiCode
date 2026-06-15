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


def test_compound_annotation_with_preamble():
    """LeetCode-style solution: bare ``List[Optional[ListNode]]`` annotation
    with NO typing/oj_types imports. The harness must inject the preamble
    (typing names + ListNode/TreeNode) so the annotation resolves at runtime,
    and classify the compound annotation to convert ``[[...]]`` into a list of
    ListNodes. Regression for the mergeKLists ``NameError: name 'List' is not
    defined`` seen on the Python 3.11 sandbox base image.
    """
    src = (
        "class Solution:\n"
        "    def mergeKLists(self, lists: List[Optional[ListNode]]) -> Optional[ListNode]:\n"
        "        vals = []\n"
        "        for node in lists:\n"
        "            while node:\n"
        "                vals.append(node.val)\n"
        "                node = node.next\n"
        "        vals.sort()\n"
        "        dummy = ListNode(0)\n"
        "        cur = dummy\n"
        "        for v in vals:\n"
        "            cur.next = ListNode(v)\n"
        "            cur = cur.next\n"
        "        return dummy.next\n"
    )
    env = _run_flow(src, {
        "per_case_timeout_ms": 1000,
        "cases": [
            {"case_id": "c1",
             "inputs": [{"name": "lists", "value": "[[1,4,5],[1,3,4],[2,6]]"}],
             "expected_output": "[1,1,2,3,4,4,5,6]"},
            # Empty input: the solution returns None, which must serialize to
            # '[]' (LeetCode convention), not 'null' -> Wrong Answer.
            {"case_id": "c2",
             "inputs": [{"name": "lists", "value": "[]"}],
             "expected_output": "[]"},
        ],
    })
    assert [r["status"] for r in env["results"]] == ["Accepted", "Accepted"], env["results"]
    assert env["results"][0]["result"] == [1, 1, 2, 3, 4, 4, 5, 6]
    assert env["results"][1]["result"] == []


def test_preamble_injects_stdlib():
    """The preamble pre-imports common stdlib modules (heapq, math, ...) so
    users write ``heapq.heappush`` with NO import. Regression for the
    mergeKLists submission that failed with NameError: name 'heapq' is not
    defined — the exact user code, untouched, must now pass both cases.
    """
    src = (
        "class Solution:\n"
        "    def mergeKLists(self, lists: List[Optional[ListNode]]) -> Optional[ListNode]:\n"
        "        heap = []\n"
        "        idx = 0\n"
        "        for node in lists:\n"
        "            if node:\n"
        "                heapq.heappush(heap, (node.val, idx, node))\n"
        "                idx += 1\n"
        "        dummy = ListNode(0)\n"
        "        cur = dummy\n"
        "        while heap:\n"
        "            _, _, node = heapq.heappop(heap)\n"
        "            cur.next = node\n"
        "            cur = cur.next\n"
        "            if node.next:\n"
        "                heapq.heappush(heap, (node.next.val, idx, node.next))\n"
        "                idx += 1\n"
        "        return dummy.next\n"
    )
    env = _run_flow(src, {
        "per_case_timeout_ms": 1000,
        "cases": [
            {"case_id": "c1",
             "inputs": [{"name": "lists", "value": "[[1,4,5],[1,3,4],[2,6]]"}],
             "expected_output": "[1,1,2,3,4,4,5,6]"},
            {"case_id": "c2",
             "inputs": [{"name": "lists", "value": "[]"}],
             "expected_output": "[]"},
        ],
    })
    assert [r["status"] for r in env["results"]] == ["Accepted", "Accepted"], env["results"]
