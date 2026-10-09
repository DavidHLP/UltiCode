import copy

import pytest

from authorized_budget_period import PeriodError, REVALIDATION_V3_POLICY
from revalidation_history import _check_unknown_rollover


def sealed_history():
    snapshot = {"state": "halted", "sql_gate": "halted", "halted": 1,
                "attempts": 25, "actual_micro_usd": 5928, "committed_micro_usd": 240_000,
                "unknown_usage_attempts": 1, "unsettled_attempts": 0}
    last = {"status": "unknown_or_unsafe", "reason": "network_or_read_failure",
            "attempt_id": "630b0d60-4270-47ab-b42c-4e407df78a6a", "lane": "prior_development",
            "reserved_micro_usd": 786_432,
            "request_sha256": "26a76e0d7c1289b4a3b8c17a18aae3ca8f2015ec857c213782372ab8cf426dec"}
    return snapshot, {"halted": True, "pending_micro_usd": 786_432,
                      "receipts": [{"status": "settled"} for _ in range(24)] + [last]}


@pytest.mark.parametrize("target,key,value", [
    ("snapshot", "state", "active"), ("snapshot", "halted", 0),
    ("snapshot", "unknown_usage_attempts", 0), ("snapshot", "committed_micro_usd", 5928),
    ("guard", "pending_micro_usd", 0), ("guard", "halted", False),
    ("last", "status", "settled"), ("last", "attempt_id", "foreign-attempt"),
    ("last", "request_sha256", "0" * 64),
])
def test_unknown_cannot_be_released_or_rebound(target, key, value):
    snapshot, guard = sealed_history()
    _check_unknown_rollover(snapshot, guard)
    altered = copy.deepcopy({"snapshot": snapshot, "guard": guard})
    row = altered["guard"]["receipts"][-1] if target == "last" else altered[target]
    row[key] = value
    with pytest.raises(PeriodError):
        _check_unknown_rollover(altered["snapshot"], altered["guard"])


def test_recovery_budget_retains_both_unknowns_and_reserves_complete_nominal_plan():
    policy = REVALIDATION_V3_POLICY
    history = policy["history"]
    assert history["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == 2 * 786_432
    assert history["known_committed_micro_usd"] == 1_138_514 + 24 * 9600
    assert history["attempts"] + policy["attempts"] == 430
    assert policy["lanes"]["prior_development"]["attempts"] == 80
    caps = [(v["attempts"], (v["prompt_token_cap"] * 3 + v["completion_token_cap"] * 12 + 9) // 10)
            for v in policy["lanes"].values()]
    assert sum(n for n, _ in caps) == 241
    reservations = sum(n * cost for n, cost in caps)
    assert reservations == 2_092_800
    peak = history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"] + reservations - min(cost for _, cost in caps) + 786_432
    assert peak == 5_816_210 <= history["cumulative_limit_micro_usd"] == 5_820_000
