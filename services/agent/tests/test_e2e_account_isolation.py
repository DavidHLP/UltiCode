import asyncio
import importlib.util
import json
from pathlib import Path

import httpx

_module_spec = importlib.util.spec_from_file_location(
    "e2e_account_isolation",
    Path(__file__).parents[1] / "e2e_account_isolation.py",
)
assert _module_spec and _module_spec.loader
e2e_account_isolation = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_account_isolation)

#: token -> the submission that account owns
OWNED = {"token-a": "sub-a", "token-b": "sub-b"}


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


def _session_response(request: httpx.Request) -> httpx.Response:
    account = _account(request)
    return httpx.Response(
        200,
        json={"data": {"id": account}},
        headers={
            "set-cookie": (
                f"access_token={account}; Path=/, csrf_token=csrf-{account}; Path=/"
            )
        },
    )


def _install(monkeypatch, handler) -> None:
    """Route every session in the script through one mock transport."""
    monkeypatch.setattr(
        e2e_account_isolation,
        "_session",
        lambda: httpx.AsyncClient(
            transport=httpx.MockTransport(handler), follow_redirects=True
        ),
    )


def correct_service(foreign_status: int = 404, listing: object = "own_only") -> object:
    """A service that serves the caller's own records and refuses others."""

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        account = _account(request)
        if path.endswith("/auth/register") or path.endswith("/auth/login"):
            return _session_response(request)
        if path.endswith("/problems"):
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions") and request.method == "POST":
            return httpx.Response(200, json={"data": {"id": OWNED[account]}})
        if path.endswith("/submissions"):
            if listing == "broken_envelope":
                return httpx.Response(200, json={"data": {}})
            if listing == "server_error":
                return httpx.Response(500, json={"message": "boom"})
            if listing == "leaks_other":
                return httpx.Response(
                    200, json={"data": {"items": [{"id": value} for value in OWNED.values()]}}
                )
            return httpx.Response(
                200, json={"data": {"items": [{"id": OWNED[account]}]}}
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
    assert "OK isolation" in output


def test_forbidden_counts_as_a_refusal(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, correct_service(foreign_status=403))

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "negative control cross_a=403 cross_b=403" in capsys.readouterr().out


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
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions") and request.method == "POST":
            # A cookie write without the CSRF echo is what the filter rejects.
            assert request.headers.get("X-CSRF-Token") == f"csrf-{account}"
            return httpx.Response(200, json={"data": {"id": OWNED[account]}})
        if "/submissions/" in path:
            return httpx.Response(200, json={"data": {"id": "somebody-elses"}})
        return httpx.Response(200, json={"data": {"items": []}})

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
