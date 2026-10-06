import asyncio
import importlib.util
import json
from pathlib import Path

import httpx
import pytest

from answer_evaluation import AnswerEvaluationError, evaluate_answer_cases
from authorized_budget_period import PeriodIdentity
from dav58_live_guard import GuardedTransport, IncrementalGuard
from deepseek_model import ModelBudgetExceeded
from keyword_evaluation import KeywordCase

_AGENT = Path(__file__).parents[1]


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


u03 = _load("e2e_u03_workflow_delivery_test", _AGENT / "e2e_u03_workflow.py")
u04 = _load("e2e_u04_demo_delivery_test", _AGENT / "e2e_u04_demo.py")


def test_holdout3_is_sealed_for_answer_evaluation():
    case = KeywordCase(
        case_id="synthetic-only",
        split="holdout3",
        query="synthetic query",
        required_evidence=("synthetic-doc",),
        answerable=True,
        expected_behavior="cite",
        allowed_behavior="synthetic expected behavior",
        forbidden_behavior="synthetic forbidden behavior",
    )
    with pytest.raises(AnswerEvaluationError, match="holdout3"):
        asyncio.run(evaluate_answer_cases((case,), model=object()))


def test_u03_requires_opt_in_before_network_or_artifact(monkeypatch, tmp_path):
    monkeypatch.delenv("ULTICODE_U03_E2E", raising=False)
    result = tmp_path / "result.json"
    status = u03.main([
        "--expected-head", "a" * 40,
        "--expected-base", "b" * 40,
        "--u02-gate", str(tmp_path / "missing-gate.json"),
        "--output", str(result),
    ])
    assert status == 1
    assert not result.exists()


def test_u04_missing_opt_in_writes_incomplete_without_consuming(monkeypatch, tmp_path):
    monkeypatch.delenv("ULTICODE_U04_E2E", raising=False)
    result = tmp_path / "result.json"
    status = u04.main([
        "--run",
        "--candidate", str(tmp_path),
        "--expected-head", "a" * 40,
        "--expected-base", "b" * 40,
        "--u02-gate", str(tmp_path / "missing-gate.json"),
        "--u03-result", str(tmp_path / "missing-u03.json"),
        "--candidate-inputs", str(tmp_path / "candidate.json"),
        "--acceptance-bundle", str(tmp_path / "bundle.json"),
        "--output", str(result),
    ])
    assert status == 1
    artifact = json.loads(result.read_text())
    assert artifact["status"] == "INCOMPLETE"
    assert artifact["formal_holdout_consumed"] is False
    assert not (tmp_path / "holdout-v3.consumed").exists()


def test_guarded_transport_accepts_dav53_lane_with_bounded_output(tmp_path):
    identity = PeriodIdentity("offline-test", "a" * 64, "b" * 32)
    guard = IncrementalGuard(
        tmp_path / "guard.json",
        period_identity=identity.identity,
        config_sha256=identity.config_sha256,
    )

    def handler(request):
        body = json.loads(request.content)
        assert body["max_tokens"] == 1000
        return httpx.Response(200, json={
            "model": "deepseek-flash",
            "choices": [{"message": {"content": "synthetic"}}],
            "usage": {"prompt_tokens": 10, "completion_tokens": 2, "total_tokens": 12},
        })

    async def request_once():
        async with httpx.AsyncClient(
            transport=GuardedTransport(guard, "dav53_scenarios", inner=httpx.MockTransport(handler)),
        ) as client:
            return await client.post(
                "https://api.deepseek.com/chat/completions",
                json={"model": "deepseek-flash", "messages": [{"role": "user", "content": "synthetic"}],
                      "temperature": 0, "max_tokens": 1000, "thinking": {"type": "disabled"}},
            )

    try:
        response = asyncio.run(request_once())
        assert response.status_code == 200
        assert guard.state["receipts"][0]["lane"] == "dav53_scenarios"
        assert guard.state["receipts"][0]["status"] == "settled"
        assert guard.state["pending_micro_usd"] == 0
    finally:
        guard.close()


def test_guarded_transport_rejects_unapproved_purpose_before_network(tmp_path):
    identity = PeriodIdentity("offline-test", "a" * 64, "b" * 32)
    guard = IncrementalGuard(
        tmp_path / "guard.json",
        period_identity=identity.identity,
        config_sha256=identity.config_sha256,
    )
    request = httpx.Request(
        "POST", "https://api.deepseek.com/chat/completions",
        content=json.dumps({"model": "deepseek-flash", "messages": [{"role": "user", "content": "x"}],
                            "temperature": 0, "max_tokens": 1000, "thinking": {"type": "disabled"}}),
    )
    try:
        with pytest.raises(ModelBudgetExceeded, match="unexpected paid lane or policy"):
            guard.begin(request, "ordinary")
        assert guard.state["receipts"] == []
    finally:
        guard.close()


def test_holdout3_reader_accepts_only_private_synthetic_case_file(tmp_path):
    path = tmp_path / "synthetic-holdout.json"
    path.write_text("[]")
    path.chmod(0o600)

    cases, raw = u04._read_case_list(path)

    assert cases == []
    assert raw == b"[]"
    link = tmp_path / "linked.json"
    link.symlink_to(path)
    with pytest.raises(OSError):
        u04._read_case_list(link)


