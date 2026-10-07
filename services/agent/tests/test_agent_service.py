import asyncio
import json
import traceback
import uuid

import httpx
import pytest

from deepseek_model import DeepseekModel
from agent_loop import ModelDecision
from agent_service.app import create_app, parse_model_answer
from agent_service.gate import GateError
from retrieval import SourceDocument
from citation_integrity import check_citations
from ulticode_client import UlticodeServiceError

HEADERS = {"cookie": "access_token=access; csrf_token=csrf", "x-csrf-token": "csrf"}


def _dispatch_on_stack() -> bool:
    """True when the graph's dispatch node is an ancestor of the current frame."""
    return any(
        frame.name == "dispatch" and frame.filename.endswith("agent_service/graph.py")
        for frame in traceback.extract_stack()
    )


def _write_client(posts: dict[str, int], saved: dict[str, object], post_flags: list[bool]):
    """Java-voiced save/readback client: one shared receipt across per-request instances."""

    class WriteClient(SessionClient):
        async def save_learning_plan(self, **payload):
            posts["count"] += 1
            post_flags.append(_dispatch_on_stack())
            saved.clear()
            saved.update({
                "id": str(uuid.uuid4()),
                "sourceSubmissionId": payload["source_submission_id"],
                "draftVersion": payload["draft_version"],
                "title": payload["title"],
                "content": payload["content"],
            })
            return dict(saved)

        async def get_learning_plan_by_key(self, key):
            return dict(saved) if saved else None

    return WriteClient


class ScriptedModel:
    def __init__(self):
        self.calls = 0

    async def decide(self, messages):
        self.calls += 1
        assert "You are a read-only learning assistant" in messages[0]["content"]
        return ModelDecision(text=json.dumps({"text": "建议核对状态", "citations": []}, ensure_ascii=False))


class AnswerModel:
    def __init__(self, text, citations=()):
        self.text, self.citations = text, list(citations)
        self.calls = 0
        self.usage = []

    async def decide(self, _messages):
        self.calls += 1
        return ModelDecision(text=json.dumps({"text": self.text, "citations": self.citations}, ensure_ascii=False))



class SessionClient:
    def __init__(self, **_):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_):
        pass

    async def principal(self):
        return "owner-1"

    async def get_my_submission(self, submission_id):
        return {"id": submission_id, "status": "Accepted"}


class MissingBudgetClient(SessionClient):
    def __init__(self, posts):
        self.posts = posts

    async def save_learning_plan(self, **_):
        self.posts["count"] += 1
        raise UlticodeServiceError(50000, 503)

    async def get_learning_plan_by_key(self, _key):
        raise UlticodeServiceError(40400, 503)


def test_create_app_rejects_another_execution_candidate_before_state_creation(tmp_path):
    model_calls = []
    state_path = tmp_path / "private-state" / "state.sqlite3"
    with pytest.raises(GateError, match="execution_candidate_mismatch"):
        create_app(state_path=state_path, candidate_root=tmp_path,
                   offline_model_factory=lambda: model_calls.append("called"))
    assert not state_path.parent.exists()
    assert model_calls == []


