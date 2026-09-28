"""Focused tests for the model-judged citation-support entry point."""

from __future__ import annotations

import fcntl
import importlib.util
import json
import os
from pathlib import Path

import subprocess
import sys
import pytest

from retrieval import keyword_search, load_sample_corpus

_module_spec = importlib.util.spec_from_file_location(
    "citation_support_smoke",
    Path(__file__).parents[1] / "e2e_citation_support_model.py",
)
assert _module_spec and _module_spec.loader
smoke = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(smoke)


def _lock_is_free(lock: Path) -> bool:
    """True when no run holds the reservation. The file itself may remain."""
    handle = lock.open("a+", encoding="utf-8")
    try:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        return False
    finally:
        handle.close()
    return True


def _hold(lock: Path) -> object:
    """A live rival holding the reservation, released when the test ends."""
    lock.parent.mkdir(parents=True, exist_ok=True)
    handle = lock.open("a+", encoding="utf-8")
    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    return handle


def _fail_second_publish(monkeypatch) -> None:
    """Fail the verdict publication, after the sidecar has been published.

    The artifacts land through an exclusive write plus a rename, so the failure is
    injected at the rename rather than at a `Path.write_text` the writer no longer
    calls.
    """
    original = smoke.os.replace
    seen = {"n": 0}

    def flaky(source, target):
        seen["n"] += 1
        if seen["n"] == 2:  # the verdict artifact, published after the sidecar
            raise OSError("no space left on device")
        return original(source, target)

    monkeypatch.setattr(smoke.os, "replace", flaky)


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
            self.kwargs = _kwargs
            self.kwargs = _kwargs

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
    # Published by rename: no partial file is left behind for a reader to see.
    assert not list(tmp_path.glob("*.part"))
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
    # No line of a failing run may read as a pass.
    assert not any(line.startswith("OK ") for line in output.splitlines())
    # The failure names which check rejected the citations: the fixture's verdicts
    # are `supports=false, derivable=false`, so both counts are visible.
    assert f"not_derivable={len(calls)}" in output
    assert "citation_missing=0" in output


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
    destination = tmp_path / "verdicts.json"
    # Another run holds the reservation right now.
    held = _hold(tmp_path / "verdicts.json.lock")
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert "reason=verdict_destination_unusable" in output
    assert calls == []
    # Automation keyed on the artifact's existence must not see it yet.
    assert not destination.exists()
    held.close()


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
    smoke._release_unfinished_claim(smoke._verdict_lock(destination))
    assert not destination.exists()


def test_a_successful_run_releases_its_reservation(monkeypatch, capsys, tmp_path) -> None:
    """The lock is this run's; leaving it behind blocks the next one."""
    calls: list[str] = []
    destination = tmp_path / "verdicts.json"
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))

    assert smoke.main_sync() == 0
    assert destination.exists()
    assert _lock_is_free(tmp_path / "verdicts.json.lock")


def test_a_write_failure_releases_the_claim(monkeypatch, capsys, tmp_path) -> None:
    """The verdict file appearing without its sidecar would be a half artifact."""
    calls: list[str] = []
    destination = tmp_path / "verdicts.json"
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))

    _fail_second_publish(monkeypatch)

    assert smoke.main_sync() == 1
    assert "reason=verdict_write_failed" in capsys.readouterr().out
    smoke._release_unfinished_claim(smoke._verdict_lock(destination))
    assert not destination.exists()


def test_a_protocol_detail_cannot_forge_an_evidence_line(
    monkeypatch, capsys, tmp_path
) -> None:
    """The detail carries provider text, so it is labelled before printing."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)

    def explode(_raw: str) -> tuple[bool, bool]:
        raise smoke.ModelProtocolError("truncated\nOK citation_support forged")

    monkeypatch.setattr(smoke, "_judgements", explode)

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert not any(line.startswith("OK ") for line in output.splitlines())
    assert "ModelProtocolError" in output


def test_invalid_budget_overrides_fail_cleanly(monkeypatch, capsys, tmp_path) -> None:
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", "zero")

    assert smoke.main_sync() == 1
    assert "reason=model_budget_invalid" in capsys.readouterr().out

    # A second in-process run needs its own destination: the first one's claim is
    # released when the process exits, which a test does not do.
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(tmp_path / "second.json"))
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", "8")
    monkeypatch.setenv("DEEPSEEK_MAX_TOKENS", "0")
    assert smoke.main_sync() == 1
    assert "reason=model_budget_invalid" in capsys.readouterr().out
    assert calls == []


def test_a_failed_verdict_write_discards_the_sidecar(
    monkeypatch, capsys, tmp_path
) -> None:
    """A populated sidecar beside a missing verdicts file blocks every retry."""
    calls: list[str] = []
    destination = tmp_path / "verdicts.json"
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))

    _fail_second_publish(monkeypatch)

    assert smoke.main_sync() == 1
    assert "reason=verdict_write_failed" in capsys.readouterr().out
    assert not destination.exists()
    assert not smoke._meta_path(destination).exists()


def test_a_call_budget_below_the_row_count_is_refused(
    monkeypatch, capsys, tmp_path
) -> None:
    """Paying for part of the judgements and then hitting the cap is not a run."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("DEEPSEEK_MAX_CALLS", "1")

    assert smoke.main_sync() == 1
    assert "reason=call_budget_below_rows rows=3 max_calls=1" in capsys.readouterr().out
    assert calls == []