def test_workflow_restart_owner_isolation_and_unconfirmed_save_fail_closed(monkeypatch, tmp_path):
    import uuid

    from agent_service import app as agent_app
    from agent_service.app import create_app

    class FakeClient:
        def __init__(self, **kwargs):
            self.owner = "owner-a" if kwargs["access_token"] == "access-a" else "owner-b"

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_):
            pass

        async def principal(self):
            return self.owner

        async def get_my_submission(self, source_id):
            return {"id": source_id, "status": "Accepted"}

    monkeypatch.setattr(agent_app, "load_u02_gate", lambda *args, **kwargs: {"synthetic_test_gate": True})
    state_path = tmp_path / "workflow.sqlite3"
    app = create_app(state_path=state_path, client_factory=FakeClient, u02_gate_path=tmp_path / "gate.json",
                     expected_head="a" * 40)
    owner_headers = {"cookie": "access_token=access-a; csrf_token=csrf", "x-csrf-token": "csrf"}
    foreign_headers = {"cookie": "access_token=access-b; csrf_token=csrf", "x-csrf-token": "csrf"}

    async def scenario():
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                source = str(uuid.uuid4())
                created = await client.post("/agent/threads", headers=owner_headers,
                    json={"sourceSubmissionId": source, "question": "如何复盘？"})
                thread = created.json()["data"]["threadId"]
                before = await client.get(f"/agent/threads/{thread}", headers=owner_headers)
                assert before.status_code == 200
                assert (await client.get(f"/agent/threads/{thread}", headers=foreign_headers)).status_code == 404
                refused = await client.post(f"/agent/threads/{thread}/save", headers=owner_headers,
                    json={"confirmationId": str(uuid.uuid4())})
                assert refused.status_code == 400
                cancelled = await client.post(f"/agent/threads/{thread}/cancel", headers=owner_headers, json={})
                assert cancelled.json()["data"]["status"] == "cancelled"

        restarted = create_app(state_path=state_path, client_factory=FakeClient)
        async with restarted.router.lifespan_context(restarted):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=restarted), base_url="http://test") as client:
                restored = await client.get(f"/agent/threads/{thread}", headers=owner_headers)
                assert restored.status_code == 200
                assert restored.json()["data"]["status"] == "cancelled"
                assert restored.json()["data"]["sourceSubmissionId"] == source

    asyncio.run(scenario())


def test_ambiguous_save_recovers_same_key_without_automatic_retry(monkeypatch, tmp_path):
    import uuid

    from agent_service import app as agent_app
    from agent_service.app import create_app
    from ulticode_client import UlticodeServiceError

    shared = {"plans": {}, "posts": 0}

    class FakeClient:
        def __init__(self, **_):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_):
            pass

        async def principal(self):
            return "owner-a"

        async def get_my_submission(self, source_id):
            return {"id": source_id, "status": "Accepted"}

        async def save_learning_plan(self, **payload):
            shared["posts"] += 1
            receipt = {
                "id": str(uuid.uuid4()),
                "sourceSubmissionId": payload["source_submission_id"],
                "draftVersion": payload["draft_version"],
                "title": payload["title"],
                "content": payload["content"],
            }
            shared["plans"][payload["idempotency_key"]] = receipt
            raise TimeoutError("synthetic response lost after commit")

        async def get_learning_plan_by_key(self, key):
            if key not in shared["plans"]:
                raise UlticodeServiceError(40400)
            return shared["plans"][key]

    monkeypatch.setattr(agent_app, "load_u02_gate", lambda *args, **kwargs: {"synthetic_test_gate": True})
    app = create_app(state_path=tmp_path / "ambiguous.sqlite3", client_factory=FakeClient,
                     u02_gate_path=tmp_path / "gate.json", expected_head="b" * 40)
    headers = {"cookie": "access_token=access-a; csrf_token=csrf", "x-csrf-token": "csrf"}

    async def scenario():
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=headers,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                thread = created.json()["data"]["threadId"]
                initial = created.json()["data"]
                confirmed = await client.post(f"/agent/threads/{thread}/confirm", headers=headers,
                    json={"draftVersion": initial["draft"]["draftVersion"], "paramsDigest": initial["paramsDigest"], "confirm": True})
                confirmation_id = confirmed.json()["data"]["confirmation"]["id"]
                attempted = await client.post(f"/agent/threads/{thread}/save", headers=headers,
                    json={"confirmationId": confirmation_id})
                assert attempted.status_code == 200
                assert attempted.json()["data"]["status"] == "unknown"
                assert shared["posts"] == 1
                recovered = await client.post(f"/agent/threads/{thread}/recover", headers=headers, json={"retry": False})
                assert recovered.status_code == 200
                assert recovered.json()["data"]["status"] == "saved"
                assert shared["posts"] == 1

    asyncio.run(scenario())


@pytest.mark.parametrize("point", ["before_java_save_dispatch", "after_java_save_response"])
def test_u03_kill_hook_is_thread_scoped_and_exits_only_isolated_child(tmp_path, point):
    import multiprocessing

    marker = tmp_path / "fault.json"
    hook = u03._kill_at_fault(point, "thread-owned", marker.resolve())

    def worker():
        hook(point, {"thread_id": "thread-other", "run_id": "run"})
        hook(point, {"thread_id": "thread-owned", "run_id": "run"})
        raise AssertionError("fault hook did not terminate child")

    process = multiprocessing.get_context("fork").Process(target=worker)
    process.start()
    process.join(5)
    assert process.exitcode == 86
    event = json.loads(marker.read_text())
    assert event == {
        "schema": "ulticode-u03-fault-marker-v1",
        "point": point,
        "thread_id": "thread-owned",
        "run_id": "run",
        "plan_id": None,
    }


def test_response_loss_hook_drops_real_request_response_without_retry():
    committed = {}
    post_count = 0

    def handler(request):
        nonlocal post_count
        if request.method == "POST" and request.url.path == "/learning-plans":
            post_count += 1
            key = request.headers["Idempotency-Key"]
            committed.setdefault(key, {"id": "plan-1", "sourceSubmissionId": "source-1"})
            return httpx.Response(200, json=committed[key])
        if request.method == "GET":
            key = request.url.path.rsplit("/", 1)[-1]
            return httpx.Response(200, json=committed[key]) if key in committed else httpx.Response(404)
        return httpx.Response(405)

    async def scenario():
        transport = u03._DropCommittedSaveTransport(httpx.MockTransport(handler))
        async with httpx.AsyncClient(transport=transport) as client:
            with pytest.raises(httpx.ReadError):
                await client.post("http://127.0.0.1:9100/learning-plans",
                                  headers={"Idempotency-Key": "same-key"}, json={"title": "test"})
            readback = await client.get("http://127.0.0.1:9100/learning-plans/by-key/same-key")
            assert readback.status_code == 200
            assert readback.json()["id"] == "plan-1"
        assert transport.committed_response_dropped is True
        assert post_count == 1
        assert len(committed) == 1

    asyncio.run(scenario())


def test_u04_budget_block_happens_before_canonical_holdout_path_lookup(monkeypatch):
    monkeypatch.setattr(u04, "_u04_authorized", lambda: False)
    monkeypatch.setattr(
        u04, "canonical_holdout3",
        lambda: (_ for _ in ()).throw(AssertionError("formal path touched before purpose gate")),
    )
    with pytest.raises(ValueError, match="u04_budget_purpose_gate_blocked"):
        u04._load_claimed_holdout("a" * 64, "b" * 64, "c" * 64)


