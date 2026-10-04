"""Real runner + loop + both adapters + shared guard, with mock HTTP only."""
import asyncio
import importlib.util
import json
import sys
from pathlib import Path

import httpx
import pytest
import model_budget as accounting
import authorized_budget_period as period
from dav58_live_guard import GuardedTransport
from test_boundary_evaluation import _all_met_script

sys.path.insert(0, str(Path(__file__).parents[1]))
import e2e_guarded_boundary_evaluation as entry


@pytest.mark.parametrize("failure_lane", [None, "dav58_loop", "dav58_judge"])
def test_complete_runner_shared_guard_and_unknown_usage_stop(tmp_path, monkeypatch, failure_lane):
    slot = tmp_path / "slot"
    slot.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: slot)
    monkeypatch.setattr(entry, "_authorization_slot", lambda: slot)
    identity = period.prepare_period(slot / "period", "offline", accounting.authorized_period_config_sha256()).identity
    budget = accounting.ModelBudget.bind_prepared(identity)
    budget.activate()  # ONLY this temporary test period
    monkeypatch.setenv("ULTICODE_BOUNDARY_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "dummy-mock-token")
    artifact = tmp_path / "result.json"
    monkeypatch.setattr(entry.runner, "_artifact_path", lambda: artifact)
    monkeypatch.setattr(entry.runner, "_repository_provenance", lambda: {"git_sha": "a" * 40, "clean": True, "source_sha256": {"src/boundary_evaluation.py": "b" * 64}})
    script, positions, requests = _all_met_script(), {}, []
    def transport(guard, lane):
        def handler(request):
            requests.append(lane)
            payload = json.loads(request.content)
            user = payload["messages"][1]["content"]
            if lane == "dav58_judge":
                answer = {"supports": "42" not in user, "derivable": False}
                content = json.dumps({"answer": json.dumps(answer)})
            else:
                marker = next(marker for marker in script if marker in user)
                index = positions.get(marker, 0)
                positions[marker] = index + 1
                decision = script[marker][min(index, len(script[marker]) - 1)]
                outer = {"answer": decision.text} if decision.tool_call is None else {"tool": decision.tool_call.name, "args": decision.tool_call.arguments}
                content = json.dumps(outer)
            usage = None if lane == failure_lane else {"prompt_tokens": 100, "completion_tokens": 20, "total_tokens": 120}
            return httpx.Response(200, json={"model": "deepseek-v4.1-flash", "usage": usage,
                "choices": [{"finish_reason": "stop", "message": {"content": content}}]})
        return GuardedTransport(guard, lane, httpx.MockTransport(handler))
    monkeypatch.setattr(entry, "GuardedTransport", transport)
    assert asyncio.run(entry.run(identity)) == int(failure_lane is not None)
    saved = json.loads(artifact.read_text())
    journal = json.loads(entry.journal_path(identity).read_text())
    assert saved["run"]["repository"]["execution_entry"] == "e2e_guarded_boundary_evaluation.py"
    assert set(saved["run"]["repository"]["source_sha256"]) == {"src/boundary_evaluation.py", "e2e_guarded_boundary_evaluation.py", "src/dav58_live_guard.py"}
    assert journal["period_identity"] == identity.identity
    assert len(journal["receipts"]) == len(requests)
    assert "dummy-mock-token" not in artifact.read_text() + entry.journal_path(identity).read_text()
    if failure_lane is None:
        assert saved["summary"]["behavior_met"] == 6
        assert all(p["gate_rejected"] for p in saved["probes"])
        assert requests.count("dav58_loop") == 9
        assert requests.count("dav58_judge") == 2  # citation + paid negative probe
        assert journal["settled_peak_micro_usd"] == 594
        assert saved["budget"]["committed_micro_usd"] == 105600
    else:
        assert journal["halted"] is True
        assert journal["pending_micro_usd"] == 786432
        assert saved["probes"][1]["error"] == "not_run_budget"
        assert requests[-1] == failure_lane
    import hashlib
    resume_sha = hashlib.sha256(entry.journal_path(identity).read_bytes()).hexdigest()
    if failure_lane is None:
        positions.clear()
        monkeypatch.setattr(entry.runner, "_artifact_path", lambda: tmp_path / "resumed-result.json")
        prior_receipts = journal["receipts"]
        assert asyncio.run(entry.run(identity, resume_sha256=resume_sha)) == 0
        resumed = json.loads(entry.journal_path(identity).read_text())
        assert resumed["receipts"][:len(prior_receipts)] == prior_receipts
        assert resumed["settled_peak_micro_usd"] == 1188
        assert budget.snapshot()["committed_micro_usd"] == 211200
    else:
        prior_requests = list(requests)
        with pytest.raises(ValueError):
            asyncio.run(entry.run(identity, resume_sha256=resume_sha))
        assert requests == prior_requests
    previous = list(requests)
    with pytest.raises(FileExistsError): asyncio.run(entry.run(identity))
    assert requests == previous  # fixed journal refuses reset/replay


def test_prepared_period_constructs_no_guard(tmp_path, monkeypatch):
    slot = tmp_path / "slot"
    slot.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: slot)
    monkeypatch.setattr(entry, "_authorization_slot", lambda: slot)
    identity = period.prepare_period(slot / "period", "offline", accounting.authorized_period_config_sha256()).identity
    accounting.ModelBudget.bind_prepared(identity)
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "dummy-mock-token")
    monkeypatch.setattr(entry.runner, "_repository_provenance", lambda: {"source_sha256": {}})
    with pytest.raises(accounting.BudgetLimitExceeded): asyncio.run(entry.run(identity))
    assert not entry.journal_path(identity).exists()


@pytest.mark.parametrize("model,key", [("deepseek-v4-pro", "dummy"), ("deepseek-flash", ""), (None, "dummy")])
def test_invalid_configuration_does_not_claim_journal(tmp_path, monkeypatch, model, key):
    slot = tmp_path / "slot"
    slot.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: slot)
    monkeypatch.setattr(entry, "_authorization_slot", lambda: slot)
    identity = period.prepare_period(slot / "period", "offline", accounting.authorized_period_config_sha256()).identity
    budget = accounting.ModelBudget.bind_prepared(identity)
    budget.activate()
    monkeypatch.setattr(entry.runner, "_repository_provenance", lambda: {"source_sha256": {}})
    if model is None: monkeypatch.delenv("DEEPSEEK_MODEL", raising=False)
    else: monkeypatch.setenv("DEEPSEEK_MODEL", model)
    monkeypatch.setenv("DEEPSEEK_API_KEY", key)
    with pytest.raises(ValueError): asyncio.run(entry.run(identity))
    assert not entry.journal_path(identity).exists()
