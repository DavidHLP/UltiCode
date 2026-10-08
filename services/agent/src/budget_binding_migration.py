"""Explicit, one-shot device identity migration; never edits original bindings."""
import copy
import fcntl
import hashlib
import json
import os
import sqlite3
import stat
import subprocess
import re
from collections.abc import Mapping
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from types import MappingProxyType

SOURCE_DEVICE = 58
TARGET_DEVICE = 59
REVIEWED_HASHES = {
    "identity": "13690ec5b0b44c9c673ca0765696d4514111f93f71d3215e752c98a882c74cc5",
    "state": "b5f963e00dcafd2d7b5a17f5bf6181fd04a8c3e7abb121a0fcafe4148a4b236c",
    "binding": "e6b146da27027f020e90e5eb6b97b55ec0696b521045e6b3a154632acfab5345",
    "ledger": "b350cf2af7b88659cee58341b799be71f316a983233c0f293a7202ad6b25fc8e",
    "journal": "35089364e67c30c5da079b53e2e8c1d63dab68f0f3c910b99fa112903ca9b56e",
}
FILESYSTEM_EVIDENCE = {"filesystem_uuid": "41239da5-4a0b-4bbe-b3db-95e20eea5316",
                       "subvolume_id": 257, "mount": "/home", "source": "/dev/mapper/root[/@home]"}
AUTHORIZATION = "user approved implementation and execution of this reviewed 58-to-59 binding migration"
EVENT = "binding-migration.json"
PENDING = "binding-migration.pending.json"
FILES = {"identity": "period/identity.json", "state": "period/state.jsonl",
         "binding": "accounting/binding.json", "ledger": "accounting/budget.sqlite3",
         "journal": "accounting/dav58-increment-b7131661377941b3b3627adaefa8d887.json"}
OBJECTS = {"period_directory": "period", "state_file": FILES["state"],
           "accounting_directory": "accounting", "ledger": FILES["ledger"]}


def _read(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_nlink not in (1, 2):
            raise ValueError("migration requires regular metadata")
        with os.fdopen(os.dup(fd), "rb") as stream:
            raw = stream.read(1_048_577)
        if len(raw) > 1_048_576:
            raise ValueError("migration metadata too large")
        return raw
    finally:
        os.close(fd)


def _read_event(path, *, interrupted=False):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        info = os.fstat(fd)
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
                or info.st_gid != os.getgid() or stat.S_IMODE(info.st_mode) != 0o600
                or info.st_nlink != (2 if interrupted else 1)):
            raise ValueError("migration event permissions or links changed")
        with os.fdopen(os.dup(fd), "rb") as stream:
            raw = stream.read(1_048_577)
        if len(raw) > 1_048_576:
            raise ValueError("migration event too large")
        event = json.loads(raw)
        if event.get("event_file") != [info.st_dev, info.st_ino]:
            raise ValueError("migration event file replaced")
        return event, raw
    finally:
        os.close(fd)


def _check_input_metadata(slot):
    for name, relative in FILES.items():
        info = (slot / relative).lstat()
        mode = 0o644 if name == "journal" else 0o600
        if (info.st_uid != os.getuid() or info.st_gid != os.getgid()
                or stat.S_IMODE(info.st_mode) != mode or info.st_nlink != 1
                or not stat.S_ISREG(info.st_mode)):
            raise ValueError("reviewed input owner or permissions changed")


def _digest(raw):
    return hashlib.sha256(raw).hexdigest()


def _filesystem_evidence(slot):
    mounted = json.loads(subprocess.run(["findmnt", "-T", str(slot), "-J", "-o", "TARGET,SOURCE,FSTYPE,FSROOT"],
                        check=True, capture_output=True, text=True).stdout)["filesystems"][0]
    shown = subprocess.run(["btrfs", "filesystem", "show", mounted["target"]],
                           check=True, capture_output=True, text=True).stdout
    found = re.search(r"uuid: ([0-9a-f-]{36})", shown)
    if (mounted["fstype"] != "btrfs" or mounted["fsroot"] != "/@home"
            or not found):
        raise ValueError("reviewed filesystem unavailable")
    # FSROOT @home and the supported mount's explicit subvolid are independently
    # checked by findmnt's options; no caller-supplied identity is trusted here.
    options = subprocess.run(["findmnt", "-T", str(slot), "-n", "-o", "OPTIONS"],
                             check=True, capture_output=True, text=True).stdout
    if "subvolid=257" not in options.split(","):
        raise ValueError("reviewed subvolume changed")
    return {"filesystem_uuid": found.group(1), "subvolume_id": 257,
            "mount": mounted["target"], "source": mounted["source"]}


