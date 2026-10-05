from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, replace
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
from threading import Event, current_thread
import selectors

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


def settled_reserve(budget, purpose="dav58_loop"):
    receipt = reserve(budget, purpose)
    budget.settle(receipt, {"prompt_tokens": 100, "completion_tokens": 20})
    return receipt


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
           "PYTHONPATH": os.pathsep.join((str(Path(accounting.__file__).parent), str(Path(period.__file__).parent)))}
    process = subprocess.Popen([sys.executable, "-c", script, str(slot), json.dumps(asdict(identity))],
                               env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
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
    assert budget.snapshot()["runtime_accounting_connected"] is False
    assert budget.snapshot()["spend_limit_enforced"] is False
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
    process = run_process(slot, identity, """from dataclasses import asdict
receipt = budget.reserve(1, 1, purpose='dav58_judge')
print(json.dumps(asdict(receipt)), flush=True)
assert sys.stdin.readline().strip() == 'settle'
budget.settle(receipt, {'prompt_tokens': 100, 'completion_tokens': 20})
print(json.dumps(budget.snapshot()))
""", wait=False)
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            assert selector.select(timeout=5), "reservation did not finish within bound"
        receipt = json.loads(process.stdout.readline())
        assert receipt["period_identity"] == identity.identity
        assert receipt["config_sha256"] == identity.config_sha256
        assert receipt["purpose"] == "dav58_judge"
        before = budget.snapshot()
        assert before["attempts"] == 1
        assert before["unsettled_attempts"] == 1
        assert before["reserved_micro_usd"] == receipt["reserved_micro_usd"] == 9600
        files = {p.name: p.read_bytes() for p in budget.path.parent.iterdir()}
        rejected = run_process(slot, identity, """try:
    budget.reserve(1, 1, purpose='dav58_judge')
except BudgetLimitExceeded:
    print(json.dumps(budget.snapshot()))
else:
    raise AssertionError('unsettled reservation accepted')
""")
        assert rejected == before == budget.snapshot()
        assert files == {p.name: p.read_bytes() for p in budget.path.parent.iterdir()}
        assert process.poll() is None
        stdout, stderr = process.communicate(input="settle\n", timeout=10)
        assert process.returncode == 0, stderr
        settled = json.loads(stdout)
        assert settled["attempts"] == 1
        assert settled["unsettled_attempts"] == settled["unknown_usage_attempts"] == 0
        assert settled["reserved_micro_usd"] == 9600
        assert settled["actual_micro_usd"] > 0
    finally:
        if process.poll() is None:
            process.kill()
        process.communicate(timeout=5)
    restarted = run_process(slot, identity, """accepted = 0
for _ in range(41):
    receipt = budget.reserve(1, 1, purpose='dav58_judge')
    budget.settle(receipt, {'prompt_tokens': 100, 'completion_tokens': 20})
    accepted += 1
try:
    budget.reserve(1, 1, purpose='dav58_judge')
except BudgetLimitExceeded:
    pass
else:
    raise AssertionError('shared judge allowance exceeded')
print(json.dumps({'accepted': accepted, 'snapshot': budget.snapshot()}))
""")
    assert restarted["accepted"] + settled["attempts"] == 42
    assert restarted["snapshot"]["attempts"] == 42
    assert restarted["snapshot"]["reserved_micro_usd"] == 403200
    assert restarted["snapshot"]["actual_micro_usd"] == 42 * settled["actual_micro_usd"]
    for purpose, count in (("dav58_loop", 24), ("dav53_scenarios", 12)):
        for _ in range(count):
            settled_reserve(budget, purpose)
    snapshot = run_process(slot, identity, "print(json.dumps(budget.snapshot()))")
    assert snapshot["attempts"] == 78
    assert snapshot["reserved_micro_usd"] == 734400
    assert snapshot["actual_micro_usd"] == 78 * settled["actual_micro_usd"]
    assert snapshot["unsettled_attempts"] == snapshot["unknown_usage_attempts"] == 0
    assert snapshot["legacy_history"] == "UNKNOWN"
    with pytest.raises(BudgetLimitExceeded):
        reserve(ModelBudget.bound(identity))
    with pytest.raises(period.PeriodError):
        ModelBudget.bind_prepared(identity)
    assert budget.snapshot()["attempts"] == 78


@pytest.mark.parametrize("purposes", [("dav58_loop", "dav58_loop"), ("dav58_loop", "dav58_judge")])
def test_last_attempt_race(slot, purposes):
    identity, budget = bound(slot)
    for purpose, count in (("dav58_loop", 23), ("dav58_judge", 42), ("dav53_scenarios", 12)):
        for _ in range(count):
            settled_reserve(budget, purpose)
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
    with pytest.raises(period.PeriodError):
        ModelBudget.bind_prepared(identity)
    assert (slot / "accounting").is_dir()
    with pytest.raises(period.PeriodError):
        ModelBudget.bind_prepared(identity)
    with pytest.raises(period.PeriodError):
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


def test_unknown_usage_blocks_every_later_bound_reservation(slot):
    identity, budget = bound(slot)
    receipt = reserve(budget)
    assert budget.settle(receipt, None) is None
    with pytest.raises(RuntimeError):
        budget.settle(receipt, None)
    before = budget.snapshot()
    assert before["unknown_usage_attempts"] == 1
    assert before["unsettled_attempts"] == 0
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget, "dav53_scenarios")
    assert budget.snapshot() == before


