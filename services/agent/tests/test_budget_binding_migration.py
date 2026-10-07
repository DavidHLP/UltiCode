"""Offline proof of the specific binding transition, including interrupted publication."""
import fcntl
import hashlib
import json
import os
import sqlite3
from pathlib import Path

import pytest
import authorized_budget_period as period
import model_budget as accounting
import budget_binding_migration as migration
from dav58_live_guard import ENVELOPE_MICRO_USD


@pytest.fixture
def prepared_migration(tmp_path, monkeypatch):
    slot = tmp_path / "slot"
    slot.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: slot)
    identity = period.prepare_period(slot / "period", "offline", accounting.authorized_period_config_sha256()).identity
    budget = accounting.ModelBudget.bind_prepared(identity)
    budget.activate()
    receipts = []
    for index in range(32):
        lane = "dav58_loop" if index < 24 else "dav58_judge"
        usage = {"prompt_tokens": 1000, "completion_tokens": 34} if index < 31 else {"prompt_tokens": 600, "completion_tokens": 19}
        reservation = budget.reserve(24000, 2000, purpose=lane)
        cost = budget.settle(reservation, usage)
        receipts.append({**usage, "total_tokens": sum(usage.values()), "lane": lane,
            "status": "settled", "request_model": "deepseek-flash", "response_model": "deepseek-flash",
            "request_sha256": hashlib.sha256(str(index).encode()).hexdigest(),
            "reserved_micro_usd": ENVELOPE_MICRO_USD, "peak_micro_usd": cost})
    journal = {"period_identity": identity.identity, "config_sha256": identity.config_sha256,
               "limit_micro_usd": 1000000, "pending_micro_usd": 0, "halted": False,
               "settled_peak_micro_usd": 10774, "receipts": receipts}
    (slot / migration.FILES["journal"]).write_text(json.dumps(journal))
    (slot / migration.FILES["journal"]).chmod(0o644)
    (slot / (migration.FILES["journal"] + ".lock")).touch(mode=0o600)
    original = json.loads((slot / migration.FILES["identity"]).read_text())
    for key in ("directory", "state_file"): original[key][0] = migration.SOURCE_DEVICE
    (slot / migration.FILES["identity"]).write_text(accounting._json(original) + '\n')
    anchor = json.loads((slot / migration.FILES["binding"]).read_text())
    for key in ("directory", "ledger"): anchor[key][0] = migration.SOURCE_DEVICE
    (slot / migration.FILES["binding"]).write_text(accounting._json(anchor) + '\n')
    with sqlite3.connect(budget.path) as db:
        db.execute("UPDATE binding SET payload=?", (accounting._json(anchor),))
    hashes = {name: hashlib.sha256((slot / relative).read_bytes()).hexdigest() for name, relative in migration.FILES.items()}
    monkeypatch.setattr(migration, "REVIEWED_HASHES", hashes)
    monkeypatch.setattr(migration, "FILESYSTEM_EVIDENCE", {"offline": True})
    monkeypatch.setattr(migration, "_filesystem_evidence", lambda slot: {"offline": True})
    monkeypatch.setattr(migration, "TARGET_DEVICE", slot.stat().st_dev)
    return slot, identity, hashes


def apply(fixture, **kwargs):
    _, identity, hashes = fixture
    return migration.migrate(identity, hashes, migration.AUTHORIZATION, filesystem_evidence={"offline": True}, **kwargs)


def test_migration_keeps_original_bytes_counters_and_enables_bound_read(prepared_migration):
    slot, identity, hashes = prepared_migration
    assert apply(prepared_migration, dry_run=True)["hashes"] == hashes
    assert not (slot / migration.EVENT).exists()
    apply(prepared_migration)
    assert {name: hashlib.sha256((slot / relative).read_bytes()).hexdigest() for name, relative in migration.FILES.items()} == hashes
    budget = accounting.ModelBudget.bound(identity)
    assert budget.snapshot()["attempts"] == 32
    assert budget.snapshot()["actual_micro_usd"] == 10774
    event = json.loads((slot / migration.EVENT).read_text())
    assert event["identity"] == identity.__dict__
    assert event["source_device"] == 58
    with pytest.raises(ValueError, match="already published"):
        apply(prepared_migration)
    # Normal ledger mutation remains supported after the audited transition.
    receipt = budget.reserve(24000, 2000, purpose="dav58_judge")
    budget.settle(receipt, {"prompt_tokens": 100, "completion_tokens": 20})
    assert budget.snapshot()["attempts"] == 33


