import asyncio
import json
import uuid

import httpx
import pytest
from pydantic import ValidationError

from agent_service.app import _response, create_app
from agent_loop import ModelDecision
from ulticode_client import UlticodeClient, UlticodeServiceError

HEADERS = {"cookie": "access_token=access; csrf_token=csrf", "x-csrf-token": "csrf"}


@pytest.mark.parametrize("data,code,message", [
    ({"value": object()}, 0, "success"),
    ({"value": float("nan")}, 0, "success"),
    ({"value": "ok"}, True, "success"),
    ({"value": "ok"}, 0, 123),
])
def test_invalid_response_envelope_is_not_serialized_as_success(data, code, message):
    with pytest.raises(ValidationError):
        _response(data, code=code, message=message)


def test_response_schema_preserves_wire_contract():
    response = _response({"reason": "session_required"}, code=40100,
                         message="session_required", status=401)
    body = json.loads(response.body)
    assert response.status_code == 401
    assert body == {"code": 40100, "message": "session_required",
                    "data": {"reason": "session_required"}, "traceId": body["traceId"]}
    assert str(uuid.UUID(body["traceId"])) == body["traceId"]


class Session:
    def __init__(self, owner="owner", **_):
        self.owner = owner

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_):
        pass

    async def principal(self):
        return self.owner

    async def get_my_submission(self, submission_id):
        return {"id": submission_id, "status": "Accepted"}


def gated(monkeypatch):
    monkeypatch.setattr("agent_service.app.load_u02_gate", lambda *args, **kwargs: {"test_only": True})


def write_client(posts, *, save=None, by_key=None):
    async def unavailable(**_):
        posts["count"] += 1
        raise UlticodeServiceError(50000, 503)

    class Client(Session):
        async def save_learning_plan(self, **payload):
            if save:
                return await save(payload)
            return await unavailable(**payload)

        async def get_learning_plan_by_key(self, key):
            if by_key:
                return await by_key(key)
            raise UlticodeServiceError(40400, 404)

    return Client


async def create_thread(client, *, headers=HEADERS):
    response = await client.post("/agent/threads", headers=headers, json={
        "sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？",
    })
    assert response.status_code == 200
    return response.json()["data"]


async def with_client(app, callback):
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            return await callback(client)


@pytest.mark.parametrize("headers", [
    {"cookie": "access_token=one; access_token=two; csrf_token=csrf", "x-csrf-token": "csrf"},
    {"cookie": "access_token=one; csrf_token=one; csrf_token=two", "x-csrf-token": "one"},
    [("cookie", "access_token=one; csrf_token=csrf"), ("x-csrf-token", "csrf"),
     ("x-csrf-token", "csrf")],
])
def test_duplicate_access_or_csrf_rejected_before_operation(tmp_path, headers):
    async def scenario(client):
        response = await client.post("/agent/threads", headers=headers, json={
            "sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？",
        })
        assert response.status_code == 403
        assert response.json()["data"]["reason"] in {"session_rejected", "csrf_mismatch"}

    app = create_app(state_path=tmp_path / "headers.sqlite3", client_factory=Session)
    asyncio.run(with_client(app, scenario))


@pytest.mark.parametrize("raw,content_type,expected_status", [
    (b'{"sourceSubmissionId":"00000000-0000-4000-8000-000000000001","question":"ok","nested":{"x":1,"x":2}}', "application/json", 400),
    (b'{"sourceSubmissionId":"00000000-0000-4000-8000-000000000001","question":"ok","value":NaN}', "application/json", 400),
    (b'{"sourceSubmissionId":"00000000-0000-4000-8000-000000000001","question":"ok"}', "text/plain", 400),
    (b" " * (128 * 1024 + 1), "application/json", 413),
])
def test_invalid_json_envelopes_rejected(tmp_path, raw, content_type, expected_status):
    async def scenario(client):
        response = await client.post("/agent/threads", headers={**HEADERS, "content-type": content_type}, content=raw)
        assert response.status_code == expected_status
        assert response.json()["code"] == 40000

    app = create_app(state_path=tmp_path / "body.sqlite3", client_factory=Session)
    asyncio.run(with_client(app, scenario))


