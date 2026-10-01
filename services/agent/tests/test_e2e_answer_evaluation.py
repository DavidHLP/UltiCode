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
from deepseek_model import _parse_decision

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


def _install(monkeypatch, *, on_call=None, answers=None, judgements=None, real_corpus=False, usage_total=10):
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
            self.usage.append({"total_tokens": usage_total})
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


@pytest.mark.parametrize("raw_timeout", ["nan", "-nan", "inf", "-inf"])
def test_a_non_finite_timeout_fails_before_any_model_call(
    monkeypatch, capsys, tmp_path, raw_timeout
) -> None:
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("DEEPSEEK_TIMEOUT", raw_timeout)
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 1

    output = capsys.readouterr().out
    assert "DEEPSEEK_TIMEOUT" in output
    assert calls == []
    assert not destination.exists()
    assert not any(line.startswith("OK ") for line in output.splitlines())


def test_a_provider_finish_reason_cannot_forge_a_success_line(
    monkeypatch, capsys, tmp_path
) -> None:
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    forged = "stop\nOK answer_eval forged"

    class _MalformedModel:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def decide(self, _messages: list[dict[str, object]]) -> object:
            return _parse_decision("not JSON", finish_reason=forged)

    monkeypatch.setattr(e2e, "DeepseekModel", _MalformedModel)

    assert e2e.main_sync() == 1

    output = capsys.readouterr().out
    assert not any(line.startswith("OK ") for line in output.splitlines())
    assert "forged" not in output
    assert not destination.exists()
    assert calls == []


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


def test_foreign_artifact_created_during_model_call_survives(monkeypatch, capsys, tmp_path):
    destination = tmp_path / "artifact.json"
    foreign = b'foreign evidence\x00\xff'
    def on_call(number):
        if number == 1:
            destination.write_bytes(foreign)
    calls = _install(monkeypatch, on_call=on_call)
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    assert e2e.main_sync() == 1
    assert len(calls) == 2
    assert destination.read_bytes() == foreign
    assert "answer_artifact_write_failed" in capsys.readouterr().out
    assert not list(tmp_path.glob("*.part"))


@pytest.mark.parametrize("failure", ["malformed", "transport", "runtime"])
def test_aborted_evaluation_reports_all_sent_usage(monkeypatch, capsys, tmp_path, failure):
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    def on_call(number):
        if number >= 2:
            if failure == "transport":
                import httpx
                raise httpx.ReadTimeout("provider response secret")
            if failure == "runtime":
                raise RuntimeError("provider response secret")
    judgements = {"dev-01": "malformed"} if failure == "malformed" else None
    calls = _install(monkeypatch, on_call=on_call, judgements=judgements)
    assert e2e.main_sync() == 1
    output = capsys.readouterr().out
    expected_calls = 4 if failure == "transport" else 2
    assert len(calls) == expected_calls
    assert output.count("ANSWER EVAL USAGE") == 1
    assert f"calls={expected_calls} total_tokens={expected_calls * 10}" in output
    assert "provider response secret" not in output
    assert not destination.exists()
    # Failure also releases the reservation immediately.
    _install(monkeypatch)
    assert e2e.main_sync() == 0


@pytest.mark.parametrize("usage_total", [10, None])
def test_later_answer_abort_keeps_previous_case_usage(monkeypatch, capsys, tmp_path, usage_total):
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    def on_call(number):
        if number == 3:
            raise RuntimeError("private provider payload")
    calls = _install(monkeypatch, on_call=on_call, usage_total=usage_total)
    monkeypatch.setattr(e2e, "load_cases", lambda **kwargs: (_case(), _case()))
    assert e2e.main_sync() == 1
    output = capsys.readouterr().out
    assert len(calls) == 3
    tokens = "unknown" if usage_total is None else "30"
    assert f"ANSWER EVAL USAGE | cases=2 calls=3 total_tokens={tokens}" in output
    assert output.count("ANSWER EVAL USAGE") == 1
    assert "private provider payload" not in output
    assert "OK answer_eval" not in output
    assert not destination.exists()


def test_success_reports_usage_once(monkeypatch, capsys, tmp_path):
    _install(monkeypatch)
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(tmp_path / "artifact.json"))
    assert e2e.main_sync() == 0
    output = capsys.readouterr().out
    assert output.count("ANSWER EVAL USAGE") == 1
    assert "calls=2 total_tokens=20" in output
