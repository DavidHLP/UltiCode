from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, replace
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
from threading import Event, current_thread

import pytest

import authorized_budget_period as period
import model_budget as accounting
from model_budget import BudgetLimitExceeded, ModelBudget, authorized_period_config_sha256


@pytest.fixture
def slot(tmp_path, monkeypatch):
    root = tmp_path / "authorization"
    root.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: root)
    return root


def prepared(slot):
    return period.prepare_period(slot / "period", "authorized-test", authorized_period_config_sha256()).identity


def bound(slot, active=True):
    identity = prepared(slot)
    budget = ModelBudget.bind_prepared(identity)
    if active:
        budget.activate()
    return identity, budget


def reserve(budget, purpose="dav58_loop"):
    return budget.reserve(1, 1, purpose=purpose)


def run_process(slot, identity, body, *, wait=True):
    script = """import json,sys
from pathlib import Path
import model_budget as accounting
from model_budget import ModelBudget, BudgetLimitExceeded
from authorized_budget_period import PeriodIdentity
slot = Path(sys.argv[1])
accounting._authorization_slot = lambda: slot
identity = PeriodIdentity(**json.loads(sys.argv[2]))
budget = ModelBudget.bound(identity)
""" + body
    env = {"PATH": os.defpath, "HOME": str(slot), "XDG_STATE_HOME": str(slot / "ignored"),
           "PYTHONDONTWRITEBYTECODE": "1",
           "PYTHONPATH": str(Path(__file__).resolve().parents[1] / "src")}
    process = subprocess.Popen([sys.executable, "-c", script, str(slot), json.dumps(asdict(identity))],
                               env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if not wait:
        return process
    stdout, stderr = process.communicate(timeout=10)
    assert process.returncode == 0, stderr
    return json.loads(stdout)


def test_explicit_binding_read_and_full_cap_receipt(slot):
    identity, budget = bound(slot, active=False)
    assert budget.snapshot()["attempts"] == 0
    assert budget.snapshot()["legacy_history"] == "UNKNOWN"
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget)
    budget.activate()
    receipt = reserve(budget)
    assert receipt.reserved_micro_usd == accounting.worst_case_micro_usd(24_000, 2000) == 9600
    assert receipt.period_identity == identity.identity
    assert receipt.config_sha256 == identity.config_sha256
    assert receipt.purpose == "dav58_loop"
    assert budget.snapshot()["attempts"] == 1
    assert period.read_period(slot / "period", identity).runtime_accounting_connected is False
    assert budget.snapshot()["runtime_accounting_connected"] is True
    for purpose in ("ordinary", "frozen_evaluation", "other"):
        with pytest.raises(ValueError):
            reserve(budget, purpose)
    with pytest.raises(ValueError):
        budget.begin_evaluation_group(1)
    with pytest.raises(ValueError):
        budget.reserve(1, 1001, purpose="dav53_scenarios")
    assert budget.snapshot()["attempts"] == 1


def test_restart_and_two_processes_share_lane_and_global_allowance(slot):
    identity, budget = bound(slot)
    body = """accepted = 0
for _ in range(30):
    try:
        budget.reserve(1, 1, purpose='dav58_judge')
        accepted += 1
    except BudgetLimitExceeded:
        pass
print(json.dumps({'accepted': accepted, 'snapshot': budget.snapshot()}))
"""
    processes = [run_process(slot, identity, body, wait=False) for _ in range(2)]
    totals = []
    for process in processes:
        stdout, stderr = process.communicate(timeout=10)
        assert process.returncode == 0, stderr
        totals.append(json.loads(stdout)["accepted"])
    assert sum(totals) == 42
    for purpose, count in (("dav58_loop", 24), ("dav53_scenarios", 12)):
        for _ in range(count):
            reserve(budget, purpose)
    snapshot = run_process(slot, identity, "print(json.dumps(budget.snapshot()))")
    assert snapshot["attempts"] == 78
    assert snapshot["reserved_micro_usd"] == 734400
    assert snapshot["legacy_history"] == "UNKNOWN"
    with pytest.raises(BudgetLimitExceeded):
        reserve(ModelBudget.bound(identity))
    with pytest.raises(FileExistsError):
        ModelBudget.bind_prepared(identity)
    assert budget.snapshot()["attempts"] == 78


@pytest.mark.parametrize("purposes", [("dav58_loop", "dav58_loop"), ("dav58_loop", "dav58_judge")])
def test_last_attempt_race(slot, purposes):
    identity, budget = bound(slot)
    for purpose, count in (("dav58_loop", 23), ("dav58_judge", 42), ("dav53_scenarios", 12)):
        for _ in range(count):
            reserve(budget, purpose)
    def attempt(purpose):
        try:
            reserve(ModelBudget.bound(identity), purpose)
            return True
        except BudgetLimitExceeded:
            return False
    with ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = list(pool.map(attempt, purposes))
    assert outcomes.count(True) == 1
    assert budget.snapshot()["attempts"] == 78


