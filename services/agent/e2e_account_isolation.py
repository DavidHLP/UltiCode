"""DAV-53 dual-account read-only isolation contrast against a local stack.

Opt-in and local-only: it registers two throwaway non-admin accounts on the
development stack, gives each one submission, and records the positive control
(each account reads its own) next to the negative control (account A asks for
account B's submission by id).

It never touches real user data and never prints a credential, a token, a cookie,
or any response body: output is fixed labels and status codes only.

Why this exists: ``SubmissionController.getSubmission`` forwards the authenticated
user id to ``findById(id, userId)``, but a call site that forwards an argument is
not proof that the owner service applies it. DAV-53 requires the refusal to be
observed. The agent's own read-only client cannot express a cross-account read,
so this contrast drives the HTTP contracts directly, one session per account.

Verdicts are deliberately narrow: a cross-account read counts as refused only on
the statuses the contract defines (403/404). A 500 or an empty body is a failure
of this script, not evidence that isolation held.

Run against the local development stack:

    ULTICODE_E2E_ISOLATION=1 \\
    ULTICODE_APP_BASE=http://localhost:9103 \\
    ULTICODE_AUTH_BASE=http://localhost:9101 \\
    uv run python e2e_account_isolation.py
"""

from __future__ import annotations

import asyncio
import os
import secrets
from typing import Any

import httpx

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")
ACCESS_COOKIE = "access_token"
CSRF_COOKIE = "csrf_token"
#: Double-submit CSRF: a cookie-authenticated write must echo this header.
CSRF_HEADER = "X-CSRF-Token"
PASSWORD_LENGTH = 24
SUBMISSION_CODE = "print(1)"
SUBMISSION_LANGUAGE = "python"
REFUSAL_STATUSES = frozenset({403, 404})


class IsolationHarnessError(RuntimeError):
    """The harness could not reach a verdict; isolation is unproven."""


def _synthetic_identity(label: str) -> tuple[str, str, str]:
    """Throwaway credentials for a local stack. Values are never printed."""
    username = f"u02-{label}-{secrets.token_hex(6)}"
    return username, f"{username}@local.invalid", secrets.token_urlsafe(PASSWORD_LENGTH)


def _session() -> httpx.AsyncClient:
    return httpx.AsyncClient(timeout=30.0, follow_redirects=True)


def _cookie(cookies: httpx.Cookies, name: str) -> str | None:
    found = [c.value for c in cookies.jar if c.name == name and c.value]
    return found[0] if len(found) == 1 else None


def _session_headers(cookies: httpx.Cookies) -> dict[str, str]:
    """Send the access cookie as a header, like UlticodeClient does.

    Copying the jar between clients loses ``Secure`` cookies over plain HTTP, so
    the value is forwarded explicitly. The value is never logged or returned.
    """
    access = _cookie(cookies, ACCESS_COOKIE)
    if access is None:
        return {}
    return {"Cookie": f"{ACCESS_COOKIE}={access}"}


def _write_headers(cookies: httpx.Cookies) -> dict[str, str]:
    """Session headers plus the double-submit CSRF header a cookie write needs.

    ``CookieCsrfFilter`` rejects an unsafe method that carries a credential cookie
    unless ``X-CSRF-Token`` matches the ``csrf_token`` cookie. Neither value is
    ever printed.
    """
    headers = _session_headers(cookies)
    if not headers:
        return {}
    csrf = _cookie(cookies, CSRF_COOKIE)
    if csrf is None:
        raise IsolationHarnessError("no csrf_token cookie for a cookie-authenticated write")
    # The double-submit check compares the cookie against the header, so the
    # csrf cookie has to travel in the Cookie header too — sending only the
    # header yields "Invalid CSRF token".
    headers["Cookie"] = f"{headers['Cookie']}; {CSRF_COOKIE}={csrf}"
    return {**headers, CSRF_HEADER: csrf}


def _payload(response: httpx.Response) -> dict[str, Any]:
    try:
        body = response.json()
    except ValueError:
        return {}
    return body if isinstance(body, dict) else {}


def _data(response: httpx.Response) -> dict[str, Any]:
    data = _payload(response).get("data")
    return data if isinstance(data, dict) else {}


def _require_200(response: httpx.Response, what: str) -> httpx.Response:
    if response.status_code != 200:
        raise IsolationHarnessError(f"{what} returned {response.status_code}")
    return response


