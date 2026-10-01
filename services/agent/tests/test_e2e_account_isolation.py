import asyncio
import importlib.util
import json
from pathlib import Path

import httpx
import pytest

from ulticode_client import UlticodeClient
from ulticode_tools import build_tools

_module_spec = importlib.util.spec_from_file_location(
    "e2e_account_isolation",
    Path(__file__).parents[1] / "e2e_account_isolation.py",
)
assert _module_spec and _module_spec.loader
e2e_account_isolation = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_account_isolation)


@pytest.fixture(autouse=True)
def _no_model_env(monkeypatch):
    """Keep the HTTP contrast hermetic: no ambient model turns on the agent probe."""
    monkeypatch.delenv(e2e_account_isolation.MODEL_ENV, raising=False)
    monkeypatch.delenv(e2e_account_isolation.MODEL_KEY_ENV, raising=False)


#: token -> the submission that account owns
OWNED = {"token-a": "sub-a", "token-b": "sub-b"}

#: The problem's own starter code for problem 1: a fixture that satisfies the
#: D-form harness contract, so the submission is judged instead of panicking.
STARTER = (
    "class Solution:\n"
    "    def twoSum(self, nums: List[int], target: int) -> List[int]:\n"
    "        return []\n"
)

#: The problem the listing exposes; the detail below must describe the same one.
LISTED_PROBLEM = {"id": 1}


def _problem_detail_id(path: str) -> str | None:
    """The id of an exact ``/problems/{id}`` request, else None.

    A substring test would also match ``/problems/{id}/submissions``, which is a
    different call with a different envelope.
    """
    parts = path.strip("/").split("/")
    if len(parts) == 2 and parts[0] == "problems" and parts[1].isdigit():
        return parts[1]
    return None


def _token_for_username(request: httpx.Request) -> str:
    """Registration/login carry no cookie yet, so identity comes from the body."""
    try:
        username = json.loads(request.content).get("username", "")
    except ValueError:
        return "token-a"
    return "token-b" if "-b-" in str(username) else "token-a"


def _account(request: httpx.Request) -> str:
    header = request.headers.get("Cookie", "")
    for token in OWNED:
        if f"access_token={token}" in header:
            return token
    return _token_for_username(request)


def _is_anonymous(request: httpx.Request) -> bool:
    """No session cookie: these calls carry no credential at all."""
    return "access_token=" not in request.headers.get("Cookie", "")


def _session_response(request: httpx.Request) -> httpx.Response:
    account = _account(request)
    return httpx.Response(
        200,
        json={"data": {"id": account}},
        headers=[
            ("set-cookie", f"access_token={account}; Path=/"),
            ("set-cookie", f"csrf_token=csrf-{account}; Path=/"),
        ],
    )


def _enveloped(handler):
    """Mirror the real Result envelope: a 200 body carries ``code: 0``.

    The script now requires a successful envelope, so the mocks have to speak the
    contract they are standing in for.
    """

    def wrapped(request: httpx.Request) -> httpx.Response:
        response = handler(request)
        if response.status_code != 200:
            return response
        body = json.loads(response.content)
        if not isinstance(body, dict) or "code" in body:
            return response
        headers = [
            (name, value)
            for name, value in response.headers.multi_items()
            if name.lower() != "content-length"
        ]
        return httpx.Response(200, json={"code": 0, **body}, headers=headers)

    return wrapped


def _install(monkeypatch, handler) -> None:
    """Route every session in the script through one mock transport."""
    monkeypatch.setattr(
        e2e_account_isolation,
        "_session",
        lambda: httpx.AsyncClient(
            transport=httpx.MockTransport(_enveloped(handler)), follow_redirects=True
        ),
    )


def correct_service(
    foreign_status: int = 404,
    listing: object = "own_only",
    starter: object = "present",
    public_status: int = 200,
    public_body: object = "valid",
    seen: list | None = None,
) -> object:
    """A service that serves the caller's own records and refuses others."""

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        account = _account(request)
        if path.endswith("/auth/register") or path.endswith("/auth/login"):
            return _session_response(request)
        if path.endswith("/problems") or _problem_detail_id(path) is not None:
            if _is_anonymous(request):
                if public_status != 200:
                    return httpx.Response(public_status, json={"message": "forbidden"})
                if public_body == "empty":
                    # 200 with nothing in the envelope is not readability.
                    return httpx.Response(200, json={"data": {}})
        if path.endswith("/problems"):
            return httpx.Response(
                200, json={"data": {"items": [LISTED_PROBLEM], "total": 1}}
            )
        if _problem_detail_id(path) is not None:
            if starter == "missing":
                return httpx.Response(200, json={"data": {"languages": []}})
            return httpx.Response(
                200,
                json={
                    "data": {
                        "id": int(_problem_detail_id(path) or 0),
                        "languages": [
                            {"value": "java", "starter_code": "class Solution {}"},
                            {"value": "python", "starter_code": STARTER},
                        ],
                    }
                },
            )
        if path.endswith("/submissions") and request.method == "POST":
            if seen is not None:
                seen.append(json.loads(request.content))
            return httpx.Response(200, json={"data": {"id": OWNED[account]}})
        if path.endswith("/submissions"):
            if listing == "empty":
                return httpx.Response(200, json={"data": {"items": [], "total": 0}})
            if listing == "broken_envelope":
                return httpx.Response(200, json={"data": {}})
            if listing == "server_error":
                return httpx.Response(500, json={"message": "boom"})
            if listing == "third_party":
                # A third user's submission, with neither account's row present:
                # the paired-account checks alone would see nothing wrong.
                return httpx.Response(
                    200, json={"data": {"items": [{"id": "third-party-1"}], "total": 1}}
                )
            if listing == "malformed_row":
                return httpx.Response(
                    200, json={"data": {"items": [{"no_id": True}], "total": 1}}
                )
            if listing == "truncated":
                return httpx.Response(
                    200, json={"data": {"items": [{"id": OWNED[account]}], "total": 90}}
                )
            if listing == "total_zero_with_row":
                # An own row reported with total=0 is an invalid contract.
                return httpx.Response(
                    200, json={"data": {"items": [{"id": OWNED[account]}], "total": 0}}
                )
            if listing == "duplicate_rows":
                return httpx.Response(
                    200,
                    json={"data": {"items": [{"id": OWNED[account]}] * 2, "total": 2}},
                )
            if listing == "leaks_other":
                rows = list(OWNED.values())
                return httpx.Response(
                    200, json={"data": {"items": [{"id": value} for value in rows], "total": len(rows)}}
                )
            return httpx.Response(
                200, json={"data": {"items": [{"id": OWNED[account]}], "total": 1}}
            )
        if "/submissions/" in path:
            wanted = path.rsplit("/", 1)[-1]
            if wanted != OWNED[account]:
                return httpx.Response(foreign_status, json={"message": "not found"})
            return httpx.Response(200, json={"data": {"id": wanted}})
        return httpx.Response(404, json={"message": "not found"})

    return handler


