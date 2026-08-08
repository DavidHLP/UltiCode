# D-Form Harness Envelope Contract

**Version**: 1.0 (pinned from current reality, 2026-07-11)
**Scope**: the wire contract between `CodeExecutionHelperImpl.buildDBatchInputsJson(...)`
(backend, `com.ulticode.modules.submission.service.impl`) and the per-language
pre-compiled harnesses that live in `/opt/harness/{lang}/` inside the sandbox
image (`docker/sandbox/harness-staging/` → `Dockerfile` COPY → `ulticode-sandbox:latest`).

This document **pins** the existing contract. It is additive: it does not
propose any change to runtime behaviour, fields, or status vocabulary. Any
deviation from what is written here is a bug in the harness or the backend.

## Why this exists

The D-form envelope was previously documented in three places: a Python
docstring (`docker/sandbox/harness/python/_case_runner.py`), scattered Javadocs
on `Main.java` / `PerCaseResultDTO.java`, and a single set string in the
backend (`DFORM_TYPES` in `CodeExecutionHelperImpl`). That left room for:

1. **Drift between languages** — a field added to the Java harness that the
   Python harness never emits (or vice versa).
2. **Backend-side assumptions** that no human-verified test pins against a
   real envelope.
3. **Status vocabulary drift** — the harness writes `"Time Limit Exceeded"`
   while the backend enum's wire value is `"Memory Limit Exceeded"` — easy to
   mistype a string when there is no canonical reference.

This contract lifts the implicit agreement into one place and pins it with:

* `input.schema.json` — what the backend writes to `input.json`.
* `envelope.schema.json` — what the harness writes to stdout.
* `golden/input.json` + `golden/envelope.json` — one representative request
  + the matching envelope. Used by the JVM conformance test
  (`DFormEnvelopeContractTest`) and by the manual conformance runner.
* `DFormEnvelopeContractTest` — JVM-side parity test, no Docker required.
* `run-conformance.sh` — Docker-side parity script (manual / build-time only).

## Languages currently implementing this contract

| Language | Path | Status |
|---|---|---|
| **java** | `docker/sandbox/harness/java/src/main/java/{Main,Harness}.java` | **Live**. Emits the full envelope (every field in §3). |
| **python** | `docker/sandbox/harness/python/{main.py,_case_runner.py,harness.py}` | **Live**. Emits the same envelope. Per-case subprocess model. |
| **cpp** | `docker/sandbox/harness/cpp/main.cpp` | **Live**. Emits the same envelope. Per-case `fork()` model. |
| **c** | `docker/sandbox/harness/c/main.c` | **Phase-1 stub**. Reads argv / stdin but does **not** consume `input.json`; not wired to the D-form dispatch path. Listed for completeness — not a constraint target. |

> **C note**: the C harness is a phase-1 stub (argv / stdio based); the
> contract below is the **target** shape. Until C is wired, ignore it for
> conformance purposes.

---

## 1. Transport

* **Request**: backend writes `input.json` to `/job/input.json` (UTF-8, LF
  newlines, JSON object).
* **Response**: harness writes the envelope JSON to **stdout** (single object,
  `allow_nan=False`); harness-panic stack traces (if any) go to **stderr**.
* **Exit code**:
  * `0` — envelope is well-formed; per-case verdicts are trustworthy.
  * `2` — harness itself panicked (parse failure, ambiguous `Solution`,
    javac failure, missing `Solution` class). The backend treats the entire
    batch as Runtime Error.
* **Process model**: the backend launches the harness inside a Docker
  container with `--network=none` and a cgroup memory cap. The harness is
  responsible for **all** per-case resource measurement and timeout
  enforcement (per-case wall-clock + per-case heap peak).

## 2. Request — `input.json`

JSON Schema: [`input.schema.json`](./input.schema.json).

| Field | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `per_case_timeout_ms` | integer (≥1) | yes | `1000` (harness default; backend always sets) | Wall-clock budget per case in milliseconds. The harness enforces it via subprocess / future / `fork+waitpid` timeout; a runaway case is SIGKILLed and the case is reported as `"Time Limit Exceeded"`. |
| `memory_limit_bytes` | integer (≥0) | no | `0` (disabled) | Per-run heap ceiling forwarded to each case. Absent / `0` disables the harness-level MLE check (the Docker cgroup is the Layer-B backstop). ADR-002 §8. |
| `method_name` | string | no | harness auto-detects | When `Solution` exposes multiple public methods, the harness needs this to disambiguate. The backend only sets it for problems with non-unique method signatures. |
| `cases` | array of case objects | yes | — | The test cases to run. Order is preserved end-to-end (`cases[i]` → `results[i]`). |

