"""Explicit filesystem-device recovery for the existing manual analysis budget."""

import fcntl
import hashlib
import json
import os
import sqlite3
import stat
import subprocess
from pathlib import Path

EVENT = "manual-device-rebinding.json"
OBJECTS = {
    "period_directory": "period",
    "state_file": "period/state.jsonl",
    "accounting_directory": "accounting",
    "ledger": "accounting/budget.sqlite3",
}


def filesystem_identity(slot):
    value = json.loads(subprocess.check_output(
        ["findmnt", "-T", str(slot), "-J", "-o", "UUID,FSROOT,FSTYPE"], text=True,
    ))["filesystems"]
    if len(value) != 1 or not value[0].get("uuid") or not value[0].get("fsroot"):
        raise ValueError("manual budget filesystem identity unavailable")
    return value[0]


def private_bytes(path):
    from authorized_budget_period import _parent, _file
    with _parent(path) as parent:
        fd = _file(parent, Path(path).name, os.O_RDONLY)
    try:
        info = os.fstat(fd)
        if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1
                or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600):
            raise ValueError("manual budget metadata is not private")
        with os.fdopen(os.dup(fd), "rb") as stream:
            raw = stream.read(1_048_577)
        if len(raw) > 1_048_576:
            raise ValueError("manual budget metadata too large")
        return raw, info
    finally:
        os.close(fd)


def validate(event, slot, identity):
    from authorized_budget_period import MANUAL_LIVE_TEST_PERIOD_ID, MANUAL_LIVE_TEST_POLICY_ID
    if (identity.get("policy_id") != MANUAL_LIVE_TEST_POLICY_ID
            or identity.get("period_id") != MANUAL_LIVE_TEST_PERIOD_ID
            or event.get("identity") != identity or event.get("slot") != str(slot)
            or event.get("schema") != "manual-device-rebinding-v1"
            or event.get("filesystem") != filesystem_identity(slot)
            or set(event.get("objects", {})) != set(OBJECTS)):
        raise ValueError("manual budget recovery scope changed")
    original = {}
    for name, path in {"identity": "period/identity.json", "binding": "accounting/binding.json"}.items():
        raw, _ = private_bytes(slot / path)
        if hashlib.sha256(raw).hexdigest() != event["hashes"][name]:
            raise ValueError("manual budget original binding changed")
        original[name] = json.loads(raw)
        if any(original[name].get(k) != v for k, v in identity.items()):
            raise ValueError("manual budget identity mismatch")
    pairs = {
        "period_directory": original["identity"]["directory"],
        "state_file": original["identity"]["state_file"],
        "accounting_directory": original["binding"]["directory"],
        "ledger": original["binding"]["ledger"],
    }
    for name, path in OBJECTS.items():
        info = (slot / path).lstat()
        mode = 0o700 if name.endswith("directory") else 0o600
        if (stat.S_ISLNK(info.st_mode) or info.st_uid != os.getuid()
                or stat.S_IMODE(info.st_mode) != mode
                or (name.endswith("directory") and not stat.S_ISDIR(info.st_mode))
                or (not name.endswith("directory") and (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1))
                or event["objects"][name] != pairs[name] or pairs[name][1] != info.st_ino):
            raise ValueError("manual budget object replaced")


def resolve_manual_pair(slot, identity, kind, recorded, observed):
    slot = Path(slot)
    raw, info = private_bytes(slot / EVENT)
    event = json.loads(raw)
    if event.get("event_inode") != info.st_ino:
        raise ValueError("manual budget recovery event replaced")
    validate(event, slot, identity)
    if event["objects"].get(kind) != recorded or recorded[1] != observed[1]:
        raise ValueError("manual budget recovery object mismatch")
    return recorded


