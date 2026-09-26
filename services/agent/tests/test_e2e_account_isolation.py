import asyncio
import importlib.util
from pathlib import Path

import httpx

_module_spec = importlib.util.spec_from_file_location(
    "e2e_account_isolation",
    Path(__file__).parents[1] / "e2e_account_isolation.py",
)
assert _module_spec and _module_spec.loader
e2e_account_isolation = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_account_isolation)

OWNED: dict[str, str] = {"token-a": "sub-a", "token-b": "sub-b"}


def _account(request: httpx.Request) -> str:
    header = request.headers.get("Cookie", "")
    for token in OWNED:
        if f"access_token={token}" in header:
            return token
    return "anonymous"


def _install(monkeypatch, handler) -> None:
    """Route every session in the script through one mock transport."""
    monkeypatch.setattr(
        e2e_account_isolation,
        "_session",
        lambda: httpx.AsyncClient(
            transport=httpx.MockTransport(handler), follow_redirects=True
        ),
    )


def _contract_handler(
    *,
    detail_status: int = 404,
    listing_status: int = 200,
    listed_ids: list[str] | None = None,
    listing_items: object = "valid",
) -> object:
    """Mock that actually knows which account owns which submission."""

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        account = _account(request)
        if path.endswith("/auth/register"):
            return httpx.Response(
                200,
                json={"data": {"id": account}},
                headers={"set-cookie": f"access_token={account}; Path=/"},
            )
        if path.endswith("/auth/login"):
            return httpx.Response(
                200,
                json={"data": {"id": account}},
                headers={"set-cookie": f"access_token={account}; Path=/"},
            )
        if path.endswith("/problems"):
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions") and request.method == "POST":
            return httpx.Response(200, json={"data": {"id": OWNED[account]}})
        if path.endswith("/submissions") and listing_status != 200:
            return httpx.Response(listing_status, json={"message": "boom"})
        if path.endswith("/submissions"):
            if listing_items == "invalid":
                return httpx.Response(200, json={"data": {}})
            ids = listed_ids if listed_ids is not None else [OWNED["token-a"]]
            return httpx.Response(
                200, json={"data": {"items": [{"id": value} for value in ids]}}
            )
        if "/submissions/" in path:
            wanted = path.rsplit("/", 1)[-1]
            if detail_status != 200:
                return httpx.Response(detail_status, json={"message": "not found"})
            if wanted != OWNED[account]:
                # A correct service never serves another account's record.
                return httpx.Response(404, json={"message": "not found"})
            return httpx.Response(200, json={"data": {"id": wanted}})
        return httpx.Response(404, json={"message": "not found"})

    return handler


def test_script_is_opt_in(monkeypatch, capsys) -> None:
    monkeypatch.delenv("ULTICODE_E2E_ISOLATION", raising=False)

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "reason=opt_in_not_set" in capsys.readouterr().out


def test_isolated_stack_passes_both_controls(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, _contract_handler())

    assert asyncio.run(e2e_account_isolation.main()) == 0
    output = capsys.readouterr().out
    assert "login statuses a=200 b=200" in output
    assert "positive control own_a=200 own_b=200" in output
    assert "negative control cross_a=404 cross_b=404" in output
    assert "b_visible_to_a=no" in output
    assert "OK isolation" in output


def test_served_foreign_record_is_reported_as_exposure(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        account = _account(request)
        path = request.url.path
        if path.endswith("/auth/register") or path.endswith("/auth/login"):
            return httpx.Response(
                200,
                json={"data": {"id": account}},
                headers={"set-cookie": f"access_token={account}; Path=/"},
            )
        if path.endswith("/problems"):
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions") and request.method == "POST":
            return httpx.Response(200, json={"data": {"id": OWNED[account]}})
        if "/submissions/" in path:
            # Serves whatever was asked for, ignoring ownership.
            return httpx.Response(
                200, json={"data": {"id": path.rsplit("/", 1)[-1], "userId": "other"}}
            )
        return httpx.Response(200, json={"data": {"items": [{"id": "sub-b"}]}})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "reason=cross_account_data_exposed" in capsys.readouterr().out


def test_server_error_is_not_mistaken_for_a_refusal(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, _contract_handler(detail_status=500))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "negative control cross_a=500" in output
    assert "reason=cross_account_data_exposed" in output
    assert "OK isolation" not in output


def test_failing_listing_is_inconclusive_rather_than_clean(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, _contract_handler(listing_status=500))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "reason=harness_inconclusive" in output
    assert "b_visible_to_a" not in output


def test_listing_without_items_array_is_inconclusive(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")
    _install(monkeypatch, _contract_handler(listing_items="invalid"))

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "reason=harness_inconclusive" in capsys.readouterr().out


def test_own_read_returning_another_id_is_a_failure(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        account = _account(request)
        path = request.url.path
        if path.endswith("/auth/register") or path.endswith("/auth/login"):
            return httpx.Response(
                200,
                json={"data": {"id": account}},
                headers={"set-cookie": f"access_token={account}; Path=/"},
            )
        if path.endswith("/problems"):
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions") and request.method == "POST":
            return httpx.Response(200, json={"data": {"id": OWNED[account]}})
        if "/submissions/" in path:
            return httpx.Response(200, json={"data": {"id": "somebody-elses"}})
        return httpx.Response(200, json={"data": {"items": []}})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    assert "reason=positive_control_returned_other_record" in capsys.readouterr().out


def test_missing_access_cookie_is_inconclusive(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/auth/register"):
            return httpx.Response(200, json={"data": {"id": "u"}})
        if path.endswith("/auth/login"):
            # 200 but no session cookie: the harness must not pretend to be logged in.
            return httpx.Response(200, json={"data": {"id": "u"}})
        return httpx.Response(404, json={"message": "not found"})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "reason=login_failed" in output
    assert "access cookie" in output