def test_analyze_rejects_unverified_empty_citation_claims_without_mutating_draft(tmp_path):
    async def scenario():
        answers = (
            "我已尝试检索源码并已读取所有用户提交。",
            "根因是运行时错误，来源 https://attacker.invalid/fake。",
            "来源字段 source_path=private.py 证明了数组越界。",
            "提交状态为 Rejected。",
        )
        for index, answer in enumerate(answers):
            app = create_app(
                state_path=tmp_path / f"empty-citation-{index}.sqlite3",
                client_factory=SessionClient,
                offline_model_factory=lambda answer=answer: (AnswerModel(answer), {}),
            )
            async with app.router.lifespan_context(app):
                async with httpx.AsyncClient(
                    transport=httpx.ASGITransport(app=app), base_url="http://test"
                ) as client:
                    created = await client.post("/agent/threads", headers=HEADERS,
                        json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                    thread_id = created.json()["data"]["threadId"]
                    failed = await client.post(f"/agent/threads/{thread_id}/analyze",
                                               headers=HEADERS, json={})
                    current = await client.get(f"/agent/threads/{thread_id}", headers=HEADERS)
                    assert failed.status_code == 502
                    assert current.json()["data"]["status"] == "awaiting_confirmation"
                    assert current.json()["data"]["draft"]["draftVersion"] == 1

        safe = (
            "当前提交状态为 Accepted。",
            "请提供具体问题，我才能进一步协助。",
            "仅凭当前提交状态无法确认源码根因。",
            "无法获取该提交源码。",
            "The source code was not provided; I cannot quote it.",
        )
        for index, answer in enumerate(safe):
            app = create_app(
                state_path=tmp_path / f"safe-empty-citation-{index}.sqlite3",
                client_factory=SessionClient,
                offline_model_factory=lambda answer=answer: (AnswerModel(answer), {}),
            )
            async with app.router.lifespan_context(app):
                async with httpx.AsyncClient(
                    transport=httpx.ASGITransport(app=app), base_url="http://test"
                ) as client:
                    created = await client.post("/agent/threads", headers=HEADERS,
                        json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                    thread_id = created.json()["data"]["threadId"]
                    analyzed = await client.post(f"/agent/threads/{thread_id}/analyze",
                                                 headers=HEADERS, json={})
                    assert analyzed.status_code == 200
                    assert analyzed.json()["data"]["draft"]["draftVersion"] == 2

    asyncio.run(scenario())


def test_analyze_rejects_unsupported_text_around_verified_citation_and_keeps_draft(tmp_path, monkeypatch):
    async def scenario():
        answer = "该提交状态为 Accepted；源码根因是数组越界。"
        doc = SourceDocument("doc", "v1", "sample.md", "sample", "synthetic",
                             "提交状态为 Accepted。", "line 1")
        citation = {
            "claim": "该提交状态为 Accepted。",
            "chunk_id": "doc:v1:1", "doc_id": "doc", "version": "v1",
            "source_path": "sample.md", "source_position": "line 1",
            "access_scope": "sample", "sample_kind": "synthetic",
            "source_trust": "untrusted-data", "text": doc.text,
        }
        retrieved = [{key: value for key, value in citation.items() if key != "claim"}]
        assert check_citations([citation], (doc,))[0].verdict == "verified"

        judged_claims = []
        async def judge(_model, *, claim, quote, facts):
            _model.usage.append({"prompt_tokens": 1, "completion_tokens": 1})
            judged_claims.append(claim)
            return True, True

        monkeypatch.setattr("boundary_evaluation.judge_citation", judge)
        rejected_answers = (
            ("如何复盘？", answer, [citation]),
            ("请获取该提交源码", "无法获取该提交源码。当前提交状态为 Accepted。", [citation]),
            ("Please quote the submission source code", "The source is not provided; I cannot quote it.", [citation]),
            ("Please quote the submission source code", "I do not have access to the submission source code. The submission status is Accepted.", [citation]),
            ("请引用这次提交的源代码", "我不具备访问源代码的权限。当前提交状态为 Accepted。", [citation]),
            ("如何复盘？", "I do not have access to the submission source code. The submission status is Accepted.", [citation]),
            ("如何复盘？", "我不具备访问源代码的权限。当前提交状态为 Accepted。", [citation]),
            ("请获取该提交源码", "无法提供源码，但可以参考 sample.md。", [citation]),
            ("请获取该提交源码", "无法提供源码，但可以参考 sample.md。", []),
            ("请获取该提交源码", "无法提供源码，参考 https://example.invalid/source。", []),
        )
        for index, (question, text, citations) in enumerate(rejected_answers):
            model = AnswerModel(text, citations)
            app = create_app(
                state_path=tmp_path / f"cited-answer-{index}.sqlite3",
                client_factory=SessionClient,
                offline_model_factory=lambda: (model, {}, (doc,), retrieved),
            )
            async with app.router.lifespan_context(app):
                async with httpx.AsyncClient(
                    transport=httpx.ASGITransport(app=app), base_url="http://test"
                ) as client:
                    created = await client.post("/agent/threads", headers=HEADERS,
                        json={"sourceSubmissionId": str(uuid.uuid4()), "question": question})
                    thread_id = created.json()["data"]["threadId"]
                    failed = await client.post(f"/agent/threads/{thread_id}/analyze",
                                               headers=HEADERS, json={})
                    current = await client.get(f"/agent/threads/{thread_id}", headers=HEADERS)
                    assert failed.status_code == 502
                    assert current.json()["data"]["draft"]["draftVersion"] == 1
                    assert current.json()["data"]["analysis"] == {}
                    assert model.usage == []

        safe_answer = "该提交状态为 Accepted；建议结合后续记录复盘。"
        safe_questions = ("如何复盘？", "Explain the source code concept using retrieved documentation")
        for index, question in enumerate(safe_questions):
            safe_model = AnswerModel(safe_answer, [citation])
            safe_app = create_app(
                state_path=tmp_path / f"supported-citation-{index}.sqlite3",
                client_factory=SessionClient,
                offline_model_factory=lambda: (safe_model, {}, (doc,), retrieved),
            )
            async with safe_app.router.lifespan_context(safe_app):
                async with httpx.AsyncClient(
                    transport=httpx.ASGITransport(app=safe_app), base_url="http://test"
                ) as client:
                    created = await client.post("/agent/threads", headers=HEADERS,
                        json={"sourceSubmissionId": str(uuid.uuid4()), "question": question})
                    thread_id = created.json()["data"]["threadId"]
                    analyzed = await client.post(f"/agent/threads/{thread_id}/analyze",
                                                 headers=HEADERS, json={})
                    assert analyzed.status_code == 200
                    assert analyzed.json()["data"]["draft"]["citations"] == [citation]
                    assert judged_claims == [citation["claim"]] * (index + 1)

    asyncio.run(scenario())


def test_offline_analyze_resumes_same_graph_and_cas_persists_answer(tmp_path):
    async def scenario():
        model = ScriptedModel()
        app = create_app(
            state_path=tmp_path / "workflow.sqlite3",
            client_factory=SessionClient,
            offline_model_factory=lambda: (model, {}),
        )
        headers = {"cookie": "access_token=access; csrf_token=csrf", "x-csrf-token": "csrf"}
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                source_id = str(uuid.uuid4())
                created = await client.post("/agent/threads", headers=headers,
                    json={"sourceSubmissionId": source_id, "question": "如何复盘？"})
                assert created.status_code == 200
                before = created.json()["data"]
                thread_id = before["threadId"]
                analyzed = await client.post(f"/agent/threads/{thread_id}/analyze", headers=headers, json={})
                assert analyzed.status_code == 200
                result = analyzed.json()["data"]
                assert result["status"] == "awaiting_confirmation"
                assert result["draft"]["draftVersion"] == 2
                assert "模型建议（待验证）：建议核对状态" in result["draft"]["content"]
                assert result["analysis"]["answer"] == "建议核对状态"
                assert model.calls == 1

    asyncio.run(scenario())

def test_deepseek_provider_answer_roundtrips_through_same_graph_and_service_parser(tmp_path):
    async def scenario():
        answer = {"text": "当前提交状态为 Accepted。", "citations": []}
        provider_content = json.dumps({"answer": json.dumps(answer, ensure_ascii=False)}, ensure_ascii=False)

        async def provider(request):
            payload = json.loads(request.content)
            assert payload["messages"]
            return httpx.Response(200, json={
                "choices": [{"message": {"content": provider_content}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 12, "completion_tokens": 8},
                "model": "deepseek-flash",
            })

        model = DeepseekModel("offline-test-key", tool_specs={}, transport=httpx.MockTransport(provider))
        app = create_app(
            state_path=tmp_path / "deepseek-inner-answer.sqlite3",
            client_factory=SessionClient,
            offline_model_factory=lambda: (model, {}),
        )
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                thread_id = created.json()["data"]["threadId"]
                analyzed = await client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={})
                assert analyzed.status_code == 200
                result = analyzed.json()["data"]
                assert result["status"] == "awaiting_confirmation"
                assert result["analysis"]["answer"] == answer["text"]
                assert model.calls_made == 1

    asyncio.run(scenario())


def test_model_answer_rejects_lone_surrogate():
    raw = json.dumps({"text": "\ud800", "citations": []})
    try:
        parse_model_answer(raw)
    except ValueError:
        pass
    else:
        raise AssertionError("unpaired surrogate was accepted")


def test_model_answer_parser_accepts_actual_inner_contract_and_rejects_malformed_envelopes():
    valid = json.dumps({"text": "ok", "citations": []})
    assert parse_model_answer(valid) == {"text": "ok", "citations": []}
    for raw in (
        json.dumps({"answer": valid}),
        '{"text":"x","text":"y","citations":[]}',
        '{"text":"x","citations":[],"extra":true}',
        '{"text":"x","citations":[],"number":NaN}',
        '{"text":"x","citations":[{"claim":"a","claim":"b"}]}',
        json.dumps({"text": "x" * (64 * 1024), "citations": []}),
    ):
        try:
            parse_model_answer(raw)
        except ValueError:
            pass
        else:
            raise AssertionError(f"invalid model protocol was accepted: {raw[:80]}")


def test_model_answer_rejects_nonfinite_constant():
    for raw in (
        '{"text":"x","citations":[],"value":NaN}',
        '{"text":"x","citations":[],"value":Infinity}',
        '{"text":"x","citations":[],"value":-Infinity}',
    ):
        try:
            parse_model_answer(raw)
        except ValueError:
            pass
        else:
            raise AssertionError("non-finite JSON constant was accepted")


def test_missing_budget_blocks_analysis_before_model_invocation(tmp_path):
    async def scenario():
        model = ScriptedModel()
        app = create_app(state_path=tmp_path / "blocked.sqlite3", client_factory=SessionClient,
                         model_factory=lambda: (model, {}))
        headers = {"cookie": "access_token=access; csrf_token=csrf", "x-csrf-token": "csrf"}
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=headers,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                response = await client.post(f"/agent/threads/{created.json()['data']['threadId']}/analyze",
                                             headers=headers, json={})
                assert response.status_code == 503
                assert response.json()["data"]["reason"] == "model_budget_blocked"
                assert model.calls == 0

    asyncio.run(scenario())


def test_confirm_save_recover_dispatch_through_checkpointed_graph(tmp_path, monkeypatch):
    monkeypatch.setattr("agent_service.app.load_u02_gate", lambda *args, **kwargs: {"test_only": True})

    async def scenario():
        posts, saved, post_flags = {"count": 0}, {}, []
        app = create_app(state_path=tmp_path / "write.sqlite3",
                         client_factory=_write_client(posts, saved, post_flags),
                         u02_gate_path=tmp_path / "synthetic-test-only-gate.json", expected_head="0" * 40)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                assert created.status_code == 200
                state = created.json()["data"]
                thread_id = state["threadId"]
                confirmed = await client.post(f"/agent/threads/{thread_id}/confirm", headers=HEADERS,
                    json={"draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True})
                assert confirmed.status_code == 200
                saved_response = await client.post(f"/agent/threads/{thread_id}/save", headers=HEADERS,
                    json={"confirmationId": confirmed.json()["data"]["confirmation"]["id"]})
                assert saved_response.status_code == 200
                assert saved_response.json()["data"]["status"] == "saved"
                recovered = await client.post(f"/agent/threads/{thread_id}/recover", headers=HEADERS,
                    json={"retry": False})
                assert recovered.status_code == 200
                assert recovered.json()["data"]["receipt"]["planId"] == saved["id"]
                assert posts["count"] == 1
                # the business POST happened while the graph dispatch node was executing
                assert post_flags == [True]

    asyncio.run(scenario())


def test_state_mutations_run_inside_dispatch_node(tmp_path, monkeypatch):
    monkeypatch.setattr("agent_service.app.load_u02_gate", lambda *args, **kwargs: {"test_only": True})

    async def scenario():
        posts, saved, post_flags = {"count": 0}, {}, []
        model = ScriptedModel()
        app = create_app(state_path=tmp_path / "dispatch.sqlite3",
                         client_factory=_write_client(posts, saved, post_flags),
                         offline_model_factory=lambda: (model, {}),
                         u02_gate_path=tmp_path / "gate.json", expected_head="0" * 40)
        store = app.state.store
        original_transition = store.transition
        seen = []

        def recording_transition(*args, **kwargs):
            seen.append((str(kwargs["kind"]), _dispatch_on_stack()))
            return original_transition(*args, **kwargs)

        monkeypatch.setattr(store, "transition", recording_transition)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                source_id = str(uuid.uuid4())
                created = (await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": source_id, "question": "如何复盘？"})).json()["data"]
                thread_id = created["threadId"]
                analyzed = (await client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={})).json()["data"]
                assert analyzed["draft"]["draftVersion"] == 2
                edited = (await client.put(f"/agent/threads/{thread_id}/draft", headers=HEADERS,
                    json={"draftVersion": 2, "title": analyzed["draft"]["title"], "content": "改写后的计划"})).json()["data"]
                assert edited["draft"]["draftVersion"] == 3
                confirmed = (await client.post(f"/agent/threads/{thread_id}/confirm", headers=HEADERS,
                    json={"draftVersion": 3, "paramsDigest": edited["paramsDigest"], "confirm": True})).json()["data"]
                saved_response = (await client.post(f"/agent/threads/{thread_id}/save", headers=HEADERS,
                    json={"confirmationId": confirmed["confirmation"]["id"]})).json()["data"]
                assert saved_response["status"] == "saved"

        in_dispatch = {kind for kind, flag in seen if flag}
        outside_dispatch = {kind for kind, flag in seen if not flag}
        assert {"analysis_completed", "draft_edited", "confirmed", "save_intent", "plan_saved"} <= in_dispatch
        # only the pre-dispatch run fence and reconcile stay in the route
        assert outside_dispatch <= {"analysis_started", "analysis_interrupted"}
        assert post_flags == [True]
        assert posts["count"] == 1

    asyncio.run(scenario())


def test_every_action_leaves_graph_at_await_action_and_restart_replays_nothing(tmp_path, monkeypatch):
    monkeypatch.setattr("agent_service.app.load_u02_gate", lambda *args, **kwargs: {"test_only": True})

    async def paused(app, thread_id):
        snapshot = await app.state.workflow.aget_state({"configurable": {"thread_id": thread_id}})
        return tuple(snapshot.next)

    async def scenario():
        posts, saved, post_flags = {"count": 0}, {}, []
        model = ScriptedModel()
        state_path = tmp_path / "restart.sqlite3"

        def build():
            return create_app(state_path=state_path, client_factory=_write_client(posts, saved, post_flags),
                              offline_model_factory=lambda: (model, {}),
                              u02_gate_path=tmp_path / "gate.json", expected_head="0" * 40)

        app = build()
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = (await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})).json()["data"]
                thread_id = created["threadId"]
                assert await paused(app, thread_id) == ("await_action",)
                analyzed = (await client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={})).json()["data"]
                assert await paused(app, thread_id) == ("await_action",)
                edited = (await client.put(f"/agent/threads/{thread_id}/draft", headers=HEADERS,
                    json={"draftVersion": 2, "title": analyzed["draft"]["title"], "content": "改写后的计划"})).json()["data"]
                assert await paused(app, thread_id) == ("await_action",)
                confirmed = (await client.post(f"/agent/threads/{thread_id}/confirm", headers=HEADERS,
                    json={"draftVersion": 3, "paramsDigest": edited["paramsDigest"], "confirm": True})).json()["data"]
                assert await paused(app, thread_id) == ("await_action",)
                saved_response = (await client.post(f"/agent/threads/{thread_id}/save", headers=HEADERS,
                    json={"confirmationId": confirmed["confirmation"]["id"]})).json()["data"]
                assert saved_response["status"] == "saved"
                assert await paused(app, thread_id) == ("await_action",)
                recovered = (await client.post(f"/agent/threads/{thread_id}/recover", headers=HEADERS,
                    json={"retry": False})).json()["data"]
                assert recovered["status"] == "saved"
                assert await paused(app, thread_id) == ("await_action",)
                assert posts["count"] == 1 and model.calls == 1 and post_flags == [True]

        restarted = build()
        async with restarted.router.lifespan_context(restarted):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=restarted), base_url="http://test") as client:
                first = (await client.get(f"/agent/threads/{thread_id}", headers=HEADERS)).json()["data"]
                second = (await client.get(f"/agent/threads/{thread_id}", headers=HEADERS)).json()["data"]
                assert first["status"] == "saved" and first["receipt"]["planId"] == saved["id"]
                assert second == first
                assert await paused(restarted, thread_id) == ("await_action",)
                again = await client.post(f"/agent/threads/{thread_id}/recover", headers=HEADERS, json={"retry": False})
                assert again.json()["data"]["receipt"]["planId"] == saved["id"]
                assert posts["count"] == 1 and model.calls == 1
                # a lost checkpoint is rebuilt from canonical state and never replays a side effect
                fresh = (await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})).json()["data"]
                await restarted.state.workflow.checkpointer.adelete_thread(fresh["threadId"])
                reedited = await client.put(f"/agent/threads/{fresh['threadId']}/draft", headers=HEADERS,
                    json={"draftVersion": 1, "title": fresh["draft"]["title"], "content": "重建后的计划"})
                assert reedited.status_code == 200
                assert reedited.json()["data"]["draft"]["draftVersion"] == 2
                assert posts["count"] == 1 and model.calls == 1
                assert await paused(restarted, fresh["threadId"]) == ("await_action",)

    asyncio.run(scenario())


