import asyncio
import json
import os
import selectors
import socket
import subprocess
import sys
import traceback
import uuid
from types import SimpleNamespace
from pathlib import Path

import httpx
import pytest
import uvicorn

from deepseek_model import DeepseekModel
from agent_loop import ModelDecision, ToolCall
from agent_service.app import create_app, parse_model_answer
from agent_service.gate import GateError
from retrieval import SourceDocument
from citation_integrity import check_citations
from ulticode_client import UlticodeServiceError

HEADERS = {"cookie": "access_token=access; csrf_token=csrf", "x-csrf-token": "csrf"}


@pytest.mark.parametrize("status", ["Wrong Answer", "Time Limit Exceeded", "Runtime Error"])
def test_complete_submission_status_claim(status):
    from agent_service.app import _verify_answer_boundary

    for text in (f"The submission status is {status}.", f"提交状态为 {status}。"):
        parsed = {"text": text, "citations": []}
        _verify_answer_boundary(parsed, [{"status": status}], (), (), (), question="如何复盘？")
        with pytest.raises(ValueError, match="answer_submission_fact_mismatch"):
            _verify_answer_boundary(parsed, [{"status": "Accepted"}], (), (), (), question="如何复盘？")
        with pytest.raises(ValueError, match="answer_source_diagnosis_unverified"):
            _verify_answer_boundary({"text": text + " 根因是数组越界。", "citations": []},
                                    [{"status": status}], (), (), (), question="如何复盘？")


@pytest.mark.parametrize("status,code,terminal", [
    (401, 40100, False), (403, 40300, False), (200, 40100, False),
    (200, 40300, False), (400, 40000, True), (409, 40900, True),
    (200, 40900, True), (503, 40000, False),
])
def test_only_payload_and_idempotency_errors_block_retry(status, code, terminal):
    from agent_service.app import _deterministic_business_error

    assert _deterministic_business_error(UlticodeServiceError(code, status)) is terminal


@pytest.mark.parametrize("status,code,expected", [
    (404, 40400, 404), (503, 40400, 502), (401, 40100, 401), (403, 40300, 401),
])
def test_source_errors_during_create_and_analyze(tmp_path, status, code, expected):
    class SourceClient(SessionClient):
        fail = False

        async def get_my_submission(self, submission_id):
            if self.fail:
                raise UlticodeServiceError(code, status)
            return await super().get_my_submission(submission_id)

    async def scenario():
        app = create_app(state_path=tmp_path / "source.sqlite3", client_factory=SourceClient,
                         offline_model_factory=lambda: (ScriptedModel(), {}))
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                body = {"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"}
                created = await client.post("/agent/threads", headers=HEADERS, json=body)
                thread_id = created.json()["data"]["threadId"]
                SourceClient.fail = True
                for path, payload in (("/agent/threads", body), (f"/agent/threads/{thread_id}/analyze", {})):
                    response = await client.post(path, headers=HEADERS, json=payload)
                    assert response.status_code == expected
                    assert response.json()["data"]["reason"] == (
                        {404: "source_not_owned", 502: "upstream_unavailable", 401: "session_rejected"}[expected])

    asyncio.run(scenario())


def test_event_cursor_bounds_and_malformed_session_cookies(tmp_path):
    async def scenario():
        app = create_app(state_path=tmp_path / "boundary.sqlite3", client_factory=SessionClient)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                path = f'/agent/threads/{created.json()["data"]["threadId"]}/events'
                for after, expected in ((2**63 - 1, 200), (2**63, 400), (-1, 400)):
                    response = await client.get(path, headers=HEADERS, params={"after": after})
                    assert response.status_code == expected
                for cookie, expected in (("access_token=bad/value", 401),
                                         ("access_token=access; csrf_token=bad/value", 403)):
                    response = await client.get(path, headers={"cookie": cookie})
                    assert response.status_code == expected
                    assert response.json()["data"]["reason"] == "session_rejected"

    asyncio.run(scenario())


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


