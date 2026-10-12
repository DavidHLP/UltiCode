# UltiCode Agent

Python Agent runtime for owner-scoped, read-only analysis and an explicit learning-plan workflow.
It is a standalone module, not a Maven service or business-data owner. The Java App remains the
authority for identity, submission access, and persisted LearningPlan records; Agent SQLite stores
only local draft/workflow state and events.

## Runtime boundary

- `src/ulticode_client.py` calls existing Auth/App and LearningPlan HTTP contracts.
- `src/ulticode_tools.py` exposes allowlisted read-only tools and projects results before model use.
- `src/agent_service/graph.py` implements the single bounded LangGraph model/tool kernel.
  `src/agent_loop.py:run_tool_loop` remains a compatibility wrapper over that graph.
- `src/agent_service/app.py` is the opt-in workflow API factory. It uses canonical private SQLite
  state plus resumable LangGraph checkpoints; checkpoints are not business records.
- Session identity comes only from `/auth/me`. Each request has an isolated, in-memory cookie client;
  unsafe Agent requests require the access + CSRF cookie pair and matching CSRF header.
- A Java LearningPlan is persisted only after a separate explicit confirmation and save. Unknown
  write outcomes remain unresolved until same-key Java readback; no automatic retry or new key.

### Optional MCP read-only bridge

The local stdio server in `src/agent_mcp_server.py` exposes only `get_my_submissions`, reusing the
same authenticated Java client and strict field projection as the direct Agent tool. It has no
Shell, SQL, file, or write tools. For standalone stdio use, configure `ULTICODE_APP_BASE`,
`ULTICODE_AUTH_BASE`, and `ULTICODE_AGENT_ACCESS_TOKEN` in the MCP host's secure environment;
`ULTICODE_AGENT_CSRF_TOKEN` is optional for this read-only GET. Start it from this directory with:

```bash
uv run python src/agent_mcp_server.py
```

The Agent's in-process MCP Client path is opt-in through `ULTICODE_AGENT_MCP_TOOLS=1`; unset or
`0` keeps the direct tool path. Before the model loop, each request discovers the single expected
tool and verifies its argument schema. The model's existing `get_my_submissions` call then goes
through MCP and the response is projected and validated again before it returns to the same graph.
The tool accepts only optional `page` (default `1`, range `1..2147483647`) and `pageSize` (default
`5`, range `1..100`) integer arguments. It returns only `id`, `problemId`, `language`, `status`,
`createdAt`, and paging totals; source code and user identity are excluded. Safe error labels are
`invalid_arguments`, `business_error`, `tool_error`, `protocol_error`, and `timeout`; raw transport
and server error text is discarded.
The session uses the authenticated request client directly, so cookies are not copied to a child
process. Protocol errors, business errors, invalid arguments, timeouts, and cancellation are
handled separately; cancellation closes the request-scoped MCP session. Authorization still comes
from the Java API and program code, not MCP.

The direct path remains the default because it has less protocol setup for one in-process tool.
MCP is useful when standardized discovery and an independently launchable local server are needed;
it adds no business capability or authorization guarantee by itself. This integration resolves to
the official Python SDK `mcp==2.3.0` and negotiates protocol version `2026-07-28`; `uv.lock` pins the
complete dependency graph. The focused `tests/test_ulticode_tools.py` suite includes a real stdio subprocess
against a local HTTP stub and an in-process Agent model/tool round trip; it does not call a live
UltiCode stack or model provider.

