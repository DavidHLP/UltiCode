import json
import asyncio

import httpx
import pytest

from ulticode_client import UlticodeClient, UlticodeError, UlticodeServiceError, canonical_uuid


def test_unwraps_result_envelope_to_data() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"code": 0, "message": "success", "data": {"items": [{"id": 7}], "total": 1}},
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            data = await client.list_problems()
        assert data == {"items": [{"id": 7}], "total": 1}

    asyncio.run(scenario())


def test_non_zero_envelope_code_is_redacted_error() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            401, json={"code": 40100, "message": "SECRET", "traceId": "t-1"}
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError) as exc_info:
                await client.list_my_submissions()
            assert str(exc_info.value) == "service_error"
            assert "40100" not in str(exc_info.value)
            assert "SECRET" not in str(exc_info.value)

    asyncio.run(scenario())


def test_response_without_result_envelope_is_rejected() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"oops": 1})

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="envelope"):
                await client.list_problems()

    asyncio.run(scenario())


def test_login_captures_cookies_and_app_sends_only_access_cookie() -> None:
    seen: dict[str, str | None] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/auth/login":
            return httpx.Response(
                200,
                json={"code": 0, "message": "success", "data": {}},
                headers=[
                    ("set-cookie", "access_token=at-123; Path=/; HttpOnly"),
                    ("set-cookie", "refresh_token=rt-123; Path=/; HttpOnly"),
                    ("set-cookie", "csrf_token=cs-1; Path=/"),
                ],
            )
        seen["cookie"] = request.headers.get("cookie")
        return httpx.Response(
            200, json={"code": 0, "message": "success", "data": {"items": [], "total": 0}}
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            await client.login("tester", "pw")
            assert client.cookie_names() == ["access_token", "csrf_token", "refresh_token"]
            await client.list_my_submissions()

    asyncio.run(scenario())
    assert seen["cookie"] == "access_token=at-123"


def test_service_error_does_not_echo_server_message() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            500,
            json={
                "code": 50001,
                "message": "SECRET user=u-secret source=private input=stdin",
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError) as exc_info:
                await client.list_problems()
            message = str(exc_info.value)
            assert message == "service_error"
            assert "50001" not in message

    asyncio.run(scenario())


@pytest.mark.parametrize("code", [{"traceback": "SECRET"}, ["SECRET"], "SECRET"])
def test_invalid_error_code_is_rejected_without_echoing_payload(code: object) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"code": code, "message": "SECRET"})

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError) as exc_info:
                await client.list_problems()
            assert "SECRET" not in str(exc_info.value)

    asyncio.run(scenario())



@pytest.mark.parametrize("code", [-2_147_483_649, 2_147_483_648])
def test_out_of_range_service_code_is_rejected(code: int) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"code": code, "message": "SECRET"})

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="invalid Result envelope") as exc_info:
                await client.list_problems()
            assert "SECRET" not in str(exc_info.value)

    asyncio.run(scenario())

@pytest.mark.parametrize("problem_id", [0, -1, True, "7"])
def test_problem_id_must_be_a_positive_integer(problem_id: object) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request should be sent for invalid ids")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="positive integer"):
                await client.get_problem(problem_id)  # type: ignore[arg-type]
    asyncio.run(scenario())





