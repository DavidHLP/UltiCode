"""Focused tests for the real-model answer-level evaluation entry point.

Everything here stubs the adapter and the corpus loader: these tests cover the
artifact contract (reserve before billing, no clobbering, atomic publish) and the
pre-call input snapshot, never a live call.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
from dataclasses import replace
from pathlib import Path

import pytest
from deepseek_model import _parse_decision

import answer_evaluation
from keyword_evaluation import KeywordCase
from retrieval import MAX_QUERY_CHARS, SourceDocument
from retrieval import load_sample_corpus as _real_load_sample_corpus

_module_spec = importlib.util.spec_from_file_location(
    "answer_eval_entry",
    Path(__file__).parents[1] / "e2e_answer_evaluation.py",
)
assert _module_spec and _module_spec.loader
e2e = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e)


@pytest.fixture(autouse=True)
def _isolated_artifact_lock_state(monkeypatch, tmp_path):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path / "state"))


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


def _install(
    monkeypatch,
    *,
    on_call=None,
    answers=None,
    judgements=None,
    real_corpus=False,
    usage_total=10,
    fail_judge_preflight=False,
    preflight_checks=None,
    cases=None,
    model_entries=None,
):
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
            if model_entries is not None:
                model_entries.append(True)
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        def check_prompt_budget(self, messages: list[dict[str, object]]) -> None:
            content = str(messages[-1]["content"])
            if preflight_checks is not None:
                preflight_checks.append(content)
            if fail_judge_preflight and "JUDGE_CONTRACT" in content:
                raise e2e.ModelBudgetExceeded(
                    "prompt exceeds the configured token budget"
                )

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
    monkeypatch.setattr(
        e2e,
        "load_cases",
        lambda **_kwargs: cases if cases is not None else (_case(),),
    )
    if not real_corpus:
        monkeypatch.setattr("retrieval.load_sample_corpus", _documents)
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL", "1")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "placeholder-not-a-real-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    for name in (
        "DEEPSEEK_MAX_CALLS",
        "DEEPSEEK_MAX_TOKENS",
        "DEEPSEEK_MAX_PROMPT_TOKENS",
        "DEEPSEEK_TIMEOUT",
    ):
        monkeypatch.delenv(name, raising=False)
    return calls


def test_judge_prompt_budget_is_preflighted_before_any_model_call(
    monkeypatch, capsys, tmp_path
) -> None:
    preflight_checks: list[str] = []
    calls = _install(
        monkeypatch,
        fail_judge_preflight=True,
        preflight_checks=preflight_checks,
    )
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(tmp_path / "artifact.json"))

    assert e2e.main_sync() == 1

    assert calls == []
    assert any("JUDGE_CONTRACT" in prompt for prompt in preflight_checks)
    assert any(r"\udbff\udfff" in prompt for prompt in preflight_checks)
    assert "reason=model_budget_exceeded" in capsys.readouterr().out


def test_nested_json_depth_error_is_sanitized_as_protocol(
    monkeypatch, capsys, tmp_path
) -> None:
    answer = "[" * 2000 + "0" + "]" * 2000
    # Keep this regression independent of Python's decoder nesting limit.
    real_json_loads = answer_evaluation.json.loads

    def raise_depth_error(raw: str, **kwargs: object) -> object:
        if raw == answer:
            raise RecursionError("maximum recursion depth exceeded")
        return real_json_loads(raw, **kwargs)

    monkeypatch.setattr(answer_evaluation.json, "loads", raise_depth_error)
    calls = _install(monkeypatch, answers={"dev-01": answer})
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 1

    output = capsys.readouterr().out
    assert "FAIL reason=protocol" in output
    assert "RecursionError" not in output
    assert len(calls) == 1
    assert not destination.exists()


def test_an_answer_over_the_text_limit_is_not_sent_to_the_judge(
    monkeypatch, capsys, tmp_path
) -> None:
    calls = _install(
        monkeypatch,
        answers={
            "dev-01": json.dumps(
                {"text": "x" * 1001, "citations": ["snap-doc:v1:1"]}
            )
        },
    )
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(tmp_path / "artifact.json"))

    assert e2e.main_sync() == 1

    assert len(calls) == 1
    assert "reason=protocol" in capsys.readouterr().out


def test_an_overlong_case_query_is_rejected_before_model_session(
    monkeypatch, capsys, tmp_path
) -> None:
    model_entries: list[bool] = []
    case = replace(_case(), query="q" * (MAX_QUERY_CHARS + 1))
    calls = _install(
        monkeypatch,
        cases=(case,),
        model_entries=model_entries,
    )
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(tmp_path / "artifact.json"))

    assert e2e.main_sync() == 1
    assert model_entries == []
    assert calls == []
    assert "reason=query_too_long" in capsys.readouterr().out


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


def test_success_line_identifies_the_default_generated_artifact(
    monkeypatch, capsys, tmp_path
) -> None:
    calls = _install(monkeypatch)
    monkeypatch.delenv("ULTICODE_ANSWER_EVAL_RESULT", raising=False)
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))

    assert e2e.main_sync() == 0

    artifacts = list((tmp_path / "ulticode").glob("answer-eval-*.json"))
    assert len(artifacts) == 1
    output = capsys.readouterr().out
    assert f"artifact={e2e._artifact_label(artifacts[0])}" in output
    assert len(calls) == 2


def test_a_lone_surrogate_answer_is_escaped_in_the_published_artifact(
    monkeypatch, capsys, tmp_path
) -> None:
    calls = _install(
        monkeypatch,
        answers={"dev-01": json.dumps({"text": chr(0xD800), "citations": ["snap-doc:v1:1"]})},
    )
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    assert e2e.main_sync() == 0

    payload = destination.read_bytes()
    assert bytes([0x5C]) + b"ud800" in payload
    artifact = json.loads(payload.decode("utf-8"))
    assert artifact["rows"][0]["answer_text"] == chr(0xD800)
    assert "OK answer_eval" in capsys.readouterr().out
    assert len(calls) == 2


@pytest.mark.parametrize("max_calls,exit_code,expected_calls", [(2, 1, 0), (5, 1, 0), (6, 0, 6)])
def test_retry_capacity_is_checked_before_billing(monkeypatch, capsys, tmp_path, max_calls, exit_code, expected_calls):
    def stall(call_number):
        if call_number % 3:
            raise TimeoutError("retryable timeout")

    calls = _install(monkeypatch, on_call=stall)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", str(max_calls))

    assert e2e.main_sync() == exit_code
    assert len(calls) == expected_calls
    assert destination.exists() == (exit_code == 0)
    if exit_code:
        assert "reason=call_budget_below_plan cases=1 required=6" in capsys.readouterr().out
    else:
        assert json.loads(destination.read_text(encoding="utf-8"))["rows"][0]["model_calls"] == 6


@pytest.mark.parametrize("remaining,expected_calls", [(1, 0), (2, 2)])
@pytest.mark.parametrize("policy_id", ["acceptance-revalidation-v2", "acceptance-revalidation-v3", "acceptance-revalidation-v4", "acceptance-revalidation-v5", "acceptance-revalidation-v6", "acceptance-revalidation-v7"])
def test_rollover_single_attempt_plan_checks_purpose_before_billing(monkeypatch, tmp_path, remaining, expected_calls, policy_id):
    from types import SimpleNamespace
    calls = _install(monkeypatch)
    budget = SimpleNamespace(_identity=SimpleNamespace(policy_id=policy_id),
                             remaining_purpose_attempts=lambda purpose: remaining)
    e2e.DeepseekModel.metering = []
    e2e.DeepseekModel._transport = SimpleNamespace(exchanges=[])
    monkeypatch.setattr(e2e, "authorized_model", lambda: ("deepseek-flash", budget))
    monkeypatch.setattr(e2e, "acceptance_transport", lambda *args: None)
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", "2")
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(tmp_path / "rollover.json"))
    assert e2e.main_sync() == (0 if expected_calls else 1)
    assert len(calls) == expected_calls


def test_bound_failure_retains_raw_record_and_prevents_clobber(monkeypatch, tmp_path):
    from types import SimpleNamespace
    from authorized_budget_period import REVALIDATION_V2_POLICY_ID
    calls = _install(monkeypatch, answers={"dev-01": "invalid protocol"})
    budget = SimpleNamespace(_identity=SimpleNamespace(policy_id=REVALIDATION_V2_POLICY_ID),
                             remaining_purpose_attempts=lambda purpose: 2)
    monkeypatch.setattr(e2e, "authorized_model", lambda: ("deepseek-flash", budget))
    monkeypatch.setattr(e2e, "acceptance_transport", lambda *args: None)
    original_model = e2e.DeepseekModel
    class RecordingModel(original_model):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, **kwargs)
            self.metering = [{"attempt_id": "synthetic-failed-attempt"}]
            self._transport = SimpleNamespace(exchanges=[{"synthetic": "failed response"}])
    monkeypatch.setattr(e2e, "DeepseekModel", RecordingModel)
    destination = tmp_path / "failed.json"
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", "2")
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    assert e2e.main_sync() == 1
    raw = destination.read_bytes()
    record = json.loads(raw)
    assert record["status"] == "INCOMPLETE"
    assert record["attempt_ids"] == ["synthetic-failed-attempt"]
    assert record["provider_exchanges"] == [{"synthetic": "failed response"}]
    assert "summary" not in record
    sent = len(calls)
    assert e2e.main_sync() == 1
    assert len(calls) == sent and destination.read_bytes() == raw


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


def test_an_existing_lock_does_not_skip_the_artifact_write_probe(
    monkeypatch, capsys, tmp_path
) -> None:
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    destination.with_name(f"{destination.name}.lock").touch()
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    original_open = e2e.os.open

    def deny_probe(name, *args, **kwargs):
        if isinstance(name, str) and name.endswith(".probe"):
            raise PermissionError("artifact directory is not writable")
        return original_open(name, *args, **kwargs)

    monkeypatch.setattr(e2e.os, "open", deny_probe)

    assert e2e.main_sync() == 1

    assert "reason=answer_artifact_unusable" in capsys.readouterr().out
    assert calls == []
    assert not list(tmp_path.glob("*.probe"))

def test_hard_link_publication_is_preflighted_before_model_calls(
    monkeypatch, capsys, tmp_path
) -> None:
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    import e2e_citation_support_model as shared

    real_link = shared.os.link

    def deny_probe_link(src, dst, *args, **kwargs):
        if isinstance(src, str) and src.endswith(".probe"):
            raise OSError("hard links unavailable")
        return real_link(src, dst, *args, **kwargs)

    monkeypatch.setattr(shared.os, "link", deny_probe_link)

    assert e2e.main_sync() == 1
    assert "reason=answer_artifact_unusable" in capsys.readouterr().out
    assert calls == []
    assert not list(tmp_path.glob("*.probe*"))


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


def test_a_controlled_artifact_name_cannot_forge_a_success_line(
    monkeypatch, capsys, tmp_path
) -> None:
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json\nOK answer_eval forged"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))

    def boom(*_args: object, **_kwargs: object) -> None:
        raise OSError("no space left on device")

    monkeypatch.setattr(e2e, "_publish", boom)

    assert e2e.main_sync() == 1

    output = capsys.readouterr().out
    assert "reason=answer_artifact_write_failed" in output
    assert not any(line.startswith("OK answer_eval") for line in output.splitlines())
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


@pytest.mark.parametrize("sidecar", [False, True])
def test_dangling_artifact_symlinks_fail_without_model_calls(monkeypatch, capsys, tmp_path, sidecar):
    calls = _install(monkeypatch)
    destination = tmp_path / "artifact.json"
    occupied = destination.with_suffix(".json.meta.json") if sidecar else destination
    missing = tmp_path / "missing"
    occupied.symlink_to(missing)
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    assert e2e.main_sync() == 1
    assert calls == []
    assert "answer_artifact_unusable" in capsys.readouterr().out
    assert occupied.is_symlink()
    assert occupied.readlink() == missing
    assert not missing.exists()


@pytest.mark.parametrize("during_publish", [False, True])
@pytest.mark.parametrize("replacement_is_link", [False, True])
def test_answer_artifact_parent_swap_never_redirects_bytes(monkeypatch, capsys, tmp_path, during_publish, replacement_is_link):
    parent = tmp_path / "reserved"
    parent.mkdir()
    moved = tmp_path / "original"
    replacement = tmp_path / "replacement"
    replacement.mkdir()
    destination = parent / "artifact.json"
    monkeypatch.setenv("ULTICODE_ANSWER_EVAL_RESULT", str(destination))
    def swap():
        parent.rename(moved)
        if replacement_is_link:
            parent.symlink_to(replacement, target_is_directory=True)
        else:
            parent.mkdir()
    def on_call(number):
        if number == 1 and not during_publish:
            swap()
    calls = _install(monkeypatch, on_call=on_call)
    if during_publish:
        import e2e_citation_support_model as shared
        real_link = shared.os.link
        def link(*args, **kwargs):
            swap()
            return real_link(*args, **kwargs)
        monkeypatch.setattr(shared.os, "link", link)
    assert e2e.main_sync() == 1
    assert len(calls) == 2
    assert not list((replacement if replacement_is_link else parent).iterdir())
    assert not list(moved.iterdir())
    output = capsys.readouterr().out
    assert "answer_artifact_write_failed" in output
    assert "OK answer_eval" not in output