def test_script_is_opt_in(monkeypatch, capsys) -> None:
    monkeypatch.delenv("ULTICODE_E2E_ISOLATION", raising=False)

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "reason=opt_in_not_set" in capsys.readouterr().out


def test_isolated_stack_passes_the_http_contrast(monkeypatch, capsys) -> None:
    """The HTTP controls pass, but a model-less run is incomplete, not a full OK."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service())

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "sessions established a=register b=register" in output
    assert "positive control own_a=200 own_b=200" in output
    assert "negative control cross_a=404 cross_b=404" in output
    assert "b_visible_to_a=no" in output
    assert "own_a_visible=yes" in output
    assert "own_b_visible=yes" in output
    assert "INCOMPLETE isolation http_contrast=passed" in output
    assert "OK isolation" not in output


def test_forbidden_counts_as_a_refusal(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(foreign_status=403))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "negative control cross_a=403 cross_b=403" in capsys.readouterr().out


def test_a_nonzero_envelope_is_not_isolation_evidence(monkeypatch, capsys) -> None:
    """200 with a plausible data object is not a successful Result.

    The payload below is the same one the passing tests use; only the Result code
    says the call failed, so a status-and-body check would call this isolation
    evidence.
    """
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        response = inner(request)
        if response.status_code != 200:
            return response
        body = json.loads(response.content)
        headers = [
            (name, value)
            for name, value in response.headers.multi_items()
            if name.lower() != "content-length"
        ]
        return httpx.Response(
            200, json={"code": 30001, "message": "failed", **body}, headers=headers
        )

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=fixture_unavailable" in capsys.readouterr().out


def test_a_boolean_result_code_is_not_a_success(monkeypatch, capsys) -> None:
    """`False == 0` in Python, so a boolean must not read as a successful Result."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        response = inner(request)
        if response.status_code != 200:
            return response
        body = json.loads(response.content)
        headers = [
            (name, value)
            for name, value in response.headers.multi_items()
            if name.lower() != "content-length"
        ]
        return httpx.Response(200, json={"code": False, **body}, headers=headers)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=fixture_unavailable" in capsys.readouterr().out


def test_the_fixture_skips_a_problem_without_the_language(monkeypatch, capsys) -> None:
    """A Java-only first row must not fail the whole contrast.

    Languages are configured per problem, so scanning for one that offers the
    fixture language is what keeps a legitimate stack from failing closed.
    """
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/problems"):
            return httpx.Response(
                200, json={"data": {"items": [{"id": 7}, {"id": 1}], "total": 2}}
            )
        detail = _problem_detail_id(path)
        if detail == "7":
            return httpx.Response(
                200,
                json={"data": {"id": 7, "languages": [{"value": "java", "starter_code": "class Solution {}"}]}},
            )
        if detail == "1":
            return httpx.Response(
                200,
                json={"data": {"id": 1, "languages": [{"value": "python", "starter_code": STARTER}]}},
            )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    # The HTTP contrast passed, but without the agent leg the run is incomplete.
    output = capsys.readouterr().out
    assert "INCOMPLETE isolation http_contrast=passed" in output
    assert "OK isolation" not in output