async def _register(client: httpx.AsyncClient, identity: tuple[str, str, str]) -> int:
    username, email, password = identity
    response = await client.post(
        f"{AUTH_BASE}/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    return response.status_code


async def _establish_session(
    client: httpx.AsyncClient, identity: tuple[str, str, str]
) -> str:
    """Prefer the session the register call already issued; fall back to login.

    ``AuthController.register`` applies a session cookie itself, so a registered
    account normally never needs a second call. Returns how the session was
    established so the evidence records it.
    """
    if _session_headers(client.cookies):
        return "register"
    username, _email, password = identity
    response = await client.post(
        f"{AUTH_BASE}/auth/login", json={"username": username, "password": password}
    )
    if response.status_code != 200:
        raise IsolationHarnessError(f"login returned {response.status_code}")
    if not _session_headers(client.cookies):
        raise IsolationHarnessError("login did not establish a single access cookie")
    return "login"


async def _first_problem_id(
    client: httpx.AsyncClient, headers: dict[str, str]
) -> int:
    response = _require_200(
        await client.get(
            f"{APP_BASE}/problems", params={"page": 1, "pageSize": 1}, headers=headers
        ),
        "problem listing",
    )
    items = _data(response).get("items")
    for item in items if isinstance(items, list) else []:
        if isinstance(item, dict) and isinstance(item.get("id"), int):
            return item["id"]
    raise IsolationHarnessError("no problem available for a submission fixture")


async def _submit(
    client: httpx.AsyncClient, cookies: httpx.Cookies, problem_id: int
) -> str:
    response = _require_200(
        await client.post(
            f"{APP_BASE}/submissions",
            json={
                "problemId": problem_id,
                "language": SUBMISSION_LANGUAGE,
                "code": SUBMISSION_CODE,
            },
            headers=_write_headers(cookies),
        ),
        "submission",
    )
    submission_id = _data(response).get("id")
    if not isinstance(submission_id, str) or not submission_id:
        raise IsolationHarnessError("submission response carried no id")
    return submission_id


async def _read_detail(
    client: httpx.AsyncClient, headers: dict[str, str], submission_id: str
) -> tuple[int, str | None]:
    """Return the status and, on 200, the id the server actually returned."""
    response = await client.get(
        f"{APP_BASE}/submissions/{submission_id}", headers=headers
    )
    if response.status_code != 200:
        return response.status_code, None
    return 200, _data(response).get("id")


async def _problem_submission_ids(
    client: httpx.AsyncClient, headers: dict[str, str], problem_id: int
) -> set[str]:
    response = _require_200(
        await client.get(
            f"{APP_BASE}/problems/{problem_id}/submissions",
            params={"page": 1, "pageSize": 50},
            headers=headers,
        ),
        "problem submission listing",
    )
    items = _data(response).get("items")
    if not isinstance(items, list):
        raise IsolationHarnessError("listing envelope had no items array")
    return {
        str(item["id"])
        for item in items
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }


async def main() -> int:
    if os.environ.get("ULTICODE_E2E_ISOLATION") != "1":
        print("SKIP reason=opt_in_not_set")
        return 0

    identity_a = _synthetic_identity("a")
    identity_b = _synthetic_identity("b")
    async with (
        _session() as auth_a,
        _session() as auth_b,
        _session() as app_a,
        _session() as app_b,
    ):
        register_a = await _register(auth_a, identity_a)
        register_b = await _register(auth_b, identity_b)
        print(f"register statuses a={register_a} b={register_b}")
        if register_a not in (200, 201) or register_b not in (200, 201):
            print("FAIL reason=registration_failed")
            return 1

        try:
            session_a = await _establish_session(auth_a, identity_a)
            session_b = await _establish_session(auth_b, identity_b)
        except IsolationHarnessError as error:
            print(f"FAIL reason=session_not_established detail={error}")
            return 1
        print(f"sessions established a={session_a} b={session_b}")

        headers_a = _session_headers(auth_a.cookies)
        headers_b = _session_headers(auth_b.cookies)
        # The write path is cookie-authenticated, so it needs the CSRF echo and
        # must read its cookies from the session that established them.
        app_a.cookies.update(auth_a.cookies)
        app_b.cookies.update(auth_b.cookies)
        try:
            problem_id = await _first_problem_id(app_a, headers_a)
            submission_a = await _submit(app_a, app_a.cookies, problem_id)
            submission_b = await _submit(app_b, app_b.cookies, problem_id)
        except IsolationHarnessError as error:
            print(f"FAIL reason=fixture_unavailable detail={error}")
            return 1
        print("submissions created a=1 b=1")

        try:
            own_a_status, own_a_id = await _read_detail(app_a, headers_a, submission_a)
            own_b_status, own_b_id = await _read_detail(app_b, headers_b, submission_b)
            print(f"positive control own_a={own_a_status} own_b={own_b_status}")
            # A 200 that returns someone else's id would not be an own-read.
            if (own_a_status, own_b_status) != (200, 200):
                print("FAIL reason=positive_control_failed")
                return 1
            if own_a_id != submission_a or own_b_id != submission_b:
                print("FAIL reason=positive_control_returned_other_record")
                return 1

            cross_a, _ = await _read_detail(app_a, headers_a, submission_b)
            cross_b, _ = await _read_detail(app_b, headers_b, submission_a)
            print(f"negative control cross_a={cross_a} cross_b={cross_b}")
            if cross_a not in REFUSAL_STATUSES or cross_b not in REFUSAL_STATUSES:
                print("FAIL reason=cross_account_data_exposed")
                return 1

            listed_by_a = await _problem_submission_ids(app_a, headers_a, problem_id)
        except IsolationHarnessError as error:
            print(f"FAIL reason=harness_inconclusive detail={error}")
            return 1

        leaked = submission_b in listed_by_a
        print(
            f"listing leak b_visible_to_a={'yes' if leaked else 'no'} "
            f"listed_count={len(listed_by_a)}"
        )
        if leaked:
            print("FAIL reason=cross_account_data_exposed")
            return 1

    print("OK isolation dual_account_contrast local_stack_only")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:  # noqa: BLE001 - fixed status label only
        print(f"FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
