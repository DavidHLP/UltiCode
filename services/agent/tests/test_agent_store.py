import os
import selectors
import sqlite3
import stat
import subprocess
import sys
import threading
import uuid
from contextlib import contextmanager
import pytest

from agent_service.store import Conflict, StoreError, WorkflowStore


def _row(owner="owner"):
    confirmation = {
        "id": str(uuid.uuid4()), "action": "save_learning_plan", "draftVersion": 1,
        "paramsDigest": "digest", "expiresAt": 100.0,
    }
    return {
        "thread_id": str(uuid.uuid4()), "owner_id": owner, "run_id": str(uuid.uuid4()),
        "status": "awaiting_confirmation", "source_submission_id": str(uuid.uuid4()),
        "question": "review submission", "source_facts": {"status": "Accepted"},
        "corpus_identity": {"scope": "sample"}, "draft": {"title": "t", "content": "c"},
        "draft_version": 1, "confirmation": confirmation, "business_key": str(uuid.uuid4()), "updated_at": 1.0,
    }



def _kill_after_ready(proc: subprocess.Popen[str], marker: str) -> None:
    assert proc.stdout is not None
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(proc.stdout, selectors.EVENT_READ)
            assert selector.select(timeout=5), "child did not signal its durable crash window"
        assert proc.stdout.readline().strip() == marker
    finally:
        if proc.poll() is None:
            proc.kill()
        proc.wait(timeout=5)
        proc.stdout.close()

def test_state_and_event_transition_commit_atomically(tmp_path):
    store = WorkflowStore.open(tmp_path / "state.sqlite3")
    row = store.create(_row())
    changed = store.transition(row["thread_id"], row["owner_id"], expected_run=row["run_id"],
        expected_version=1, statuses={"awaiting_confirmation"},
        changes={"status": "analyzing", "run_id": "next-run"}, kind="analysis_started",
        detail={"runId": "forged-run", "phase": "model"})
    events, cursor = store.events(row["thread_id"], after=0)
    assert changed["status"] == "analyzing"
    assert changed["run_id"] == "next-run"
    assert [event["kind"] for event in events] == ["thread_created", "analysis_started"]
    assert [event["detail"]["runId"] for event in events] == [row["run_id"], "next-run"]
    assert events[1]["detail"]["phase"] == "model"
    assert cursor == 2
    with pytest.raises(Conflict):
        store.transition(row["thread_id"], row["owner_id"], expected_run=row["run_id"],
            expected_version=1, statuses={"awaiting_confirmation"}, changes={"status": "failed"}, kind="late")
    store.close()
    reopened = WorkflowStore.open(tmp_path / "state.sqlite3")
    assert reopened.events(row["thread_id"], after=0)[0] == events
    reopened.close()


def test_existing_conversation_turns_get_empty_context_without_losing_data(tmp_path):
    path = tmp_path / "context-migration.sqlite3"
    store = WorkflowStore.open(path)
    conversation_id, turn_id = str(uuid.uuid4()), str(uuid.uuid4())
    store.create_conversation(conversation_id, "owner", str(uuid.uuid4()), "Existing conversation", now=1)
    store.create_conversation_turn(conversation_id, "owner", turn_id, str(uuid.uuid4()), "Original question", now=1)
    store.close()
    with sqlite3.connect(path) as db:
        db.execute("ALTER TABLE conversation_turns DROP COLUMN context_json")
    reopened = WorkflowStore.open(path)
    turn = reopened.get_conversation_turn(conversation_id, turn_id, "owner")
    assert turn["context"] == {}
    assert turn["question"] == "Original question"
    reopened.close()


