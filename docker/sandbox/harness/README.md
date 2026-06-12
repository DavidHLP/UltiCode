# UltiCode Sandbox Harness

LeetCode/HackerRank-style execution harness compiled into the sandbox image
(form D — see Phase 2 of `docs/SANDBOX_V2_PLAN.md`).

## Directory layout

| Path | Phase 1 status | Phase scope |
|---|---|---|
| `java/` | ✅ complete | Production-ready: 31 unit + 8 E2E tests, full ListNode/TreeNode support, reflective Solution invocation, per-case worker thread with soft TLE, stdout capture |
| `python/` | ✅ complete | Mirrors Java contract; uses `inspect.signature` annotations for ListNode/TreeNode adaptation |
| `cpp/` | 🚧 skeleton only | Phase 1: smoke binary that prints empty envelope. Phase 3+ adds full ListNode/TreeNode/JSON-parse + reflective dispatch (likely template-based) |
| `c/` | 🚧 skeleton only | Same as cpp/. The C path will likely require explicit registration since C has no reflection |

## Envelope contract (stdout JSON)

```json
{
  "harness_version": "1.0",
  "language": "java | python | cpp | c",
  "exit_code": 0,
  "total_elapsed_ms": 245,
  "results": [
    {
      "case_id": "...",
      "label": "...",
      "status": "Accepted | Wrong Answer | Runtime Error | Time Limit Exceeded",
      "elapsed_ms": 12,
      "result": <jsonable>,
      "user_stdout": "",
      "user_stderr": "",
      "interrupted": true,
      "error": { "type": "...", "message": "...", "stack": ["..."] }
    }
  ]
}
```

- `status` enum is closed: `Accepted | Wrong Answer | Runtime Error | Time Limit Exceeded` for Phase 1.
- `interrupted` only present on TLE results.
- `error` only present on RE results; `stack` strips harness frames, keeps only user frames.
- Harness panics (parse error, missing Solution class, etc.) → stderr stack + `exit 2`, no envelope.

## Local development

### Java

```bash
cd docker/sandbox/harness/java
mvn test            # 31 unit + 8 E2E tests
mvn test-compile    # just compile, for IDE recompilation
```

### Python

```bash
cd docker/sandbox/harness/python
pytest test_harness.py -v
```

### C / C++

```bash
cd docker/sandbox/harness/c
gcc -O2 -o /tmp/c-smoke main.c && /tmp/c-smoke

cd docker/sandbox/harness/cpp
g++ -O2 -std=c++17 -o /tmp/cpp-smoke main.cpp && /tmp/cpp-smoke
```

## Image installation (Phase 2)

The sandbox Dockerfile (Phase 2) uses a multi-stage build:
1. Builder stage: jdk + gcc + g++ compiles all four harness flavors
2. Runtime stage: `debian:bookworm-slim` + runtime-only packages, with
   `/opt/harness/{java,python,cpp,c}/` populated from the builder stage

The backend `SandboxServiceImpl.executeV2` mounts the user's `Solution.*`
file + `input.json` at `/job/` (read-only) and invokes the appropriate
language harness, which produces the envelope JSON on stdout.

## Safety properties (per language)

| Property | Java | Python | C/C++ |
|---|---|---|---|
| Per-case soft timeout | ✅ `Thread.interrupt()` via worker | ⚠️ wall-clock check after return | 🚧 Phase 3+ |
| Outer hard timeout | (backend `ProcessBuilder.waitFor` + `destroyForcibly`) for all langs |
| User stdout capture | ✅ `System.setOut(buffer)` | ✅ `redirect_stdout(buffer)` | 🚧 Phase 3+ |
| Cycle-safe ListNode serialize | ✅ `LIST_NODE_TRAVERSAL_CAP=100_000` | ✅ same constant | n/a |
| Harness frames hidden from RE stack | ✅ | ✅ | n/a |
