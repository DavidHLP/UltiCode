import asyncio
import os
from types import SimpleNamespace

import pytest

from agent_service import runtime, runtime_authorization
from agent_service.store import WorkflowStore
from authorized_budget_period import (
    MANUAL_LIVE_TEST_PERIOD_ID, MANUAL_LIVE_TEST_POLICY_ID,
    REVALIDATION_POLICY_ID, PeriodIdentity, prepare_period, read_period_identity,
    policy_for,
)
from model_budget import authorized_period_config_sha256, worst_case_micro_usd
from dav58_live_guard import IncrementalGuard


def _identity():
    return PeriodIdentity(
        period_id="test-period",
        config_sha256="c" * 64,
        identity="b" * 32,
        policy_id=REVALIDATION_POLICY_ID,
    )


def _budget_snapshot(identity, **updates):
    snapshot = {
        "period_identity": identity.identity,
        "config_sha256": identity.config_sha256,
        "attempts": 3,
        "actual_micro_usd": 10,
        "state": "active",
        "sql_gate": "active",
        "halted": False,
        "unknown_usage_attempts": 0,
        "unsettled_attempts": 0,
    }
    snapshot.update(updates)
    return snapshot


def _stub_binding(monkeypatch, identity, *, snapshot_updates=None):
    budget = SimpleNamespace(snapshot=lambda: _budget_snapshot(identity, **(snapshot_updates or {})))
    anchor = {
        "period_id": identity.period_id,
        "identity": identity.identity,
        "config_sha256": identity.config_sha256,
        "policy_id": identity.policy_id,
        "attempts": 3,
        "actual_micro_usd": 10,
    }
    guard_data = {
        "period_identity": identity.identity,
        "config_sha256": identity.config_sha256,
        "receipts": [{}, {}, {}],
        "settled_peak_micro_usd": 10,
        "pending_micro_usd": 0,
        "halted": False,
    }
    monkeypatch.setattr(runtime_authorization, "authorized_model", lambda _expected: ("deepseek-flash", budget))
    monkeypatch.setattr(runtime_authorization, "current_budget_anchor", lambda *_args: anchor)
    monkeypatch.setattr(runtime_authorization, "read_existing_guard", lambda _path: (guard_data, "a" * 64))
    return budget, anchor, guard_data