def test_cancel_is_a_short_store_fence_that_never_dispatches(tmp_path, monkeypatch):
    monkeypatch.setattr("agent_service.app.load_u02_gate", lambda *args, **kwargs: {"test_only": True})

    async def scenario():
        posts, saved, post_flags = {"count": 0}, {}, []
        model = ScriptedModel()
        app = create_app(state_path=tmp_path / "cancel.sqlite3",
                         client_factory=_write_client(posts, saved, post_flags),
                         offline_model_factory=lambda: (model, {}),
                         u02_gate_path=tmp_path / "gate.json", expected_head="0" * 40)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = (await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})).json()["data"]
                thread_id = created["threadId"]
                cancelled = (await client.post(f"/agent/threads/{thread_id}/cancel", headers=HEADERS, json={})).json()["data"]
                assert cancelled["status"] == "cancelled"
                assert cancelled["receipt"]["cancelRequested"] is True
                repeated = await client.post(f"/agent/threads/{thread_id}/cancel", headers=HEADERS, json={})
                assert repeated.status_code == 200
                events = (await client.get(f"/agent/threads/{thread_id}/events", headers=HEADERS)).json()["data"]["events"]
                assert [event["kind"] for event in events] == ["thread_created", "cancel_requested", "cancel_requested"]
                blocked = await client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={})
                assert blocked.status_code == 409
                snapshot = await app.state.workflow.aget_state({"configurable": {"thread_id": thread_id}})
                assert tuple(snapshot.next) == ("await_action",)
                assert posts["count"] == 0 and model.calls == 0 and post_flags == []

    asyncio.run(scenario())