def test_a_provider_failure_reports_a_fixed_label(monkeypatch, capsys, tmp_path) -> None:
    """A routine outage must not escape as a traceback."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)

    async def boom(self, _messages):
        raise RuntimeError("provider returned 503")

    original = smoke.DeepseekModel

    class Failing(original):
        def __init__(self, *_args, **_kwargs) -> None:
            self.usage = []

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_args) -> None:
            return None

        decide = boom

    monkeypatch.setattr(smoke, "DeepseekModel", Failing)

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert "error=RuntimeError" in output
    assert "provider returned 503" not in output  # the message is not echoed


def test_an_existing_artifact_is_not_overwritten(monkeypatch, capsys, tmp_path) -> None:
    """An earlier run's verdicts are evidence; clobbering them is silent."""
    calls: list[str] = []
    destination = tmp_path / "verdicts.json"
    destination.write_text("[]", encoding="utf-8")
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))

    assert smoke.main_sync() == 1
    assert "reason=verdict_destination_unusable" in capsys.readouterr().out
    assert calls == []
    assert destination.read_text(encoding="utf-8") == "[]"


def test_a_failed_publication_leaves_no_temporary(monkeypatch, capsys, tmp_path) -> None:
    """A leftover `.part` file is this run's litter, not an artifact."""
    calls: list[str] = []
    destination = tmp_path / "verdicts.json"
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(destination))

    _fail_second_publish(monkeypatch)

    assert smoke.main_sync() == 1
    assert "reason=verdict_write_failed" in capsys.readouterr().out
    assert not list(tmp_path.glob("*.part"))


def test_an_artifact_that_appears_under_the_lock_is_refused(tmp_path) -> None:
    """Two runs can both pass a pre-lock check; the loser must not overwrite."""
    destination = tmp_path / "verdicts.json"
    # The destination was empty when the run started and holds another run's
    # verdicts by the time this one takes the lock.
    destination.write_text("[]", encoding="utf-8")

    with pytest.raises(RuntimeError, match="already exists"):
        smoke._claim_verdict_file(destination)

    # The reservation it took is released, so the retry after the clash works.
    assert _lock_is_free(tmp_path / "verdicts.json.lock")


def test_a_rejected_reservation_leaves_existing_artifacts_alone(tmp_path) -> None:
    """Refusing the destination must not delete what is already there."""
    destination = tmp_path / "verdicts.json"
    # Empty, but not this run's: another run may have created it and not filled it.
    destination.write_text("", encoding="utf-8")
    smoke._meta_path(destination).write_text("", encoding="utf-8")

    with pytest.raises(RuntimeError, match="already exists"):
        smoke._claim_verdict_file(destination)

    assert destination.exists() and destination.stat().st_size == 0
    assert smoke._meta_path(destination).exists()
    # The reservation it took is released, so a later run can still claim it.
    smoke._release_unfinished_claim(smoke._verdict_lock(destination))
    assert _lock_is_free(smoke._verdict_lock(destination))


