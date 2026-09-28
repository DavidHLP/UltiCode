import asyncio
import importlib.util
import json
from pathlib import Path

import httpx
import pytest

_module_spec = importlib.util.spec_from_file_location(
    "e2e_account_isolation",
    Path(__file__).parents[1] / "e2e_account_isolation.py",
)
assert _module_spec and _module_spec.loader
e2e_account_isolation = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_account_isolation)

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


def test_isolated_stack_passes_both_controls(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service())

    assert asyncio.run(e2e_account_isolation.main()) == 0
    output = capsys.readouterr().out
    assert "sessions established a=register b=register" in output
    assert "positive control own_a=200 own_b=200" in output
    assert "negative control cross_a=404 cross_b=404" in output
    assert "b_visible_to_a=no" in output
    assert "own_a_visible=yes" in output
    assert "own_b_visible=yes" in output
    assert "OK isolation" in output


def test_forbidden_counts_as_a_refusal(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(foreign_status=403))

    assert asyncio.run(e2e_account_isolation.main()) == 0
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

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "OK isolation" in capsys.readouterr().out


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

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "OK isolation" in capsys.readouterr().out


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

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "OK isolation" in capsys.readouterr().out

    # Below the needed depth the scan is honest about finding nothing.
    monkeypatch.setenv("ULTICODE_E2E_FIXTURE_MAX_PAGES", "2")
    _install(monkeypatch, handler)
    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "FAIL reason=fixture_unavailable" in capsys.readouterr().out


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

    assert asyncio.run(e2e_account_isolation.main()) == 0
    output = capsys.readouterr().out
    assert (
        "public control anonymous_listing=200 anonymous_detail=200 "
        "listing_rows=1 detail_id_matches=yes"
    ) in output
    assert "OK isolation" in output


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

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "OK isolation" in capsys.readouterr().out
    assert len(seen) == 2
    assert {body["code"] for body in seen} == {STARTER}
    assert {body["language"] for body in seen} == {"python"}


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
            return httpx.Response(
                200, json={"data": {"languages": [{"value": "python", "starter_code": STARTER}]}}
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


def test_a_remote_opt_in_run_is_not_labelled_local(monkeypatch, capsys) -> None:
    """Evidence from a remote disposable stack must not claim to be local."""
    smoke = e2e_account_isolation
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.setenv(smoke.REMOTE_WRITE_OPT_IN, "1")
    # A non-loopback target is only reachable with the remote opt-in.
    monkeypatch.setattr(smoke, "APP_BASE", "https://staging.example.invalid")
    monkeypatch.setattr(smoke, "AUTH_BASE", "https://staging.example.invalid")
    _install(monkeypatch, correct_service())

    assert asyncio.run(smoke.main()) == 0
    output = capsys.readouterr().out
    assert "remote_opt_in" in output
    assert "local_stack_only" not in output


def test_a_loopback_run_is_labelled_local(monkeypatch, capsys) -> None:
    smoke = e2e_account_isolation
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    monkeypatch.delenv(smoke.REMOTE_WRITE_OPT_IN, raising=False)
    monkeypatch.setattr(smoke, "APP_BASE", "http://127.0.0.1:9103")
    monkeypatch.setattr(smoke, "AUTH_BASE", "http://127.0.0.1:9101")
    _install(monkeypatch, correct_service())

    assert asyncio.run(smoke.main()) == 0
    assert "local_stack_only" in capsys.readouterr().out


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
