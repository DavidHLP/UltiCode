from keyword_evaluation import compare_limits, evaluate_cases, load_cases


def test_dataset_keeps_development_and_holdout_separate() -> None:
    cases = load_cases()

    assert len(cases) == 30
    assert sum(case.split == "development" for case in cases) == 20
    assert sum(case.split == "holdout" for case in cases) == 10


def test_keyword_evaluation_checks_expected_hits_and_no_evidence() -> None:
    result = evaluate_cases(load_cases(), limit=3)

    assert result["development"]["total"] == 20
    assert result["holdout"]["total"] == 10
    assert result["development"]["passed"] == 12
    assert result["holdout"]["passed"] == 5
    assert result["development"]["unexpected_hits"] == 8
    assert result["holdout"]["unexpected_hits"] == 4
    assert result["development"]["no_evidence"] == 2
    assert result["holdout"]["no_evidence"] == 0
    assert result["holdout"]["false_positive_no_evidence"] == 1


def test_top_k_comparison_changes_only_retrieval_limit() -> None:
    cases = load_cases()
    result = compare_limits(cases, limits=(1, 3))

    assert result[1]["development"]["passed"] == 13
    assert result[3]["development"]["passed"] == 12
    assert result[1]["holdout"]["passed"] == 6
    assert result[3]["holdout"]["passed"] == 5
    assert result[1]["development"]["total"] == 20
    assert result[1]["holdout"]["total"] == 10
    assert result[3]["development"]["unexpected_hits"] > result[1]["development"]["unexpected_hits"]
    assert result[3]["holdout"]["unexpected_hits"] > result[1]["holdout"]["unexpected_hits"]