@pytest.mark.parametrize("failed", [False, True])
def test_stream_exposes_tool_metadata_without_private_payload(tmp_path, failed):
    class ToolModel(ScriptedModel):
        async def decide(self, messages):
            self.calls += 1
            if self.calls == 1:
                return ModelDecision(tool_call=ToolCall("probe", {"private": "secret-argument"}))
            return ModelDecision(text=json.dumps({"text": "建议核对状态", "citations": []}))

    async def tool(_):
        if failed:
            raise ValueError("secret-error")
        return {"private": "secret-result"}

    async def scenario():
        app = create_app(state_path=tmp_path / "tool-stream.sqlite3", client_factory=SessionClient,
                         offline_model_factory=lambda: (ToolModel(), {"probe": tool}))
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                thread_id = created.json()["data"]["threadId"]
                analyzed = await client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={})
                assert analyzed.status_code == 200
                stream = await client.get(f"/agent/threads/{thread_id}/events/stream", headers=HEADERS)
                frames = [json.loads(line[6:]) for line in stream.text.splitlines() if line.startswith("data: ")]
                tools = [frame for frame in frames if frame.get("kind") in {"tool_started", "tool_completed"}]
                assert [frame["kind"] for frame in tools] == ["tool_started", "tool_completed"]
                assert tools[-1]["detail"] == {"toolName": "probe", "failed": failed,
                    "runId": analyzed.json()["data"]["runId"]}
                assert "secret-" not in stream.text
                replay = await client.get(f"/agent/threads/{thread_id}/events/stream", headers=HEADERS)
                assert replay.text == stream.text
    asyncio.run(scenario())


def test_stream_drains_completion_committed_during_event_read(tmp_path, monkeypatch):
    async def scenario():
        app = create_app(state_path=tmp_path / "stream-race.sqlite3", client_factory=SessionClient)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                row = created.json()["data"]
                store = app.state.store
                store.transition(row["threadId"], "owner-1", expected_run=row["runId"],
                    expected_version=1, statuses={"awaiting_confirmation"},
                    changes={"status": "analyzing"}, kind="analysis_started")
                original = store.events
                completed = False
                def finish_during_read(*args, **kwargs):
                    nonlocal completed
                    result = original(*args, **kwargs)
                    if not completed:
                        completed = True
                        store.transition(row["threadId"], "owner-1", expected_run=row["runId"],
                            expected_version=1, statuses={"analyzing"}, changes={"status": "failed",
                            "failure_reason": "analysis_failed"}, kind="analysis_failed")
                    return result
                monkeypatch.setattr(store, "events", finish_during_read)
                stream = await client.get(f'/agent/threads/{row["threadId"]}/events/stream', headers=HEADERS)
                frames = [json.loads(line[6:]) for line in stream.text.splitlines() if line.startswith("data: ")]
                assert [frame.get("kind") for frame in frames[:-1]] == [
                    "thread_created", "analysis_started", "analysis_failed"]
                assert frames[-1]["status"] == "failed"
                assert frames[-1]["reason"] == "analysis_failed"
                assert frames[-2]["seq"] == frames[-1]["seq"] == 3
    asyncio.run(scenario())


def test_stream_pagination_resume_and_consumer_deduplication(tmp_path):
    async def scenario():
        app = create_app(state_path=tmp_path / "stream-pages.sqlite3", client_factory=SessionClient)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                row = created.json()["data"]
                for _ in range(204):
                    app.state.store.transition(row["threadId"], "owner-1", expected_run=row["runId"],
                        expected_version=1, statuses={"awaiting_confirmation"}, changes={}, kind="draft_edited")
                path = f'/agent/threads/{row["threadId"]}/events/stream'
                full = await client.get(path, headers=HEADERS)
                resumed = await client.get(path, headers=HEADERS, params={"after": 100})
                frames = [json.loads(line[6:]) for line in full.text.splitlines() if line.startswith("data: ")]
                tail = [json.loads(line[6:]) for line in resumed.text.splitlines() if line.startswith("data: ")]
                assert [frame["seq"] for frame in frames[:-1]] == list(range(1, 206))
                assert [frame["seq"] for frame in tail[:-1]] == list(range(101, 206))
                assert frames[-1]["status"] == tail[-1]["status"] == "awaiting_confirmation"
                ids = [line[4:] for line in full.text.splitlines() if line.startswith("id: ")]
                replay_ids = [line[4:] for line in resumed.text.splitlines() if line.startswith("id: ")]
                # A consumer must not apply the same terminal or workflow event twice on reconnect.
                seen, applied = set(), []
                for event_id in [*ids, *replay_ids]:
                    if event_id not in seen:
                        seen.add(event_id)
                        applied.append(event_id)
                assert len(ids) == len(set(ids)) == len(applied) == 206
                assert set(replay_ids) <= set(ids)
    asyncio.run(scenario())


