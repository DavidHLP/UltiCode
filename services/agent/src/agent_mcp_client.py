"""Request-scoped MCP Client bridge for the one allowlisted submission tool."""

from __future__ import annotations

import asyncio
from contextlib import AsyncExitStack

from mcp import Client

from agent_mcp_server import TOOL_NAME, create_server
from ulticode_tools import parse_my_submissions_arguments, project_my_submissions_listing

MCP_CALL_TIMEOUT_SECONDS = 5.0
_TOOL_ERRORS = {"business_error", "invalid_arguments", "tool_error"}


class MCPProtocolError(RuntimeError):
    """The expected read-only MCP tool could not be discovered safely."""


def _valid_tool_definition(tool: object) -> bool:
    schema = getattr(tool, "input_schema", None)
    if not isinstance(schema, dict):
        return False
    properties = schema.get("properties")
    if not isinstance(properties, dict) or set(properties) != {"page", "pageSize"}:
        return False
    if schema.get("required", []) != []:
        return False
    return all(
        isinstance(properties[name], dict)
        and properties[name].get("type") == "integer"
        for name in ("page", "pageSize")
    )


class MCPSubmissionSession:
    """Discover once before the model loop and close the protocol session after it."""

    def __init__(self, business_client: object) -> None:
        self._business_client = business_client
        self._stack: AsyncExitStack | None = None
        self.client: Client | None = None

    async def __aenter__(self) -> dict[str, object]:
        stack = AsyncExitStack()
        self._stack = stack
        try:
            self.client = await stack.enter_async_context(
                Client(
                    create_server(self._business_client),
                    read_timeout_seconds=MCP_CALL_TIMEOUT_SECONDS,
                )
            )
            discovered = await self.client.list_tools(cache_mode="refresh")
            if (
                discovered.next_cursor is not None
                or len(discovered.tools) != 1
                or discovered.tools[0].name != TOOL_NAME
                or not _valid_tool_definition(discovered.tools[0])
            ):
                raise MCPProtocolError("mcp_discovery_failed")
        except asyncio.CancelledError:
            await stack.aclose()
            self._stack = None
            self.client = None
            raise
        except Exception:
            await stack.aclose()
            self._stack = None
            self.client = None
            raise MCPProtocolError("mcp_discovery_failed") from None
        return {TOOL_NAME: self._call}

    async def __aexit__(self, exc_type, exc_value, traceback) -> None:
        stack, self._stack = self._stack, None
        self.client = None
        if stack is not None:
            await stack.__aexit__(exc_type, exc_value, traceback)

    async def _call(self, arguments: dict[str, object]) -> object:
        try:
            page, page_size = parse_my_submissions_arguments(arguments)
        except (TypeError, ValueError):
            return {"error": "invalid_arguments"}
        if self.client is None:
            return {"error": "protocol_error"}
        try:
            async with asyncio.timeout(MCP_CALL_TIMEOUT_SECONDS):
                result = await self.client.call_tool(
                    TOOL_NAME,
                    {"page": page, "pageSize": page_size},
                    read_timeout_seconds=MCP_CALL_TIMEOUT_SECONDS,
                )
        except TimeoutError:
            return {"error": "timeout"}
        except asyncio.CancelledError:
            raise
        except Exception:
            return {"error": "protocol_error"}

        if result.is_error:
            safe_text = " ".join(
                text for item in result.content
                if isinstance((text := getattr(item, "text", None)), str)
            )
            label = next(
                (error for error in _TOOL_ERRORS if error in safe_text),
                "business_error",
            )
            return {"error": label}
        try:
            return project_my_submissions_listing(
                result.structured_content,
                page=page,
                page_size=page_size,
            )
        except (TypeError, ValueError):
            return {"error": "protocol_error"}
