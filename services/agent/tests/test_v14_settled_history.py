from authorized_budget_period import policy_for


def test_integrated_acceptance_retains_settled_costs_and_old_unknowns():
    previous = policy_for("acceptance-revalidation-v13")
    policy = policy_for("acceptance-revalidation-v14")
    history, old = policy["history"], previous["history"]
    assert history["attempts"] == old["attempts"] + 98 == 1086
    assert history["known_actual_micro_usd"] == old["known_actual_micro_usd"] + 33738
    assert history["known_committed_micro_usd"] == old["known_committed_micro_usd"] + 877200
    assert history["unknown_attempts"] == old["unknown_attempts"] == 3
    assert history["unknown_encumbrance_micro_usd"] == old["unknown_encumbrance_micro_usd"] == 2359296
    assert history["cumulative_limit_micro_usd"] == old["cumulative_limit_micro_usd"] == 200000000
    assert history["cumulative_attempt_limit"] == history["attempts"] + policy["attempts"]
    assert policy["lanes"] == previous["lanes"]
    assert policy["limit_micro_usd"] == previous["limit_micro_usd"]
    assert (history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"]
            + policy["limit_micro_usd"] <= history["cumulative_limit_micro_usd"])
