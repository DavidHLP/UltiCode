"""Create and activate the fixed one-shot local Agent smoke budget."""

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))

from authorized_budget_period import (
    MANUAL_LIVE_TEST_PERIOD_ID, MANUAL_LIVE_TEST_POLICY_ID, PeriodIdentity,
    _parent, prepare_period, read_period_identity,
)
from agent_service.runtime_authorization import shared_guard_path
from dav58_live_guard import IncrementalGuard
from model_budget import ModelBudget, authorization_slot, authorized_period_config_sha256


def main() -> None:
    config_sha256 = authorized_period_config_sha256(MANUAL_LIVE_TEST_POLICY_ID)
    expected = PeriodIdentity(
        MANUAL_LIVE_TEST_PERIOD_ID, config_sha256, "0" * 32, MANUAL_LIVE_TEST_POLICY_ID,
    )
    slot = authorization_slot(expected)
    period_path = slot / "period"
    if period_path.exists():
        identity = read_period_identity(
            period_path, period_id=MANUAL_LIVE_TEST_PERIOD_ID,
            config_sha256=config_sha256, policy_id=MANUAL_LIVE_TEST_POLICY_ID,
        )
        budget = ModelBudget.bound(identity)
    else:
        with _parent(slot) as parent:
            os.mkdir(slot.name, 0o700, dir_fd=parent)
            os.fsync(parent)
        prepared = prepare_period(
            period_path, MANUAL_LIVE_TEST_PERIOD_ID, config_sha256,
            policy_id=MANUAL_LIVE_TEST_POLICY_ID,
        )
        identity = prepared.identity
        budget = ModelBudget.bind_prepared(identity)
        budget.activate()

    snapshot = budget.snapshot()
    if (snapshot.get("state") != "active" or snapshot.get("sql_gate") != "active"
            or snapshot.get("attempts") != 0 or snapshot.get("reserved_micro_usd") != 0
            or snapshot.get("actual_micro_usd") != 0 or snapshot.get("halted")
            or snapshot.get("unknown_usage_attempts") or snapshot.get("unsettled_attempts")):
        raise RuntimeError("manual Agent budget is not an unused active period")

    guard = IncrementalGuard(
        shared_guard_path(identity), period_identity=identity.identity,
        config_sha256=identity.config_sha256, budget=budget,
    )
    guard.close()
    print("one-shot local Agent budget and guard prepared: maximum $182.00; no provider request made")


if __name__ == "__main__":
    main()
