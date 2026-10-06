"""UltiCode HTTP client: Result envelope, cookie session, and offline write contract.

The client exposes the Java LearningPlan wire contract for offline preparation;
it does not register tools or enable confirmation/runtime flows. Access and
refresh JWTs are issued only as HttpOnly cookies, and refresh is never forwarded.
"""

from __future__ import annotations

import json
import re
import unicodedata
from collections.abc import Callable
from types import TracebackType

import httpx


def _reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate key")
        result[key] = value
    return result


SUBMISSION_ID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

SEARCH_QUERY_MIN = 2
SEARCH_QUERY_MAX = 200
SEARCH_LIMIT_MAX = 100


class UlticodeError(RuntimeError):
    """The Result envelope reported failure, or the body was not a valid envelope."""


class UlticodeServiceError(UlticodeError):
    """A Result error retaining safe status and code, never server text."""

    def __init__(self, code: int, status_code: int = 200) -> None:
        super().__init__("service_error")
        self.code = code
        self.status_code = status_code


LEARNING_PLAN_UUID_PATTERN = SUBMISSION_ID_PATTERN


def canonical_uuid(value: object, name: str = "value") -> str:
    if not isinstance(value, str) or not LEARNING_PLAN_UUID_PATTERN.fullmatch(value):
        raise ValueError(f"{name} must be a canonical UUID")
    return value.lower()


def _java_blank(value: str) -> bool:
    """Mirror ``LearningPlanController.isBlankLikePythonStrip``.

    Java ORs ``Character.isWhitespace`` with ``Character.isSpaceChar`` (so NBSP,
    U+2007 and U+202F count via Zs) plus an explicit U+0085; NEL is not
    ``isWhitespace`` in Java, which is why it is listed here explicitly.
    """
    return all(
        unicodedata.category(character) in {"Zs", "Zl", "Zp"}
        or ord(character) in {*range(0x09, 0x0E), 0x85, *range(0x1C, 0x20)}
        for character in value
    )


def _validate_search_payload(
    data: dict[str, object], *, page: int, limit: int
) -> dict[str, object]:
    """A ``/search`` page must describe itself, or the caller cannot scan it.

    ``page``/``limit`` echo the request (the DTO binds them) and ``total`` is the
    population the page came from; a payload that disagrees with the request is not
    a page this client can walk. A bool is not an int here: ``True == 1``, so a
    ``true`` page echo would otherwise pass as page 1. A negative ``total`` is not
    a population this client can bound.
    """
    results = data.get("results")
    total = data.get("total")
    if (
        not isinstance(results, list)
        or not isinstance(total, int)
        or isinstance(total, bool)
        or total < 0
    ):
        raise UlticodeError("invalid search response")
    if (
        isinstance(data.get("page"), bool)
        or isinstance(data.get("limit"), bool)
        or data.get("page") != page
        or data.get("limit") != limit
    ):
        raise UlticodeError("invalid search response")
    return data


