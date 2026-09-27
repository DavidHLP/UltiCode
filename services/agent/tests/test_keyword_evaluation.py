import json
from pathlib import Path

import pytest

from keyword_evaluation import (
    DEFERRED,
    SPLITS,
    compare_limits,
    evaluate_case_records,
    evaluate_cases,
    load_cases,
    summarize_records,
)
from retrieval import keyword_search


def test_dataset_keeps_development_and_holdout_separate() -> None:
    cases = load_cases()

    assert len(cases) == 30
    assert sum(case.split == "development" for case in cases) == 20
    assert sum(case.split == "holdout" for case in cases) == 10
    # The one-shot confirmation set must stay out of the routine dataset, or
    # every ordinary test run would consume it.
    assert all(case.split != "holdout2" for case in cases)


def test_every_case_declares_evidence_and_behaviour_annotations() -> None:
    cases = load_cases()

    assert [case.expected_behavior for case in cases].count("cite") == 25
    assert [case.expected_behavior for case in cases].count("no_evidence") == 3
    assert [case.expected_behavior for case in cases].count("refuse") == 2
    for case in cases:
        # A `no_evidence` case must require nothing; an empty list is the point.
        assert bool(case.required_evidence) == (case.expected_behavior != "no_evidence")
        assert case.allowed_behavior.strip(), case.case_id
        assert case.forbidden_behavior.strip(), case.case_id
        assert case.answerable is (case.expected_behavior == "cite")


def test_refuse_cases_forbid_their_own_specific_claim() -> None:
    refuse_rules = {
        case.case_id: case.forbidden_behavior
        for case in load_cases()
        if case.expected_behavior == "refuse"
    }

    assert set(refuse_rules) == {"dev-10", "holdout-08"}
    # A shared rule would collapse "locate the code line" and "name the runtime
    # cause" into one indistinguishable expectation.
    assert len(set(refuse_rules.values())) == 2
    assert "code line" in refuse_rules["dev-10"]
    assert "runtime cause" in refuse_rules["holdout-08"]


def test_records_cover_every_required_dimension() -> None:
    records = evaluate_case_records(load_cases(), limit=3)

    assert len(records) == 30
    for record in records:
        assert record.tool_calls == 1
        assert record.elapsed_us >= 0
        assert isinstance(record.retrieval_hit, bool)
        # Retrieval-only evaluator: answer behaviour is never observed.
        assert record.observed_behavior == "not_measured"
        assert record.retrieval_outcome in {
            "matched",
            "extra_hits",
            "missed",
            "false_positive",
            # A `refuse` case is classified apart: the citable vocabulary would
            # report the forbidden retrieval as a match.
            "refused_with_evidence",
            "refused_without_evidence",
        }
        assert record.allowed_behavior.strip()
        assert record.forbidden_behavior.strip()


def test_answer_level_dimensions_are_deferred_not_passed() -> None:
    for record in evaluate_case_records(load_cases(), limit=3):
        # Retrieval can prove a hit is traceable; it cannot prove the fragment
        # supports a conclusion. Claiming otherwise would fake DAV-58's gate.
        assert record.citation_support == DEFERRED
        assert record.answer_completion == DEFERRED


def test_only_cases_with_a_hit_can_be_traceable() -> None:
    cases = {case.case_id: case for case in load_cases()}
    traced = 0
    for record in evaluate_case_records(load_cases(), limit=3):
        hits = keyword_search(cases[record.case_id].query, limit=3)
        assert record.citation_traceable is bool(hits)
        traced += record.citation_traceable

    assert 0 < traced < len(cases)


