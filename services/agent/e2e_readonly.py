"""DAV-41 U01 e2e — REAL read-only calls against the local UltiCode stack.

Prints status lines with counts and non-sensitive scalars only: no bodies, no
tokens, no cookie values, no submission source. Login issues a server session
(the only auth write); every business endpoint call is GET, which is what the
`business_mutations=0` label means. Credentials come from environment
variables (local `up.sh` dev administrator) — never hardcode or commit them.

Scope label: REAL local stack (not mock).

    ULTICODE_E2E_USERNAME=... ULTICODE_E2E_PASSWORD=... uv run python e2e_readonly.py
"""

from __future__ import annotations

import asyncio
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from ulticode_client import UlticodeClient
from ulticode_tools import build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")


async def main() -> int:
    username = os.environ["ULTICODE_E2E_USERNAME"]
    password = os.environ["ULTICODE_E2E_PASSWORD"]

    async with UlticodeClient(APP_BASE, AUTH_BASE) as client:
        tools = build_tools(client)

        problems = await client.list_problems(page=1, page_size=3)
        print(f"OK GET /problems code=0 items={len(problems['items'])}")

        first_id = int(problems["items"][0]["id"])
        detail = await tools["get_problem"]({"id": first_id})
        print(f"OK GET /problems/{{id}} code=0 difficulty={detail['difficulty']}")

        await client.login(username, password)
        print("OK POST /auth/login code=0 session=established")

        listing = await tools["get_my_submissions"]({"page": 1, "pageSize": 3})
        print(f"OK GET /submissions code=0 items={len(listing['items'])}")

    print(
        "E2E READ-ONLY PASS | scope=REAL local stack | "
        "business_mutations=0 (login issues a server session; every business endpoint was GET)"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:
        print(f"E2E READ-ONLY FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