def _validate(event, slot, identity):
    if (event.get("schema") != "dav58-device-migration-v1"
            or event.get("identity") != identity
            or event.get("slot") != str(slot)
            or event.get("source_device") != SOURCE_DEVICE
            or event.get("target_device") != TARGET_DEVICE
            or event.get("authorization") != AUTHORIZATION
            or event.get("hashes") != REVIEWED_HASHES
            or event.get("filesystem_evidence") != FILESYSTEM_EVIDENCE
            or event.get("boot_id") != _read(Path('/proc/sys/kernel/random/boot_id')).decode().strip()
            or set(event.get("objects", {})) != set(OBJECTS)):
        raise ValueError("migration authorization or scope drift")
    try:
        utc = datetime.fromisoformat(event["utc"])
        if utc.utcoffset() != timezone.utc.utcoffset(utc):
            raise ValueError("not UTC")
    except (ValueError, TypeError, KeyError):
        raise ValueError("invalid migration timestamp") from None
    for name, relative in OBJECTS.items():
        obj = event["objects"][name]
        info = (slot / relative).lstat()
        expected_mode = 0o700 if name.endswith("directory") else 0o600
        if (obj.get("before") != [SOURCE_DEVICE, info.st_ino]
                or obj.get("after") != [TARGET_DEVICE, info.st_ino]
                or info.st_dev != TARGET_DEVICE or info.st_uid != os.getuid()
                or info.st_gid != os.getgid() or stat.S_IMODE(info.st_mode) != expected_mode
                or (not name.endswith("directory") and info.st_nlink != 1) or stat.S_ISLNK(info.st_mode)):
            raise ValueError("migration object identity or permissions drift")
    for name in ("identity", "binding"):
        raw = _read(slot / FILES[name])
        if (_digest(raw) != event["hashes"][name]
                or raw.decode() != event["original_documents"][name]):
            raise ValueError("original binding content drift")
    original = json.loads(event["original_documents"]["identity"])
    binding = json.loads(event["original_documents"]["binding"])
    pairs = {"period_directory": original["directory"], "state_file": original["state_file"],
             "accounting_directory": binding["directory"], "ledger": binding["ledger"]}
    if any(event["objects"][name]["before"] != pair for name, pair in pairs.items()):
        raise ValueError("migration does not match original bindings")
    if any(original.get(k) != v or binding.get(k) != v for k, v in identity.items()):
        raise ValueError("original identity drift")


def resolve_pair(slot, identity, kind, recorded, observed):
    """Authorize only the exact audited object; no generic device exception."""
    if recorded == observed:
        return observed
    try:
        event, _ = _read_event(Path(slot) / EVENT)
        _validate(event, Path(slot), identity)
        obj = event["objects"][kind]
        if obj["before"] != recorded or obj["after"] != observed:
            raise ValueError("unapproved binding migration")
        return recorded
    except (ValueError, OSError, KeyError, TypeError) as error:
        from authorized_budget_period import PeriodError
        raise PeriodError("binding migration unavailable or invalid") from error


