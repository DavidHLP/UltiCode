import hashlib
import json
from types import SimpleNamespace

import pytest

from authorized_budget_period import PeriodError, policy_for


@pytest.mark.parametrize("mutation", [None, "fingerprint", "state", "sql_gate", "halted",
                                     "unknown_usage_attempts", "unsettled_attempts", "attempts",
                                     "actual_micro_usd", "committed_micro_usd", "guard", "receipts"])
def test_v19_binding_requires_intact_settled_and_halted_v18(monkeypatch, tmp_path, mutation):
    import model_budget
    import revalidation_history as history
    from dav58_live_guard import IncrementalGuard

    accounting = tmp_path / "accounting"
    accounting.mkdir(mode=0o700)
    guard_name = f'dav58-increment-{history.V18_ROLLOVER_IDENTITY["identity"]}.json'
    guard = {"halted": mutation != "guard", "receipts": []}
    pins = {}
    for name, raw in {"budget.sqlite3": b"sealed fixture", "binding.json": b"{}",
                      guard_name: json.dumps(guard).encode()}.items():
        path = accounting / name
        path.write_bytes(raw)
        path.chmod(0o600)
        pins[name] = hashlib.sha256(raw).hexdigest()
    monkeypatch.setattr(history, "V18_ROLLOVER_SHA256", pins)
    real_validate = history._validate_rollover_history
    predecessor = {"sha256": {"older": "retained"}}
    def previous(sources, *, settled_policy="v1"):
        if settled_policy == "v17":
            return predecessor
        return real_validate(sources, settled_policy=settled_policy)
    monkeypatch.setattr(history, "_validate_rollover_history", previous)
    monkeypatch.setattr(model_budget, "authorization_slot", lambda expected: tmp_path)
    snapshot = {"state": "halted", "sql_gate": "halted", "halted": 1, "attempts": 104,
                "actual_micro_usd": 35994, "committed_micro_usd": 932400,
                "unknown_usage_attempts": 0, "unsettled_attempts": 0}
    if mutation == "fingerprint":
        (accounting / "budget.sqlite3").write_bytes(b"changed")
    elif mutation in snapshot:
        snapshot[mutation] = "active" if mutation in {"state", "sql_gate"} else 0 if mutation == "halted" else snapshot[mutation] + 1
    def sealed_view(expected, retained):
        assert expected.policy_id == "acceptance-revalidation-v18"
        assert retained is predecessor
        return SimpleNamespace(snapshot=lambda: snapshot, policy={})
    monkeypatch.setattr(model_budget.ModelBudget, "_sealed_history_view", sealed_view)
    def check_receipts(receipt_view, *args):
        assert receipt_view["halted"] is False
        if mutation == "receipts":
            raise ValueError("dispatch mismatch")
    monkeypatch.setattr(IncrementalGuard, "_validate_resume", check_receipts)
    if mutation:
        with pytest.raises(PeriodError):
            history.validate_history({}, policy_id="acceptance-revalidation-v19")
    else:
        audit = history.validate_history({}, policy_id="acceptance-revalidation-v19")
        assert audit["sha256"]["older"] == "retained"
        assert audit["baseline"] == dict(policy_for("acceptance-revalidation-v19")["history"])
        assert audit["acceptance_evidence"] is audit["unknown_released"] is False


def test_recovery_revalidation_retains_settled_v18_and_unknown_liabilities():
    old = policy_for("acceptance-revalidation-v18")
    policy = policy_for("acceptance-revalidation-v19")
    history = policy["history"]
    assert history["attempts"] == old["history"]["attempts"] + 104 == 1590
    assert history["known_actual_micro_usd"] == old["history"]["known_actual_micro_usd"] + 35994 == 471411
    assert history["known_committed_micro_usd"] == old["history"]["known_committed_micro_usd"] + 932400 == 14092514
    assert history["unknown_attempts"] == old["history"]["unknown_attempts"] == 3
    assert history["unknown_encumbrance_micro_usd"] == old["history"]["unknown_encumbrance_micro_usd"] == 2359296
    assert history["cumulative_limit_micro_usd"] == old["history"]["cumulative_limit_micro_usd"] == 200000000
    assert history["cumulative_attempt_limit"] == history["attempts"] + policy["attempts"] == 1831
    assert policy["lanes"] == old["lanes"]
    assert policy["limit_micro_usd"] == old["limit_micro_usd"]
    assert (history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
            + policy["limit_micro_usd"] <= history["cumulative_limit_micro_usd"])