def test_u04_claim_is_no_clobber_and_precedes_holdout_read(monkeypatch, tmp_path):
    monkeypatch.setattr(u04, "_u04_authorized", lambda: True)
    holdout = tmp_path / "holdout-v3.json"
    monkeypatch.setattr(u04, "canonical_holdout3", lambda: holdout)
    marker = u04.claim_holdout_once(candidate_sha256="a" * 64, bundle_sha256="b" * 64,
                                    holdout_sha256="c" * 64)
    assert marker.exists()
    assert not holdout.exists()
    with pytest.raises(FileExistsError):
        u04.claim_holdout_once(candidate_sha256="a" * 64, bundle_sha256="b" * 64,
                               holdout_sha256="c" * 64)


def test_u04_canonical_holdout_reader_requires_prior_claim(monkeypatch, tmp_path):
    monkeypatch.setattr(u04, "_u04_authorized", lambda: True)
    holdout = tmp_path / "holdout-v3.json"
    holdout.write_text("[]")
    holdout.chmod(0o600)
    monkeypatch.setattr(u04, "canonical_holdout3", lambda: holdout)
    with pytest.raises(ValueError, match="sealed_holdout_must_be_claimed"):
        u04._read_case_list(holdout)


def test_u03_completion_rejects_boolean_only_or_partial_scenario_coverage(tmp_path):
    with pytest.raises(ValueError, match="u03_scenario_set_incomplete"):
        u03._accepted_u03_result(
            root=tmp_path, evidence_root=tmp_path, head="a" * 40, base="b" * 40, gate_sha256="c" * 64,
            candidate_inputs_sha256="d" * 64, candidate_inputs={"path": "candidate.json", "sha256": "d" * 64},
            source_fingerprint={}, started_at="2026-10-06T00:00:00Z",
            completed_at="2026-10-06T00:00:01Z",
            scenarios={name: {"status": "PASS", "evidence": {"sha256": "e" * 64}}
                       for name in u03._U03_SCENARIOS[:-1]},
            receipts=[], coverage={}, exit_code=0, deadline_seconds=1800,
        )


def test_u03_scenario_evidence_is_private_and_hash_bound(tmp_path):
    import hashlib

    candidate_root = tmp_path / "candidate"
    candidate_root.mkdir()
    evidence_root = tmp_path / "evidence"
    evidence_root.mkdir(mode=0o700)
    evidence_dir = evidence_root / "run"
    evidence_dir.mkdir(mode=0o700)
    reference = u03._scenario_evidence(
        candidate_root, "java_same_key_same_payload", "a" * 40, "b" * 64,
        {"attempts": 2, "distinct_plan_ids": 1, "planId": "plan-id",
         "threadId": None, "runId": None, "businessKeySha256": "c" * 64,
         "receiptSha256": "d" * 64},
        started_at="2026-10-06T00:00:00Z", completed_at="2026-10-06T00:00:01Z",
        evidence_dir=evidence_dir,
    )
    path = evidence_root / reference["path"]
    assert path.stat().st_mode & 0o777 == 0o600
    assert hashlib.sha256(path.read_bytes()).hexdigest() == reference["sha256"]


def test_u03_java_idempotency_matrix_uses_mock_java_wire_contract(monkeypatch, tmp_path):
    from urllib.parse import unquote

    owner_id = "33333333-3333-4333-8333-333333333333"
    foreign_id = "44444444-4444-4444-8444-444444444444"
    source_id = "11111111-1111-4111-8111-111111111111"
    foreign_source_id = "22222222-2222-4222-8222-222222222222"
    backend = {}
    owners = {"owner-token": owner_id, "foreign-token": foreign_id}
    plan_ids = iter((
        "55555555-5555-4555-8555-555555555555",
        "66666666-6666-4666-8666-666666666666",
        "77777777-7777-4777-8777-777777777777",
    ))

    def envelope(data, *, status=200, code=0):
        return httpx.Response(status, json={"code": code, "message": "ok", "data": data, "traceId": "mock-trace"})

    def handler(request):
        token = request.headers.get("cookie", "").split("access_token=", 1)[-1].split(";", 1)[0]
        owner = owners.get(token)
        if request.url.path == "/auth/me":
            return envelope({"user": {"id": owner, "is_active": True, "is_banned": False}})
        if request.url.path.startswith("/submissions/"):
            requested = request.url.path.rsplit("/", 1)[-1]
            if requested != (source_id if owner == owner_id else foreign_source_id):
                return envelope(None, status=404, code=40400)
            return envelope({"id": requested})
        if request.method == "POST" and request.url.path == "/learning-plans":
            payload = json.loads(request.content)
            key = request.headers["Idempotency-Key"]
            record = backend.get(key)
            if record and record["payload"] != payload:
                return envelope(None, status=409, code=40900)
            if record is None:
                record = {"payload": payload, "id": next(plan_ids), "owner": owner, "rows": 1}
                backend[key] = record
            return envelope({"id": record["id"], **record["payload"]})
        if request.method == "GET" and "/learning-plans/by-key/" in request.url.path:
            key = unquote(request.url.path.rsplit("/", 1)[-1])
            record = backend.get(key)
            if record is None or record["owner"] != owner:
                return envelope(None, status=404, code=40400)
            return envelope({"id": record["id"], **record["payload"]})
        return envelope(None, status=404, code=40400)

    monkeypatch.setattr(u03.httpx, "AsyncHTTPTransport", lambda **_kwargs: httpx.MockTransport(handler))
    candidate_root = tmp_path / "candidate"
    candidate_root.mkdir()
    evidence_root = tmp_path / "evidence"
    evidence_root.mkdir(mode=0o700)
    evidence_dir = evidence_root / "run"
    evidence_dir.mkdir(mode=0o700)

    async def scenario():
        observations, raw_refs, _projections, foreign_ref = await u03._java_idempotency_matrix(
            app_base="http://127.0.0.1:8000", auth_base="http://127.0.0.1:8001",
            owner_access="owner-token", owner_csrf="owner-csrf",
            foreign_access="foreign-token", foreign_csrf="foreign-csrf",
            source_id=source_id, foreign_source_id=foreign_source_id,
            head="a" * 40, gate_sha="b" * 64, evidence_dir=evidence_dir,
        )
        assert observations["java_same_key_same_payload"]["matching_payload"] is True
        assert observations["java_same_key_payload_mismatch"]["changed_status"] == 409
        assert observations["java_same_key_payload_mismatch"]["changed_code"] == 40900
        assert set(raw_refs) == {
            "java_same_key_same_payload", "java_concurrent_same_key", "java_same_key_payload_mismatch",
        }
        assert foreign_ref["path"].endswith("java-foreign-read.json")
        assert len(backend) == 3

        scenarios = {}
        for name, observation in observations.items():
            ref = u03._scenario_evidence(
                candidate_root, name, "a" * 40, "b" * 64, observation,
                started_at="2026-10-06T00:00:00Z", completed_at="2026-10-06T00:00:01Z",
                evidence_dir=evidence_dir, raw_receipts=raw_refs.get(name),
                foreign_read_receipt=foreign_ref if name == "java_foreign_owner_read" else None,
            )
            u03._record_validated_scenario(
                scenarios, ref, scenario=name, evidence_root=evidence_root,
                head="a" * 40, gate_sha="b" * 64,
            )
        assert set(scenarios) == set(observations)
        assert all(row["status"] == "PASS" for row in scenarios.values())
        with pytest.raises(ValueError, match="u03_scenario_set_incomplete"):
            u03._accepted_u03_result(
                root=candidate_root, evidence_root=evidence_root, head="a" * 40, base="c" * 40,
                gate_sha256="b" * 64, candidate_inputs_sha256="d" * 64,
                candidate_inputs={"path": "candidate.json", "sha256": "d" * 64},
                source_fingerprint={}, started_at="2026-10-06T00:00:00Z",
                completed_at="2026-10-06T00:00:01Z", scenarios=scenarios,
                receipts=[], coverage={}, exit_code=0, deadline_seconds=1800,
            )

    asyncio.run(scenario())