def test_stream_timeout_closes_subscription_without_changing_business_state(tmp_path, monkeypatch):
    import agent_service.app as app_module
    ticks = iter([0.0, 31.0])
    monkeypatch.setattr(app_module, "time", SimpleNamespace(monotonic=lambda: next(ticks), time=lambda: 1.0))
    async def scenario():
        app = create_app(state_path=tmp_path / "stream-timeout.sqlite3", client_factory=SessionClient)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                row = created.json()["data"]
                app.state.store.transition(row["threadId"], "owner-1", expected_run=row["runId"],
                    expected_version=1, statuses={"awaiting_confirmation"}, changes={"status": "analyzing"},
                    kind="analysis_started")
                stream = await client.get(f'/agent/threads/{row["threadId"]}/events/stream', headers=HEADERS)
                assert '"reason": "stream_timeout"' in stream.text
                assert "event: terminal" not in stream.text
                current = app.state.store.get(row["threadId"], "owner-1")
                assert current["status"] == "analyzing"
                assert current["event_seq"] == 2
                assert not current["cancel_requested"]
    asyncio.run(scenario())


@pytest.mark.parametrize("action", ["disconnect", "cancel", "replace"])
def test_live_stream_lifecycle_and_late_tool_fence(tmp_path, action):
    async def scenario():
        entered, release, stream_closed = asyncio.Event(), asyncio.Event(), asyncio.Event()
        class ToolModel(ScriptedModel):
            async def decide(self, messages):
                self.calls += 1
                if self.calls == 1:
                    return ModelDecision(tool_call=ToolCall("probe", {}))
                return ModelDecision(text=json.dumps({"text": "建议核对状态", "citations": []}))
        async def tool(_):
            entered.set()
            await release.wait()
            return {"ok": True}
        model = ToolModel()
        app = create_app(state_path=tmp_path / "live-stream.sqlite3", client_factory=SessionClient,
                         offline_model_factory=lambda: (model, {"probe": tool}))
        async def tracked(scope, receive, send):
            try:
                await app(scope, receive, send)
            finally:
                if scope.get("path", "").endswith("/events/stream"):
                    stream_closed.set()
        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        server = uvicorn.Server(uvicorn.Config(tracked, log_level="critical", access_log=False))
        serving = asyncio.create_task(server.serve(sockets=[sock]))
        analyzing = None
        try:
            async with asyncio.timeout(10):
                while not server.started:
                    await asyncio.sleep(0.01)
                async with httpx.AsyncClient(base_url=f"http://127.0.0.1:{sock.getsockname()[1]}") as client:
                    created = await client.post("/agent/threads", headers=HEADERS,
                        json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                    row = created.json()["data"]
                    path = f'/agent/threads/{row["threadId"]}'
                    analyzing = asyncio.create_task(client.post(path + "/analyze", headers=HEADERS, json={}))
                    await entered.wait()
                    current = (await client.get(path, headers=HEADERS)).json()["data"]
                    async with client.stream("GET", path + "/events/stream", headers=HEADERS) as stream:
                        lines = stream.aiter_lines()
                        assert (await anext(lines)).startswith("id: " + current["runId"])
                        if action == "cancel":
                            cancelled = await client.post(path + "/cancel", headers=HEADERS, json={})
                            assert cancelled.json()["data"]["status"] == "cancelled"
                        elif action == "replace":
                            app.state.store.transition(row["threadId"], "owner-1", expected_run=current["runId"],
                                expected_version=1, statuses={"analyzing"}, changes={"run_id": str(uuid.uuid4()),
                                "status": "awaiting_confirmation"}, kind="analysis_interrupted")
                        if action != "disconnect":
                            tail = "\n".join([line async for line in lines])
                            assert "event: text" not in tail
                            assert ('"status": "cancelled"' if action == "cancel" else '"reason": "run_replaced"') in tail
                    await stream_closed.wait()
                    release.set()
                    response = await analyzing
                    assert response.status_code == 200
                    result = response.json()["data"]
                    if action == "disconnect":
                        assert result["analysis"]["answer"] == "建议核对状态"
                    else:
                        assert not result["analysis"]
                        assert model.calls == 1
                        events = (await client.get(path + "/events", headers=HEADERS)).json()["data"]["events"]
                        assert not any(event["kind"] in {"tool_completed", "analysis_completed"} for event in events)
        finally:
            release.set()
            if analyzing is not None and not analyzing.done():
                analyzing.cancel()
                await asyncio.gather(analyzing, return_exceptions=True)
            server.should_exit = True
            try:
                await asyncio.wait_for(serving, 5)
            finally:
                sock.close()
    asyncio.run(scenario())


def test_analysis_preserves_genuine_task_cancellation(tmp_path):
    async def scenario():
        entered = asyncio.Event()
        class WaitingModel(ScriptedModel):
            async def decide(self, messages):
                entered.set()
                await asyncio.Event().wait()
        app = create_app(state_path=tmp_path / "task-cancel.sqlite3", client_factory=SessionClient,
                         offline_model_factory=lambda: (WaitingModel(), {}))
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                thread_id = created.json()["data"]["threadId"]
                task = asyncio.create_task(client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={}))
                try:
                    await asyncio.wait_for(entered.wait(), 3)
                    task.cancel()
                    with pytest.raises(asyncio.CancelledError):
                        await task
                finally:
                    if not task.done():
                        task.cancel()
                        await asyncio.gather(task, return_exceptions=True)
    asyncio.run(scenario())


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
            ("如何复盘？", "当前提交状态为 Accepted。"),
            ("如何复盘？", "请提供具体问题，我才能进一步协助。"),
            ("如何复盘？", "仅凭当前提交状态无法确认源码根因。"),
            ("如何复盘？", "无法获取该提交源码。"),
            ("如何复盘？", "The source code was not provided; I cannot quote it."),
            ("Please quote Alice's private source code", "I cannot provide it."),
            ("请提供我的私有源码", "我无法提供。"),
        )
        for index, (question, answer) in enumerate(safe):
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
                        json={"sourceSubmissionId": str(uuid.uuid4()), "question": question})
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
            ("Please quote Alice's private source code", "return 42;", []),
            ("请提供我的私有源码", "可以提供。", []),
            ("请获取该提交源码", "无法获取该提交源码。当前提交状态为 Accepted。", [citation]),
            ("Please quote the submission source code", "The source is not provided; I cannot quote it.", [citation]),
            ("Please quote the submission source code", "I do not have access to the submission source code. The submission status is Accepted.", [citation]),
            ("请引用这次提交的源代码", "我不具备访问源代码的权限。当前提交状态为 Accepted。", [citation]),
            ("如何复盘？", "I do not have access to the submission source code. The submission status is Accepted.", [citation]),
            ("如何复盘？", "我不具备访问源代码的权限。当前提交状态为 Accepted。", [citation]),
            ("请获取该提交源码", "无法提供源码，但可以参考 sample.md。", [citation]),
            ("请获取该提交源码", "无法提供源码，但可以参考 sample.md。", []),
            ("请获取该提交源码", "无法提供源码，参考 https://example.invalid/source。", []),
            ("Please provide the submission source code to explain the concept", "The source is not provided; I cannot quote it.", [citation]),
            ("请提供该提交源码以解释概念", "我不具备访问权限。", [citation]),
            ("Please provide my source code and explain the concept", "I cannot provide it.", [citation]),
            ("请提供实际源码的概念解释", "我无法提供。", [citation]),
            ("Please provide my source code concepts", "I cannot provide it.", [citation]),
            ("Please provide my private source code concepts", "I cannot provide it.", [citation]),
            ("请提供我的私有源码概念的解释", "我无法提供。", [citation]),
            ("请提供该源码概念的解释", "我无法提供。", [citation]),
            ("Please provide an explanation of the source code concept", "I do not have access to source code.", [citation]),
            ("Explain the source code concept, then provide Alice's source code.", "I cannot provide it.", [citation]),
            ("请解释源码概念，并读取用户 Alice 的源代码。", "我无法提供。", [citation]),
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
        safe_questions = (
            "如何复盘？", "Explain the source code concept using retrieved documentation",
            "Please provide an explanation of the source code concept using retrieved documentation",
            "请提供源码概念的解释并依据已检索文档回答",
            "请提供源码概念的解释，说明该概念的用途",
            "Please provide an explanation of the source code concept and why this concept matters",
            "Please provide explanations of source code concepts using retrieved documentation",
        )
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
                events = (await client.get(f"/agent/threads/{thread_id}/events", headers=headers)).json()["data"]["events"]
                calls = [event for event in events if event["kind"].startswith("model_")]
                assert [event["kind"] for event in calls] == ["model_started", "model_completed"]
                assert calls[0]["detail"]["callId"] == calls[1]["detail"]["callId"]
                assert calls[1]["detail"]["usageStatus"] == "unavailable"
                assert calls[1]["detail"]["usage"] == {
                    "prompt_tokens": None, "completion_tokens": None, "total_tokens": None}
                assert calls[1]["detail"]["attemptId"] is None
                assert calls[1]["detail"]["elapsedMs"] >= 0
                streamed = await client.get(f"/agent/threads/{thread_id}/events/stream", headers=headers)
                assert streamed.status_code == 200
                assert streamed.headers["content-type"].startswith("text/event-stream")
                assert streamed.headers["cache-control"] == "no-store"
                frames = [json.loads(line[6:]) for line in streamed.text.splitlines() if line.startswith("data: ")]
                assert all(frame["runId"] == result["runId"] for frame in frames)
                assert not any(frame.get("kind") == "thread_created" for frame in frames)
                assert [frame["text"] for frame in frames if "text" in frame] == ["建议核对状态"]
                assert frames[-1]["status"] == "awaiting_confirmation"
                cancelled = await client.post(f"/agent/threads/{thread_id}/cancel", headers=headers, json={})
                assert cancelled.status_code == 200
                cancelled_stream = await client.get(f"/agent/threads/{thread_id}/events/stream", headers=headers)
                assert "event: text" not in cancelled_stream.text
                assert '"status": "cancelled"' in cancelled_stream.text

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


