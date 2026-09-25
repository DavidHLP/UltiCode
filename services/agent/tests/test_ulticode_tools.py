import asyncio

import httpx

from ulticode_client import UlticodeClient
from ulticode_tools import build_tools


def test_submission_projection_excludes_source_and_identity_fields() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "sub-1",
                            "problemId": 7,
                            "userId": "u-secret",
                            "language": "java",
                            "code": "SECRET SOURCE",
                            "status": "AC",
                            "input": "stdin",
                            "errorDetail": "boom",
                            "user": {"username": "tester"},
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 1,
                    "page": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            tools = build_tools(client)
            result = await tools["get_my_submissions"]({})

        item = result["items"][0]  # type: ignore[index]
        assert set(item) == {"id", "problemId", "language", "status", "createdAt"}
        assert "SECRET" not in repr(result)
        assert "u-secret" not in repr(result)

    asyncio.run(scenario())


def test_problem_scoped_submission_query_uses_authenticated_problem_scoped_endpoint() -> None:
    seen: dict[str, str | None] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/auth/login":
            return httpx.Response(
                200,
                json={"code": 0, "message": "success", "data": {}},
                headers=[("set-cookie", "access_token=access; Path=/; HttpOnly")],
            )
        seen["path"] = request.url.path
        seen["cookie"] = request.headers.get("cookie")
        seen["page"] = request.url.params.get("page")
        seen["page_size"] = request.url.params.get("pageSize")
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "sub-1",
                            "language": "java",
                            "status": "Wrong Answer",
                            "createdAt": "2026-09-25T00:00:00",
                            "problem": {"id": 7, "title": "Sample", "slug": "sample"},
                        }
                    ],
                    "total": 1,
                    "page": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            await client.login("tester", "pw")
            tools = build_tools(client)
            result = await tools["get_problem_submissions"](
                {"problemId": 7, "page": 2, "pageSize": 50}
            )

        assert seen == {
            "path": "/problems/7/submissions",
            "cookie": "access_token=access",
            "page": "2",
            "page_size": "50",
        }
        assert result["items"][0]["problemId"] == 7  # type: ignore[index]
        assert set(result["items"][0]) == {"id", "problemId", "language", "status", "createdAt"}

    asyncio.run(scenario())


def test_problem_scoped_submission_query_accepts_missing_problem_identity() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "sub-1",
                            "language": "java",
                            "status": "Wrong Answer",
                            "createdAt": "2026-09-25T00:00:00",
                        }
                    ],
                    "total": 1,
                    "page": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            result = await build_tools(client)["get_problem_submissions"]({"problemId": 7})
        assert result["items"][0]["problemId"] == 7  # type: ignore[index]

    asyncio.run(scenario())


def test_problem_projection_keeps_public_summary_fields_only() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": 7,
                    "slug": "sample",
                    "title": "Sample problem",
                    "difficulty": "medium",
                    "submission_count": 12,
                    "detail": {"content": "long statement"},
                    "viewer": {"some": "personal state"},
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            tools = build_tools(client)
            result = await tools["get_problem"]({"id": 7})

        assert set(result) == {"id", "slug", "title", "difficulty", "submission_count"}
        assert result["submission_count"] == 12
        assert "long statement" not in repr(result)

    asyncio.run(scenario())