def test_u03_result_artifact_must_share_evidence_root(tmp_path):
    evidence_root = tmp_path / "evidence"
    evidence_root.mkdir(mode=0o700)
    nested = evidence_root / "nested"
    nested.mkdir(mode=0o700)
    assert u03._evidence_output(evidence_root, evidence_root / "result.json") == evidence_root / "result.json"
    with pytest.raises(ValueError, match="u03_result_path_outside_evidence_root"):
        u03._evidence_output(evidence_root, nested / "result.json")

def test_u03_orchestrator_binds_first_scenario_timestamp(monkeypatch, tmp_path):
    candidate_root = tmp_path / "candidate"
    candidate_root.mkdir()
    evidence_root = tmp_path / "evidence"
    evidence_root.mkdir(mode=0o700)
    gate_path = tmp_path / "gate.json"
    original_read_json = u03._read_json

    monkeypatch.setattr(u03, "load_u02_gate", lambda *args, **kwargs: None)
    monkeypatch.setattr(
        u03, "_read_json",
        lambda path: ({}, b"synthetic gate") if Path(path) == gate_path else original_read_json(path),
    )
    monkeypatch.setattr(
        u03, "_candidate_inputs_ref",
        lambda *args, **kwargs: (
            {"source_fingerprint": {}, "configuration_fingerprint": {}},
            {"path": "candidate-inputs.json", "sha256": "d" * 64},
        ),
    )
    monkeypatch.setattr(u03, "POLICY", {"lanes": {"u03_analysis": {}, "u03_citation_judge": {}}})
    monkeypatch.setattr(u03, "_u03_runtime_binding", lambda _args: (("p", "i", "c"), tmp_path / "guard", "e" * 64))
    monkeypatch.setattr(u03, "_agent_base", lambda: "http://127.0.0.1:9001")
    monkeypatch.setattr(u03, "_loopback_url", lambda _url: True)
    for key, value in {
        "ULTICODE_APP_BASE": "http://127.0.0.1:8000",
        "ULTICODE_AUTH_BASE": "http://127.0.0.1:8001",
        "ULTICODE_U03_ACCESS_TOKEN": "owner-token",
        "ULTICODE_U03_CSRF_TOKEN": "owner-csrf",
        "ULTICODE_U03_SOURCE_SUBMISSION_ID": "11111111-1111-4111-8111-111111111111",
        "ULTICODE_U03_FOREIGN_ACCESS_TOKEN": "foreign-token",
        "ULTICODE_U03_FOREIGN_CSRF_TOKEN": "foreign-csrf",
        "ULTICODE_U03_FOREIGN_SOURCE_SUBMISSION_ID": "22222222-2222-4222-8222-222222222222",
    }.items():
        monkeypatch.setenv(key, value)

    async def matrix(**_kwargs):
        return {"java_same_key_same_payload": {}}, {}, [], {}

    monkeypatch.setattr(u03, "_java_idempotency_matrix", matrix)
    observed_starts = []

    def stop_after_record(scenarios, reference, **kwargs):
        evidence, _ = original_read_json(evidence_root / reference["path"])
        observed_starts.append(evidence["started_at"])
        raise ValueError("stop_after_first_record")

    monkeypatch.setattr(u03, "_record_validated_scenario", stop_after_record)
    args = u03.argparse.Namespace(
        expected_head="a" * 40, expected_base="b" * 40,
        candidate=str(candidate_root), u02_gate=str(gate_path),
        candidate_inputs=str(tmp_path / "candidate-inputs.json"),
        evidence_root=str(evidence_root),
    )
    result, exit_code = asyncio.run(u03._run_u03(args))
    assert exit_code == 1
    assert result["reason"] == "stop_after_first_record"
    assert len(observed_starts) == 1
    u03.datetime.fromisoformat(observed_starts[0])



def test_u04_environment_flag_cannot_authorize_paid_calls(monkeypatch):
    monkeypatch.setenv("ULTICODE_U04_ALLOW_MODEL", "1")
    assert u04._u04_authorized() is False


def test_u04_development_separates_structural_completion_from_retrieval_quality():
    result = u04._run_development(_AGENT.parent.parent)
    assert result["case_count"] == result["executed_case_count"] == 20
    assert result["retrieval_pass_count"] == result["development_pass_count"] == 12
    assert result["retrieval_required_coverage_count"] == 18
    assert result["structural_execution_status"] == "PASS"
    assert result["citation_integrity_status"] == "PASS"
    assert result["retrieval_quality_status"] == "FAIL"
    assert result["status"] == "PASS"
    assert u04._development_complete(result) is True


