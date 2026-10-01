from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest

from model_budget import (
    BudgetLimitExceeded,
    MAX_PROMPT_TOKENS,
    ModelBudget,
    worst_case_micro_usd,
)


def test_peak_price_upper_bound_and_evaluation_reserve(tmp_path: Path) -> None:
    assert worst_case_micro_usd(24_000, 4_000) == 12_000
    budget = ModelBudget(tmp_path / "budget.sqlite3")
    reservation = budget.reserve(100, 100)
    assert reservation.reserved_micro_usd == worst_case_micro_usd(100, 100)
    assert budget.snapshot()["attempts"] == 1
    for _ in range(139):
        budget.reserve(1, 1)
    assert budget.snapshot()["attempts"] == 140
    with pytest.raises(BudgetLimitExceeded, match="evaluation reserve"):
        budget.reserve(MAX_PROMPT_TOKENS, 4_000)


def test_ledger_is_shared_across_instances_and_settlement_is_single_use(tmp_path: Path) -> None:
    path = tmp_path / "state" / "budget.sqlite3"
    first = ModelBudget(path)
    reservation = first.reserve(20, 30)
    second = ModelBudget(path)
    assert second.snapshot()["attempts"] == 1
    actual = second.settle(reservation, {"prompt_tokens": 10, "completion_tokens": 4})
    assert actual == worst_case_micro_usd(10, 4)
    with pytest.raises(RuntimeError, match="already settled"):
        first.settle(reservation, None)
    assert second.snapshot()["attempts"] == 1


def test_concurrent_reservations_cannot_exceed_request_limit(tmp_path: Path) -> None:
    path = tmp_path / "budget.sqlite3"

    def reserve(_: int) -> bool:
        try:
            ModelBudget(path).reserve(1, 1)
            return True
        except BudgetLimitExceeded:
            return False

    with ThreadPoolExecutor(max_workers=24) as workers:
        outcomes = list(workers.map(reserve, range(224)))
    assert sum(outcomes) == 140  # 60 requests remain protected for frozen evaluation.
    assert ModelBudget(path).snapshot()["attempts"] == 140


def test_frozen_evaluation_requires_and_consumes_whole_group_reservation(tmp_path: Path) -> None:
    budget = ModelBudget(tmp_path / "budget.sqlite3")
    with pytest.raises(BudgetLimitExceeded, match="not reserved"):
        budget.reserve(100, 100, purpose="frozen_evaluation")
    budget.begin_evaluation_group(60)
    budget.reserve(100, 100, purpose="frozen_evaluation")
    with pytest.raises(BudgetLimitExceeded, match="insufficient"):
        budget.begin_evaluation_group(60)


def test_ordinary_calls_preserve_started_evaluation_group_size(tmp_path: Path) -> None:
    budget = ModelBudget(tmp_path / "budget.sqlite3")
    budget.begin_evaluation_group(100)
    for _ in range(100):
        budget.reserve(1, 1)

    with pytest.raises(BudgetLimitExceeded, match="evaluation reserve"):
        budget.reserve(1, 1)
    assert budget.snapshot()["evaluation_remaining_calls"] == 100


def test_unknown_usage_remains_charged_at_reservation(tmp_path: Path) -> None:
    budget = ModelBudget(tmp_path / "budget.sqlite3")
    reservation = budget.reserve(50, 200)
    before = budget.snapshot()["reserved_micro_usd"]
    assert budget.settle(reservation, None) is None
    assert budget.snapshot()["reserved_micro_usd"] == before


def test_over_bound_usage_halts_the_shared_ledger(tmp_path: Path) -> None:
    """A provider usage above the attempt's reserved bounds halts the ledger."""
    path = tmp_path / "budget.sqlite3"
    budget = ModelBudget(path)
    reservation = budget.reserve(10, 10)
    with pytest.raises(BudgetLimitExceeded, match="halted"):
        budget.settle(reservation, {"prompt_tokens": 500, "completion_tokens": 10})
    # The usage is recorded and the halt survives a restart / new instance.
    restarted = ModelBudget(path)
    assert restarted.snapshot()["halted"] == 1
    assert restarted.snapshot()["actual_micro_usd"] == worst_case_micro_usd(500, 10)
    with pytest.raises(BudgetLimitExceeded, match="halted"):
        restarted.reserve(1, 1)
    assert restarted.snapshot()["attempts"] == 1


def test_partial_known_over_bound_usage_halts_the_shared_ledger(tmp_path: Path) -> None:
    budget = ModelBudget(tmp_path / "budget.sqlite3")
    reservation = budget.reserve(10, 10)
    with pytest.raises(BudgetLimitExceeded, match="halted"):
        budget.settle(reservation, {"prompt_tokens": 500, "completion_tokens": None})
    assert ModelBudget(budget.path).snapshot()["halted"] == 1


def test_usage_within_the_reserved_bounds_does_not_halt(tmp_path: Path) -> None:
    budget = ModelBudget(tmp_path / "budget.sqlite3")
    reservation = budget.reserve(100, 100)
    budget.settle(reservation, {"prompt_tokens": 40, "completion_tokens": 30})
    assert budget.snapshot()["halted"] == 0
    budget.reserve(1, 1)
    assert budget.snapshot()["attempts"] == 2
