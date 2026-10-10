"""Synthetic offline proofs; these fixtures are never real acceptance records."""
import copy
import hashlib
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

from authorized_budget_period import REVALIDATION_POLICY_ID, REVALIDATION_POLICY
from agent_service import gate

sys.path.insert(0, str(Path(__file__).parents[1]))
import e2e_u04_demo as u04


def test_fresh_prior_prefix_requires_canonical_dispatch_and_raw_ids(tmp_path, monkeypatch):
    identity = {"period_id": "synthetic", "identity": "a" * 32, "config_sha256": "b" * 64,
                "policy_id": REVALIDATION_POLICY_ID}
    monkeypatch.setattr(gate, "_expected_identity", lambda value: value)
    monkeypatch.setattr(gate, "_verified_prior_exchanges", lambda raw, receipts: [])
    monkeypatch.setattr(gate, "_check_prior_model_records", lambda *args: None)
    history = dict(REVALIDATION_POLICY["history"])

    def snapshot(attempts):
        return {"policy_id": REVALIDATION_POLICY_ID, "period_identity": identity["identity"],
                "config_sha256": identity["config_sha256"], "state": "active", "sql_gate": "active",
                "halted": False, "retained_history": history, "legacy_history": "retained_unknown_encumbered",
                "attempts": attempts, "actual_micro_usd": attempts * 2, "committed_micro_usd": attempts * 9600,
                "unknown_usage_attempts": 0, "unsettled_attempts": 0,
                "cumulative_attempts": 47 + attempts, "cumulative_committed_micro_usd": 801746 + attempts * 9600}

    lanes = {"本人提交检索分析": ("prior_source", 1), "三引用支持负例": ("prior_citation_judge", 3),
             "20dev评估": ("prior_development", 40)}
    canonical, items = [], []
    for name, (lane, count) in lanes.items():
        ids = [f"{lane}-{i}" for i in range(count)]
        canonical.extend({"attempt_id": value, "lane": lane, "peak_micro_usd": 2,
                          "request_sha256": "c" * 64} for value in ids)
        path = tmp_path / f"{lane}.json"
        raw = json.dumps({"attempt_ids": ids, "records": {"facts": ["synthetic fact"]}}).encode()
        path.write_bytes(raw)
        path.chmod(0o600)
        items.append({"name": name, "raw_record": {"path": path.name, "sha256": hashlib.sha256(raw).hexdigest()}})
    receipts = [{"attempt_id": r["attempt_id"], "purpose": r["lane"], "actual_micro_usd": 2,
                 "request_sha256": r["request_sha256"], "usage_known": True, "settled": True} for r in canonical]
    before = snapshot(44)
    prior = {"items": items, "authorized_period": {"identity": identity, "purposes": [x[0] for x in lanes.values()],
             "before": snapshot(0), "after": before, "receipts": receipts}}
    gate._check_prior_budget_prefix(prior, before, identity, canonical, tmp_path)
    forged = copy.deepcopy(prior)
    forged["authorized_period"]["receipts"][0]["request_sha256"] = "d" * 64
    with pytest.raises(gate.GateError, match="prior_five_budget_receipt_mismatch"):
        gate._check_prior_budget_prefix(forged, before, identity, canonical, tmp_path)
    forged = copy.deepcopy(prior)
    forged["authorized_period"]["after"]["retained_history"]["unknown_attempts"] = 0
    with pytest.raises(gate.GateError, match="budget_retained_history_mismatch"):
        gate._check_prior_budget_prefix(forged, before, identity, canonical, tmp_path)


def test_u04_frozen_policy_and_runtime_identity_are_equal():
    policy = REVALIDATION_POLICY
    args = SimpleNamespace(policy_id=REVALIDATION_POLICY_ID, period_id="synthetic",
                           period_identity="a" * 32, config_sha256="b" * 64)
    candidate = {"model_budget_policy": {"policy_id": args.policy_id, "limit_micro_usd": policy["limit_micro_usd"],
                 "attempts": policy["attempts"], "prompt_token_cap": policy["prompt_token_cap"],
                 "lanes": {name: dict(lane) for name, lane in policy["lanes"].items()}}}
    proof = {"budget_anchor": {"policy_id": args.policy_id, "period_id": args.period_id,
             "identity": args.period_identity, "config_sha256": args.config_sha256}}
    u04._check_candidate_policy(args, candidate, proof)
    proof["budget_anchor"]["identity"] = "c" * 32
    with pytest.raises(ValueError, match="u04_budget_identity_mismatch"):
        u04._check_candidate_policy(args, candidate, proof)
    candidate["model_budget_policy"]["attempts"] = 999
    with pytest.raises(ValueError, match="candidate_budget_policy_mismatch"):
        u04._check_candidate_policy(args, candidate, proof)