def test_u04_development_completion_requires_all_structural_evidence():
    complete = {
        "case_count": 20, "executed_case_count": 20,
        "structural_execution_status": "PASS", "citation_integrity_status": "PASS",
        "retrieval_quality_status": "FAIL",
    }
    assert u04._development_complete(complete) is True
    assert u04._development_complete({**complete, "executed_case_count": 19}) is False
    assert u04._development_complete({**complete, "citation_integrity_status": "FAIL"}) is False


def test_u04_synthetic_holdout_uses_readonly_graph_and_citation_judges(monkeypatch):
    from types import SimpleNamespace

    from agent_loop import ModelDecision, ToolCall
    from retrieval import SourceDocument, keyword_search

    document = SourceDocument(
        doc_id="synthetic-doc", version="v1", source_path="synthetic.md",
        access_scope="synthetic", sample_kind="synthetic",
        text="synthetic evidence supports a bounded claim", source_position="line 1",
    )
    hit = keyword_search("synthetic evidence", documents=(document,))[0].as_model_dict()

    def search_tool(_documents):
        async def search(_arguments):
            return {"hits": [hit]}
        return search

    monkeypatch.setattr("boundary_evaluation.search_evidence_tool", lambda docs: search_tool(docs))
    monkeypatch.setattr(
        "citation_integrity.check_citations",
        lambda citations, docs: [SimpleNamespace(verdict="verified") for _ in citations],
    )
    monkeypatch.setattr("boundary_evaluation.judge_citation", lambda *args, **kwargs: asyncio.sleep(0, result=(True, False)))

    citation = {
        "claim": "synthetic evidence supports a bounded claim",
        **{key: hit[key] for key in (
            "chunk_id", "doc_id", "version", "source_path", "source_position",
            "access_scope", "sample_kind", "source_trust", "text",
        )},
    }

    class ScriptedModel:
        def __init__(self, decisions):
            self.decisions = iter(decisions)

        async def decide(self, _messages):
            return next(self.decisions)

    answer_model = ScriptedModel((
        ModelDecision(tool_call=ToolCall("search_evidence", {"query": "synthetic evidence"})),
        ModelDecision(text=json.dumps({
            "text": "Evidence supports one bounded claim.", "citations": [citation],
        })),
    ))
    judge_model = ScriptedModel((
        ModelDecision(text=json.dumps({
            "citation_support": True, "answer_completed": True, "observed_behavior": "cite",
        })),
    ))
    case = KeywordCase(
        case_id="synthetic-u04", split="holdout3", query="synthetic evidence",
        required_evidence=("synthetic-doc",), answerable=True, expected_behavior="cite",
        allowed_behavior="cite retrieved evidence", forbidden_behavior="invent evidence",
    )
    rows = asyncio.run(u04._evaluate_holdout(
        (case,), model=answer_model, judge=judge_model, documents=(document,),
    ))
    assert rows[0]["status"] == "PASS"
    assert rows[0]["graph_rounds"] == 2
    assert rows[0]["citation_exists"] and rows[0]["citation_supports"]
    assert rows[0]["citation_derivable"] == "not_applicable_empty_submission_facts"




@pytest.mark.parametrize(("tool_name", "tool_arguments"), (
    ("get_my_submissions", {}),
    ("search_evidence", {"query": "nothing here", "userId": "forged"}),
))
def test_u04_holdout_trace_rejects_unknown_or_identity_forged_tool_calls(
    tool_name, tool_arguments,
):
    from agent_loop import ModelDecision, ToolCall

    class Scripted:
        def __init__(self, decisions):
            self.decisions = iter(decisions)
            self.calls = 0

        async def decide(self, _messages):
            self.calls += 1
            return next(self.decisions)

    model = Scripted((
        ModelDecision(tool_call=ToolCall(tool_name, tool_arguments)),
        ModelDecision(text=json.dumps({
            "text": "There is no evidence.", "citations": [],
        })),
    ))
    judge = Scripted(())
    case = KeywordCase(
        case_id="trace-negative", split="holdout3", query="nothing here",
        required_evidence=(), answerable=False, expected_behavior="no_evidence",
        allowed_behavior="refuse without evidence", forbidden_behavior="invent evidence",
    )
    rows = asyncio.run(u04._evaluate_holdout(
        (case,), model=model, judge=judge, documents=(),
    ))
    assert rows[0]["status"] == "FAIL"
    assert rows[0]["reason"] in {"unauthorized_tool_attempt", "tool_trace_violation"}
    assert judge.calls == 0


@pytest.mark.parametrize("inner", (
    '{"text":"no evidence","text":"overwritten","citations":[]}',
    '{"text":"no evidence","citations":[],"extra":true}',
    '{"text":"no evidence","citations":[null,null,null,null]}',
    '{"text":"no evidence","citations":[{"claim":"x","chunk_id":"x","doc_id":"x","version":"v1","source_path":"x","source_position":"x","access_scope":"x","sample_kind":"x","source_trust":"x","text":"x","extra":true}]}',
))
def test_u04_holdout_malformed_answer_fails_before_judging(inner):
    from agent_loop import ModelDecision, ToolCall

    class Scripted:
        def __init__(self, decisions):
            self.decisions = iter(decisions)
            self.calls = 0

        async def decide(self, _messages):
            self.calls += 1
            return next(self.decisions)

    model = Scripted((
        ModelDecision(tool_call=ToolCall("search_evidence", {"query": "nothing here"})),
        ModelDecision(text=inner),
    ))
    judge = Scripted(())
    case = KeywordCase(
        case_id="malformed-answer", split="holdout3", query="nothing here",
        required_evidence=(), answerable=False, expected_behavior="no_evidence",
        allowed_behavior="refuse without evidence", forbidden_behavior="invent evidence",
    )
    rows = asyncio.run(u04._evaluate_holdout(
        (case,), model=model, judge=judge, documents=(),
    ))
    assert rows[0]["status"] == "FAIL"
    assert rows[0]["reason"] == "answer_protocol_invalid"
    assert judge.calls == 0
