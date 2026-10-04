"""Process-shared hard budget for explicitly authorized DeepSeek calls."""

from __future__ import annotations

import os
import hashlib
import json
import pwd
import sqlite3
import stat
import uuid
from datetime import datetime, timezone
from dataclasses import dataclass
from contextlib import contextmanager
from typing import Iterator
from pathlib import Path

import authorized_budget_period as period

MAX_ATTEMPTS = 200
MAX_MICRO_USD = 2_000_000
MAX_PROMPT_TOKENS = 24_000
MAX_COMPLETION_TOKENS = 4_000
EVALUATION_RESERVE_ATTEMPTS = 60
MODEL_ALIAS = "deepseek-flash"
INPUT_RATE_TENTHS = 3
OUTPUT_RATE_TENTHS = 12


class BudgetLimitExceeded(RuntimeError):
    """Request was not sent because shared budget could not reserve its ceiling."""


@dataclass(frozen=True)
class Reservation:
    attempt_id: str
    reserved_micro_usd: int
    period_identity: str | None = None
    config_sha256: str | None = None
    purpose: str | None = None


def _ceil_tenths(value: int) -> int:
    return (value + 9) // 10


def worst_case_micro_usd(prompt_tokens: int, completion_tokens: int) -> int:
    if prompt_tokens < 0 or completion_tokens < 0:
        raise ValueError("token counts must be non-negative")
    # Peak cache-miss rates: input $0.30/M, output $1.20/M.
    return _ceil_tenths(prompt_tokens * INPUT_RATE_TENTHS + completion_tokens * OUTPUT_RATE_TENTHS)


def authorized_period_config() -> dict:
    return {
        "schema": "ulticode-authorized-budget-v1", "policy_id": period.POLICY_ID,
        "model_alias": MODEL_ALIAS,
        "policy": {**period.POLICY, "lanes": {k: dict(v) for k, v in period.POLICY["lanes"].items()}},
        "pricing": {"input_micro_usd_tenths_per_token": INPUT_RATE_TENTHS,
                    "output_micro_usd_tenths_per_token": OUTPUT_RATE_TENTHS,
                    "rounding": "ceil(sum/10)", "source": "inherited-model-budget-v1"},
    }


def _json(value: dict) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def authorized_period_config_sha256() -> str:
    return hashlib.sha256(_json(authorized_period_config()).encode()).hexdigest()


def _authorization_slot() -> Path:
    # Fixed OS identity, independent of HOME/XDG and caller-selected paths.
    return Path(pwd.getpwuid(os.getuid()).pw_dir) / ".local/state/ulticode/dav58-dav53-v1"


def _inode(fd: int) -> list[int]:
    info = os.fstat(fd)
    return [info.st_dev, info.st_ino]


def _ledger_inode(directory: int) -> list[int]:
    # stat does not open or close a ledger fd outside SQLite's Unix VFS.
    info = os.stat("budget.sqlite3", dir_fd=directory, follow_symlinks=False)
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        raise period.PeriodError("regular single-link ledger required")
    return [info.st_dev, info.st_ino]


def _state_db() -> Path:
    state_home = os.environ.get("XDG_STATE_HOME")
    root = Path(state_home) if state_home else Path.home() / ".local" / "state"
    if not root.is_absolute():
        raise ValueError("XDG_STATE_HOME must be absolute")
    return root / "ulticode" / "first-delivery" / "model-budget.sqlite3"


