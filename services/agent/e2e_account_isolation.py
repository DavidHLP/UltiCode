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
so this contrast drives the HTTP contracts directly, one cookie session per
account.

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
import sys
from pathlib import Path

import httpx

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")
PASSWORD_LENGTH = 24
SUBMISSION_CODE = "print(1)"
SUBMISSION_LANGUAGE = "python"


def _synthetic_identity(label: str) -> tuple[str, str, str]:
    """Throwaway credentials for a local stack. Values are never printed."""
    username = f"u02-{label}-{secrets.token_hex(6)}"
    return username, f"{username}@local.invalid", secrets.token_urlsafe(PASSWORD_LENGTH)


def _session() -> httpx.AsyncClient:
    return httpx.AsyncClient(timeout=30.0, follow_redirects=True)


def _payload(response: httpx.Response) -> dict[str, object]:
    try:
        body = response.json()
    except ValueError:
        return {}
    return body if isinstance(body, dict) else {}


def _data(response: httpx.Response) -> dict[str, object]:
    data = _payload(response).get("data")
    return data if isinstance(data, dict) else {}


async def _register(client: httpx.AsyncClient, identity: tuple[str, str, str]) -> int:
    username, email, password = identity
    response = await client.post(
        f"{AUTH_BASE}/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    return response.status_code


async def _login(client: httpx.AsyncClient, identity: tuple[str, str, str]) -> int:
    username, _email, password = identity
    response = await client.post(
        f"{AUTH_BASE}/auth/login", json={"username": username, "password": password}
    )
    return response.status_code


async def _first_problem_id(client: httpx.AsyncClient) -> int | None:
    response = await client.get(f"{APP_BASE}/problems", params={"page": 1, "pageSize": 1})
    items = _data(response).get("items")
    for item in items if isinstance(items, list) else []:
        if isinstance(item, dict) and isinstance(item.get("id"), int):
            return item["id"]
    return None


async def _submit(client: httpx.AsyncClient, problem_id: int) -> str | None:
    response = await client.post(
        f"{APP_BASE}/submissions",
        json={
            "problemId": problem_id,
            "language": SUBMISSION_LANGUAGE,
            "code": SUBMISSION_CODE,
        },
    )
    submission_id = _data(response).get("id")
    return submission_id if isinstance(submission_id, str) and submission_id else None


async def _detail_status(client: httpx.AsyncClient, submission_id: str) -> int:
    return (await client.get(f"{APP_BASE}/submissions/{submission_id}")).status_code


async def _problem_submission_ids(client: httpx.AsyncClient, problem_id: int) -> set[str]:
    response = await client.get(
        f"{APP_BASE}/problems/{problem_id}/submissions",
        params={"page": 1, "pageSize": 50},
    )
    items = _data(response).get("items")
    return {
        str(item["id"])
        for item in items if isinstance(items, list)
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }


async def main() -> int:
    if os.environ.get("ULTICODE_E2E_ISOLATION") != "1":
        print("SKIP reason=opt_in_not_set")
        return 0

    identity_a = _synthetic_identity("a")
    identity_b = _synthetic_identity("b")
    async with _session() as auth_a, _session() as auth_b, _session() as app_a, _session() as app_b:
        register_a = await _register(auth_a, identity_a)
        register_b = await _register(auth_b, identity_b)
        print(f"register statuses a={register_a} b={register_b}")
        if register_a not in (200, 201) or register_b not in (200, 201):
            print("FAIL reason=registration_failed")
            return 1

        login_a = await _login(auth_a, identity_a)
        login_b = await _login(auth_b, identity_b)
        print(f"login statuses a={login_a} b={login_b}")
        if login_a != 200 or login_b != 200:
            print("FAIL reason=login_failed")
            return 1
        # Both accounts now hold their own session cookies on their auth client;
        # carry them onto the app client, mirroring how the agent client works.
        app_a.cookies.update(auth_a.cookies)
        app_b.cookies.update(auth_b.cookies)

        problem_id = await _first_problem_id(app_a)
        if problem_id is None:
            print("FAIL reason=no_problem_available")
            return 1

        submission_a = await _submit(app_a, problem_id)
        submission_b = await _submit(app_b, problem_id)
        if submission_a is None or submission_b is None:
            print("FAIL reason=submission_not_created")
            return 1
        print("submissions created a=1 b=1")

        own_a = await _detail_status(app_a, submission_a)
        own_b = await _detail_status(app_b, submission_b)
        print(f"positive control own_a={own_a} own_b={own_b}")
        if own_a != 200 or own_b != 200:
            print("FAIL reason=positive_control_failed")
            return 1

        cross_a = await _detail_status(app_a, submission_b)
        cross_b = await _detail_status(app_b, submission_a)
        print(f"negative control cross_a={cross_a} cross_b={cross_b}")

        listed_by_a = await _problem_submission_ids(app_a, problem_id)
        leaked = submission_b in listed_by_a
        print(
            f"listing leak b_visible_to_a={'yes' if leaked else 'no'} "
            f"listed_count={len(listed_by_a)}"
        )

        if cross_a == 200 or cross_b == 200 or leaked:
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