@pytest.mark.parametrize("policy_id", ["acceptance-revalidation-v2", "acceptance-revalidation-v3", "acceptance-revalidation-v4", "acceptance-revalidation-v5", "acceptance-revalidation-v6"])
def test_rollover_snapshot_retains_conservative_liability_instead_of_actual(policy_id):
    from authorized_budget_period import policy_for
    policy = policy_for(policy_id)
    history = dict(policy["history"])
    identity = {"policy_id": policy_id, "identity": "a" * 32, "config_sha256": "b" * 64}
    assert history["attempts"] + policy["attempts"] == history["cumulative_attempt_limit"]
    assert sum(lane["attempts"] for lane in policy["lanes"].values()) == policy["attempts"]
    limit = history["known_committed_micro_usd"] + history["unknown_encumbrance_micro_usd"] + policy["limit_micro_usd"]
    assert limit <= history["cumulative_limit_micro_usd"]
    snapshot = {"policy_id": policy_id, "period_identity": identity["identity"],
                "config_sha256": identity["config_sha256"], "state": "active", "sql_gate": "active",
                "halted": 0, "attempts": policy["attempts"], "actual_micro_usd": 1, "committed_micro_usd": policy["limit_micro_usd"],
                "retained_history": history, "legacy_history": "retained_unknown_encumbered",
                "cumulative_attempts": history["cumulative_attempt_limit"], "cumulative_committed_micro_usd": limit}
    gate._check_fresh_snapshot(snapshot, identity)
    snapshot["cumulative_committed_micro_usd"] -= history["known_committed_micro_usd"] - history["known_actual_micro_usd"]
    with pytest.raises(gate.GateError, match="budget_retained_history_mismatch"):
        gate._check_fresh_snapshot(snapshot, identity)
    snapshot["cumulative_committed_micro_usd"] = limit
    snapshot["retained_history"]["unknown_encumbrance_micro_usd"] = 0
    with pytest.raises(gate.GateError, match="budget_retained_history_mismatch"):
        gate._check_fresh_snapshot(snapshot, identity)


def test_provider_verified_source_answer_cannot_be_replaced():
    from e2e_sourced_analysis_model import ANSWER_CONTRACT, QUESTION
    evidence = {"facts": ["synthetic fact"], "allowed_hypotheses": ["synthetic hypothesis"],
                "citations": [{"doc_id": "synthetic-doc"}]}
    answer = json.dumps({"facts": evidence["facts"], "hypotheses": evidence["allowed_hypotheses"],
                         "citations": ["synthetic-doc"]})
    prompt = ("Analyze the submission using only the supplied evidence. "
              f"{ANSWER_CONTRACT} EVIDENCE_JSON={json.dumps(evidence, ensure_ascii=False)}")
    exchange = ({"messages": [{"role": "system", "content": "synthetic"},
                               {"role": "user", "content": prompt}]},
                json.dumps({"answer": answer}), "stop")
    raw = {"records": {"model_answer": answer, "facts": evidence["facts"], "citations": evidence["citations"],
           "retrieval_calls": [{"query": QUESTION, "results": evidence["citations"]}]}}
    gate._check_prior_model_records("本人提交检索分析", raw, [exchange])
    raw["records"]["model_answer"] = "forged answer with original receipt IDs"
    with pytest.raises(gate.GateError, match="prior_five_provider_result_mismatch"):
        gate._check_prior_model_records("本人提交检索分析", raw, [exchange])


def test_citation_objects_and_deterministic_verdicts_cannot_be_replaced():
    from citation_review import revalidation_citation_cases
    from e2e_citation_support_model import JUDGE_CONTRACT
    facts = ["Synthetic submission status is Wrong Answer."]
    probes = revalidation_citation_cases(facts)
    exchanges, rows = [], []
    for probe, supports in zip(probes, (True, True, False)):
        assert probe["row"].integrity_verdict == "verified"
        prompt = JUDGE_CONTRACT + "\nINPUT_JSON " + json.dumps(
            {"CLAIM": probe["row"].claim, "QUOTE": probe["row"].quote, "SUBMISSION_FACTS": facts}, ensure_ascii=True)
        exchanges.append(({"messages": [{"role": "system", "content": "synthetic"},
                                         {"role": "user", "content": prompt}]},
                          json.dumps({"answer": json.dumps({"supports": supports, "derivable": False})}), "stop"))
        rows.append({"exists": True, "supports": supports, "derivable": False, "gate_rejected": not supports})
    raw = {"records": {"citations": rows}}
    gate._check_prior_model_records("三引用支持负例", raw, exchanges, facts)
    for field in ("exists", "gate_rejected"):
        forged = copy.deepcopy(raw)
        forged["records"]["citations"][2][field] = False
        with pytest.raises(gate.GateError, match="prior_five_provider_result_mismatch"):
            gate._check_prior_model_records("三引用支持负例", forged, exchanges, facts)
    forged = copy.deepcopy(exchanges)
    forged[0][0]["messages"][-1]["content"] = JUDGE_CONTRACT + '\nINPUT_JSON {"QUOTE":"unrelated"}'
    with pytest.raises(gate.GateError, match="prior_five_provider_result_mismatch"):
        gate._check_prior_model_records("三引用支持负例", raw, forged, facts)
