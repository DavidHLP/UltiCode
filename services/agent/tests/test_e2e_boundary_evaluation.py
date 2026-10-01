"""Opt-in gating for the DAV-58 boundary entry point.

No billed call is made: these tests only prove the entry refuses to run without an
explicit opt-in and an authorised model alias, before any request.
"""

from __future__ import annotations

import asyncio
import importlib.util
from pathlib import Path

_module_spec = importlib.util.spec_from_file_location(
    "e2e_boundary_evaluation",
    Path(__file__).parents[1] / "e2e_boundary_evaluation.py",
)
assert _module_spec and _module_spec.loader
e2e_boundary_evaluation = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_boundary_evaluation)


def test_script_is_opt_in(monkeypatch, capsys) -> None:
    monkeypatch.delenv("ULTICODE_BOUNDARY_EVAL", raising=False)

    assert asyncio.run(e2e_boundary_evaluation.main()) == 0
    assert "reason=opt_in_not_set" in capsys.readouterr().out


def test_entry_refuses_an_unauthorised_model(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_BOUNDARY_EVAL", "1")
    monkeypatch.delenv("DEEPSEEK_MODEL", raising=False)

    assert asyncio.run(e2e_boundary_evaluation.main()) == 1
    assert "reason=model_not_authorized" in capsys.readouterr().out


def test_entry_refuses_a_mismatched_alias(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_BOUNDARY_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-chat")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    assert asyncio.run(e2e_boundary_evaluation.main()) == 1
    assert "reason=model_not_authorized" in capsys.readouterr().out


def test_entry_refuses_a_call_budget_below_the_plan(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_BOUNDARY_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", "2")

    assert asyncio.run(e2e_boundary_evaluation.main()) == 1
    assert "reason=call_budget_below_plan" in capsys.readouterr().out
