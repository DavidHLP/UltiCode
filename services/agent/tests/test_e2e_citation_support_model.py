"""Focused tests for the model-judged citation-support entry point."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import pytest

from retrieval import keyword_search, load_sample_corpus

_module_spec = importlib.util.spec_from_file_location(
    "citation_support_smoke",
    Path(__file__).parents[1] / "e2e_citation_support_model.py",
)
assert _module_spec and _module_spec.loader
smoke = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(smoke)


def _citation(index: int = 0) -> dict[str, object]:
    hits = keyword_search("submission status", limit=1)
    assert hits, "the sample corpus must return a hit for this query"
    return hits[index].as_model_dict()


def _install(monkeypatch, tmp_path: Path, judgements: list[str], calls: list[str]) -> None:
    class _Decision:
        def __init__(self, text: str) -> None:
            self.text = text
            self.tool_call = None

    class _Model:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            pass

        async def __aenter__(self) -> "_Model":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def decide(self, messages: list[dict[str, object]]) -> object:
            calls.append(str(messages[-1]["content"]))
            return _Decision(judgements[len(calls) - 1])

    class _Client:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            pass

        async def __aenter__(self) -> "_Client":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def login(self, *_args: object) -> None:
            return None

    async def _first(_tools: object) -> dict[str, object]:
        return {"id": "sub-1", "status": "Wrong Answer"}

    def _analyze(_submission: object, question: str) -> dict[str, object]:
        citation = _citation()
        return {
            "facts": ["f"],
            # Question-specific, like the real path: a claim is what a verdict binds to.
            "hypotheses": [f"{question} -> the status alone does not locate a code line"],
            "citations": [citation],
            "citation_checks": [
                {"chunk_id": citation["chunk_id"], "verdict": "verified", "detail": ""}
            ],
        }

    monkeypatch.setattr(smoke, "DeepseekModel", _Model)
    monkeypatch.setattr(smoke, "UlticodeClient", _Client)
    monkeypatch.setattr(smoke, "build_tools", lambda _client: {})
    monkeypatch.setattr(smoke, "first_wrong_answer_submission", _first)
    monkeypatch.setattr(smoke, "analyze_submission", _analyze)
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setenv("ULTICODE_CITATION_SUPPORT", "1")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "placeholder-not-a-real-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "test-model")
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(tmp_path / "verdicts.json"))
    for name in ("DEEPSEEK_MAX_CALLS", "DEEPSEEK_MAX_TOKENS"):
        monkeypatch.delenv(name, raising=False)


def test_opt_in_is_required_and_no_key_is_used_without_it(monkeypatch, capsys) -> None:
    monkeypatch.delenv("ULTICODE_CITATION_SUPPORT", raising=False)

    assert smoke.main_sync() == 0
    assert "reason=opt_in_not_set" in capsys.readouterr().out


def test_the_model_judges_one_row_per_question(monkeypatch, capsys, tmp_path) -> None:
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)

    assert smoke.main_sync() == 0
    output = capsys.readouterr().out
    assert "judge=model" in output
    assert "rows=3 calls=3" in output
    assert "human_review=not_performed" in output
    # `exists` is never asked of the model: the prompt carries no file or source path.
    assert all("SUBMISSION_FACTS" in prompt for prompt in calls)
    verdicts = json.loads((tmp_path / "verdicts.json").read_text(encoding="utf-8"))
    assert len(verdicts) == 3
    assert all(row["verdicts"]["exists"] is True for row in verdicts)
    assert all(row["verdicts"]["supports"] is True for row in verdicts)


def test_an_unsupported_citation_fails_the_gate(monkeypatch, capsys, tmp_path) -> None:
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": false, "derivable": false}'] * 3, calls)

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert "supports=0" in output
    assert "FAIL reason=citation_gate_failed" in output


def test_a_non_boolean_judgement_is_a_protocol_failure(monkeypatch, capsys, tmp_path) -> None:
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": "yes", "derivable": false}'] * 3, calls)

    assert smoke.main_sync() == 1
    assert "ModelProtocolError" in capsys.readouterr().out