def test_failed_relogin_clears_previous_session() -> None:
    login_count = 0
    app_cookie: str | None = None

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal login_count, app_cookie
        if request.url.path == "/auth/login":
            login_count += 1
            if login_count == 1:
                return httpx.Response(
                    200,
                    json={"code": 0, "message": "success", "data": {}},
                    headers=[("set-cookie", "access_token=old; Path=/; HttpOnly")],
                )
            return httpx.Response(
                401,
                json={"code": 40100, "message": "Unauthorized"},
                headers=[("set-cookie", "access_token=new-invalid; Path=/; HttpOnly")],
            )
        app_cookie = request.headers.get("cookie")
        return httpx.Response(
            200, json={"code": 0, "message": "success", "data": {"items": [], "total": 0}}
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            await client.login("old", "pw")
            with pytest.raises(UlticodeError):
                await client.login("new", "bad")
            await client.list_my_submissions()

    asyncio.run(scenario())
    assert app_cookie is None


def test_failed_relogin_clears_auth_cookie_before_me() -> None:
    login_count = 0
    auth_cookie: str | None = None

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal login_count, auth_cookie
        if request.url.path == "/auth/login":
            login_count += 1
            if login_count == 1:
                return httpx.Response(
                    200,
                    json={"code": 0, "message": "success", "data": {}},
                    headers=[("set-cookie", "access_token=old; Path=/; HttpOnly")],
                )
            return httpx.Response(
                401,
                json={"code": 40100, "message": "Unauthorized"},
                headers=[("set-cookie", "access_token=new-invalid; Path=/; HttpOnly")],
            )
        if request.url.path == "/auth/me":
            auth_cookie = request.headers.get("cookie")
        return httpx.Response(
            200, json={"code": 0, "message": "success", "data": {"id": "user"}}
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            await client.login("old", "pw")
            with pytest.raises(UlticodeError):
                await client.login("new", "bad")
            await client.me()

    asyncio.run(scenario())
    assert auth_cookie is None


def test_non_object_success_data_is_rejected_without_assertions() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"code": 0, "message": "success", "data": []})

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="invalid response shape"):
                await client.list_problems()

    asyncio.run(scenario())


def test_login_without_access_cookie_is_rejected_and_clears_session() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"code": 0, "message": "success", "data": {}})

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="login_error"):
                await client.login("tester", "pw")
            assert client.cookie_names() == []

    asyncio.run(scenario())


def test_login_with_duplicate_access_token_cookies_sends_validated_cookie() -> None:
    seen_cookie: str | None = None

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal seen_cookie
        if request.url.path == "/auth/login":
            return httpx.Response(
                200,
                json={"code": 0, "message": "success", "data": {}},
                headers=[
                    ("set-cookie", "access_token=; Path=/"),
                    ("set-cookie", "access_token=good; Path=/app"),
                ],
            )
        seen_cookie = request.headers.get("cookie")
        return httpx.Response(
            200, json={"code": 0, "message": "success", "data": {"items": [], "total": 0}}
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            await client.login("tester", "pw")
            await client.list_my_submissions()

    asyncio.run(scenario())
    assert seen_cookie == "access_token=good"
def test_app_requests_do_not_forward_refresh_or_csrf_cookies() -> None:
    seen_cookie: str | None = None

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal seen_cookie
        if request.url.path == "/auth/login":
            return httpx.Response(
                200,
                json={"code": 0, "message": "success", "data": {}},
                headers=[
                    ("set-cookie", "access_token=access; Path=/; HttpOnly"),
                    ("set-cookie", "refresh_token=refresh; Path=/; HttpOnly"),
                    ("set-cookie", "csrf_token=csrf; Path=/"),
                ],
            )
        seen_cookie = request.headers.get("cookie")
        return httpx.Response(
            200, json={"code": 0, "message": "success", "data": {"items": [], "total": 0}}
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            await client.login("tester", "pw")
            await client.list_my_submissions()

    asyncio.run(scenario())
    assert seen_cookie == "access_token=access"
def test_get_my_submission_rejects_path_traversal_id() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request should be sent for an invalid submission id")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            for invalid in ("../problems", "", "  ", 7):
                with pytest.raises(ValueError, match="submission UUID"):
                    await client.get_my_submission(invalid)  # type: ignore[arg-type]

    asyncio.run(scenario())


@pytest.mark.parametrize("body", [
    '{"code":500,"code":0,"message":"success","data":{}}',
    '{"code":0,"message":"success","data":{"total":1,"total":999}}',
])
def test_unwrap_rejects_duplicate_json_keys(body: str) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text=body)

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="duplicate key in service response"):
                await client.list_my_submissions()
    asyncio.run(scenario())


def test_unwrap_reports_malformed_utf8_as_non_json() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b'{"code":0,"message":"\xff\xfe"}')

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="non-JSON response"):
                await client.list_my_submissions()

    asyncio.run(scenario())

