"""Narrow offline regressions for U04 holdout and reliability orchestration."""
import asyncio
import hashlib
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).parents[1]))
import e2e_u04_demo as u04


def _case(case_id, query, *, split="holdout3", evidence=("synthetic-doc",), behavior="cite"):
    return SimpleNamespace(
        case_id=case_id,
        split=split,
        query=query,
        required_evidence=evidence,
        answerable=True,
        expected_behavior=behavior,
        allowed_behavior="synthetic allowed",
        forbidden_behavior="synthetic forbidden",
    )


def test_load_claimed_holdout_rejects_normalized_reused_query_with_changed_case_fields(
    monkeypatch, tmp_path,
):
    candidate_sha, bundle_sha = "a" * 64, "b" * 64
    raw = b"synthetic sealed fixture"
    holdout_path = tmp_path / "holdout-v3.json"
    marker = tmp_path / "holdout-v3.consumed"
    reused = _case("new-case-id", "  SYNTHETIC   reused Query! ", evidence=("different-doc",), behavior="refuse")
    baseline = (
        _case("old-case-id", "synthetic reused query!"),
        *(_case(f"baseline-{index}", f"synthetic baseline query {index}") for index in range(9)),
    )
    calls = []

    monkeypatch.setattr(u04, "claim_holdout_once", lambda **kwargs: marker)
    monkeypatch.setattr(u04, "canonical_holdout3", lambda: holdout_path)
    monkeypatch.setattr(u04.delivery, "_read_json", lambda path: ({
        "candidate_inputs_sha256": candidate_sha,
        "acceptance_bundle_sha256": bundle_sha,
        "holdout_sha256": hashlib.sha256(raw).hexdigest(),
    }, raw))
    monkeypatch.setattr(u04, "_read_case_list", lambda path, *, claimed_marker: ([], raw))

    def load_cases(*, path, text=None):
        calls.append(Path(path))
        if Path(path) == holdout_path:
            return (reused, *(
                _case(f"new-{index}", f"unseen synthetic query {index}")
                for index in range(9)
            ))
        if Path(path).name == "keyword_cases.json":
            return baseline
        return ()

    monkeypatch.setattr(u04, "load_cases", load_cases)
    with pytest.raises(ValueError, match="holdout3_case_overlap"):
        u04._load_claimed_holdout(candidate_sha, bundle_sha, hashlib.sha256(raw).hexdigest())
    assert len(calls) == 3
    assert calls[0] == holdout_path
    assert calls[1].name == "keyword_cases.json"


def _reliability_fixture(tmp_path, monkeypatch):
    import boundary_evaluation
    import retrieval

    evidence_root = tmp_path / "evidence"
    run_dir = evidence_root / "run"
    run_dir.mkdir(parents=True)
    monkeypatch.setattr(u04, "_real_u03_refs", lambda _preflight, names: {
        name: {"path": f"{name}.json", "sha256": "c" * 64} for name in names
    })

    async def passing_probe(*_args, **_kwargs):
        return {"status": "PASS"}

    monkeypatch.setattr(u04, "_probe_r02", passing_probe)
    monkeypatch.setattr(u04, "_probe_r03", passing_probe)
    monkeypatch.setattr(u04, "_probe_r06", passing_probe)
    document = retrieval.SourceDocument(
        doc_id="synthetic", version="v1", source_path="synthetic.md",
        source_position="line 1", access_scope="synthetic",
        sample_kind="synthetic", text="Synthetic corpus text.",
    )
    monkeypatch.setattr(retrieval, "load_sample_corpus", lambda: (document,))
    return {"evidence_root": evidence_root}, run_dir, boundary_evaluation


@pytest.mark.parametrize("provider_error", [False, True])
def test_run_reliability_keeps_prior_rows_and_records_r07_outcome(
    tmp_path, monkeypatch, provider_error,
):
    preflight, run_dir, boundary = _reliability_fixture(tmp_path, monkeypatch)

    async def unsupported(*_args, **_kwargs):
        if provider_error:
            raise RuntimeError("synthetic provider unavailable")
        # Synthetic unsupported claim received a positive support verdict.
        return True, False

    monkeypatch.setattr(boundary, "judge_citation", unsupported)
    rows = asyncio.run(u04._run_reliability(preflight, object(), run_dir))

    assert {row["scenario"] for row in rows} == {f"R{i:02}" for i in range(1, 11)}
    assert all(row["status"] == "PASS" for row in rows if row["scenario"] != "R07")
    r07 = next(row for row in rows if row["scenario"] == "R07")
    assert r07["status"] == ("INCOMPLETE" if provider_error else "FAIL")
    evidence = json.loads((run_dir / "R07.json").read_text())
    assert evidence["status"] == r07["status"]
    if provider_error:
        assert evidence["observations"]["execution_error"] == "RuntimeError"
    else:
        assert evidence["observations"]["unsupported_supports"] is True


def test_execute_corpus_load_failure_precedes_demo_and_holdout_and_closes_guard(
    tmp_path, monkeypatch,
):
    import retrieval

    class Guard:
        closed = False

        def close(self):
            self.closed = True

    guard = Guard()
    demo_calls, holdout_calls = [], []

    async def forbidden_demo(*_args, **_kwargs):
        demo_calls.append(True)

    def forbidden_holdout(*_args, **_kwargs):
        holdout_calls.append(True)

    def fail_corpus_load():
        raise OSError("synthetic corpus unavailable")

    monkeypatch.setattr(retrieval, "load_sample_corpus", fail_corpus_load)
    monkeypatch.setattr(u04, "_run_human_demo", forbidden_demo)
    monkeypatch.setattr(u04, "_load_claimed_holdout", forbidden_holdout)
    preflight = {
        "guard": guard,
        "budget": object(),
        "lane": {},
        "purpose": "synthetic-only",
        "evidence_root": tmp_path,
        "development": {"status": "PASS"},
        "candidate_sha256": "a" * 64,
        "bundle_sha256": "b" * 64,
        "candidate": {"holdout3_sha256": "c" * 64},
        "u03": {"scenarios": []},
    }

    with pytest.raises(OSError, match="synthetic corpus unavailable"):
        asyncio.run(u04._execute(SimpleNamespace(), preflight))
    assert demo_calls == []
    assert holdout_calls == []
    assert guard.closed
