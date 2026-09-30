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



class _StubModel:
    """Answers the pass the prompt asks for, keyed by evaluation order."""

    def __init__(self, answers: dict[str, str], judgements: dict[str, str]) -> None:
        self._answers = answers
        self._judgements = judgements
        self._case_ids = iter(answers)
        self._current_case_id: str | None = None
        self.usage: list[dict[str, int]] = []
        self.prompts: list[str] = []
        # Mirrors DeepseekModel: incremented before the request, so a call that
        # later fails is still counted.
        self.calls_made = 0

    async def decide(self, messages: list[dict[str, object]]):
        self.calls_made += 1
        content = str(messages[-1]["content"])
        self.prompts.append(content)
        self.usage.append({"total_tokens": 12})
        if "ANSWER_CONTRACT" in content:
            self._current_case_id = next(self._case_ids)
            return _decision(self._answers[self._current_case_id])
        assert self._current_case_id is not None
        return _decision(self._judgements[self._current_case_id])


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
    answers = {"dev-01": '{"text": "状态为 Wrong Answer，说明输出与预期不一致，来源 sample-status-only。", "citations": ["sample-status-only:v1:1"]}'}
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
    # The recorded count is the attempts actually made, which for a clean run is
    # the adapter's own call count.
    assert model.calls_made == 2
    assert len(model.usage) == 2


def test_answer_generation_does_not_see_expected_outcomes() -> None:
    case = _case(expected="refuse")
    answers = {
        case.case_id: '{"text": "无法从可用信息中确定。", "citations": []}'
    }
    judgements = {
        case.case_id: '{"citation_support": false, "answer_completed": true, "observed_behavior": "refuse"}'
    }
    _rows, model = _run([case], answers, judgements)
    answer_prompt = model.prompts[0]

    assert "QUESTION wrong answer status" in answer_prompt
    assert "CASE_ID" not in answer_prompt
    assert "EXPECTED" not in answer_prompt
    assert "ALLOWED" not in answer_prompt
    assert "FORBIDDEN" not in answer_prompt


def test_only_answer_citations_are_recorded_and_judged(monkeypatch) -> None:
    from retrieval import SourceHit

    case = _case()
    hits = (
        SourceHit("sample-status-only", "v1", "sample-status-only:v1:1",
                  "status.md", "lines 1-2", "synthetic", "synthetic",
                  "untrusted-data", ("status",), "status evidence"),
        SourceHit("unused", "v1", "unused:v1:1", "unused.md", "lines 1-2",
                  "synthetic", "synthetic", "untrusted-data", ("status",),
                  "unreferenced retrieval hit"),
    )
    monkeypatch.setattr("answer_evaluation.keyword_search", lambda *_args, **_kwargs: hits)
    answers = {
        case.case_id: '{"text": "状态说明。", "citations": ["sample-status-only:v1:1"]}'
    }
    judgements = {
        case.case_id: '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'
    }
    rows, model = _run([case], answers, judgements)

    assert rows[0].citations == ("sample-status-only:v1:1",)
    assert "status evidence" in model.prompts[1]
    assert "unreferenced retrieval hit" not in model.prompts[1]