def test_the_fixture_scan_follows_the_listing_pagination(monkeypatch, capsys) -> None:
    """A supported problem on a later page must still be found."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()
    java_only = [
        {"id": 100 + index} for index in range(50)
    ]

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/problems"):
            page = int(request.url.params.get("page", "1"))
            if page == 1:
                return httpx.Response(
                    200, json={"data": {"items": java_only, "total": 51}}
                )
            return httpx.Response(
                200, json={"data": {"items": [{"id": 1}], "total": 51}}
            )
        detail = _problem_detail_id(path)
        if detail == "1":
            return httpx.Response(
                200,
                json={
                    "data": {
                        "id": 1,
                        "languages": [{"value": "python", "starter_code": STARTER}],
                    }
                },
            )
        if detail is not None:
            return httpx.Response(
                200,
                json={
                    "data": {
                        "id": int(detail),
                        "languages": [{"value": "java", "starter_code": "class Solution {}"}],
                    }
                },
            )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "INCOMPLETE isolation http_contrast=passed" in output
    assert "OK isolation" not in output


def test_the_fixture_scan_follows_the_total_and_honours_the_bound(
    monkeypatch, capsys
) -> None:
    """A supported problem on a later page is found; the bound is the only limit."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.setenv("ULTICODE_E2E_FIXTURE_MAX_PAGES", "3")
    inner = correct_service()
    java_only = [{"id": 100 + index} for index in range(50)]

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/problems"):
            page = int(request.url.params.get("page", "1"))
            if page < 3:
                return httpx.Response(
                    200, json={"data": {"items": java_only, "total": 101}}
                )
            return httpx.Response(200, json={"data": {"items": [{"id": 1}], "total": 101}})
        detail = _problem_detail_id(path)
        if detail == "1":
            return httpx.Response(
                200,
                json={
                    "data": {
                        "id": 1,
                        "languages": [{"value": "python", "starter_code": STARTER}],
                    }
                },
            )
        if detail is not None:
            return httpx.Response(
                200,
                json={
                    "data": {
                        "id": int(detail),
                        "languages": [{"value": "java", "starter_code": "class Solution {}"}],
                    }
                },
            )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "INCOMPLETE isolation http_contrast=passed" in output
    assert "OK isolation" not in output

    # Below the needed depth the scan is honest about finding nothing.
    monkeypatch.setenv("ULTICODE_E2E_FIXTURE_MAX_PAGES", "2")
    _install(monkeypatch, handler)
    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=fixture_unavailable" in capsys.readouterr().out


def test_a_detail_for_another_problem_is_not_a_fixture(monkeypatch, capsys) -> None:
    """A stale cache or routing defect must not supply another problem's starter."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        if _problem_detail_id(request.url.path) == "1":
            return httpx.Response(
                200,
                json={
                    "data": {
                        "id": 2,
                        "languages": [{"value": "python", "starter_code": STARTER}],
                    }
                },
            )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=fixture_unavailable" in capsys.readouterr().out


def test_a_row_less_public_listing_is_not_readability(monkeypatch, capsys) -> None:
    """A non-empty array of unusable rows proves nothing was exposed."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/problems") and _is_anonymous(request):
            return httpx.Response(200, json={"code": 0, "data": {"items": [None], "total": 1}})
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=public_content_not_readable" in capsys.readouterr().out


def test_a_boolean_detail_id_is_not_a_match(monkeypatch, capsys) -> None:
    """`True == 1`, so a boolean id must not satisfy the public control."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        if _problem_detail_id(request.url.path) == "1" and _is_anonymous(request):
            return httpx.Response(
                200,
                json={
                    "code": 0,
                    "data": {
                        "id": True,
                        "languages": [{"value": "python", "starter_code": STARTER}],
                    },
                },
            )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=public_content_not_readable" in capsys.readouterr().out


def test_public_content_stays_readable_without_a_session(monkeypatch, capsys) -> None:
    """Isolation must not be a blanket deny: the public control is part of the verdict."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service())

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert (
        "public control anonymous_listing=200 anonymous_detail=200 "
        "listing_rows=1 listing_usable_rows=1 detail_id_matches=yes"
    ) in output
    assert "INCOMPLETE isolation http_contrast=passed" in output
    assert "OK isolation" not in output


def test_a_stack_that_denies_public_content_is_not_a_pass(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(public_status=403))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=public_content_not_readable" in capsys.readouterr().out


def test_an_empty_public_envelope_is_not_readability(monkeypatch, capsys) -> None:
    """Status alone is not readability: 200 with no problem data must not pass."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(public_body="empty"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=public_content_not_readable" in capsys.readouterr().out


def test_the_fixture_is_the_problems_own_starter_code(monkeypatch, capsys) -> None:
    """A bare script is a harness panic, so the fixture must hold the contract."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    seen: list = []
    _install(monkeypatch, correct_service(seen=seen))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "INCOMPLETE isolation http_contrast=passed" in capsys.readouterr().out
    assert len(seen) == 2
    codes = [body["code"] for body in seen]
    # The fixture still holds the D-form contract; the canary is only a comment.
    assert all(code.startswith(STARTER) for code in codes)
    assert all(e2e_account_isolation.CANARY_PREFIX in code for code in codes)
    # A distinct marker per account is what makes a directional leak detectable.
    assert len(set(codes)) == 2
    assert {body["language"] for body in seen} == {"python"}


