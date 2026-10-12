"""One local MCP endpoint over the existing authenticated read-only tool."""

from __future__ import annotations

import asyncio
import os
import sys

from mcp.server import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from pydantic import StrictInt

from ulticode_client import UlticodeClient, UlticodeError
from ulticode_tools import build_tools, parse_my_submissions_arguments

TOOL_NAME = "get_my_submissions"
TOOL_DESCRIPTION = (
    "Read the authenticated user's paged submission metadata. Java enforces the "
    "session and owner scope; source code and user identity fields are excluded."
)


def create_server(client: UlticodeClient) -> MCPServer:
    handler = build_tools(client)[TOOL_NAME]
    server = MCPServer(name="ulticode-agent-readonly", version="0.1.0")

    @server.tool(
        name=TOOL_NAME,
        description=TOOL_DESCRIPTION,
        structured_output=True,
    )
    async def get_my_submissions(
        page: StrictInt = 1, pageSize: StrictInt = 5
    ) -> dict[str, object]:
        try:
            normalized_page, page_size = parse_my_submissions_arguments(
                {"page": page, "pageSize": pageSize}
            )
        except ValueError:
            raise ToolError("invalid_arguments") from None
        try:
            return await handler({"page": normalized_page, "pageSize": page_size})  # type: ignore[no-any-return]
        except UlticodeError:
            raise ToolError("business_error") from None
        except ValueError:
            raise ToolError("business_error") from None
        except Exception:
            raise ToolError("tool_error") from None

    return server


async def _run_stdio() -> None:
    app_base = os.environ.get("ULTICODE_APP_BASE", "")
    auth_base = os.environ.get("ULTICODE_AUTH_BASE", "")
    access_token = os.environ.get("ULTICODE_AGENT_ACCESS_TOKEN", "")
    csrf_token = os.environ.get("ULTICODE_AGENT_CSRF_TOKEN") or None
    if not app_base or not auth_base or not access_token:
        raise ValueError("mcp_configuration_invalid")

    client = UlticodeClient.for_session(
        app_base,
        auth_base,
        access_token=access_token,
        csrf_token=csrf_token,
    )
    async with client:
        await create_server(client).run_stdio_async()


def main() -> None:
    try:
        asyncio.run(_run_stdio())
    except (ValueError, RuntimeError):
        print("mcp_configuration_or_runtime_error", file=sys.stderr)
        raise SystemExit(2) from None


if __name__ == "__main__":
    main()