def test_unsettled_bound_attempt_blocks_new_reservations(slot):
    _, budget = bound(slot)
    reserve(budget)
    before = budget.snapshot()
    assert before["unsettled_attempts"] == 1
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget, "dav53_scenarios")
    assert budget.snapshot() == before


def test_overbound_usage_halts_bound_budget(slot, monkeypatch):
    other_slot = slot.parent / "overbound-slot"
    other_slot.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: other_slot)
    _, budget = bound(other_slot)
    receipt = reserve(budget, "dav53_scenarios")
    with pytest.raises(BudgetLimitExceeded):
        budget.settle(receipt, {"prompt_tokens": 24_001, "completion_tokens": 1001})
    snapshot = budget.snapshot()
    assert snapshot["sql_gate"] == snapshot["state"] == "halted"
    assert snapshot["reserved_micro_usd"] >= receipt.reserved_micro_usd
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget)
    with pytest.raises(RuntimeError):
        budget.settle(receipt, None)


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


@pytest.mark.parametrize("exception_cleanup", [False, True])
def test_thread_cleanup_preserves_live_sqlite_locks_across_processes(slot, monkeypatch, exception_cleanup):
    identity, budget = bound(slot)
    entered, release = Event(), Event()
    original = ModelBudget._commit
    def pause(self, db, locked, *ledger_fd):
        if current_thread().name.startswith("fd-owner"):
            entered.set()
            assert release.wait(5)
        original(self, db, locked, *ledger_fd)
    monkeypatch.setattr(ModelBudget, "_commit", pause)
    def cleanup():
        with ModelBudget.bound(identity)._accounting() as (db, locked, *unused):
            db.execute("SELECT attempts FROM budget").fetchone()
            if exception_cleanup:
                raise RuntimeError("injected read cleanup failure")
    with ThreadPoolExecutor(max_workers=1, thread_name_prefix="fd-owner") as owner:
        writer = owner.submit(reserve, budget)
        assert entered.wait(5)
        try:
            with ThreadPoolExecutor(max_workers=1) as readers:
                reader = readers.submit(cleanup)
                if exception_cleanup:
                    with pytest.raises(RuntimeError):
                        reader.result(timeout=5)
                else:
                    reader.result(timeout=5)
            body = """import fcntl,os
fd = os.open(budget.path, os.O_RDWR)
try:
    try:
        fcntl.lockf(fd, fcntl.LOCK_EX | fcntl.LOCK_NB, 1, 0x40000001, os.SEEK_SET)
        held = False
    except BlockingIOError:
        held = True
finally:
    os.close(fd)
print('HELD' if held else 'RELEASED', flush=True)
if not held:
    raise RuntimeError('other thread cleanup canceled live SQLite RESERVED lock')
import sqlite3
db = sqlite3.connect(budget.path, timeout=5, isolation_level=None)
db.execute('BEGIN IMMEDIATE')
db.execute('ROLLBACK')
db.close()
print(json.dumps(budget.snapshot()))
"""
            process = run_process(slot, identity, body, wait=False)
            try:
                with selectors.DefaultSelector() as selector:
                    selector.register(process.stdout, selectors.EVENT_READ)
                    assert selector.select(timeout=5), "probe did not finish within bound"
                assert process.stdout.readline().strip() == "HELD"
                assert process.poll() is None
                release.set()
                writer.result(timeout=5)
                stdout, stderr = process.communicate(timeout=10)
                assert process.returncode == 0, stderr
                assert json.loads(stdout)["attempts"] == 1
            finally:
                if process.poll() is None:
                    process.kill()
                process.communicate(timeout=5)
        finally:
            release.set()
        writer.result(timeout=5)
    assert budget.snapshot()["attempts"] == 1