Each `cases[i]`:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `case_id` | string | yes | Stable id from `submissions_test_cases.id`. Echoed back in `results[i].case_id`. |
| `label` | string | no (defaults to `case_id`) | Human-readable label. |
| `expected_output` | string (JSON-encoded) | yes | The canonical JSON form of the expected return value. The harness parses it and compares against the user's JSON-normalized result. |
| `inputs` | array of input spec | no | Per-argument input specs; positional, matching the `Solution` method signature in order. |

Each `inputs[i]` (one per method argument):

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | no | Argument name (debug only). |
| `value` | string (JSON-encoded) or raw JSON | yes | The argument value. Backend always sends it as a JSON-encoded literal (string). The harness parses it via `parse_input_value` / `extractInputValue`. |
| `type` | string | no | OJ type hint from `DFORM_TYPES` (see §4). When set, takes precedence over the method's signature annotation. |

### 2.1 `expected_output` and `value` encoding

The backend persists inputs / expected outputs as **JSON-encoded string
literals** (so `"[1,2,3]"` is stored verbatim in MySQL). The harness
**parses** these strings with `json.loads` / `parseJson` before use. This is
load-bearing: do not change `buildDInputSpecs` to ship raw JSON or the
backend→harness contract breaks.

## 3. Response — per-case envelope

JSON Schema: [`envelope.schema.json`](./envelope.schema.json).

Top-level envelope:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `harness_version` | string | yes | Currently `"1.0"`. Bumped on contract breaks. |
| `language` | string | yes | One of `"java"`, `"python"`, `"cpp"`. (C stub does not emit.) |
| `exit_code` | integer | yes | `0` if envelope is trustworthy; non-zero for harness panic. |
| `total_elapsed_ms` | integer (≥0) | yes | Wall-clock duration of the whole batch, measured by the harness parent process. |
| `results` | array of per-case verdicts | yes | Per-case verdicts. Order matches `input.cases`. Length may be **less** than `input.cases.length` only on harness panic — backend maps missing entries to Runtime Error. |

Each `results[i]` (per-case verdict):

| Field | Type | Required | Nullability | Meaning |
|---|---|---|---|---|
| `case_id` | string | yes | never null | Echo of `input.cases[i].case_id`. |
| `label` | string | yes | never null | Echo of `input.cases[i].label` (or `case_id` if absent). |
| `elapsed_ms` | integer (≥0) | yes | never null | Wall-clock duration of this case, ms-truncated. Kept for backwards compat (the API was first built on ms). |
| `elapsed_us` | integer (≥0) | **yes** | never null | Precise wall-clock in microseconds. ADR-002 §8. Backend prefers this for display; falls back to `elapsed_ms` when missing. |
| `cpu_ms` | integer (≥0) | **yes** | never null | CPU time (user + sys) the user code consumed, in ms. Excludes harness reflection / IPC overhead. ADR-002 §8. |
| `peak_memory_bytes` | integer (≥0) | **yes** | never null | Per-case peak memory in bytes. Semantics differ by language (Java: sum of per-pool heap peak after `resetPeakUsage`; Python: `ru_maxrss` of the per-case subprocess ×1024 on Linux; C++: `getrusage(RUSAGE_CHILDREN).ru_maxrss` on Linux). All measure a true high-water mark, not a single-point sample. |
| `status` | string | yes | never null | One of the wire values in §4. |
| `result` | any JSON value | yes | may be `null` | The user's JSON-normalized return value, on success. `null` on `Wrong Answer`, `Runtime Error`, `Time Limit Exceeded`, `Memory Limit Exceeded`, `Compile Error`. |
| `interrupted` | boolean | conditional | may be absent | `true` when the case was SIGKILLed (TLE / case-runner subprocess timeout). Absent on most other verdicts. The backend's parser treats absent as `null`. |
| `error` | object | conditional | may be absent | Present on `Runtime Error`. Schema: `{type, message, stack[]}`. `stack` filters out harness frames (`Main`, `Harness`, `java.lang.reflect.*`, `main.py`, `harness.py`, `_case_runner.py`). |
| `user_stdout` | string | yes | never null | Captured `System.out` (Java) / `print()` (Python) / `std::cout` (C++) during the case. Capped at 64 KiB; truncated with `"\n... [truncated, original=N bytes]"`. |
| `user_stderr` | string | yes | never null | Always emitted by every language (even when empty) for shape stability. |