Workflow route and field contracts, U02 evidence gate, and budget behavior are in the canonical
[API reference](../../docs/REFERENCE.md#agent-workflow-api) and
[Development guide](../../docs/DEVELOPMENT.md#u03-agent-workflow).

Offline scripted-model analysis can be injected into the checkpointed workflow and uses the same
bounded read-only LangGraph kernel. Formal live analysis is fail-closed unless the evidence-bound
U02 gate, expected budget period, shared budget guard, and authorized `u03_analysis` purpose are
valid; citation judging uses the separate `u03_citation_judge` purpose. A local manual smoke mode
can bypass only the formal acceptance gate for read-only analysis using its fixed one-shot budget;
it does not mount confirmation, save, or recovery routes. Missing authorization returns
`503 model_budget_blocked` before provider calls. Nonempty citations must match same-run retrieved
evidence, pass integrity checks, and be judged as supporting and derivable; unknown judge usage
rejects analysis. Empty citations are accepted only after the bounded source-fact/tool-trace/source-
reference answer checks. These are fail-closed safeguards, not perfect semantic verification.
Offline scripted models and `httpx.MockTransport` do not establish real-model acceptance, live Java
persistence, or complete U03 acceptance.

In this manual mode only, analysis reads the exact submission already bound to the owner-verified
thread through a no-argument, request-scoped tool. Source is treated as untrusted input and capped at
12,000 characters; missing or oversized source stops before a provider call. The generic submission
listing tools remain metadata-only. Full source and citation excerpts are not written to the Agent
store, events, or checkpoints; citation excerpts are integrity-checked in memory and replaced with a
redaction note before storage. This local smoke path is not a substitute for formal U02/U03/U04
acceptance.

For the local HTTP demo, start Uvicorn with `agent_service.runtime:create_app`. Live analysis is
off by default. Formal acceptance mode requires `ULTICODE_AGENT_LIVE_ANALYSIS=1`, a current private
U02 gate, and its exact `ULTICODE_AGENT_EXPECTED_HEAD` and `ULTICODE_AGENT_EXPECTED_BASE` bindings.
For a local real-model smoke test, run `uv run python prepare_manual_live_test_budget.py` from
this directory, then set `ULTICODE_AGENT_LIVE_ANALYSIS=1` and
`ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY=1`. This creates the fixed `$182.00` budget and its empty,
private shared guard to keep the existing `$15.024914` committed plus `$2.359296` unresolved
exposure under the approved `$200` cumulative ceiling; unknown usage remains reserved. If setup
stopped after activating an unused budget, the script may finish only that exact active period
when its attempts, reservations, actual usage, and unknown usage are all zero. It never replaces an
existing guard or resets a period. The mode preserves session, owner, and source-ownership checks
but mounts only analysis. It is not formal U02/U03 acceptance. Both modes derive the period from
existing bound state and lock its shared guard; runtime startup does not create/reset accounting or
call the provider. Set `DEEPSEEK_MODEL=deepseek-flash` and keep the API key in a secure environment.
Keep one Agent server process per shared guard. Without a valid mode and budget, ordinary thread
operations remain available and analysis returns `503 model_budget_blocked`.

Runtime notes:
- `src/deepseek_model.py` is an external model adapter. Its API key is read from the environment and
  is never logged or committed.
- `e2e_*.py` are opt-in smoke scripts against the local UltiCode stack; unit tests use
  `httpx.MockTransport` and do not call a real service.

## Local checks

From the repository root:

```bash
cd services/agent
uv sync --locked
uv run pytest -q
```

Real-stack smoke checks are opt-in. Supply account/model credentials through an existing secure
environment or secret store; never put their values in commands, shell history, or documentation.
Run from `services/agent`:

```bash
uv run python e2e_readonly.py
uv run python e2e_model_qa.py
uv run python e2e_sourced_analysis.py
uv run python e2e_sourced_analysis_model.py
```

Both sourced-analysis smokes require the authenticated account to have at least one
`Wrong Answer` submission anywhere in its reported pages; otherwise they exit with a fixed
`no_wrong_answer_submission` status. The scan continues until it finds a match or exhausts the
owner-reported total, so an account with a long submission history issues one read-only listing
request per page. The smoke scripts emit only fixed status labels and item
counts. They do not print response bodies, cookie names or values, tokens, submission source,
usernames, roles, tool names, source text, or model answer content.

### Answer-level evaluation (development split only)

For the answer-evaluation contract, dynamic call-budget calculation, opt-in command and artifact
behavior, see the canonical [Development and testing guide](../../docs/DEVELOPMENT.md).

## U02 boundary

U02 covers authorized-corpus retrieval and sourced analysis. The checked-in corpus is an
agent-authored synthetic sample for local tests only: it is not a real user submission, an
UltiCode DTO, or licensed user material. Public user solutions are not a licensed corpus merely
because their API is public. Any source sent to a model requires recorded permission, scope,
version, chunk/source position, and exact model-input projection. Retrieved source text is
untrusted data, never an instruction source.

The synthetic sample is explicitly `synthetic` and `agent-authored-synthetic`; it is suitable for
the deterministic sample slice only. The executable keyword evaluation is versioned with the Agent
module; authorized-corpus, vector-retrieval, real-model evaluation, and isolation evidence are
tracked in the U02 Linear tasks.

U02 and later phases preserve U01 regression coverage for unknown tools, invalid arguments, tool
failure, timeout, cancellation, and exclusion of raw source/error data from model messages. Model-
facing tools remain read-only. The explicit Agent save route is separate and remains unavailable
unless its evidence-bound U02 gate validates.


### Opt-in acceptance runners (DAV-58 / DAV-53)

- `e2e_boundary_evaluation.py` runs the real model over the six synthetic boundary
  categories — `missing_id`, `no_tool`, `no_hit`, `tool_failure`, `source_injection`,
  `wrong_citation` — and fails closed when the citation gate rejects an emitted citation,
  when a behaviour misses, or when a forged or unsupported negative control is not rejected.
- `e2e_account_isolation.py` records the dual-account HTTP contrast beside the own-account
  positive control. Without a configured model the run reports the HTTP contrast and exits
  non-zero as `INCOMPLETE`; that is not a DAV-53 pass.

Existing runner commands, ledger binding, and validation contracts live in the canonical
[Development and testing guide](../../docs/DEVELOPMENT.md). The frozen-candidate U02 → U03 → U04
commands, evidence bindings, and quality-layer interpretation are documented in the
[immutable acceptance workflow](../../docs/DEVELOPMENT.md#u02-u03-u04-immutable-acceptance-chain).

### Corpus override for the acceptance entry point

The corpus-override command and validation contract for
`e2e_citation_support_model.py` are documented in the canonical
[Development and testing guide](../../docs/DEVELOPMENT.md).