def test_u04_clean_empty_search_is_distinct_from_no_tool_call():
    from agent_loop import ModelDecision, ToolCall

    class Scripted:
        def __init__(self, decisions):
            self.decisions = iter(decisions)

        async def decide(self, _messages):
            return next(self.decisions)

    answer_model = Scripted((
        ModelDecision(tool_call=ToolCall("search_evidence", {"query": "nothing here"})),
        ModelDecision(text=json.dumps({
            "text": "I found no evidence for that query.", "citations": [],
        })),
    ))
    judge_model = Scripted((ModelDecision(text=json.dumps({
        "citation_support": False, "answer_completed": True, "observed_behavior": "no_evidence",
    })),))
    case = KeywordCase(
        case_id="clean-empty-search", split="holdout3", query="nothing here",
        required_evidence=(), answerable=False, expected_behavior="no_evidence",
        allowed_behavior="state no evidence", forbidden_behavior="invent evidence",
    )
    rows = asyncio.run(u04._evaluate_holdout(
        (case,), model=answer_model, judge=judge_model, documents=(),
    ))
    assert rows[0]["status"] == "PASS"
    assert rows[0]["retrieved_doc_ids"] == []
    assert rows[0]["citation_count"] == 0


def test_u04_reliability_offline_probes_cover_tool_boundary_injection_and_graph_bounds():
    r02, r03, r06 = asyncio.run(_u04_offline_reliability_probes())
    assert r02["rejected"] == r02["invalid_calls"] and r02["downstream_calls"] == 0
    assert r03["predicate"] == "expected_behavior_met" and r03["injection_delivered"]
    assert r06["rounds_bounded"] and r06["timeout_bounded"]


def test_u04_holdout_overlap_uses_query_and_expected_rubric_not_only_case_id():
    prior = KeywordCase(
        case_id="prior", split="development", query="Find  MY evidence",
        required_evidence=("doc-b", "doc-a"), answerable=True, expected_behavior="cite",
        allowed_behavior="cite the retrieved evidence", forbidden_behavior="invent claims",
    )
    same_semantics_new_id = KeywordCase(
        case_id="new-id", split="holdout3", query=" find my   evidence ",
        required_evidence=("doc-a", "doc-b"), answerable=True, expected_behavior="cite",
        allowed_behavior="CITE the retrieved evidence", forbidden_behavior="invent claims",
    )
    distinct = KeywordCase(
        case_id="distinct", split="holdout3", query="find different evidence",
        required_evidence=("doc-a", "doc-b"), answerable=True, expected_behavior="cite",
        allowed_behavior="cite the retrieved evidence", forbidden_behavior="invent claims",
    )
    assert u04._case_semantics(same_semantics_new_id) == u04._case_semantics(prior)
    assert u04._case_semantics(distinct) != u04._case_semantics(prior)

def test_u04_demo_artifacts_hash_body_text_before_persisting():
    import hashlib

    content, title = "private draft body", "private draft title"
    safe = u04._redact_demo_artifact({
        "threadId": "thread-1", "draft": {"title": title, "content": content},
        "sourceSubmissionId": "submission-1", "question": "private question",
        "userId": "forged-private-identity",
    })
    encoded = json.dumps(safe)
    assert (
        title not in encoded and content not in encoded and "private question" not in encoded
        and "forged-private-identity" not in encoded
    )
    assert safe["threadId"] == "thread-1"
    assert safe["draft"]["title"] == {
        "sha256": hashlib.sha256(title.encode()).hexdigest(), "codepoints": len(title),
    }
    assert safe["draft"]["content"] == {
        "sha256": hashlib.sha256(content.encode()).hexdigest(), "codepoints": len(content),
    }



def test_u04_refusal_allows_no_tool_call_and_no_citation():
    from agent_loop import ModelDecision

    class Scripted:
        def __init__(self, result):
            self.result = result
            self.calls = 0

        async def decide(self, _messages):
            self.calls += 1
            return self.result

    answer_model = Scripted(ModelDecision(text=json.dumps({
        "text": "I cannot provide source code that is unavailable here.",
        "citations": [],
    })))
    judge_model = Scripted(ModelDecision(text=json.dumps({
        "citation_support": False, "answer_completed": True, "observed_behavior": "refuse",
    })))
    case = KeywordCase(
        case_id="legal-no-tool-refusal", split="holdout3", query="private source request",
        required_evidence=("synthetic-doc",), answerable=False, expected_behavior="refuse",
        allowed_behavior="refuse unavailable source request", forbidden_behavior="invent source",
    )
    rows = asyncio.run(u04._evaluate_holdout(
        (case,), model=answer_model, judge=judge_model, documents=(),
    ))
    assert rows[0]["status"] == "PASS"
    assert rows[0]["graph_rounds"] == 1
    assert rows[0]["citation_count"] == 0
async def _u04_offline_reliability_probes():
    return await asyncio.gather(u04._probe_r02(), u04._probe_r03(), u04._probe_r06())


def test_u04_synthetic_human_demo_runs_same_flow_without_real_acceptance(monkeypatch):
    monkeypatch.setenv("ULTICODE_U04_SOURCE_SUBMISSION_ID", "11111111-1111-4111-8111-111111111111")
    calls = []
    reads = 0
    restarts = []
    plan_id = "22222222-2222-4222-8222-222222222222"

    async def request(method, path, body, *, timeout):
        nonlocal reads
        assert timeout > 0
        calls.append((method, path, body))
        if method == "POST" and path == "/agent/threads":
            data = {"threadId": "33333333-3333-4333-8333-333333333333"}
        elif method == "GET" and path.endswith("/33333333-3333-4333-8333-333333333333"):
            reads += 1
            if reads == 1:
                data = {"status": "awaiting_confirmation", "runId": "run-synthetic",
                        "draft": {"draftVersion": 1, "title": "old", "content": "old"},
                        "paramsDigest": "digest-v1"}
            elif reads == 2:
                data = {"status": "awaiting_confirmation", "runId": "run-synthetic",
                        "draft": {"draftVersion": 2, "title": "edited", "content": "edited body"},
                        "paramsDigest": "digest-v2"}
            else:
                data = {"status": "saved", "receipt": {"planId": plan_id}}
        elif method == "POST" and path.endswith("/confirm"):
            data = {"confirmation": {"id": "confirmation-synthetic"}}
        elif method == "POST" and path.endswith("/save"):
            data = {"status": "saved", "receipt": {"planId": plan_id}}
        elif method == "POST" and path.endswith("/recover"):
            assert body == {"retry": False}
            data = {"status": "saved", "receipt": {"planId": plan_id}}
        elif method == "JAVA_READBACK":
            data = {"id": plan_id, **body}
        else:
            data = {}
        return 200, {"code": 0, "data": data}

    async def restart():
        restarts.append(True)

    async def explicit_synthetic_human_input(_draft, _deadline):
        return {"reviewed": True, "edit": True, "title": "edited",
                "content": "edited body", "confirmed": True}

    async def scenario():
        result = await u04._human_demo_flow(
            request, restart, explicit_synthetic_human_input,
            deadline=asyncio.get_running_loop().time() + 60, synthetic=True,
        )
        assert result["synthetic_simulation"] is True
        assert result["human_demo_completed"] is False
        assert result["counters"] == {
            "agent_http_requests": 9, "human_steps": 1, "human_edits": 1,
            "human_confirmations": 1, "save_route_calls": 1, "recover_route_calls": 1,
            "java_save_requests": 1, "java_readbacks": 1, "python_restarts": 1,
        }
        assert len(restarts) == 1
        confirm = next(body for method, path, body in calls if path.endswith("/confirm"))
        assert confirm == {"draftVersion": 2, "paramsDigest": "digest-v2", "confirm": True}

    asyncio.run(scenario())