def test_extra_identity_field_rejected(tmp_path):
    async def scenario(client):
        response = await client.post("/agent/threads", headers=HEADERS, json={
            "sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？", "user_id": "victim",
        })
        assert response.status_code == 400
        assert response.json()["data"]["reason"] == "validation_error"

    app = create_app(state_path=tmp_path / "identity.sqlite3", client_factory=Session)
    asyncio.run(with_client(app, scenario))


@pytest.mark.parametrize("headers", [{}, {"authorization": "Bearer forged"}])
@pytest.mark.parametrize("operation", ["create", "get", "analyze"])
def test_missing_session_rejected_before_client_creation(tmp_path, headers, operation):
    def forbidden_client(**_):
        pytest.fail("A missing cookie identity must not construct an upstream client")

    async def scenario(client):
        thread_path = f"/agent/threads/{uuid.uuid4()}"
        if operation == "create":
            response = await client.post("/agent/threads", headers=headers, json={
                "sourceSubmissionId": str(uuid.uuid4()), "question": "如何复盘？",
            })
        elif operation == "get":
            response = await client.get(thread_path, headers=headers)
        else:
            response = await client.post(thread_path + "/analyze", headers=headers, json={})
        assert response.status_code == 401
        assert response.json()["code"] == 40100
        assert response.json()["data"]["reason"] == "session_required"

    app = create_app(state_path=tmp_path / "missing-session.sqlite3", client_factory=forbidden_client)
    asyncio.run(with_client(app, scenario))


