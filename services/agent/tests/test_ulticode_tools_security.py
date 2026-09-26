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
                    "difficulty": "medium",
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
                            "id": "sub-1",
                            "problemId": 7,
                            "language": {"nested": "SECRET"},
                            "status": "Accepted",
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
                            "id": "sub-1",
                            "problemId": 7,
                            "language": "java",
                            "status": "AC",
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
            with pytest.raises(ValueError, match="invalid tool response"):
                await build_tools(client)["get_my_submissions"]({})

    asyncio.run(scenario())




def test_submission_listing_rejects_nested_totals() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"code": 0, "message": "success", "data": {"items": [], "total": {"x": 1}, "page": 1}},
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
                            "id": "sub-1",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                    ],
                    "total": 0,
                    "page": 1,
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
                            "id": f"sub-{index}",
                            "problemId": 7,
                            "language": "java",
                            "status": "Accepted",
                            "createdAt": "2026-09-24T00:00:00",
                        }
                        for index in range(2)
                    ],
                    "total": 2,
                    "page": 1,
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
        {"id": "sub-1", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problem": {"id": 99}},
        {"id": "sub-1", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problem": {"id": True}},
        {"id": "sub-1", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problemId": 99},
        {"id": "sub-1", "language": "java", "status": "Wrong Answer", "createdAt": "2026-09-25T00:00:00", "problemId": True},
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
                "data": {"items": [item], "total": 1, "page": 1},
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
                    "difficulty": "medium",
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
                    "difficulty": "medium",
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
            json={"code": 0, "message": "success", "data": {"items": [], "total": 0, "page": 2_147_483_647}},
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
            json={"code": 0, "message": "success", "data": {"items": [], "total": 0, "page": 2_147_483_648}},
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
                    "difficulty": "medium",
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
