from authorized_budget_period import policy_for


def test_json_acceptance_retains_all_known_costs_and_unknown_liabilities():
    old = policy_for("acceptance-revalidation-v14")
    policy = policy_for("acceptance-revalidation-v15")
    history = policy["history"]
    assert history["attempts"] == old["history"]["attempts"] + 97 == 1183
    assert history["known_actual_micro_usd"] == old["history"]["known_actual_micro_usd"] + 33416 == 330069
    assert history["known_committed_micro_usd"] == old["history"]["known_committed_micro_usd"] + 872400 == 10440914
    assert history["unknown_attempts"] == old["history"]["unknown_attempts"] == 3
    assert history["unknown_encumbrance_micro_usd"] == old["history"]["unknown_encumbrance_micro_usd"] == 2359296
    assert history["cumulative_limit_micro_usd"] == old["history"]["cumulative_limit_micro_usd"] == 200000000
    assert history["cumulative_attempt_limit"] == history["attempts"] + policy["attempts"] == 1424
    assert policy["lanes"] == old["lanes"]
    assert policy["limit_micro_usd"] == old["limit_micro_usd"]
    assert (history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
            + policy["limit_micro_usd"] <= history["cumulative_limit_micro_usd"])