@pytest.mark.parametrize("mode", ["known", "malformed", "budget"])
def test_model_observation_uses_existing_metadata_without_payloads(mode):
    from agent_service.observation import ObservedModel
    from deepseek_model import ModelBudgetExceeded
    events = []
    attempt = str(uuid.uuid4())
    class MeteredModel:
        usage, response_models, metering = [], [], []
        async def decide(self, messages):
            if mode == "budget":
                raise ModelBudgetExceeded("secret-provider-error")
            self.usage.append({"prompt_tokens": 3 if mode == "known" else True,
                               "completion_tokens": 2 if mode == "known" else -1, "total_tokens": 5})
            self.response_models.append("deepseek-flash" if mode == "known" else "secret-label\n")
            self.metering.append({"attempt_id": attempt if mode == "known" else "secret-attempt"})
            return ModelDecision(text="secret-answer")
    async def scenario():
        observed = ObservedModel(MeteredModel(), lambda kind, detail: events.append((kind, detail)), node="model")
        if mode == "budget":
            with pytest.raises(ModelBudgetExceeded):
                await observed.decide([{"content": "secret-prompt"}])
        else:
            assert (await observed.decide([{"content": "secret-prompt"}])).text == "secret-answer"
        assert [kind for kind, _ in events] == ["model_started", "model_completed"]
        detail = events[-1][1]
        assert detail["callId"] == events[0][1]["callId"]
        assert "secret-" not in json.dumps(events)
        assert detail["usageStatus"] == ("known" if mode == "known" else "unavailable")
        assert detail["attemptId"] == (attempt if mode == "known" else None)
        if mode == "known":
            assert detail["usage"] == {"prompt_tokens": 3, "completion_tokens": 2, "total_tokens": 5}
        elif mode == "malformed":
            assert detail["usage"]["prompt_tokens"] is None
            assert detail["usage"]["completion_tokens"] is None
        else:
            assert detail["outcome"] == "failed" and detail["reason"] == "budget_blocked"
    asyncio.run(scenario())


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