def test_refuse_cases_never_report_an_answer_behaviour() -> None:
    cases = {case.case_id: case for case in load_cases()}
    records = {
        record.case_id: record for record in evaluate_case_records(load_cases(), limit=3)
    }
    refuse_ids = [
        case_id for case_id, case in cases.items() if case.expected_behavior == "refuse"
    ]

    assert len(refuse_ids) == 2
    assert {cases[case_id].split for case_id in refuse_ids} == {"development", "holdout"}
    for case_id in refuse_ids:
        record = records[case_id]
        hits = keyword_search(cases[case_id].query, limit=3)
        required = set(cases[case_id].required_evidence)
        assert record.observed_behavior == "not_measured"
        # Any retrieved fragment is the temptation to fabricate, even one that
        # happens to be a required document.
        assert record.fabrication_risk is bool(hits)
        # Not the citable formula: for a refusal, retrieving the required
        # document is precisely the failure this case exists to catch.
        assert record.retrieval_hit is (not hits)
        assert record.retrieval_outcome == (
            "refused_with_evidence" if hits else "refused_without_evidence"
        )
    flagged = {case_id for case_id, record in records.items() if record.fabrication_risk}
    assert flagged <= set(refuse_ids)


def test_summary_totals_match_the_records() -> None:
    records = evaluate_case_records(load_cases(), limit=3)
    summary = summarize_records(records)

    for split in SPLITS:
        assert summary[split]["total"] == sum(
            1 for record in records if record.split == split
        )
        assert summary[split]["tool_calls"] == summary[split]["total"]
        assert summary[split]["answer_level_deferred"] == summary[split]["total"]
        assert summary[split]["behavior_not_measured"] == sum(
            1
            for record in records
            if record.split == split and record.observed_behavior == "not_measured"
        )


def test_loader_rejects_incomplete_annotations(tmp_path: Path) -> None:
    complete = {
        "case_id": "dev-01",
        "split": "development",
        "query": "Wrong Answer status",
        "required_evidence": ["sample-status-only"],
        "answerable": True,
        "expected_behavior": "cite",
        "allowed_behavior": "answer from the retrieved fragment and cite it",
        "forbidden_behavior": "claim the code was executed",
    }
    for dropped in (
        "required_evidence",
        "answerable",
        "expected_behavior",
        "allowed_behavior",
        "forbidden_behavior",
    ):
        incomplete = {key: value for key, value in complete.items() if key != dropped}
        path = tmp_path / f"cases-{dropped}.json"
        path.write_text(json.dumps([incomplete]), encoding="utf-8")
        try:
            load_cases(path)
        except ValueError:
            continue
        raise AssertionError(f"a case without {dropped} must be rejected")


def test_keyword_evaluation_checks_expected_hits_and_no_evidence() -> None:
    result = evaluate_cases(load_cases(), limit=3)

    assert result["development"]["total"] == 20
    assert result["holdout"]["total"] == 10
    assert result["development"]["passed"] == 12
    # The holdout split contains a refusal case; retrieving its document is a
    # fabrication risk and is tallied separately, never as a pass.
    assert result["holdout"]["refused_with_evidence"] == 1
    assert result["holdout"]["passed"] == 4
    assert result["development"]["unexpected_hits"] == 8
    assert result["holdout"]["unexpected_hits"] == 4
    assert result["development"]["no_evidence"] == 2
    assert result["holdout"]["no_evidence"] == 0
    assert result["holdout"]["false_positive_no_evidence"] == 1


def test_top_k_comparison_changes_only_retrieval_limit() -> None:
    cases = load_cases()
    result = compare_limits(cases, limits=(1, 3))

    # Each split carries one refusal case, which is tallied apart from `passed`:
    # retrieving a refusal case's document is a fabrication risk, never a pass.
    for limit in (1, 3):
        for split in ("development", "holdout"):
            assert result[limit][split]["refused_with_evidence"] == 1
    assert result[1]["development"]["passed"] == 12
    assert result[3]["development"]["passed"] == 12
    assert result[1]["holdout"]["passed"] == 5
    assert result[3]["holdout"]["passed"] == 4
    assert result[1]["development"]["total"] == 20
    assert result[1]["holdout"]["total"] == 10
    assert result[3]["development"]["unexpected_hits"] > result[1]["development"]["unexpected_hits"]
    assert result[3]["holdout"]["unexpected_hits"] > result[1]["holdout"]["unexpected_hits"]


def test_confirmation_set_is_versioned_separately_and_not_loaded_by_default() -> None:
    from keyword_evaluation import CONFIRMATION_CASES_PATH

    confirmation = load_cases(CONFIRMATION_CASES_PATH)

    assert len(confirmation) == 10
    assert all(case.split == "holdout2" for case in confirmation)
    assert all(case.required_evidence or case.expected_behavior != "cite" for case in confirmation)
    assert {case.case_id for case in load_cases()}.isdisjoint(
        case.case_id for case in confirmation
    )