@pytest.mark.parametrize("change", ["journal", "ledger", "identity", "binding", "state", "owner_mode", "replace", "symlink"])
def test_changed_reviewed_inputs_fail_without_publishing(prepared_migration, change):
    slot, _, _ = prepared_migration
    if change in migration.FILES:
        path = slot / migration.FILES[change]
        path.write_bytes(path.read_bytes() + b' ')
    elif change == "owner_mode":
        (slot / migration.FILES["state"]).chmod(0o644)
    elif change == "replace":
        path = slot / migration.FILES["ledger"]
        copy = path.with_suffix('.copy'); copy.write_bytes(path.read_bytes()); copy.chmod(0o600); copy.replace(path)
    else:
        path = slot / migration.FILES["journal"]
        copy = path.with_suffix('.copy'); copy.write_bytes(path.read_bytes()); path.unlink(); path.symlink_to(copy)
    with pytest.raises((ValueError, OSError)):
        apply(prepared_migration)
    assert not (slot / migration.EVENT).exists()


@pytest.mark.parametrize("lock", ["state", "journal"])
def test_concurrent_owner_blocks_migration(prepared_migration, lock):
    slot, _, _ = prepared_migration
    path = slot / (migration.FILES["state"] if lock == "state" else migration.FILES["journal"] + ".lock")
    with path.open('rb') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        with pytest.raises(BlockingIOError): apply(prepared_migration)
    assert not (slot / migration.EVENT).exists()


def test_complete_pending_record_resumes_but_tampered_pending_does_not(prepared_migration):
    slot, identity, _ = prepared_migration
    def crash(): raise OSError("offline interruption before publish")
    with pytest.raises(OSError): apply(prepared_migration, before_publish=crash)
    assert (slot / migration.PENDING).exists() and not (slot / migration.EVENT).exists()
    pending = (slot / migration.PENDING).read_bytes()
    (slot / migration.PENDING).write_bytes(pending.replace(b'dav58-device-migration-v1', b'bad-schema'))
    with pytest.raises(ValueError): apply(prepared_migration)
    (slot / migration.PENDING).write_bytes(pending)
    apply(prepared_migration)
    assert not (slot / migration.PENDING).exists()
    assert accounting.ModelBudget.bound(identity).snapshot()["attempts"] == 32


@pytest.mark.parametrize("field", ["authorization", "target_device", "boot_id", "hashes"])
def test_published_event_tampering_rejects_runtime(prepared_migration, field):
    slot, identity, _ = prepared_migration
    apply(prepared_migration)
    path = slot / migration.EVENT
    event = json.loads(path.read_text())
    event[field] = "tampered"
    path.write_text(json.dumps(event))
    with pytest.raises((ValueError, period.PeriodError)):
        accounting.ModelBudget.bound(identity)


def test_unknown_pending_receipt_stops_even_if_new_digest_is_supplied(prepared_migration, monkeypatch):
    slot, _, hashes = prepared_migration
    path = slot / migration.FILES["journal"]
    journal = json.loads(path.read_text()); journal["pending_micro_usd"] = ENVELOPE_MICRO_USD
    path.write_text(json.dumps(journal))
    hashes["journal"] = hashlib.sha256(path.read_bytes()).hexdigest()
    monkeypatch.setattr(migration, "REVIEWED_HASHES", hashes)
    with pytest.raises(ValueError): apply(prepared_migration)
    assert not (slot / migration.EVENT).exists()


def test_lock_replacement_during_publication_is_rejected(prepared_migration):
    slot, _, _ = prepared_migration
    def replace_lock():
        path = slot / (migration.FILES["journal"] + ".lock")
        path.unlink(); path.touch(mode=0o600)
    with pytest.raises(ValueError, match="guard lock replaced"):
        apply(prepared_migration, before_publish=replace_lock)
    assert not (slot / migration.EVENT).exists()


