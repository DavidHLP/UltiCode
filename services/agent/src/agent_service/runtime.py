"""Uvicorn factory for the Agent service with explicit, evidence-bound live analysis opt-in."""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any

from authorized_budget_period import (
    MANUAL_LIVE_TEST_PERIOD_ID, MANUAL_LIVE_TEST_POLICY_ID, POLICY_ID,
    PeriodIdentity, read_period_identity,
)
from agent_service.app import create_app as _create_app
from agent_service.gate import load_u02_gate
from agent_service.runtime_authorization import open_u03_runtime_guard, resolve_u03_runtime_binding
from model_budget import MODEL_ALIAS, authorization_slot, authorized_period_config_sha256


class RuntimeAuthorizationError(RuntimeError):
    """The live analysis startup configuration is missing or invalid."""


_LIVE_ANALYSIS = "ULTICODE_AGENT_LIVE_ANALYSIS"
_MANUAL_ANALYSIS_ONLY = "ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY"
_GATE_PATH = "ULTICODE_AGENT_U02_GATE_PATH"
_EXPECTED_HEAD = "ULTICODE_AGENT_EXPECTED_HEAD"
_EXPECTED_BASE = "ULTICODE_AGENT_EXPECTED_BASE"
_AUTH_ENV = (_GATE_PATH, _EXPECTED_HEAD, _EXPECTED_BASE)
_AUTH_KWARGS = {
    "u02_gate_path", "expected_head", "expected_base", "candidate_root",
    "expected_period", "budget_guard", "model_purpose", "own_budget_guard",
    "analysis_only_test_mode",
}


def _manual_test_period_identity() -> PeriodIdentity:
    config_sha256 = authorized_period_config_sha256(MANUAL_LIVE_TEST_POLICY_ID)
    slot = authorization_slot(PeriodIdentity(
        MANUAL_LIVE_TEST_PERIOD_ID, config_sha256, "0" * 32, MANUAL_LIVE_TEST_POLICY_ID,
    ))
    return read_period_identity(
        slot / "period", period_id=MANUAL_LIVE_TEST_PERIOD_ID,
        config_sha256=config_sha256, policy_id=MANUAL_LIVE_TEST_POLICY_ID,
    )


def _manual_live_authorization_kwargs(environ: dict[str, str]) -> dict[str, Any]:
    if any(environ.get(name) for name in _AUTH_ENV):
        raise RuntimeAuthorizationError("agent_manual_test_gate_override_forbidden")
    if (not environ.get("DEEPSEEK_API_KEY")
            or environ.get("DEEPSEEK_MODEL") != MODEL_ALIAS):
        raise RuntimeAuthorizationError("agent_manual_test_provider_configuration_invalid")
    try:
        expected_period = _manual_test_period_identity()
        binding = resolve_u03_runtime_binding(expected_period)
        guard = open_u03_runtime_guard(binding)
    except Exception:
        raise RuntimeAuthorizationError("agent_manual_test_budget_unavailable") from None
    return {
        "u02_gate_path": None,
        "expected_period": expected_period,
        "budget_guard": guard,
        "model_purpose": "u03_analysis",
        "own_budget_guard": True,
        "analysis_only_test_mode": True,
    }


def _live_authorization_kwargs(environ: dict[str, str]) -> dict[str, Any] | None:
    enabled = environ.get(_LIVE_ANALYSIS, "0")
    manual = environ.get(_MANUAL_ANALYSIS_ONLY, "0")
    if enabled not in {"0", "1"}:
        raise RuntimeAuthorizationError("agent_live_analysis_flag_invalid")
    if manual not in {"0", "1"}:
        raise RuntimeAuthorizationError("agent_manual_analysis_flag_invalid")
    configured = any(environ.get(name) for name in _AUTH_ENV)
    if enabled == "0":
        if configured or manual == "1":
            raise RuntimeAuthorizationError("agent_live_authorization_disabled")
        return None
    if manual == "1":
        return _manual_live_authorization_kwargs(environ)

    missing = [name for name in _AUTH_ENV if not environ.get(name)]
    if missing:
        raise RuntimeAuthorizationError("agent_live_authorization_incomplete")
    if not environ.get("DEEPSEEK_API_KEY"):
        raise RuntimeAuthorizationError("agent_live_provider_credentials_missing")

    gate_path = Path(environ[_GATE_PATH])
    expected_head = environ[_EXPECTED_HEAD]
    expected_base = environ[_EXPECTED_BASE]
    if (not gate_path.is_absolute() or not re.fullmatch(r"[0-9a-f]{40}", expected_head)
            or not re.fullmatch(r"[0-9a-f]{40}", expected_base)):
        raise RuntimeAuthorizationError("agent_live_gate_binding_invalid")

    candidate_root = Path(__file__).resolve().parents[4]
    try:
        gate = load_u02_gate(
            gate_path, expected_head, expected_base=expected_base,
            candidate_root=candidate_root, runtime=True,
        )
        budget_anchor = gate.get("budget_anchor")
        if not isinstance(budget_anchor, dict):
            raise ValueError("agent_live_budget_anchor_missing")
        expected_period = PeriodIdentity(
            period_id=budget_anchor.get("period_id"),
            identity=budget_anchor.get("identity"),
            config_sha256=budget_anchor.get("config_sha256"),
            policy_id=budget_anchor.get("policy_id", POLICY_ID),
        )
        binding = resolve_u03_runtime_binding(expected_period)
        guard = open_u03_runtime_guard(binding)
    except Exception:
        raise RuntimeAuthorizationError("agent_live_authorization_invalid") from None

    return {
        "u02_gate_path": gate_path,
        "expected_head": expected_head,
        "expected_base": expected_base,
        "candidate_root": candidate_root,
        "expected_period": expected_period,
        "budget_guard": guard,
        "model_purpose": "u03_analysis",
        "own_budget_guard": True,
        "analysis_only_test_mode": False,
    }


def create_app(**kwargs):
    """Build the normal local API, or an explicitly authorized live-analysis API."""
    authorization = _live_authorization_kwargs(os.environ)
    if authorization is None:
        if _AUTH_KWARGS.intersection(kwargs):
            raise RuntimeAuthorizationError("agent_live_authorization_must_use_environment")
        return _create_app(**kwargs)

    if _AUTH_KWARGS.intersection(kwargs):
        guard = authorization["budget_guard"]
        guard.close()
        raise RuntimeAuthorizationError("agent_live_authorization_override_forbidden")
    try:
        return _create_app(**kwargs, **authorization)
    except Exception:
        authorization["budget_guard"].close()
        raise
