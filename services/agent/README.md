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

`src/keyword_evaluation.py` measures retrieval only, so it records `citation_support` and
`answer_completion` as `DEFERRED` and `observed_behavior` as `not_measured` — a traceable
source id says where a fragment came from, not that it supports a conclusion. The answer-level
columns come from `src/answer_evaluation.py`, which adds an answer pass and a judging pass on
top of the same pinned keyword retrieval. The answer pass sees only the question and retrieved
fragments; expected/allowed/forbidden outcomes are withheld until judging. It returns explicit
retrieved chunk IDs as citations, and the judge/artifact use only those IDs, not every retrieval hit.

```bash
ULTICODE_ANSWER_EVAL=1 DEEPSEEK_MODEL=<model> uv run python e2e_answer_evaluation.py
```

Scope is enforced by the evaluator, not by the caller: it raises on any case outside
`development`, so `holdout` and `holdout2` stay sealed. The answer response must include `text`
and a `citations` array containing unique IDs from that case's retrieved fragments; empty citations
are valid when the answer cites nothing. An empty citation list records `citation_support` as
`not_applicable` rather than as a failed check. The run writes a run-scoped artifact under the
state directory carrying `scope=development_only`, `sealed_splits`, `judge=model`,
`human_review=not_performed` and every row — this is machine evidence, not human review.

Budget: two logical passes per case (answer, then judge), with up to three attempts per pass.
Before the first billed call, the entry requires `DEEPSEEK_MAX_CALLS` to cover six calls per case,
including retry capacity. The default ceiling remains 64; larger batches require an explicit
ceiling covering the full plan. A transport error or timeout is retried per call, not per batch, and a
retried attempt is billed, so each row records the attempts actually made in `model_calls`
rather than a fixed two. A protocol failure is not retried because the same input yields the
same shape. The destination artifact is reserved before the first billed call, an existing
artifact is never overwritten: complete bytes are published through a no-clobber hard link. The corpus
and case file are snapshotted once before the calls so the artifact identifies the material actually judged.
Tune `DEEPSEEK_TIMEOUT` (seconds, default 120) for a reasoning model that can exceed the
adapter's 30s default on one response.

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
  declares nothing at all → `corpus_empty`, distinguished from a manifest that will not parse
  (`corpus_manifest_unusable`).
- **Declarations come from the manifest.** Missing, unreadable, invalid or non-conforming
  manifest → `corpus_manifest_unusable`. Every entry must declare exactly the material class
  the run pins in source — `ACCEPTED_PERMISSION`, `ACCEPTED_SCOPE`, `ACCEPTED_SAMPLE_KIND` and
  `ACCEPTED_ACCESS_SCOPE` (all four change together for authorised material) →
  `corpus_declaration_unsupported`. The manifest is read **once**: its entries, the documents
  they describe and the pinned class travel as one immutable snapshot through retrieval, the
  worksheet and the verdict metadata, so replacing the file mid-run cannot leave them
  describing different material.
- **One file per entry**, named by the manifest's own `source_path`, which must be a plain
  name directly under the corpus directory — an absolute or external path is not the file that
  was opened and is refused as `corpus_entry_path_not_relative`. The root is opened once with
  `O_DIRECTORY|O_NOFOLLOW` and every entry relative to that descriptor — so neither the root nor
  an entry can be swapped for a link between the check and the read: symlinked entry →
  `corpus_entry_escapes_root`;
  missing file →
  `corpus_entry_missing`; two entries resolving to the *same file* — including two hard-link
  names for one inode — → `corpus_entry_duplicate_source`, and copies under separate names →
  `corpus_entry_duplicate_content`, compared on the canonical text so CRLF/LF and insignificant
  whitespace variants are the same fragment (one fragment must never count as several
  citations); unreadable, not UTF-8, empty, over `MAX_SOURCE_CHARS`, containing a NUL
  byte in the declared path, or a read that stops short of EOF — a prefix that passes the
  size check while a suffix stays unread → `corpus_entry_unusable`.
- **Positions are derived from the raw file**, spanning the first to the last non-blank
  physical line, so leading blank lines are covered and content starting on line 3 reports
  `lines 3-5` rather than `lines 1-5`. A manifest position that does not match →
  `corpus_entry_position_mismatch`, so a citation cannot cite a location that does not exist.
- **The manifest is bound to its files at preflight**: entries whose `content_digest` or
  derived `chunk_id` disagree with the document they describe are refused before the run
  logs in → `corpus_entry_unbound`.
- **`ULTICODE_CITATION_REQUIRED_ROWS` above the retrieval limit is refused** rather than
  reported as a material gap: retrieval caps at `MAX_RESULTS` (3), so a higher bar is
  unreachable for any corpus → `citation_threshold_above_retrieval_limit`.
- With neither variable set, behaviour is exactly the pinned, manifest-gated sample corpus.

The policy is four pinned constants in `e2e_citation_support_model.py`. Authorised material
(DAV-58) changes all four together with its manifest in a reviewed commit; a corpus file cannot
grant itself a policy. The evidence line and verdict metadata report that pinned permission as
`corpus=...`, so a non-synthetic corpus is never labelled synthetic. The verdict sidecar also
carries `validated_corpus`: the manifest SHA-256 plus each judged document's id, version, verified
source name, position and content digest, so the verdicts are bound to the exact material rather
than to whatever the files hold after the run.

`data/keyword_cases.json` annotates every case with `required_evidence`, `answerable`,
`expected_behavior` (`cite`, `no_evidence`, or `refuse`), `allowed_behavior`, and
`forbidden_behavior`; the loader rejects a case missing any of them. `refuse` marks questions the
corpus cannot answer, and each one forbids its own specific claim — locating a code line and naming
a runtime cause are different errors, so they are not collapsed into one rule.

`src/keyword_evaluation.py` records one entry per case: retrieval hit, citation traceability,
retrieval outcome, expected versus observed behavior, fabrication risk, tool calls, and elapsed
time. Any hit on a `refuse` case is recorded as a fabrication risk, and a case with no hit is not
counted as traceable because it has no citation to trace.

Retrieval facts and answer judgements are kept apart on purpose. In the retrieval slice
`citation_support` and `answer_completion` are recorded as `DEFERRED`: a traceable source id
proves where a fragment came from, not that it supports a conclusion. The development split
fills those columns through `src/answer_evaluation.py`, which judges a generated answer rather
than reading them off document availability; the human support check tracked in DAV-58 remains
separate, and an artifact carrying `human_review=not_performed` is machine evidence only.

`src/retrieval.py` provides bounded keyword retrieval and source metadata. `src/sourced_analysis.py`
separates observed submission facts from hypotheses and only cites retrieved fragments. Java services
remain the authority for identity, ownership, publication, and submission facts. The tool projections
validate scalar types and bounded string lengths before returning data to the model.

U02 and later phases must preserve U01's regression coverage for unknown tools, invalid arguments,
tool failure, timeout, cancellation, and the absence of raw source/error data in captured model
messages. Do not add write tools until the corresponding DAV-53 isolation and confirmation gates are
satisfied.
