"""Uvicorn factory with explicit live-analysis and persistence opt-ins."""

from __future__ import annotations

import os
from ipaddress import ip_address
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

from authorized_budget_period import (
    MANUAL_LIVE_TEST_PERIOD_ID, MANUAL_LIVE_TEST_POLICY_ID, PeriodIdentity,
    policy_for, read_period_identity,
)
from agent_service.app import create_app as _create_app
from agent_service.runtime_accounting import read_active_period_identity
from agent_service.runtime_authorization import open_u03_runtime_guard, resolve_u03_runtime_binding
from model_budget import MODEL_ALIAS, authorization_slot, authorized_period_config_sha256


class RuntimeAuthorizationError(RuntimeError):
    """The live analysis startup configuration is missing or invalid."""


_LIVE_ANALYSIS = "ULTICODE_AGENT_LIVE_ANALYSIS"
_MANUAL_ANALYSIS_ONLY = "ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY"
_BUDGET_POLICY = "ULTICODE_AGENT_BUDGET_POLICY"
_PERSISTENCE_ENABLED = "ULTICODE_AGENT_PERSISTENCE_ENABLED"
_RETIRED_ENV = (
    "ULTICODE_AGENT_U02_GATE_PATH",
    "ULTICODE_AGENT_EXPECTED_HEAD",
    "ULTICODE_AGENT_EXPECTED_BASE",
)
_RETIRED_KWARGS = {"u02_gate_path", "expected_head", "expected_base", "candidate_root"}
_AUTH_KWARGS = {
    *_RETIRED_KWARGS, "expected_period", "budget_guard", "model_purpose",
    "own_budget_guard", "analysis_only_test_mode", "persistence_enabled",
    "unbudgeted_development_mode",
}


def _is_loopback_origin(value: str) -> bool:
    try:
        parsed = urlsplit(value)
        host = parsed.hostname
        return bool(
            host and parsed.scheme in {"http", "https"}
            and (host.lower() == "localhost" or ip_address(host).is_loopback)
        )
    except ValueError:
        return False


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
    if _BUDGET_POLICY in environ:
        raise RuntimeAuthorizationError("agent_manual_test_budget_override_forbidden")
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
        "expected_period": expected_period,
        "budget_guard": guard,
        "model_purpose": "u03_analysis",
        "own_budget_guard": True,
        "analysis_only_test_mode": True,
    }


def _live_authorization_kwargs(environ: dict[str, str]) -> dict[str, Any] | None:
    if any(name in environ for name in _RETIRED_ENV):
        raise RuntimeAuthorizationError("agent_retired_gate_configuration")
    enabled = environ.get(_LIVE_ANALYSIS, "0")
    manual = environ.get(_MANUAL_ANALYSIS_ONLY, "0")
    if enabled not in {"0", "1"}:
        raise RuntimeAuthorizationError("agent_live_analysis_flag_invalid")
    if manual not in {"0", "1"}:
        raise RuntimeAuthorizationError("agent_manual_analysis_flag_invalid")
    if enabled == "0":
        if _BUDGET_POLICY in environ or manual == "1":
            raise RuntimeAuthorizationError("agent_live_authorization_disabled")
        return None
    if manual == "1":
        return _manual_live_authorization_kwargs(environ)
    if not environ.get("DEEPSEEK_API_KEY"):
        raise RuntimeAuthorizationError("agent_live_provider_credentials_missing")
    policy_id = environ.get(_BUDGET_POLICY)
    if not policy_id:
        if not _is_loopback_origin(environ.get("ULTICODE_AGENT_ORIGIN", "")):
            raise RuntimeAuthorizationError("agent_live_budget_policy_missing")
        return {
            "expected_period": None,
            "budget_guard": None,
            "model_purpose": "u03_analysis",
            "own_budget_guard": False,
            "analysis_only_test_mode": False,
            "unbudgeted_development_mode": True,
        }
    if policy_id == MANUAL_LIVE_TEST_POLICY_ID:
        raise RuntimeAuthorizationError("agent_manual_budget_requires_analysis_only")
    try:
        policy_for(policy_id)
    except Exception:
        raise RuntimeAuthorizationError("agent_live_budget_policy_invalid") from None
    try:
        expected_period = read_active_period_identity(policy_id)
        binding = resolve_u03_runtime_binding(expected_period)
        guard = open_u03_runtime_guard(binding)
    except Exception:
        raise RuntimeAuthorizationError("agent_live_authorization_invalid") from None

    return {
        "expected_period": expected_period,
        "budget_guard": guard,
        "model_purpose": "u03_analysis",
        "own_budget_guard": True,
        "analysis_only_test_mode": False,
        "unbudgeted_development_mode": False,
    }


def _persistence_enabled(environ: dict[str, str]) -> bool:
    enabled = environ.get(_PERSISTENCE_ENABLED, "0")
    if enabled not in {"0", "1"}:
        raise RuntimeAuthorizationError("agent_persistence_flag_invalid")
    return enabled == "1"


def create_app(**kwargs):
    """Build the local API with explicitly selected analysis and persistence modes."""
    persistence_enabled = _persistence_enabled(os.environ)
    authorization = _live_authorization_kwargs(os.environ)
    if authorization is not None and authorization["analysis_only_test_mode"] and persistence_enabled:
        authorization["budget_guard"].close()
        raise RuntimeAuthorizationError("agent_manual_persistence_forbidden")

    retired = _RETIRED_KWARGS.intersection(kwargs)
    if retired:
        if authorization is not None and authorization["budget_guard"] is not None:
            authorization["budget_guard"].close()
        raise RuntimeAuthorizationError("agent_retired_gate_configuration")
    if _AUTH_KWARGS.intersection(kwargs):
        if authorization is not None and authorization["budget_guard"] is not None:
            authorization["budget_guard"].close()
        raise RuntimeAuthorizationError("agent_live_authorization_override_forbidden")
    if authorization is None:
        return _create_app(**kwargs, persistence_enabled=persistence_enabled)

    try:
        return _create_app(**kwargs, persistence_enabled=persistence_enabled, **authorization)
    except Exception:
        if authorization["budget_guard"] is not None:
            authorization["budget_guard"].close()
        raise