def test_recover_503_with_business_40400_stays_unknown_and_never_reposts(tmp_path, monkeypatch):
    monkeypatch.setattr("agent_service.app.load_u02_gate", lambda *args, **kwargs: {"test_only": True})

    async def scenario():
        posts = {"count": 0}
        app = create_app(
            state_path=tmp_path / "ambiguous-by-key.sqlite3",
            client_factory=lambda **_: MissingBudgetClient(posts),
            u02_gate_path=tmp_path / "synthetic-test-only-gate.json",
            expected_head="0" * 40,
        )
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                state = created.json()["data"]
                thread_id = state["threadId"]
                confirmed = await client.post(f"/agent/threads/{thread_id}/confirm", headers=HEADERS,
                    json={"draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True})
                saved = await client.post(f"/agent/threads/{thread_id}/save", headers=HEADERS,
                    json={"confirmationId": confirmed.json()["data"]["confirmation"]["id"]})
                assert saved.status_code == 200
                assert saved.json()["data"]["status"] == "unknown"
                assert posts["count"] == 1
                recovered = await client.post(f"/agent/threads/{thread_id}/recover", headers=HEADERS,
                    json={"retry": True})
                current = await client.get(f"/agent/threads/{thread_id}", headers=HEADERS)
                assert recovered.status_code == 502
                assert current.json()["data"]["status"] == "unknown"
                assert posts["count"] == 1

    asyncio.run(scenario())
