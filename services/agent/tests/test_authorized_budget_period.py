from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, replace
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest

from authorized_budget_period import (
    POLICY, POLICY_ID, PeriodError, PeriodIdentity, activate_period,
    halt_period, prepare_period, read_period,
)

CONFIG = "a" * 64


def test_explicit_lifecycle_and_descriptive_policy(tmp_path):
    path = tmp_path / "period"
    prepared = prepare_period(path, "dav58-20261003", CONFIG)
    assert prepared.state == "prepared"
    assert POLICY_ID == "dav58-dav53-v1"
    assert POLICY["limit_micro_usd"] == 1_000_000
    assert POLICY["attempts"] == sum(lane["attempts"] for lane in POLICY["lanes"].values()) == 78
    for snapshot in (prepared, read_period(path, prepared.identity),
                     activate_period(path, prepared.identity), halt_period(path, prepared.identity)):
        assert snapshot.legacy_history == "UNKNOWN"
        assert snapshot.runtime_accounting_connected is False
        assert snapshot.spend_limit_enforced is False
    assert read_period(path, prepared.identity).state == "halted"
    assert sorted(p.name for p in path.iterdir()) == ["identity.json", "state.jsonl"]


def test_invalid_transitions_do_not_mutate(tmp_path):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    activate_period(path, identity)
    with pytest.raises(PeriodError):
        activate_period(path, identity)
    halt_period(path, identity)
    for operation in (activate_period, halt_period):
        with pytest.raises(PeriodError):
            operation(path, identity)


@pytest.mark.parametrize("state", ["prepared", "active", "halted"])
def test_prepare_never_reinitializes(tmp_path, state):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    if state in ("active", "halted"):
        activate_period(path, identity)
    if state == "halted":
        halt_period(path, identity)
    before = {p.name: p.read_bytes() for p in path.iterdir()}
    with pytest.raises(PeriodError):
        prepare_period(path, "p", CONFIG)
    assert before == {p.name: p.read_bytes() for p in path.iterdir()}


@pytest.mark.parametrize("missing", ["identity.json", "state.jsonl"])
def test_missing_files_never_repaired_or_reset(tmp_path, missing):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    (path / missing).unlink()
    for operation in (read_period, activate_period, halt_period):
        with pytest.raises(PeriodError):
            operation(path, identity)
        assert not (path / missing).exists()
    with pytest.raises(PeriodError):
        prepare_period(path, "p", CONFIG)


def test_missing_directory_not_created_by_read_or_transition(tmp_path):
    path = tmp_path / "missing"
    identity = PeriodIdentity("p", CONFIG, "b" * 32)
    for operation in (read_period, activate_period, halt_period):
        with pytest.raises(PeriodError):
            operation(path, identity)
        assert not path.exists()


def test_partial_prepare_retains_tombstone(tmp_path, monkeypatch):
    import authorized_budget_period as module
    path = tmp_path / "period"
    monkeypatch.setattr(module, "_write", lambda *args: (_ for _ in ()).throw(OSError("disk failure")))
    with pytest.raises(PeriodError):
        prepare_period(path, "p", CONFIG)
    assert path.is_dir()
    with pytest.raises(PeriodError):
        prepare_period(path, "p", CONFIG)


@pytest.mark.parametrize("change", [{"period_id": "other"}, {"config_sha256": "b" * 64},
                                    {"identity": "b" * 32}, {"policy_id": "other"}])
def test_expected_identity_drift_rejected_before_mutation(tmp_path, change):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    before = (path / "state.jsonl").read_bytes()
    for operation in (read_period, activate_period, halt_period):
        with pytest.raises(PeriodError):
            operation(path, replace(identity, **change))
    assert (path / "state.jsonl").read_bytes() == before


@pytest.mark.parametrize("field", ["period_id", "config_sha256", "policy_id", "identity", "state_file", "directory", "path"])
def test_marker_drift_rejected(tmp_path, field):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    marker = path / "identity.json"
    data = json.loads(marker.read_text())
    data[field] = "changed"
    marker.write_text(json.dumps(data) + "\n")
    with pytest.raises(PeriodError):
        activate_period(path, identity)
    assert (path / "state.jsonl").read_text() == '{"state":"prepared"}\n'


def test_replaced_state_fails_closed(tmp_path):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    state = path / "state.jsonl"
    replacement = path / "replacement"
    replacement.write_bytes(state.read_bytes())
    replacement.replace(state)
    with pytest.raises(PeriodError):
        read_period(path, identity)


def test_moved_directory_fails_closed(tmp_path):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    moved = tmp_path / "moved"
    path.rename(moved)
    with pytest.raises(PeriodError):
        read_period(moved, identity)