def migrate(expected, expected_hashes, authorization, *, filesystem_evidence, before_publish=None, dry_run=False):
    """Publish a migration after rechecking fixed fingerprints under both locks.

    Complete pending records may resume; partial/tampered ones remain blocked.
    Original bindings, SQLite data and receipt journal are never rewritten.
    """
    import authorized_budget_period as period
    import model_budget as accounting
    from dav58_live_guard import IncrementalGuard
    if expected_hashes != REVIEWED_HASHES or authorization != AUTHORIZATION:
        raise ValueError("unreviewed migration request")
    slot = accounting._authorization_slot()
    identity = expected.__dict__
    with period._parent(slot / "period") as slot_fd:
        # Both parent directories are opened no-follow before opening their files.
        pfd = os.open("period", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=slot_fd)
        afd = os.open("accounting", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=slot_fd)
        state_fd = lock_fd = None
        try:
            state_fd = period._file(pfd, "state.jsonl", os.O_RDWR)
            lock_fd = period._file(afd, Path(FILES["journal"]).name + ".lock", os.O_RDWR)
            fcntl.flock(state_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if _filesystem_evidence(slot) != filesystem_evidence:
                raise ValueError("filesystem evidence changed")
            _check_input_metadata(slot)
            raws = {name: _read(slot / relative) for name, relative in FILES.items()}
            if set(expected_hashes) != set(FILES) or any(_digest(raw) != expected_hashes[name] for name, raw in raws.items()):
                raise ValueError("reviewed migration fingerprints changed")
            original, binding, journal = (json.loads(raws[name]) for name in ("identity", "binding", "journal"))
            if (expected.config_sha256 != accounting.authorized_period_config_sha256()
                    or journal.get("continuation_run") is not None
                    or raws["state"] != b'{"state":"prepared"}\n{"state":"active"}\n'):
                raise ValueError("migration configuration or lifecycle drift")
            IncrementalGuard._validate_resume(journal, expected.identity, expected.config_sha256)
            db = sqlite3.connect(f"file:/proc/self/fd/{afd}/budget.sqlite3?mode=ro", uri=True)
            try:
                if db.execute("PRAGMA integrity_check").fetchall() != [("ok",)]:
                    raise ValueError("ledger integrity failure")
                if db.execute("SELECT payload,gate FROM binding").fetchall() != [(accounting._json(binding), "active")]:
                    raise ValueError("SQL binding drift")
                rows = db.execute("SELECT purpose,prompt_tokens,completion_cap,reserved_micro_usd,actual_micro_usd,usage_known,settled FROM attempts ORDER BY rowid").fetchall()
                if len(rows) != 32 or len(journal["receipts"]) != 32:
                    raise ValueError("migration history changed")
                for row, receipt in zip(rows, journal["receipts"]):
                    if row != (receipt["lane"], 24000, 2000, 9600, receipt["peak_micro_usd"], 1, 1):
                        raise ValueError("ledger receipt mismatch")
                if db.execute("SELECT attempts,reserved_micro_usd,actual_micro_usd,halted FROM budget").fetchall() != [(32, 307200, 10774, 0)]:
                    raise ValueError("reviewed budget baseline changed")
                if db.execute("SELECT name FROM sqlite_master WHERE name='dav58_continuation'").fetchone():
                    raise ValueError("unexpected continuation allocation")
            finally:
                db.close()
            pairs = {"period_directory": original["directory"], "state_file": original["state_file"],
                     "accounting_directory": binding["directory"], "ledger": binding["ledger"]}
            event = {"schema": "dav58-device-migration-v1", "identity": identity, "slot": str(slot),
                     "source_device": SOURCE_DEVICE, "target_device": TARGET_DEVICE,
                     "authorization": authorization, "utc": datetime.now(timezone.utc).isoformat(),
                     "hashes": expected_hashes, "original_documents": {name: raws[name].decode() for name in ("identity", "binding")},
                     "objects": {name: {"before": pair, "after": [TARGET_DEVICE, pair[1]]} for name, pair in pairs.items()},
                     "filesystem_evidence": filesystem_evidence, "boot_id": _read(Path('/proc/sys/kernel/random/boot_id')).decode().strip()}
            _validate(event, slot, identity)
            if dry_run:
                return event
            if os.path.lexists(slot / EVENT):
                # Finish only a publication interrupted between link and unlink.
                if not os.path.lexists(slot / PENDING) or not os.path.samefile(slot / EVENT, slot / PENDING):
                    raise ValueError("migration already published")
                saved, _ = _read_event(slot / EVENT, interrupted=True); _validate(saved, slot, identity)
                if saved["hashes"] != expected_hashes or saved["authorization"] != authorization:
                    raise ValueError("interrupted migration drift")
                os.unlink(PENDING, dir_fd=slot_fd); os.fsync(slot_fd)
                return saved
            if os.path.lexists(slot / PENDING):
                event, _ = _read_event(slot / PENDING)
                if event["hashes"] != expected_hashes or event["authorization"] != authorization:
                    raise ValueError("pending migration drift")
            _validate(event, slot, identity)
            if not os.path.lexists(slot / PENDING):
                fd = os.open(PENDING, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=slot_fd)
                try:
                    info = os.fstat(fd)
                    event["event_file"] = [info.st_dev, info.st_ino]
                    period._write(fd, event)
                finally:
                    os.close(fd)
                os.fsync(slot_fd)
            if before_publish:
                before_publish()
            _check_input_metadata(slot)
            published_event, expected_bytes = _read_event(slot / PENDING)
            if published_event != event:
                raise ValueError("pending migration content changed")
            # Recheck all reviewed content and fd/path identities before publication.
            if any(_digest(_read(slot / relative)) != expected_hashes[name] for name, relative in FILES.items()):
                raise ValueError("migration inputs changed before publication")
            if os.stat("state.jsonl", dir_fd=pfd, follow_symlinks=False) != os.fstat(state_fd):
                raise ValueError("lifecycle file replaced")
            _validate(event, slot, identity)
            if os.stat(Path(FILES["journal"]).name + ".lock", dir_fd=afd, follow_symlinks=False) != os.fstat(lock_fd):
                raise ValueError("guard lock replaced")
            pending_fd = os.open(PENDING, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=slot_fd)
            try:
                if [os.fstat(pending_fd).st_dev, os.fstat(pending_fd).st_ino] != event["event_file"]:
                    raise ValueError("pending migration replaced before publication")
                # linkat follows this proc fd to the pinned inode, never a replaced
                # pending pathname. The held fd stays open through publication.
                os.link(f"/proc/self/fd/{pending_fd}", EVENT, dst_dir_fd=slot_fd, follow_symlinks=True)
                os.fsync(slot_fd)
                saved, actual_bytes = _read_event(slot / EVENT, interrupted=True)
                if saved != event or actual_bytes != expected_bytes:
                    raise ValueError("published migration content changed")
                _check_input_metadata(slot)
                _validate(saved, slot, identity)
                if not os.path.samefile(slot / EVENT, slot / PENDING):
                    raise ValueError("pending migration path replaced after publication")
                os.unlink(PENDING, dir_fd=slot_fd)
                os.fsync(slot_fd)
                _read_event(slot / EVENT)
                return event
            finally:
                os.close(pending_fd)
        finally:
            for fd in (lock_fd, state_fd, afd, pfd):
                if fd is not None:
                    os.close(fd)


# This copy rehearsal never participates in resolve_pair or live authorization.
def _audit_recovery_snapshot(db):
    manifest_rows = db.execute("SELECT payload FROM audit_manifest ORDER BY singleton").fetchall()
    if len(manifest_rows) != 1:
        raise ValueError("one locked audit manifest required")
    manifest = json.loads(manifest_rows[0][0])
    if (manifest.get("status") != "BLOCKED_AUDIT_ONLY"
            or manifest.get("authorization_applied") is not False
            or manifest.get("paid_calls_enabled") is not False
            or type(manifest.get("approved_cumulative_attempts")) is not int
            or manifest["approved_cumulative_attempts"] != 180
            or type(manifest.get("approved_cumulative_micro_usd")) is not int
            or manifest["approved_cumulative_micro_usd"] != 2200000):
        raise ValueError("audit copy cannot activate or change an approval")
    attempts = db.execute("SELECT attempt_id,created_at,purpose,prompt_tokens,completion_cap,"
                          "reserved_micro_usd,actual_micro_usd,usage_known,settled "
                          "FROM attempts ORDER BY attempt_id").fetchall()
    if not attempts or len(attempts) > 180:
        raise ValueError("invalid cumulative audit history")
    for row in attempts:
        if (any(type(row[index]) is not int or row[index] < 0 for index in (3, 4, 5, 7, 8))
                or row[7] not in (0, 1) or row[8] not in (0, 1)
                or (row[6] is not None and (type(row[6]) is not int or row[6] < 0))
                or (row[7] == 1 and row[6] is None)
                or (row[7] == 0 and row[6] is not None)):
            raise ValueError("inconsistent original usage")
    reserved = sum(row[5] for row in attempts)
    actual = sum(row[6] or 0 for row in attempts)
    unknown = sum(row[7] != 1 or row[8] != 1 for row in attempts)
    budget = db.execute("SELECT attempts,reserved_micro_usd,actual_micro_usd,evaluation_started,"
                        "evaluation_remaining_calls,evaluation_remaining_micro_usd,halted "
                        "FROM budget WHERE singleton=1").fetchall()
    if budget != [(len(attempts), reserved, actual, 0, 0, 0, 1)]:
        raise ValueError("audit counters or halt disagree with original rows")
    totals = {"historical_attempts": len(attempts), "sql_reserved_micro_usd": reserved,
              "known_actual_micro_usd": actual, "unknown_attempts": unknown}
    if any(type(manifest.get(key)) is not int or manifest[key] != value for key, value in totals.items()):
        raise ValueError("audit manifest understates history or unknown exposure")
    pending = manifest.get("original_guard_pending_micro_usd")
    if type(pending) is not int or pending < 0 or (unknown and pending == 0):
        raise ValueError("unknown provider exposure must remain recorded")
    sources = db.execute("SELECT ledger_uuid,payload FROM audit_sources ORDER BY ledger_uuid").fetchall()
    mapping = db.execute("SELECT attempt_id,ledger_uuid FROM attempt_source ORDER BY attempt_id").fetchall()
    source_ids = {row[0] for row in sources}
    if ({row[0] for row in mapping} != {row[0] for row in attempts}
            or len(mapping) != len(attempts) or {row[1] for row in mapping} != source_ids):
        raise ValueError("every original request needs an exact source mapping")
    bindings = {}
    for ledger_uuid, raw in sources:
        source = json.loads(raw)
        pair = source.get("recorded_ledger", source.get("ledger"))
        if (source.get("ledger_uuid") != ledger_uuid or not isinstance(pair, list) or len(pair) != 2
                or any(type(value) is not int or value < 0 for value in pair)):
            raise ValueError("original device binding missing or inconsistent")
        bindings[ledger_uuid] = pair
    if db.execute("PRAGMA integrity_check").fetchone() != ("ok",):
        raise ValueError("audit SQLite integrity failed")
    schema = db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name").fetchall()
    return {"manifest": manifest, "attempts": attempts, "budget": budget,
            "sources": sources, "mapping": mapping, "schema": schema, "source_bindings": bindings}


def _audit_private(info, *, directory=False):
    required = stat.S_ISDIR if directory else stat.S_ISREG
    if (not required(info.st_mode) or info.st_uid != os.getuid()
            or stat.S_IMODE(info.st_mode) != (0o700 if directory else 0o600)
            or (not directory and info.st_nlink != 1)):
        raise ValueError("private owned audit object required")


def rehearse_audit_recovery(source, destination):
    """Copy locked accounting and record new file bindings without granting use.

    Original bindings remain evidence, not device exceptions. A failure retains
    a pending tombstone; retry requires a different, explicitly unused directory.
    """
    from contextlib import closing
    import uuid
    from urllib.parse import quote
    import authorized_budget_period as period
    import model_budget as accounting

    source, destination = Path(os.path.abspath(source)), Path(os.path.abspath(destination))
    if (destination.is_relative_to(accounting._authorization_slot())
            or destination.is_relative_to(accounting._state_db().parent)):
        raise ValueError("rehearsal cannot replace a runtime authorization slot")
    with period._parent(source) as source_directory, period._parent(destination) as parent:
        _audit_private(os.fstat(source_directory), directory=True)
        _audit_private(os.fstat(parent), directory=True)
        info = os.stat(source.name, dir_fd=source_directory, follow_symlinks=False)
        _audit_private(info)
        source_binding = [info.st_dev, info.st_ino]
        uri = f"file:/proc/self/fd/{source_directory}/{quote(source.name, safe='')}?mode=ro"
        with closing(sqlite3.connect(uri, uri=True, isolation_level=None)) as original:
            original.execute("BEGIN")
            snapshot = _audit_recovery_snapshot(original)
            os.mkdir(destination.name, 0o700, dir_fd=parent)
            directory = os.open(destination.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
            try:
                claim = period._file(directory, "recovery.pending.json", os.O_WRONLY | os.O_CREAT | os.O_EXCL)
                try:
                    period._write(claim, {"status": "PENDING_AUDIT_COPY", "paid_calls_enabled": False})
                finally:
                    os.close(claim)
                fd = period._file(directory, "budget.sqlite3", os.O_RDWR | os.O_CREAT | os.O_EXCL)
                copy_info = os.fstat(fd)
                os.close(fd)
                target_uri = f"file:/proc/self/fd/{directory}/budget.sqlite3?mode=rw"
                with closing(sqlite3.connect(target_uri, uri=True, isolation_level=None)) as copy:
                    original.backup(copy)
                    if _audit_recovery_snapshot(copy) != snapshot:
                        raise ValueError("recovery copy changed original accounting")
                    event = {"schema": "ulticode-audit-copy-recovery-v1", "operation_id": uuid.uuid4().hex,
                             "utc": datetime.now(timezone.utc).isoformat(), "status": "BLOCKED_AUDIT_ONLY",
                             "paid_calls_enabled": False, "authorization_applied": False,
                             "source_binding": source_binding, "copy_binding": [copy_info.st_dev, copy_info.st_ino],
                             "source_bindings": snapshot["source_bindings"],
                             "ledger_uuid": snapshot["manifest"]["ledger_uuid"],
                             "unknown_attempts": snapshot["manifest"]["unknown_attempts"],
                             "approved_cumulative_attempts": 180, "approved_cumulative_micro_usd": 2200000,
                             "runtime_device_exception": False}
                    copy.execute("PRAGMA synchronous=FULL")
                    copy.execute("BEGIN IMMEDIATE")
                    copy.execute("CREATE TABLE recovery_event(singleton INTEGER PRIMARY KEY CHECK(singleton=1),payload TEXT NOT NULL)")
                    copy.execute("INSERT INTO recovery_event VALUES(1,?)", (json.dumps(event, sort_keys=True),))
                    copy.commit()
                observed = os.stat(source.name, dir_fd=source_directory, follow_symlinks=False)
                if [observed.st_dev, observed.st_ino] != source_binding:
                    raise ValueError("source audit file replaced during copy")
                with period._parent(destination) as checked_parent:
                    if os.fstat(checked_parent) != os.fstat(parent):
                        raise ValueError("recovery parent replaced during copy")
                if os.stat(destination.name, dir_fd=parent, follow_symlinks=False) != os.fstat(directory):
                    raise ValueError("recovery directory replaced during copy")
                marker = period._file(directory, "recovery.json", os.O_WRONLY | os.O_CREAT | os.O_EXCL)
                try:
                    period._write(marker, event)
                finally:
                    os.close(marker)
                os.unlink("recovery.pending.json", dir_fd=directory)
                os.fsync(directory)
                os.fsync(parent)
                return event
            finally:
                os.close(directory)


# Offline recovery-source validation. A snapshot digest proves recorded content
# only: it never proves that a live physical source ledger, guard journal or
# approval still exists on the recorded device, so no caller-supplied
# provenance is trusted without re-derivation against the snapshot itself.
_LEDGER_ID = re.compile(r"[0-9a-f]{32}")
_SHA256 = re.compile(r"[0-9a-f]{64}")
_PERIOD_ID_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
_MAX_RECOVERY_ATTEMPTS = 180
_MAX_PROVENANCE_BYTES = 4096
_MAX_ROW_TEXT = 128
_MAX_SCHEMA_ROWS = 256
_SCHEMA_COLUMNS = 8
_MAX_SCHEMA_TEXT = 16384
_MAX_MANIFEST_FIELDS = 32
_MAX_MANIFEST_TEXT = 256
_RECOVERY_ROW_FIELDS = 9
_RECOVERY_BUDGET_FIELDS = 7
_MAX_LEDGER_TEXT = 64
# Reviewed field catalogues for the recorded guard snapshot: unknown fields are
# refused so a caller cannot smuggle an unbounded payload past the digest.
_GUARD_STATE_KEYS = ("config_sha256", "continuation_run", "halted", "limit_micro_usd",
                     "pending_micro_usd", "period_identity", "policy", "receipts",
                     "settled_peak_micro_usd")
_GUARD_RECEIPT_KEYS = ("completion_token_cap", "completion_tokens", "finished_at_utc", "lane",
                       "peak_micro_usd", "prompt_token_cap", "prompt_tokens", "reason",
                       "request_bytes", "request_model", "request_sha256", "reserved_micro_usd",
                       "response_model", "started_at_utc", "status", "total_tokens")
_MAX_LANE_TEXT = 64
# Exactly the fields the unknown-row binding declares; extras are refused so the
# binding cannot smuggle an unexamined field alongside the checked ones.
_UNKNOWN_MATCH_KEYS = ("attempt_id", "config_sha256", "ledger_uuid", "pending_micro_usd",
                       "period_identity", "request_sha256")
_MAX_GUARD_TEXT = 256
# Finite ceiling for every recorded integer: JSON-exact, so magnitudes are
# bounded (not just type-checked) before serialization and arithmetic.
_MAX_RECOVERY_MAGNITUDE = 2 ** 53
# Private mint token: evidence is only produced by the validator below.
_RECOVERY_EVIDENCE_TOKEN = object()
# The contract fields every recovery snapshot must carry; the locked audit
# producer may add its bounded `manifest` summary alongside them.
_RECOVERY_SNAPSHOT_KEYS = ("attempts", "budget", "mapping", "request_bindings", "schema",
                           "source_bindings", "sources")


def _recovery_digest(value):
    try:
        raw = json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    except (TypeError, ValueError):
        raise ValueError("recovery snapshot is not a plain JSON record") from None
    return hashlib.sha256(raw).hexdigest()


def _bounded_count(value):
    return type(value) is int and 0 <= value <= _MAX_RECOVERY_MAGNITUDE


def _frozen_metadata(value):
    # Deep-freeze metadata: mappings become read-only views and every sequence
    # becomes a tuple, so the evidence keeps no mutable alias of its inputs.
    if isinstance(value, Mapping):
        return MappingProxyType({key: _frozen_metadata(item) for key, item in value.items()})
    if isinstance(value, (list, tuple)):
        return tuple(_frozen_metadata(item) for item in value)
    return copy.deepcopy(value)


@dataclass(frozen=True)
class RecoverySourceEvidence:
    """Frozen offline evidence of recorded content; mappings are read-only.

    Metadata mappings are read-only and sequences become tuples, so neither
    later input mutation nor mutation through an evidence leaf changes it.
    """

    historical_attempts: int
    known_attempts: int
    unknown_attempts: int
    known_actual_micro_usd: int
    pending_micro_usd: int
    unknown_attempt_id: str
    source_attempt_counts: Mapping
    source_sha256_by_ledger: Mapping
    source_provenance_by_ledger: Mapping
    guard_source_sha256: str
    unknown_guard_match: Mapping
    _origin: object = field(default=None, repr=False, compare=False)

    def __post_init__(self):
        # Only validate_recovery_sources may mint evidence; a hand-built instance
        # would otherwise bypass every re-derived row, receipt and digest check.
        if self._origin is not _RECOVERY_EVIDENCE_TOKEN:
            raise ValueError("recovery evidence must come from validate_recovery_sources")


def _recovery_provenance(ledger_uuid, raw, source_bindings):
    try:
        provenance = json.loads(raw)
    except (TypeError, ValueError):
        raise ValueError("recovery provenance is not valid JSON") from None
    if (not isinstance(ledger_uuid, str) or not _LEDGER_ID.fullmatch(ledger_uuid)
            or not isinstance(provenance, dict) or provenance.get("ledger_uuid") != ledger_uuid
            or not isinstance(provenance.get("period_id"), str)
            or not _PERIOD_ID_PATTERN.fullmatch(provenance["period_id"])
            or not isinstance(provenance.get("identity"), str)
            or not _LEDGER_ID.fullmatch(provenance["identity"])
            or not isinstance(provenance.get("config_sha256"), str)
            or not _SHA256.fullmatch(provenance["config_sha256"])):
        raise ValueError("recovery provenance identity or ledger drift")
    pair = provenance.get("recorded_ledger", provenance.get("ledger"))
    if (not isinstance(pair, list) or len(pair) != 2
            or any(not _bounded_count(value) for value in pair)
            or not _bounded_count(provenance.get("actual_device"))
            or provenance["actual_device"] != pair[0]
            or source_bindings != {ledger_uuid: pair}):
        raise ValueError("recovery device binding missing or inconsistent")
    return provenance


def _bounded_scalar(value):
    # Guard fields are bounded strings, bounded non-negative integers, bool or None.
    if value is None or type(value) is bool:
        return True
    if type(value) is int:
        return 0 <= value <= _MAX_RECOVERY_MAGNITUDE
    return isinstance(value, str) and len(value) <= _MAX_GUARD_TEXT


def _bounded_schema(schema):
    for row in schema:
        if (not isinstance(row, (list, tuple)) or len(row) > _SCHEMA_COLUMNS
                or any(value is not None and (not isinstance(value, str)
                                              or len(value) > _MAX_SCHEMA_TEXT)
                       for value in row)):
            raise ValueError("recovery schema listing is unbounded")
    return schema


def _bounded_manifest(manifest):
    if not isinstance(manifest, dict) or len(manifest) > _MAX_MANIFEST_FIELDS:
        raise ValueError("recovery manifest missing or unbounded")
    if any(not isinstance(key, str) or not 1 <= len(key) <= _MAX_MANIFEST_TEXT for key in manifest):
        raise ValueError("recovery manifest keys missing or unbounded")
    for value in manifest.values():
        if isinstance(value, str):
            if len(value) > _MAX_MANIFEST_TEXT:
                raise ValueError("recovery manifest missing or unbounded")
        elif value is not None and type(value) not in (int, bool):
            raise ValueError("recovery manifest missing or unbounded")
    return manifest


def validate_recovery_sources(sources, guard, unknown_match):
    """Re-derive every recovered row, total, ledger id, guard receipt and digest.

    Accepts only bounded per-ledger projections whose digests, counters, source
    mappings, explicit request-binding crosswalk, provenance and device bindings
    agree exactly; the single unknown row must bind to exactly one guard receipt
    through that crosswalk. Each element of `sources` carries exactly one ledger:
    a whole-ledger audit snapshot that lists several ledgers must first be split
    by ledger id, because one ledger per element is required. The crosswalk is
    caller-supplied conditional input -- the ledger tables hold no request hash
    -- so these checks prove internal consistency with the recorded receipts
    only. They never prove that a real request-body hash, a live physical
    source, the guard journal or an unspent approval has been recovered.
    """
    if (not isinstance(sources, (list, tuple)) or not sources or len(sources) > _MAX_RECOVERY_ATTEMPTS
            or not isinstance(guard, dict) or not isinstance(unknown_match, dict)):
        raise ValueError("recovery snapshots, guard snapshot and unknown binding required")
    if set(unknown_match) != set(_UNKNOWN_MATCH_KEYS):
        raise ValueError("unknown binding must declare exactly its six fields")
    state = guard.get("state")
    if (not isinstance(state, dict) or not set(state) <= set(_GUARD_STATE_KEYS)
            or not isinstance(state.get("receipts"), (list, tuple))
            or not state["receipts"] or len(state["receipts"]) > _MAX_RECOVERY_ATTEMPTS):
        raise ValueError("guard snapshot declares missing or unbounded fields")
    if state.get("halted") is not True:
        raise ValueError("unknown recovery guard must remain halted")
    for key, value in state.items():
        if key not in ("receipts", "policy", "continuation_run") and not _bounded_scalar(value):
            raise ValueError("guard snapshot declares out-of-range fields")
    for key in ("policy", "continuation_run"):
        if key in state:
            _bounded_manifest(state[key])
    for receipt in state["receipts"]:
        peak_micro_usd = receipt.get("peak_micro_usd") if isinstance(receipt, dict) else None
        if (not isinstance(receipt, dict) or not set(receipt) <= set(_GUARD_RECEIPT_KEYS)
                or not isinstance(receipt.get("lane"), str)
                or not 1 <= len(receipt["lane"]) <= _MAX_LANE_TEXT
                or (peak_micro_usd is not None and not _bounded_count(peak_micro_usd))
                or any(not _bounded_scalar(value) for value in receipt.values())):
            raise ValueError("guard receipt declares missing or unbounded fields")
    if (not isinstance(guard.get("source_sha256"), str)
            or not _SHA256.fullmatch(guard["source_sha256"])
            or _recovery_digest(state) != guard["source_sha256"]):
        raise ValueError("guard snapshot digest drifted")

    attempt_ids = set()
    manifest_pending = []
    rows_by_ledger, counts, sha_by_ledger, provenance_by_ledger = {}, {}, {}, {}
    request_bindings_by_ledger = {}
    for source in sources:
        if not isinstance(source, dict) or not isinstance(source.get("snapshot"), dict):
            raise ValueError("snapshot-shaped recovery source required")
        snapshot = source["snapshot"]
        if not set(_RECOVERY_SNAPSHOT_KEYS) <= set(snapshot) <= set(_RECOVERY_SNAPSHOT_KEYS) | {"manifest"}:
            raise ValueError("recovery snapshot must declare exactly the bounded fields")
        if "manifest" in snapshot:
            manifest = _bounded_manifest(snapshot["manifest"])
            if "original_guard_pending_micro_usd" in manifest:
                declared_pending = manifest["original_guard_pending_micro_usd"]
                if not _bounded_count(declared_pending):
                    raise ValueError("recovery manifest pending exposure missing or unbounded")
                manifest_pending.append(declared_pending)
        attempts, mapping, budget = snapshot["attempts"], snapshot["mapping"], snapshot["budget"]
        declared, schema = snapshot["sources"], snapshot["schema"]
        if (not isinstance(attempts, (list, tuple)) or not attempts
                or len(attempts) > _MAX_RECOVERY_ATTEMPTS
                or len(attempt_ids) + len(attempts) > _MAX_RECOVERY_ATTEMPTS
                or not isinstance(mapping, (list, tuple)) or len(mapping) != len(attempts)
                or not isinstance(budget, (list, tuple)) or len(budget) != 1
                or not isinstance(budget[0], (list, tuple))
                or not isinstance(declared, (list, tuple)) or len(declared) != 1
                or not isinstance(schema, (list, tuple)) or len(schema) > _MAX_SCHEMA_ROWS):
            raise ValueError("recovery snapshot is missing or unbounded")
        _bounded_schema(schema)
        for row in attempts:
            if (not isinstance(row, (list, tuple)) or len(row) != _RECOVERY_ROW_FIELDS
                    or any(not isinstance(value, str) or not value or len(value) > _MAX_ROW_TEXT
                           for value in row[:3])
                    or any(not _bounded_count(value) for value in row[3:6])):
                raise ValueError("recovery attempt row is missing or unbounded")
        for entry_ in mapping:
            if (not isinstance(entry_, (list, tuple)) or len(entry_) != 2
                    or not isinstance(entry_[0], str) or not entry_[0]
                    or len(entry_[0]) > _MAX_ROW_TEXT
                    or not isinstance(entry_[1], str) or not entry_[1]
                    or len(entry_[1]) > _MAX_LEDGER_TEXT):
                raise ValueError("recovery source mapping is missing or unbounded")
        if len(budget[0]) != _RECOVERY_BUDGET_FIELDS or any(
                not _bounded_count(value) for value in budget[0]):
            raise ValueError("recovery counters are missing or unbounded")
        bindings = snapshot["source_bindings"]
        if (not isinstance(bindings, dict) or not 1 <= len(bindings) <= 4
                or any(not isinstance(key, str) or not 1 <= len(key) <= _MAX_LEDGER_TEXT
                       for key in bindings)
                or any(not isinstance(value, (list, tuple)) or len(value) != 2
                       or any(not _bounded_count(item) for item in value)
                       for value in bindings.values())):
            raise ValueError("recovery source bindings are missing or unbounded")
        request_bindings = snapshot["request_bindings"]
        if (not isinstance(request_bindings, dict) or len(request_bindings) != len(attempts)
                or any(not isinstance(key, str) or not key or len(key) > _MAX_ROW_TEXT
                       for key in request_bindings)
                or any(not isinstance(value, str) or not _SHA256.fullmatch(value)
                       for value in request_bindings.values())):
            raise ValueError("recovery request bindings are missing or unbounded")
        entry = declared[0]
        if (not isinstance(entry, (list, tuple)) or len(entry) != 2
                or not isinstance(entry[0], str) or not 1 <= len(entry[0]) <= _MAX_LEDGER_TEXT):
            raise ValueError("one ledger provenance per recovery source required")
        ledger_uuid, raw = entry
        if (ledger_uuid in rows_by_ledger or not isinstance(raw, str)
                or len(raw) > _MAX_PROVENANCE_BYTES):
            raise ValueError("recovery ledger or provenance missing or unbounded")
        if (not isinstance(source.get("source_sha256"), str)
                or not _SHA256.fullmatch(source["source_sha256"])
                or _recovery_digest(snapshot) != source["source_sha256"]):
            raise ValueError("recovery source digest drifted")
        provenance = _recovery_provenance(ledger_uuid, raw, snapshot["source_bindings"])
        rows = []
        for row in attempts:
            if (not isinstance(row, (list, tuple)) or len(row) != _RECOVERY_ROW_FIELDS
                    or not isinstance(row[0], str) or not row[0] or row[0] in attempt_ids
                    or not isinstance(row[1], str) or not row[1]
                    or not isinstance(row[2], str) or not row[2]):
                raise ValueError("recovery attempt row or identifier drift")
            if any(not _bounded_count(row[index]) for index in (3, 4, 5)):
                raise ValueError("recovery attempt token or reservation drift")
            if (type(row[7]) is not int or row[7] not in (0, 1)
                    or type(row[8]) is not int or row[8] not in (0, 1)
                    or (row[6] is not None and not _bounded_count(row[6]))
                    or (row[7] == 1 and row[8] == 1) != (row[6] is not None)):
                raise ValueError("inconsistent recovered usage")
            attempt_ids.add(row[0])
            rows.append(row)
        mapped = set()
        for entry in mapping:
            if (not isinstance(entry, (list, tuple)) or len(entry) != 2
                    or not isinstance(entry[0], str) or entry[1] != ledger_uuid):
                raise ValueError("every recovered attempt needs an exact one-ledger mapping")
            mapped.add(entry[0])
        if mapped != {row[0] for row in rows}:
            raise ValueError("every recovered attempt needs an exact one-ledger mapping")
        if set(request_bindings) != {row[0] for row in rows}:
            raise ValueError("every recovered attempt needs an explicit request binding")
        if len(set(request_bindings.values())) != len(rows):
            raise ValueError("recovery requires an unambiguous per-attempt request crosswalk")
        if tuple(budget[0]) != (len(rows), sum(row[5] for row in rows),
                                sum(row[6] or 0 for row in rows), 0, 0, 0, 1):
            raise ValueError("recovery counters disagree with recovered rows")
        rows_by_ledger[ledger_uuid] = rows
        counts[ledger_uuid] = len(rows)
        sha_by_ledger[ledger_uuid] = source["source_sha256"]
        provenance_by_ledger[ledger_uuid] = provenance
        request_bindings_by_ledger[ledger_uuid] = request_bindings
    if not 1 <= len(attempt_ids) <= _MAX_RECOVERY_ATTEMPTS:
        raise ValueError("invalid cumulative recovery history")

    all_rows = [row for rows in rows_by_ledger.values() for row in rows]
    unknown_rows = [row for row in all_rows if not (row[7] == 1 and row[8] == 1)]
    if len(unknown_rows) != 1:
        raise ValueError("exactly one unknown recovered attempt required")

    guard_ledgers = [ledger_uuid for ledger_uuid, provenance in provenance_by_ledger.items()
                     if provenance["identity"] == state.get("period_identity")
                     and provenance["config_sha256"] == state.get("config_sha256")]
    if len(guard_ledgers) != 1:
        raise ValueError("exactly one recovered ledger must match the guard period")
    guard_ledger = guard_ledgers[0]
    receipts = state.get("receipts")
    if not isinstance(receipts, (list, tuple)) or not receipts:
        raise ValueError("guard receipts missing")
    by_request = {}
    for receipt in receipts:
        if (not isinstance(receipt, dict) or not isinstance(receipt.get("request_sha256"), str)
                or not _SHA256.fullmatch(receipt["request_sha256"])
                or not _bounded_count(receipt.get("reserved_micro_usd"))
                or receipt["request_sha256"] in by_request):
            raise ValueError("guard receipt drift")
        by_request[receipt["request_sha256"]] = receipt
    guard_rows = rows_by_ledger[guard_ledger]
    guard_bindings = request_bindings_by_ledger[guard_ledger]
    if not guard_bindings:
        raise ValueError("guard ledger has no explicit request binding crosswalk")
    if set(by_request) != {guard_bindings[row[0]] for row in guard_rows}:
        raise ValueError("guard receipts disagree with the recovered request bindings")
    settled_peak_micro_usd = pending_micro_usd = 0
    for row in guard_rows:
        receipt = by_request[guard_bindings[row[0]]]
        is_known = row[7] == 1 and row[8] == 1
        if (receipt.get("status") != ("settled" if is_known else "unknown_or_unsafe")
                or receipt.get("lane") != row[2]
                or receipt.get("peak_micro_usd") != row[6]):
            raise ValueError("guard receipt disagrees with its recovered attempt")
        if is_known:
            settled_peak_micro_usd += row[6]
        else:
            pending_micro_usd += receipt["reserved_micro_usd"]
    if (state.get("settled_peak_micro_usd") != settled_peak_micro_usd
            or state.get("pending_micro_usd") != pending_micro_usd):
        raise ValueError("guard exposure disagrees with the recovered rows")
    if any(declared_pending != pending_micro_usd for declared_pending in manifest_pending):
        raise ValueError("recovered manifest exposure disagrees with the guard")

    unknown_row = unknown_rows[0]
    if (unknown_row[0] not in {row[0] for row in guard_rows}
            or unknown_match.get("ledger_uuid") != guard_ledger
            or unknown_match.get("attempt_id") != unknown_row[0]
            or unknown_match.get("request_sha256") != guard_bindings[unknown_row[0]]
            or unknown_match.get("period_identity") != state.get("period_identity")
            or unknown_match.get("config_sha256") != state.get("config_sha256")
            or unknown_match.get("pending_micro_usd") != pending_micro_usd):
        raise ValueError("unknown attempt is not bound to its unique guard receipt")

    return RecoverySourceEvidence(
        historical_attempts=len(attempt_ids),
        known_attempts=len(attempt_ids) - len(unknown_rows),
        unknown_attempts=len(unknown_rows),
        known_actual_micro_usd=sum(row[6] or 0 for row in all_rows),
        pending_micro_usd=pending_micro_usd,
        unknown_attempt_id=unknown_row[0],
        source_attempt_counts=MappingProxyType(dict(counts)),
        source_sha256_by_ledger=MappingProxyType(dict(sha_by_ledger)),
        source_provenance_by_ledger=MappingProxyType(
            {ledger: _frozen_metadata(provenance) for ledger, provenance in provenance_by_ledger.items()}),
        guard_source_sha256=guard["source_sha256"],
        unknown_guard_match=_frozen_metadata(unknown_match),
        _origin=_RECOVERY_EVIDENCE_TOKEN,
    )