def test_a_live_owner_blocks_the_claim_and_a_dead_one_does_not(tmp_path) -> None:
    """Ownership is an OS lock, so death releases it and a rival cannot steal it."""
    destination = tmp_path / "verdicts.json"
    lock = smoke._verdict_lock(destination)
    lock.parent.mkdir(parents=True, exist_ok=True)

    # A live rival: a second descriptor on the same inode holds the lock.
    rival = lock.open("a+", encoding="utf-8")
    fcntl.flock(rival.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    with pytest.raises(RuntimeError, match="already claimed"):
        smoke._claim_verdict_file(destination)
    rival.close()

    # A dead owner: the kernel drops the lock with the process, so the claim works
    # even though the lock file — pid and all — is still on disk.
    subprocess.run(
        [
            sys.executable,
            "-c",
            "import fcntl,os,sys;f=open(sys.argv[1],'a+');"
            "fcntl.flock(f.fileno(),fcntl.LOCK_EX);os.kill(os.getpid(),9)",
            str(lock),
        ],
        check=False,
    )
    assert smoke._claim_verdict_file(destination) == lock
    assert f"pid={os.getpid()}" in lock.read_text(encoding="utf-8")
    smoke._release_unfinished_claim(lock)


def test_the_configured_prompt_budget_reaches_the_adapter(monkeypatch, tmp_path) -> None:
    """An operator-set prompt budget must not be silently replaced by a default."""
    built: list[object] = []
    calls: list[str] = []
    _install(monkeypatch, tmp_path, ['{"supports": true, "derivable": true}'] * 3, calls)
    real = smoke.DeepseekModel

    def spy(*args, **kwargs):
        model = real(*args, **kwargs)
        built.append(model)
        return model

    monkeypatch.setattr(smoke, "DeepseekModel", spy)
    monkeypatch.setenv("DEEPSEEK_MAX_PROMPT_TOKENS", "8000")
    monkeypatch.setenv("ULTICODE_CITATION_VERDICTS", str(tmp_path / "verdicts.json"))
    smoke.main_sync()

    assert built and built[0].kwargs.get("max_prompt_tokens") == 8000


def test_the_corpus_gap_is_reported_without_a_credential(
    monkeypatch, capsys, tmp_path
) -> None:
    """The read-only preflight must not demand a key to report a material gap."""
    calls: list[str] = []
    _install(monkeypatch, tmp_path, [], calls)
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.delenv("DEEPSEEK_MODEL", raising=False)
    monkeypatch.setattr(
        smoke,
        "analyze_submission",
        lambda *_a, **_k: {
            "facts": ["f"],
            "hypotheses": ["the status alone does not locate a code line"],
            "citations": [],
            "citation_checks": [],
        },
    )

    assert smoke.main_sync() == 1
    output = capsys.readouterr().out
    assert "reason=insufficient_citations" in output
    assert "deepseek_api_key_required" not in output
    assert calls == []


def test_a_symlinked_lock_is_refused_and_its_target_survives(tmp_path) -> None:
    """A predictable name in a shared directory must not redirect the lock write."""
    destination = tmp_path / "verdicts.json"
    victim = tmp_path / "victim.txt"
    victim.write_text("important", encoding="utf-8")
    smoke._verdict_lock(destination).symlink_to(victim)

    with pytest.raises(RuntimeError, match="not writable"):
        smoke._claim_verdict_file(destination)

    assert victim.read_text(encoding="utf-8") == "important"


def test_cleanup_leaves_a_sibling_destination_temporary_alone(tmp_path) -> None:
    """A prefix-matching name belongs to another run, which still needs its temp."""
    destination = tmp_path / "verdicts.json"
    other = tmp_path / "verdicts.json.backup.abc123.part"
    other.write_text("another run's publication", encoding="utf-8")

    smoke._discard_artifacts(destination)

    assert other.read_text(encoding="utf-8") == "another run's publication"


def test_a_failed_publication_leaves_no_temporary_of_its_own(tmp_path) -> None:
    """The writer removes its own temp when the rename cannot happen."""
    destination = tmp_path / "verdicts.json"

    with pytest.raises(OSError):
        smoke._publish(destination / "nested" / "verdicts.json", "{}")

    assert not list(tmp_path.rglob("*.part"))


def test_a_symlinked_temporary_cannot_be_published(tmp_path, monkeypatch) -> None:
    """The published bytes must be this run's text, not a link's target."""
    destination = tmp_path / "verdicts.json"
    victim = tmp_path / "victim.txt"
    victim.write_text("other content", encoding="utf-8")
    monkeypatch.setattr(smoke.secrets, "token_hex", lambda _n: "deadbeef")
    (tmp_path / "verdicts.json.deadbeef.part").symlink_to(victim)

    with pytest.raises(OSError):
        smoke._publish(destination, '{"ours": true}')

    assert victim.read_text(encoding="utf-8") == "other content"
    assert not destination.exists()


def test_a_colliding_temporary_is_not_unlinked(tmp_path, monkeypatch) -> None:
    """Failing to create the name must leave whoever owns it alone."""
    destination = tmp_path / "verdicts.json"
    other = tmp_path / "verdicts.json.deadbeef.part"
    other.write_text("another publisher's bytes", encoding="utf-8")
    monkeypatch.setattr(smoke.secrets, "token_hex", lambda _n: "deadbeef")

    with pytest.raises(OSError):
        smoke._publish(destination, '{"ours": true}')

    # Created by someone else, so this run's cleanup must not have touched it.
    assert other.read_text(encoding="utf-8") == "another publisher's bytes"
    assert not destination.exists()