@pytest.mark.parametrize("operation", ["get", "events", "edit", "analyze", "confirm", "save", "recover", "cancel"])
def test_foreign_owner_is_uniform_404_without_lock_file(tmp_path, monkeypatch, operation):
    gated(monkeypatch)
    def factory(**kwargs):
        token = kwargs.get("access_token")
        return Session(owner="owner" if token == "access" else "other")

    app = create_app(state_path=tmp_path / "foreign.sqlite3", client_factory=factory,
                     u02_gate_path=tmp_path / "synthetic-test-only-gate.json", expected_head="0" * 40)

    async def scenario(client):
        state = await create_thread(client)
        thread_id = state["threadId"]
        before = list(tmp_path.glob("*.lock"))
        assert before == []
        foreign = {"cookie": "access_token=foreign; csrf_token=csrf", "x-csrf-token": "csrf"}
        if operation == "get":
            response = await client.get(f"/agent/threads/{thread_id}", headers=foreign)
        elif operation == "events":
            response = await client.get(f"/agent/threads/{thread_id}/events", headers=foreign)
        elif operation == "edit":
            response = await client.put(f"/agent/threads/{thread_id}/draft", headers=foreign, json={
                "draftVersion": 1, "title": "改标题", "content": "改内容",
            })
        else:
            bodies = {
                "analyze": {}, "confirm": {"draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True},
                "save": {"confirmationId": str(uuid.uuid4())}, "recover": {"retry": True}, "cancel": {},
            }
            response = await client.post(f"/agent/threads/{thread_id}/{operation}", headers=foreign,
                                         json=bodies[operation])
        assert response.status_code == 404
        assert response.json()["code"] == 40400
        assert list(tmp_path.glob("*.lock")) == before

    asyncio.run(with_client(app, scenario))


def test_write_routes_absent_without_gate_and_never_post(tmp_path):
    posts = {"count": 0}
    app = create_app(state_path=tmp_path / "nogate.sqlite3", client_factory=write_client(posts))

    async def scenario(client):
        state = await create_thread(client)
        thread_id = state["threadId"]
        for route, body in (("confirm", {"draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True}),
                            ("save", {"confirmationId": str(uuid.uuid4())}), ("recover", {"retry": True})):
            response = await client.post(f"/agent/threads/{thread_id}/{route}", headers=HEADERS, json=body)
            assert response.status_code == 404
        assert posts["count"] == 0

    asyncio.run(with_client(app, scenario))


@pytest.mark.parametrize("expiry", [False, True])
def test_edit_invalidates_confirmation_and_ttl_boundary_never_posts(tmp_path, monkeypatch, expiry):
    gated(monkeypatch)
    posts = {"count": 0}
    now = {"value": 1000.0}
    app = create_app(state_path=tmp_path / f"confirm-{expiry}.sqlite3", client_factory=write_client(posts),
                     u02_gate_path=tmp_path / "synthetic-test-only-gate.json", expected_head="0" * 40,
                     clock=lambda: now["value"])

    async def scenario(client):
        state = await create_thread(client)
        thread_id = state["threadId"]
        confirmed = await client.post(f"/agent/threads/{thread_id}/confirm", headers=HEADERS, json={
            "draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True,
        })
        confirmation_id = confirmed.json()["data"]["confirmation"]["id"]
        if expiry:
            now["value"] += 1800
        else:
            edited = await client.put(f"/agent/threads/{thread_id}/draft", headers=HEADERS, json={
                "draftVersion": 1, "title": "改过的标题", "content": "改过的内容",
            })
            assert edited.status_code == 200
        response = await client.post(f"/agent/threads/{thread_id}/save", headers=HEADERS,
                                     json={"confirmationId": confirmation_id})
        assert response.status_code == 409 if expiry else response.status_code == 400
        assert posts["count"] == 0

    asyncio.run(with_client(app, scenario))


@pytest.mark.parametrize("readback_error", [(40400, 404), (40400, 500), (50000, 503)])
def test_unknown_readback_requires_explicit_same_key_retry(tmp_path, monkeypatch, readback_error):
    gated(monkeypatch)
    posts, keys = {"count": 0}, []

    async def save(payload):
        posts["count"] += 1
        keys.append(payload["idempotency_key"])
        if posts["count"] == 1:
            raise UlticodeServiceError(50000, 503)
        source = payload["source_submission_id"]
        return {"id": str(uuid.uuid4()), "sourceSubmissionId": source,
                "draftVersion": payload["draft_version"], "title": payload["title"], "content": payload["content"]}

    async def by_key(_key):
        raise UlticodeServiceError(*readback_error)

    app = create_app(state_path=tmp_path / "retry.sqlite3", client_factory=write_client(posts, save=save, by_key=by_key),
                     u02_gate_path=tmp_path / "synthetic-test-only-gate.json", expected_head="0" * 40)

    async def scenario(client):
        state = await create_thread(client)
        thread_id = state["threadId"]
        confirmed = await client.post(f"/agent/threads/{thread_id}/confirm", headers=HEADERS, json={
            "draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True,
        })
        cid = confirmed.json()["data"]["confirmation"]["id"]
        first = await client.post(f"/agent/threads/{thread_id}/save", headers=HEADERS, json={"confirmationId": cid})
        assert first.status_code == 200 and first.json()["data"]["status"] == "unknown"
        assert posts["count"] == 1
        no_retry = await client.post(f"/agent/threads/{thread_id}/recover", headers=HEADERS, json={"retry": False})
        expected_status = 200 if readback_error == (40400, 404) else 502
        assert no_retry.status_code == expected_status
        if expected_status == 200:
            assert no_retry.json()["data"]["status"] == "unknown"
        assert posts["count"] == 1
        if readback_error == (40400, 404):
            retried = await client.post(f"/agent/threads/{thread_id}/recover", headers=HEADERS, json={"retry": True})
            assert retried.status_code == 200 and retried.json()["data"]["status"] == "saved"
            assert posts["count"] == 2 and keys[0] == keys[1]
        else:
            assert posts["count"] == 1
    asyncio.run(with_client(app, scenario))

@pytest.mark.parametrize("status", [500, 503])
def test_http_error_result_code_does_not_override_http_status(status):
    response = httpx.Response(
        status, json={"code": 40400, "message": "not found", "data": None},
        request=httpx.Request("GET", "http://java/learning-plans/by-key/test"),
    )
    with pytest.raises(UlticodeServiceError) as error:
        UlticodeClient._unwrap(response)
    assert error.value.code == 40400
    assert error.value.status_code == status



@pytest.mark.parametrize("readback", ["bad_vo", "not_found"])
def test_saved_status_survives_bad_or_missing_readback(tmp_path, monkeypatch, readback):
    gated(monkeypatch)
    posts = {"count": 0}

    async def save(payload):
        posts["count"] += 1
        return {"id": str(uuid.uuid4()), "sourceSubmissionId": payload["source_submission_id"],
                "draftVersion": payload["draft_version"], "title": payload["title"], "content": payload["content"]}

    async def by_key(_key):
        if readback == "not_found":
            raise UlticodeServiceError(40400, 404)
        return {"id": str(uuid.uuid4()), "sourceSubmissionId": "wrong", "draftVersion": 1,
                "title": "wrong", "content": "wrong"}

    app = create_app(state_path=tmp_path / f"saved-{readback}.sqlite3",
                     client_factory=write_client(posts, save=save, by_key=by_key),
                     u02_gate_path=tmp_path / "synthetic-test-only-gate.json", expected_head="0" * 40)

    async def scenario(client):
        state = await create_thread(client)
        thread_id = state["threadId"]
        confirmed = await client.post(f"/agent/threads/{thread_id}/confirm", headers=HEADERS, json={
            "draftVersion": 1, "paramsDigest": state["paramsDigest"], "confirm": True,
        })
        saved = await client.post(f"/agent/threads/{thread_id}/save", headers=HEADERS,
                                  json={"confirmationId": confirmed.json()["data"]["confirmation"]["id"]})
        assert saved.json()["data"]["status"] == "saved"
        recovered = await client.post(f"/agent/threads/{thread_id}/recover", headers=HEADERS, json={"retry": False})
        assert recovered.status_code == 200 and recovered.json()["data"]["status"] == "saved"
        assert posts["count"] == 1

    asyncio.run(with_client(app, scenario))


class WaitingModel:
    def __init__(self, started, release):
        self.started, self.release = started, release

    async def decide(self, _messages):
        self.started.set()
        await self.release.wait()
        return ModelDecision(text=json.dumps({"text": "late answer", "citations": []}))


def test_cancel_during_analysis_discards_late_answer_without_sleep(tmp_path):
    started, release = asyncio.Event(), asyncio.Event()
    model = WaitingModel(started, release)
    app = create_app(state_path=tmp_path / "cancel-analysis.sqlite3", client_factory=Session,
                     offline_model_factory=lambda: (model, {}))

    async def scenario(client):
        state = await create_thread(client)
        thread_id = state["threadId"]
        initial = state["draft"]
        analysis_task = asyncio.create_task(client.post(f"/agent/threads/{thread_id}/analyze", headers=HEADERS, json={}))
        await asyncio.wait_for(started.wait(), timeout=2)
        cancelled = await client.post(f"/agent/threads/{thread_id}/cancel", headers=HEADERS, json={})
        assert cancelled.status_code == 200 and cancelled.json()["data"]["status"] == "cancelled"
        release.set()
        response = await analysis_task
        assert response.status_code == 200
        final = await client.get(f"/agent/threads/{thread_id}", headers=HEADERS)
        data = final.json()["data"]
        assert data["status"] == "cancelled"
        assert data["draft"] == initial
        assert data["draft"]["draftVersion"] == 1
        events = await client.get(f"/agent/threads/{thread_id}/events", headers=HEADERS)
        kinds = [event["kind"] for event in events.json()["data"]["events"]]
        assert kinds == ["thread_created", "analysis_started", "model_started", "cancel_requested"]

    asyncio.run(with_client(app, scenario))
