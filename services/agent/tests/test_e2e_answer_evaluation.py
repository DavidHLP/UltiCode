"""Focused tests for the real-model answer-level evaluation entry point.

Everything here stubs the adapter and the corpus loader: these tests cover the
artifact contract (reserve before billing, no clobbering, atomic publish) and the
pre-call input snapshot, never a live call.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path

import pytest

from keyword_evaluation import KeywordCase
from retrieval import SourceDocument
from retrieval import load_sample_corpus as _real_load_sample_corpus

_module_spec = importlib.util.spec_from_file_location(
    "answer_eval_entry",
    Path(__file__).parents[1] / "e2e_answer_evaluation.py",
)
assert _module_spec and _module_spec.loader
e2e = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e)


_ANSWERS = {"dev-01": '{"text": "状态说明。", "citations": ["snap-doc:v1:1"]}'}
_JUDGEMENTS = {
    "dev-01": '{"citation_support": true, "answer_completed": true, "observed_behavior": "cite"}'
}


def _documents(*_args: object, **_kwargs: object) -> tuple[SourceDocument, ...]:
    return (
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


def _case() -> KeywordCase:
    return KeywordCase(
        case_id="dev-01",
        split="development",
        query="wrong answer status",
        required_evidence=("snap-doc",),
        answerable=True,
        expected_behavior="cite",
        allowed_behavior="cite the retrieved fragment",
        forbidden_behavior="claim a code line",
    )


def _install(monkeypatch, *, on_call=None, answers=None, judgements=None, real_corpus=False):
    """Stub the adapter, corpus and case file so only the entry point runs."""
    calls: list[str] = []
    answers = answers if answers is not None else _ANSWERS
    judgements = judgements if judgements is not None else _JUDGEMENTS

    class _Decision:
        def __init__(self, text: str) -> None:
            self.text = text
            self.tool_call = None

    class _Model:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            self.usage: list[dict[str, object]] = []
            self.kwargs = _kwargs

        async def __aenter__(self) -> "_Model":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def decide(self, messages: list[dict[str, object]]) -> object:
            content = str(messages[-1]["content"])
            calls.append(content)
            self.usage.append({"total_tokens": 10})
            if on_call is not None:
                on_call(len(calls))
            if "ANSWER_CONTRACT" in content:
                return _Decision(answers["dev-01"])
            return _Decision(judgements["dev-01"])

    monkeypatch.setattr(e2e, "DeepseekModel", _Model)
    monkeypatch.setattr(e2e, "load_cases", lambda **_kwargs: (_case(),))
    if not real_corpus:
        monkeypatch.setattr("retrieval.load_sample_corpus", _documents)
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "placeholder-not-a-real-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "test-model")
    for name in (
        "DEEPSEEK_MAX_CALLS",
        "DEEPSEEK_MAX_TOKENS",
        "DEEPSEEK_MAX_PROMPT_TOKENS",
        "DEEPSEEK_TIMEOUT",
    ):
        monkeypatch.delenv(name, raising=False)
    return calls


def test_opt_in_is_required_and_no_key_is_used_without_it(monkeypatch, capsys) -> None:
    monkeypatch.delenv("ULTICODE_ANSWER_EVAL", raising=False)

    assert e2e.main_sync() == 0
    assert "SKIP reason=opt_in_not_set" in capsys.readouterr().out


def test_a_completed_run_publishes_the_artifact(monkeypatch, capsys, tmp_path) -> None:
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 0

    artifact = json.loads(destination.read_text(encoding="utf-8"))
    assert artifact["rows"][0]["citations"] == ["snap-doc:v1:1"]
    assert artifact["rows"][0]["model_calls"] == 2
    assert "OK answer_eval" in capsys.readouterr().out
    assert len(calls) == 2


def test_an_existing_artifact_is_not_overwritten_before_any_call(
    monkeypatch, capsys, tmp_path
) -> None:
    """An earlier run's artifact is evidence; clobbering it is silent."""
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    destination.write_text("earlier evidence", encoding="utf-8")
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 1

    assert "reason=answer_artifact_unusable" in capsys.readouterr().out
    assert calls == []
    assert destination.read_text(encoding="utf-8") == "earlier evidence"


def test_an_unwritable_artifact_destination_fails_before_any_call(
    monkeypatch, capsys, tmp_path
) -> None:
    """A destination under a non-directory is refused before the model is billed."""
    calls = _install(monkeypatch)
    blocker = tmp_path / "blocker"
    blocker.write_text("not a directory", encoding="utf-8")
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(blocker / "artifact.json"))

    assert e2e.main_sync() == 1

    assert "reason=answer_artifact_unusable" in capsys.readouterr().out
    assert calls == []