def test_cancel_fence_and_dispatch_binding_are_atomic(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    row = store.create(_row())
    saving = store.transition(row["thread_id"], row["owner_id"], expected_run=row["run_id"],
        expected_version=1, statuses={"awaiting_confirmation"},
        changes={"status": "saving", "save_attempted": 1}, kind="save_intent")
    confirmation_id = row["confirmation"]["id"]
    assert store.dispatch_allowed(row["thread_id"], row["owner_id"], saving["run_id"], 1,
                                 confirmation_id=confirmation_id, params_digest="digest", clock=lambda: 99.0)
    assert not store.dispatch_allowed(row["thread_id"], row["owner_id"], saving["run_id"], 1,
                                     confirmation_id=confirmation_id, params_digest="wrong", clock=lambda: 99.0)
    assert not store.dispatch_allowed(row["thread_id"], row["owner_id"], saving["run_id"], 1,
                                     confirmation_id=confirmation_id, params_digest="digest", clock=lambda: 100.0)
    assert not store.dispatch_allowed(row["thread_id"], row["owner_id"], saving["run_id"], 1,
                                     confirmation_id="wrong", params_digest="digest", clock=lambda: 99.0)
    assert not store.dispatch_allowed(row["thread_id"], row["owner_id"], "wrong-run", 1,
                                     confirmation_id=confirmation_id, params_digest="digest", clock=lambda: 99.0)
    assert not store.dispatch_allowed(row["thread_id"], row["owner_id"], saving["run_id"], 2,
                                     confirmation_id=confirmation_id, params_digest="digest", clock=lambda: 99.0)
    changed_confirmation = {**row["confirmation"], "action": "other"}
    store.transition(row["thread_id"], row["owner_id"], expected_run=saving["run_id"], expected_version=1,
        statuses={"saving"}, changes={"confirmation": changed_confirmation}, kind="invalid_action")
    assert not store.dispatch_allowed(row["thread_id"], row["owner_id"], saving["run_id"], 1,
                                     confirmation_id=confirmation_id, params_digest="digest", clock=lambda: 99.0)
    cancelled = store.cancel(row["thread_id"], row["owner_id"])
    assert cancelled["status"] == "unknown"
    assert cancelled["cancel_requested"] == 1
    assert not store.dispatch_allowed(row["thread_id"], row["owner_id"], saving["run_id"], 1,
                                     confirmation_id=confirmation_id, params_digest="digest", clock=lambda: 99.0)
    store.close()


def test_dispatch_clock_is_sampled_after_waiting_for_sqlite_write_lock(tmp_path, monkeypatch):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    row = store.create(_row())
    store.transition(row["thread_id"], row["owner_id"], expected_run=row["run_id"],
        expected_version=1, statuses={"awaiting_confirmation"},
        changes={"status": "saving", "save_attempted": 1}, kind="save_intent")
    beginning = threading.Event()
    sampled = threading.Event()
    finished = threading.Event()
    now = [99.0]
    results = []
    errors = []
    connect = store._connect

    @contextmanager
    def traced_connect():
        with connect() as db:
            db.set_trace_callback(lambda statement: beginning.set() if statement == "BEGIN IMMEDIATE" else None)
            yield db

    def clock():
        sampled.set()
        return now[0]

    def dispatch():
        try:
            results.append(store.dispatch_allowed(
                row["thread_id"], row["owner_id"], row["run_id"], 1,
                confirmation_id=row["confirmation"]["id"], params_digest="digest", clock=clock))
        except BaseException as exc:
            errors.append(exc)
        finally:
            finished.set()

    blocker = sqlite3.connect(store.db_path)
    worker = threading.Thread(target=dispatch, daemon=True)
    try:
        blocker.execute("BEGIN IMMEDIATE")
        monkeypatch.setattr(store, "_connect", traced_connect)
        worker.start()
        assert beginning.wait(timeout=2)
        assert not finished.wait(timeout=0.05)
        assert not sampled.is_set()
        now[0] = 100.0
        blocker.rollback()
        worker.join(timeout=6)
        assert not worker.is_alive()
        assert not errors
        assert sampled.is_set()
        assert results == [False]
    finally:
        blocker.rollback()
        blocker.close()
        if worker.ident is not None:
            worker.join(timeout=6)
        store.close()


def test_orphan_analysis_reconciles_without_losing_draft_or_replaying(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    row = store.create(_row())
    analyzing = store.transition(row["thread_id"], row["owner_id"], expected_run=row["run_id"],
        expected_version=1, statuses={"awaiting_confirmation"},
        changes={"status": "analyzing", "run_id": "orphan-run"}, kind="analysis_started")
    recovered = store.transition(row["thread_id"], row["owner_id"], expected_run=analyzing["run_id"],
        expected_version=1, statuses={"analyzing"}, changes={
            "status": "awaiting_confirmation", "confirmation": {}, "failure_reason": "analysis_interrupted",
        }, kind="analysis_interrupted")
    events, cursor = store.events(row["thread_id"], after=0)
    assert recovered["draft"] == row["draft"]
    assert recovered["confirmation"] == {}
    assert recovered["status"] == "awaiting_confirmation"
    assert [event["kind"] for event in events] == [
        "thread_created", "analysis_started", "analysis_interrupted",
    ]
    assert cursor == recovered["event_seq"] == 3
    store.close()

def test_cancelled_orphan_analysis_cannot_be_reconciled_back_to_active(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    row = store.create(_row())
    analyzing = store.transition(row["thread_id"], row["owner_id"], expected_run=row["run_id"],
        expected_version=1, statuses={"awaiting_confirmation"},
        changes={"status": "analyzing", "run_id": "cancelled-run"}, kind="analysis_started")
    cancelled = store.cancel(row["thread_id"], row["owner_id"])
    assert cancelled["status"] == "cancelled"
    with pytest.raises(Conflict):
        store.transition(row["thread_id"], row["owner_id"], expected_run=analyzing["run_id"],
            expected_version=1, statuses={"analyzing"},
            changes={"status": "awaiting_confirmation"}, kind="late_analysis_reconcile")
    current = store.get(row["thread_id"], row["owner_id"])
    events, cursor = store.events(row["thread_id"], after=0)
    assert current["status"] == "cancelled"
    assert current["draft"] == row["draft"]
    assert current["event_seq"] == cursor == 3
    assert [event["detail"]["runId"] for event in events] == [row["run_id"], "cancelled-run", "cancelled-run"]
    assert [event["kind"] for event in events] == [
        "thread_created", "analysis_started", "cancel_requested",
    ]
    store.close()


def test_create_transition_rollback_keeps_state_and_event_sequence(tmp_path, monkeypatch):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    row = store.create(_row())
    def fail_event(*_args):
        raise OSError("simulated disk failure")
    monkeypatch.setattr(store, "_event", fail_event)
    with pytest.raises(OSError, match="disk failure"):
        store.transition(row["thread_id"], row["owner_id"], expected_run=row["run_id"],
            expected_version=1, statuses={"awaiting_confirmation"}, changes={"status": "analyzing"},
            kind="analysis_started")
    current = store.get(row["thread_id"], row["owner_id"])
    events, cursor = store.events(row["thread_id"], after=0)
    assert current["status"] == "awaiting_confirmation"
    assert current["event_seq"] == cursor == 1
    assert len(events) == 1
    store.close()


def test_thread_lock_is_private_nonblocking_and_never_unlinked(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    row = store.create(_row())
    lock = tmp_path / f"{row['thread_id']}.lock"
    with store.thread_lock(row["thread_id"]):
        assert stat.S_IMODE(lock.stat().st_mode) == 0o600
        with pytest.raises(Conflict, match="run_busy"):
            with store.thread_lock(row["thread_id"]):
                pass
    assert lock.exists()
    os.chmod(lock, 0o644)
    with pytest.raises(StoreError, match="private"):
        with store.thread_lock(row["thread_id"]):
            pass
    store.close()


def test_thread_lock_rejects_symlink_and_hardlink(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    thread_id = str(uuid.uuid4())
    lock = tmp_path / f"{thread_id}.lock"
    target = tmp_path / "target"
    target.write_text("keep")
    lock.symlink_to(target)
    with pytest.raises(OSError):
        with store.thread_lock(thread_id):
            pass
    lock.unlink()
    lock.write_text("")
    os.link(lock, tmp_path / "linked-lock")
    with pytest.raises(StoreError, match="single-link"):
        with store.thread_lock(thread_id):
            pass
    store.close()

def test_thread_lock_is_cross_process(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    thread_id = str(uuid.uuid4())
    lock = tmp_path / f"{thread_id}.lock"
    script = """
import fcntl, os, sys, time
fd = os.open(sys.argv[1], os.O_CREAT | os.O_RDWR, 0o600)
fcntl.flock(fd, fcntl.LOCK_EX)
print('LOCKED', flush=True)
time.sleep(30)
"""
    proc = subprocess.Popen([sys.executable, "-c", script, str(lock)],
                            stdout=subprocess.PIPE, text=True)
    assert proc.stdout is not None and proc.stdout.readline().strip() == "LOCKED"
    proc.stdout.close()
    with pytest.raises(Conflict, match="run_busy"):
        with store.thread_lock(thread_id):
            pass
    proc.kill()
    proc.wait(timeout=5)
    store.close()


def test_private_directory_database_and_sidecars(tmp_path):
    db_path = tmp_path / "state.sqlite3"
    store = WorkflowStore(db_path)
    assert stat.S_IMODE(tmp_path.stat().st_mode) == 0o700
    assert stat.S_IMODE(db_path.stat().st_mode) == 0o600
    assert db_path.stat().st_nlink == 1
    assert store.db_path == store.checkpoint_path
    assert store.db_path.startswith(f"/proc/self/fd/{store._dirfd}/")
    assert os.stat(store.db_path).st_ino == os.fstat(store._dbfd).st_ino
    with store._connect() as db:
        assert db.execute("PRAGMA busy_timeout").fetchone()[0] == 5000
        assert db.execute("PRAGMA synchronous").fetchone()[0] == 2
        assert db.execute("PRAGMA journal_mode").fetchone()[0].lower() == "wal"
        db.execute("BEGIN IMMEDIATE")
        db.execute("INSERT INTO events VALUES (?,?,?,?,?)", ("private", 1, "x", "{}", 1.0))
        store._secure_sidecars()
        for suffix in ("-wal", "-shm"):
            sidecar = tmp_path / f"state.sqlite3{suffix}"
            if sidecar.exists():
                assert stat.S_IMODE(sidecar.stat().st_mode) == 0o600
        db.rollback()
    store.close()

def test_sidecars_reject_symlink_fifo_and_nonprivate_modes_without_opening(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    sidecar = tmp_path / "state.sqlite3-shm"
    sidecar.symlink_to(tmp_path / "target")
    with pytest.raises(StoreError, match="private"):
        store._secure_sidecars()
    sidecar.unlink()
    os.mkfifo(sidecar)
    with pytest.raises(StoreError, match="private"):
        store._secure_sidecars()
    sidecar.unlink()
    sidecar.touch(mode=0o600)
    os.chmod(sidecar, 0o644)
    with pytest.raises(StoreError, match="private"):
        store._secure_sidecars()
    store.close()


def test_sidecar_validation_does_not_release_process_wal_locks(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    holder = sqlite3.connect(store.path, timeout=0, isolation_level=None)
    holder.execute("BEGIN IMMEDIATE")
    try:
        store._secure_sidecars()
        script = """
import sqlite3, sys
db = sqlite3.connect(sys.argv[1], timeout=0, isolation_level=None)
try:
    db.execute('BEGIN IMMEDIATE')
except sqlite3.OperationalError:
    print('LOCKED')
else:
    print('UNLOCKED')
    sys.exit(1)
"""
        result = subprocess.run([sys.executable, "-c", script, str(store.path)],
                               capture_output=True, text=True, timeout=5)
        assert result.returncode == 0
        assert result.stdout.strip() == "LOCKED"
    finally:
        holder.rollback()
        holder.close()
        store.close()


def test_store_rejects_private_directory_mode_changes(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    os.chmod(tmp_path, 0o755)
    try:
        with pytest.raises(StoreError, match="directory identity or permissions"):
            store.get("missing", "owner")
    finally:
        os.chmod(tmp_path, 0o700)
        store.close()

def test_repeated_reads_close_sqlite_connections(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    row = store.create(_row())
    baseline = len(os.listdir("/proc/self/fd"))
    for _ in range(40):
        assert store.get(row["thread_id"], row["owner_id"])["status"] == "awaiting_confirmation"
        store.events(row["thread_id"], after=0)
    assert len(os.listdir("/proc/self/fd")) == baseline
    store.close()


def test_explicit_paths_enforce_directory_and_database_modes(tmp_path):
    parent = tmp_path / "open"
    parent.mkdir()
    os.chmod(parent, 0o755)
    with pytest.raises(StoreError, match="directory permissions"):
        WorkflowStore(parent / "state.sqlite3")
    os.chmod(parent, 0o700)
    path = parent / "insecure.sqlite3"
    path.touch(mode=0o644)
    with pytest.raises(StoreError, match="database permissions"):
        WorkflowStore(path)

def test_default_store_uses_private_canonical_home_slot(tmp_path, monkeypatch):
    import agent_service.store as module
    home = tmp_path / "home"
    home.mkdir(mode=0o700)
    monkeypatch.setattr(module.pwd, "getpwuid", lambda _uid: type("User", (), {"pw_dir": str(home)})())
    store = WorkflowStore()
    expected = home / ".local/state/ulticode/u03-workflow/state.sqlite3"
    assert store.path == expected
    assert stat.S_IMODE(expected.parent.stat().st_mode) == 0o700
    assert stat.S_IMODE(expected.stat().st_mode) == 0o600
    store.close()


def test_database_rejects_symlink_hardlink_and_replacement(tmp_path):
    symlink = tmp_path / "symlink.sqlite3"
    target = tmp_path / "target"
    target.touch()
    symlink.symlink_to(target)
    with pytest.raises(OSError):
        WorkflowStore(symlink)
    path = tmp_path / "state.sqlite3"
    store = WorkflowStore(path)
    alias = tmp_path / "alias.sqlite3"
    os.link(path, alias)
    with pytest.raises(StoreError, match="identity or permissions"):
        with store._connect():
            pass
    alias.unlink()
    replacement = tmp_path / "replacement.sqlite3"
    replacement.touch(mode=0o600)
    os.replace(replacement, path)
    with pytest.raises(StoreError, match="identity or permissions"):
        _ = store.checkpoint_path
    with pytest.raises(StoreError, match="identity or permissions"):
        with store._connect():
            pass
    store.close()


def test_ancestor_symlink_is_rejected_without_following(tmp_path):
    real = tmp_path / "real"
    real.mkdir()
    os.chmod(real, 0o700)
    link = tmp_path / "link"
    link.symlink_to(real, target_is_directory=True)
    with pytest.raises(OSError):
        WorkflowStore(link / "state.sqlite3")
    assert list(real.iterdir()) == []


def test_process_death_rolls_back_uncommitted_state_and_event(tmp_path):
    path = tmp_path / "state.sqlite3"
    store = WorkflowStore(path)
    row = store.create(_row())
    script = """
import sqlite3, sys, time
db = sqlite3.connect(sys.argv[1], isolation_level=None)
db.execute('BEGIN IMMEDIATE')
db.execute("UPDATE threads SET status='analyzing',event_seq=2 WHERE thread_id=?", (sys.argv[2],))
db.execute("INSERT INTO events VALUES (?,?,?,?,?)", (sys.argv[2],2,'uncommitted','{}',1.0))
print('READY', flush=True)
time.sleep(30)
"""
    proc = subprocess.Popen([sys.executable, "-c", script, str(path), row["thread_id"]],
                            stdout=subprocess.PIPE, text=True)
    _kill_after_ready(proc, "READY")
    current = store.get(row["thread_id"], row["owner_id"])
    events, cursor = store.events(row["thread_id"], after=0)
    assert current["status"] == "awaiting_confirmation"
    assert current["event_seq"] == cursor == 1
    assert len(events) == 1
    store.close()


def test_process_death_after_commit_preserves_canonical_state_and_event(tmp_path):
    path = tmp_path / "state.sqlite3"
    store = WorkflowStore(path)
    row = store.create(_row())
    script = """
import sqlite3, sys, time
db = sqlite3.connect(sys.argv[1], isolation_level=None)
db.execute('BEGIN IMMEDIATE')
db.execute("UPDATE threads SET status='analyzing',event_seq=2 WHERE thread_id=?", (sys.argv[2],))
db.execute("INSERT INTO events VALUES (?,?,?,?,?)", (sys.argv[2],2,'committed','{}',1.0))
db.commit()
print('COMMITTED', flush=True)
time.sleep(30)
"""
    proc = subprocess.Popen([sys.executable, "-c", script, str(path), row["thread_id"]],
                            stdout=subprocess.PIPE, text=True)
    _kill_after_ready(proc, "COMMITTED")
    current = store.get(row["thread_id"], row["owner_id"])
    events, cursor = store.events(row["thread_id"], after=0)
    assert current["status"] == "analyzing"
    assert current["event_seq"] == cursor == 2
    assert [event["kind"] for event in events] == ["thread_created", "committed"]
    store.close()


def test_conversation_turns_are_owner_scoped_ordered_and_deleted_without_touching_plan_threads(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    plan_thread = store.create(_row())
    conversation_id = str(uuid.uuid4())
    source_id = str(uuid.uuid4())
    conversation = store.create_conversation(
        conversation_id, "owner", source_id, "Explain this result", now=1.0,
    )
    first = store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), "client-turn-1", "Explain result", now=2.0,
    )
    started = store.start_conversation_turn(
        conversation_id, first["turn_id"], "owner", first["run_id"], now=3.0,
    )
    store.append_conversation_event(
        conversation_id, first["turn_id"], "owner", started["run_id"],
        "tool_started", {"toolName": "search_evidence"}, now=4.0,
    )
    completed = store.complete_conversation_turn(
        conversation_id, first["turn_id"], "owner", started["run_id"],
        answer="Use a boundary case.", citations=[], trace=[], now=5.0,
    )
    second = store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), "client-turn-2", "Why this complexity?", now=6.0,
    )

    assert conversation["turn_count"] == 0
    assert completed["status"] == "completed"
    assert [turn["turn_index"] for turn in store.list_conversation_turns(
        conversation_id, "owner", before=None, limit=10,
    )] == [1, 2]
    assert store.get_conversation(conversation_id, "other-owner") is None
    assert store.list_conversations("other-owner", offset=0, limit=10) == []
    assert store.get_conversation_turn(conversation_id, second["turn_id"], "other-owner") is None

    store.delete_conversation(conversation_id, "owner")
    assert store.get_conversation(conversation_id, "owner") is None
    assert store.get_conversation_turn(conversation_id, first["turn_id"], "owner") is None
    assert store.get(plan_thread["thread_id"], plan_thread["owner_id"]) is not None
    store.close()


def test_cancelled_turn_lock_blocks_new_turn_across_store_instances(tmp_path):
    first_store = WorkflowStore(tmp_path / "conversation-lock.sqlite3")
    second_store = WorkflowStore(tmp_path / "conversation-lock.sqlite3")
    conversation_id = str(uuid.uuid4())
    first_store.create_conversation(conversation_id, "owner", str(uuid.uuid4()), "Review", now=1.0)
    turn = first_store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), str(uuid.uuid4()), "First question", now=2.0,
    )
    first_store.start_conversation_turn(
        conversation_id, turn["turn_id"], "owner", turn["run_id"], now=3.0,
    )

    with first_store.thread_lock(turn["turn_id"]):
        cancelled = second_store.cancel_conversation_turn(
            conversation_id, turn["turn_id"], "owner", expected_run_id=turn["run_id"], now=4.0,
        )
        assert cancelled["status"] == "cancelled"
        with pytest.raises(Conflict, match="conversation_turn_active"):
            second_store.delete_conversation(conversation_id, "owner")
        assert second_store.get_conversation(conversation_id, "owner") is not None
        with pytest.raises(Conflict, match="conversation_turn_active"):
            second_store.create_conversation_turn(
                conversation_id, "owner", str(uuid.uuid4()), str(uuid.uuid4()),
                "Second question", now=5.0,
            )

    created = second_store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), str(uuid.uuid4()),
        "Second question", now=6.0,
    )
    assert created["status"] == "queued"
    first_store.close()
    second_store.close()


def test_cancelled_process_blocks_admission_and_deletion_until_exit(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    conversation_id = str(uuid.uuid4())
    store.create_conversation(conversation_id, "owner", str(uuid.uuid4()), "Review", now=1.0)
    turn = store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), "first", "First", now=2.0,
    )
    store.start_conversation_turn(conversation_id, turn["turn_id"], "owner", turn["run_id"], now=3.0)
    script = """
import fcntl, os, sys
fd = os.open(sys.argv[1], os.O_CREAT | os.O_RDWR, 0o600)
fcntl.flock(fd, fcntl.LOCK_EX)
print('LOCKED', flush=True)
sys.stdin.read()
"""
    proc = subprocess.Popen(
        [sys.executable, "-I", "-c", script, str(tmp_path / f"{turn['turn_id']}.lock")],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True,
    )
    try:
        assert proc.stdout is not None
        with selectors.DefaultSelector() as selector:
            selector.register(proc.stdout, selectors.EVENT_READ)
            assert selector.select(timeout=5), "child did not acquire execution lock"
        assert proc.stdout.readline().strip() == "LOCKED"
        store.cancel_conversation_turn(conversation_id, turn["turn_id"], "owner", expected_run_id=turn["run_id"], now=4.0)
        with pytest.raises(Conflict, match="conversation_turn_active"):
            store.delete_conversation(conversation_id, "owner")
        with pytest.raises(Conflict, match="conversation_turn_active"):
            store.create_conversation_turn(
                conversation_id, "owner", str(uuid.uuid4()), "second", "Second", now=5.0,
            )
    finally:
        proc.kill()
        proc.wait(timeout=5)
        proc.stdout.close()
        proc.stdin.close()
        store.close()
    reopened = WorkflowStore(tmp_path / "state.sqlite3")
    assert reopened.get_conversation_turn(conversation_id, turn["turn_id"], "owner")["status"] == "cancelled"
    next_turn = reopened.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), "second", "Second", now=6.0,
    )
    assert reopened.start_conversation_turn(
        conversation_id, next_turn["turn_id"], "owner", next_turn["run_id"], now=7.0,
    )["status"] == "running"
    reopened.close()


