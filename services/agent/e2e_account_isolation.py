"""DAV-53 dual-account read-only isolation contrast against a local stack.

Opt-in and local-only: it registers two throwaway non-admin accounts on the
development stack, gives each one submission, and records the positive control
(each account reads its own) next to the negative control (account A asks for
account B's submission by id).

It never touches real user data and never prints a credential, a token, a cookie,
or any response body: output is fixed labels and status codes only.

The submission fixture is the target problem's **own** Python starter code, read
from the public problem detail. The D-form harness requires a ``Solution`` class
and the problem's method signature: a bare ``print(1)`` is a harness panic
(stderr traceback, exit 2, no envelope), which the judge reports as
``Runtime Error``. Such a submission still yields the id this contrast needs, but
it exercises nothing and leaves rows that read like an environment failure, so
the fixture holds the contract instead.

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
import string
from typing import Any

import httpx

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")
ACCESS_COOKIE = "access_token"
CSRF_COOKIE = "csrf_token"
#: Double-submit CSRF: a cookie-authenticated write must echo this header.
CSRF_HEADER = "X-CSRF-Token"
SUBMISSION_LANGUAGE = "python"
#: The fixture scan follows the listing's own `total`, and this is only a safety
#: net against a pathological listing. Raise it with
#: `ULTICODE_E2E_FIXTURE_MAX_PAGES` rather than editing the code.
PROBLEM_SCAN_PAGE_SIZE = 50
DEFAULT_PROBLEM_SCAN_PAGES = 20
REFUSAL_STATUSES = frozenset({403, 404})


class IsolationHarnessError(RuntimeError):
    """The harness could not reach a verdict; isolation is unproven."""


def _synthetic_password() -> str:
    """Registration requires upper, lower and digit; random text need not have all three."""
    return (
        f"{secrets.token_urlsafe(12)}"
        f"{secrets.choice(string.ascii_uppercase)}"
        f"{secrets.choice(string.ascii_lowercase)}"
        f"{secrets.choice(string.digits)}"
    )


def _synthetic_identity(label: str) -> tuple[str, str, str]:
    """Throwaway credentials for a local stack. Values are never printed."""
    username = f"u02-{label}-{secrets.token_hex(6)}"
    return username, f"{username}@local.invalid", _synthetic_password()


LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1", "[::1]"})
#: Set only when the target really is a disposable stack you own.
REMOTE_WRITE_OPT_IN = "ULTICODE_E2E_ISOLATION_ALLOW_REMOTE"


def _require_local_targets() -> str | None:
    """Refuse to register accounts and submit on a non-loopback stack.

    This harness *writes* (two accounts, two submissions). Inheriting a
    base URL that points at staging or production would mutate shared data, so a
    remote target needs a separate, explicit opt-in.
    """
    from urllib.parse import urlparse

    if os.environ.get(REMOTE_WRITE_OPT_IN) == "1":
        return None
    for name, base in (("app", APP_BASE), ("auth", AUTH_BASE)):
        host = (urlparse(base).hostname or "").lower()
        if host not in LOOPBACK_HOSTS:
            return f"{name} target {host!r} is not loopback"
    return None


def _session() -> httpx.AsyncClient:
    # Redirects are not followed: a 307/308 from a loopback endpoint would
    # replay the POST body against a staging or production host, which the
    # base-URL guard cannot see.
    # trust_env=False: with HTTP_PROXY/ALL_PROXY set, httpx would otherwise send
    # these loopback requests — carrying generated credentials, session cookies
    # and submission bodies — through a remote proxy.
    return httpx.AsyncClient(timeout=30.0, follow_redirects=False, trust_env=False)


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
    """The envelope's ``data``, but only for a successful ``Result``.

    ``UlticodeClient._unwrap`` requires an integer ``code == 0``. Accepting a
    plausible ``data`` object without it would let a nonzero envelope — a
    gateway error that still carries a body — read as isolation evidence.
    """
    payload = _payload(response)
    code = payload.get("code")
    # `False == 0` in Python, so a boolean would otherwise pass as success.
    if isinstance(code, bool) or not isinstance(code, int) or code != 0:
        return {}
    data = payload.get("data")
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


def _scan_page_limit() -> int:
    """How many listing pages the fixture scan may read before giving up."""
    raw = os.environ.get("ULTICODE_E2E_FIXTURE_MAX_PAGES", "").strip()
    if not raw:
        return DEFAULT_PROBLEM_SCAN_PAGES
    try:
        value = int(raw)
    except ValueError:
        raise IsolationHarnessError(
            "ULTICODE_E2E_FIXTURE_MAX_PAGES must be an integer"
        ) from None
    if value < 1:
        raise IsolationHarnessError("ULTICODE_E2E_FIXTURE_MAX_PAGES must be at least 1")
    return value


async def _fixture_problem(
    client: httpx.AsyncClient, headers: dict[str, str]
) -> tuple[int, str]:
    """A problem the harness can actually submit to, with its own starter code.

    The first listing row is not necessarily one this harness can use: languages
    are configured per problem, so a Java-only first row would fail the run closed
    even though a later problem offers the fixture language. The scan follows the
    listing's own pagination, bounded so a broken `total` cannot spin here.
    """
    page = 1
    page_limit = _scan_page_limit()
    while page <= page_limit:
        response = _require_200(
            await client.get(
                f"{APP_BASE}/problems",
                params={"page": page, "pageSize": PROBLEM_SCAN_PAGE_SIZE},
                headers=headers,
            ),
            "problem listing",
        )
        payload = _data(response)
        items = payload.get("items")
        if not isinstance(items, list) or not items:
            break
        for item in items:
            if not isinstance(item, dict):
                continue
            problem_id = item.get("id")
            if isinstance(problem_id, bool) or not isinstance(problem_id, int):
                # `True` is an int in Python, so a boolean id is not a problem id.
                continue
            try:
                return problem_id, await _starter_code(client, headers, problem_id)
            except IsolationHarnessError:
                # No starter for this language here; the listing may still offer one.
                continue
        total = payload.get("total")
        if not isinstance(total, int) or isinstance(total, bool):
            break
        if page * PROBLEM_SCAN_PAGE_SIZE >= total:
            break
        page += 1
    raise IsolationHarnessError("no problem offers a fixture for this language")


async def _starter_code(
    client: httpx.AsyncClient, headers: dict[str, str], problem_id: int
) -> str:
    """The problem's own starter code for ``SUBMISSION_LANGUAGE``.

    It is the only fixture shape that satisfies the D-form harness contract, so it
    is read from the contract rather than guessed: a submission the harness cannot
    load is a harness panic, not a judged submission.
    """
    response = _require_200(
        await client.get(f"{APP_BASE}/problems/{problem_id}", headers=headers),
        "problem detail",
    )
    languages = _data(response).get("languages")
    for language in languages if isinstance(languages, list) else []:
        if not isinstance(language, dict) or language.get("value") != SUBMISSION_LANGUAGE:
            continue
        code = language.get("starter_code")
        if isinstance(code, str) and code.strip():
            return code
    raise IsolationHarnessError("problem carries no starter code for this language")


async def _submit(
    client: httpx.AsyncClient, cookies: httpx.Cookies, problem_id: int, code: str
) -> str:
    response = _require_200(
        await client.post(
            f"{APP_BASE}/submissions",
            json={
                "problemId": problem_id,
                "language": SUBMISSION_LANGUAGE,
                "code": code,
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
    payload = _data(response)
    items = payload.get("items")
    total = payload.get("total")
    if not isinstance(items, list) or not isinstance(total, int) or isinstance(total, bool):
        raise IsolationHarnessError("listing envelope had no items array or total")
    ids: set[str] = set()
    for item in items:
        # A row we cannot parse must not be skipped: it could be the very row
        # that leaked.
        if not isinstance(item, dict) or not isinstance(item.get("id"), str):
            raise IsolationHarnessError("listing had a malformed row")
        ids.add(item["id"])
    if len(items) < min(total, 50):
        # A page shorter than the reported total means unseen rows.
        raise IsolationHarnessError("listing page was truncated")
    if total != len(items):
        # An own row reported with total=0, or a total larger than the rows we
        # received, is an invalid listing contract — not evidence of isolation.
        raise IsolationHarnessError("listing total disagrees with its rows")
    if len(ids) != len(items):
        # Duplicate ids collapse in the set, hiding a repeated row.
        raise IsolationHarnessError("listing contained duplicate rows")
    return ids


async def main() -> int:
    if os.environ.get("ULTICODE_E2E_ISOLATION") != "1":
        print("SKIP reason=opt_in_not_set")
        return 0
    unsafe = _require_local_targets()
    if unsafe is not None:
        print(f"FAIL reason={unsafe}")
        return 1

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
            problem_id, code = await _fixture_problem(app_a, headers_a)
            submission_a = await _submit(app_a, app_a.cookies, problem_id, code)
            submission_b = await _submit(app_b, app_b.cookies, problem_id, code)
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

            # Leakage can be directional: B's listing could expose A's rows
            # while A's stays scoped, so both directions are checked.
            listed_by_a = await _problem_submission_ids(app_a, headers_a, problem_id)
            listed_by_b = await _problem_submission_ids(app_b, headers_b, problem_id)
        except IsolationHarnessError as error:
            print(f"FAIL reason=harness_inconclusive detail={error}")
            return 1

        own_a_visible = submission_a in listed_by_a
        own_b_visible = submission_b in listed_by_b
        # Isolation means each account sees only its own row: a third user's
        # submission is just as much a leak as the paired account's.
        unexpected_a = listed_by_a - {submission_a}
        unexpected_b = listed_by_b - {submission_b}
        leaked = bool(unexpected_a) or bool(unexpected_b)
        print(
            f"listing own_a_visible={'yes' if own_a_visible else 'no'} "
            f"own_b_visible={'yes' if own_b_visible else 'no'} "
            f"b_visible_to_a={'yes' if submission_b in listed_by_a else 'no'} "
            f"a_visible_to_b={'yes' if submission_a in listed_by_b else 'no'} "
            f"listed_count_a={len(listed_by_a)} listed_count_b={len(listed_by_b)} "
            f"unexpected_a={len(unexpected_a)} unexpected_b={len(unexpected_b)}"
        )
        if leaked:
            print("FAIL reason=cross_account_data_exposed")
            return 1
        if not (own_a_visible and own_b_visible):
            # Empty listings would make the foreign-absence checks vacuously
            # true, so the listing path must first show it returns own rows.
            print("FAIL reason=listing_positive_control_failed")
            return 1

        # Isolation must not be a blanket deny. Content the contract marks public
        # stays readable with no session at all — that is what separates "scoped
        # to the caller" from "everything forbidden", and a stack that refuses
        # both would otherwise look like a passing contrast.
        async with _session() as anonymous:
            public_listing = await anonymous.get(
                f"{APP_BASE}/problems", params={"page": 1, "pageSize": 1}
            )
            public_detail = await anonymous.get(f"{APP_BASE}/problems/{problem_id}")
        # A status code alone is not readability: a proxy fallback, or a handler
        # that exposes no problem data, can still answer 200. Parse the same
        # Result envelope the authenticated controls use, and require the selected
        # problem to come back from the detail.
        public_items = (
            _data(public_listing).get("items")
            if public_listing.status_code == 200
            else None
        )
        public_detail_id = (
            _data(public_detail).get("id")
            if public_detail.status_code == 200
            else None
        )
        print(
            f"public control anonymous_listing={public_listing.status_code} "
            f"anonymous_detail={public_detail.status_code} "
            f"listing_rows={len(public_items) if isinstance(public_items, list) else 'none'} "
            f"detail_id_matches={'yes' if public_detail_id == problem_id else 'no'}"
        )
        if not isinstance(public_items, list) or not public_items:
            print("FAIL reason=public_content_not_readable")
            return 1
        if isinstance(public_detail_id, bool) or public_detail_id != problem_id:
            # `True == 1`, so a boolean id must not match problem 1.
            print("FAIL reason=public_content_not_readable")
            return 1

    # The label must follow the targets actually validated: evidence gathered
    # through the remote opt-in is not local.
    remote = os.environ.get(REMOTE_WRITE_OPT_IN) == "1"
    print(
        "OK isolation dual_account_contrast "
        + ("remote_opt_in" if remote else "local_stack_only")
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:  # noqa: BLE001 - fixed status label only
        print(f"FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
