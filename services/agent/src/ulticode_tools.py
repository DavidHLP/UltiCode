"""Read-only tool bindings for the agent loop.

Two deliberate boundaries:

- Identity stays server-side: handlers call the authenticated client and
  accept no user id argument, so model output cannot redirect a query to
  another account.
- Field projection: submission source code, test payloads, error details and
  nested user/problem objects never enter the model's context — only the
  whitelisted fields below are returned.
"""

from __future__ import annotations

from ulticode_client import UlticodeClient

PROBLEM_FIELDS = {
    "id": (int, 9_223_372_036_854_775_807),
    "slug": (str, 256),
    "title": (str, 512),
    "difficulty": (str, 64),
    "submission_count": (int, 9_223_372_036_854_775_807),
}
SUBMISSION_FIELDS = {
    "id": (str, 40),
    "problemId": (int, 9_223_372_036_854_775_807),
    "language": (str, 64),
    "status": (str, 64),
    "createdAt": (str, 64),
}

# Argument contract shown to the model — names here must match the handlers below.
TOOL_SPECS = {
    "get_problem": 'args: {"id": <int problem id>}; returns id/slug/title/difficulty/submission_count',
    "get_my_submissions": 'args: optional {"page": <int>, "pageSize": <int>}; returns my submissions (id/problemId/language/status/createdAt) with totals — never source code',
    "get_problem_submissions": 'args: {"problemId": <int>, optional "page": <int>, "pageSize": <int>}; returns my submissions for one problem — never source code',
}


MAX_LONG = 9_223_372_036_854_775_807
MAX_INT = 2_147_483_647


def _project(data: object, fields: dict[str, tuple[type, int]]) -> dict[str, object]:
    if not isinstance(data, dict) or not fields.keys() <= data.keys():
        raise ValueError("invalid tool response")
    projected: dict[str, object] = {}
    for field, (expected_type, max_value) in fields.items():
        value = data[field]
        minimum = 1 if field in {"id", "problemId"} else 0
        if expected_type is int and (
            isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= max_value
        ):
            raise ValueError("invalid tool response")
        if expected_type is str and (
            not isinstance(value, str) or not value.strip() or len(value) > max_value
        ):
            raise ValueError("invalid tool response")
        projected[field] = value
    return projected


def _bounded_int(value: object, *, minimum: int = 0, maximum: int = MAX_LONG) -> int:
    if (
        isinstance(value, bool)
        or not isinstance(value, int)
        or value < minimum
        or value > maximum
    ):
        raise ValueError("invalid tool response")
    return value


def _positive_int(value: object, *, maximum: int = MAX_LONG) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not 1 <= value <= maximum:
        raise ValueError("invalid tool arguments")
    return value


def _page_int(value: object) -> int:
    return _positive_int(value, maximum=MAX_INT)


def _exact_keys(arguments: dict[str, object], allowed: set[str], *, required: set[str]) -> None:
    if not required <= arguments.keys() or arguments.keys() - allowed:
        raise ValueError("invalid tool arguments")



def build_tools(client: UlticodeClient) -> dict[str, object]:
    async def get_problem(arguments: dict[str, object]) -> object:
        _exact_keys(arguments, {"id"}, required={"id"})
        problem_id = _positive_int(arguments["id"])
        detail = await client.get_problem(problem_id)
        projected = _project(detail, PROBLEM_FIELDS)
        if projected["id"] != problem_id:
            raise ValueError("invalid tool response")
        return projected

    def project_submission_listing(
        listing: object, *, page_size: int
    ) -> dict[str, object]:
        if not isinstance(listing, dict) or not isinstance(listing.get("items"), list):
            raise ValueError("invalid tool response")
        if len(listing["items"]) > page_size:
            raise ValueError("invalid tool response")
        items = [_project(item, SUBMISSION_FIELDS) for item in listing["items"]]
        return {
            "items": items,
            "total": _bounded_int(listing.get("total")),
            "page": _bounded_int(listing.get("page"), minimum=1, maximum=MAX_INT),
        }

    def project_problem_submission_listing(
        listing: object, *, page_size: int, problem_id: int
    ) -> dict[str, object]:
        if not isinstance(listing, dict) or not isinstance(listing.get("items"), list):
            raise ValueError("invalid tool response")
        if len(listing["items"]) > page_size:
            raise ValueError("invalid tool response")
        items: list[dict[str, object]] = []
        for raw_item in listing["items"]:
            if not isinstance(raw_item, dict):
                raise ValueError("invalid tool response")
            problem = raw_item.get("problem")
            nested_id = problem.get("id") if isinstance(problem, dict) else None
            raw_problem_id = raw_item.get("problemId")
            if nested_id is not None and (
                isinstance(nested_id, bool)
                or not isinstance(nested_id, int)
                or nested_id != problem_id
            ):
                raise ValueError("invalid tool response")
            if raw_problem_id is not None and (
                isinstance(raw_problem_id, bool)
                or not isinstance(raw_problem_id, int)
                or raw_problem_id != problem_id
            ):
                raise ValueError("invalid tool response")
            normalized_item = dict(raw_item)
            normalized_item["problemId"] = problem_id
            items.append(_project(normalized_item, SUBMISSION_FIELDS))
        return {
            "items": items,
            "total": _bounded_int(listing.get("total")),
            "page": _bounded_int(listing.get("page"), minimum=1, maximum=MAX_INT),
        }

    async def get_my_submissions(arguments: dict[str, object]) -> object:
        _exact_keys(arguments, {"page", "pageSize"}, required=set())
        page = _page_int(arguments.get("page", 1))
        page_size = _positive_int(arguments.get("pageSize", 5), maximum=100)
        listing = await client.list_my_submissions(page=page, page_size=page_size)
        return project_submission_listing(listing, page_size=page_size)

    async def get_problem_submissions(arguments: dict[str, object]) -> object:
        _exact_keys(arguments, {"problemId", "page", "pageSize"}, required={"problemId"})
        problem_id = _positive_int(arguments["problemId"])
        page = _page_int(arguments.get("page", 1))
        page_size = _positive_int(arguments.get("pageSize", 5), maximum=100)
        listing = await client.list_problem_submissions(
            problem_id, page=page, page_size=page_size
        )
        return project_problem_submission_listing(
            listing, page_size=page_size, problem_id=problem_id
        )

    return {
        "get_problem": get_problem,
        "get_my_submissions": get_my_submissions,
        "get_problem_submissions": get_problem_submissions,
    }