class UlticodeClient:
    def __init__(
        self,
        app_base_url: str,
        auth_base_url: str,
        *,
        total_timeout: float = 10.0,
        transport: httpx.AsyncBaseTransport | None = None,
        trust_env: bool = False,
    ) -> None:
        if total_timeout <= 0:
            raise ValueError("total_timeout must be positive")
        # trust_env=False by default: with HTTP_PROXY/ALL_PROXY set, httpx would
        # otherwise send these session-carrying requests through a remote proxy.
        # Redirects stay off so a 307/308 cannot replay a body against another host.
        self._app = httpx.AsyncClient(
            base_url=app_base_url,
            timeout=total_timeout,
            transport=transport,
            trust_env=trust_env,
            follow_redirects=False,
        )
        self._auth = httpx.AsyncClient(
            base_url=auth_base_url,
            timeout=total_timeout,
            transport=transport,
            trust_env=trust_env,
            follow_redirects=False,
        )
        self._cookies = httpx.Cookies()

    @classmethod
    def for_session(
        cls,
        app_base_url: str,
        auth_base_url: str,
        *,
        access_token: str,
        csrf_token: str | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> "UlticodeClient":
        """Create an isolated request client from already-validated session cookies."""
        cookie_value = re.compile(r"^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$")
        values = (access_token,) if csrf_token is None else (access_token, csrf_token)
        if any(not isinstance(value, str) or not cookie_value.fullmatch(value) for value in values):
            raise ValueError("invalid session cookie")
        client = cls(
            app_base_url, auth_base_url, transport=transport, trust_env=False
        )
        client._cookies.set("access_token", access_token)
        if csrf_token is not None:
            client._cookies.set("csrf_token", csrf_token)
        return client

    async def principal(self) -> str:
        """Return verified /auth/me principal id; legacy `data.id` is not accepted."""
        data = await self.me()
        user = data.get("user")
        if (
            not isinstance(user, dict)
            or not isinstance(user.get("id"), str)
            or not user["id"].strip()
            or user.get("is_active") is not True
            or user.get("is_banned") is not False
        ):
            raise UlticodeError("identity_unreadable")
        return user["id"]

    async def __aenter__(self) -> "UlticodeClient":
        await self._app.__aenter__()
        await self._auth.__aenter__()
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        await self._app.__aexit__(exc_type, exc_value, traceback)
        await self._auth.__aexit__(exc_type, exc_value, traceback)

    # -- envelope and cookie handling ---------------------------------

    @staticmethod
    def _unwrap(response: httpx.Response) -> object:
        try:
            payload = json.loads(response.content, object_pairs_hook=_reject_duplicate_keys)
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise UlticodeError(f"non-JSON response (http={response.status_code})") from exc
        except ValueError as exc:
            raise UlticodeError("duplicate key in service response") from exc
        if not isinstance(payload, dict) or "code" not in payload:
            raise UlticodeError("invalid Result envelope")
        code = payload["code"]
        if (
            not isinstance(code, int)
            or isinstance(code, bool)
            or not -2_147_483_648 <= code <= 2_147_483_647
        ):
            raise UlticodeError("invalid Result envelope")
        if response.is_success and code == 0:
            return payload.get("data")
        raise UlticodeServiceError(code, response.status_code)

    def _clear_session_cookies(self) -> None:
        self._cookies.clear()
        self._auth.cookies.clear()
        self._app.cookies.clear()

    @staticmethod
    def _unwrap_dict(response: httpx.Response) -> dict[str, object]:
        data = UlticodeClient._unwrap(response)
        if not isinstance(data, dict):
            raise UlticodeError("invalid response shape")
        return data

    def _session_headers(self) -> dict[str, str]:
        access_cookies = [
            cookie
            for cookie in self._cookies.jar
            if cookie.name == "access_token" and cookie.value
        ]
        if len(access_cookies) != 1:
            return {}
        return {"Cookie": f"access_token={access_cookies[0].value}"}

    def cookie_names(self) -> list[str]:
        """Cookie names only — values must never be printed or persisted."""
        return sorted(cookie.name for cookie in self._cookies.jar)

    def _write_session_headers(self, idempotency_key: str) -> dict[str, str]:
        access = [cookie.value for cookie in self._cookies.jar if cookie.name == "access_token"]
        csrf = [cookie.value for cookie in self._cookies.jar if cookie.name == "csrf_token"]
        if len(access) != 1 or not access[0]:
            raise UlticodeError("session_required")
        if len(csrf) != 1 or not csrf[0]:
            raise UlticodeError("csrf_token_required")
        idempotency_key = canonical_uuid(idempotency_key, "idempotency_key")
        csrf_value = csrf[0]
        return {
            "Cookie": f"access_token={access[0]}; csrf_token={csrf_value}",
            "X-CSRF-Token": csrf_value,
            "Idempotency-Key": idempotency_key,
        }

    @staticmethod
    def _validate_learning_plan_payload(
        source_submission_id: str, draft_version: int, title: str, content: str
    ) -> None:
        canonical_uuid(source_submission_id, "source_submission_id")
        if type(draft_version) is not int or not 1 <= draft_version <= 2_147_483_647:
            raise ValueError("draft_version must be a positive 32-bit integer")
        for name, value, limit in (("title", title, 200), ("content", content, 16000)):
            if not isinstance(value, str) or _java_blank(value) or len(value) > limit:
                raise ValueError(f"{name} must be non-blank and within its code-point limit")
            try:
                value.encode("utf-8")
            except UnicodeEncodeError:
                raise ValueError(f"{name} must be valid UTF-8") from None

    async def save_learning_plan(
        self,
        *,
        source_submission_id: str,
        draft_version: int,
        title: str,
        content: str,
        idempotency_key: str,
    ) -> dict[str, object]:
        self._validate_learning_plan_payload(source_submission_id, draft_version, title, content)
        source_submission_id = canonical_uuid(source_submission_id, "source_submission_id")
        headers = self._write_session_headers(idempotency_key)
        response = await self._app.post(
            "/learning-plans",
            json={
                "sourceSubmissionId": source_submission_id,
                "draftVersion": draft_version,
                "title": title,
                "content": content,
            },
            headers=headers,
        )
        return self._unwrap_dict(response)

    async def get_learning_plan(self, plan_id: str) -> dict[str, object]:
        plan_id = canonical_uuid(plan_id, "plan_id")
        response = await self._app.get(
            f"/learning-plans/{plan_id}", headers=self._session_headers()
        )
        return self._unwrap_dict(response)

    async def get_learning_plan_by_key(self, key: str) -> dict[str, object]:
        key = canonical_uuid(key, "key")
        response = await self._app.get(
            f"/learning-plans/by-key/{key}", headers=self._session_headers()
        )
        return self._unwrap_dict(response)

    # -- read-only operations -----------------------------------------

    async def list_problems(
        self, *, page: int = 1, page_size: int = 10, search: str | None = None
    ) -> dict[str, object]:
        params: dict[str, object] = {"page": page, "pageSize": page_size}
        if search:
            params["search"] = search
        response = await self._app.get("/problems", params=params)
        data = self._unwrap_dict(response)
        return data

    async def get_problem(self, problem_id: int) -> dict[str, object]:
        if (
            isinstance(problem_id, bool)
            or not isinstance(problem_id, int)
            or not 1 <= problem_id <= 9_223_372_036_854_775_807
        ):
            raise ValueError("problem_id must be a positive integer")
        response = await self._app.get(f"/problems/{problem_id}")
        data = self._unwrap_dict(response)
        return data

    async def login(self, username: str, password: str) -> dict[str, object]:
        self._clear_session_cookies()
        response = await self._auth.post(
            "/auth/login", json={"username": username, "password": password}
        )
        try:
            data = self._unwrap_dict(response)
            self._cookies.update(response.cookies)
            access_cookies = [
                cookie
                for cookie in self._cookies.jar
                if cookie.name == "access_token" and cookie.value
            ]
            if len(access_cookies) != 1:
                raise UlticodeError("login_error")
        except Exception:
            self._clear_session_cookies()
            raise
        return data

    async def list_my_submissions(
        self, *, page: int = 1, page_size: int = 10
    ) -> dict[str, object]:
        response = await self._app.get(
            "/submissions",
            params={"page": page, "pageSize": page_size},
            headers=self._session_headers(),
        )
        data = self._unwrap_dict(response)
        return data

    async def list_problem_submissions(
        self, problem_id: int, *, page: int = 1, page_size: int = 10
    ) -> dict[str, object]:
        if (
            isinstance(problem_id, bool)
            or not isinstance(problem_id, int)
            or not 1 <= problem_id <= 9_223_372_036_854_775_807
        ):
            raise ValueError("problem_id must be a positive 64-bit integer")
        response = await self._app.get(
            f"/problems/{problem_id}/submissions",
            params={"page": page, "pageSize": page_size},
            headers=self._session_headers(),
        )
        return self._unwrap_dict(response)

    async def search_problems(
        self,
        query: str,
        *,
        page: int = 1,
        limit: int = 10,
        on_payload: Callable[[dict[str, object]], None] | None = None,
    ) -> dict[str, object]:
        """Public full-text search over problems/users/posts/solutions.

        The endpoint is ``permitAll``, so an anonymous client sends no cookie and
        still works. When this client *is* authenticated, the request carries its
        current ``access_token`` cookie — and nothing else: the refresh and CSRF
        cookies are never forwarded, and no refresh is attempted.

        ``on_payload`` sees the successful Result's ``data`` *before* the page
        contract is checked. A page that does not describe itself is still
        rejected — nothing here is relaxed — but a caller scanning for leaked rows
        gets the hits that did arrive instead of losing them to the error. A body
        that is not a successful envelope with a dict ``data`` never reaches the
        callback.
        """
        if (
            not isinstance(query, str)
            or not SEARCH_QUERY_MIN <= len(query) <= SEARCH_QUERY_MAX
        ):
            raise ValueError("query must be 2-200 characters")
        if isinstance(page, bool) or not isinstance(page, int) or page < 1:
            raise ValueError("page must be a positive integer")
        if (
            isinstance(limit, bool)
            or not isinstance(limit, int)
            or not 1 <= limit <= SEARCH_LIMIT_MAX
        ):
            raise ValueError("limit must be between 1 and 100")
        response = await self._app.get(
            "/search",
            params={"query": query, "page": page, "limit": limit},
            headers=self._session_headers(),
        )
        data = self._unwrap_dict(response)
        if on_payload is not None:
            on_payload(data)
        return _validate_search_payload(data, page=page, limit=limit)

    async def get_my_submission(self, submission_id: str) -> dict[str, object]:
        try:
            submission_id = canonical_uuid(submission_id, "submission_id")
        except ValueError:
            raise ValueError("submission UUID must be canonical") from None
        response = await self._app.get(
            f"/submissions/{submission_id}", headers=self._session_headers()
        )
        data = self._unwrap_dict(response)
        return data

    async def me(self) -> dict[str, object]:
        response = await self._auth.get("/auth/me", headers=self._session_headers())
        data = self._unwrap_dict(response)
        return data
