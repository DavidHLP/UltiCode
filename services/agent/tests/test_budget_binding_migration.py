"""Offline proof of the specific binding transition, including interrupted publication."""
import fcntl
import hashlib
import json
import os
import sqlite3
from copy import deepcopy
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


def _recovery_digest(value):
    raw = json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(raw).hexdigest()


def _synthetic_recovery_inputs():
    """Synthetic snapshot-shaped unit inputs only; never a ledger, guard file, or approval manifest."""
    ids = ("a" * 32, "b" * 32)
    known_amounts = iter([333] * 42 + [332] * 4)

    def make_source(index, ledger_uuid, count, unknown=False):
        prefix = "legacy-58" if index == 0 else "legacy-53"
        rows = []
        for attempt_index in range(count):
            is_unknown = unknown and attempt_index == count - 1
            purpose = ("dav58_loop" if attempt_index < 24 else "dav58_judge") if index == 0 else "dav53_scenarios"
            rows.append((
                f"{prefix}-{attempt_index + 1:02d}",
                f"2026-10-{3 + index:02d} 10:{attempt_index:02d}:00",
                purpose,
                24000,
                1000 if purpose == "dav53_scenarios" else 2000,
                9600,
                None if is_unknown else next(known_amounts),
                0 if is_unknown else 1,
                0 if is_unknown else 1,
            ))
        provenance = {
            "ledger_uuid": ledger_uuid,
            "identity": ("c" if index == 0 else "e") * 32,
            "period_id": f"source-{index + 1}",
            "config_sha256": ("d" if index == 0 else "f") * 64,
            "recorded_ledger": [58, 123 + index],
            "actual_device": 58,
        }
        snapshot = {
            "attempts": tuple(rows),
            "budget": [(len(rows), sum(row[5] for row in rows), sum(row[6] or 0 for row in rows), 0, 0, 0, 1)],
            "sources": [(ledger_uuid, json.dumps(provenance, sort_keys=True, separators=(",", ":")))],
            "mapping": [(row[0], ledger_uuid) for row in rows],
            "schema": [],
            "source_bindings": {ledger_uuid: provenance["recorded_ledger"]},
        }
        return {"snapshot": snapshot, "source_sha256": _recovery_digest(snapshot)}

    sources = (
        make_source(0, ids[0], 35, unknown=True),
        make_source(1, ids[1], 12),
    )
    first_provenance = json.loads(sources[0]["snapshot"]["sources"][0][1])
    guard_state = {
        "period_identity": first_provenance["identity"],
        "config_sha256": first_provenance["config_sha256"],
        "limit_micro_usd": 1_000_000,
        "settled_peak_micro_usd": sum(row[6] or 0 for row in sources[0]["snapshot"]["attempts"]),
        "pending_micro_usd": 786432,
        "halted": True,
        "receipts": [
            {
                "lane": row[2],
                "request_sha256": hashlib.sha256(row[0].encode()).hexdigest(),
                "reserved_micro_usd": 786432,
                "peak_micro_usd": row[6],
                "status": "unknown_or_unsafe" if row[7] == 0 or row[8] == 0 else "settled",
            }
            for row in sources[0]["snapshot"]["attempts"]
        ],
    }
    guard = {"state": guard_state, "source_sha256": _recovery_digest(guard_state)}
    unknown_row = sources[0]["snapshot"]["attempts"][-1]
    unknown_row_sha256 = hashlib.sha256(unknown_row[0].encode()).hexdigest()
    bound_receipts = [receipt for receipt in guard_state["receipts"] if receipt["request_sha256"] == unknown_row_sha256]
    assert len(bound_receipts) == 1  # the unique unknown row binds to exactly one guard receipt by request hash
    unknown_receipt = bound_receipts[0]
    unknown_match = {
        "ledger_uuid": ids[0],
        "attempt_id": unknown_row[0],
        "request_sha256": unknown_receipt["request_sha256"],
        "period_identity": guard_state["period_identity"],
        "config_sha256": guard_state["config_sha256"],
        "pending_micro_usd": guard_state["pending_micro_usd"],
    }
    return sources, guard, unknown_match


