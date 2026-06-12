"""Adversarial smoke tests for the Python harness — maps to CR findings.

CR coverage:
- vacuous list_node_round_trip test fixed (now has real assertions)
- system exit blocked
- cyclic result -> Runtime Error
- NaN result -> Runtime Error
- input spec 'type' metadata honored without annotation
- multiple public methods rejected without method_name
- method_name hint disambiguates
- TreeNode case
- ListNode[] case
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


def _run_flow(solution_src: str, input_payload: dict, expect_exit: int = 0):
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
        assert result.returncode == expect_exit, (
            f"main.py exited {result.returncode} (expected {expect_exit})\n"
            f"stderr:\n{result.stderr}\nstdout:\n{result.stdout}"
        )
        if expect_exit == 0:
            return json.loads(result.stdout), result.stderr
        return None, result.stderr


# ── Process-control guard ─────────────────────────────────────────────────


def test_sys_exit_blocked():
    src = (
        "class Solution:\n"
        "    def boom(self, n):\n"
        "        import sys\n"
        "        sys.exit(0)\n"
        "        return 42\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}], "expected_output": "0"}
    ]})
    assert env["exit_code"] == 0
    r = env["results"][0]
    assert r["status"] == "Runtime Error"
    assert "exit" in r["error"]["message"].lower() or "terminate" in r["error"]["message"].lower()


def test_os_underscore_exit_blocked():
    src = (
        "class Solution:\n"
        "    def boom(self, n):\n"
        "        import os\n"
        "        os._exit(0)\n"
        "        return 42\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}], "expected_output": "0"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Runtime Error"


# ── Cycle detection ───────────────────────────────────────────────────────


def test_cyclic_list_result():
    src = (
        "class Solution:\n"
        "    def cycle(self, n):\n"
        "        a = [1]\n"
        "        a.append(a)\n"
        "        return a\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}], "expected_output": "[]"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Runtime Error"
    assert "Cyclic" in r["error"]["message"]


def test_cyclic_dict_result():
    src = (
        "class Solution:\n"
        "    def cycle(self, n):\n"
        "        d = {}\n"
        "        d['self'] = d\n"
        "        return d\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}], "expected_output": "{}"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Runtime Error"


# ── Non-finite values ─────────────────────────────────────────────────────


def test_nan_result_rejected():
    src = (
        "class Solution:\n"
        "    def bad(self, n):\n"
        "        return float('nan')\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}], "expected_output": "0"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Runtime Error"
    assert "Non-finite" in r["error"]["message"] or "nan" in r["error"]["message"].lower()


def test_infinity_result_rejected():
    src = (
        "class Solution:\n"
        "    def bad(self, n):\n"
        "        return float('inf')\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}], "expected_output": "0"}
    ]})
    assert env["results"][0]["status"] == "Runtime Error"


# ── Input type metadata (CR #5: previously vacuous test) ──────────────────


def test_input_type_metadata_honored_without_annotation():
    """User code without annotations gets a real ListNode when input spec
    declares ``type: ListNode``. Previously this would pass a raw list and
    user .next access would crash."""
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
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1",
         "inputs": [{"name": "head", "value": "[1,2,3,4]", "type": "ListNode"}],
         "expected_output": "[4,3,2,1]"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Accepted", r
    assert r["result"] == [4, 3, 2, 1]


def test_treenode_input_via_type_metadata():
    src = (
        "class Solution:\n"
        "    def max_depth(self, root):\n"
        "        if root is None:\n"
        "            return 0\n"
        "        return 1 + max(self.max_depth(root.left), self.max_depth(root.right))\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1",
         "inputs": [{"name": "root", "value": "[3,9,20,null,null,15,7]", "type": "TreeNode"}],
         "expected_output": "3"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Accepted", r
    assert r["result"] == 3


def test_listnode_array_input_via_type_metadata():
    """problem #7 shape: ListNode[] argument."""
    src = (
        "class Solution:\n"
        "    def merge_k(self, lists):\n"
        "        vals = []\n"
        "        for head in lists:\n"
        "            while head:\n"
        "                vals.append(head.val)\n"
        "                head = head.next\n"
        "        return sorted(vals)\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1",
         "inputs": [{"name": "lists", "value": "[[1,4,5],[1,3,4],[2,6]]", "type": "ListNode[]"}],
         "expected_output": "[1,1,2,3,4,4,5,6]"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Accepted", r
    assert r["result"] == [1, 1, 2, 3, 4, 4, 5, 6]


# ── Deterministic method selection (CR #4) ───────────────────────────────


def test_multiple_public_methods_rejected_without_method_name():
    src = (
        "class Solution:\n"
        "    def alpha(self, n):\n"
        "        return n\n"
        "    def beta(self, n):\n"
        "        return n\n"
    )
    env, _ = _run_flow(src, {"cases": [
        {"case_id": "c1", "inputs": [{"name": "n", "value": "1"}], "expected_output": "1"}
    ]})
    r = env["results"][0]
    assert r["status"] == "Runtime Error"
    assert "multiple public methods" in r["error"]["message"]


def test_method_name_hint_disambiguates():
    src = (
        "class Solution:\n"
        "    def alpha(self, n):\n"
        "        return n * 10\n"
        "    def beta(self, n):\n"
        "        return n * 100\n"
    )
    env, _ = _run_flow(src, {
        "method_name": "beta",
        "cases": [
            {"case_id": "c1", "inputs": [{"name": "n", "value": "3"}], "expected_output": "300"}
        ],
    })
    r = env["results"][0]
    assert r["status"] == "Accepted", r
    assert r["result"] == 300