### 3.1 Why every language emits the same field set

The backend `PerCaseResultDTO.fromMap` reads each field by name and tolerates
absent ones (defaulting to `0` / `null`). That tolerance is **not** a
license to omit fields: every live harness (`java` / `python` / `cpp`)
emits every field above on every verdict path. The conformance test pins
that.

### 3.2 `interrupted` semantics

| Status | `interrupted` |
|---|---|
| `Accepted`, `Wrong Answer`, `Memory Limit Exceeded`, `Compile Error` | absent |
| `Runtime Error` | absent (the case ran to completion and raised; not killed) |
| `Time Limit Exceeded` (parent-killed) | `true` |
| `Time Limit Exceeded` (soft: method returned but over budget) | `true` (python emits it; java may emit `false`) |

The backend does not act on `interrupted` (it is debug telemetry). Do not
remove it.

### 3.3 `peak_memory_bytes` semantics by language

| Language | What it measures |
|---|---|
| Java | `sum(MemoryPoolMXBean.peakUsage.used)` over HEAP pools after `resetPeakUsage()` is called at case start. Reflects only this case's peak. |
| Python | `resource.getrusage(RUSAGE_SELF).ru_maxrss × 1024` from the per-case subprocess (KB → bytes on Linux; raw bytes on macOS — but the production image is Linux). |
| C++ | `getrusage(RUSAGE_CHILDREN).ru_maxrss × 1024` of the per-case forked child (Linux). |

All three report **bytes**, never KB / MB. The backend divides by
`(1024 * 1024)` to get MiB; it floors at `1` to avoid `0.0MB` UI noise.

## 4. Status wire vocabulary

These are the only valid values for `results[i].status`. All values match
`SubmissionStatus.displayName` (i.e. `SubmissionStatus.fromWire(s)` accepts
them). Reverse mapping is owned by
`com.ulticode.modules.submission.codec.SubmissionStatusCodec`.

| Wire value | Source enum | Notes |
|---|---|---|
| `Accepted` | `SubmissionStatus.ACCEPTED` | Per-case pass. |
| `Wrong Answer` | `SubmissionStatus.WRONG_ANSWER` | Result JSON does not equal normalized expected JSON. |
| `Time Limit Exceeded` | `SubmissionStatus.TIME_LIMIT_EXCEEDED` | Wall-clock over `per_case_timeout_ms`. |
| `Memory Limit Exceeded` | `SubmissionStatus.MEMORY_LIMIT_EXCEEDED` | `peak_memory_bytes > memory_limit_bytes`. |
| `Runtime Error` | `SubmissionStatus.RUNTIME_ERROR` | Uncaught exception / signal / non-zero exit. |
| `Compile Error` | `SubmissionStatus.COMPILE_ERROR` | C++ / Java compilation failure. C++ harness emits one Compile Error verdict per case in the batch when compile fails. |
| `Output Limit Exceeded` | `SubmissionStatus.OUTPUT_LIMIT_EXCEEDED` | Reserved for future use — no current harness path emits this; kept in the enum. |

> **Not in the envelope vocabulary but exist on the wire elsewhere**:
> `Pending`, `Judging`, `Presentation Error`, `Sandbox Error`, `System Error`
> are produced by the **backend** (lifecycle / reducer / infra classification),
> never by a harness. The conformance test asserts no harness path emits any
> of these.

### 4.1 `DFORM_TYPES` — argument type vocabulary

`CodeExecutionHelperImpl.DFORM_TYPES` is the canonical set of `inputs[i].type`
values the backend will write. The harness must handle at least:

```
int, long, double, boolean,
String,
int[], int[][], long[], String[],
ListNode, ListNode[], TreeNode, TreeNode[]
```

Anything outside this set is dropped by the backend (`buildDInputSpecs`)
and the harness falls back to whatever the method's signature annotation
says (Java) or pass-through (Python / C++).

## 5. ListNode / TreeNode normalization

