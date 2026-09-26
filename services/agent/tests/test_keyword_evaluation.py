import json
from pathlib import Path

from keyword_evaluation import (
    compare_limits,
    evaluate_case_records,
    evaluate_cases,
    load_cases,
    summarize_records,
)


def test_dataset_keeps_development_and_holdout_separate() -> None:
    cases = load_cases()

    assert len(cases) == 30
    assert sum(case.split == "development" for case in cases) == 20
    assert sum(case.split == "holdout" for case in cases) == 10


def test_every_case_declares_answerability_and_expected_behavior() -> None:
    cases = load_cases()
    behaviors = [case.expected_behavior for case in cases]

    assert behaviors.count("cite") == 25
    assert behaviors.count("no_evidence") == 3
    assert behaviors.count("refuse") == 2
    for case in cases:
        assert case.expected_behavior in {"cite", "no_evidence", "refuse"}
        if case.expected_behavior == "cite":
            assert case.answerable is True
            assert case.expected_doc_ids
        else:
            assert case.answerable is False


def test_records_cover_every_required_dimension() -> None:
    records = evaluate_case_records(load_cases(), limit=3)

    assert len(records) == 30
    for record in records:
        assert record.tool_calls == 1
        assert record.elapsed_ms >= 0
        assert record.observed_behavior in {
            "answered_with_citation",
            "no_evidence",
            "refuse_required",
        }
        assert record.task_completion in {
            "matched",
            "extra_hits",
            "missed",
            "false_positive",
        }
        assert isinstance(record.retrieval_hit, bool)


def test_every_returned_hit_is_traceable_to_a_source() -> None:
    records = evaluate_case_records(load_cases(), limit=3)

    assert all(record.citation_traceable for record in records)


def test_refuse_cases_never_report_an_answer_behaviour() -> None:
    records = evaluate_case_records(load_cases(), limit=3)
    refuse_records = [r for r in records if r.expected_behavior == "refuse"]

    assert len(refuse_records) == 2
    for record in refuse_records:
        assert record.observed_behavior == "refuse_required"
        # A retrieved fragment is exactly the temptation to fabricate, so the
        # risk flag must line up with the refuse annotation and nothing else.
        assert record.fabrication_risk is bool(record.unexpected_doc_ids)
    assert {r.split for r in refuse_records} == {"development", "holdout"}


def test_summary_totals_match_the_records() -> None:
    records = evaluate_case_records(load_cases(), limit=3)
    summary = summarize_records(records)

    for split in ("development", "holdout"):
        assert summary[split]["total"] == sum(
            1 for record in records if record.split == split
        )
        assert summary[split]["tool_calls"] == summary[split]["total"]
        assert summary[split]["refuse_required"] == sum(
            1
            for record in records
            if record.split == split and record.observed_behavior == "refuse_required"
        )


def test_loader_rejects_case_without_behavior_annotation(tmp_path: Path) -> None:
    incomplete = tmp_path / "cases.json"
    incomplete.write_text(
        json.dumps(
            [
                {
                    "case_id": "dev-01",
                    "split": "development",
                    "query": "Wrong Answer status",
                    "expected_doc_ids": ["sample-status-only"],
                }
            ]
        ),
        encoding="utf-8",
    )

    try:
        load_cases(incomplete)
    except ValueError:
        return
    raise AssertionError("a case without expected_behavior must be rejected")


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
