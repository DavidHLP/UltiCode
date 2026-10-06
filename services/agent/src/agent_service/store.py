"""Private SQLite canonical state for resumable agent workflows."""

from __future__ import annotations

import fcntl
import json
import os
import pwd
import sqlite3
import stat
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Callable, Iterator




class StoreError(RuntimeError):
    pass


class NotFound(StoreError):
    pass


class Conflict(StoreError):
    pass


class WorkflowStore:
    def __init__(self, path: Path | str | None = None) -> None:
        self.path = Path(path) if path is not None else (
            Path(pwd.getpwuid(os.getuid()).pw_dir)
            / ".local/state/ulticode/u03-workflow/state.sqlite3"
        )
        if not self.path.is_absolute() or ".." in self.path.parts or self.path.name in {"", ".", ".."}:
            raise ValueError("state database path must be absolute and normalized")
        self._dirfd = self._open_parent(self.path.parent)
        self._dir_identity = self._identity(os.fstat(self._dirfd))
        self._dbfd = -1
        self._db_identity: tuple[int, int] | None = None
        self._open_lock = threading.RLock()
        self._db_path = f"/proc/self/fd/{self._dirfd}/{self.path.name}"
        try:
            self._pin_database()
            self._initialize()
        except Exception:
            self.close()
            raise

    @classmethod
    def open(cls, path: Path | str | None = None) -> "WorkflowStore":
        return cls(path)

    @staticmethod
    def _identity(info: os.stat_result) -> tuple[int, int]:
        return info.st_dev, info.st_ino

    @staticmethod
    def _open_parent(parent: Path) -> int:
        fd = os.open("/", os.O_RDONLY | os.O_DIRECTORY)
        try:
            parts = parent.parts[1:]
            for part in parts:
                try:
                    child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
                except FileNotFoundError:
                    os.mkdir(part, 0o700, dir_fd=fd)
                    child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
                    os.fchmod(child, 0o700)
                os.close(fd)
                fd = child
                info = os.fstat(fd)
                if not stat.S_ISDIR(info.st_mode):
                    raise StoreError("state path ancestors must be real directories")
            info = os.fstat(fd)
            if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o700:
                raise StoreError("state directory permissions are unsafe")
            return fd
        except Exception:
            os.close(fd)
            raise

    @property
    def db_path(self) -> str:
        self._verify_paths()
        return self._db_path

    @property
    def checkpoint_path(self) -> str:
        return self.db_path

    def close(self) -> None:
        dbfd = getattr(self, "_dbfd", -1)
        if dbfd >= 0:
            os.close(dbfd)
            self._dbfd = -1
        dirfd = getattr(self, "_dirfd", -1)
        if dirfd >= 0:
            os.close(dirfd)
            self._dirfd = -1

    def __enter__(self) -> "WorkflowStore":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def _pin_database(self) -> None:
        created = False
        try:
            fd = os.open(self.path.name, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600,
                         dir_fd=self._dirfd)
            created = True
        except FileExistsError:
            fd = os.open(self.path.name, os.O_RDWR | os.O_NOFOLLOW, dir_fd=self._dirfd)
        try:
            info = os.fstat(fd)
            if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_nlink != 1:
                raise StoreError("state database must be a private single-link regular file")
            if created:
                os.fchmod(fd, 0o600)
            elif stat.S_IMODE(info.st_mode) != 0o600:
                raise StoreError("state database permissions are unsafe")
            self._dbfd = fd
            self._db_identity = self._identity(info)
        except Exception:
            os.close(fd)
            raise

    def _verify_paths(self) -> None:
        """Reject replacement or permission changes to pinned canonical storage."""
        directory = os.fstat(self._dirfd)
        if (
            self._identity(directory) != self._dir_identity
            or not stat.S_ISDIR(directory.st_mode)
            or directory.st_uid != os.getuid()
            or stat.S_IMODE(directory.st_mode) != 0o700
        ):
            raise StoreError("state directory identity or permissions changed")
        if self._dbfd < 0 or self._identity(os.fstat(self._dbfd)) != self._db_identity:
            raise StoreError("pinned state database identity changed")
        info = os.stat(self.path.name, dir_fd=self._dirfd, follow_symlinks=False)
        if (
            self._identity(info) != self._db_identity
            or not stat.S_ISREG(info.st_mode)
            or info.st_uid != os.getuid()
            or info.st_nlink != 1
            or stat.S_IMODE(info.st_mode) != 0o600
        ):
            raise StoreError("state database identity or permissions changed")


    def _secure_sidecars(self) -> None:
        for suffix in ("-wal", "-shm"):
            try:
                info = os.stat(self.path.name + suffix, dir_fd=self._dirfd, follow_symlinks=False)
            except FileNotFoundError:
                continue
            if (
                not stat.S_ISREG(info.st_mode)
                or info.st_uid != os.getuid()
                or info.st_nlink != 1
                or stat.S_IMODE(info.st_mode) != 0o600
            ):
                raise StoreError("state sidecar must be a private single-link regular file")

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        with self._open_lock:
            self._verify_paths()
            self._secure_sidecars()
            db = sqlite3.connect(self._db_path, uri=False, timeout=5, isolation_level=None)
            try:
                self._verify_paths()
                db.row_factory = sqlite3.Row
                db.execute("PRAGMA busy_timeout=5000")
                db.execute("PRAGMA synchronous=FULL")
                self._secure_sidecars()
            except Exception:
                db.close()
                raise
        try:
            yield db
        finally:
            db.close()


    def _initialize(self) -> None:
        with self._connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("""CREATE TABLE IF NOT EXISTS threads (
                thread_id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, run_id TEXT NOT NULL,
                status TEXT NOT NULL, source_submission_id TEXT NOT NULL, question TEXT NOT NULL,
                source_facts_json TEXT NOT NULL, corpus_identity_json TEXT NOT NULL,
                draft_json TEXT NOT NULL, analysis_json TEXT NOT NULL DEFAULT '{}',
                draft_version INTEGER NOT NULL, confirmation_json TEXT NOT NULL DEFAULT '{}',
                business_key TEXT NOT NULL, save_attempted INTEGER NOT NULL DEFAULT 0,
                cancel_requested INTEGER NOT NULL DEFAULT 0, event_seq INTEGER NOT NULL DEFAULT 0,
                plan_id TEXT, failure_reason TEXT, retry_blocked INTEGER NOT NULL DEFAULT 0,
                save_retry_count INTEGER NOT NULL DEFAULT 0,
                updated_at REAL NOT NULL
            )""")
            db.execute("""CREATE TABLE IF NOT EXISTS events (
                thread_id TEXT NOT NULL, seq INTEGER NOT NULL, kind TEXT NOT NULL,
                detail_json TEXT NOT NULL, created_at REAL NOT NULL,
                PRIMARY KEY(thread_id,seq)
            )""")

            db.execute("CREATE INDEX IF NOT EXISTS events_by_thread ON events(thread_id,seq)")
            columns = {row["name"] for row in db.execute("PRAGMA table_info(threads)")}
            if "save_retry_count" not in columns:
                db.execute("ALTER TABLE threads ADD COLUMN save_retry_count INTEGER NOT NULL DEFAULT 0")
            self._secure_sidecars()

    @contextmanager
    def thread_lock(self, thread_id: str) -> Iterator[None]:
        try:
            if str(uuid.UUID(thread_id)) != thread_id:
                raise ValueError
        except (ValueError, TypeError, AttributeError) as exc:
            raise ValueError("thread_id must be a canonical UUID") from exc
        name = f"{thread_id}.lock"
        fd = os.open(name, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600, dir_fd=self._dirfd)
        try:
            info = os.fstat(fd)
            if (
                not stat.S_ISREG(info.st_mode)
                or info.st_uid != os.getuid()
                or info.st_nlink != 1
                or stat.S_IMODE(info.st_mode) != 0o600
            ):
                raise StoreError("thread lock must be a private single-link regular file")
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as exc:
                raise Conflict("run_busy") from exc
            yield
        finally:
            try:
                fcntl.flock(fd, fcntl.LOCK_UN)
            finally:
                os.close(fd)

    @staticmethod
    def _decode(row: sqlite3.Row) -> dict[str, object]:
        result = dict(row)
        for field in ("source_facts_json", "corpus_identity_json", "draft_json", "analysis_json", "confirmation_json"):
            result[field.removesuffix("_json")] = json.loads(result.pop(field))
        return result

    def create(self, row: dict[str, object], *, kind: str = "thread_created") -> dict[str, object]:
        fields = (
            "thread_id", "owner_id", "run_id", "status", "source_submission_id", "question",
            "source_facts", "corpus_identity", "draft", "draft_version", "confirmation",
            "business_key", "updated_at",
        )
        values = [row[name] for name in fields]
        values[6] = json.dumps(values[6], ensure_ascii=False, separators=(",", ":"))
        values[7] = json.dumps(values[7], ensure_ascii=False, separators=(",", ":"))
        values[8] = json.dumps(values[8], ensure_ascii=False, separators=(",", ":"))
        values[10] = json.dumps(values[10], ensure_ascii=False, separators=(",", ":"))
        columns = ("thread_id,owner_id,run_id,status,source_submission_id,question,source_facts_json,"
                   "corpus_identity_json,draft_json,draft_version,confirmation_json,business_key,updated_at")
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                db.execute(f"INSERT INTO threads ({columns}) VALUES ({','.join('?' for _ in values)})", values)
                self._event(db, str(row["thread_id"]), 1, kind, {})
                db.execute("UPDATE threads SET event_seq=1 WHERE thread_id=?", (row["thread_id"],))
                db.commit()
            except Exception:
                db.rollback()
                raise
        return self.get(str(row["thread_id"]), str(row["owner_id"]))  # type: ignore[return-value]

    @staticmethod
    def _event(db: sqlite3.Connection, thread_id: str, seq: int, kind: str, detail: dict[str, object]) -> None:
        db.execute("INSERT INTO events VALUES (?,?,?,?,?)", (
            thread_id, seq, kind, json.dumps(detail, ensure_ascii=False, separators=(",", ":")), time.time()
        ))

    def get(self, thread_id: str, owner_id: str) -> dict[str, object] | None:
        with self._connect() as db:
            row = db.execute("SELECT * FROM threads WHERE thread_id=? AND owner_id=?", (thread_id, owner_id)).fetchone()
        return self._decode(row) if row else None

    def transition(
        self, thread_id: str, owner_id: str, *, expected_run: str, expected_version: int,
        statuses: set[str], changes: dict[str, object], kind: str, detail: dict[str, object] | None = None,
    ) -> dict[str, object]:
        json_fields = {"source_facts", "corpus_identity", "draft", "analysis", "confirmation"}
        allowed = {
            "run_id", "status", "question", "source_facts", "corpus_identity", "draft", "analysis",
            "draft_version", "confirmation", "business_key", "save_attempted", "cancel_requested",
            "plan_id", "failure_reason", "retry_blocked", "save_retry_count",
        }
        if changes.keys() - allowed:
            raise ValueError("invalid state change")
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                row = db.execute(
                    "SELECT * FROM threads WHERE thread_id=? AND owner_id=?", (thread_id, owner_id)
                ).fetchone()
                if row is None:
                    raise NotFound("thread_not_found")
                current = self._decode(row)
                if current["run_id"] != expected_run or current["draft_version"] != expected_version or current["status"] not in statuses:
                    raise Conflict("state_changed")
                seq = int(current["event_seq"]) + 1
                assignments = []
                values: list[object] = []
                for key, value in changes.items():
                    column = f"{key}_json" if key in json_fields else key
                    assignments.append(f"{column}=?")
                    values.append(json.dumps(value, ensure_ascii=False, separators=(",", ":")) if key in json_fields else value)
                assignments.extend(("event_seq=?", "updated_at=?"))
                values.extend((seq, time.time(), thread_id, owner_id, expected_run, expected_version))
                result = db.execute(
                    f"UPDATE threads SET {','.join(assignments)} WHERE thread_id=? AND owner_id=? AND run_id=? AND draft_version=?",
                    values,
                )
                if result.rowcount != 1:
                    raise Conflict("state_changed")
                self._event(db, thread_id, seq, kind, detail or {})
                db.commit()
            except Exception:
                db.rollback()
                raise
        return self.get(thread_id, owner_id)  # type: ignore[return-value]

    def cancel(self, thread_id: str, owner_id: str) -> dict[str, object]:
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                row = db.execute("SELECT * FROM threads WHERE thread_id=? AND owner_id=?", (thread_id, owner_id)).fetchone()
                if row is None:
                    raise NotFound("thread_not_found")
                current = self._decode(row)
                seq = int(current["event_seq"]) + 1
                if current["status"] == "saved":
                    status = "saved"
                elif current["save_attempted"]:
                    status = "unknown"
                else:
                    status = "cancelled"
                db.execute("UPDATE threads SET status=?,cancel_requested=1,event_seq=?,updated_at=? WHERE thread_id=?",
                           (status, seq, time.time(), thread_id))
                self._event(db, thread_id, seq, "cancel_requested", {})
                db.commit()
            except Exception:
                db.rollback()
                raise
        return self.get(thread_id, owner_id)  # type: ignore[return-value]

    def events(self, thread_id: str, *, after: int, limit: int = 100) -> tuple[list[dict[str, object]], int]:
        with self._connect() as db:
            rows = db.execute("SELECT seq,kind,detail_json,created_at FROM events WHERE thread_id=? AND seq>? ORDER BY seq LIMIT ?",
                              (thread_id, after, limit)).fetchall()
        result = [{"seq": row["seq"], "kind": row["kind"], "detail": json.loads(row["detail_json"]), "createdAt": row["created_at"]} for row in rows]
        return result, (int(rows[-1]["seq"]) if rows else after)

    def dispatch_allowed(
        self, thread_id: str, owner_id: str, run_id: str, version: int, *,
        confirmation_id: str, params_digest: str, clock: Callable[[], float],
    ) -> bool:
        """Atomically enforce cancellation, confirmation binding, and TTL before POST."""
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                now = clock()
                row = db.execute(
                    "SELECT status,run_id,draft_version,save_attempted,cancel_requested,confirmation_json "
                    "FROM threads WHERE thread_id=? AND owner_id=?",
                    (thread_id, owner_id),
                ).fetchone()
                confirmation = json.loads(row["confirmation_json"]) if row else {}
                allowed = bool(
                    row and row["status"] == "saving" and row["run_id"] == run_id
                    and row["draft_version"] == version and row["save_attempted"]
                    and not row["cancel_requested"] and confirmation.get("id") == confirmation_id
                    and confirmation.get("action") == "save_learning_plan"
                    and confirmation.get("draftVersion") == version
                    and confirmation.get("paramsDigest") == params_digest
                    and isinstance(confirmation.get("expiresAt"), (int, float))
                    and now < confirmation["expiresAt"]
                )
                db.commit()
                return allowed
            except Exception:
                db.rollback()
                raise
