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


def _install(monkeypatch, handler) -> None:
    """Route every session in the script through one mock transport."""
    monkeypatch.setattr(
        e2e_account_isolation,
        "_session",
        lambda: httpx.AsyncClient(transport=httpx.MockTransport(handler), follow_redirects=True),
    )


def test_script_is_opt_in(monkeypatch, capsys) -> None:
    monkeypatch.delenv("ULTICODE_E2E_ISOLATION", raising=False)

    assert asyncio.run(e2e_account_isolation.main()) == 0
    assert "reason=opt_in_not_set" in capsys.readouterr().out


def test_positive_and_negative_controls_pass_without_any_account_data(
    monkeypatch, capsys
) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/auth/register"):
            return httpx.Response(200, json={"data": {"id": "u"}})
        if path.endswith("/auth/login"):
            return httpx.Response(200, json={"data": {"id": "u"}})
        if path.endswith("/problems"):
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions") and request.method == "POST":
            return httpx.Response(200, json={"data": {"id": "sub-1"}})
        if path.endswith("/submissions/sub-1"):
            return httpx.Response(200, json={"data": {"id": "sub-1"}})
        if path.endswith("/submissions") or "/submissions?" in str(request.url):
            return httpx.Response(200, json={"data": {"items": []}})
        return httpx.Response(404, json={"message": "not found"})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 0
    output = capsys.readouterr().out
    assert "positive control own_a=200 own_b=200" in output
    assert "negative control cross_a=404 cross_b=404" in output
    assert "b_visible_to_a=no" in output
    assert "OK isolation" in output


def test_cross_account_read_is_reported_as_exposure(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/auth/register") or path.endswith("/auth/login"):
            return httpx.Response(200, json={"data": {"id": "u"}})
        if path.endswith("/problems"):
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions") and request.method == "POST":
            return httpx.Response(200, json={"data": {"id": "sub-1"}})
        # A foreign submission detail is served instead of refused.
        if path.endswith("/submissions/sub-1"):
            return httpx.Response(200, json={"data": {"id": "sub-1", "userId": "other"}})
        return httpx.Response(200, json={"data": {"items": [{"id": "sub-1"}]}})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "negative control cross_a=200" in output
    assert "b_visible_to_a=yes" in output
    assert "reason=cross_account_data_exposed" in output


def test_failed_positive_control_stops_before_the_negative_one(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_ISOLATION", "1")

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/auth/register") or path.endswith("/auth/login"):
            return httpx.Response(200, json={"data": {"id": "u"}})
        if path.endswith("/problems"):
            return httpx.Response(200, json={"data": {"items": [{"id": 7}]}})
        if path.endswith("/submissions"):
            return httpx.Response(200, json={"data": {"id": "sub-1"}})
        # Even the owner cannot read their own submission back.
        return httpx.Response(500, json={"message": "boom"})

    _install(monkeypatch, handler)

    assert asyncio.run(e2e_account_isolation.main()) == 1
    output = capsys.readouterr().out
    assert "positive control own_a=500" in output
    assert "negative control" not in output
    assert "reason=positive_control_failed" in output