def rebind_manual_budget(slot):
    """Publish recovery evidence without modifying or replenishing the original ledger."""
    from authorized_budget_period import MANUAL_LIVE_TEST_POLICY_ID, MANUAL_LIVE_TEST_PERIOD_ID
    from model_budget import authorized_period_config_sha256
    from agent_service.runtime_authorization import resolve_u03_runtime_binding, open_u03_runtime_guard
    from authorized_budget_period import PeriodIdentity
    slot = Path(slot)
    raw, _ = private_bytes(slot / "period/identity.json")
    identity = json.loads(raw)
    expected = PeriodIdentity(MANUAL_LIVE_TEST_PERIOD_ID,
        authorized_period_config_sha256(MANUAL_LIVE_TEST_POLICY_ID), identity["identity"], MANUAL_LIVE_TEST_POLICY_ID)
    guard_path = slot / "accounting" / f"dav58-increment-{expected.identity}.json"
    from authorized_budget_period import _parent, _file
    with _parent(guard_path) as parent:
        lock_fd = _file(parent, guard_path.name + ".lock", os.O_RDWR)
    try:
        with _parent(slot / "period/state.jsonl") as parent:
            state_fd = _file(parent, "state.jsonl", os.O_RDWR)
    except Exception:
        os.close(lock_fd)
        raise
    published = False
    try:
        fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        fcntl.flock(state_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        binding_raw, _ = private_bytes(slot / "accounting/binding.json")
        binding = json.loads(binding_raw)
        event = {"schema": "manual-device-rebinding-v1", "identity": expected.__dict__,
            "slot": str(slot), "filesystem": filesystem_identity(slot),
            "hashes": {"identity": hashlib.sha256(raw).hexdigest(), "binding": hashlib.sha256(binding_raw).hexdigest()},
            "objects": {"period_directory": identity["directory"], "state_file": identity["state_file"],
                "accounting_directory": binding["directory"], "ledger": binding["ledger"]}}
        validate(event, slot, expected.__dict__)
        journal = json.loads(private_bytes(guard_path)[0])
        db = sqlite3.connect(f"file:{slot / 'accounting/budget.sqlite3'}?mode=ro", uri=True)
        try:
            if db.execute("PRAGMA integrity_check").fetchall() != [("ok",)]:
                raise ValueError("manual budget ledger integrity failure")
            count, actual, halted = db.execute("SELECT attempts,actual_micro_usd,halted FROM budget").fetchone()
            if (halted or journal.get("halted") is not False or journal.get("pending_micro_usd") != 0
                    or len(journal.get("receipts", [])) != count or journal.get("settled_peak_micro_usd") != actual
                    or db.execute("SELECT count(*) FROM attempts WHERE settled=0 OR usage_known!=1").fetchone()[0]):
                raise ValueError("manual budget usage unresolved")
        finally:
            db.close()
        if not (slot / EVENT).exists():
            with _parent(slot / EVENT) as parent:
                temporary = EVENT + "." + os.urandom(12).hex() + ".tmp"
                fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=parent)
                try:
                    with os.fdopen(fd, "w") as stream:
                        event["event_inode"] = os.fstat(stream.fileno()).st_ino
                        json.dump(event, stream)
                        stream.flush()
                        os.fsync(stream.fileno())
                    # The held guard lock serializes recovery publishers.
                    if os.path.lexists(slot / EVENT):
                        raise ValueError("manual budget recovery already published")
                    os.rename(temporary, EVENT, src_dir_fd=parent, dst_dir_fd=parent)
                    published = True
                finally:
                    if os.path.lexists(slot / temporary):
                        os.unlink(temporary, dir_fd=parent)
                    os.fsync(parent)
        resolve_manual_pair(slot, expected.__dict__, "period_directory", identity["directory"],
            [slot.joinpath("period").stat().st_dev, slot.joinpath("period").stat().st_ino])
    except Exception:
        if published:
            (slot / EVENT).unlink()
        raise
    finally:
        os.close(state_fd)
        os.close(lock_fd)
    # Reconcile the complete runtime contract after releasing recovery locks.
    guard = open_u03_runtime_guard(resolve_u03_runtime_binding(expected))
    guard.close()
    return {"attempts": count, "actual_micro_usd": actual}
