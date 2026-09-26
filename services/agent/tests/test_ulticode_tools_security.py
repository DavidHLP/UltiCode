import asyncio

import httpx
import pytest

from ulticode_client import UlticodeClient
from ulticode_tools import build_tools


@pytest.mark.parametrize(
    "bad_value",
    [
        {"nested": "SECRET"},
        ["SECRET"],
        "x" * 513,
    ],
)
def test_problem_projection_rejects_non_scalar_or_oversized_values(
    bad_value: object,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": 7,
                    "slug": "sample",
                    "title": bad_value,
                    "difficulty": "MEDIUM",
                    "submission_count": 12,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem"]({"id": 7})

    asyncio.run(scenario())


def test_submission_projection_rejects_nested_allowlisted_value() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": {"nested": "SECRET"},
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 1,
                    "page": 1,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())
def test_submission_projection_rejects_unknown_status() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "java",
                            "status": "AC",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 1,
                    "page": 1,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())


@pytest.mark.parametrize("created_at", ["2026-99-99T99:99:99", "2026-09-25T99:00:00", "2026-09-25T00:00:00Z"])
def test_submission_projection_rejects_invalid_created_at(created_at: str) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": created_at,
                        }
                    ],
                    "total": 1,
                    "page": 1,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())


def test_submission_listing_rejects_nested_totals() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"code": 0, "message": "success", "data": {"items": [], "total": {"x": 1}, "page": 1, "pageSize": 5}},
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())


def test_submission_listing_rejects_total_smaller_than_items() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 0,
                    "page": 1,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())
def test_submission_projection_rejects_malformed_id() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "not-a-uuid",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 1,
                    "page": 1,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())
def test_submission_projection_rejects_language_outside_contract() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "JAVA",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 1,
                    "page": 1,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())

def test_problem_projection_rejects_unknown_difficulty() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": 7,
                    "slug": "sample",
                    "title": "Sample",
                    "difficulty": "IMPOSSIBLE",
                    "submission_count": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem"]({"id": 7})

    asyncio.run(scenario())
@pytest.mark.parametrize("difficulty", ["medium", "Medium", "easy "])
def test_problem_projection_rejects_noncanonical_difficulty_casing(
    difficulty: str,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": 7,
                    "slug": "sample",
                    "title": "Sample",
                    "difficulty": difficulty,
                    "submission_count": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem"]({"id": 7})

    asyncio.run(scenario())
@pytest.mark.parametrize(
    "field,value",
    [
        ("slug", "../admin"),
        ("slug", "Sample-Problem"),
        ("slug", "a" * 121),
        ("title", "t" * 256),
    ],
)
def test_problem_projection_rejects_slug_and_title_outside_owner_contract(
    field: str, value: str
) -> None:
    detail = {
        "id": 7,
        "slug": "sample",
        "title": "Sample",
        "difficulty": "EASY",
        "submission_count": 1,
    }

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"code": 0, "message": "success", "data": {**detail, field: value}},
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem"]({"id": 7})

    asyncio.run(scenario())




def test_submission_listing_rejects_total_below_page_offset() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 1,
                    "page": 2,
                    "pageSize": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({"page": 2, "pageSize": 1})

    asyncio.run(scenario())
def test_problem_scoped_listing_rejects_total_below_page_offset() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                            "problem": {"id": 7, "title": "Sample", "slug": "sample"},
                        }
                    ],
                    "total": 1,
                    "page": 2,
                    "pageSize": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem_submissions"](
                    {"problemId": 7, "page": 2, "pageSize": 1}
                )

    asyncio.run(scenario())
@pytest.mark.parametrize("tool_name,arguments", [
    ("get_my_submissions", {}),
    ("get_problem_submissions", {"problemId": 7}),
])
def test_submission_listing_rejects_empty_page_before_total(
    tool_name: str, arguments: dict[str, object]
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"items": [], "total": 1, "page": 1, "pageSize": 5},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)[tool_name](arguments)

    asyncio.run(scenario())
@pytest.mark.parametrize("tool_name,arguments", [
    ("get_my_submissions", {"page": 2}),
    ("get_problem_submissions", {"problemId": 7, "page": 2}),
])
def test_submission_listing_accepts_empty_page_beyond_total(
    tool_name: str, arguments: dict[str, object]
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"items": [], "total": 0, "page": 2, "pageSize": 5},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            result = await build_tools(client)[tool_name](arguments)

        assert result == {"items": [], "total": 0, "page": 2, "pageSize": 5}

    asyncio.run(scenario())
@pytest.mark.parametrize("tool_name,arguments", [
    ("get_my_submissions", {}),
    ("get_problem_submissions", {"problemId": 7}),
])
def test_submission_listing_rejects_underfilled_non_final_page(
    tool_name: str, arguments: dict[str, object]
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 100,
                    "page": 1,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)[tool_name](arguments)

    asyncio.run(scenario())


@pytest.mark.parametrize("tool_name,arguments", [
    ("get_my_submissions", {"page": 2}),
    ("get_problem_submissions", {"problemId": 7, "page": 2}),
])
def test_submission_listing_accepts_underfilled_final_page(
    tool_name: str, arguments: dict[str, object]
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 6,
                    "page": 2,
                    "pageSize": 5,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            result = await build_tools(client)[tool_name](arguments)

        assert result["items"] and result["total"] == 6

    asyncio.run(scenario())









def test_submission_listing_rejects_stale_page_size() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 6,
                    "page": 1,
                    "pageSize": 4,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({"pageSize": 5})

    asyncio.run(scenario())


def test_problem_scoped_listing_rejects_stale_page_size() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                            "problem": {"id": 7, "title": "Sample", "slug": "sample"},
                        }
                    ],
                    "total": 6,
                    "page": 1,
                    "pageSize": 4,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem_submissions"](
                    {"problemId": 7, "pageSize": 5}
                )

    asyncio.run(scenario())