def test_search_problems_sends_the_query_page_and_limit() -> None:
    seen: dict[str, object] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["query"] = request.url.params.get("query")
        seen["page"] = request.url.params.get("page")
        seen["limit"] = request.url.params.get("limit")
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "query": "Two Sum",
                    "total": 1,
                    "page": 2,
                    "limit": 5,
                    "results": [{"id": "1", "title": "Two Sum"}],
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            data = await client.search_problems("Two Sum", page=2, limit=5)
            assert data["total"] == 1
            assert data["page"] == 2

    asyncio.run(scenario())
    assert seen == {"path": "/search", "query": "Two Sum", "page": "2", "limit": "5"}


@pytest.mark.parametrize(
    ("query", "page", "limit"),
    [
        ("a", 1, 10),
        ("x" * 201, 1, 10),
        ("ok", 0, 10),
        ("ok", 1, 0),
        ("ok", 1, 101),
        ("ok", True, 10),
    ],
)
def test_search_problems_rejects_invalid_arguments(
    query: object, page: object, limit: object
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request for invalid search arguments")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError):
                await client.search_problems(  # type: ignore[arg-type]
                    query, page=page, limit=limit
                )

    asyncio.run(scenario())


def test_search_problems_forwards_only_the_current_access_cookie() -> None:
    """A logged-in search carries the session cookie; an anonymous one carries none."""
    seen: list[str | None] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/auth/login":
            return httpx.Response(
                200,
                json={"code": 0, "message": "success", "data": {}},
                headers=[
                    ("set-cookie", "access_token=at-1; Path=/; HttpOnly"),
                    ("set-cookie", "refresh_token=rt-1; Path=/; HttpOnly"),
                    ("set-cookie", "csrf_token=cs-1; Path=/"),
                ],
            )
        seen.append(request.headers.get("cookie"))
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"query": "Two Sum", "total": 0, "page": 1, "limit": 10, "results": []},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            # Anonymous: the endpoint is public, so no cookie is sent at all.
            await client.search_problems("Two Sum")
            await client.login("tester", "pw")
            await client.search_problems("Two Sum")

    asyncio.run(scenario())
    # Only the access cookie travels; refresh/csrf stay in the jar.
    assert seen == [None, "access_token=at-1"]


def test_search_problems_rejects_a_page_that_does_not_echo_the_request() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"query": "Two Sum", "total": 1, "page": 3, "limit": 10, "results": []},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="invalid search response"):
                await client.search_problems("Two Sum", page=1, limit=10)

    asyncio.run(scenario())


def test_search_problems_rejects_a_bool_echo() -> None:
    """``True == 1`` in Python, so a bool page/limit echo must be rejected explicitly."""
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"query": "Two Sum", "total": 0, "page": True, "limit": 10, "results": []},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="invalid search response"):
                await client.search_problems("Two Sum", page=1, limit=10)

    asyncio.run(scenario())


def test_search_problems_rejects_a_negative_total() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"query": "Two Sum", "total": -1, "page": 1, "limit": 10, "results": []},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(UlticodeError, match="invalid search response"):
                await client.search_problems("Two Sum", page=1, limit=10)

    asyncio.run(scenario())