def test_cost_gate_race_charges_only_one_full_ceiling(slot):
    identity, budget = bound(slot)
    # Exercise the cost boundary independently of the tighter natural lane caps.
    with sqlite3.connect(budget.path) as db:
        db.execute("UPDATE budget SET reserved_micro_usd=990400 WHERE singleton=1")
    def attempt(_):
        try:
            return reserve(ModelBudget.bound(identity)).reserved_micro_usd
        except BudgetLimitExceeded:
            return 0
    with ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = list(pool.map(attempt, range(2)))
    assert sum(outcomes) == 9600
    assert budget.snapshot()["attempts"] == 1
    assert budget.snapshot()["reserved_micro_usd"] == 1_000_000


@pytest.mark.parametrize("first", ["reserve", "halt"])
def test_lifecycle_lock_held_until_sql_commit(slot, monkeypatch, first):
    identity, budget = bound(slot)
    entered, release, second_started = Event(), Event(), Event()
    original = ModelBudget._commit
    def pause(self, db, locked):
        if current_thread().name.endswith("_0"):
            entered.set()
            assert release.wait(5)
        original(self, db, locked)
    monkeypatch.setattr(ModelBudget, "_commit", pause)
    def first_operation():
        return reserve(budget) if first == "reserve" else budget.halt()
    def second_operation():
        second_started.set()
        return budget.halt() if first == "reserve" else reserve(budget)
    with ThreadPoolExecutor(max_workers=2) as pool:
        primary = pool.submit(first_operation)
        assert entered.wait(5)
        secondary = pool.submit(second_operation)
        assert second_started.wait(5)
        assert not secondary.done()
        release.set()
        receipt = primary.result(timeout=5)
        if first == "halt":
            with pytest.raises(BudgetLimitExceeded):
                secondary.result(timeout=5)
        else:
            secondary.result(timeout=5)
            assert budget.settle(receipt, None) is None
    snapshot = budget.snapshot()
    assert snapshot["sql_gate"] == snapshot["state"] == "halted"
    assert snapshot["attempts"] == (1 if first == "reserve" else 0)


def test_canonical_config_second_directory_and_env_cannot_fork(slot, tmp_path, monkeypatch):
    identity, budget = bound(slot)
    other = period.prepare_period(tmp_path / "other-period", identity.period_id, identity.config_sha256).identity
    for operation in (ModelBudget.bound, ModelBudget.bind_prepared):
        with pytest.raises(period.PeriodError):
            operation(other)
        with pytest.raises(period.PeriodError):
            operation(replace(identity, config_sha256="a" * 64))
    monkeypatch.setenv("HOME", str(tmp_path / "fake-home"))
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path / "fake-state"))
    assert ModelBudget.bound(identity).path == budget.path
    config = accounting.authorized_period_config()
    assert config["model_alias"] == "deepseek-flash"
    assert config["pricing"]["input_micro_usd_tenths_per_token"] == 3
    assert config["pricing"]["output_micro_usd_tenths_per_token"] == 12


@pytest.mark.parametrize("missing", ["ledger", "anchor", "budget", "purpose", "binding", "attempt"])
def test_missing_binding_parts_never_create_or_repair(slot, missing):
    identity, budget = bound(slot)
    if missing == "attempt":
        receipt = reserve(budget)
    if missing == "ledger":
        budget.path.unlink()
    elif missing == "anchor":
        (budget.path.parent / "binding.json").unlink()
    else:
        table = {"purpose": "purposes", "attempt": "attempts"}.get(missing, missing)
        with sqlite3.connect(budget.path) as db:
            db.execute(f"DELETE FROM {table}")
    before = {p.name: p.read_bytes() for p in budget.path.parent.iterdir()}
    operations = (lambda: budget.settle(receipt, None),) if missing == "attempt" else (lambda: ModelBudget.bound(identity), budget.snapshot, lambda: reserve(budget))
    for operation in operations:
        with pytest.raises((period.PeriodError, OSError, sqlite3.Error, RuntimeError)):
            operation()
    assert before == {p.name: p.read_bytes() for p in budget.path.parent.iterdir()}


@pytest.mark.parametrize("replace_part", ["ledger", "anchor", "purpose"])
def test_binding_replacement_or_drift_denies_reserve(slot, replace_part):
    identity, budget = bound(slot)
    if replace_part == "ledger":
        copy = budget.path.with_name("replacement")
        copy.write_bytes(budget.path.read_bytes())
        copy.replace(budget.path)
    elif replace_part == "anchor":
        marker = budget.path.parent / "binding.json"
        data = json.loads(marker.read_text())
        data["config"]["model_alias"] = "other"
        marker.write_text(json.dumps(data) + "\n")
    else:
        with sqlite3.connect(budget.path) as db:
            db.execute("UPDATE purposes SET attempt_limit=999 WHERE purpose='dav58_loop'")
    with pytest.raises(period.PeriodError):
        reserve(budget)