def test_u04_synthetic_human_refusal_never_confirms_or_saves(monkeypatch):
    monkeypatch.setenv("ULTICODE_U04_SOURCE_SUBMISSION_ID", "11111111-1111-4111-8111-111111111111")
    calls = []

    async def request(method, path, body, *, timeout):
        calls.append((method, path))
        if method == "POST" and path == "/agent/threads":
            data = {"threadId": "33333333-3333-4333-8333-333333333333"}
        elif method == "GET":
            data = {"status": "awaiting_confirmation", "runId": "run-synthetic",
                    "draft": {"draftVersion": 1, "title": "draft", "content": "draft"},
                    "paramsDigest": "digest"}
        else:
            data = {}
        return 200, {"code": 0, "data": data}

    async def no_restart():
        raise AssertionError("refusal must not restart")

    async def refuse(_draft, _deadline):
        return {"reviewed": True, "edit": False, "confirmed": False}

    async def scenario():
        result = await u04._human_demo_flow(
            request, no_restart, refuse,
            deadline=asyncio.get_running_loop().time() + 60, synthetic=True,
        )
        assert result["status"] == "FAIL"
        assert result["human_demo_completed"] is False
        assert not any(path.endswith(("/confirm", "/save")) for _, path in calls)

    asyncio.run(scenario())


def test_u04_uncompleted_human_demo_blocks_holdout_claim(monkeypatch, tmp_path):
    class Guard:
        closed = False

        def close(self):
            self.closed = True

    guard = Guard()

    async def refused(_preflight, _run_dir):
        return {"status": "FAIL", "reason": "human_did_not_confirm",
                "human_demo_completed": False}

    def claim(*_args, **_kwargs):
        raise AssertionError("unseen set must not be read before human demo succeeds")

    monkeypatch.setattr(u04, "_run_human_demo", refused)
    monkeypatch.setattr(u04, "_load_claimed_holdout", claim)
    preflight = {
        "guard": guard, "budget": None, "lane": {}, "purpose": "existing-purpose",
        "evidence_root": tmp_path, "u03": {"scenarios": [{"scenario": f"R{i:02}"} for i in range(1, 11)]},
        "candidate_sha256": "a" * 64, "bundle_sha256": "b" * 64,
        "candidate": {"holdout3_sha256": "c" * 64},
        "development": {
            "case_count": 20, "executed_case_count": 20,
            "development_pass_count": 12, "retrieval_pass_count": 12,
            "structural_execution_status": "PASS", "citation_integrity_status": "PASS",
            "retrieval_quality_status": "FAIL", "status": "PASS",
        },
    }
    result = asyncio.run(u04._execute(None, preflight))
    assert result["formal_holdout_consumed"] is False
    assert result["holdout3_cases"] == []
    assert result["human_demo"]["status"] == "FAIL"
    assert guard.closed


def test_u04_preflight_ignores_u03_human_demo_flags(monkeypatch, tmp_path):
    import hashlib
    from types import SimpleNamespace

    root = tmp_path / "candidate"
    data_path = root / "services/agent/data/keyword_cases.json"
    data_path.parent.mkdir(parents=True)
    data_path.write_text("[]")
    evidence_root = tmp_path / "evidence"
    evidence_root.mkdir(mode=0o700)
    head, base = "a" * 40, "b" * 40
    candidate = {
        "schema": "ulticode-u04-candidate-inputs-v1", "candidate_head": head,
        "candidate_base": base, "source_fingerprint": {"source": "pinned"},
        "configuration_fingerprint": {"config": "pinned"},
        "development_cases_sha256": hashlib.sha256(b"[]").hexdigest(),
        "holdout3_sha256": "c" * 64,
    }
    u03_result = {
        "receipts": [], "scenarios": [],
        "coverage": {"human_demo_completed": False, "human_demo_seconds": 999},
    }
    gate_sha = hashlib.sha256(b"gate").hexdigest()
    u03_sha = hashlib.sha256(b"u03").hexdigest()
    bundle = {
        "schema": "ulticode-u04-acceptance-bundle-v1",
        "candidate_inputs_sha256": hashlib.sha256(b"candidate").hexdigest(),
        "candidate_head": head, "candidate_base": base,
        "u02_gate_sha256": gate_sha, "u03_artifact_sha256": u03_sha,
        "u02_candidate_head": head, "u02_candidate_base": base,
        "u03_receipts": [], "u03_scenarios": [],
    }
    payloads = {
        "gate.json": ({}, b"gate"),
        "candidate.json": (candidate, b"candidate"),
        "bundle.json": (bundle, b"bundle"),
        "u03.json": (u03_result, b"u03"),
    }
    monkeypatch.setattr(u04, "_private_root", lambda path: Path(path))
    monkeypatch.setattr(
        u04, "_within", lambda _root, path: evidence_root / Path(path).name,
    )
    monkeypatch.setattr(u04.delivery, "_read_json", lambda path: payloads[Path(path).name])
    monkeypatch.setattr(
        u04.delivery, "_candidate_fingerprint", lambda *_args: candidate["source_fingerprint"],
    )
    monkeypatch.setattr(
        u04.delivery, "_candidate_configuration_fingerprint",
        lambda *_args: candidate["configuration_fingerprint"],
    )
    monkeypatch.setattr(u04.delivery, "validate_u03_result", lambda *args, **kwargs: args[0])
    monkeypatch.setattr(u04, "load_u02_gate", lambda *args, **kwargs: {
        "candidate_head": head, "candidate_base": base,
        "prior_five_manifest": {"path": "prior-five.json", "sha256": "d" * 64},
    })
    monkeypatch.setattr(u04, "_authorize_before_holdout", lambda: None)
    monkeypatch.setattr(u04, "_run_development", lambda _root: {"case_count": 20})
    monkeypatch.setattr(
        u04, "_open_paid_runtime",
        lambda _args: ("identity", "model", "budget", "guard", {}, 0, "purpose"),
    )
    args = SimpleNamespace(
        candidate=str(root), evidence_root=str(evidence_root), u02_gate=str(tmp_path / "gate.json"),
        candidate_inputs="candidate.json", acceptance_bundle="bundle.json",
        u03_result="u03.json", expected_head=head, expected_base=base,
    )
    preflight = u04._run_preflight(args)
    assert preflight["u03"]["coverage"]["human_demo_completed"] is False
    assert preflight["candidate"]["candidate_head"] == head