def test_recovery_source_snapshot_preserves_exact_47_rows_and_unknown_guard_provenance():
    ids = ("a" * 32, "b" * 32)
    sources, guard, unknown_match = _synthetic_recovery_inputs()
    evidence = migration.validate_recovery_sources(sources, guard, unknown_match)
    provenance = {
        ledger_uuid: json.loads(raw)
        for source in sources
        for ledger_uuid, raw in source["snapshot"]["sources"]
    }
    unknown_rows = [
        row for source in sources for row in source["snapshot"]["attempts"]
        if row[7] != 1 or row[8] != 1
    ]
    pending_receipts = [receipt for receipt in guard["state"]["receipts"] if receipt["status"] == "unknown_or_unsafe"]

    assert evidence.historical_attempts == 47
    assert evidence.known_attempts == 46
    assert evidence.unknown_attempts == 1
    assert evidence.known_actual_micro_usd == 15314
    assert evidence.source_attempt_counts == {ids[0]: 35, ids[1]: 12}
    assert evidence.unknown_attempt_id == unknown_rows[0][0] == unknown_match["attempt_id"]
    assert len(pending_receipts) == 1
    assert pending_receipts[0]["request_sha256"] == unknown_match["request_sha256"]
    assert pending_receipts[0]["reserved_micro_usd"] == evidence.pending_micro_usd == 786432
    assert evidence.unknown_guard_match == unknown_match
    assert evidence.source_sha256_by_ledger == {
        ledger_uuid: source["source_sha256"]
        for source in sources
        for ledger_uuid, _ in source["snapshot"]["sources"]
    }
    assert evidence.source_provenance_by_ledger == provenance
    assert evidence.guard_source_sha256 == guard["source_sha256"]
    with pytest.raises(TypeError):
        evidence.source_sha256_by_ledger[ids[0]] = "0" * 64
    with pytest.raises(TypeError):
        evidence.source_provenance_by_ledger[ids[0]]["period_id"] = "forged"


@pytest.mark.parametrize("drift", ["row_loss", "unknown_forgery", "guard_mismatch", "guard_receipt_mismatch", "unknown_match", "unknown_request_hash", "source_hash", "provenance"])
def test_recovery_source_snapshot_rejects_row_loss_unknown_forgery_or_unbound_provenance(drift):
    sources, guard, unknown_match = deepcopy(_synthetic_recovery_inputs())
    if drift == "row_loss":
        snapshot = sources[0]["snapshot"]
        snapshot["attempts"] = snapshot["attempts"][:-1]
        snapshot["mapping"] = snapshot["mapping"][:-1]
        rows = snapshot["attempts"]
        snapshot["budget"] = [(len(rows), sum(row[5] for row in rows), sum(row[6] or 0 for row in rows), 0, 0, 0, 1)]
        sources[0]["source_sha256"] = _recovery_digest(snapshot)
    elif drift == "unknown_forgery":
        snapshot = sources[0]["snapshot"]
        rows = list(snapshot["attempts"])
        row = list(rows[-1])
        row[6], row[7], row[8] = 0, 1, 1
        rows[-1] = tuple(row)
        snapshot["attempts"] = tuple(rows)
        sources[0]["source_sha256"] = _recovery_digest(snapshot)
    elif drift == "guard_mismatch":
        guard["state"]["pending_micro_usd"] = 786431
        guard["source_sha256"] = _recovery_digest(guard["state"])
    elif drift == "guard_receipt_mismatch":
        guard["state"]["receipts"][-1]["request_sha256"] = "0" * 64
        guard["source_sha256"] = _recovery_digest(guard["state"])
    elif drift == "unknown_match":
        unknown_match["attempt_id"] = sources[0]["snapshot"]["attempts"][-2][0]
    elif drift == "unknown_request_hash":
        unknown_match["request_sha256"] = "0" * 64
    elif drift == "source_hash":
        sources[0]["source_sha256"] = "0" * 64
    else:
        snapshot = sources[0]["snapshot"]
        ledger_uuid, raw = snapshot["sources"][0]
        provenance = json.loads(raw)
        provenance["ledger_uuid"] = "9" * 32
        snapshot["sources"] = [(ledger_uuid, json.dumps(provenance, sort_keys=True, separators=(",", ":")))]
        sources[0]["source_sha256"] = _recovery_digest(snapshot)

    with pytest.raises(ValueError):
        migration.validate_recovery_sources(sources, guard, unknown_match)


def _recovery_plan_inputs(evidence):
    """Synthetic condition inputs only; not an approval manifest and not evidence."""
    return {
        "source_evidence": evidence,
        "approval": {"cumulative_attempts": 204, "limit_micro_usd": 2900000},
        "purpose_caps": {
            "dav58_loop": {"attempts": 24, "prompt_token_cap": 8000, "completion_token_cap": 2000},
            "dav58_judge": {"attempts": 19, "prompt_token_cap": 8000, "completion_token_cap": 2000},
            "dav53_scenarios": {"attempts": 12, "prompt_token_cap": 24000, "completion_token_cap": 1000},
            "u03_analysis": {"attempts": 12, "prompt_token_cap": 24000, "completion_token_cap": 2000},
            "u03_citation_judge": {"attempts": 9, "prompt_token_cap": 24000, "completion_token_cap": 2000},
            "u04_post_demo": {"attempts": 81, "prompt_token_cap": 24000, "completion_token_cap": 2000},
        },
        "pricing": {"input_tenths_micro_usd_per_token": 3, "output_tenths_micro_usd_per_token": 12},
        "envelopes": {
            "input_context_token_cap": 1048576,
            "output_token_cap": 393216,
            "envelope_micro_usd": ENVELOPE_MICRO_USD,
            "unknown_pending_micro_usd": 786432,
            "legacy_unknown_encumbrance_micro_usd": 786432,
        },
        "stage_caps": {"u04_demo_attempts": 7, "u04_total_attempts": 88},
    }