@pytest.mark.parametrize("history", [b"", b'{"state":"prepared"}', b'{}\n',
                                       b'{"state":"active"}\n', b'bad\n',
                                       b'{"state":"prepared"}\n{"state":"active"}\n{"state":"active"}\n'])
def test_corrupt_or_torn_history_fails_closed(tmp_path, history):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    (path / "state.jsonl").write_bytes(history)
    with pytest.raises(PeriodError):
        activate_period(path, identity)
    assert (path / "state.jsonl").read_bytes() == history


@pytest.mark.parametrize("period,config", [("", CONFIG), ("../p", CONFIG), ("p", "A" * 64),
                                           ("p", "a" * 63), (None, CONFIG), ("p", None)])
def test_invalid_identity_inputs_create_nothing(tmp_path, period, config):
    path = tmp_path / "period"
    with pytest.raises(PeriodError):
        prepare_period(path, period, config)
    assert not path.exists()


def test_explicit_existing_absolute_parent_required(tmp_path):
    for path in (Path("relative"), tmp_path / "absent" / "period", tmp_path / ".." / "period"):
        with pytest.raises(PeriodError):
            prepare_period(path, "p", CONFIG)


@pytest.mark.parametrize("component", ["parent", "directory", "identity.json", "state.jsonl"])
def test_symlinks_rejected(tmp_path, component):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    if component == "parent":
        alias = tmp_path / "alias"
        alias.symlink_to(tmp_path, target_is_directory=True)
        path = alias / "period"
    elif component == "directory":
        real = tmp_path / "real"
        path.rename(real)
        path.symlink_to(real, target_is_directory=True)
    else:
        target = path / component
        real = path / "real"
        target.rename(real)
        target.symlink_to(real)
    with pytest.raises(PeriodError):
        activate_period(path, identity)


def test_new_process_reads_pinned_identity_without_reset(tmp_path):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    activate_period(path, identity)
    script = """import json,sys
from pathlib import Path
from dataclasses import asdict
from authorized_budget_period import PeriodIdentity, read_period
print(json.dumps(asdict(read_period(Path(sys.argv[1]), PeriodIdentity(**json.loads(sys.argv[2]))))))
"""
    result = subprocess.run([sys.executable, "-c", script, str(path), json.dumps(asdict(identity))],
                            env={"PATH": os.defpath, "HOME": str(tmp_path),
                                 "PYTHONPATH": str(Path(__file__).resolve().parents[1] / "src"),
                                 "PYTHONDONTWRITEBYTECODE": "1"}, capture_output=True, text=True, check=True)
    snapshot = json.loads(result.stdout)
    assert snapshot["identity"] == asdict(identity)
    assert snapshot["state"] == "active"
    assert snapshot["runtime_accounting_connected"] is False
    assert snapshot["spend_limit_enforced"] is False


def test_concurrent_activation_has_one_winner(tmp_path):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    def attempt(_):
        try:
            return activate_period(path, identity).state
        except PeriodError:
            return "rejected"
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(attempt, range(8)))
    assert results.count("active") == 1
    assert results.count("rejected") == 7
    assert read_period(path, identity).state == "active"


def test_prepared_can_halt_without_activation(tmp_path):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    assert halt_period(path, identity).state == "halted"
    assert read_period(path, identity).state == "halted"
    with pytest.raises(PeriodError):
        activate_period(path, identity)


def test_policy_is_deeply_immutable():
    for mapping, key in ((POLICY, "attempts"), (POLICY["lanes"], "dav58_loop"),
                         (POLICY["lanes"]["dav58_loop"], "attempts")):
        with pytest.raises(TypeError):
            mapping[key] = 0


@pytest.mark.parametrize("bad_identity", [None, 123, "invalid"] )
def test_malformed_expected_identity_is_domain_error(tmp_path, bad_identity):
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    with pytest.raises(PeriodError):
        read_period(path, replace(identity, identity=bad_identity))


@pytest.mark.parametrize("filename", ["identity.json", "state.jsonl"])
def test_fifo_metadata_rejected_without_blocking_or_repair(tmp_path, filename):
    import stat
    path = tmp_path / "period"
    identity = prepare_period(path, "p", CONFIG).identity
    target = path / filename
    target.unlink()
    os.mkfifo(target)
    before = target.stat()
    other = path / ("state.jsonl" if filename == "identity.json" else "identity.json")
    original = other.read_bytes()
    for operation in (read_period, activate_period, halt_period):
        with pytest.raises(PeriodError):
            operation(path, identity)
        after = target.stat()
        assert stat.S_ISFIFO(after.st_mode)
        assert (after.st_dev, after.st_ino) == (before.st_dev, before.st_ino)
        assert other.read_bytes() == original
