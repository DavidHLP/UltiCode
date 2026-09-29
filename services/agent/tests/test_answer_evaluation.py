"""Per-case answer-level evaluation, development split only (U02 acceptance #3).

The retrieval slice in `keyword_evaluation` records `citation_support` and
`answer_completion` as ``DEFERRED`` because it never produces an answer. This
module adds the answer pass and the judging pass that fill those columns, and
refuses to run on a sealed split so the holdouts cannot be consumed by an
evaluation that was only ever meant for the development set.
"""

from __future__ import annotations

import pytest

from answer_evaluation import (
    AnswerEvaluationError,
    development_cases,
    evaluate_answer_cases,
    summarize,
)
from keyword_evaluation import DEFERRED, KeywordCase, load_cases


def _decision(text: str):
    class _Decision:
        pass

    decision = _Decision()
    decision.text = text
    decision.tool_call = None
    return decision


def _case_id(content: str) -> str:
    for line in content.splitlines():
        if line.startswith("CASE_ID "):
            return line.split(" ", 1)[1].strip()
    raise AssertionError("prompt must carry a CASE_ID line")


class _StubModel:
    """Answers the pass the prompt asks for, keyed off the contract marker."""

    def __init__(self, answers: dict[str, str], judgements: dict[str, str]) -> None:
        self._answers = answers
        self._judgements = judgements
        self.usage: list[dict[str, int]] = []

    async def decide(self, messages: list[dict[str, object]]):
        content = str(messages[-1]["content"])
        self.usage.append({"total_tokens": 12})
        case_id = _case_id(content)
        if "ANSWER_CONTRACT" in content:
            return _decision(self._answers[case_id])
        return _decision(self._judgements[case_id])


def _case(
    case_id: str = "dev-01",
    split: str = "development",
    expected: str = "cite",
    query: str = "wrong answer status",
) -> KeywordCase:
    return KeywordCase(
        case_id=case_id,
        split=split,
        query=query,
        required_evidence=("sample-status-only",),
        answerable=True,
        expected_behavior=expected,
        allowed_behavior="cite the retrieved fragment",
        forbidden_behavior="claim a code line was located",
    )


def _run(cases, answers, judgements):
    import asyncio

    model = _StubModel(answers, judgements)
    return asyncio.run(evaluate_answer_cases(cases, model=model)), model


def test_a_sealed_split_is_refused_before_any_call() -> None:
    with pytest.raises(AnswerEvaluationError):
        _run(
            [_case(case_id="holdout-01", split="holdout")],
            {},
            {},
        )


def test_development_cases_drops_both_holdouts() -> None:
    cases = load_cases()
    dev = development_cases(cases)
    assert len(dev) == 20
    assert {case.split for case in dev} == {"development"}
    assert not ({case.case_id for case in dev} & {c.case_id for c in cases
                                                  if c.split != "development"})