def test_interruption_after_link_can_complete_cleanup(prepared_migration):
    slot, identity, _ = prepared_migration
    def crash(): raise OSError("interruption")
    with pytest.raises(OSError): apply(prepared_migration, before_publish=crash)
    os.link(slot / migration.PENDING, slot / migration.EVENT)
    apply(prepared_migration)
    assert not (slot / migration.PENDING).exists()
    assert accounting.ModelBudget.bound(identity).snapshot()["attempts"] == 32



@pytest.mark.parametrize("change", ["pending_content", "pending_replace", "journal_mode", "identity_mode", "binding_link"])
def test_publication_window_tampering_stops(prepared_migration, change):
    slot, _, _ = prepared_migration
    def tamper():
        path = slot / migration.PENDING
        if change == "pending_content":
            event = json.loads(path.read_text()); event["utc"] = "2000-01-01T00:00:00+00:00"; path.write_text(json.dumps(event))
        elif change == "pending_replace":
            copy = path.with_suffix('.copy'); copy.write_bytes(path.read_bytes()); copy.chmod(0o600); copy.replace(path)
        elif change == "binding_link":
            os.link(slot / migration.FILES["binding"], slot / "foreign-link")
        else:
            name = "journal" if change == "journal_mode" else "identity"
            (slot / migration.FILES[name]).chmod(0o666)
    with pytest.raises(ValueError): apply(prepared_migration, before_publish=tamper)
    assert not (slot / migration.EVENT).exists()


@pytest.mark.parametrize("change", ["mode", "link", "replace"])
def test_runtime_event_permissions_or_identity_change_fails(prepared_migration, change):
    slot, identity, _ = prepared_migration
    apply(prepared_migration)
    path = slot / migration.EVENT
    if change == "mode": path.chmod(0o666)
    elif change == "link": os.link(path, slot / "foreign-event")
    else:
        copy = path.with_suffix('.copy'); copy.write_bytes(path.read_bytes()); copy.chmod(0o600); copy.replace(path)
    with pytest.raises(period.PeriodError): accounting.ModelBudget.bound(identity)


@pytest.fixture
def locked_audit_ledger(tmp_path):
    directory = tmp_path / "audit"
    directory.mkdir(mode=0o700)
    path = directory / "budget.sqlite3"
    manifest = {
        "ledger_uuid": "a" * 32, "status": "BLOCKED_AUDIT_ONLY",
        "approved_cumulative_attempts": 180, "approved_cumulative_micro_usd": 2200000,
        "authorization_applied": False, "paid_calls_enabled": False,
        "historical_attempts": 2, "sql_reserved_micro_usd": 19200,
        "known_actual_micro_usd": 300, "unknown_attempts": 1,
        "original_guard_pending_micro_usd": ENVELOPE_MICRO_USD,
    }
    with sqlite3.connect(path) as db:
        accounting._initialize_tables(db)
        db.executemany("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?,?)", [
            ("known", "2026-10-03 10:00:00", "dav58_loop", 24000, 2000, 9600, 300, 1, 1),
            ("unknown", "2026-10-04 05:38:10", "dav58_loop", 24000, 2000, 9600, None, 0, 0),
        ])
        db.execute("UPDATE budget SET attempts=2,reserved_micro_usd=19200,actual_micro_usd=300,halted=1")
        db.execute("CREATE TABLE audit_manifest(singleton INTEGER PRIMARY KEY,payload TEXT NOT NULL)")
        db.execute("INSERT INTO audit_manifest VALUES(1,?)", (json.dumps(manifest),))
        db.execute("CREATE TABLE audit_sources(ledger_uuid TEXT PRIMARY KEY,payload TEXT NOT NULL)")
        db.execute("INSERT INTO audit_sources VALUES(?,?)", ("b" * 32, json.dumps({
            "ledger_uuid": "b" * 32, "identity": "c" * 32,
            "period_id": "original", "config_sha256": "d" * 64,
            "recorded_ledger": [58, 123], "actual_device": path.stat().st_dev,
        })))
        db.execute("CREATE TABLE attempt_source(attempt_id TEXT PRIMARY KEY,ledger_uuid TEXT NOT NULL)")
        db.executemany("INSERT INTO attempt_source VALUES(?,?)", [(name, "b" * 32) for name in ("known", "unknown")])
    path.chmod(0o600)
    return path