def test_recovery_plan_compiles_only_from_explicit_immutable_approval_caps_and_pricing():
    sources, guard, unknown_match = _synthetic_recovery_inputs()
    evidence = migration.validate_recovery_sources(sources, guard, unknown_match)
    inputs = _recovery_plan_inputs(evidence)
    plan = period.compile_recovery_plan(**inputs)

    assert period.POLICY_ID == "dav58-dav53-v1"
    assert period.POLICY["limit_micro_usd"] == 1_000_000
    assert period.POLICY["attempts"] == 78
    assert dict(period.POLICY["lanes"]["dav58_loop"]) == {
        "attempts": 24, "completion_token_cap": 2000, "rounds": 4,
    }
    assert dict(period.POLICY["lanes"]["dav58_judge"]) == {
        "attempts": 42, "completion_token_cap": 2000, "rounds": 1,
    }
    assert dict(period.POLICY["lanes"]["dav53_scenarios"]) == {
        "attempts": 12, "completion_token_cap": 1000, "rounds": 4,
    }
    assert plan.approved_attempts == inputs["approval"]["cumulative_attempts"] == 204
    assert plan.approved_limit_micro_usd == inputs["approval"]["limit_micro_usd"] == 2900000
    assert plan.future_attempts == sum(cap["attempts"] for cap in inputs["purpose_caps"].values()) == 157
    assert {name: dict(cap) for name, cap in plan.purpose_caps.items()} == inputs["purpose_caps"]
    assert (plan.u04_demo_attempts, plan.u04_post_demo_attempts, plan.u04_total_attempts) == (7, 81, 88)
    assert plan.pricing == inputs["pricing"]
    assert plan.envelopes == inputs["envelopes"]
    caps, price = inputs["envelopes"], inputs["pricing"]
    envelope = plan.envelopes["envelope_micro_usd"]
    assert envelope == ENVELOPE_MICRO_USD == 786432
    assert envelope == (
        caps["input_context_token_cap"] * price["input_tenths_micro_usd_per_token"]
        + caps["output_token_cap"] * price["output_tenths_micro_usd_per_token"] + 9) // 10
    assert plan.cost_components_micro_usd == {
        "A": 15314, "U": envelope, "R": 1286400, "cmax": 9600, "E": envelope,
    }
    assert plan.peak_micro_usd == 15314 + envelope + (1286400 - 9600) + envelope == 2864978
    assert plan.remaining_micro_usd == 35022
    assert plan.legacy_encumbrance_status == "UNKNOWN"
    assert plan.legacy_encumbrance_micro_usd == 786432
    assert plan.paid_authorized is False
    assert plan.runtime_applied is False
    assert plan.source_sha256_by_ledger == evidence.source_sha256_by_ledger
    assert plan.source_provenance_by_ledger == evidence.source_provenance_by_ledger

    with pytest.raises(AttributeError):
        plan.future_attempts = 0
    with pytest.raises(TypeError):
        plan.purpose_caps["dav58_loop"] = {}
    with pytest.raises(TypeError):
        plan.purpose_caps["dav58_loop"]["attempts"] = 0
    with pytest.raises(TypeError):
        plan.pricing["output_tenths_micro_usd_per_token"] = 0


def test_recovery_plan_rejects_an_approval_limit_below_computed_peak():
    sources, guard, unknown_match = _synthetic_recovery_inputs()
    evidence = migration.validate_recovery_sources(sources, guard, unknown_match)
    inputs = _recovery_plan_inputs(evidence)
    inputs["approval"]["limit_micro_usd"] = 2864977

    with pytest.raises(ValueError):
        period.compile_recovery_plan(**inputs)


@pytest.mark.parametrize("drift", ["missing_purpose", "price", "envelope"])
def test_recovery_plan_rejects_missing_purpose_or_pricing_envelope_drift(drift):
    sources, guard, unknown_match = _synthetic_recovery_inputs()
    evidence = migration.validate_recovery_sources(sources, guard, unknown_match)
    inputs = _recovery_plan_inputs(evidence)
    if drift == "missing_purpose":
        inputs["purpose_caps"].pop("u03_citation_judge")
    elif drift == "price":
        inputs["pricing"]["output_tenths_micro_usd_per_token"] += 1
    else:
        inputs["envelopes"]["input_context_token_cap"] -= 1

    with pytest.raises(ValueError):
        period.compile_recovery_plan(**inputs)