def test_search_problems_hands_over_a_page_before_rejecting_it() -> None:
    """A page that carries hits but does not describe itself still reaches the sink.

    The contract check is not relaxed — the page is still rejected — but a caller
    scanning for leaked rows sees the hits that did arrive, instead of losing them
    to the error that follows. Only a successful envelope with a dict ``data`` is
    handed over: a failed or shapeless Result never reaches the callback.
    """
    seen: list[dict[str, object]] = []

    async def invalid_page(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "query": "Two Sum",
                    "total": 1,
                    "page": 3,
                    "limit": 10,
                    "results": [{"id": "1", "title": "u02-canary-a"}],
                },
            },
        )

    async def failed_envelope(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 50000,
                "message": "SECRET",
                "data": {"results": [{"id": "1", "title": "u02-canary-a"}]},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test",
            "https://auth.test",
            transport=httpx.MockTransport(invalid_page),
        ) as client:
            with pytest.raises(UlticodeError, match="invalid search response"):
                await client.search_problems(
                    "Two Sum", page=1, limit=10, on_payload=seen.append
                )
        async with UlticodeClient(
            "https://app.test",
            "https://auth.test",
            transport=httpx.MockTransport(failed_envelope),
        ) as client:
            with pytest.raises(UlticodeError, match="service_error"):
                await client.search_problems(
                    "Two Sum", page=1, limit=10, on_payload=seen.append
                )

    asyncio.run(scenario())
    # The invalid-but-successful page was handed over once; the failed envelope
    # never was.
    assert [page["results"] for page in seen] == [
        [{"id": "1", "title": "u02-canary-a"}]
    ]


PLAN_ID = "11111111-2222-3333-4444-555555555555"
SUBMISSION_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"


def test_learning_plan_wire_contract_uses_cookie_csrf_and_idempotency_without_refresh():
    seen = []

    async def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/auth/login":
            return httpx.Response(
                200,
                json={"code": 0, "message": "success", "data": {}},
                headers=[
                    ("set-cookie", "access_token=access; Path=/; HttpOnly"),
                    ("set-cookie", "refresh_token=refresh; Path=/; HttpOnly"),
                    ("set-cookie", "csrf_token=csrf; Path=/"),
                ],
            )
        seen.append(request)
        return httpx.Response(
            200,
            json={"code": 0, "message": "success", "data": {"id": PLAN_ID}},
        )

    async def scenario():
        async with UlticodeClient(
            "https://app.test",
            "https://auth.test",
            transport=httpx.MockTransport(handler),
        ) as client:
            await client.login("test", "dummy")
            saved = await client.save_learning_plan(
                source_submission_id=SUBMISSION_ID,
                draft_version=2,
                title="😀" * 200,
                content="课" * 16000,
                idempotency_key=PLAN_ID,
            )
            by_id = await client.get_learning_plan(PLAN_ID)
            by_key = await client.get_learning_plan_by_key(PLAN_ID)
        assert saved == by_id == by_key == {"id": PLAN_ID}

    asyncio.run(scenario())
    post, get_id, get_key = seen
    assert (post.method, post.url.path) == ("POST", "/learning-plans")
    assert json.loads(post.content) == {
        "sourceSubmissionId": SUBMISSION_ID,
        "draftVersion": 2,
        "title": "😀" * 200,
        "content": "课" * 16000,
    }
    assert post.headers["cookie"] == "access_token=access; csrf_token=csrf"
    assert post.headers["x-csrf-token"] == "csrf"
    assert post.headers["idempotency-key"] == PLAN_ID
    assert "refresh_token" not in post.headers["cookie"]
    for request, path in ((get_id, f"/learning-plans/{PLAN_ID}"), (get_key, f"/learning-plans/by-key/{PLAN_ID}")):
        assert (request.method, request.url.path) == ("GET", path)
        assert request.headers["cookie"] == "access_token=access"
        assert "csrf_token" not in request.headers["cookie"]
        assert "x-csrf-token" not in request.headers


@pytest.mark.parametrize(
    "field,value",
    [
        ("source_submission_id", "1-1-1-1-1"),
        ("draft_version", True),
        ("draft_version", 0),
        ("draft_version", 2_147_483_648),
        ("title", "\u00a0"),
        ("title", "😀" * 201),
        ("content", "x" * 16001),
    ],
)
def test_learning_plan_invalid_payload_is_rejected_before_http(field, value):
    requests = []

    async def handler(request):
        requests.append(request)
        raise AssertionError("invalid DTO must not issue HTTP")

    async def scenario():
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            payload = {
                "source_submission_id": SUBMISSION_ID,
                "draft_version": 1,
                "title": "Plan",
                "content": "Content",
            }
            payload[field] = value
            with pytest.raises(ValueError):
                await client.save_learning_plan(**payload, idempotency_key=PLAN_ID)

    asyncio.run(scenario())
    assert requests == []


