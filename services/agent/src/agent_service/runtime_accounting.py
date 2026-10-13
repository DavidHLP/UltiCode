"""Read-only selection and reconciliation of existing live Agent accounting."""

from __future__ import annotations

import os

import authorized_budget_period as period
from authorized_budget_period import PeriodIdentity, read_period, read_period_identity
from dav58_live_guard import IncrementalGuard
from model_budget import authorization_slot, authorized_period_config_sha256


def read_active_period_identity(policy_id: str) -> PeriodIdentity:
    """Read one existing active policy period without creating or activating it."""
    try:
        policy = period.policy_for(policy_id)
        config_sha256 = authorized_period_config_sha256(policy_id)
        if not {"u03_analysis", "u03_citation_judge"} <= set(policy["lanes"]):
            raise ValueError("model_budget_purpose_blocked")
        slot = authorization_slot(PeriodIdentity("runtime", config_sha256, "0" * 32, policy_id))
        path = slot / "period"
        with period._parent(path / "identity.json") as directory_fd:
            identity_fd = period._file(directory_fd, "identity.json", os.O_RDONLY)
            try:
                records = period._read(identity_fd)
            finally:
                os.close(identity_fd)
        if len(records) != 1 or not isinstance(records[0], dict):
            raise ValueError("invalid period identity")
        record = records[0]
        if (record.get("policy_id") != policy_id
                or record.get("config_sha256") != config_sha256
                or record.get("path") != str(path)):
            raise ValueError("period identity policy mismatch")
        expected = read_period_identity(
            path,
            period_id=record.get("period_id"),
            config_sha256=config_sha256,
            policy_id=policy_id,
        )
        if read_period(path, expected).state != "active":
            raise ValueError("budget period is not active")
        return expected
    except (OSError, ValueError, TypeError, KeyError, RecursionError, UnicodeError):
        raise ValueError("active authorized budget period unavailable") from None


def current_budget_anchor(
    expected: PeriodIdentity, budget, snapshot: dict[str, object], guard_data: dict[str, object],
) -> dict[str, object]:
    """Reconcile the existing policy ledger and guard without acceptance evidence."""
    if (expected.policy_id not in period.REVALIDATION_POLICY_IDS
            or getattr(budget, "_identity", None) != expected):
        raise ValueError("authorized revalidation policy required")
    attempts = snapshot.get("attempts")
    actual = snapshot.get("actual_micro_usd")
    if (snapshot.get("state") != "active" or snapshot.get("sql_gate") != "active"
            or snapshot.get("halted") or snapshot.get("unknown_usage_attempts")
            or snapshot.get("unsettled_attempts") or type(attempts) is not int
            or type(actual) is not int or not isinstance(guard_data.get("receipts"), list)):
        raise ValueError("fresh budget unresolved")
    try:
        IncrementalGuard._validate_resume(
            guard_data, expected.identity, expected.config_sha256, budget.policy, budget,
        )
        with budget._accounting() as (db, _):
            rows = db.execute(
                "SELECT rowid,attempt_id,purpose,actual_micro_usd,usage_known,settled "
                "FROM attempts ORDER BY rowid"
            ).fetchall()
    except (OSError, ValueError, TypeError, KeyError, RuntimeError):
        raise ValueError("fresh budget reconciliation failed") from None
    if (len(rows) != attempts or len(guard_data["receipts"]) != len(rows)
            or any(type(row[3]) is not int for row in rows)
            or sum(row[3] for row in rows) != actual
            or guard_data.get("settled_peak_micro_usd") != actual):
        raise ValueError("fresh budget counters disagree")
    return {
        **expected.__dict__,
        "attempts": attempts,
        "actual_micro_usd": actual,
    }
