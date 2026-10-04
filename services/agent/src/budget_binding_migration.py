"""Explicit, one-shot device identity migration; never edits original bindings."""
import fcntl
import hashlib
import json
import os
import sqlite3
import stat
import subprocess
import re
from datetime import datetime, timezone
from pathlib import Path

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
