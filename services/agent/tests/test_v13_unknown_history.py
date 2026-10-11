import copy

import pytest

from authorized_budget_period import PeriodError, policy_for
from revalidation_history import _check_unknown_rollover


def test_v13_retains_full_history_and_three_unknown_liabilities():
    previous = policy_for("acceptance-revalidation-v12")
    policy = policy_for("acceptance-revalidation-v13")
    history = policy["history"]
    assert history["attempts"] == previous["history"]["attempts"] + 5 == 988
    assert history["known_actual_micro_usd"] == 262_218 + 697
    assert history["known_committed_micro_usd"] == 8_643_314 + 48_000
    assert history["unknown_attempts"] == 3
    assert history["unknown_encumbrance_micro_usd"] == 3 * 786_432
    assert history["cumulative_limit_micro_usd"] == 200_000_000
    assert history["cumulative_attempt_limit"] == 988 + policy["attempts"]
    assert policy["lanes"] == previous["lanes"]
    assert (history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
            + policy["limit_micro_usd"] <= history["cumulative_limit_micro_usd"])


@pytest.mark.parametrize("mutation", [None, "unknown", "pending", "request", "active", "unsettled", "actual", "commit"])
def test_v12_unknown_remains_sealed(mutation):
    snapshot = {"state": "halted", "sql_gate": "halted", "halted": 1,
                "attempts": 5, "actual_micro_usd": 697, "committed_micro_usd": 48_000,
                "unknown_usage_attempts": 1, "unsettled_attempts": 0}
    guard = {"halted": True, "pending_micro_usd": 786_432,
             "receipts": [{"status": "settled"} for _ in range(4)] + [{
                 "status": "unknown_or_unsafe", "reason": "network_or_read_failure",
                 "attempt_id": "d7026201-c803-4d20-8296-5632575bd0f9",
                 "lane": "prior_development", "reserved_micro_usd": 786_432,
                 "request_sha256": "97915b2819a4cfa4aba4c4b979a7514dc49e93412ba3efc49e87473b722dc0ac"}]}
    altered = copy.deepcopy(guard)
    if mutation == "unknown":
        snapshot["unknown_usage_attempts"] = 0
    elif mutation == "pending":
        altered["pending_micro_usd"] = 0
    elif mutation == "request":
        altered["receipts"][-1]["request_sha256"] = "0" * 64
    elif mutation == "active":
        snapshot["state"] = "active"
    elif mutation == "unsettled":
        snapshot["unsettled_attempts"] = 1
    elif mutation == "actual":
        snapshot["actual_micro_usd"] = 0
    elif mutation == "commit":
        snapshot["committed_micro_usd"] = 697
    if mutation:
        with pytest.raises(PeriodError):
            _check_unknown_rollover(snapshot, altered, predecessor="v12")
    else:
        _check_unknown_rollover(snapshot, altered, predecessor="v12")