def test_answer_level_columns_are_measured_not_deferred() -> None:
    answers = {"dev-01": '{"text": "状态为 Wrong Answer，说明输出与预期不一致，来源 sample-status-only。"}'}
    judgements = {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'}
    rows, model = _run([_case()], answers, judgements)

    row = rows[0]
    assert row.citation_support == "supported"
    assert row.answer_completion == "completed"
    assert row.observed_behavior == "cite"
    assert row.behavior_match is True
    # The whole point of the module: these stop being placeholders.
    assert DEFERRED not in (row.citation_support, row.answer_completion)
    assert row.observed_behavior != "not_measured"
    assert row.model_calls == 2
    assert len(model.usage) == 2


def test_no_retrieval_marks_citation_support_not_applicable() -> None:
    # A single token absent from the corpus: `_terms` splits on ASCII words, so a
    # natural-language sentence would still match some word and the fixture would
    # silently exercise the cited path instead of the empty one.
    case = _case(query="zzqqxx", expected="no_evidence")
    answers = {case.case_id: '{"text": "没有检索到可用资料，无法给出结论。"}'}
    judgements = {case.case_id: '{"citation_support": false, "answer_completed": true, "observed_behavior": "no_evidence"}'}
    rows, _model = _run([case], answers, judgements)

    row = rows[0]
    assert row.citations == ()
    # Nothing was cited, so a false support flag must not read as a failed citation.
    assert row.citation_support == "not_applicable"
    assert row.answer_completion == "completed"


def test_a_behavior_mismatch_is_recorded_not_hidden() -> None:
    answers = {"dev-01": '{"text": "看起来是第 42 行出错。"}'}
    judgements = {"dev-01": '{"citation_support": false, "answer_completed": false, "observed_behavior": "cite"}'}
    rows, _model = _run([_case(expected="refuse")], answers, judgements)

    row = rows[0]
    assert row.expected_behavior == "refuse"
    assert row.observed_behavior == "cite"
    assert row.behavior_match is False
    assert row.citation_support == "unsupported"
    assert row.answer_completion == "incomplete"


def test_an_unknown_behavior_label_is_a_protocol_failure() -> None:
    # The answer no longer declares its own behaviour, so an unknown label can only
    # arrive through the judge — which is the pass that owns the classification.
    answers = {"dev-01": '{"text": "x 的回答文本。"}'}
    with pytest.raises(AnswerEvaluationError):
        _run(
            [_case()],
            answers,
            {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "hallucinate"}'},
        )


def test_an_answer_that_declares_its_own_behaviour_is_refused() -> None:
    """A self-declared label would make `observed_behavior` a self-report."""
    answers = {"dev-01": '{"text": "x", "behavior": "cite"}'}
    with pytest.raises(AnswerEvaluationError):
        _run(
            [_case()],
            answers,
            {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'},
        )


def test_a_judgement_that_is_not_boolean_is_a_protocol_failure() -> None:
    answers = {"dev-01": '{"text": "x 的回答文本。"}'}
    with pytest.raises(AnswerEvaluationError):
        _run([_case()], answers, {"dev-01": '{"citation_support": "yes", "answer_completed": true, "observed_behavior": "cite"}'})


def test_a_judgement_with_extra_fields_is_a_protocol_failure() -> None:
    answers = {"dev-01": '{"text": "x 的回答文本。"}'}
    with pytest.raises(AnswerEvaluationError):
        _run(
            [_case()],
            answers,
            {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite", "note": "x"}'},
        )


def test_summary_counts_every_answer_level_dimension() -> None:
    answers = {"dev-01": '{"text": "x 的回答文本。"}'}
    judgements = {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'}
    rows, _model = _run([_case()], answers, judgements)

    summary = summarize(rows)
    assert summary["cases"] == 1
    assert summary["supported"] == 1
    assert summary["completed"] == 1
    assert summary["behavior_match"] == 1
    assert summary["model_calls"] == 2
    # A deferred value in a summary would silently reintroduce the placeholder.
    assert summary["deferred"] == 0


class _FlakyModel(_StubModel):
    """Fails the first N calls with a transport error, then behaves."""

    def __init__(self, answers, judgements, failures: int) -> None:
        super().__init__(answers, judgements)
        self.failures = failures
        self.attempts = 0

    async def decide(self, messages):
        self.attempts += 1
        if self.attempts <= self.failures:
            class ReadTimeout(Exception):
                pass

            raise ReadTimeout("simulated stall")
        return await super().decide(messages)


def test_a_stalled_call_is_retried_without_restarting_the_batch() -> None:
    """A transport stall must not abort the batch or rebill completed cases."""
    import asyncio

    answers = {"dev-01": '{"text": "x 的回答文本。"}'}
    judgements = {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'}
    model = _FlakyModel(answers, judgements, failures=2)

    rows = asyncio.run(evaluate_answer_cases([_case()], model=model))

    assert len(rows) == 1
    assert rows[0].citation_support == "supported"
    # Two simulated stalls consumed attempts but only two billed calls succeeded.
    assert model.attempts == 4


def test_a_persistent_failure_is_reported_not_swallowed() -> None:
    import asyncio

    import pytest as _pytest

    answers = {"dev-01": '{"text": "x 的回答文本。"}'}
    model = _FlakyModel(answers, {}, failures=99)

    with _pytest.raises(AnswerEvaluationError):
        asyncio.run(evaluate_answer_cases([_case()], model=model, attempts=2))