def test_array_range_revalidation_retains_settled_v17_and_unknown_liabilities():
    old = policy_for("acceptance-revalidation-v17")
    policy = policy_for("acceptance-revalidation-v18")
    history = policy["history"]
    assert history["attempts"] == old["history"]["attempts"] + 96 == 1486
    assert history["known_actual_micro_usd"] == old["history"]["known_actual_micro_usd"] + 33723 == 435417
    assert history["known_committed_micro_usd"] == old["history"]["known_committed_micro_usd"] + 864000 == 13160114
    assert history["unknown_attempts"] == old["history"]["unknown_attempts"] == 3
    assert history["unknown_encumbrance_micro_usd"] == old["history"]["unknown_encumbrance_micro_usd"] == 2359296
    assert history["cumulative_limit_micro_usd"] == old["history"]["cumulative_limit_micro_usd"] == 200000000
    assert history["cumulative_attempt_limit"] == history["attempts"] + policy["attempts"] == 1727
    assert policy["lanes"] == old["lanes"]
    assert policy["limit_micro_usd"] == old["limit_micro_usd"]
    assert (history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
            + policy["limit_micro_usd"] <= history["cumulative_limit_micro_usd"])


def test_confirmation_revalidation_retains_settled_v15_and_unknown_liabilities():
    old = policy_for("acceptance-revalidation-v15")
    policy = policy_for("acceptance-revalidation-v16")
    history = policy["history"]
    assert history["attempts"] == old["history"]["attempts"] + 103 == 1286
    assert history["known_actual_micro_usd"] == old["history"]["known_actual_micro_usd"] + 35648 == 365717
    assert history["known_committed_micro_usd"] == old["history"]["known_committed_micro_usd"] + 922800 == 11363714
    assert history["unknown_attempts"] == old["history"]["unknown_attempts"] == 3
    assert history["unknown_encumbrance_micro_usd"] == old["history"]["unknown_encumbrance_micro_usd"] == 2359296
    assert history["cumulative_limit_micro_usd"] == old["history"]["cumulative_limit_micro_usd"] == 200000000
    assert history["cumulative_attempt_limit"] == history["attempts"] + policy["attempts"] == 1527
    assert policy["lanes"] == old["lanes"]
    assert policy["limit_micro_usd"] == old["limit_micro_usd"]
    assert (history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
            + policy["limit_micro_usd"] <= history["cumulative_limit_micro_usd"])


def test_cancellation_revalidation_retains_settled_v16_and_unknown_liabilities():
    old = policy_for("acceptance-revalidation-v16")
    policy = policy_for("acceptance-revalidation-v17")
    history = policy["history"]
    assert history["attempts"] == old["history"]["attempts"] + 104 == 1390
    assert history["known_actual_micro_usd"] == old["history"]["known_actual_micro_usd"] + 35977 == 401694
    assert history["known_committed_micro_usd"] == old["history"]["known_committed_micro_usd"] + 932400 == 12296114
    assert history["unknown_attempts"] == old["history"]["unknown_attempts"] == 3
    assert history["unknown_encumbrance_micro_usd"] == old["history"]["unknown_encumbrance_micro_usd"] == 2359296
    assert history["cumulative_limit_micro_usd"] == old["history"]["cumulative_limit_micro_usd"] == 200000000
    assert history["cumulative_attempt_limit"] == history["attempts"] + policy["attempts"] == 1631
    assert policy["lanes"] == old["lanes"]
    assert policy["limit_micro_usd"] == old["limit_micro_usd"]
    assert (history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
            + policy["limit_micro_usd"] <= history["cumulative_limit_micro_usd"])