def _initialize_tables(connection: sqlite3.Connection) -> None:
    connection.execute(
        "CREATE TABLE IF NOT EXISTS budget ("
        "singleton INTEGER PRIMARY KEY CHECK(singleton=1), "
        "attempts INTEGER NOT NULL, reserved_micro_usd INTEGER NOT NULL, "
        "actual_micro_usd INTEGER NOT NULL, evaluation_started INTEGER NOT NULL DEFAULT 0, "
        "evaluation_remaining_calls INTEGER NOT NULL DEFAULT 0, "
        "evaluation_remaining_micro_usd INTEGER NOT NULL DEFAULT 0, "
        "halted INTEGER NOT NULL DEFAULT 0)"
    )
    columns = {row[1] for row in connection.execute("PRAGMA table_info(budget)")}
    for name, declaration in (
        ("evaluation_started", "INTEGER NOT NULL DEFAULT 0"),
        ("evaluation_remaining_calls", "INTEGER NOT NULL DEFAULT 0"),
        ("evaluation_remaining_micro_usd", "INTEGER NOT NULL DEFAULT 0"),
        ("halted", "INTEGER NOT NULL DEFAULT 0"),
    ):
        if name not in columns:
            connection.execute(f"ALTER TABLE budget ADD COLUMN {name} {declaration}")
    connection.execute(
        "INSERT OR IGNORE INTO budget(singleton,attempts,reserved_micro_usd,actual_micro_usd) "
        "VALUES (1, 0, 0, 0)"
    )
    connection.execute(
        "CREATE TABLE IF NOT EXISTS attempts ("
        "attempt_id TEXT PRIMARY KEY, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        "purpose TEXT NOT NULL, prompt_tokens INTEGER NOT NULL, "
        "completion_cap INTEGER NOT NULL, reserved_micro_usd INTEGER NOT NULL, "
        "actual_micro_usd INTEGER, usage_known INTEGER NOT NULL DEFAULT 0, "
        "settled INTEGER NOT NULL DEFAULT 0)"
    )



# One explicitly authorized continuation; base policy/identity stay immutable.
DAV58_CONTINUATION_PLAN = {"dav58_loop": 24, "dav58_judge": 19}


def _continuation(db, identity):
    if not db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='dav58_continuation'").fetchone():
        return None
    rows = db.execute("SELECT payload,sha256 FROM dav58_continuation").fetchall()
    if len(rows) != 1:
        raise period.PeriodError("invalid continuation audit count")
    raw, digest = rows[0]
    event = json.loads(raw)
    if (hashlib.sha256(raw.encode()).hexdigest() != digest
            or set(event) != {"schema", "identity", "authorization", "utc", "before", "after", "plan", "usage_before", "effective_config_sha256"}
            or event["schema"] != "dav58-one-continuation-v1"
            or event["identity"] != identity.__dict__
            or event["before"] != 24 or event["after"] != 48
            or event["plan"] != DAV58_CONTINUATION_PLAN
            or not isinstance(event["authorization"], str) or not event["authorization"].strip()
            or not isinstance(event["utc"], str)):
        raise period.PeriodError("invalid continuation audit")
    config = {"base_config_sha256": identity.config_sha256, "loop_attempt_limit": 48,
              "plan": DAV58_CONTINUATION_PLAN, "prompt_cap": 8000, "completion_cap": 2000}
    if event["effective_config_sha256"] != hashlib.sha256(_json(config).encode()).hexdigest():
        raise period.PeriodError("continuation configuration drift")
    usage = event["usage_before"]
    if (not isinstance(usage, dict) or set(usage) != {"attempts", "reserved_micro_usd", "actual_micro_usd"}
            or any(type(v) is not int or v < 0 for v in usage.values())):
        raise period.PeriodError("invalid continuation baseline")
    try:
        parsed_utc = datetime.fromisoformat(event["utc"])
        if parsed_utc.utcoffset() != timezone.utc.utcoffset(parsed_utc):
            raise ValueError("not UTC")
    except (ValueError, TypeError):
        raise period.PeriodError("invalid continuation timestamp") from None
    return event


