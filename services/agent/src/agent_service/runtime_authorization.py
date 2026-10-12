"""Shared validation for evidence-bound U03 analysis runtime authorization."""

from __future__ import annotations

import hashlib
import json
import os
import stat
from dataclasses import dataclass
from pathlib import Path

from authorized_budget_period import (
    MANUAL_LIVE_TEST_POLICY_ID, POLICY_ID, PeriodIdentity, policy_for,
)
from dav58_live_guard import IncrementalGuard
from model_budget import ModelBudget, authorization_slot, authorized_model
from agent_service.gate import current_budget_anchor


def _reject_duplicate_keys(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError("duplicate_guard_key")
        value[key] = item
    return value


def _reject_constant(_value):
    raise ValueError("invalid_guard_number")


def shared_guard_path(expected: PeriodIdentity) -> Path:
    return authorization_slot(expected) / "accounting" / f"dav58-increment-{expected.identity}.json"


def read_existing_guard(path: Path) -> tuple[dict, str]:
    if not path.is_absolute():
        raise ValueError("u03_guard_path_must_be_absolute")
    fd = os.open(path, os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW | os.O_CLOEXEC)
    try:
        info = os.fstat(fd)
        if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_uid != os.getuid()
                or stat.S_IMODE(info.st_mode) != 0o600):
            raise ValueError("u03_shared_guard_not_private")
        with os.fdopen(fd, "rb", closefd=False) as stream:
            raw = stream.read()
        value = json.loads(raw, object_pairs_hook=_reject_duplicate_keys, parse_constant=_reject_constant)
    except (OSError, ValueError, TypeError, RecursionError, UnicodeError):
        raise ValueError("u03_shared_guard_invalid") from None
    finally:
        os.close(fd)
    if (not isinstance(value, dict) or value.get("halted") is not False
            or value.get("pending_micro_usd") != 0 or not isinstance(value.get("receipts"), list)):
        raise ValueError("u03_shared_guard_unresolved")
    return value, hashlib.sha256(raw).hexdigest()


@dataclass(frozen=True)
class U03RuntimeBinding:
    expected_period: PeriodIdentity
    budget: ModelBudget
    guard_path: Path
    guard_sha256: str
    anchor: dict[str, object]


def resolve_u03_runtime_binding(expected: PeriodIdentity) -> U03RuntimeBinding:
    """Read and reconcile the existing authorized budget and guard without creating either."""
    policy = policy_for(expected.policy_id)
    if not {"u03_analysis", "u03_citation_judge"} <= set(policy["lanes"]):
        raise ValueError("model_budget_purpose_blocked")

    _, budget = authorized_model(expected)
    snapshot = budget.snapshot()
    if expected.policy_id == MANUAL_LIVE_TEST_POLICY_ID:
        anchor = {
            "identity": expected.identity,
            "period_id": expected.period_id,
            "config_sha256": expected.config_sha256,
            "policy_id": expected.policy_id,
            "attempts": snapshot.get("attempts"),
            "actual_micro_usd": snapshot.get("actual_micro_usd"),
        }
    else:
        anchor = current_budget_anchor(expected) if expected.policy_id != POLICY_ID else current_budget_anchor()
    if (anchor.get("identity") != expected.identity or anchor.get("period_id") != expected.period_id
            or anchor.get("config_sha256") != expected.config_sha256 or anchor.get("policy_id") != expected.policy_id
            or snapshot.get("period_identity") != expected.identity
            or snapshot.get("config_sha256") != expected.config_sha256
            or snapshot.get("attempts") != anchor.get("attempts")
            or snapshot.get("actual_micro_usd") != anchor.get("actual_micro_usd")
            or snapshot.get("state") != "active" or snapshot.get("sql_gate") != "active"
            or snapshot.get("halted") or snapshot.get("unknown_usage_attempts")
            or snapshot.get("unsettled_attempts")):
        raise ValueError("u03_budget_anchor_mismatch")

    guard_path = shared_guard_path(expected)
    guard_data, guard_sha = read_existing_guard(guard_path)
    if (guard_data.get("period_identity") != expected.identity
            or guard_data.get("config_sha256") != expected.config_sha256
            or len(guard_data["receipts"]) != anchor.get("attempts")
            or guard_data.get("settled_peak_micro_usd") != anchor.get("actual_micro_usd")):
        raise ValueError("u03_guard_prefix_mismatch")
    return U03RuntimeBinding(expected, budget, guard_path, guard_sha, anchor)


def open_u03_runtime_guard(binding: U03RuntimeBinding) -> IncrementalGuard:
    """Lock the verified shared journal for one server process; never create a fresh journal."""
    expected = binding.expected_period
    guard = IncrementalGuard(
        binding.guard_path,
        resume_sha256=binding.guard_sha256,
        period_identity=expected.identity,
        config_sha256=expected.config_sha256,
        budget=binding.budget if expected.policy_id != POLICY_ID else None,
    )
    state = guard.state
    if (not isinstance(state, dict) or not isinstance(state.get("receipts"), list)
            or len(state["receipts"]) != binding.anchor.get("attempts")
            or state.get("settled_peak_micro_usd") != binding.anchor.get("actual_micro_usd")
            or state.get("pending_micro_usd") != 0 or state.get("halted") is not False):
        guard.close()
        raise ValueError("u03_shared_guard_budget_mismatch")
    return guard