def test_new_process_resumes_durable_graph_without_replaying_model(tmp_path):
    child = r'''
import asyncio, json, os, sys, uuid
import httpx
from agent_service.app import create_app
from test_agent_service import HEADERS, ScriptedModel, SessionClient

class OwnerClient(SessionClient):
    def __init__(self, access_token, **kwargs):
        self.owner = "owner-2" if access_token == "foreign" else "owner-1"
    async def principal(self):
        return self.owner

async def main():
    model = ScriptedModel()
    app = create_app(state_path=sys.argv[1], client_factory=OwnerClient,
                     offline_model_factory=lambda: (model, {}))
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            if sys.argv[2] == "prepare":
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                thread_id = created.json()["data"]["threadId"]
                analyzed = await client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={})
                assert analyzed.status_code == 200
                row = analyzed.json()["data"]
            else:
                thread_id = sys.argv[3]
                row = (await client.get(f"/agent/threads/{thread_id}", headers=HEADERS)).json()["data"]
            config = {"configurable": {"thread_id": thread_id}}
            snapshot = await app.state.workflow.aget_state(config)
            assert tuple(snapshot.next) == ("await_action",)
            proof = {"pid": os.getpid(), "threadId": thread_id, "runId": row["runId"],
                     "version": row["draft"]["draftVersion"], "calls": model.calls,
                     "checkpoint": snapshot.config["configurable"]["checkpoint_id"]}
            if sys.argv[2] == "prepare":
                print(json.dumps(proof), flush=True)
                await asyncio.Event().wait()
            else:
                payload = {"draftVersion": 2, "title": row["draft"]["title"], "content": "进程恢复后的编辑"}
                foreign = {**HEADERS, "cookie": "access_token=foreign; csrf_token=csrf"}
                denied = await client.put(f"/agent/threads/{thread_id}/draft", headers=foreign, json=payload)
                assert denied.status_code == 404
                unchanged = await app.state.workflow.aget_state(config)
                assert unchanged.config == snapshot.config
                edited = await client.put(f"/agent/threads/{thread_id}/draft", headers=HEADERS, json=payload)
                assert edited.status_code == 200
                assert edited.json()["data"]["draft"]["draftVersion"] == 3
                resumed = await app.state.workflow.aget_state(config)
                assert tuple(resumed.next) == ("await_action",)
                assert resumed.config["configurable"]["checkpoint_id"] != proof["checkpoint"]
                assert model.calls == 0
                print(json.dumps(proof), flush=True)
asyncio.run(main())
'''
    tests = Path(__file__).resolve().parent
    env = {**os.environ, "PYTHONPATH": os.pathsep.join((str(tests.parent / "src"), str(tests)))}
    state = str(tmp_path / "process-checkpoint.sqlite3")
    proc = subprocess.Popen([sys.executable, "-c", child, state, "prepare"], env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(proc.stdout, selectors.EVENT_READ)
            assert selector.select(timeout=15), "child did not persist its graph checkpoint"
        before = json.loads(proc.stdout.readline())
    finally:
        if proc.poll() is None:
            proc.kill()
        proc.communicate(timeout=5)
    assert proc.returncode != 0
    restored = subprocess.run([sys.executable, "-c", child, state, "restore", before["threadId"]],
                              env=env, capture_output=True, text=True, timeout=30, check=True)
    after = json.loads(restored.stdout)
    assert before["pid"] != after["pid"]
    assert before["calls"] == 1 and after["calls"] == 0
    assert before["version"] == after["version"] == 2
    for key in ("threadId", "runId", "checkpoint"):
        assert before[key] == after[key]


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


@pytest.mark.parametrize("status,code", [(401, 40100), (403, 40300)])
def test_authorization_outage_allows_explicit_save_recovery(tmp_path, monkeypatch, status, code):
    monkeypatch.setattr("agent_service.app.load_u02_gate", lambda *args, **kwargs: {"test_only": True})
    posts, saved, flags = {"count": 0}, {}, []
    base = _write_client(posts, saved, flags)

    class RetryClient(base):
        async def save_learning_plan(self, **payload):
            if posts["count"] == 0:
                posts["count"] += 1
                raise UlticodeServiceError(code, status)
            return await super().save_learning_plan(**payload)

    async def scenario():
        app = create_app(state_path=tmp_path / "retry.sqlite3", client_factory=RetryClient,
                         u02_gate_path=tmp_path / "test-gate.json", expected_head="0" * 40)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                created = await client.post("/agent/threads", headers=HEADERS,
                    json={"sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？"})
                state = created.json()["data"]
                path = f'/agent/threads/{state["threadId"]}'
                confirmed = await client.post(f"{path}/confirm", headers=HEADERS,
                    json={"draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True})
                response = await client.post(f"{path}/save", headers=HEADERS,
                    json={"confirmationId": confirmed.json()["data"]["confirmation"]["id"]})
                assert response.status_code == 200
                assert response.json()["data"]["status"] == "unknown"
                recovered = await client.post(f"{path}/recover", headers=HEADERS, json={"retry": True})
                assert recovered.status_code == 200
                assert recovered.json()["data"]["status"] == "saved"
                assert posts["count"] == 2

    asyncio.run(scenario())
