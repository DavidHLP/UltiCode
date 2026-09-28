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
            # The adapter records one usage entry per sent call, unknowns included.
            self.usage: list[dict[str, object]] = []

        async def __aenter__(self) -> "_Model":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def decide(self, messages: list[dict[str, object]]) -> object:
            calls.append(str(messages[-1]["content"]))
            self.usage.append({"total_tokens": 10})
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

    def _analyze(_submission: object, _question: str) -> dict[str, object]:
        citations = keyword_search("submission status source citation record", limit=3)
        return {
            "facts": ["f"],
            "hypotheses": ["the status alone does not locate a code line"],
            "citations": [hit.as_model_dict() for hit in citations],
            "citation_checks": [
                {"chunk_id": hit.chunk_id, "verdict": "verified", "detail": ""}
                for hit in citations
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
    # The threshold is read from the environment; pin it so the result cannot
    # depend on the developer's shell.
    monkeypatch.setenv("ULTICODE_CITATION_REQUIRED_ROWS", "3")


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
    assert all("SUBMISSION_FACTS" in prompt for prompt in calls)
    # The judgement must be the adapter's own answer field: `tool_specs={}` makes the
    # system message ask for `{"answer": ...}`, so a competing envelope fails.
    assert all("make the answer a JSON object" in prompt for prompt in calls)
    assert all("CLAIM:" in prompt and "QUOTE:" in prompt for prompt in calls)
    verdicts = json.loads((tmp_path / "verdicts.json").read_text(encoding="utf-8"))
    assert len(verdicts) == 3
    assert all(row["verdicts"]["exists"] is True for row in verdicts)
    assert all(row["verdicts"]["supports"] is True for row in verdicts)
    assert "USAGE | calls=3" in output
    # The artifact says who judged it, so it cannot be mistaken for a human pass.
    meta = json.loads(
        (tmp_path / "verdicts.json.meta.json").read_text(encoding="utf-8")
    )
    assert meta["judge"] == "model"
    assert meta["human_review"] == "not_performed"
    digest = meta["submission_facts_digest"]
    assert digest.startswith("sha256:")
    # Full length: a truncated digest would weaken the binding it exists for.
    assert len(digest) == len("sha256:") + 64


def test_an_unsupported_citation_fails_the_gate(monkeypatch, capsys, tmp_path) -> None:
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": false, "derivable": false}'] * 3, calls)

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert "supports=0" in output
    assert "FAIL reason=citation_gate_failed" in output


def test_fewer_emitted_citations_than_required_is_a_material_gap(
    monkeypatch, capsys, tmp_path
) -> None:
    """One emitted citation is not three; the run must say so."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    one = _citation()
    monkeypatch.setattr(
        smoke,
        "analyze_submission",
        lambda *_a, **_k: {
            "facts": ["f"],
            "hypotheses": ["the status alone does not locate a code line"],
            "citations": [one],
            "citation_checks": [
                {"chunk_id": one["chunk_id"], "verdict": "verified", "detail": ""}
            ],
        },
    )

    assert smoke.main_sync() == 1
    assert "reason=insufficient_citations emitted=1 required=3" in capsys.readouterr().out


def test_a_non_boolean_judgement_is_a_protocol_failure(monkeypatch, capsys, tmp_path) -> None:
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": "yes", "derivable": false}'] * 3, calls)

    assert smoke.main_sync() == 1
    assert "ModelProtocolError" in capsys.readouterr().out


def test_a_threshold_below_the_acceptance_is_refused(monkeypatch, capsys, tmp_path) -> None:
    """The environment may raise the bar, never lower it under the acceptance's."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_REQUIRED_ROWS", "1")

    assert smoke.main_sync() == 1
    assert "reason=citation_threshold_below_minimum" in capsys.readouterr().out
    assert calls == []


def test_unverified_citations_fail_before_any_call(monkeypatch, capsys, tmp_path) -> None:
    """An unverified row can never pass the gate, so it must not be billed."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    hits = keyword_search("submission status source citation record", limit=3)
    citations = [hit.as_model_dict() for hit in hits]
    # A drifted version: the worksheet's own integrity check must mark the row
    # unverified, and the run must fail before spending a call on it.
    citations[0]["version"] = "v2"
    monkeypatch.setattr(
        smoke,
        "analyze_submission",
        lambda *_a, **_k: {
            "facts": ["f"],
            "hypotheses": ["the status alone does not locate a code line"],
            "citations": citations,
            "citation_checks": [],
        },
    )

    assert smoke.main_sync() == 1
    assert "reason=citation_integrity_failed" in capsys.readouterr().out
    assert calls == []


def test_duplicate_judgement_keys_are_refused(monkeypatch, capsys, tmp_path) -> None:
    """`{"supports": false, "supports": true}` must not read as support."""
    calls: list[str] = []
    duplicated = '{"supports": false, "supports": true, "derivable": true}'
    _install(monkeypatch, tmp_path, [duplicated] * 3, calls)

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert "ModelProtocolError" in output
    assert "USAGE | calls=1" in output  # the billed call is still accounted for


def test_extra_judgement_fields_are_refused(monkeypatch, capsys, tmp_path) -> None:
    """The contract names two fields; an explanation must not ride along unread."""
    calls: list[str] = []
    extra = '{"supports": true, "derivable": true, "explanation": "because"}'
    _install(monkeypatch, tmp_path, [extra] * 3, calls)

    assert smoke.main_sync() == 1
    assert "ModelProtocolError" in capsys.readouterr().out


def test_the_default_verdict_path_is_run_scoped(monkeypatch) -> None:
    """Two runs from the documented directory must not write one file."""
    monkeypatch.delenv("ULTICODE_CITATION_VERDICTS", raising=False)

    first = smoke._verdict_file()
    second = smoke._verdict_file()

    assert first != second
    assert first.name.startswith("citation-verdicts-") and first.suffix == ".json"


def test_an_unusable_verdict_destination_fails_before_any_call(
    monkeypatch, capsys, tmp_path
) -> None:
    """Discovering a bad destination after paying for judgements wastes them."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    taken = tmp_path / "already-there.json"
    taken.write_text("{}", encoding="utf-8")
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(taken))

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert "reason=verdict_destination_unusable" in output
    assert calls == []


def test_a_non_integer_threshold_fails_cleanly(monkeypatch, capsys, tmp_path) -> None:
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_REQUIRED_ROWS", "three")

    assert smoke.main_sync() == 1
    assert "reason=citation_threshold_invalid" in capsys.readouterr().out


def test_the_default_artifact_stays_out_of_the_worktree(monkeypatch, tmp_path) -> None:
    """The documented invocation runs from `services/agent`; state is not the checkout."""
    monkeypatch.delenv("ULTICODE_CITATION_VERDICTS", raising=False)
    monkeypatch.delenv("XDG_STATE_HOME", raising=False)
    monkeypatch.setenv("HOME", str(tmp_path))

    path = smoke._verdict_file()

    assert path.is_absolute()
    assert path.parent == tmp_path / ".local" / "state" / "ulticode"
    assert not str(path).startswith(str(Path.cwd()))


def test_an_aborted_run_releases_the_claimed_destination(
    monkeypatch, capsys, tmp_path
) -> None:
    """An empty placeholder would block every later run on an explicit path."""
    calls: list[str] = []
    destination = tmp_path / "verdicts.json"
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))
    hits = keyword_search("submission status source citation record", limit=3)
    citations = [hit.as_model_dict() for hit in hits]
    citations[0]["version"] = "v2"
    monkeypatch.setattr(
        smoke,
        "analyze_submission",
        lambda *_a, **_k: {
            "facts": ["f"],
            "hypotheses": ["the status alone does not locate a code line"],
            "citations": citations,
            "citation_checks": [],
        },
    )

    assert smoke.main_sync() == 1
    assert "reason=citation_integrity_failed" in capsys.readouterr().out
    # The claim is released, so a later run can use the same path.
    smoke._release_unfinished_claim(destination)
    assert not destination.exists()


def test_a_failed_sidecar_claim_rolls_back_the_primary(
    monkeypatch, capsys, tmp_path
) -> None:
    """A half-claimed pair must not leave the primary file behind."""
    calls: list[str] = []
    destination = tmp_path / "verdicts.json"
    # The primary is free, the sidecar is taken: the claim must fail and undo.
    (tmp_path / "verdicts.json.meta.json").write_text("{}", encoding="utf-8")
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))

    assert smoke.main_sync() == 1
    assert "reason=verdict_destination_unusable" in capsys.readouterr().out
    assert not destination.exists()
    assert calls == []
