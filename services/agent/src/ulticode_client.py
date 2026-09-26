"""Read-only UltiCode HTTP client: Result envelope + cookie session.

Contract facts (verified against the local stack, 2026-09-24):

- Success envelope: ``{"code": 0, "message": "success", "data": ...}``;
  failures carry a non-zero ``code`` plus HTTP error status.
- ``POST /auth/login`` (auth service) sets ``Set-Cookie`` for ``access_token``
  / ``refresh_token`` / ``csrf_token``. Access/refresh JWTs are issued only as
  HttpOnly cookies; the JSON body additionally returns ``csrfToken`` plus the
  user profile. ``csrf_token`` is a readable double-submit cookie — state-
  changing requests would send ``X-CSRF-Token``; this client is GET-only and
  never mutates, so no CSRF header is involved.
- Cookies are stored in an ``httpx.Cookies`` jar and sent as one explicit
  ``Cookie`` header, because the local stack is plain http while cookies may
  be Secure-flagged (jar-only attachment would silently drop them).

Read-only by design: no method mutates server state. Identity always comes
from the server-side session cookie — callers cannot inject a user id.
"""

from __future__ import annotations

from types import TracebackType

import httpx


class UlticodeError(RuntimeError):
    """The Result envelope reported failure, or the body was not a valid envelope."""


class UlticodeClient:
    def __init__(
        self,
        app_base_url: str,
        auth_base_url: str,
        *,
        total_timeout: float = 10.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if total_timeout <= 0:
            raise ValueError("total_timeout must be positive")
        self._app = httpx.AsyncClient(
            base_url=app_base_url, timeout=total_timeout, transport=transport
        )
        self._auth = httpx.AsyncClient(
            base_url=auth_base_url, timeout=total_timeout, transport=transport
        )
        self._cookies = httpx.Cookies()

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
            payload = response.json()
        except ValueError as exc:
            raise UlticodeError(f"non-JSON response (http={response.status_code})") from exc
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
        raise UlticodeError("service_error")

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
        access = next(
            (cookie for cookie in self._cookies.jar if cookie.name == "access_token"),
            None,
        )
        return {"Cookie": f"access_token={access.value}"} if access is not None else {}

    def cookie_names(self) -> list[str]:
        """Cookie names only — values must never be printed or persisted."""
        return sorted(cookie.name for cookie in self._cookies.jar)

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

    async def get_my_submission(self, submission_id: str) -> dict[str, object]:
        if not isinstance(submission_id, str) or not submission_id.strip():
            raise ValueError("submission_id must be a non-empty string")
        response = await self._app.get(
            f"/submissions/{submission_id}", headers=self._session_headers()
        )
        data = self._unwrap_dict(response)
        return data

    async def me(self) -> dict[str, object]:
        response = await self._auth.get("/auth/me", headers=self._session_headers())
        data = self._unwrap_dict(response)
        return data