@pytest.mark.parametrize("active_status", ["queued", "running", "cancelled"])
def test_retry_cannot_bypass_another_active_turn(tmp_path, active_status):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    conversation_id = str(uuid.uuid4())
    store.create_conversation(conversation_id, "owner", str(uuid.uuid4()), "Review", now=1.0)
    first = store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), "first", "First", now=2.0,
    )
    store.start_conversation_turn(conversation_id, first["turn_id"], "owner", first["run_id"], now=3.0)
    store.fail_conversation_turn(
        conversation_id, first["turn_id"], "owner", first["run_id"], "model_failed", now=4.0,
    )
    second = store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), "second", "Second", now=5.0,
    )
    with store.thread_lock(second["turn_id"]):
        if active_status != "queued":
            store.start_conversation_turn(
                conversation_id, second["turn_id"], "owner", second["run_id"], now=6.0,
            )
        if active_status == "cancelled":
            store.cancel_conversation_turn(conversation_id, second["turn_id"], "owner", expected_run_id=second["run_id"], now=7.0)
        with pytest.raises(Conflict, match="conversation_turn_active"):
            store.retry_conversation_turn(conversation_id, first["turn_id"], "owner", now=8.0)
    assert store.get_conversation_turn(conversation_id, first["turn_id"], "owner")["status"] == "failed"
    store.close()