LeetCode convention: a method whose return type is `ListNode` / `TreeNode`
(or a collection thereof) returning **`None` for an empty input** is
correct and must compare equal to an expected `[]`, **not** `null`.

* **Python**: `harness.normalize_return_value(result, method)` — if the
  return is `None` AND the return annotation is / contains `ListNode` or
  `TreeNode`, remap to `[]`. Non-OJ return types (`Optional[int] -> None`)
  stay `null`.
* **Java**: `Harness.normalizeReturnValue(methodResult, method)` — same
  rule; remaps `null` → `[]` for `ListNode` / `TreeNode` typed returns.
* **C++**: n/a — C++ harnesses accept only `int` / `vector<int>` / string
  today; no OJ data-structure adapter yet.

This is the only "smart" comparison rule. **All other comparisons are
JSON-string equality** after `normalizeJson` (`json.dumps(...,
separators=(",", ":"), allow_nan=False, sort_keys=False)` on both sides).
A wrong-answer case is purely "JSON strings differ".

## 6. Stacktrace scrubbing

The `error.stack[]` array hides harness frames so debug output stays short:

| Language | Hidden frames |
|---|---|
| Python | frames whose filename ends in `/main.py`, `/harness.py`, or `/_case_runner.py` |
| Java | `Main` / `Harness` / `Main$*` / `Harness$*` / `java.*` / `jdk.*` / `sun.*` |
| C++ | harness does not emit stack traces (signal-only); `error.stack` is always `[]` |

The user keeps seeing their own frames (`Solution` / `class::*`).

## 7. Process-control guard

Every live harness blocks the user code's process-control primitives before
any user code runs:

* **Python**: replaces `os._exit` / `sys.exit` / `builtins.exit` /
  `builtins.quit` with a guard that raises `RuntimeError`. The subprocess
  then surfaces as `Runtime Error` for that case, not a harness panic.
* **Java**: installs a `SecurityManager` (`NoExitSecurityManager`) that
  blocks `Runtime.halt` and `System.exit`. JDK 17's SM is deprecated but
  still functional; Phase 2+ will switch to per-case child-process isolation.
* **C++**: per-case `fork()` — a SEGV / ABRT / stack-overflow in the child
  kills only the child; the parent reports `Runtime Error` with
  `error.type = "infrastructure"` for fork failure and the signal-driven
  verdict for a user crash.

---

## 8. Conformance

### 8.1 JVM conformance test (no Docker)

`services/app/app-web/src/test/java/com/ulticode/modules/submission/contract/DFormEnvelopeContractTest.java`

* Loads `golden/envelope.json` from the contract dir (path relative to the
  harness contract, mirrored into `src/test/resources` for the build).
* Asserts `CodeExecutionHelperImpl.parseDEnvelope(...)` decodes it into
  expected `RunResultDTO.RunCaseResult` values.
* Asserts `buildDBatchInputsJson(...)` / `buildDInputsJson(...)` produce
  JSON that contains every field the schema requires (top-level +
  per-case).
* Asserts every `status` value in §4 decodes via
  `SubmissionStatusCodec.fromWire(...)`.

Runs in the regular unit-test lane (`./mvnw test`).

### 8.2 Sandbox conformance runner (manual / build-time)

`docker/sandbox/harness/contract/run-conformance.sh`

Run inside the built sandbox image to play `golden/input.json` against
every language harness and diff the produced envelope against
`golden/envelope.json` (structural parity; status / case_id / fields must
match exactly).

* **NOT** part of the JVM test suite.
* **NOT** wired into CI.
* Requires the built `ulticode-sandbox:latest` image.

Header comment in the script carries the same warning.

---

## 9. Versioning

* Bump `harness_version` on the envelope when a wire change happens.
* Bump this contract's version on the same change.
* **Breaking** changes (field removal, status vocabulary change, wire
  format change) require a migration plan:
  1. Old harness versions must still emit envelopes the new backend parses
     (within reason — defensive parsing is the rule, but lost information
     is gone).
  2. New harness versions must still emit envelopes the old backend
     handles gracefully (defaulting / tolerating missing fields).
* **Non-breaking** changes (new optional fields, new status values behind
  a feature flag) can ship without backend changes if `PerCaseResultDTO`
  defaults are sensible.

See ADR-002 §8 (resource measurement) and ADR-001 (status wire contract)
for the historical decisions this contract pins.