def test_submission_listing_rejects_more_items_than_page_size() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "id": f"11111111-1111-4111-8111-1111111111{index:02d}",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                        for index in range(2)
                    ],
                    "total": 2,
                    "page": 1,
                    "pageSize": 1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({"pageSize": 1})

    asyncio.run(scenario())


@pytest.mark.parametrize(
    "arguments",
    [
        {},
        {"problemId": 0},
        {"problemId": -1},
        {"problemId": True},
        {"problemId": 9_223_372_036_854_775_808},
        {"problemId": 7, "page": 2_147_483_648},
        {"problemId": 7, "pageSize": 101},
        {"problemId": 7, "userId": "other-user"},
        {"problemId": 7, "unexpected": True},
    ],
)
def test_get_problem_submissions_rejects_invalid_model_arguments(
    arguments: dict[str, object],
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request should be sent for invalid arguments")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool arguments"):
                await build_tools(client)["get_problem_submissions"](arguments)

    asyncio.run(scenario())


@pytest.mark.parametrize(
    "item",
    [
        {"id": "11111111-1111-4111-8111-111111111111", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problem": {"id": 99}},
        {"id": "11111111-1111-4111-8111-111111111111", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problem": {"id": True}},
        {"id": "11111111-1111-4111-8111-111111111111", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problemId": 99},
        {"id": "11111111-1111-4111-8111-111111111111", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problemId": True},
    ],
)
def test_get_problem_submissions_rejects_mismatched_or_missing_problem_identity(
    item: dict[str, object],
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"items": [item], "total": 1, "page": 1, "pageSize": 5},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem_submissions"]({"problemId": 7})

    asyncio.run(scenario())


def test_problem_projection_accepts_64_bit_integer() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": 2_147_483_648,
                    "slug": "sample",
                    "title": "Sample",
                    "difficulty": "MEDIUM",
                    "submission_count": 9_223_372_036_854_775_807,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            result = await build_tools(client)["get_problem"]({"id": 2_147_483_648})
        assert result["id"] == 2_147_483_648
        assert result["submission_count"] == 9_223_372_036_854_775_807

    asyncio.run(scenario())


def test_problem_projection_rejects_integer_beyond_64_bit() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": 7,
                    "slug": "sample",
                    "title": "Sample",
                    "difficulty": "MEDIUM",
                    "submission_count": 9_223_372_036_854_775_808,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem"]({"id": 7})

    asyncio.run(scenario())


@pytest.mark.parametrize(
    "arguments",
    [
        {},
        {"id": True},
        {"id": 7.9},
        {"id": "7"},
        {"id": 0},
        {"id": -1},
    ],
)
def test_get_problem_rejects_invalid_model_arguments(arguments: dict[str, object]) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request should be sent for invalid arguments")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool arguments"):
                await build_tools(client)["get_problem"](arguments)

    asyncio.run(scenario())

@pytest.mark.parametrize(
    "arguments",
    [
        {"page": True},
        {"page": 0},
        {"page": -1},
        {"page": 2_147_483_648},
        {"pageSize": True},
        {"pageSize": 0},
        {"pageSize": -1},
        {"pageSize": 101},
    ],
)
def test_get_my_submissions_rejects_invalid_pagination(arguments: dict[str, object]) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request should be sent for invalid arguments")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool arguments"):
                await build_tools(client)["get_my_submissions"](arguments)

    asyncio.run(scenario())
def test_submission_page_max_integer_is_accepted() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {"items": [], "total": 0, "page": 2_147_483_647, "pageSize": 5},
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            tools = build_tools(client)
            global_result = await tools["get_my_submissions"]({"page": 2_147_483_647})
            scoped_result = await tools["get_problem_submissions"](
                {"problemId": 7, "page": 2_147_483_647}
            )
        assert global_result["page"] == 2_147_483_647
        assert scoped_result["page"] == 2_147_483_647

    asyncio.run(scenario())


def test_submission_page_response_rejects_above_integer_max() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"code": 0, "message": "success", "data": {"items": [], "total": 0, "page": 2_147_483_648, "pageSize": 5}},
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())



@pytest.mark.parametrize(
    "arguments",
    [{"id": 7, "userId": "other-user"}, {"id": 7, "unexpected": True}],
)
def test_get_problem_rejects_extra_model_arguments(arguments: dict[str, object]) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request should be sent for extra arguments")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool arguments"):
                await build_tools(client)["get_problem"](arguments)

    asyncio.run(scenario())


@pytest.mark.parametrize("arguments", [{"page": 1, "userId": "other-user"}, {"page": 1, "unexpected": True}])
def test_get_my_submissions_rejects_extra_model_arguments(arguments: dict[str, object]) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("no request should be sent for extra arguments")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool arguments"):
                await build_tools(client)["get_my_submissions"](arguments)

    asyncio.run(scenario())


def test_problem_projection_requires_all_fields_and_rejects_negative_values() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": 7,
                    "slug": "sample",
                    "title": "Sample",
                    "difficulty": "MEDIUM",
                    "submission_count": -1,
                },
            },
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem"]({"id": 7})

    asyncio.run(scenario())


def test_problem_projection_rejects_missing_fields() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"code": 0, "message": "success", "data": {"id": 7}},
        )

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_problem"]({"id": 7})

    asyncio.run(scenario())
