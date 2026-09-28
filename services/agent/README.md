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

### Corpus override for the acceptance entry point

`e2e_citation_support_model.py` can analyse a corpus outside the repository instead of the
pinned sample, without editing either:

```bash
# `DEEPSEEK_API_KEY` must already be in the environment (exported by the operator or
# injected by the secret store) — it is never part of this command line, and without
# it the run stops at `deepseek_api_key_required`.
ULTICODE_CITATION_SUPPORT=1 DEEPSEEK_MODEL=<model> \
ULTICODE_CITATION_CORPUS_DIR=/absolute/path/to/corpus \
ULTICODE_CITATION_CORPUS_MANIFEST=/absolute/path/to/manifest.json \
uv run python e2e_citation_support_model.py
```

Contract — every violation is a fixed `FAIL reason=...` evidence line and exit 1:

- **Both variables or neither.** One set alone → `corpus_source_incomplete`. The run never
  falls back to the pinned corpus: reporting evidence from a different corpus than the one it
  was asked for is worse than stopping.
- **The root is a real directory**, not a symlink (`corpus_root_unusable`), and the manifest
  must declare at least one entry (`corpus_empty`).
- **Declarations come from the manifest.** Missing, unreadable, invalid or non-conforming
  manifest → `corpus_manifest_unusable`. Every entry must declare exactly `ACCEPTED_PERMISSION`
  and `ACCEPTED_SCOPE`, the policy the run pins in source → `corpus_declaration_unsupported`.
- **One file per entry**, resolved through the manifest's own `source_path` basename under the
  corpus directory: symlinked entry → `corpus_entry_escapes_root`; missing file →
  `corpus_entry_missing`; two entries resolving to one file → `corpus_entry_duplicate_source`
  (a single fragment must never count as several citations); empty or over `MAX_SOURCE_CHARS`
  → `corpus_entry_unusable`.
- **Positions are derived from the loaded text.** A manifest position that does not match the
  file → `corpus_entry_position_mismatch`, so a citation cannot cite a location that does not
  exist.
- With neither variable set, behaviour is exactly the pinned, manifest-gated sample corpus.

The policy is two pinned constants in `e2e_citation_support_model.py`. Authorised material
(DAV-58) changes them together with its manifest in a reviewed commit; a corpus file cannot
grant itself a policy. The evidence line and the verdict metadata report that pinned permission
as `corpus=...`, so a non-synthetic corpus is never labelled synthetic.

`data/keyword_cases.json` annotates every case with `required_evidence`, `answerable`,
`expected_behavior` (`cite`, `no_evidence`, or `refuse`), `allowed_behavior`, and
`forbidden_behavior`; the loader rejects a case missing any of them. `refuse` marks questions the
corpus cannot answer, and each one forbids its own specific claim — locating a code line and naming
a runtime cause are different errors, so they are not collapsed into one rule.

`src/keyword_evaluation.py` records one entry per case: retrieval hit, citation traceability,
retrieval outcome, expected versus observed behavior, fabrication risk, tool calls, and elapsed
time. Any hit on a `refuse` case is recorded as a fabrication risk, and a case with no hit is not
counted as traceable because it has no citation to trace.

Retrieval facts and answer judgements are kept apart on purpose. `citation_support` and
`answer_completion` are recorded as `DEFERRED`: a traceable source id proves where a fragment came
from, not that it supports a conclusion, and deciding that needs the model or human pass tracked in
DAV-58.

`src/retrieval.py` provides bounded keyword retrieval and source metadata. `src/sourced_analysis.py`
separates observed submission facts from hypotheses and only cites retrieved fragments. Java services
remain the authority for identity, ownership, publication, and submission facts. The tool projections
validate scalar types and bounded string lengths before returning data to the model.

U02 and later phases must preserve U01's regression coverage for unknown tools, invalid arguments,
tool failure, timeout, cancellation, and the absence of raw source/error data in captured model
messages. Do not add write tools until the corresponding DAV-53 isolation and confirmation gates are
satisfied.