def test_partial_bind_retains_tombstone(slot, monkeypatch):
    identity = prepared(slot)
    original = period._write
    def fail(fd, value):
        if "ledger_uuid" in value:
            raise OSError("injected binding write failure")
        original(fd, value)
    monkeypatch.setattr(period, "_write", fail)
    with pytest.raises(OSError):
        ModelBudget.bind_prepared(identity)
    assert (slot / "accounting").is_dir()
    with pytest.raises(FileExistsError):
        ModelBudget.bind_prepared(identity)
    with pytest.raises(OSError):
        ModelBudget.bound(identity)
    assert period.read_period(slot / "period", identity).state == "prepared"


@pytest.mark.parametrize("stage", ["before_append", "after_append", "sql_commit"])
def test_activation_failure_readback_and_explicit_retry(slot, monkeypatch, stage):
    identity, budget = bound(slot, active=False)
    if stage == "before_append":
        original = period._LockedPeriod.confirm
        monkeypatch.setattr(period._LockedPeriod, "confirm", lambda self: (_ for _ in ()).throw(OSError("fsync failure")))
        restore = lambda: monkeypatch.setattr(period._LockedPeriod, "confirm", original)
    elif stage == "after_append":
        original = period._write
        def write_then_fail(fd, value):
            original(fd, value)
            raise OSError("fsync acknowledgement failure after complete append")
        monkeypatch.setattr(period, "_write", write_then_fail)
        restore = lambda: monkeypatch.setattr(period, "_write", original)
    else:
        original = ModelBudget._commit
        monkeypatch.setattr(ModelBudget, "_commit", lambda *args: (_ for _ in ()).throw(sqlite3.OperationalError("commit failure")))
        restore = lambda: monkeypatch.setattr(ModelBudget, "_commit", original)
    with pytest.raises((period.PeriodError, sqlite3.Error)):
        budget.activate()
    restore()
    snapshot = ModelBudget.bound(identity).snapshot()
    assert snapshot["state"] == ("prepared" if stage == "before_append" else "active")
    assert snapshot["sql_gate"] == "prepared"
    assert snapshot["attempts"] == 0
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget)
    budget.activate()
    budget.activate()  # Same completed operation confirms durability without reset.
    assert reserve(budget).reserved_micro_usd == 9600


def test_halt_append_failure_stays_closed_and_explicit_retry_finishes(slot, monkeypatch):
    identity, budget = bound(slot)
    receipt = reserve(budget)
    original = period._write
    monkeypatch.setattr(period, "_write", lambda *args: (_ for _ in ()).throw(OSError("append failure")))
    with pytest.raises(period.PeriodError):
        budget.halt()
    monkeypatch.setattr(period, "_write", original)
    snapshot = budget.snapshot()
    assert (snapshot["state"], snapshot["sql_gate"]) == ("active", "halted")
    assert snapshot["attempts"] == 1
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget)
    budget.halt()
    budget.halt()
    assert budget.settle(receipt, {"prompt_tokens": 1, "completion_tokens": 1}) == 2
    assert budget.snapshot()["reserved_micro_usd"] == 9600


def test_receipt_scope_duplicate_unknown_and_overbound_halt(slot):
    identity, budget = bound(slot)
    receipt = reserve(budget)
    for forged in (replace(receipt, reserved_micro_usd=1), replace(receipt, purpose="dav58_judge"),
                   replace(receipt, period_identity="b" * 32), replace(receipt, config_sha256="a" * 64)):
        with pytest.raises(period.PeriodError):
            budget.settle(forged, None)
    assert budget.settle(receipt, None) is None
    with pytest.raises(RuntimeError):
        budget.settle(receipt, None)
    second = reserve(budget, "dav53_scenarios")
    with pytest.raises(BudgetLimitExceeded):
        budget.settle(second, {"prompt_tokens": 24_001, "completion_tokens": 1001})
    snapshot = budget.snapshot()
    assert snapshot["sql_gate"] == snapshot["state"] == "halted"
    assert snapshot["reserved_micro_usd"] >= receipt.reserved_micro_usd + second.reserved_micro_usd
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget)
    with pytest.raises(RuntimeError):
        budget.settle(second, None)


def test_legacy_mode_cannot_open_bound_ledger(slot):
    identity, budget = bound(slot, active=False)
    for path in (budget.path, budget.path.parent / ".." / "accounting" / "budget.sqlite3"):
        legacy = ModelBudget(path)
        with pytest.raises(period.PeriodError):
            legacy.snapshot()
        with pytest.raises(period.PeriodError):
            legacy.reserve(1, 1)
    assert budget.snapshot()["attempts"] == 0
    assert budget.snapshot()["sql_gate"] == "prepared"
