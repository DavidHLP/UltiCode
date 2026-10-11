from authorized_budget_period import policy_for


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
