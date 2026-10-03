"""Opt-in gating for the DAV-58 boundary entry point.

No billed call is made: these tests only prove the entry refuses to run without an
explicit opt-in and an authorised model alias, before any request.
"""

from __future__ import annotations

import asyncio
import importlib.util
from dataclasses import replace
import json
import sqlite3

import httpx
import pytest
import authorized_budget_period as period
import model_budget as accounting
from deepseek_model import DeepseekModel, ModelBudgetExceeded
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

    assert asyncio.run(e2e_boundary_evaluation.main(period.PeriodIdentity("test", accounting.authorized_period_config_sha256(), "a" * 32))) == 1
    assert "reason=model_not_authorized" in capsys.readouterr().out


def test_entry_refuses_a_mismatched_alias(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_BOUNDARY_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-chat")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    assert asyncio.run(e2e_boundary_evaluation.main(period.PeriodIdentity("test", accounting.authorized_period_config_sha256(), "a" * 32))) == 1
    assert "reason=model_not_authorized" in capsys.readouterr().out


def test_entry_refuses_a_call_budget_below_the_plan(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_BOUNDARY_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", "2")

    assert asyncio.run(e2e_boundary_evaluation.main()) == 1
    assert "reason=call_budget_below_plan" in capsys.readouterr().out


@pytest.fixture
def isolated_slot(tmp_path, monkeypatch):
    root = tmp_path / "slot"
    root.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: root)
    monkeypatch.setenv("ULTICODE_BOUNDARY_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "dummy-mock-token")
    return root


def prepare_binding(root, *, active=True):
    identity = period.prepare_period(root / "period", "test", accounting.authorized_period_config_sha256()).identity
    budget = accounting.ModelBudget.bind_prepared(identity)
    if active:
        budget.activate()
    return identity, budget


@pytest.mark.parametrize("state", ["missing_identity", "missing_binding", "wrong_identity", "prepared", "halted"])
def test_entry_invalid_binding_constructs_no_http_adapter(isolated_slot, monkeypatch, capsys, state):
    constructed = []
    def forbidden(*args, **kwargs):
        constructed.append(True)
        raise AssertionError("adapter must not be constructed")
    monkeypatch.setattr(e2e_boundary_evaluation, "DeepseekModel", forbidden)
    if state == "missing_identity":
        expected = None
    elif state == "missing_binding":
        expected = period.prepare_period(isolated_slot / "period", "test", accounting.authorized_period_config_sha256()).identity
    else:
        expected, budget = prepare_binding(isolated_slot, active=state != "prepared")
        if state == "wrong_identity":
            expected = replace(expected, identity="b" * 32)
        if state == "halted":
            budget.halt()
    assert asyncio.run(e2e_boundary_evaluation.main(expected)) == 1
    assert constructed == []
    assert "FAIL reason=" in capsys.readouterr().out


@pytest.mark.parametrize("setting,value", [("DEEPSEEK_MAX_ROUNDS", "5"), ("DEEPSEEK_MAX_TOKENS", "2001"), ("DEEPSEEK_MAX_PROMPT_TOKENS", "24001")])
def test_entry_caps_fail_before_adapter(isolated_slot, monkeypatch, capsys, setting, value):
    identity, budget = prepare_binding(isolated_slot)
    monkeypatch.setenv(setting, value)
    monkeypatch.setattr(e2e_boundary_evaluation, "DeepseekModel", lambda *args, **kwargs: pytest.fail("unexpected adapter"))
    assert asyncio.run(e2e_boundary_evaluation.main(identity)) == 1
    assert "reason=authorized_caps_exceeded" in capsys.readouterr().out
    assert budget.snapshot()["attempts"] == 0


def test_cli_only_accepts_explicit_three_identity_fields():
    identity = e2e_boundary_evaluation._parse_identity(["--period-id", "test", "--period-identity", "a" * 32, "--config-sha256", accounting.authorized_period_config_sha256()])
    assert identity.policy_id == period.POLICY_ID
    with pytest.raises(SystemExit):
        e2e_boundary_evaluation._parse_identity(["--period-id", "test", "--period-id", "other", "--period-identity", "a" * 32, "--config-sha256", identity.config_sha256])
    with pytest.raises(SystemExit):
        e2e_boundary_evaluation._parse_identity([])
    with pytest.raises(SystemExit):
        e2e_boundary_evaluation._parse_identity(["--ledger-path", "/tmp/not-accepted"] )


@pytest.mark.parametrize("failure_lane", [None, "loop", "judge"])
def test_runner_bound_receipts_and_budget_failure_stop_both_adapters(isolated_slot, tmp_path, monkeypatch, failure_lane):
    identity, budget = prepare_binding(isolated_slot)
    artifact = tmp_path / "result.json"
    requests, purposes = [], []
    async def handler(request):
        requests.append(request)
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"answer":"ok"}'}}]})
    def adapter(*args, **kwargs):
        purposes.append(kwargs["budget_purpose"])
        assert kwargs["max_tokens"] <= 2000
        assert kwargs["max_prompt_tokens"] <= 24000
        assert kwargs["max_calls"] == (24 if kwargs["budget_purpose"] == "dav58_loop" else 42)
        return DeepseekModel(*args, transport=httpx.MockTransport(handler), **kwargs)
    monkeypatch.setattr(e2e_boundary_evaluation, "DeepseekModel", adapter)
    monkeypatch.setattr(e2e_boundary_evaluation, "_artifact_path", lambda: artifact)
    monkeypatch.setattr(e2e_boundary_evaluation, "_repository_provenance", lambda: {"git_sha": "a" * 40, "clean": True, "source_sha256": {"src/boundary_evaluation.py": "b" * 64}})
    original = accounting.ModelBudget._commit
    def fail_after_commit(self, db, locked):
        original(self, db, locked)
        raise sqlite3.OperationalError("dummy lost commit acknowledgement")
    async def evaluate(cases, *, model, judge_model, **kwargs):
        records = []
        for index, case in enumerate(cases):
            try:
                if index == 0 and failure_lane == "loop":
                    monkeypatch.setattr(accounting.ModelBudget, "_commit", fail_after_commit)
                await model.decide([{"role": "user", "content": "synthetic"}])
                if index == 0 and failure_lane == "judge":
                    monkeypatch.setattr(accounting.ModelBudget, "_commit", fail_after_commit)
                await judge_model.decide([{"role": "user", "content": "synthetic judge"}])
                records.append({"failure_handling": {"error": None}})
            except ModelBudgetExceeded:
                records.extend({"failure_handling": {"error": "ModelBudgetExceeded" if rest == index else "not_run"}} for rest in range(index, len(cases)))
                break
        return tuple(records)
    async def probe(judge, documents):
        await judge.decide([{"role": "user", "content": "synthetic negative control"}])
        return {"gate_rejected": True}
    monkeypatch.setattr(e2e_boundary_evaluation, "evaluate_boundary_cases", evaluate)
    monkeypatch.setattr(e2e_boundary_evaluation, "unsupported_claim_probe", probe)
    monkeypatch.setattr(e2e_boundary_evaluation, "forged_citation_probe", lambda documents: {"gate_rejected": True})
    monkeypatch.setattr(e2e_boundary_evaluation, "summarize_boundary", lambda records: {"cases": len(records), "behavior_met": len(records), "behavior_failed": 0, "errors": int(failure_lane is not None), "citation_exists": {"failed": 0}, "citation_supports": {"unsupported": 0, "failed": 0}, "citation_derivable": {"not_derivable": 0, "failed": 0}})
    assert asyncio.run(e2e_boundary_evaluation.main(identity)) == int(failure_lane is not None)
    monkeypatch.setattr(accounting.ModelBudget, "_commit", original)
    assert purposes == ["dav58_loop", "dav58_judge"]
    assert len(requests) == (13 if failure_lane is None else int(failure_lane == "judge"))
    saved = json.loads(artifact.read_text())
    authorization = saved["authorized_period"]
    assert authorization["identity"]["identity"] == identity.identity
    assert authorization["identity"]["config_sha256"] == identity.config_sha256
    assert authorization["before"]["attempts"] == 0
    assert authorization["after"]["attempts"] == (13 if failure_lane is None else (1 if failure_lane == "loop" else 2))
    assert all(row["purpose"] in ("dav58_loop", "dav58_judge") for row in authorization["receipts"])
    assert authorization["after"]["legacy_history"] == "UNKNOWN"
    assert authorization["after"]["runtime_accounting_connected"] is False
    assert "dummy-mock-token" not in artifact.read_text()
    assert "Authorization" not in artifact.read_text()