def test_dead_conversation_run_can_be_recovered_and_retried(tmp_path):
    store = WorkflowStore(tmp_path / "conversation-recovery.sqlite3")
    conversation_id = str(uuid.uuid4())
    store.create_conversation(conversation_id, "owner", str(uuid.uuid4()), "Review", now=1.0)
    turn = store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), str(uuid.uuid4()), "First question", now=2.0,
    )
    started = store.start_conversation_turn(
        conversation_id, turn["turn_id"], "owner", turn["run_id"], now=3.0,
    )

    recovered = store.recover_conversation_turn(
        conversation_id, turn["turn_id"], "owner", now=4.0,
    )
    assert recovered["status"] == "failed"
    assert recovered["failure_reason"] == "turn_interrupted"
    retried = store.retry_conversation_turn(
        conversation_id, turn["turn_id"], "owner", now=5.0,
    )
    assert retried["status"] == "queued"
    assert retried["run_id"] != started["run_id"]
    assert store.start_conversation_turn(
        conversation_id, turn["turn_id"], "owner", retried["run_id"], now=6.0,
    )["status"] == "running"
    store.close()


def test_conversation_turn_idempotency_and_cancel_fence(tmp_path):
    store = WorkflowStore(tmp_path / "state.sqlite3")
    conversation_id = str(uuid.uuid4())
    store.create_conversation(conversation_id, "owner", str(uuid.uuid4()), "Review", now=1.0)
    turn_id = str(uuid.uuid4())
    turn = store.create_conversation_turn(
        conversation_id, "owner", turn_id, "client-turn", "Review the boundary case", now=2.0,
    )
    duplicate = store.create_conversation_turn(
        conversation_id, "owner", str(uuid.uuid4()), "client-turn", "Review the boundary case", now=3.0,
    )
    assert duplicate["turn_id"] == turn["turn_id"]
    with pytest.raises(Conflict):
        store.create_conversation_turn(
            conversation_id, "owner", str(uuid.uuid4()), "client-turn", "Different prompt", now=3.0,
        )

    started = store.start_conversation_turn(
        conversation_id, turn["turn_id"], "owner", turn["run_id"], now=4.0,
    )
    with pytest.raises(Conflict, match="conversation_turn_active"):
        store.delete_conversation(conversation_id, "owner")
    cancelled = store.cancel_conversation_turn(
        conversation_id, turn["turn_id"], "owner", expected_run_id=turn["run_id"], now=5.0,
    )
    assert cancelled["status"] == "cancelled"
    assert cancelled["cancel_requested"] is True
    with pytest.raises(Conflict):
        store.complete_conversation_turn(
            conversation_id, turn["turn_id"], "owner", started["run_id"],
            answer="Late response", citations=[], trace=[], now=6.0,
        )
    store.delete_conversation(conversation_id, "owner")
    store.close()