def test_real_commit_acknowledgement_failure_preserves_charged_attempt(slot, monkeypatch):
    identity, budget = bound(slot)
    original = ModelBudget._commit
    def commit_then_fail(self, db, locked):
        original(self, db, locked)
        raise sqlite3.OperationalError("injected lost commit acknowledgement")
    monkeypatch.setattr(ModelBudget, "_commit", commit_then_fail)
    with pytest.raises(sqlite3.OperationalError):
        reserve(budget)
    monkeypatch.setattr(ModelBudget, "_commit", original)
    snapshot = run_process(slot, identity, "print(json.dumps(budget.snapshot()))")
    assert snapshot["attempts"] == 1
    assert snapshot["reserved_micro_usd"] == 9600
    with pytest.raises(period.PeriodError):
        ModelBudget.bind_prepared(identity)
    assert ModelBudget.bound(identity).snapshot()["attempts"] == 1


def test_explicit_authorized_factory_never_falls_back_to_legacy(slot, monkeypatch):
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "dummy-mock-token")
    monkeypatch.setenv("XDG_STATE_HOME", str(slot))
    assert accounting.authorized_model()[1]._identity is None
    with pytest.raises(period.PeriodError):
        accounting.authorized_model(None)
    with pytest.raises(period.PeriodError):
        accounting.authorized_model(object())
    identity, budget = bound(slot, active=False)
    with pytest.raises(BudgetLimitExceeded):
        accounting.authorized_model(identity)
    budget.activate()
    alias, connected = accounting.authorized_model(identity)
    assert alias == "deepseek-flash"
    assert connected.snapshot()["period_identity"] == identity.identity
    assert connected.snapshot()["legacy_history"] == "UNKNOWN"
    receipt = reserve(connected)
    connected.settle(receipt, None)
    with pytest.raises(BudgetLimitExceeded):
        accounting.authorized_model(identity)
    before = connected.snapshot()
    with pytest.raises(BudgetLimitExceeded):
        reserve(connected, "dav53_scenarios")
    assert connected.snapshot() == before



def consume_continuation_baseline(budget):
    for purpose, count in (("dav58_loop", 24), ("dav58_judge", 8)):
        for _ in range(count):
            settled_reserve(budget, purpose)


def test_one_audited_extension_preserves_identity_history_and_global_budget(slot):
    identity, budget = bound(slot)
    consume_continuation_baseline(budget)
    before = budget.snapshot()
    with sqlite3.connect(budget.path) as db:
        history = db.execute("SELECT * FROM attempts ORDER BY attempt_id").fetchall()
    event = budget.extend_dav58_once("explicit user requests one complete real validation")
    assert (event["before"], event["after"]) == (24, 48)
    assert event["identity"] == identity.__dict__
    assert event["usage_before"]["attempts"] == 32
    assert budget.snapshot() == before
    assert ModelBudget.bound(identity).continuation() == event
    with sqlite3.connect(budget.path) as db:
        assert db.execute("SELECT * FROM attempts ORDER BY attempt_id").fetchall() == history
    with pytest.raises(period.PeriodError, match="already allocated"):
        budget.extend_dav58_once("repeat")
    for _ in range(24):
        receipt = reserve(budget)
        budget.settle(receipt, {"prompt_tokens": 100, "completion_tokens": 20})
    with pytest.raises(BudgetLimitExceeded):
        reserve(budget)
    assert budget.snapshot()["attempts"] == 56


@pytest.mark.parametrize("failure", ["not_exhausted", "unknown", "pending", "cost", "global", "judge"])
def test_extension_rejects_incomplete_or_unfunded_plan(slot, failure):
    _, budget = bound(slot)
    if failure != "not_exhausted":
        consume_continuation_baseline(budget)
        with sqlite3.connect(budget.path) as db:
            if failure == "unknown": db.execute("UPDATE attempts SET usage_known=0")
            if failure == "pending": db.execute("UPDATE attempts SET settled=0")
            if failure == "cost": db.execute("UPDATE budget SET reserved_micro_usd=900000")
            if failure == "global": db.execute("UPDATE budget SET attempts=40")
            if failure == "judge": db.execute("UPDATE purposes SET attempts=30 WHERE purpose='dav58_judge'")
    with pytest.raises(BudgetLimitExceeded):
        budget.extend_dav58_once("explicit one run")
    assert budget.continuation() is None


@pytest.mark.parametrize("tamper", ["digest", "limit", "delete"])
def test_extension_audit_or_limit_drift_fails_closed(slot, tamper):
    identity, budget = bound(slot)
    consume_continuation_baseline(budget)
    budget.extend_dav58_once("explicit one run")
    with sqlite3.connect(budget.path) as db:
        if tamper == "digest": db.execute("UPDATE dav58_continuation SET sha256=?", ("0" * 64,))
        if tamper == "limit": db.execute("UPDATE purposes SET attempt_limit=49 WHERE purpose='dav58_loop'")
        if tamper == "delete": db.execute("DROP TABLE dav58_continuation")
    with pytest.raises(period.PeriodError):
        ModelBudget.bound(identity)
