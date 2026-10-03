"""Lifecycle metadata only: runtime_accounting_connected=False, spend_limit_enforced=False.

Explicit paths and preparation are required. Active metadata grants no spending
allowance. Legacy accounting is never read; its historical usage remains UNKNOWN.
"""

from contextlib import contextmanager
from dataclasses import dataclass
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import uuid
from types import MappingProxyType

POLICY_ID = "dav58-dav53-v1"
POLICY = MappingProxyType({
    "limit_micro_usd": 1_000_000,
    "attempts": 78,
    "prompt_token_cap": 24_000,
    "lanes": MappingProxyType({
        "dav58_loop": MappingProxyType({"attempts": 24, "completion_token_cap": 2000, "rounds": 4}),
        "dav58_judge": MappingProxyType({"attempts": 42, "completion_token_cap": 2000, "rounds": 1}),
        "dav53_scenarios": MappingProxyType({"attempts": 12, "completion_token_cap": 1000, "rounds": 4}),
    }),
})


class PeriodError(ValueError):
    pass


@dataclass(frozen=True)
class PeriodIdentity:
    period_id: str
    config_sha256: str
    identity: str
    policy_id: str = POLICY_ID


@dataclass(frozen=True)
class PeriodSnapshot:
    """Metadata only: runtime_accounting_connected=False, spend_limit_enforced=False."""

    identity: PeriodIdentity
    state: str
    legacy_history: str = "UNKNOWN"
    runtime_accounting_connected: bool = False
    spend_limit_enforced: bool = False