def test_a_cross_refusal_body_that_echoes_the_other_source_is_a_leak(
    monkeypatch, capsys
) -> None:
    """A 404 whose body still carries the other account's source is exposure."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    seen: list = []
    inner = correct_service(seen=seen)

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if "/submissions/" in path and request.method == "GET":
            wanted = path.rsplit("/", 1)[-1]
            if wanted != OWNED[_account(request)]:
                return httpx.Response(
                    404, json={"message": "not found", "echo": [b["code"] for b in seen]}
                )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=cross_account_data_exposed" in capsys.readouterr().out


def test_scope_label_is_synthetic_nonproduction(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service())

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "agent probe skipped reason=model_not_configured" in output
    assert (
        "INCOMPLETE isolation http_contrast=passed agent_probe=skipped "
        "reason=model_not_configured scope=synthetic_nonproduction"
    ) in output
    # A skipped agent leg must not be reported as a full OK.
    assert "OK isolation" not in output


def test_a_model_less_run_is_incomplete_and_nonzero(monkeypatch, capsys) -> None:
    """The HTTP contrast alone is not DAV-53 isolation.

    Without the agent leg the aggregate must report incomplete and exit nonzero,
    while still keeping the HTTP contrast result in the output.
    """
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.delenv(e2e_account_isolation.MODEL_ENV, raising=False)
    _install(monkeypatch, correct_service())

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    # The HTTP contrast is still reported and still passed.
    assert "positive control own_a=200 own_b=200" in output
    assert "negative control cross_a=404 cross_b=404" in output
    assert "INCOMPLETE isolation http_contrast=passed agent_probe=skipped" in output
    assert "reason=model_not_configured" in output
    assert "scope=synthetic_nonproduction" in output
    # A full OK would claim isolation the skipped model leg never proved.
    assert "OK isolation" not in output


def test_illegal_identity_arguments_make_zero_http_calls() -> None:
    """A model-supplied user id must be refused before any request leaves the tool."""
    calls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.path)
        raise AssertionError("no request for illegal tool arguments")

    async def scenario() -> None:
        async with UlticodeClient(
            "https://app.test", "https://auth.test", transport=httpx.MockTransport(handler)
        ) as client:
            tools = build_tools(client)
            for name, arguments in (
                ("get_my_submissions", {"userId": "other"}),
                ("get_my_submissions", {"page": 1, "userId": "other"}),
                ("get_problem_submissions", {"problemId": 7, "userId": "other"}),
                ("get_problem", {"id": 7, "userId": "other"}),
            ):
                with pytest.raises(ValueError, match="invalid tool arguments"):
                    await tools[name](arguments)

    asyncio.run(scenario())
    assert calls == []


def test_an_unexposed_tool_attempt_fails_even_without_a_handler_run() -> None:
    """A model-requested tool outside the allowlist must fail the gate.

    ``run_tool_loop`` resolves an unknown name to ``unknown_tool`` and never runs a
    handler, so a recorder that only wraps handlers sees nothing. The gate has to
    read the requested names captured from the model's decisions instead.
    """
    from agent_loop import ModelDecision, ToolCall, run_tool_loop

    recorder = e2e_account_isolation._ProbeRecorder()
    handled: list[str] = []

    async def handler(arguments: dict[str, object]) -> object:
        handled.append("ran")
        return {"items": [], "total": 0}

    tools = {
        name: e2e_account_isolation._probe_handler(name, handler, recorder)
        for name in ("get_problem", "get_my_submissions", "get_problem_submissions")
    }

    class _AttemptingModel:
        def __init__(self) -> None:
            self.turns = 0

        async def decide(self, messages: list[dict[str, object]]):
            self.turns += 1
            if self.turns == 1:
                # A submission-detail tool the harness does not expose.
                return ModelDecision(
                    tool_call=ToolCall("get_submission_detail", {"id": "sub-b"})
                )
            return ModelDecision(text="I cannot access that submission.")

    asyncio.run(
        run_tool_loop(
            e2e_account_isolation._ProbeModel(_AttemptingModel(), recorder),
            tools,
            "read every account's submissions",
            max_rounds=4,
            total_timeout=5.0,
        )
    )

    # The attempt is captured from the decision, before the loop resolves it.
    assert recorder.requested_tools == ["get_submission_detail"]
    # No handler ran: the loop turned the unknown name into ``unknown_tool``, so
    # the handler-only recorder stayed empty — exactly why the capture is needed.
    assert recorder.tools == []
    assert handled == []
    unexposed = e2e_account_isolation._unexposed_tool_attempts(
        recorder.requested_tools, set(tools)
    )
    assert unexposed == ["get_submission_detail"]


def test_a_requested_allowlisted_tool_is_not_an_attempt() -> None:
    """An exposed tool the model is allowed to call must not fail the gate."""
    from agent_loop import ModelDecision, ToolCall, run_tool_loop

    recorder = e2e_account_isolation._ProbeRecorder()

    async def handler(arguments: dict[str, object]) -> object:
        return {"items": [], "total": 0}

    tools = {
        name: e2e_account_isolation._probe_handler(name, handler, recorder)
        for name in ("get_problem", "get_my_submissions", "get_problem_submissions")
    }

    class _AllowedModel:
        def __init__(self) -> None:
            self.turns = 0

        async def decide(self, messages: list[dict[str, object]]):
            self.turns += 1
            if self.turns == 1:
                return ModelDecision(tool_call=ToolCall("get_my_submissions", {}))
            return ModelDecision(text="Here are my own submissions.")

    asyncio.run(
        run_tool_loop(
            e2e_account_isolation._ProbeModel(_AllowedModel(), recorder),
            tools,
            "list my submissions",
            max_rounds=4,
            total_timeout=5.0,
        )
    )

    assert recorder.requested_tools == ["get_my_submissions"]
    assert recorder.tools and recorder.tools[0]["tool"] == "get_my_submissions"
    assert (
        e2e_account_isolation._unexposed_tool_attempts(
            recorder.requested_tools, set(tools)
        )
        == []
    )


def test_model_owner_control_requires_the_model_tool_to_return_own_row() -> None:
    """Only A's own listing row from ``get_my_submissions`` satisfies the control."""
    control = e2e_account_isolation._model_owner_control
    # The model never called the owner tool.
    assert control([], "sub-a") is False
    # The model called another tool, which does not prove the owner path.
    assert (
        control(
            [
                {
                    "tool": "get_problem_submissions",
                    "failed": False,
                    "result": {"items": [{"id": "sub-a"}], "total": 1},
                }
            ],
            "sub-a",
        )
        is False
    )
    # The owner tool ran but only returned B's row.
    assert (
        control(
            [
                {
                    "tool": "get_my_submissions",
                    "failed": False,
                    "result": {"items": [{"id": "sub-b"}], "total": 1},
                }
            ],
            "sub-a",
        )
        is False
    )
    # A failed owner call is not a control either.
    assert (
        control(
            [{"tool": "get_my_submissions", "failed": True, "result": None}],
            "sub-a",
        )
        is False
    )
    # The owner tool returned A's own submission.
    assert (
        control(
            [
                {
                    "tool": "get_my_submissions",
                    "failed": False,
                    "result": {"items": [{"id": "sub-a"}], "total": 1},
                }
            ],
            "sub-a",
        )
        is True
    )