def test_a_failed_publication_reports_and_leaves_nothing(
    monkeypatch, capsys, tmp_path
) -> None:
    """A write failure is a failed run, not a half-written artifact."""
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    def boom(*_args: object, **_kwargs: object) -> None:
        raise OSError("no space left on device")

    monkeypatch.setattr(e2e, "_publish", boom)

    assert e2e.main_sync() == 1

    assert "reason=answer_artifact_write_failed" in capsys.readouterr().out
    assert not destination.exists()
    assert len(calls) == 2


def test_the_artifact_binds_the_preflight_inputs_after_a_mid_run_mutation(
    monkeypatch, capsys, tmp_path
) -> None:
    """Inputs are snapshotted before the first call; a later write cannot move them."""
    from corpus_manifest import MANIFEST_PATH as real_manifest

    manifest = tmp_path / "manifest.json"
    # Valid declarations: the entry point now parses the same bytes it hashes, so a
    # placeholder would be refused before the snapshot is used at all.
    manifest.write_bytes(real_manifest.read_bytes())
    case_file = tmp_path / "cases.json"
    case_file.write_text('[{"case_id": "dev-01"}]', encoding="utf-8")
    manifest_bytes = manifest.read_bytes()
    case_bytes = case_file.read_bytes()
    monkeypatch.setattr("corpus_manifest.MANIFEST_PATH", manifest)
    monkeypatch.setattr("keyword_evaluation._CASES_PATH", case_file)

    def mutate(call_number: int) -> None:
        if call_number == 1:  # after the snapshot, during the run
            manifest.write_text("MUTATED", encoding="utf-8")
            case_file.write_text("MUTATED", encoding="utf-8")

    calls = _install(monkeypatch, on_call=mutate)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 0

    artifact = json.loads(destination.read_text(encoding="utf-8"))
    assert artifact["corpus"]["manifest_sha256"] == hashlib.sha256(manifest_bytes).hexdigest()
    assert artifact["cases"]["sha256"] == hashlib.sha256(case_bytes).hexdigest()
    assert len(calls) == 2


def test_retrieval_reads_the_corpus_once_for_the_whole_run(
    monkeypatch, capsys, tmp_path
) -> None:
    """The snapshot is taken once, not re-read per case."""
    reads: list[int] = []

    def loader(*_args: object, **_kwargs: object) -> tuple[SourceDocument, ...]:
        reads.append(1)
        return _documents()

    calls = _install(monkeypatch)
    monkeypatch.setattr("retrieval.load_sample_corpus", loader)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 0
    assert reads == [1]
    assert len(calls) == 2


def test_the_corpus_and_its_digest_come_from_one_manifest_read(
    monkeypatch, capsys, tmp_path
) -> None:
    """Validation and the recorded digest bind the same bytes, read once."""
    from corpus_manifest import MANIFEST_PATH as real_manifest

    copy = tmp_path / "manifest.json"
    copy.write_bytes(real_manifest.read_bytes())
    monkeypatch.setattr("corpus_manifest.MANIFEST_PATH", copy)

    def no_reread(*_args: object, **_kwargs: object):
        raise AssertionError("the manifest was read again after the snapshot")

    monkeypatch.setattr("corpus_manifest.load_manifest", no_reread)
    answers = {"dev-01": '{"text": "状态说明。", "citations": ["sample-status-only:v1:1"]}'}
    calls = _install(monkeypatch, answers=answers)
    monkeypatch.setattr("retrieval.load_sample_corpus", _real_load_sample_corpus)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 0

    artifact = json.loads(destination.read_text(encoding="utf-8"))
    assert artifact["corpus"]["manifest_sha256"] == hashlib.sha256(copy.read_bytes()).hexdigest()
    assert len(calls) == 2


def test_a_runtime_failure_releases_the_claim(monkeypatch, capsys, tmp_path) -> None:
    """An arbitrary exception must not leave the destination claimed until exit."""
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    def boom(_call_number: int) -> None:
        raise RuntimeError("provider exploded")

    _install(monkeypatch, on_call=boom)
    assert e2e.main_sync() == 1
    assert "error=RuntimeError" in capsys.readouterr().out

    # The reservation is gone, so a fresh run can take the same destination.
    _install(monkeypatch)
    assert e2e.main_sync() == 0
    assert destination.exists()