def _validate(period_id: str, config_sha256: str, policy_id: str) -> None:
    if not isinstance(period_id, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", period_id):
        raise PeriodError("invalid period ID")
    if not isinstance(config_sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", config_sha256):
        raise PeriodError("config must be a lower-case SHA256")
    if policy_id != POLICY_ID:
        raise PeriodError("unsupported policy")


@contextmanager
def _parent(path: Path):
    # Walk with dirfds: neither parent components nor final files may be symlinks.
    if not path.is_absolute() or ".." in path.parts or path.name in ("", ".") or path == Path("/"):
        raise PeriodError("explicit absolute period directory required")
    fd = os.open("/", os.O_RDONLY | os.O_DIRECTORY)
    try:
        for part in path.parts[1:-1]:
            next_fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = next_fd
        yield fd
    finally:
        os.close(fd)


def _file(directory: int, name: str, flags: int) -> int:
    fd = os.open(name, flags | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600, dir_fd=directory)
    if not stat.S_ISREG(os.fstat(fd).st_mode) or os.fstat(fd).st_nlink != 1:
        os.close(fd)
        raise PeriodError("regular, unlinked-to-other-paths metadata required")
    return fd


def _write(fd: int, value: dict) -> None:
    data = (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()
    while data:
        size = os.write(fd, data)
        if size <= 0:
            raise PeriodError("incomplete metadata write")
        data = data[size:]
    os.fsync(fd)


def _read(fd: int) -> list[dict]:
    os.lseek(fd, 0, os.SEEK_SET)
    with os.fdopen(os.dup(fd), "rb") as stream:
        data = stream.read(16_385)
    if not data or len(data) > 16_384 or not data.endswith(b"\n"):
        raise PeriodError("missing or incomplete metadata")
    try:
        return [json.loads(line) for line in data.splitlines()]
    except (ValueError, UnicodeError) as exc:
        raise PeriodError("invalid metadata") from exc


def prepare_period(path: Path, period_id: str, config_sha256: str) -> PeriodSnapshot:
    """Prepare only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    _validate(period_id, config_sha256, POLICY_ID)
    path = Path(path)
    identity = PeriodIdentity(period_id, config_sha256, uuid.uuid4().hex)
    try:
        with _parent(path) as parent:
            # Retain this one-shot tombstone even if a later write fails.
            os.mkdir(path.name, 0o700, dir_fd=parent)
            directory = os.open(path.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
            try:
                state = _file(directory, "state.jsonl", os.O_RDWR | os.O_CREAT | os.O_EXCL)
                try:
                    _write(state, {"state": "prepared"})
                    marker = _file(directory, "identity.json", os.O_WRONLY | os.O_CREAT | os.O_EXCL)
                    try:
                        _write(marker, {
                            **identity.__dict__, "path": str(path),
                            "directory": [os.fstat(directory).st_dev, os.fstat(directory).st_ino],
                            "state_file": [os.fstat(state).st_dev, os.fstat(state).st_ino],
                        })
                    finally:
                        os.close(marker)
                    os.fsync(directory)
                    os.fsync(parent)
                finally:
                    os.close(state)
            finally:
                os.close(directory)
    except OSError as exc:
        raise PeriodError("period cannot be prepared; existing directories are never reset") from exc
    return PeriodSnapshot(identity, "prepared")


@dataclass
class _LockedPeriod:
    snapshot: PeriodSnapshot
    state_fd: int
    directory_fd: int
    parent_fd: int
    path: Path

    def confirm(self) -> None:
        with _parent(self.path) as parent:
            if (os.fstat(parent).st_dev, os.fstat(parent).st_ino) != (os.fstat(self.parent_fd).st_dev, os.fstat(self.parent_fd).st_ino):
                raise PeriodError("period parent path replaced")
        if os.stat("state.jsonl", dir_fd=self.directory_fd, follow_symlinks=False) != os.fstat(self.state_fd):
            raise PeriodError("state file replaced")
        current = os.stat(self.path.name, dir_fd=self.parent_fd, follow_symlinks=False)
        pinned = os.fstat(self.directory_fd)
        if (current.st_dev, current.st_ino) != (pinned.st_dev, pinned.st_ino):
            raise PeriodError("period directory replaced")
        os.fsync(self.state_fd)

    def append(self, target: str) -> None:
        if (self.snapshot.state, target) not in (
            ("prepared", "active"), ("prepared", "halted"), ("active", "halted"),
        ):
            raise PeriodError("invalid lifecycle transition")
        self.confirm()
        os.lseek(self.state_fd, 0, os.SEEK_END)
        _write(self.state_fd, {"state": target})
        self.snapshot = PeriodSnapshot(self.snapshot.identity, target)


@contextmanager
def _locked_period(path: Path, expected: PeriodIdentity, *, exclusive: bool = True):
    if not isinstance(expected, PeriodIdentity):
        raise PeriodError("expected identity required")
    _validate(expected.period_id, expected.config_sha256, expected.policy_id)
    if not isinstance(expected.identity, str) or not re.fullmatch(r"[0-9a-f]{32}", expected.identity):
        raise PeriodError("invalid identity")
    path = Path(path)
    try:
        with _parent(path) as parent:
            directory = os.open(path.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
            try:
                state = _file(directory, "state.jsonl", os.O_RDWR if exclusive else os.O_RDONLY)
                try:
                    fcntl.flock(state, fcntl.LOCK_EX if exclusive else fcntl.LOCK_SH)
                    marker = _file(directory, "identity.json", os.O_RDONLY)
                    try:
                        records = _read(marker)
                    finally:
                        os.close(marker)
                    required = {
                        **expected.__dict__, "path": str(path),
                        "directory": [os.fstat(directory).st_dev, os.fstat(directory).st_ino],
                        "state_file": [os.fstat(state).st_dev, os.fstat(state).st_ino],
                    }
                    if records != [required]:
                        raise PeriodError("period identity, config, policy or path drift")
                    states = _read(state)
                    valid = [[{"state": s} for s in history] for history in (
                        ("prepared",), ("prepared", "active"),
                        ("prepared", "halted"), ("prepared", "active", "halted"),
                    )]
                    if states not in valid:
                        raise PeriodError("invalid lifecycle history")
                    yield _LockedPeriod(PeriodSnapshot(expected, states[-1]["state"]), state, directory, parent, path)
                finally:
                    os.close(state)
            finally:
                os.close(directory)
    except OSError as exc:
        raise PeriodError("period metadata unavailable; no files created or repaired") from exc


def _access(path: Path, expected: PeriodIdentity, target: str | None) -> PeriodSnapshot:
    with _locked_period(path, expected, exclusive=target is not None) as locked:
        if target:
            locked.append(target)
        return locked.snapshot


def read_period(path: Path, expected: PeriodIdentity) -> PeriodSnapshot:
    """Read only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    return _access(path, expected, None)


def activate_period(path: Path, expected: PeriodIdentity) -> PeriodSnapshot:
    """Activate metadata only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    return _access(path, expected, "active")


def halt_period(path: Path, expected: PeriodIdentity) -> PeriodSnapshot:
    """Halt metadata only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    return _access(path, expected, "halted")