def test_runtime_factory_without_opt_in_keeps_live_analysis_disabled(monkeypatch, tmp_path):
    monkeypatch.delenv("ULTICODE_AGENT_LIVE_ANALYSIS", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_U02_GATE_PATH", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_EXPECTED_HEAD", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_EXPECTED_BASE", raising=False)
    captured = {}
    monkeypatch.setattr(runtime, "_create_app", lambda **kwargs: captured.update(kwargs) or "app")

    assert runtime.create_app(state_path=tmp_path / "workflow.sqlite3") == "app"
    assert captured == {"state_path": tmp_path / "workflow.sqlite3"}


def test_runtime_factory_rejects_partial_live_authorization_at_startup(monkeypatch):
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.setenv("ULTICODE_AGENT_U02_GATE_PATH", "/private/u02-gate.json")
    monkeypatch.delenv("ULTICODE_AGENT_EXPECTED_HEAD", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_EXPECTED_BASE", raising=False)

    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_live_authorization_incomplete"):
        runtime.create_app()


def test_runtime_factory_derives_period_from_gate_and_passes_shared_guard(monkeypatch, tmp_path):
    identity = _identity()
    gate = {
        "budget_anchor": {
            "period_id": identity.period_id,
            "identity": identity.identity,
            "config_sha256": identity.config_sha256,
            "policy_id": identity.policy_id,
        }
    }
    guard = SimpleNamespace(close=lambda: None)
    binding = SimpleNamespace(expected_period=identity, guard=guard)
    gate_path = tmp_path / "u02-gate.json"
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.setenv("ULTICODE_AGENT_U02_GATE_PATH", str(gate_path))
    monkeypatch.setenv("ULTICODE_AGENT_EXPECTED_HEAD", "1" * 40)
    monkeypatch.setenv("ULTICODE_AGENT_EXPECTED_BASE", "2" * 40)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    gate_calls = []
    binding_calls = []
    captured = {}
    monkeypatch.setattr(runtime, "load_u02_gate", lambda *args, **kwargs: gate_calls.append((args, kwargs)) or gate)
    monkeypatch.setattr(runtime, "resolve_u03_runtime_binding", lambda expected: binding_calls.append(expected) or binding)
    monkeypatch.setattr(runtime, "open_u03_runtime_guard", lambda current: current.guard)
    monkeypatch.setattr(runtime, "_create_app", lambda **kwargs: captured.update(kwargs) or "app")

    assert runtime.create_app(state_path=tmp_path / "workflow.sqlite3") == "app"
    assert gate_calls[0][0][0] == gate_path
    assert gate_calls[0][0][1] == "1" * 40
    assert gate_calls[0][1]["expected_base"] == "2" * 40
    assert gate_calls[0][1]["runtime"] is True
    assert binding_calls == [identity]
    assert captured["u02_gate_path"] == gate_path
    assert captured["expected_period"] == identity
    assert captured["budget_guard"] is guard
    assert captured["model_purpose"] == "u03_analysis"
    assert captured["own_budget_guard"] is True


def test_manual_runtime_uses_only_the_fixed_budget_and_skips_u02_gate(monkeypatch, tmp_path):
    identity = PeriodIdentity(
        MANUAL_LIVE_TEST_PERIOD_ID, "c" * 64, "b" * 32, MANUAL_LIVE_TEST_POLICY_ID,
    )
    guard = SimpleNamespace(close=lambda: None)
    binding = SimpleNamespace(expected_period=identity, guard=guard)
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.setenv("ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY", "1")
    monkeypatch.delenv("ULTICODE_AGENT_U02_GATE_PATH", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_EXPECTED_HEAD", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_EXPECTED_BASE", raising=False)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setattr(runtime, "_manual_test_period_identity", lambda: identity)
    monkeypatch.setattr(runtime, "resolve_u03_runtime_binding", lambda expected: binding)
    monkeypatch.setattr(runtime, "open_u03_runtime_guard", lambda current: current.guard)
    captured = {}
    monkeypatch.setattr(runtime, "_create_app", lambda **kwargs: captured.update(kwargs) or "app")

    assert runtime.create_app(state_path=tmp_path / "workflow.sqlite3") == "app"
    assert captured["expected_period"] == identity
    assert captured["budget_guard"] is guard
    assert captured["analysis_only_test_mode"] is True
    assert captured["u02_gate_path"] is None
    assert captured["model_purpose"] == "u03_analysis"


def test_manual_analysis_mode_cannot_enable_without_live_opt_in(monkeypatch):
    monkeypatch.delenv("ULTICODE_AGENT_LIVE_ANALYSIS", raising=False)
    monkeypatch.setenv("ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY", "1")
    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_live_authorization_disabled"):
        runtime.create_app()


def test_manual_test_policy_stays_within_remaining_user_cap():
    policy = policy_for(MANUAL_LIVE_TEST_POLICY_ID)
    old = policy_for("acceptance-revalidation-v20")["history"]
    worst_case = worst_case_micro_usd(24_000, 2_000)
    total_calls = sum(lane["attempts"] for lane in policy["lanes"].values())

    assert policy["limit_micro_usd"] == 182_000_000
    assert old["known_committed_micro_usd"] + old["unknown_encumbrance_micro_usd"] + policy["limit_micro_usd"] <= 200_000_000
    assert total_calls * worst_case <= policy["limit_micro_usd"]


def test_manual_period_identity_reads_only_matching_private_period(tmp_path):
    config_sha256 = authorized_period_config_sha256(MANUAL_LIVE_TEST_POLICY_ID)
    period_path = tmp_path / "period"
    prepared = prepare_period(
        period_path, MANUAL_LIVE_TEST_PERIOD_ID, config_sha256,
        policy_id=MANUAL_LIVE_TEST_POLICY_ID,
    )

    assert read_period_identity(
        period_path, period_id=MANUAL_LIVE_TEST_PERIOD_ID,
        config_sha256=config_sha256, policy_id=MANUAL_LIVE_TEST_POLICY_ID,
    ) == prepared.identity
    with pytest.raises(ValueError, match="period identity unavailable or invalid"):
        read_period_identity(
            period_path, period_id="other", config_sha256=config_sha256,
            policy_id=MANUAL_LIVE_TEST_POLICY_ID,
        )


def test_manual_bound_guard_initializes_and_resumes_empty_journal(tmp_path):
    identity = PeriodIdentity(
        MANUAL_LIVE_TEST_PERIOD_ID, "c" * 64, "b" * 32, MANUAL_LIVE_TEST_POLICY_ID,
    )
    accounting = tmp_path / "accounting"
    accounting.mkdir(mode=0o700)
    budget = SimpleNamespace(
        _identity=identity,
        policy=policy_for(MANUAL_LIVE_TEST_POLICY_ID),
        path=accounting / "budget.sqlite3",
    )
    guard_path = accounting / f"dav58-increment-{identity.identity}.json"
    initial = IncrementalGuard(
        guard_path, period_identity=identity.identity,
        config_sha256=identity.config_sha256, budget=budget,
    )
    initial.close()
    guard_data, guard_sha = runtime_authorization.read_existing_guard(guard_path)
    binding = SimpleNamespace(
        expected_period=identity, budget=budget, guard_path=guard_path,
        guard_sha256=guard_sha,
        anchor={"attempts": 0, "actual_micro_usd": 0},
    )

    resumed = runtime_authorization.open_u03_runtime_guard(binding)
    try:
        assert guard_data["receipts"] == []
        assert resumed.state["receipts"] == []
        assert resumed.state["halted"] is False
    finally:
        resumed.close()


def test_manual_analysis_mode_keeps_confirmation_and_save_routes_unmounted(tmp_path):
    identity = PeriodIdentity(
        MANUAL_LIVE_TEST_PERIOD_ID, "c" * 64, "b" * 32, MANUAL_LIVE_TEST_POLICY_ID,
    )
    store = WorkflowStore.open(tmp_path / "workflow.sqlite3")
    try:
        app = runtime._create_app(
            store=store, expected_period=identity,
            budget_guard=SimpleNamespace(close=lambda: None),
            model_purpose="u03_analysis", own_budget_guard=True,
            analysis_only_test_mode=True,
        )
        paths = {route.path for route in app.routes}
        assert "/agent/threads/{thread_id}/analyze" in paths
        assert "/agent/threads/{thread_id}/confirm" not in paths
        assert "/agent/threads/{thread_id}/save" not in paths
    finally:
        store.close()


def test_u03_binding_rejects_unknown_usage_before_opening_guard(monkeypatch):
    identity = _identity()
    _stub_binding(monkeypatch, identity, snapshot_updates={"unknown_usage_attempts": 1})
    monkeypatch.setattr(
        runtime_authorization,
        "read_existing_guard",
        lambda _path: pytest.fail("must reject unresolved accounting before opening guard"),
    )

    with pytest.raises(ValueError, match="u03_budget_anchor_mismatch"):
        runtime_authorization.resolve_u03_runtime_binding(identity)


def test_legacy_budget_lane_cannot_authorize_u03(monkeypatch):
    from authorized_budget_period import POLICY_ID

    identity = PeriodIdentity("old-period", "c" * 64, "b" * 32, POLICY_ID)
    monkeypatch.setattr(
        runtime_authorization,
        "authorized_model",
        lambda _expected: pytest.fail("legacy policy must be rejected before budget access"),
    )
    with pytest.raises(ValueError, match="model_budget_purpose_blocked"):
        runtime_authorization.resolve_u03_runtime_binding(identity)


def test_open_u03_guard_rejects_mismatched_prefix_and_closes(monkeypatch, tmp_path):
    identity = _identity()
    budget = SimpleNamespace()
    binding = SimpleNamespace(
        expected_period=identity,
        budget=budget,
        guard_path=tmp_path / "guard.json",
        guard_sha256="a" * 64,
        anchor={"attempts": 3, "actual_micro_usd": 10},
    )

    class Guard:
        def __init__(self, *args, **kwargs):
            self.state = {"receipts": [{}, {}], "settled_peak_micro_usd": 10, "pending_micro_usd": 0, "halted": False}
            self.closed = False

        def close(self):
            self.closed = True

    created = []

    def make_guard(*args, **kwargs):
        guard = Guard(*args, **kwargs)
        created.append(guard)
        return guard

    monkeypatch.setattr(runtime_authorization, "IncrementalGuard", make_guard)
    with pytest.raises(ValueError, match="u03_shared_guard_budget_mismatch"):
        runtime_authorization.open_u03_runtime_guard(binding)
    assert created[0].closed is True


def test_open_u03_guard_rejects_malformed_state_and_closes(monkeypatch, tmp_path):
    identity = _identity()
    binding = SimpleNamespace(
        expected_period=identity,
        budget=SimpleNamespace(),
        guard_path=tmp_path / "guard.json",
        guard_sha256="a" * 64,
        anchor={"attempts": 0, "actual_micro_usd": 0},
    )

    class Guard:
        def __init__(self, *args, **kwargs):
            self.state = {}
            self.closed = False

        def close(self):
            self.closed = True

    created = []

    def make_guard(*args, **kwargs):
        guard = Guard()
        created.append(guard)
        return guard

    monkeypatch.setattr(runtime_authorization, "IncrementalGuard", make_guard)
    with pytest.raises(ValueError, match="u03_shared_guard_budget_mismatch"):
        runtime_authorization.open_u03_runtime_guard(binding)
    assert created[0].closed is True


def test_existing_guard_reader_rejects_fifo_without_blocking(tmp_path):
    path = tmp_path / "guard.json"
    os.mkfifo(path, 0o600)
    with pytest.raises(ValueError, match="u03_shared_guard_invalid"):
        runtime_authorization.read_existing_guard(path)


def test_open_u03_guard_resumes_existing_authorized_journal(monkeypatch, tmp_path):
    identity = _identity()
    budget = SimpleNamespace()
    binding = SimpleNamespace(
        expected_period=identity,
        budget=budget,
        guard_path=tmp_path / "guard.json",
        guard_sha256="a" * 64,
        anchor={"attempts": 3, "actual_micro_usd": 10},
    )

    class Guard:
        def __init__(self, path, **kwargs):
            self.path = path
            self.kwargs = kwargs
            self.state = {"receipts": [{}, {}, {}], "settled_peak_micro_usd": 10,
                          "pending_micro_usd": 0, "halted": False}

        def close(self):
            raise AssertionError("matching guard must remain open for the service lifetime")

    monkeypatch.setattr(runtime_authorization, "IncrementalGuard", Guard)
    guard = runtime_authorization.open_u03_runtime_guard(binding)
    assert guard.path == binding.guard_path
    assert guard.kwargs["resume_sha256"] == binding.guard_sha256
    assert guard.kwargs["period_identity"] == identity.identity
    assert guard.kwargs["budget"] is budget


def test_app_lifecycle_closes_owned_budget_guard(tmp_path):
    closed = []
    guard = SimpleNamespace(close=lambda: closed.append(True))
    app = runtime._create_app(
        state_path=tmp_path / "workflow.sqlite3", budget_guard=guard, own_budget_guard=True,
    )

    async def scenario():
        async with app.router.lifespan_context(app):
            pass

    asyncio.run(scenario())
    assert closed == [True]