def test_no_evidence_case_with_a_hit_is_not_a_retrieval_hit() -> None:
    """An empty required set is contained in everything, so containment lies."""
    records = {record.case_id: record for record in evaluate_case_records(load_cases(), limit=3)}
    false_positive = [
        record
        for record in records.values()
        if record.expected_behavior == "no_evidence" and record.unexpected_doc_ids
    ]

    assert false_positive, "expected at least one no-evidence case to be a false positive"
    for record in false_positive:
        assert record.retrieval_hit is False
        assert record.retrieval_outcome == "false_positive"


def test_elapsed_microseconds_are_not_truncated_to_zero() -> None:
    records = evaluate_case_records(load_cases(), limit=3)

    assert all(record.elapsed_us > 0 for record in records)


def test_loader_rejects_contradictory_annotations(tmp_path: Path) -> None:
    base = json.loads((Path(__file__).parents[1] / "data/keyword_cases.json").read_text(encoding="utf-8"))

    contradicted = json.loads(json.dumps(base[0]))
    contradicted["answerable"] = False
    path = tmp_path / "contradicted.json"
    path.write_text(json.dumps([contradicted]), encoding="utf-8")
    with pytest.raises(ValueError, match="answerable must agree"):
        load_cases(path)

    citable_without_evidence = json.loads(json.dumps(base[0]))
    citable_without_evidence["expected_behavior"] = "cite"
    citable_without_evidence["required_evidence"] = []
    path = tmp_path / "no-evidence-cite.json"
    path.write_text(json.dumps([citable_without_evidence]), encoding="utf-8")
    with pytest.raises(ValueError, match="must name its required evidence"):
        load_cases(path)


def test_duplicate_case_identifiers_are_rejected(tmp_path: Path) -> None:
    base = json.loads(
        (Path(__file__).parents[1] / "data/keyword_cases.json").read_text(encoding="utf-8")
    )
    path = tmp_path / "duplicated.json"
    path.write_text(json.dumps([base[0], dict(base[0])]), encoding="utf-8")

    with pytest.raises(ValueError, match="duplicate case id"):
        load_cases(path)


def test_no_evidence_case_cannot_declare_required_evidence(tmp_path: Path) -> None:
    """Self-contradictory annotations would be scored as a retrieval success."""
    base = json.loads(
        (Path(__file__).parents[1] / "data/keyword_cases.json").read_text(encoding="utf-8")
    )
    negative = next(case for case in base if case["expected_behavior"] == "no_evidence")
    contradictory = dict(negative)
    contradictory["required_evidence"] = ["sample-status-only"]

    path = tmp_path / "contradictory.json"
    path.write_text(json.dumps([contradictory]), encoding="utf-8")

    with pytest.raises(ValueError, match="no-evidence case cannot require evidence"):
        load_cases(path)


def test_refuse_case_must_declare_the_evidence_it_resists(tmp_path: Path) -> None:
    base = json.loads(
        (Path(__file__).parents[1] / "data/keyword_cases.json").read_text(encoding="utf-8")
    )
    refuse = dict(next(case for case in base if case["expected_behavior"] == "refuse"))
    refuse["required_evidence"] = []

    path = tmp_path / "refuse-without-evidence.json"
    path.write_text(json.dumps([refuse]), encoding="utf-8")

    with pytest.raises(ValueError, match="refuse case must name the evidence"):
        load_cases(path)


def test_required_evidence_must_name_a_real_corpus_document(tmp_path: Path) -> None:
    """An unretrievable id would quietly move limit or arm selection."""
    base = json.loads(
        (Path(__file__).parents[1] / "data/keyword_cases.json").read_text(encoding="utf-8")
    )
    typo = dict(base[0])
    typo["required_evidence"] = ["sample-status_only"]  # misspelt

    path = tmp_path / "typo.json"
    path.write_text(json.dumps([typo]), encoding="utf-8")

    with pytest.raises(ValueError, match="must name documents in the corpus"):
        load_cases(path)
