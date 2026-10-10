import copy
import hashlib
import json

import pytest

from authorized_budget_period import PeriodError, REVALIDATION_V3_POLICY, REVALIDATION_V4_POLICY, REVALIDATION_V5_POLICY, REVALIDATION_V6_POLICY, policy_for
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


def test_v4_preserves_sealed_liabilities_and_complete_four_round_plan():
    policy = policy_for("acceptance-revalidation-v4")
    assert policy is REVALIDATION_V4_POLICY
    history = policy["history"]
    previous = REVALIDATION_V3_POLICY["history"]
    assert history["attempts"] == previous["attempts"] + 115 == 304
    assert history["known_actual_micro_usd"] == previous["known_actual_micro_usd"] + 32_484
    assert history["known_committed_micro_usd"] == previous["known_committed_micro_usd"] + 976_800
    assert history["unknown_attempts"] == previous["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == previous["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == REVALIDATION_V3_POLICY["lanes"]
    caps = [(v["attempts"], (v["prompt_token_cap"] * 3 + v["completion_token_cap"] * 12 + 9) // 10)
            for v in policy["lanes"].values()]
    assert sum(n for n, _ in caps) == policy["attempts"] == 241
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 545
    retained = history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
    assert retained + policy["limit_micro_usd"] == history["cumulative_limit_micro_usd"] == 6_800_000
    assert retained + sum(n * cost for n, cost in caps) - min(cost for _, cost in caps) + 786_432 == 6_793_010


def test_v5_retains_failed_run_without_reducing_complete_plan():
    policy, previous = REVALIDATION_V5_POLICY, REVALIDATION_V4_POLICY
    history = policy["history"]
    assert policy_for("acceptance-revalidation-v5") is policy
    assert history["attempts"] == previous["history"]["attempts"] + 35 == 339
    assert history["known_actual_micro_usd"] == previous["history"]["known_actual_micro_usd"] + 9123
    assert history["known_committed_micro_usd"] == previous["history"]["known_committed_micro_usd"] + 336_000
    assert history["unknown_attempts"] == 2 and history["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == previous["lanes"]
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 580
    retained = history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
    assert retained + policy["limit_micro_usd"] == history["cumulative_limit_micro_usd"] == 7_140_000
    assert retained + 2_092_800 - 4800 + 786_432 == 7_129_010


def test_v6_retains_all_failed_attempts_under_owner_cumulative_ceiling():
    policy = policy_for("acceptance-revalidation-v6")
    assert policy is REVALIDATION_V6_POLICY
    history = policy["history"]
    previous = REVALIDATION_V5_POLICY["history"]
    assert history["attempts"] == previous["attempts"] + 79 == 418
    assert history["known_actual_micro_usd"] == previous["known_actual_micro_usd"] + 20_433 == 110_275
    assert history["known_committed_micro_usd"] == previous["known_committed_micro_usd"] + 758_400 == 3_440_114
    assert history["unknown_attempts"] == previous["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == previous["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == REVALIDATION_V5_POLICY["lanes"]
    assert policy["attempts"] == 241
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 659
    assert history["cumulative_limit_micro_usd"] == 100_000_000
    assert policy["limit_micro_usd"] == 2_885_422
    retained = history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
    assert retained + policy["limit_micro_usd"] == 7_898_400 < history["cumulative_limit_micro_usd"]


def test_v7_retains_sealed_v6_and_complete_reviewed_acceptance_package():
    policy = policy_for("acceptance-revalidation-v7")
    history = policy["history"]
    previous = REVALIDATION_V6_POLICY["history"]
    assert history["attempts"] == previous["attempts"] + 105 == 523
    assert history["known_actual_micro_usd"] == previous["known_actual_micro_usd"] + 28_533 == 138_808
    assert history["known_committed_micro_usd"] == previous["known_committed_micro_usd"] + 928_800 == 4_368_914
    assert history["unknown_attempts"] == previous["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == previous["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == REVALIDATION_V6_POLICY["lanes"]
    assert policy["attempts"] == 241
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 764
    assert history["cumulative_limit_micro_usd"] == 100_000_000
    assert policy["limit_micro_usd"] == 2_885_422
    retained = history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
    assert retained + policy["limit_micro_usd"] == 8_827_200 < history["cumulative_limit_micro_usd"]


def test_v8_retains_sealed_v7_under_authorized_two_hundred_dollar_ceiling():
    policy = policy_for("acceptance-revalidation-v8")
    previous = policy_for("acceptance-revalidation-v7")
    history = policy["history"]
    assert history["attempts"] == previous["history"]["attempts"] + 104 == 627
    assert history["known_actual_micro_usd"] == previous["history"]["known_actual_micro_usd"] + 28_239 == 167_047
    assert history["known_committed_micro_usd"] == previous["history"]["known_committed_micro_usd"] + 927_600 == 5_296_514
    assert history["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == previous["lanes"]
    assert policy["attempts"] == 241
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 868
    assert policy["limit_micro_usd"] == 2_885_422
    assert history["cumulative_limit_micro_usd"] == 200_000_000
    assert history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"] + policy["limit_micro_usd"] == 9_754_800


def test_v9_retains_sealed_v8_without_refilling_old_period():
    policy = policy_for("acceptance-revalidation-v9")
    previous = policy_for("acceptance-revalidation-v8")
    history = policy["history"]
    assert history["attempts"] == previous["history"]["attempts"] + 84 == 711
    assert history["known_actual_micro_usd"] == previous["history"]["known_actual_micro_usd"] + 21_639 == 188_686
    assert history["known_committed_micro_usd"] == previous["history"]["known_committed_micro_usd"] + 806_400 == 6_102_914
    assert history["unknown_attempts"] == previous["history"]["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == previous["lanes"]
    assert policy["attempts"] == 241
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 952
    assert policy["limit_micro_usd"] == 2_885_422
    assert history["cumulative_limit_micro_usd"] == 200_000_000
    assert history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"] + policy["limit_micro_usd"] == 10_561_200


def test_v10_retains_sealed_v9_without_refilling_old_period():
    policy = policy_for("acceptance-revalidation-v10")
    previous = policy_for("acceptance-revalidation-v9")
    history = policy["history"]
    assert history["attempts"] == previous["history"]["attempts"] + 104 == 815
    assert history["known_actual_micro_usd"] == previous["history"]["known_actual_micro_usd"] + 28_254 == 216_940
    assert history["known_committed_micro_usd"] == previous["history"]["known_committed_micro_usd"] + 927_600 == 7_030_514
    assert history["unknown_attempts"] == previous["history"]["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == previous["lanes"]
    assert policy["attempts"] == 241
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 1056
    assert policy["limit_micro_usd"] == 2_885_422
    assert history["cumulative_limit_micro_usd"] == 200_000_000
    assert history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"] + policy["limit_micro_usd"] == 11_488_800


def test_v11_retains_failed_v10_without_refilling_old_period():
    policy = policy_for("acceptance-revalidation-v11")
    previous = policy_for("acceptance-revalidation-v10")
    history = policy["history"]
    assert history["attempts"] == previous["history"]["attempts"] + 84 == 899
    assert history["known_actual_micro_usd"] == previous["history"]["known_actual_micro_usd"] + 21_564 == 238_504
    assert history["known_committed_micro_usd"] == previous["history"]["known_committed_micro_usd"] + 806_400 == 7_836_914
    assert history["unknown_attempts"] == previous["history"]["unknown_attempts"] == 2
    assert history["unknown_encumbrance_micro_usd"] == 1_572_864
    assert policy["lanes"] == previous["lanes"]
    assert policy["attempts"] == 241
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"] == 1140
    assert policy["limit_micro_usd"] == 2_885_422
    assert history["cumulative_limit_micro_usd"] == 200_000_000
    assert history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"] + policy["limit_micro_usd"] == 12_295_200


@pytest.mark.parametrize("policy_id", ["acceptance-revalidation-v4", "acceptance-revalidation-v5", "acceptance-revalidation-v6", "acceptance-revalidation-v7", "acceptance-revalidation-v8", "acceptance-revalidation-v9", "acceptance-revalidation-v10", "acceptance-revalidation-v11"])
@pytest.mark.parametrize("mutation", [None, "fingerprint", "active", "unsettled", "unknown", "commit", "receipts"])
def test_binding_rejects_changed_or_unsealed_history(monkeypatch, tmp_path, mutation, policy_id):
    from types import SimpleNamespace
    import model_budget
    import revalidation_history as history
    from dav58_live_guard import IncrementalGuard

    accounting = tmp_path / "accounting"
    accounting.mkdir(mode=0o700)
    pins = {}
    v5 = policy_id == "acceptance-revalidation-v5"
    v6 = policy_id == "acceptance-revalidation-v6"
    v7 = policy_id == "acceptance-revalidation-v7"
    v8 = policy_id == "acceptance-revalidation-v8"
    v9 = policy_id == "acceptance-revalidation-v9"
    v10 = policy_id == "acceptance-revalidation-v10"
    v11 = policy_id == "acceptance-revalidation-v11"
    identity = history.V10_ROLLOVER_IDENTITY if v11 else history.V9_ROLLOVER_IDENTITY if v10 else history.V8_ROLLOVER_IDENTITY if v9 else history.V7_ROLLOVER_IDENTITY if v8 else history.V6_ROLLOVER_IDENTITY if v7 else history.V5_ROLLOVER_IDENTITY if v6 else history.V4_ROLLOVER_IDENTITY if v5 else history.SETTLED_ROLLOVER_IDENTITY
    guard_name = f'dav58-increment-{identity["identity"]}.json'
    for name, raw in {"budget.sqlite3": b"sealed fixture", "binding.json": b"{}",
                      guard_name: json.dumps({"receipts": []}).encode()}.items():
        path = accounting / name
        path.write_bytes(raw)
        path.chmod(0o600)
        pins[name] = hashlib.sha256(raw).hexdigest()
    monkeypatch.setattr(history, "V10_ROLLOVER_SHA256" if v11 else "V9_ROLLOVER_SHA256" if v10 else "V8_ROLLOVER_SHA256" if v9 else "V7_ROLLOVER_SHA256" if v8 else "V6_ROLLOVER_SHA256" if v7 else "V5_ROLLOVER_SHA256" if v6 else "V4_ROLLOVER_SHA256" if v5 else "SETTLED_ROLLOVER_SHA256", pins)
    monkeypatch.setattr(history, "_validate_unknown_rollover_history", lambda sources: {"sha256": {"older": "retained"}})
    if v5 or v6 or v7 or v8 or v9 or v10 or v11:
        real_validate = history._validate_rollover_history
        def previous(sources, *, settled_policy="v1"):
            if settled_policy == ("v9" if v11 else "v8" if v10 else "v7" if v9 else "v6" if v8 else "v5" if v7 else "v4" if v6 else "v3"):
                return {"sha256": {"older": "retained"}}
            return real_validate(sources, settled_policy=settled_policy)
        monkeypatch.setattr(history, "_validate_rollover_history", previous)
    monkeypatch.setattr(model_budget, "authorization_slot", lambda expected: tmp_path)
    snapshot = {"state": "halted", "sql_gate": "halted", "halted": 1, "attempts": 115,
                "actual_micro_usd": 32_484, "committed_micro_usd": 976_800,
                "unknown_usage_attempts": 0, "unsettled_attempts": 0}
    if v5:
        snapshot.update(attempts=35, actual_micro_usd=9123, committed_micro_usd=336_000)
    if v6:
        snapshot.update(attempts=79, actual_micro_usd=20_433, committed_micro_usd=758_400)
    if v7:
        snapshot.update(attempts=105, actual_micro_usd=28_533, committed_micro_usd=928_800)
    if v8:
        snapshot.update(attempts=104, actual_micro_usd=28_239, committed_micro_usd=927_600)
    if v9:
        snapshot.update(attempts=84, actual_micro_usd=21_639, committed_micro_usd=806_400)
    if v10:
        snapshot.update(attempts=104, actual_micro_usd=28_254, committed_micro_usd=927_600)
    if v11:
        snapshot.update(attempts=84, actual_micro_usd=21_564, committed_micro_usd=806_400)
    if mutation == "fingerprint":
        (accounting / "budget.sqlite3").write_bytes(b"changed")
    for case, key, value in [("active", "state", "active"), ("unsettled", "unsettled_attempts", 1),
                             ("unknown", "unknown_usage_attempts", 1), ("commit", "committed_micro_usd", 32_484)]:
        if mutation == case:
            snapshot[key] = value
    predecessors = []
    def sealed_view(expected, predecessor):
        predecessors.append(predecessor)
        return SimpleNamespace(snapshot=lambda: snapshot, policy={})
    monkeypatch.setattr(model_budget.ModelBudget, "_sealed_history_view", sealed_view)
    checked = []
    def validate(*args):
        checked.append(True)
        if mutation == "receipts":
            raise ValueError("dispatch mismatch")
    monkeypatch.setattr(IncrementalGuard, "_validate_resume", validate)
    if mutation:
        with pytest.raises(PeriodError):
            history.validate_history({}, policy_id=policy_id)
    else:
        audit = history.validate_history({}, policy_id=policy_id)
        assert len(predecessors) == 1 and predecessors[0]["sha256"] == {"older": "retained"}
        assert checked and audit["sha256"]["older"] == "retained"
        assert audit["baseline"] == dict(policy_for(policy_id)["history"])
        assert audit["acceptance_evidence"] is audit["unknown_released"] is False