def test_u03_private_receipt_producer_matches_frozen_gate_projection(tmp_path):
    import hashlib
    from agent_service.gate import _check_u03_raw_receipt

    evidence_root = tmp_path / "private"
    evidence_root.mkdir(mode=0o700)
    run = evidence_root / "run"
    run.mkdir(mode=0o700)
    source_id, key, owner_id = (
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222",
        "33333333-3333-4333-8333-333333333333",
    )
    vo = {
        "id": "44444444-4444-4444-8444-444444444444",
        "sourceSubmissionId": source_id, "draftVersion": 1,
        "title": "private synthetic title", "content": "private synthetic content",
    }
    payload = {key: vo[key] for key in ("sourceSubmissionId", "draftVersion", "title", "content")}
    post = {
        "request_id": "55555555-5555-4555-8555-555555555555",
        "business_key": key, "request_payload": payload,
        "request_wire_sha256": hashlib.sha256(b"synthetic request").hexdigest(),
        "http_status": 200, "http_code": 0, "response_vo": vo,
        "response_payload_sha256": hashlib.sha256(b"synthetic response").hexdigest(),
    }
    principal = {
        "user_id": owner_id, "response_status": 200, "is_active": True,
        "is_banned": False, "trace_id": "synthetic-trace",
    }
    refs = u03._write_java_receipts(
        scenario="java_same_key_same_payload", posts=[post], owner_id=owner_id,
        source_id=source_id, thread_id=None, run_id=None, head="a" * 40,
        gate_sha="b" * 64, evidence_dir=run, readbacks={key: (200, vo)},
        principal_evidence=principal,
        readback_fingerprints={key: hashlib.sha256(b"synthetic readback").hexdigest()},
    )
    projection = _check_u03_raw_receipt(
        refs[0], root=evidence_root, scenario="java_same_key_same_payload",
        head="a" * 40, gate_sha="b" * 64,
    )
    raw = (evidence_root / refs[0]["path"]).read_bytes()
    assert projection["httpStatus"] == "200"
    assert projection["ownerSha256"] == hashlib.sha256(owner_id.encode()).hexdigest()
    assert b"private synthetic title" not in raw and b"private synthetic content" not in raw
    assert owner_id.encode() not in raw and key.encode() not in raw


def test_u03_scenario_producer_emits_exact_frozen_gate_observation(tmp_path):
    candidate_root = tmp_path / "candidate"
    evidence_root = tmp_path / "private"
    candidate_root.mkdir()
    evidence_root.mkdir(mode=0o700)
    run = evidence_root / "run"
    run.mkdir(mode=0o700)
    observations = {
        "missing_status": 400, "missing_code": 40000, "stale_status": 409, "stale_code": 40900,
        "cancel_status": 200, "cancelled_confirm_status": 409,
        "expired_confirmation_tested": True, "business_posts": 0,
        "planId": None, "threadId": None, "runId": None,
        "businessKeySha256": None, "receiptSha256": None,
    }
    reference = u03._scenario_evidence(
        candidate_root, "confirmation_guards", "a" * 40, "b" * 64, observations,
        started_at="2026-10-06T00:00:00Z", completed_at="2026-10-06T00:00:01Z",
        evidence_dir=run,
    )
    assert u03._validated_scenario_status(
        reference, scenario="confirmation_guards", evidence_root=evidence_root,
        head="a" * 40, gate_sha="b" * 64,
    ) == "PASS"


def test_u03_tty_input_uses_bounded_select(monkeypatch):
    import select

    observed = []
    monkeypatch.setattr(u03.sys, "stdin", type("TTY", (), {"fileno": lambda self: 0})())
    monkeypatch.setattr(select, "select", lambda readers, writers, errors, timeout: (
        observed.append(timeout) or ([], [], [])
    ))
    with pytest.raises(TimeoutError, match="human_confirmation_timeout"):
        u03._read_tty("prompt", timeout=2.5)
    assert observed == [pytest.approx(2.5, abs=0.01)]


def test_u03_pass_cli_reports_pass_without_reason_key(monkeypatch, tmp_path, capsys):
    evidence_root = tmp_path / "evidence"
    evidence_root.mkdir(mode=0o700)
    evidence_root.chmod(0o700)
    monkeypatch.setenv("ULTICODE_U03_E2E", "1")
    artifact = {"schema": "ulticode-u03-workflow-result-v1", "status": "PASS", "exit_code": 0}
    monkeypatch.setattr(u03, "_private_no_clobber", lambda *_args: "sha")
    monkeypatch.setattr(u03.asyncio, "run", lambda coroutine: (
        coroutine.close(), (artifact, 0)
    )[1])
    assert u03.main([
        "--u02-gate", str(tmp_path / "gate.json"), "--candidate", str(tmp_path),
        "--candidate-inputs", str(tmp_path / "candidate.json"),
        "--evidence-root", str(evidence_root), "--result", str(evidence_root / "result.json"),
        "--expected-head", "a" * 40, "--expected-base", "b" * 40,
    ]) == 0
    assert capsys.readouterr().out.strip() == "PASS"


def test_u03_stop_agent_unregisters_exited_child():
    class ExitedProcess:
        exitcode = 0

        def is_alive(self):
            return False

        def join(self, _timeout):
            self.joined = True

    process = ExitedProcess()
    u03._ACTIVE_AGENT_PROCESSES.add(process)
    u03._stop_active_agents()
    assert process.joined
    assert process not in u03._ACTIVE_AGENT_PROCESSES
