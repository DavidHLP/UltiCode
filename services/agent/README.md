# UltiCode Agent

Python agent runtime for UltiCode's read-only learning workflow. It is a standalone service
module: it does not own business tables, does not write UltiCode data, and never replaces the
Java Owner services for identity, submissions, judging, or persistence.

## Runtime boundary

- `src/ulticode_client.py` calls existing Auth/App HTTP contracts with a server-side cookie session.
- `src/ulticode_tools.py` projects tool results to the model; submission source, identity, error
  details, test payloads, and nested user/problem objects are excluded by default.
- `src/agent_loop.py` owns bounded model → tool → result rounds, timeout, and cancellation behavior.

The current migrated slice provides the U01 read-only client, bounded model/tool loop, field
projection, deterministic tests, a U02 sample-only keyword retrieval/sourced-analysis path, and
executable keyword evaluation tooling. Authorized-corpus, vector-retrieval, real-model evaluation,
and isolation evidence are tracked in the U02 Linear tasks.
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

Real local-stack smoke checks are opt-in and require the existing UltiCode environment:

```bash
ULTICODE_E2E_USERNAME=... ULTICODE_E2E_PASSWORD=... uv run python e2e_readonly.py
ULTICODE_E2E_USERNAME=... ULTICODE_E2E_PASSWORD=... DEEPSEEK_API_KEY=... DEEPSEEK_MODEL=... \
  uv run python e2e_model_qa.py
ULTICODE_E2E_USERNAME=... ULTICODE_E2E_PASSWORD=... uv run python e2e_sourced_analysis.py
ULTICODE_E2E_USERNAME=... ULTICODE_E2E_PASSWORD=... DEEPSEEK_API_KEY=... DEEPSEEK_MODEL=... \
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

U02 is preparing authorized-corpus retrieval and sourced analysis. The checked-in corpus is an
agent-authored synthetic sample for local tests only: it is not a real user submission, an
UltiCode DTO, or licensed user material. Public user solutions are not a licensed corpus merely
because their API is public. Before any future source is sent to a model, record its permission,
scope, version, chunk/source position, and the exact model-input projection. Retrieved source text
is untrusted data, never an instruction source.

The synthetic sample is explicitly `synthetic` and `agent-authored-synthetic`; it is suitable for
the deterministic sample slice only. The executable keyword evaluation is versioned with the Agent
module; authorized-corpus, vector-retrieval, real-model evaluation, and isolation evidence are
tracked in the U02 Linear tasks.

U02 and later phases must preserve U01's regression coverage for unknown tools, invalid arguments,
tool failure, timeout, cancellation, and the absence of raw source/error data in captured model
messages. Do not add write tools until the corresponding DAV-53 isolation and confirmation gates are
satisfied.


### Opt-in acceptance runners (DAV-58 / DAV-53)

- `e2e_boundary_evaluation.py` runs the real model over the six synthetic boundary
  categories — `missing_id`, `no_tool`, `no_hit`, `tool_failure`, `source_injection`,
  `wrong_citation` — and fails closed when the citation gate rejects an emitted citation,
  when a behaviour misses, or when a forged or unsupported negative control is not rejected.
- `e2e_account_isolation.py` records the dual-account HTTP contrast beside the own-account
  positive control. Without a configured model the run reports the HTTP contrast and exits
  non-zero as `INCOMPLETE`; that is not a DAV-53 pass.

Commands, ledger binding and the full validation contracts live in the canonical
[Development and testing guide](../../docs/DEVELOPMENT.md).

### Corpus override for the acceptance entry point

The corpus-override command and validation contract for
`e2e_citation_support_model.py` are documented in the canonical
[Development and testing guide](../../docs/DEVELOPMENT.md).