class ModelBudget:
    """Atomic cross-process request and worst-case cost accounting.

    Reservations are never refunded. At least 60 maximum-sized calls remain
    unavailable to non-evaluation work until the frozen evaluation starts.
    """

    def __init__(self, path: Path | None = None) -> None:
        self._identity: period.PeriodIdentity | None = None
        self.path = path or _state_db()
        if not self.path.is_absolute():
            raise ValueError("budget path must be absolute")

    @classmethod
    def bound(cls, expected: period.PeriodIdentity) -> ModelBudget:
        if not isinstance(expected, period.PeriodIdentity) or expected.config_sha256 != authorized_period_config_sha256():
            raise period.PeriodError("canonical configuration identity required")
        instance = cls.__new__(cls)
        instance._identity = expected
        instance.path = _authorization_slot() / "accounting/budget.sqlite3"
        with instance._accounting():
            pass
        return instance

    @classmethod
    def bind_prepared(cls, expected: period.PeriodIdentity) -> ModelBudget:
        if not isinstance(expected, period.PeriodIdentity) or expected.config_sha256 != authorized_period_config_sha256():
            raise period.PeriodError("canonical configuration identity required")
        slot = _authorization_slot()
        with period._locked_period(slot / "period", expected) as locked:
            if locked.snapshot.state != "prepared":
                raise period.PeriodError("binding requires a prepared period")
            with period._parent(slot / "accounting") as parent:
                # One-shot tombstone: never remove or reinitialize after partial failure.
                os.mkdir("accounting", 0o700, dir_fd=parent)
                directory = os.open("accounting", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
                try:
                    fd = period._file(directory, "budget.sqlite3", os.O_RDWR | os.O_CREAT | os.O_EXCL)
                    try:
                        anchor = {**expected.__dict__, "config": authorized_period_config(),
                                  "period_path": str(slot / "period"), "ledger_path": str(slot / "accounting/budget.sqlite3"),
                                  "directory": _inode(directory), "ledger": _inode(fd),
                                  "ledger_uuid": uuid.uuid4().hex, "legacy_history": "UNKNOWN"}
                        os.fsync(fd)
                    finally:
                        os.close(fd)  # No auxiliary ledger fd remains when SQLite opens.
                    db = sqlite3.connect(f"file:/proc/self/fd/{directory}/budget.sqlite3?mode=rw", uri=True, isolation_level=None)
                    try:
                        db.execute("PRAGMA synchronous=FULL")
                        db.execute("BEGIN IMMEDIATE")
                        _initialize_tables(db)
                        db.execute("CREATE TABLE binding (singleton INTEGER PRIMARY KEY CHECK(singleton=1), payload TEXT NOT NULL, gate TEXT NOT NULL)")
                        db.execute("INSERT INTO binding VALUES (1,?, 'prepared')", (_json(anchor),))
                        db.execute("CREATE TABLE purposes (purpose TEXT PRIMARY KEY, attempts INTEGER NOT NULL, attempt_limit INTEGER NOT NULL, completion_cap INTEGER NOT NULL)")
                        db.executemany("INSERT INTO purposes VALUES (?,0,?,?)", [
                            (name, lane["attempts"], lane["completion_token_cap"])
                            for name, lane in period.POLICY["lanes"].items()
                        ])
                        locked.confirm()
                        db.commit()
                    finally:
                        db.close()
                    marker = period._file(directory, "binding.json", os.O_WRONLY | os.O_CREAT | os.O_EXCL)
                    try:
                        period._write(marker, anchor)
                    finally:
                        os.close(marker)
                    os.fsync(directory)
                    os.fsync(parent)
                finally:
                    os.close(directory)
        return cls.bound(expected)

    @contextmanager
    def _accounting(self, *, exclusive: bool = False):
        if self._identity is None:
            with self._connect() as db:
                yield db, None
            return
        slot = _authorization_slot()
        if self.path != slot / "accounting/budget.sqlite3" or self._identity.config_sha256 != authorized_period_config_sha256():
            raise period.PeriodError("canonical path or configuration drift")
        with period._locked_period(slot / "period", self._identity, exclusive=exclusive) as locked:
            with period._parent(self.path) as directory:
                marker = period._file(directory, "binding.json", os.O_RDONLY)
                try:
                    records = period._read(marker)
                finally:
                    os.close(marker)
                if len(records) != 1 or not isinstance(records[0], dict):
                    raise period.PeriodError("invalid binding anchor")
                anchor = records[0]
                required = {**self._identity.__dict__, "config": authorized_period_config(),
                            "period_path": str(slot / "period"), "ledger_path": str(self.path),
                            "directory": _inode(directory), "ledger": _ledger_inode(directory),
                            "ledger_uuid": anchor.get("ledger_uuid"), "legacy_history": "UNKNOWN"}
                if anchor != required or not isinstance(anchor["ledger_uuid"], str) or len(anchor["ledger_uuid"]) != 32:
                    raise period.PeriodError("binding identity or ledger drift")
                db = sqlite3.connect(f"file:/proc/self/fd/{directory}/budget.sqlite3?mode=rw", uri=True, timeout=5, isolation_level=None)
                try:
                    db.execute("PRAGMA synchronous=FULL")
                    if db.execute("SELECT payload,gate FROM binding").fetchall() not in (
                        [(_json(anchor), "prepared")], [(_json(anchor), "active")], [(_json(anchor), "halted")],
                    ):
                        raise period.PeriodError("SQL binding identity or gate drift")
                    rows = db.execute("SELECT purpose,attempts,attempt_limit,completion_cap FROM purposes").fetchall()
                    lanes = period.POLICY["lanes"]
                    continuation = _continuation(db, self._identity)
                    limits = {name: (48 if continuation and name == "dav58_loop" else lane["attempts"]) for name, lane in lanes.items()}
                    if len(rows) != len(lanes) or any(
                        name not in lanes or not 0 <= used <= limits[name]
                        or (limit, cap) != (limits[name], lanes[name]["completion_token_cap"])
                        for name, used, limit, cap in rows
                    ):
                        raise period.PeriodError("purpose rows missing or changed")
                    budget = db.execute("SELECT singleton,attempts,reserved_micro_usd,actual_micro_usd,halted FROM budget").fetchall()
                    if len(budget) != 1 or budget[0][0] != 1:
                        raise period.PeriodError("budget row missing or counters changed")
                    if _ledger_inode(directory) != anchor["ledger"]:
                        raise period.PeriodError("ledger replaced")
                    yield db, locked
                finally:
                    db.close()

    def _commit(self, db: sqlite3.Connection, locked) -> None:
        if locked is not None:
            anchor = json.loads(db.execute("SELECT payload FROM binding WHERE singleton=1").fetchone()[0])
            with period._parent(self.path) as directory:
                if _inode(directory) != anchor["directory"]:
                    raise period.PeriodError("accounting directory replaced")
                if _ledger_inode(directory) != anchor["ledger"]:
                    raise period.PeriodError("ledger replaced")
                # SQLite synchronous=FULL owns ledger durability and fd lifetimes.
                marker = period._file(directory, "binding.json", os.O_RDONLY)
                try:
                    if period._read(marker) != [anchor]:
                        raise period.PeriodError("binding anchor changed")
                    os.fsync(marker)
                finally:
                    os.close(marker)
                os.fsync(directory)
            locked.confirm()
        db.commit()

    def extend_dav58_once(self, authorization: str) -> dict:
        """Atomically audit a single +24 loop allocation, without resetting usage."""
        if self._identity is None or not isinstance(authorization, str) or not authorization.strip():
            raise period.PeriodError("explicit continuation authorization required")
        with self._accounting(exclusive=True) as (db, locked):
            db.execute("BEGIN IMMEDIATE")
            if _continuation(db, self._identity) is not None:
                raise period.PeriodError("continuation already allocated")
            snapshot = db.execute("SELECT attempts,reserved_micro_usd,actual_micro_usd,halted FROM budget").fetchone()
            quotas = {name: (used, limit) for name, used, limit in db.execute("SELECT purpose,attempts,attempt_limit FROM purposes")}
            if (locked.snapshot.state != "active" or db.execute("SELECT gate FROM binding").fetchone()[0] != "active"
                    or snapshot[3] or quotas["dav58_loop"] != (24, 24)
                    or quotas["dav58_judge"][1] - quotas["dav58_judge"][0] < 19
                    or snapshot[0] + 43 > period.POLICY["attempts"]
                    or snapshot[1] + 43 * worst_case_micro_usd(24000, 2000) > 1_000_000
                    or db.execute("SELECT count(*) FROM attempts WHERE settled=0 OR usage_known!=1").fetchone()[0]):
                raise BudgetLimitExceeded("continuation cannot reserve complete plan")
            config = {"base_config_sha256": self._identity.config_sha256, "loop_attempt_limit": 48,
                      "plan": DAV58_CONTINUATION_PLAN, "prompt_cap": 8000, "completion_cap": 2000}
            event = {"schema": "dav58-one-continuation-v1", "identity": self._identity.__dict__,
                     "authorization": authorization, "utc": datetime.now(timezone.utc).isoformat(),
                     "before": 24, "after": 48, "plan": DAV58_CONTINUATION_PLAN,
                     "usage_before": dict(zip(("attempts", "reserved_micro_usd", "actual_micro_usd"), snapshot[:3])),
                     "effective_config_sha256": hashlib.sha256(_json(config).encode()).hexdigest()}
            raw = _json(event)
            db.execute("CREATE TABLE dav58_continuation (singleton INTEGER PRIMARY KEY CHECK(singleton=1),payload TEXT NOT NULL,sha256 TEXT NOT NULL)")
            db.execute("INSERT INTO dav58_continuation VALUES (1,?,?)", (raw, hashlib.sha256(raw.encode()).hexdigest()))
            db.execute("UPDATE purposes SET attempt_limit=48 WHERE purpose='dav58_loop'")
            self._commit(db, locked)
            return event

    def continuation(self):
        with self._accounting() as (db, _):
            return _continuation(db, self._identity) if self._identity else None

    def activate(self) -> None:
        if self._identity is None:
            raise period.PeriodError("bound period required")
        with self._accounting(exclusive=True) as (db, locked):
            db.execute("BEGIN IMMEDIATE")
            gate = db.execute("SELECT gate FROM binding WHERE singleton=1").fetchone()[0]
            if gate not in ("prepared", "active") or locked.snapshot.state not in ("prepared", "active"):
                raise period.PeriodError("invalid bound activation")
            if gate == "active" and locked.snapshot.state != "active":
                raise period.PeriodError("inconsistent activation")
            if locked.snapshot.state == "prepared":
                locked.append("active")
            else:
                locked.confirm()  # Explicit retry confirms complete durable records.
            db.execute("UPDATE binding SET gate='active' WHERE singleton=1")
            self._commit(db, locked)

    def halt(self) -> None:
        if self._identity is None:
            raise period.PeriodError("bound period required")
        with self._accounting(exclusive=True) as (db, locked):
            db.execute("BEGIN IMMEDIATE")
            db.execute("UPDATE binding SET gate='halted' WHERE singleton=1")
            db.execute("UPDATE budget SET halted=1 WHERE singleton=1")
            self._commit(db, locked)
            if locked.snapshot.state != "halted":
                locked.append("halted")
            else:
                locked.confirm()

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        canonical = _authorization_slot() / "accounting/budget.sqlite3"
        if self._identity is not None or self.path.resolve() == canonical.resolve():
            raise period.PeriodError("canonical period ledger requires bound accounting")
        directory = self.path.parent
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        if directory.is_symlink():
            raise ValueError("budget directory must not be a symlink")
        os.chmod(directory, 0o700)
        if self.path.is_symlink():
            raise ValueError("budget database must not be a symlink")
        connection = sqlite3.connect(self.path, timeout=5, isolation_level=None)
        try:
            os.chmod(self.path, 0o600)
            connection.execute("PRAGMA busy_timeout=5000")
            connection.execute("PRAGMA journal_mode=WAL")
            _initialize_tables(connection)
            yield connection
        finally:
            connection.close()
            for path in (self.path, Path(f"{self.path}-wal"), Path(f"{self.path}-shm")):
                if path.exists():
                    os.chmod(path, 0o600)

    def begin_evaluation_group(self, attempts: int) -> None:
        if self._identity is not None:
            raise ValueError("bound policy does not use frozen evaluation groups")
        if not 1 <= attempts <= MAX_ATTEMPTS:
            raise ValueError("invalid evaluation group size")
        ceiling = worst_case_micro_usd(MAX_PROMPT_TOKENS, MAX_COMPLETION_TOKENS)
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            used, reserved, started, halted = db.execute(
                "SELECT attempts,reserved_micro_usd,evaluation_started,halted "
                "FROM budget WHERE singleton=1"
            ).fetchone()
            group_cost = attempts * ceiling
            if (
                halted
                or started
                or used + attempts > MAX_ATTEMPTS
                or reserved + group_cost > MAX_MICRO_USD
            ):
                db.rollback()
                raise BudgetLimitExceeded("insufficient shared budget for frozen evaluation")
            db.execute(
                "UPDATE budget SET evaluation_started=1,evaluation_remaining_calls=?,"
                "evaluation_remaining_micro_usd=? WHERE singleton=1",
                (attempts, group_cost),
            )
            self._commit(db, None)

    def reserve(
        self,
        prompt_tokens: int,
        completion_cap: int,
        *,
        purpose: str = "ordinary",
    ) -> Reservation:
        if self._identity is not None and (type(prompt_tokens) is not int or type(completion_cap) is not int):
            raise ValueError("bound token counts must be integers")
        if not 0 <= prompt_tokens <= MAX_PROMPT_TOKENS:
            raise BudgetLimitExceeded("prompt exceeds shared token ceiling")
        if not 1 <= completion_cap <= MAX_COMPLETION_TOKENS:
            raise BudgetLimitExceeded("completion exceeds shared token ceiling")
        lane = period.POLICY["lanes"].get(purpose) if self._identity is not None else None
        if self._identity is not None:
            if lane is None or completion_cap > lane["completion_token_cap"]:
                raise ValueError("invalid bound purpose or completion cap")
            prompt_tokens, completion_cap = period.POLICY["prompt_token_cap"], lane["completion_token_cap"]
        elif purpose not in {"ordinary", "frozen_evaluation"}:
            raise ValueError("invalid budget purpose")
        reserve = worst_case_micro_usd(prompt_tokens, completion_cap)
        max_attempts = period.POLICY["attempts"] if lane else MAX_ATTEMPTS
        max_cost = period.POLICY["limit_micro_usd"] if lane else MAX_MICRO_USD
        attempt_id = str(uuid.uuid4())
        with self._accounting() as (db, locked):
            db.execute("BEGIN IMMEDIATE")
            attempts, reserved, evaluation_started, evaluation_calls, evaluation_micro, halted = db.execute(
                "SELECT attempts,reserved_micro_usd,evaluation_started,"
                "evaluation_remaining_calls,evaluation_remaining_micro_usd,halted "
                "FROM budget WHERE singleton=1"
            ).fetchone()
            if locked is not None and (locked.snapshot.state != "active" or db.execute(
                "SELECT gate FROM binding WHERE singleton=1").fetchone()[0] != "active"):
                raise BudgetLimitExceeded("bound period is not active")
            if halted:
                # A provider-reported usage exceeded the attempt's reserved bounds;
                # the ledger is halted until a human reviews it, so no later call
                # can be sent on this budget.
                db.rollback()
                raise BudgetLimitExceeded("shared budget halted after over-bound usage")
            if attempts >= max_attempts or reserved + reserve > max_cost:
                db.rollback()
                raise BudgetLimitExceeded("shared request/cost budget exhausted")
            if lane:
                used, limit = db.execute("SELECT attempts,attempt_limit FROM purposes WHERE purpose=?", (purpose,)).fetchone()
                if used >= limit:
                    raise BudgetLimitExceeded("purpose attempt budget exhausted")
                db.execute("UPDATE purposes SET attempts=attempts+1 WHERE purpose=?", (purpose,))
            elif purpose == "frozen_evaluation":
                ceiling = worst_case_micro_usd(MAX_PROMPT_TOKENS, MAX_COMPLETION_TOKENS)
                if not evaluation_started or evaluation_calls < 1 or evaluation_micro < ceiling:
                    db.rollback()
                    raise BudgetLimitExceeded("frozen evaluation group not reserved")
                db.execute(
                    "UPDATE budget SET evaluation_remaining_calls=?,"
                    "evaluation_remaining_micro_usd=? WHERE singleton=1",
                    (evaluation_calls - 1, evaluation_micro - ceiling),
                )
            else:
                ceiling = worst_case_micro_usd(MAX_PROMPT_TOKENS, MAX_COMPLETION_TOKENS)
                protected_calls = (
                    evaluation_calls if evaluation_started else EVALUATION_RESERVE_ATTEMPTS
                )
                protected_cost = (
                    evaluation_micro
                    if evaluation_started
                    else EVALUATION_RESERVE_ATTEMPTS * ceiling
                )
                if (
                    attempts + 1 + protected_calls > MAX_ATTEMPTS
                    or reserved + reserve + protected_cost > MAX_MICRO_USD
                ):
                    db.rollback()
                    raise BudgetLimitExceeded("frozen evaluation reserve protected")
            db.execute(
                "UPDATE budget SET attempts=?,reserved_micro_usd=? WHERE singleton=1",
                (attempts + 1, reserved + reserve),
            )
            db.execute(
                "INSERT INTO attempts(attempt_id,purpose,prompt_tokens,completion_cap,reserved_micro_usd) "
                "VALUES (?,?,?,?,?)",
                (attempt_id, purpose, prompt_tokens, completion_cap, reserve),
            )
            self._commit(db, locked)
        return Reservation(attempt_id, reserve,
                           self._identity.identity if self._identity else None,
                           self._identity.config_sha256 if self._identity else None,
                           purpose if self._identity else None)

    def settle(
        self,
        reservation: Reservation,
        usage: dict[str, int | None] | None,
    ) -> int | None:
        """Record reported usage. Unknown usage remains charged at reservation."""
        if self._identity is not None:
            if (reservation.period_identity, reservation.config_sha256) != (self._identity.identity, self._identity.config_sha256):
                raise period.PeriodError("reservation scope mismatch")
        elif reservation.period_identity is not None or reservation.config_sha256 is not None:
            raise period.PeriodError("bound receipt cannot settle in legacy accounting")
        prompt = usage.get("prompt_tokens") if usage else None
        completion = usage.get("completion_tokens") if usage else None
        known = (
            isinstance(prompt, int) and not isinstance(prompt, bool) and prompt >= 0
            and isinstance(completion, int) and not isinstance(completion, bool) and completion >= 0
        )
        actual = worst_case_micro_usd(prompt, completion) if known else None
        with self._accounting(exclusive=True) as (db, locked):
            db.execute("BEGIN IMMEDIATE")
            row = db.execute(
                "SELECT reserved_micro_usd,settled,prompt_tokens,completion_cap,purpose "
                "FROM attempts WHERE attempt_id=?",
                (reservation.attempt_id,),
            ).fetchone()
            if row is None:
                db.rollback()
                raise RuntimeError("budget reservation not found")
            reserved, settled, bound_prompt, bound_completion, purpose = row
            if locked is not None:
                lane = period.POLICY["lanes"].get(purpose)
                if (lane is None or reservation.reserved_micro_usd != reserved or reservation.purpose != purpose
                    or bound_prompt != period.POLICY["prompt_token_cap"]
                    or bound_completion != lane["completion_token_cap"]
                    or reserved != worst_case_micro_usd(bound_prompt, bound_completion)):
                    raise period.PeriodError("reservation receipt mismatch")
            if settled:
                db.rollback()
                raise RuntimeError("budget reservation already settled")
            # A reported usage above the attempt's reserved token bounds (or the
            # cost they implied) means the ceiling that gated this call was wrong.
            # The usage is recorded and the ledger is halted in the *same*
            # transaction, so every later reserve() sees the halt.
            over_bound = (
                (
                    isinstance(prompt, int)
                    and not isinstance(prompt, bool)
                    and prompt > bound_prompt
                )
                or (
                    isinstance(completion, int)
                    and not isinstance(completion, bool)
                    and completion > bound_completion
                )
                or (known and actual is not None and actual > reserved)
            )
            charged_extra = max(0, actual - reserved) if actual is not None else 0
            db.execute(
                "UPDATE attempts SET actual_micro_usd=?,usage_known=?,settled=1 WHERE attempt_id=?",
                (actual, int(known), reservation.attempt_id),
            )
            db.execute(
                "UPDATE budget SET reserved_micro_usd=reserved_micro_usd+?, "
                "actual_micro_usd=actual_micro_usd+?, "
                "halted=MAX(halted,?) WHERE singleton=1",
                (charged_extra, actual or 0, int(over_bound)),
            )
            total = db.execute(
                "SELECT reserved_micro_usd FROM budget WHERE singleton=1"
            ).fetchone()[0]
            if locked is not None and over_bound:
                db.execute("UPDATE binding SET gate='halted' WHERE singleton=1")
            self._commit(db, locked)
            if locked is not None and over_bound:
                if locked.snapshot.state != "halted":
                    locked.append("halted")
                else:
                    locked.confirm()
        if over_bound:
            raise BudgetLimitExceeded(
                "reported usage exceeded the reserved token bound; budget halted"
            )
        if total > (period.POLICY["limit_micro_usd"] if self._identity else MAX_MICRO_USD):
            raise BudgetLimitExceeded("reported usage exceeded authorized cost ceiling")
        return actual

    def snapshot(self) -> dict[str, int | str | bool]:
        with self._accounting() as (db, locked):
            attempts, reserved, actual, eval_calls, eval_micro, halted = db.execute(
                "SELECT attempts,reserved_micro_usd,actual_micro_usd,"
                "evaluation_remaining_calls,evaluation_remaining_micro_usd,halted "
                "FROM budget WHERE singleton=1"
            ).fetchone()
            binding = {} if locked is None else {
                "period_identity": self._identity.identity, "config_sha256": self._identity.config_sha256,
                "state": locked.snapshot.state,
                "sql_gate": db.execute("SELECT gate FROM binding WHERE singleton=1").fetchone()[0],
                "legacy_history": "UNKNOWN", "runtime_accounting_connected": False,
                "spend_limit_enforced": False,
            }
        committed = reserved + eval_micro
        max_attempts = period.POLICY["attempts"] if self._identity else MAX_ATTEMPTS
        max_cost = period.POLICY["limit_micro_usd"] if self._identity else MAX_MICRO_USD
        return {
            **binding,
            "attempts": attempts,
            "reserved_micro_usd": reserved,
            "evaluation_remaining_calls": eval_calls,
            "evaluation_reserved_micro_usd": eval_micro,
            "committed_micro_usd": committed,
            "actual_micro_usd": actual,
            "halted": halted,
            "remaining_attempts": max(0, max_attempts - attempts),
            "remaining_micro_usd": max(0, max_cost - committed),
        }


_LEGACY_MODEL = object()


def authorized_model(expected: period.PeriodIdentity | object = _LEGACY_MODEL) -> tuple[str, ModelBudget]:
    """Return only the explicitly approved model alias and shared ledger."""
    model = os.environ.get("DEEPSEEK_MODEL")
    if model != MODEL_ALIAS:
        raise ValueError("authorized DeepSeek model alias is not configured")
    budget = None
    if expected is not _LEGACY_MODEL:
        budget = ModelBudget.bound(expected)
        snapshot = budget.snapshot()
        if snapshot["state"] != "active" or snapshot["sql_gate"] != "active" or snapshot["halted"]:
            raise BudgetLimitExceeded("bound period is not active")
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        raise ValueError("DeepSeek API key is not configured")
    return model, budget if budget is not None else ModelBudget()