def test_audit_recovery_copy_preserves_unknown_caps_and_source(locked_audit_ledger, tmp_path):
    source = locked_audit_ledger
    before = source.read_bytes()
    destination = tmp_path / "recovery"
    event = migration.rehearse_audit_recovery(source, destination)
    assert source.read_bytes() == before
    assert event["status"] == "BLOCKED_AUDIT_ONLY"
    assert event["paid_calls_enabled"] is False
    assert event["unknown_attempts"] == 1
    assert event["approved_cumulative_attempts"] == 180
    assert event["approved_cumulative_micro_usd"] == 2200000
    assert event["source_binding"] != event["copy_binding"]
    assert event["source_bindings"]["b" * 32] == [58, 123]
    assert json.loads((destination / "recovery.json").read_text()) == event
    assert not (destination / "recovery.pending.json").exists()
    copy = destination / "budget.sqlite3"
    assert copy.stat().st_mode & 0o777 == 0o600
    assert destination.stat().st_mode & 0o777 == 0o700
    with sqlite3.connect(copy) as db:
        assert db.execute("SELECT attempts,halted FROM budget").fetchone() == (2, 1)
        assert db.execute("SELECT settled,usage_known,actual_micro_usd FROM attempts WHERE attempt_id='unknown'").fetchone() == (0, 0, None)
    with pytest.raises(accounting.BudgetLimitExceeded):
        accounting.ModelBudget(copy).reserve(1, 1)


@pytest.mark.parametrize("drift", ["unknown", "counter", "mapping", "pending", "approval"])
def test_audit_recovery_rejects_inconsistent_history_before_claim(locked_audit_ledger, tmp_path, drift):
    with sqlite3.connect(locked_audit_ledger) as db:
        manifest = json.loads(db.execute("SELECT payload FROM audit_manifest").fetchone()[0])
        if drift == "unknown":
            manifest["unknown_attempts"] = 0
        elif drift == "counter":
            db.execute("UPDATE budget SET attempts=1")
        elif drift == "mapping":
            db.execute("DELETE FROM attempt_source WHERE attempt_id='unknown'")
        elif drift == "pending":
            manifest["original_guard_pending_micro_usd"] = 0
        else:
            manifest["authorization_applied"] = True
        db.execute("UPDATE audit_manifest SET payload=?", (json.dumps(manifest),))
    target = tmp_path / "recovery"
    with pytest.raises(ValueError):
        migration.rehearse_audit_recovery(locked_audit_ledger, target)
    assert not target.exists()


def test_audit_recovery_rejects_symlink_and_existing_destination(locked_audit_ledger, tmp_path):
    alias = tmp_path / "alias.sqlite3"
    alias.symlink_to(locked_audit_ledger)
    with pytest.raises((ValueError, OSError)):
        migration.rehearse_audit_recovery(alias, tmp_path / "symlink-copy")
    target = tmp_path / "existing"
    target.mkdir(mode=0o700)
    with pytest.raises(FileExistsError):
        migration.rehearse_audit_recovery(locked_audit_ledger, target)
    assert list(target.iterdir()) == []


def test_audit_recovery_rejects_public_input(locked_audit_ledger, tmp_path):
    locked_audit_ledger.chmod(0o644)
    with pytest.raises(ValueError):
        migration.rehearse_audit_recovery(locked_audit_ledger, tmp_path / "recovery")


def test_audit_recovery_failure_retains_pending_without_success(locked_audit_ledger, tmp_path, monkeypatch):
    original = migration._audit_recovery_snapshot
    calls = 0
    def fail_copy(connection):
        nonlocal calls
        calls += 1
        if calls == 2:
            raise ValueError("interrupted copy verification")
        return original(connection)
    monkeypatch.setattr(migration, "_audit_recovery_snapshot", fail_copy)
    target = tmp_path / "recovery"
    with pytest.raises(ValueError, match="interrupted copy"):
        migration.rehearse_audit_recovery(locked_audit_ledger, target)
    assert (target / "recovery.pending.json").exists()
    assert not (target / "recovery.json").exists()
    with pytest.raises(FileExistsError):
        migration.rehearse_audit_recovery(locked_audit_ledger, target)
