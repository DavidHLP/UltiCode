"""Device recovery preserves accounting and rejects replaced or unresolved objects."""
import json
import sqlite3

import pytest
import authorized_budget_period as period
import model_budget as accounting
import manual_budget_rebinding as recovery
import agent_service.runtime_authorization as runtime_authorization
from dav58_live_guard import IncrementalGuard


@pytest.fixture
def manual_budget(tmp_path, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    slot = tmp_path / "manual"
    slot.mkdir(mode=0o700)
    monkeypatch.setattr(accounting, "authorization_slot", lambda _identity: slot)
    monkeypatch.setattr(runtime_authorization, "authorization_slot", lambda _identity: slot)
    monkeypatch.setattr(recovery, "filesystem_identity", lambda _slot: {"uuid": "offline", "fsroot": "/", "fstype": "test"})
    config = accounting.authorized_period_config_sha256(period.MANUAL_LIVE_TEST_POLICY_ID)
    identity = period.prepare_period(slot / "period", period.MANUAL_LIVE_TEST_PERIOD_ID,
        config, policy_id=period.MANUAL_LIVE_TEST_POLICY_ID).identity
    budget = accounting.ModelBudget.bind_prepared(identity)
    budget.activate()
    guard = IncrementalGuard(slot / "accounting" / f"dav58-increment-{identity.identity}.json",
        period_identity=identity.identity, config_sha256=config, budget=budget)
    guard.close()
    for path, keys in [(slot / "period/identity.json", ("directory", "state_file")),
                       (slot / "accounting/binding.json", ("directory", "ledger"))]:
        data = json.loads(path.read_text())
        for key in keys:
            data[key][0] += 1
        path.write_text(accounting._json(data) + "\n")
        if path.name == "binding.json":
            with sqlite3.connect(budget.path) as db:
                db.execute("UPDATE binding SET payload=?", (accounting._json(data),))
    return slot, identity, budget


def test_explicit_rebinding_preserves_originals_and_budget(manual_budget):
    slot, identity, budget = manual_budget
    originals = {p: (slot / p).read_bytes() for p in ("period/identity.json", "accounting/binding.json", "accounting/budget.sqlite3")}
    assert recovery.rebind_manual_budget(slot) == {"attempts": 0, "actual_micro_usd": 0}
    assert all((slot / p).read_bytes() == raw for p, raw in originals.items())
    assert accounting.ModelBudget.bound(identity).snapshot()["attempts"] == 0
    reservation = budget.reserve(100, 100, purpose="u03_analysis")
    budget.settle(reservation, {"prompt_tokens": 100, "completion_tokens": 10})
    assert budget.snapshot()["attempts"] == 1


@pytest.mark.parametrize("fault", ["replace", "unknown", "filesystem"])
def test_rebinding_fails_closed(manual_budget, monkeypatch, fault):
    slot, identity, budget = manual_budget
    if fault == "replace":
        path = slot / "period/state.jsonl"
        replacement = path.with_suffix(".new")
        replacement.write_bytes(path.read_bytes())
        replacement.chmod(0o600)
        replacement.replace(path)
    elif fault == "unknown":
        with sqlite3.connect(budget.path) as db:
            db.execute("UPDATE budget SET halted=1")
    else:
        recovery.rebind_manual_budget(slot)
        monkeypatch.setattr(recovery, "filesystem_identity", lambda _slot: {"uuid": "other"})
    with pytest.raises((ValueError, period.PeriodError)):
        recovery.rebind_manual_budget(slot)
    if fault != "filesystem":
        assert not (slot / recovery.EVENT).exists()


def test_interrupted_event_write_does_not_block_retry(manual_budget, monkeypatch):
    slot, _, _ = manual_budget
    original_dump = recovery.json.dump
    def interrupted_dump(_event, stream):
        stream.write("{")
        raise OSError("interrupted write")
    monkeypatch.setattr(recovery.json, "dump", interrupted_dump)
    with pytest.raises(OSError, match="interrupted write"):
        recovery.rebind_manual_budget(slot)
    assert not (slot / recovery.EVENT).exists()
    monkeypatch.setattr(recovery.json, "dump", original_dump)
    assert recovery.rebind_manual_budget(slot)["attempts"] == 0
