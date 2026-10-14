import asyncio
import os
from contextlib import nullcontext
from types import SimpleNamespace

import pytest

from agent_service import runtime, runtime_authorization, runtime_accounting
from agent_service.store import WorkflowStore
from authorized_budget_period import (
    MANUAL_LIVE_TEST_PERIOD_ID, MANUAL_LIVE_TEST_POLICY_ID,
    REVALIDATION_POLICY_ID, PeriodIdentity, activate_period, prepare_period,
    read_period_identity, policy_for,
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
    for name in (
        "ULTICODE_AGENT_LIVE_ANALYSIS", "ULTICODE_AGENT_U02_GATE_PATH",
        "ULTICODE_AGENT_EXPECTED_HEAD", "ULTICODE_AGENT_EXPECTED_BASE",
        "ULTICODE_AGENT_BUDGET_POLICY", "ULTICODE_AGENT_PERSISTENCE_ENABLED",
    ):
        monkeypatch.delenv(name, raising=False)
    captured = {}
    monkeypatch.setattr(runtime, "_create_app", lambda **kwargs: captured.update(kwargs) or "app")

    assert runtime.create_app(state_path=tmp_path / "workflow.sqlite3") == "app"
    assert captured == {
        "state_path": tmp_path / "workflow.sqlite3", "persistence_enabled": False,
    }


def test_runtime_factory_enables_unbudgeted_live_analysis_for_loopback_development(monkeypatch, tmp_path):
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.delenv("ULTICODE_AGENT_BUDGET_POLICY", raising=False)
    monkeypatch.setenv("ULTICODE_AGENT_ORIGIN", "http://localhost:9002")
    monkeypatch.setenv("ULTICODE_AGENT_PERSISTENCE_ENABLED", "1")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    captured = {}
    monkeypatch.setattr(runtime, "_create_app", lambda **kwargs: captured.update(kwargs) or "app")

    assert runtime.create_app(state_path=tmp_path / "workflow.sqlite3") == "app"
    assert captured["unbudgeted_development_mode"] is True
    assert captured["expected_period"] is None
    assert captured["budget_guard"] is None
    assert captured["persistence_enabled"] is True


def test_runtime_factory_still_requires_budget_for_non_loopback_live_analysis(monkeypatch):
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.delenv("ULTICODE_AGENT_BUDGET_POLICY", raising=False)
    monkeypatch.setenv("ULTICODE_AGENT_ORIGIN", "https://agent.example.com")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_live_budget_policy_missing"):
        runtime.create_app()


@pytest.mark.parametrize("name", [
    "ULTICODE_AGENT_U02_GATE_PATH",
    "ULTICODE_AGENT_EXPECTED_HEAD",
    "ULTICODE_AGENT_EXPECTED_BASE",
])
def test_runtime_factory_rejects_retired_gate_environment(monkeypatch, name):
    monkeypatch.delenv("ULTICODE_AGENT_LIVE_ANALYSIS", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_PERSISTENCE_ENABLED", raising=False)
    monkeypatch.setenv(name, "")

    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_retired_gate_configuration"):
        runtime.create_app()


def test_runtime_factory_uses_named_active_period_and_shared_guard(monkeypatch, tmp_path):
    identity = _identity()
    guard = SimpleNamespace(close=lambda: None)
    binding = SimpleNamespace(expected_period=identity, guard=guard)
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.setenv("ULTICODE_AGENT_BUDGET_POLICY", REVALIDATION_POLICY_ID)
    monkeypatch.setenv("ULTICODE_AGENT_PERSISTENCE_ENABLED", "1")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    identity_calls = []
    binding_calls = []
    captured = {}
    monkeypatch.setattr(
        runtime, "read_active_period_identity",
        lambda policy: identity_calls.append(policy) or identity,
    )
    monkeypatch.setattr(runtime, "resolve_u03_runtime_binding", lambda expected: binding_calls.append(expected) or binding)
    monkeypatch.setattr(runtime, "open_u03_runtime_guard", lambda current: current.guard)
    monkeypatch.setattr(runtime, "_create_app", lambda **kwargs: captured.update(kwargs) or "app")

    assert runtime.create_app(state_path=tmp_path / "workflow.sqlite3") == "app"
    assert identity_calls == [REVALIDATION_POLICY_ID]
    assert binding_calls == [identity]
    assert captured["expected_period"] == identity
    assert captured["budget_guard"] is guard
    assert captured["model_purpose"] == "u03_analysis"
    assert captured["own_budget_guard"] is True
    assert captured["analysis_only_test_mode"] is False
    assert captured["persistence_enabled"] is True
    assert not {"u02_gate_path", "expected_head", "expected_base", "candidate_root"} & set(captured)


def test_manual_runtime_uses_fixed_budget_without_persistence(monkeypatch, tmp_path):
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
    monkeypatch.delenv("ULTICODE_AGENT_BUDGET_POLICY", raising=False)
    monkeypatch.delenv("ULTICODE_AGENT_PERSISTENCE_ENABLED", raising=False)
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
    assert captured["persistence_enabled"] is False
    assert captured["model_purpose"] == "u03_analysis"


def test_runtime_factory_rejects_invalid_persistence_flag(monkeypatch):
    monkeypatch.setenv("ULTICODE_AGENT_PERSISTENCE_ENABLED", "yes")

    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_persistence_flag_invalid"):
        runtime.create_app()


def test_manual_runtime_rejects_persistence_and_closes_budget_guard(monkeypatch):
    identity = PeriodIdentity(
        MANUAL_LIVE_TEST_PERIOD_ID, "c" * 64, "b" * 32, MANUAL_LIVE_TEST_POLICY_ID,
    )
    closed = []
    guard = SimpleNamespace(close=lambda: closed.append(True))
    binding = SimpleNamespace(expected_period=identity, guard=guard)
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.setenv("ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY", "1")
    monkeypatch.setenv("ULTICODE_AGENT_PERSISTENCE_ENABLED", "1")
    monkeypatch.delenv("ULTICODE_AGENT_BUDGET_POLICY", raising=False)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setattr(runtime, "_manual_test_period_identity", lambda: identity)
    monkeypatch.setattr(runtime, "resolve_u03_runtime_binding", lambda _expected: binding)
    monkeypatch.setattr(runtime, "open_u03_runtime_guard", lambda current: current.guard)

    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_manual_persistence_forbidden"):
        runtime.create_app()
    assert closed == [True]


    monkeypatch.delenv("ULTICODE_AGENT_LIVE_ANALYSIS", raising=False)
    monkeypatch.setenv("ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY", "1")
    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_live_authorization_disabled"):
        runtime.create_app()


@pytest.mark.parametrize("persistence", ["0", "1"])
def test_manual_policy_cannot_be_selected_as_normal_live_budget(monkeypatch, persistence):
    for name in runtime._RETIRED_ENV:
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("ULTICODE_AGENT_LIVE_ANALYSIS", "1")
    monkeypatch.setenv("ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY", "0")
    monkeypatch.setenv("ULTICODE_AGENT_BUDGET_POLICY", MANUAL_LIVE_TEST_POLICY_ID)
    monkeypatch.setenv("ULTICODE_AGENT_PERSISTENCE_ENABLED", persistence)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(
        runtime, "read_active_period_identity",
        lambda _policy: pytest.fail("manual policy must be rejected before reading its budget"),
    )

    with pytest.raises(runtime.RuntimeAuthorizationError, match="agent_manual_budget_requires_analysis_only"):
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


def test_runtime_accounting_reads_only_an_existing_active_policy_period(monkeypatch, tmp_path):
    policy_id = REVALIDATION_POLICY_ID
    config_sha256 = authorized_period_config_sha256(policy_id)
    slot = tmp_path / "authorized-slot"
    slot.mkdir()
    period_path = slot / "period"
    expected = prepare_period(
        period_path, "selected-runtime-period", config_sha256, policy_id=policy_id,
    ).identity
    activate_period(period_path, expected)
    monkeypatch.setattr(runtime_accounting, "authorization_slot", lambda _expected: slot)

    assert runtime_accounting.read_active_period_identity(policy_id) == expected


def test_runtime_accounting_does_not_create_or_accept_unprepared_period(monkeypatch, tmp_path):
    missing_slot = tmp_path / "missing-slot"
    monkeypatch.setattr(runtime_accounting, "authorization_slot", lambda _expected: missing_slot)
    with pytest.raises(ValueError, match="active authorized budget period unavailable"):
        runtime_accounting.read_active_period_identity(REVALIDATION_POLICY_ID)
    assert not missing_slot.exists()

    slot = tmp_path / "prepared-slot"
    slot.mkdir()
    path = slot / "period"
    identity = prepare_period(
        path, "prepared-only", authorized_period_config_sha256(REVALIDATION_POLICY_ID),
        policy_id=REVALIDATION_POLICY_ID,
    ).identity
    monkeypatch.setattr(runtime_accounting, "authorization_slot", lambda _expected: slot)
    with pytest.raises(ValueError, match="active authorized budget period unavailable"):
        runtime_accounting.read_active_period_identity(REVALIDATION_POLICY_ID)
    assert read_period_identity(
        path, period_id=identity.period_id, config_sha256=identity.config_sha256,
        policy_id=identity.policy_id,
    ) == identity


def test_runtime_budget_anchor_reconciles_ledger_and_retains_unknown_history(monkeypatch):
    identity = _identity()
    rows = [
        (1, "a", "u03_analysis", 4, 1, 1),
        (2, "b", "u03_analysis", 6, 1, 1),
        (3, "c", "u03_citation_judge", 0, 1, 1),
    ]

    class Database:
        def execute(self, _query):
            return self

        def fetchall(self):
            return rows

    history = {"legacy_unknown_encumbrance_micro_usd": 123}
    snapshot = {
        "attempts": 3, "actual_micro_usd": 10, "state": "active", "sql_gate": "active",
        "halted": False, "unknown_usage_attempts": 0, "unsettled_attempts": 0,
        "retained_history": history,
    }
    guard_data = {"receipts": [{}, {}, {}], "settled_peak_micro_usd": 10}
    budget = SimpleNamespace(
        _identity=identity, policy=policy_for(REVALIDATION_POLICY_ID),
        _accounting=lambda: nullcontext((Database(), None)),
    )
    validations = []
    monkeypatch.setattr(
        runtime_accounting.IncrementalGuard, "_validate_resume",
        lambda *_args: validations.append(True),
    )

    anchor = runtime_accounting.current_budget_anchor(identity, budget, snapshot, guard_data)

    assert anchor == {
        **identity.__dict__, "attempts": 3, "actual_micro_usd": 10,
    }
    assert validations == [True]
    assert snapshot["retained_history"] is history

    rows[0] = (1, "a", "u03_analysis", 5, 1, 1)
    with pytest.raises(ValueError, match="fresh budget counters disagree"):
        runtime_accounting.current_budget_anchor(identity, budget, snapshot, guard_data)


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
        with pytest.raises(ValueError, match="analysis-only mode cannot enable persistence routes"):
            runtime._create_app(
                store=store, expected_period=identity,
                budget_guard=SimpleNamespace(close=lambda: None),
                model_purpose="u03_analysis", own_budget_guard=True,
                analysis_only_test_mode=True, persistence_enabled=True,
            )
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