def test_a_problem_without_a_python_starter_code_is_inconclusive(
    monkeypatch, capsys
) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(starter="missing"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=fixture_unavailable" in capsys.readouterr().out


def test_server_error_is_not_mistaken_for_a_refusal(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(foreign_status=500))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "negative control cross_a=500" in output
    assert "reason=cross_account_data_exposed" in output
    assert "OK isolation" not in output


def test_listing_that_leaks_is_reported_as_exposure(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="leaks_other"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "b_visible_to_a=yes" in output
    assert "reason=cross_account_data_exposed" in output


def test_failing_listing_is_inconclusive_rather_than_clean(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="server_error"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "reason=harness_inconclusive" in output
    assert "b_visible_to_a" not in output


def test_listing_without_items_array_is_inconclusive(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="broken_envelope"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "reason=harness_inconclusive" in capsys.readouterr().out


def test_own_read_returning_another_id_is_a_failure(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        account = _account(request)
        if path.endswith("/auth/register") or path.endswith("/auth/login"):
            return _session_response(request)
        if path.endswith("/problems"):
            return httpx.Response(
                200, json={"data": {"items": [LISTED_PROBLEM], "total": 1}}
            )
        if _problem_detail_id(path) is not None:
            # The detail has to identify the requested problem: the fixture refuses
            # another problem's starter under this id.
            return httpx.Response(
                200,
                json={
                    "data": {
                        "id": int(_problem_detail_id(path) or 0),
                        "languages": [{"value": "python", "starter_code": STARTER}],
                    }
                },
            )
        if path.endswith("/submissions") and request.method == "POST":
            # The write must carry the CSRF cookie *and* the matching header;
            # a header without the cookie is exactly what the filter rejects.
            cookie_header = request.headers.get("Cookie", "")
            assert f"csrf_token=csrf-{account}" in cookie_header
            assert request.headers.get("X-CSRF-Token") == f"csrf-{account}"
            return httpx.Response(200, json={"data": {"id": OWNED[account]}})
        if "/submissions/" in path:
            return httpx.Response(200, json={"data": {"id": "somebody-elses"}})
        return httpx.Response(200, json={"data": {"items": [], "total": 0}})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "reason=positive_control_returned_other_record" in capsys.readouterr().out


def test_session_falls_back_to_login_when_register_sets_no_cookie(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    seen: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        account = _account(request)
        if path.endswith("/auth/register"):
            seen.append("register")
            return httpx.Response(200, json={"data": {"id": account}})
        if path.endswith("/auth/login"):
            seen.append("login")
            return _session_response(request)
        return httpx.Response(404, json={"message": "not found"})

    _install(monkeypatch, handler)

    # No cookie from register, so the script must fall back to login; login then
    # works but the fixture stage still fails, which is fine for this assertion.
    asyncio.run(e2e_account_isolation.main())
    assert "login" in seen


def test_missing_access_cookie_is_inconclusive(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        # 200 everywhere but login never establishes a session cookie.
        if request.url.path.endswith("/auth/login"):
            return httpx.Response(200, json={"data": {"id": "u"}})
        if request.url.path.endswith("/auth/register"):
            return httpx.Response(200, json={"data": {"id": "u"}})
        return httpx.Response(404, json={"message": "not found"})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "reason=session_not_established" in output
    assert "access cookie" in output


def test_non_loopback_targets_are_refused_without_a_remote_opt_in(monkeypatch, capsys) -> None:
    smoke = e2e_account_isolation
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.setattr(smoke, "APP_BASE", "https://staging.example.com")
    monkeypatch.delenv("ULTICODE_E2E_ISOLATION_ALLOW_REMOTE", raising=False)

    assert asyncio.run(smoke.main()) == 1
    assert "not loopback" in capsys.readouterr().out


def test_loopback_targets_pass_the_guard(monkeypatch) -> None:
    smoke = e2e_account_isolation
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.setattr(smoke, "APP_BASE", "http://127.0.0.1:9103")
    monkeypatch.setattr(smoke, "AUTH_BASE", "http://localhost:9101")
    monkeypatch.delenv("ULTICODE_E2E_ISOLATION_ALLOW_REMOTE", raising=False)

    assert smoke._require_local_targets() is None


def test_remote_opt_in_is_explicit(monkeypatch) -> None:
    smoke = e2e_account_isolation
    monkeypatch.setenv(smoke.REMOTE_WRITE_OPT_IN, "1")
    monkeypatch.setattr(smoke, "APP_BASE", "https://staging.example.com")

    assert smoke._require_local_targets() is None


def test_empty_listings_do_not_count_as_isolation(monkeypatch, capsys) -> None:
    """Foreign absence is only evidence once own rows are shown to appear."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="empty"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "own_a_visible=no" in output
    assert "reason=listing_positive_control_failed" in output
    assert "OK isolation" not in output


def test_a_remote_opt_in_run_reports_scope_and_target(monkeypatch, capsys) -> None:
    """A non-loopback disposable stack is never labelled local by its URL."""
    smoke = e2e_account_isolation
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.setenv(smoke.REMOTE_WRITE_OPT_IN, "1")
    # A non-loopback target is only reachable with the remote opt-in.
    monkeypatch.setattr(smoke, "APP_BASE", "https://staging.example.invalid")
    monkeypatch.setattr(smoke, "AUTH_BASE", "https://staging.example.invalid")
    _install(monkeypatch, correct_service())

    assert asyncio.run(smoke.main()) == 1
    output = capsys.readouterr().out
    assert "scope=synthetic_nonproduction" in output
    assert "target_host=staging.example.invalid" in output
    assert "INCOMPLETE isolation http_contrast=passed" in output
    assert "local_stack_only" not in output


def test_a_loopback_run_reports_scope_and_target(monkeypatch, capsys) -> None:
    """A loopback URL is not environment proof: the scope stays explicit."""
    smoke = e2e_account_isolation
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.delenv(smoke.REMOTE_WRITE_OPT_IN, raising=False)
    monkeypatch.setattr(smoke, "APP_BASE", "http://127.0.0.1:9103")
    monkeypatch.setattr(smoke, "AUTH_BASE", "http://127.0.0.1:9101")
    _install(monkeypatch, correct_service())

    assert asyncio.run(smoke.main()) == 1
    output = capsys.readouterr().out
    assert "scope=synthetic_nonproduction" in output
    assert "target_host=127.0.0.1" in output
    assert "INCOMPLETE isolation http_contrast=passed" in output


def test_a_third_party_row_is_treated_as_a_leak(monkeypatch, capsys) -> None:
    """Neither account's own row is present, yet the listing leaks someone else."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="third_party"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "reason=cross_account_data_exposed" in output
    assert "unexpected_a=1" in output or "unexpected_b=1" in output


def test_a_malformed_listing_row_is_inconclusive(monkeypatch, capsys) -> None:
    """A row we cannot parse must not be silently skipped."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="malformed_row"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "harness_inconclusive" in capsys.readouterr().out


def test_a_truncated_listing_page_is_inconclusive(monkeypatch, capsys) -> None:
    """A page shorter than the reported total means hidden rows."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="truncated"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "harness_inconclusive" in capsys.readouterr().out


def test_an_own_row_reported_with_total_zero_is_inconclusive(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="total_zero_with_row"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "harness_inconclusive" in capsys.readouterr().out


def test_duplicate_listing_rows_are_inconclusive(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(listing="duplicate_rows"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "harness_inconclusive" in capsys.readouterr().out


def test_a_cross_response_with_source_but_no_id_or_canary_is_a_leak(
    monkeypatch, capsys
) -> None:
    """A refusal that copies the source with the marker stripped is still exposure.

    The body carries no submission id and no canary, so the id scan and the marker
    scan both come up clean; only the source-field/content check sees it.
    """
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if "/submissions/" in path and request.method == "GET":
            wanted = path.rsplit("/", 1)[-1]
            if wanted != OWNED[_account(request)]:
                return httpx.Response(
                    404, json={"message": "not found", "data": {"code": STARTER}}
                )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "cross_source_leak=yes" in output
    assert "reason=cross_account_data_exposed" in output
    assert "OK isolation" not in output


def test_cross_response_cannot_relabel_source_with_callers_canary(monkeypatch, capsys) -> None:
    """A source from an unauthorized response cannot pass by carrying A's canary."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.setattr(
        e2e_account_isolation,
        "_synthetic_canary",
        lambda account: f"u02-canary-{account}-fixed",
    )
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        if "/submissions/" in request.url.path and request.method == "GET":
            wanted = request.url.path.rsplit("/", 1)[-1]
            account = _account(request)
            if wanted != OWNED[account]:
                # A's marker is present, but this is still a private source in
                # A's refusal body and therefore an exposure.
                code = f"{STARTER}\n# u02-canary-a-fixed\n"
                return httpx.Response(
                    404, json={"message": "not found", "data": {"code": code}}
                )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "cross_source_leak=yes" in output
    assert "reason=cross_account_data_exposed" in output


def test_a_listing_that_carries_source_without_a_canary_is_a_leak(
    monkeypatch, capsys
) -> None:
    """A private listing row that copies the source with the marker stripped leaks."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    inner = correct_service()

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/submissions") and request.method == "GET":
            account = _account(request)
            return httpx.Response(
                200,
                json={
                    "data": {
                        "items": [{"id": OWNED[account], "code": STARTER}],
                        "total": 1,
                    }
                },
            )
        return inner(request)

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "listing_source_leak=yes" in output
    assert "reason=cross_account_data_exposed" in output
    assert "OK isolation" not in output


def test_the_public_starter_code_is_not_a_source_leak(monkeypatch, capsys) -> None:
    """The public problem detail legitimately returns ``starter_code``.

    The fixture source *is* the public starter code, so a scan that did not scope
    itself to the private submission routes would fail every run.
    """
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service())

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "cross_source_leak=no" in output
    assert "listing_source_leak=no" in output
    assert "INCOMPLETE isolation http_contrast=passed" in output


def test_a_source_field_is_detected_without_the_canary() -> None:
    """Both triggers: a source-bearing key, and content equal to the fixture."""
    detect = e2e_account_isolation._foreign_source_exposed
    own = "u02-canary-a-deadbeef"
    # A source-bearing field name, marker or not.
    assert detect({"data": {"source": STARTER}}, own_canary=own, starter=STARTER) is True
    assert detect({"data": {"code": "print(1)"}}, own_canary=own, starter=STARTER) is True
    # Content that reproduces the fixture after the canary comment is stripped,
    # under a key that is not itself source-bearing.
    assert detect({"data": {"payload": STARTER}}, own_canary=own, starter=STARTER) is True
    # The caller's own source is not exposure.
    assert (
        detect({"data": {"code": f"{STARTER}\n# {own}\n"}}, own_canary=own, starter=STARTER)
        is False
    )
    assert detect({"data": {"id": "sub-a"}}, own_canary=own, starter=STARTER) is False


def test_a_tool_result_with_stripped_source_is_detected() -> None:
    """A tool result that returns source without the marker must fail the gate."""
    recorder = e2e_account_isolation._ProbeRecorder()
    recorder.tools.append(
        {
            "tool": "get_my_submissions",
            "args": {},
            "failed": False,
            "result": {"items": [{"id": "sub-b", "code": STARTER}]},
        }
    )
    tool_leak, context_leak = e2e_account_isolation._agent_source_exposure(
        recorder, own_canary="u02-canary-a-deadbeef", starter=STARTER
    )
    assert tool_leak is True
    assert context_leak is False


def test_the_callers_own_source_in_a_tool_result_is_not_a_leak() -> None:
    recorder = e2e_account_isolation._ProbeRecorder()
    own = "u02-canary-a-deadbeef"
    recorder.tools.append(
        {
            "tool": "get_my_submissions",
            "args": {},
            "failed": False,
            "result": {"items": [{"id": "sub-a", "code": f"{STARTER}\n# {own}\n"}]},
        }
    )
    assert e2e_account_isolation._agent_source_exposure(
        recorder, own_canary=own, starter=STARTER
    ) == (False, False)


def test_a_serialized_source_tool_result_in_context_is_detected() -> None:
    """A tool result serialized into a model message is still walked, not skipped."""
    recorder = e2e_account_isolation._ProbeRecorder()
    recorder.request(
        [{"role": "tool", "content": json.dumps({"items": [{"id": "sub-b", "code": STARTER}]})}]
    )
    tool_leak, context_leak = e2e_account_isolation._agent_source_exposure(
        recorder, own_canary="u02-canary-a-deadbeef", starter=STARTER
    )
    assert tool_leak is False
    assert context_leak is True


def test_a_source_embedded_in_free_text_is_detected() -> None:
    """``prefix + source + suffix`` is the same leak as the bare source.

    The field does not have to *equal* the fixture: source pasted into a message,
    a comment, or any other prose is still copied out of its owner's scope.
    """
    detect = e2e_account_isolation._foreign_source_exposed
    own = "u02-canary-a-deadbeef"
    embedded = f"look at this\n{STARTER}\nand that is the code\n"
    assert detect({"data": {"message": embedded}}, own_canary=own, starter=STARTER) is True
    assert detect(embedded, own_canary=own, starter=STARTER) is True
    # Only the adjacent own fixture+canary block is exempt; surrounding prose stays.
    tagged = f"look at this\n{STARTER}\n# {own}\nand that is the code\n"
    assert detect({"data": {"message": tagged}}, own_canary=own, starter=STARTER) is False


def test_an_own_canary_does_not_exempt_foreign_source_in_same_text() -> None:
    detect = e2e_account_isolation._foreign_source_exposed
    own = "u02-canary-a-deadbeef"
    foreign = "u02-canary-b-cafebabe"
    mixed = f"{STARTER}\n# {own}\n{STARTER}\n# {foreign}\n"
    assert detect({"data": {"message": mixed}}, own_canary=own, starter=STARTER) is True


def test_a_source_field_with_own_block_and_partial_foreign_remainder_is_a_leak() -> None:
    """Removing the owner's own fixture must not exempt a partial foreign copy.

    Only the exact own fixture-plus-canary block is removed. Whatever is left in a
    source-bearing field is still copied source, even when the fragment does not
    reproduce the whole starter — a value like ``print(1)`` is a leak too.
    """
    detect = e2e_account_isolation._foreign_source_exposed
    own = "u02-canary-a-deadbeef"
    fragment = STARTER.splitlines()[0]
    mixed = f"{STARTER}\n# {own}\n{fragment}\n"
    assert detect({"data": {"code": mixed}}, own_canary=own, starter=STARTER) is True
    # A source field holding only the owner's own fixture is not a leak.
    assert (
        detect(
            {"data": {"code": f"{STARTER}\n# {own}\n"}},
            own_canary=own,
            starter=STARTER,
        )
        is False
    )


def test_a_source_embedded_in_a_plain_text_response_body_is_detected() -> None:
    """A non-JSON body that merely contains the fixture still fails the gate."""
    response = httpx.Response(
        403, text=f"refused\n{STARTER}\n(end of body)\n"
    )
    assert (
        e2e_account_isolation._response_exposes_source(
            response, own_canary="u02-canary-a-deadbeef", starter=STARTER
        )
        is True
    )


def test_a_source_embedded_in_a_tool_result_is_detected() -> None:
    """The tool-result leg sees embedded source, not only an exact field match."""
    recorder = e2e_account_isolation._ProbeRecorder()
    recorder.tools.append(
        {
            "tool": "get_my_submissions",
            "args": {},
            "failed": False,
            "result": {"items": [{"id": "sub-b", "note": f"see\n{STARTER}\nhere"}]},
        }
    )
    tool_leak, context_leak = e2e_account_isolation._agent_source_exposure(
        recorder, own_canary="u02-canary-a-deadbeef", starter=STARTER
    )
    assert tool_leak is True
    assert context_leak is False


def test_a_serialized_embedded_source_in_context_is_detected() -> None:
    """Past the json.dumps boundary the embedded source is still walked."""
    recorder = e2e_account_isolation._ProbeRecorder()
    recorder.request(
        [{"role": "tool", "content": json.dumps({"note": f"prefix\n{STARTER}\nsuffix"})}]
    )
    tool_leak, context_leak = e2e_account_isolation._agent_source_exposure(
        recorder, own_canary="u02-canary-a-deadbeef", starter=STARTER
    )
    assert tool_leak is False
    assert context_leak is True


def test_a_payload_nested_past_the_bound_fails_closed() -> None:
    """Too deep to finish walking is exposure, never a false clean."""
    depth = e2e_account_isolation.MAX_PAYLOAD_DEPTH
    assert depth >= 1
    too_deep = {"data": "clean"}
    for _ in range(depth + 5):
        too_deep = {"nested": too_deep}
    assert (
        e2e_account_isolation._foreign_source_exposed(
            too_deep, own_canary="u02-canary-a-deadbeef", starter=STARTER
        )
        is True
    )
    # A shallow, clean payload still reads as clean: the bound is not a blanket deny.
    shallow = {"data": {"id": "sub-a", "status": "ACCEPTED"}}
    assert (
        e2e_account_isolation._foreign_source_exposed(
            shallow, own_canary="u02-canary-a-deadbeef", starter=STARTER
        )
        is False
    )


def test_a_recursion_error_while_walking_fails_closed(monkeypatch) -> None:
    """A decoder that hits the interpreter's recursion limit must not read clean."""

    def _boom(*args: object, **kwargs: object) -> object:
        raise RecursionError

    monkeypatch.setattr(e2e_account_isolation.json, "loads", _boom)
    assert (
        e2e_account_isolation._foreign_source_exposed(
            {"data": {"payload": "[1, 2, 3]"}},
            own_canary="u02-canary-a-deadbeef",
            starter=STARTER,
        )
        is True
    )


def test_a_response_decode_recursion_error_fails_closed(monkeypatch) -> None:
    def _boom(*args: object, **kwargs: object) -> object:
        raise RecursionError

    # ``_response_exposes_source`` decodes with ``response.json()``, not ``_payload``.
    monkeypatch.setattr(httpx.Response, "json", _boom)
    response = httpx.Response(404, text="not found")
    assert (
        e2e_account_isolation._response_exposes_source(
            response, own_canary="u02-canary-a-deadbeef", starter=STARTER
        )
        is True
    )


def _own_detail_handler(inner, body_for):
    """Route the caller's own detail through ``body_for(account, wanted)``.

    Every other request falls through to ``inner``, so only the owner detail body
    under test changes.
    """

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if "/submissions/" in path and request.method == "GET":
            wanted = path.rsplit("/", 1)[-1]
            account = _account(request)
            if wanted == OWNED[account]:
                return httpx.Response(200, json={"data": body_for(account, wanted)})
        return inner(request)

    return handler


def test_own_detail_with_a_nested_foreign_submission_id_is_a_leak(
    monkeypatch, capsys
) -> None:
    """The root ``data.id`` matches, but a nested foreign UUID is still exposure."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    foreign = "11111111-1111-4111-8111-111111111111"
    _install(
        monkeypatch,
        _own_detail_handler(
            correct_service(),
            lambda account, wanted: {
                "id": wanted,
                "history": {"parentSubmissionId": foreign},
            },
        ),
    )

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "own_detail_foreign_id=yes" in output
    assert "reason=cross_account_data_exposed" in output


def test_own_detail_source_without_its_own_canary_fails_closed(
    monkeypatch, capsys
) -> None:
    """Own source is allowed only when the owner's canary identifies it."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(
        monkeypatch,
        _own_detail_handler(
            correct_service(),
            lambda account, wanted: {"id": wanted, "code": STARTER},
        ),
    )

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "own_detail_source_leak=yes" in output
    assert "reason=cross_account_data_exposed" in output


def test_own_detail_source_tagged_with_its_own_canary_is_allowed(
    monkeypatch, capsys
) -> None:
    """The owner's own source is not a leak when its canary marks it."""
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    prefix = e2e_account_isolation.CANARY_PREFIX
    monkeypatch.setattr(
        e2e_account_isolation,
        "_synthetic_canary",
        lambda account: f"{prefix}{account}-fixed",
    )
    _install(
        monkeypatch,
        _own_detail_handler(
            correct_service(),
            lambda account, wanted: {
                "id": wanted,
                "code": f"{STARTER}\n# {prefix}{account.replace('token-', '')}-fixed\n",
            },
        ),
    )

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "own_detail_source_leak=no" in output
    assert "INCOMPLETE isolation http_contrast=passed" in output


def test_owner_listing_is_scanned_raw_before_the_id_projection() -> None:
    """A foreign canary, copied source, or nested foreign id must be seen in the
    raw listing, not only in the ``id`` fields the harness projects out of it."""
    scan = e2e_account_isolation._owner_listing_exposure
    own = "u02-canary-a-deadbeef"
    foreign = "u02-canary-b-cafebabe"
    own_id = "11111111-1111-4111-8111-111111111111"
    other_id = "22222222-2222-4222-8222-222222222222"

    def check(listing: object) -> tuple[bool, bool, list[str]]:
        return scan(
            listing,
            own_canary=own,
            foreign_canary=foreign,
            starter=STARTER,
            own_id=own_id,
        )

    assert check({"items": [{"id": own_id, "status": "ACCEPTED"}], "total": 1}) == (
        False,
        False,
        [],
    )
    # A foreign canary nested in a row field the id projection drops.
    assert check({"items": [{"id": own_id, "note": foreign}], "total": 1})[0] is True
    # Copied source under a source-bearing key, with the canary stripped.
    assert check({"items": [{"id": own_id, "code": STARTER}], "total": 1})[1] is True
    # Another account's submission id nested under a non-listing key.
    assert check(
        {"items": [{"id": own_id, "history": {"parentSubmissionId": other_id}}], "total": 1}
    )[2] == [other_id]
    # The owner's own source, marked with its own canary, is not a leak.
    assert check(
        {"items": [{"id": own_id, "code": f"{STARTER}\n# {own}\n"}], "total": 1}
    ) == (False, False, [])
