# UltiCode Agent

Standalone Python Agent runtime for owner-scoped, read-only analysis, persistent multi-turn conversations, and the explicit LearningPlan workflow. It is not a Maven service or business-data owner. Java remains authoritative for identity, submission access, and persisted LearningPlan records; Agent SQLite stores private conversation/turn history and local LearningPlan workflow state.

## Runtime boundary

- `src/ulticode_client.py` calls the existing Auth/App and LearningPlan HTTP contracts.
- `src/ulticode_tools.py` exposes allowlisted read-only tools and projects results before model use.
- `src/agent_service/graph.py` implements the bounded LangGraph model/tool kernel; `src/agent_loop.py:run_tool_loop` is a compatibility wrapper.
- `src/agent_service/app.py` creates the conversation and LearningPlan workflow APIs. LearningPlan persistence routes are mounted only when persistence is explicitly enabled.
- Conversation turns use separate status, run, event, and cancellation state from LearningPlan threads; deleting a conversation does not delete saved plans.
- Session identity comes only from `/auth/me`. Each request has an isolated in-memory cookie client; unsafe Agent requests require the access and CSRF cookie pair and matching CSRF header.
- Java LearningPlan persistence requires a separate explicit confirmation and save. Unknown write outcomes remain unresolved until same-key Java readback; the Agent does not automatically retry with a new key.

## Live analysis and persistence

Live analysis is off by default. Set `ULTICODE_AGENT_LIVE_ANALYSIS=1` and provide `DEEPSEEK_API_KEY` to enable it. With a loopback `ULTICODE_AGENT_ORIGIN`, omitting `ULTICODE_AGENT_BUDGET_POLICY` enables local development calls without the shared cumulative budget ledger; each analysis still has per-request token and attempt limits, but no cumulative cost ceiling. For non-loopback deployments, configure an existing policy with an already-active budget period. Startup reads and reconciles that accounting state; it does not create or activate a period. The shared spend guard enforces the configured ceiling, and unknown usage remains reserved.

Persistence is separately disabled by default. Set `ULTICODE_AGENT_PERSISTENCE_ENABLED=1` when the workflow store and its write routes should be available; owner checks, CSRF, explicit confirmation, idempotency, and Java readback still apply. The manual analysis-only mode requires its preconfigured budget and cannot be combined with persistence. The retired U02 gate and expected head/base SHA settings are not accepted.

The model adapter reads its API key from the environment and never logs it. Keep credentials in a secure environment or secret store, not in commands, shell history, or documentation.

## Optional MCP read-only bridge

The local stdio server in `src/agent_mcp_server.py` exposes only `get_my_submissions`, reusing the authenticated Java client and strict field projection from the direct Agent tool. It has no shell, SQL, file, or write tools. For standalone stdio use, configure `ULTICODE_APP_BASE`, `ULTICODE_AUTH_BASE`, and `ULTICODE_AGENT_ACCESS_TOKEN` in the MCP host's secure environment; `ULTICODE_AGENT_CSRF_TOKEN` is optional for this read-only GET. Start it from this directory with:

```bash
uv run python src/agent_mcp_server.py
```

The in-process MCP Client path is opt-in through `ULTICODE_AGENT_MCP_TOOLS=1`; unset or `0` keeps the direct tool path. It verifies the expected tool and argument schema before the model loop, and validates/projects the response again before returning it to the graph. The tool accepts only optional `page` (default `1`, range `1..2147483647`) and `pageSize` (default `5`, range `1..100`). It returns only `id`, `problemId`, `language`, `status`, `createdAt`, and paging totals; source code and user identity are excluded. Protocol errors, business errors, invalid arguments, timeouts, and cancellation are handled separately; cancellation closes the request-scoped MCP session.

The direct path remains the default because one in-process tool needs less protocol setup. MCP adds no business capability or authorization guarantee by itself. The integration uses the official Python SDK `mcp==2.3.0` and negotiates protocol version `2026-07-28`; `uv.lock` pins the dependency graph.

## Local checks

From the repository root:

```bash
cd services/agent
uv sync --locked
uv run pytest -q
```

These checks use local test doubles and do not require live service or model credentials.

## Source and citation boundaries

The checked-in Agent corpus contains synthetic or project-authored samples for local use; it is not a real user submission, an UltiCode DTO, or licensed user material. Public user solutions are not a licensed corpus merely because their API is public. Any source sent to a model must stay within its recorded permission, scope, version, chunk/source position, and exact model-input projection. Retrieved source text and tool results are untrusted data, never instructions.

In manual analysis-only mode, each owner-source conversation turn requires explicit consent. Full submission source and citation excerpts remain request-scoped; they are not written to conversation/turn rows, events, or checkpoints. Citations retain safe references without the quoted source text. LearningPlan thread excerpts are redacted before storage. Logs record only a safe failure stage and reason code, never prompts, source, model answers, credentials, or raw server errors.

Workflow route and field contracts are documented in the canonical [API reference](../../docs/REFERENCE.md#agent-workflow-api).
