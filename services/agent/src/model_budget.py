"""Process-shared hard budget for explicitly authorized DeepSeek calls."""

from __future__ import annotations

import os
import sqlite3
import uuid
from dataclasses import dataclass
from contextlib import contextmanager
from typing import Iterator
from pathlib import Path

MAX_ATTEMPTS = 200
MAX_MICRO_USD = 2_000_000
MAX_PROMPT_TOKENS = 24_000
MAX_COMPLETION_TOKENS = 4_000
EVALUATION_RESERVE_ATTEMPTS = 60
MODEL_ALIAS = "deepseek-flash"


class BudgetLimitExceeded(RuntimeError):
    """Request was not sent because shared budget could not reserve its ceiling."""


@dataclass(frozen=True)
class Reservation:
    attempt_id: str
    reserved_micro_usd: int


def _ceil_tenths(value: int) -> int:
    return (value + 9) // 10


def worst_case_micro_usd(prompt_tokens: int, completion_tokens: int) -> int:
    if prompt_tokens < 0 or completion_tokens < 0:
        raise ValueError("token counts must be non-negative")
    # Peak cache-miss rates: input $0.30/M, output $1.20/M.
    return _ceil_tenths(prompt_tokens * 3 + completion_tokens * 12)


def _state_db() -> Path:
    state_home = os.environ.get("XDG_STATE_HOME")
    root = Path(state_home) if state_home else Path.home() / ".local" / "state"
    if not root.is_absolute():
        raise ValueError("XDG_STATE_HOME must be absolute")
    return root / "ulticode" / "first-delivery" / "model-budget.sqlite3"


class ModelBudget:
    """Atomic cross-process request and worst-case cost accounting.

    Reservations are never refunded. At least 60 maximum-sized calls remain
    unavailable to non-evaluation work until the frozen evaluation starts.
    """

    def __init__(self, path: Path | None = None) -> None:
        self.path = path or _state_db()
        if not self.path.is_absolute():
            raise ValueError("budget path must be absolute")

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
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
            yield connection
        finally:
            connection.close()
            for path in (self.path, Path(f"{self.path}-wal"), Path(f"{self.path}-shm")):
                if path.exists():
                    os.chmod(path, 0o600)

    def begin_evaluation_group(self, attempts: int) -> None:
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
            db.commit()

    def reserve(
        self,
        prompt_tokens: int,
        completion_cap: int,
        *,
        purpose: str = "ordinary",
    ) -> Reservation:
        if not 0 <= prompt_tokens <= MAX_PROMPT_TOKENS:
            raise BudgetLimitExceeded("prompt exceeds shared token ceiling")
        if not 1 <= completion_cap <= MAX_COMPLETION_TOKENS:
            raise BudgetLimitExceeded("completion exceeds shared token ceiling")
        if purpose not in {"ordinary", "frozen_evaluation"}:
            raise ValueError("invalid budget purpose")
        reserve = worst_case_micro_usd(prompt_tokens, completion_cap)
        attempt_id = str(uuid.uuid4())
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            attempts, reserved, evaluation_started, evaluation_calls, evaluation_micro, halted = db.execute(
                "SELECT attempts,reserved_micro_usd,evaluation_started,"
                "evaluation_remaining_calls,evaluation_remaining_micro_usd,halted "
                "FROM budget WHERE singleton=1"
            ).fetchone()
            if halted:
                # A provider-reported usage exceeded the attempt's reserved bounds;
                # the ledger is halted until a human reviews it, so no later call
                # can be sent on this budget.
                db.rollback()
                raise BudgetLimitExceeded("shared budget halted after over-bound usage")
            if attempts >= MAX_ATTEMPTS or reserved + reserve > MAX_MICRO_USD:
                db.rollback()
                raise BudgetLimitExceeded("shared request/cost budget exhausted")
            if purpose == "frozen_evaluation":
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
            db.commit()
        return Reservation(attempt_id, reserve)

    def settle(
        self,
        reservation: Reservation,
        usage: dict[str, int | None] | None,
    ) -> int | None:
        """Record reported usage. Unknown usage remains charged at reservation."""
        prompt = usage.get("prompt_tokens") if usage else None
        completion = usage.get("completion_tokens") if usage else None
        known = (
            isinstance(prompt, int) and not isinstance(prompt, bool) and prompt >= 0
            and isinstance(completion, int) and not isinstance(completion, bool) and completion >= 0
        )
        actual = worst_case_micro_usd(prompt, completion) if known else None
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute(
                "SELECT reserved_micro_usd,settled,prompt_tokens,completion_cap "
                "FROM attempts WHERE attempt_id=?",
                (reservation.attempt_id,),
            ).fetchone()
            if row is None:
                db.rollback()
                raise RuntimeError("budget reservation not found")
            reserved, settled, bound_prompt, bound_completion = row
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
            db.commit()
        if over_bound:
            raise BudgetLimitExceeded(
                "reported usage exceeded the reserved token bound; budget halted"
            )
        if total > MAX_MICRO_USD:
            raise BudgetLimitExceeded("reported usage exceeded authorized cost ceiling")
        return actual

    def snapshot(self) -> dict[str, int]:
        with self._connect() as db:
            attempts, reserved, actual, eval_calls, eval_micro, halted = db.execute(
                "SELECT attempts,reserved_micro_usd,actual_micro_usd,"
                "evaluation_remaining_calls,evaluation_remaining_micro_usd,halted "
                "FROM budget WHERE singleton=1"
            ).fetchone()
        committed = reserved + eval_micro
        return {
            "attempts": attempts,
            "reserved_micro_usd": reserved,
            "evaluation_remaining_calls": eval_calls,
            "evaluation_reserved_micro_usd": eval_micro,
            "committed_micro_usd": committed,
            "actual_micro_usd": actual,
            "halted": halted,
            "remaining_attempts": max(0, MAX_ATTEMPTS - attempts),
            "remaining_micro_usd": max(0, MAX_MICRO_USD - committed),
        }


def authorized_model() -> tuple[str, ModelBudget]:
    """Return only the explicitly approved model alias and shared ledger."""
    model = os.environ.get("DEEPSEEK_MODEL")
    if model != MODEL_ALIAS:
        raise ValueError("authorized DeepSeek model alias is not configured")
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        raise ValueError("DeepSeek API key is not configured")
    return model, ModelBudget()