@pytest.mark.parametrize(
    "duplicate_cookie,error",
    [("access_token", "session_required"), ("csrf_token", "csrf_token_required")],
)
def test_learning_plan_write_rejects_duplicate_session_cookies_before_http(duplicate_cookie, error):
    requests = []

    async def handler(request):
        requests.append(request)
        raise AssertionError("ambiguous cookies must not issue HTTP")

    async def scenario():
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            client._cookies.set("access_token", "access", domain="app.test", path="/")
            client._cookies.set("csrf_token", "csrf", domain="app.test", path="/")
            client._cookies.set(
                duplicate_cookie, "duplicate", domain="app.test", path="/learning-plans"
            )
            with pytest.raises(UlticodeError, match=error):
                await client.save_learning_plan(
                    source_submission_id=SUBMISSION_ID,
                    draft_version=1,
                    title="Plan",
                    content="Content",
                    idempotency_key=PLAN_ID,
                )

    asyncio.run(scenario())
    assert requests == []


@pytest.mark.parametrize(
    "status,code",
    [(400, 40000), (401, 40100), (403, 40300), (404, 40400), (409, 40900)],
)
def test_learning_plan_result_codes_survive_without_server_message(status, code):
    requests = []

    async def handler(request):
        if request.url.path == "/auth/login":
            return httpx.Response(
                200,
                json={"code": 0, "message": "success", "data": {}},
                headers=[("set-cookie", "access_token=access; Path=/; HttpOnly"),
                         ("set-cookie", "csrf_token=csrf; Path=/")],
            )
        requests.append(request)
        return httpx.Response(
            status,
            json={"code": code, "message": "SECRET service detail", "data": {"private": True}},
        )

    async def scenario():
        async with UlticodeClient(
            "https://app.test",
            "https://auth.test",
            transport=httpx.MockTransport(handler),
        ) as client:
            await client.login("test", "dummy")
            with pytest.raises(UlticodeServiceError) as error:
                await client.save_learning_plan(
                    source_submission_id=SUBMISSION_ID,
                    draft_version=1,
                    title="Plan",
                    content="Content",
                    idempotency_key=PLAN_ID,
                )
            assert error.value.code == code
            assert str(error.value) == "service_error"
            assert "SECRET" not in str(error.value)

    asyncio.run(scenario())


def test_for_session_principal_requires_active_verified_user():
    seen = []

    async def handler(request):
        seen.append((request.url.path, request.headers.get("cookie")))
        return httpx.Response(200, json={"code": 0, "message": "success", "data": {
            "user": {"id": "user-1", "is_active": True, "is_banned": False}
        }})

    async def scenario():
        client = UlticodeClient.for_session(
            "https://app.test", "https://auth.test", access_token="access",
            transport=httpx.MockTransport(handler),
        )
        async with client:
            assert await client.principal() == "user-1"

    asyncio.run(scenario())
    assert seen == [("/auth/me", "access_token=access")]


@pytest.mark.parametrize("token", ["", "bad token", "a;b", "x=y"])
def test_for_session_rejects_unsafe_cookie_values(token):
    with pytest.raises(ValueError, match="invalid session cookie"):
        UlticodeClient.for_session("https://app.test", "https://auth.test", access_token=token)


def test_for_session_rejects_legacy_identity_and_inactive_user():
    seen = []

    async def handler(request):
        seen.append(request.url.path)
        return httpx.Response(200, json={"code": 0, "message": "success", "data": {"id": "legacy"}})

    async def scenario():
        client = UlticodeClient.for_session(
            "https://app.test", "https://auth.test", access_token="access",
            transport=httpx.MockTransport(handler),
        )
        async with client:
            with pytest.raises(UlticodeError, match="identity_unreadable"):
                await client.principal()

    asyncio.run(scenario())
    assert seen == ["/auth/me"]


def test_canonical_uuid_accepts_non_versioned_and_normalizes_case():
    value = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
    assert canonical_uuid(value) == value.lower()