def test_retrieval_uses_the_supplied_corpus_snapshot(monkeypatch) -> None:
    """A run judges the snapshot it was handed, not a fresh corpus per case."""
    import asyncio

    from retrieval import SourceDocument

    snapshot = (
        SourceDocument(
            doc_id="snap-doc",
            version="v1",
            source_path="snap.md",
            access_scope="agent-authored-synthetic",
            sample_kind="synthetic",
            text="wrong answer status snapshot evidence",
            source_position="lines 1-1",
        ),
    )
    # Any per-case read of the pinned corpus is a bug here: the caller's snapshot
    # is the only material this run may judge.
    monkeypatch.setattr(
        "retrieval.load_sample_corpus",
        lambda: (_ for _ in ()).throw(AssertionError("corpus re-read per case")),
    )
    case = _case(query="wrong answer status")
    answers = {"dev-01": '{"text": "状态说明。", "citations": ["snap-doc:v1:1"]}'}
    judgements = {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'}
    model = _StubModel(answers, judgements)

    rows = asyncio.run(
        evaluate_answer_cases([case], model=model, documents=snapshot)
    )

    assert rows[0].citations == ("snap-doc:v1:1",)
    assert "snapshot evidence" in model.prompts[0]


def test_answer_cannot_cite_an_unretrieved_chunk() -> None:
    case = _case()
    answers = {
        case.case_id: '{"text": "依据资料。", "citations": ["forged:v1:1"]}'
    }
    with pytest.raises(AnswerEvaluationError, match="answer citations"):
        _run([case], answers, {})


def test_no_retrieval_marks_citation_support_not_applicable() -> None:
    # A single token absent from the corpus keeps this on the empty-retrieval path.
    case = _case(query="zzqqxx", expected="no_evidence")
    answers = {
        case.case_id: '{"text": "没有检索到可用资料，无法给出结论。", "citations": []}'
    }
    judgements = {
        case.case_id: '{"citation_support": false, "answer_completed": true, "observed_behavior": "no_evidence"}'
    }
    rows, _model = _run([case], answers, judgements)

    row = rows[0]
    assert row.citations == ()
    assert row.citation_support == "not_applicable"
    assert row.answer_completion == "completed"


def test_a_behavior_mismatch_is_recorded_not_hidden() -> None:
    answers = {
        "dev-01": '{"text": "看起来是第 42 行出错。", "citations": ["sample-status-only:v1:1"]}'
    }
    judgements = {
        "dev-01": '{"citation_support": false, "answer_completed": false, "observed_behavior": "cite"}'
    }
    rows, _model = _run([_case(expected="refuse")], answers, judgements)

    row = rows[0]
    assert row.expected_behavior == "refuse"
    assert row.observed_behavior == "cite"
    assert row.behavior_match is False
    assert row.citation_support == "unsupported"
    assert row.answer_completion == "incomplete"


def test_an_unknown_behavior_label_is_a_protocol_failure() -> None:
    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": []}'}
    with pytest.raises(AnswerEvaluationError):
        _run(
            [_case()],
            answers,
            {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "hallucinate"}'},
        )


def test_an_answer_that_declares_its_own_behaviour_is_refused() -> None:
    answers = {"dev-01": '{"text": "x", "behavior": "cite", "citations": []}'}
    with pytest.raises(AnswerEvaluationError):
        _run(
            [_case()],
            answers,
            {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'},
        )


def test_a_nested_answer_with_a_duplicate_key_is_a_protocol_failure() -> None:
    """`{"text": "a", "text": "b"}` must fail, not read as last-write-wins."""
    answers = {
        "dev-01": '{"text": "first answer text。", "text": "second answer text。", "citations": []}'
    }
    with pytest.raises(AnswerEvaluationError, match="repeated a key"):
        _run([_case()], answers, {})


def test_a_nested_judgement_with_a_duplicate_key_is_a_protocol_failure() -> None:
    """A repeated judgement flag must not silently keep the last value."""
    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": []}'}
    judgements = {
        "dev-01": '{"citation_support": true, "citation_support": false, "answer_completed": true, "observed_behavior": "cite"}'
    }
    with pytest.raises(AnswerEvaluationError, match="repeated a key"):
        _run([_case()], answers, judgements)


def test_a_judgement_that_is_not_boolean_is_a_protocol_failure() -> None:
    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": []}'}
    with pytest.raises(AnswerEvaluationError):
        _run([_case()], answers, {"dev-01": '{"citation_support": "yes", "answer_completed": true, "observed_behavior": "cite"}'})


def test_a_judgement_with_extra_fields_is_a_protocol_failure() -> None:
    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": []}'}
    with pytest.raises(AnswerEvaluationError):
        _run(
            [_case()],
            answers,
            {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite", "note": "x"}'},
        )


def test_summary_counts_every_answer_level_dimension() -> None:
    answers = {
        "dev-01": '{"text": "x 的回答文本。", "citations": ["sample-status-only:v1:1"]}'
    }
    judgements = {
        "dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'
    }
    rows, _model = _run([_case()], answers, judgements)

    summary = summarize(rows)
    assert summary["cases"] == 1
    assert summary["supported"] == 1
    assert summary["completed"] == 1
    assert summary["behavior_match"] == 1
    assert summary["model_calls"] == 2
    # A deferred value in a summary would silently reintroduce the placeholder.
    assert summary["deferred"] == 0


class ReadTimeout(Exception):
    """A transport stall the adapter retries — same name httpcore raises."""


class _FlakyModel(_StubModel):
    """Fails the first N calls with a transport-level error, then behaves."""

    def __init__(
        self,
        answers,
        judgements,
        failures: int,
        error: type[Exception] = ReadTimeout,
    ) -> None:
        super().__init__(answers, judgements)
        self.failures = failures
        self.error = error
        self.attempts = 0

    async def decide(self, messages):
        self.attempts += 1
        if self.attempts <= self.failures:
            # Counted before it fails, exactly as DeepseekModel increments
            # `calls_made` before the request is sent.
            self.calls_made += 1
            raise self.error("simulated stall")
        return await super().decide(messages)


def test_a_stalled_call_is_retried_without_restarting_the_batch() -> None:
    """A transport stall must not abort the batch or rebill completed cases."""
    import asyncio

    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": ["sample-status-only:v1:1"]}'}
    judgements = {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'}
    model = _FlakyModel(answers, judgements, failures=2)

    rows = asyncio.run(evaluate_answer_cases([_case()], model=model))

    assert len(rows) == 1
    assert rows[0].citation_support == "supported"
    # Two simulated stalls consumed attempts but only two billed calls succeeded.
    assert model.attempts == 4
    # The case records every attempt made, retried stalls included, not the two
    # logical passes.
    assert rows[0].model_calls == 4
    assert model.calls_made == 4


def test_a_persistent_failure_is_reported_not_swallowed() -> None:
    import asyncio

    import pytest as _pytest

    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": []}'}
    model = _FlakyModel(answers, {}, failures=99)
    with _pytest.raises(AnswerEvaluationError):
        asyncio.run(evaluate_answer_cases([_case()], model=model, attempts=2))


def test_a_timed_out_call_is_retried_like_a_transport_failure() -> None:
    """A timeout retries on the same budget, and the retry is billed."""
    import asyncio

    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": ["sample-status-only:v1:1"]}'}
    judgements = {"dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'}
    model = _FlakyModel(answers, judgements, failures=1, error=TimeoutError)

    rows = asyncio.run(evaluate_answer_cases([_case()], model=model))

    assert len(rows) == 1
    assert rows[0].citation_support == "supported"
    # answer pass: one stalled attempt then one success; judge pass: one success.
    assert model.attempts == 3
    assert rows[0].model_calls == 3
    assert model.calls_made == 3


def test_a_persistent_timeout_is_reported_not_swallowed() -> None:
    import asyncio

    import pytest as _pytest

    answers = {"dev-01": '{"text": "x 的回答文本。", "citations": []}'}
    model = _FlakyModel(answers, {}, failures=99, error=TimeoutError)
    with _pytest.raises(AnswerEvaluationError, match="timed out"):
        asyncio.run(evaluate_answer_cases([_case()], model=model, attempts=2))
