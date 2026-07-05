# UltiCode Sandbox Harness

LeetCode/HackerRank-style execution harness compiled into the sandbox image
(form D — see Phase 2 of `docs/SANDBOX_V2_PLAN.md`).

## Directory layout

| Path | Phase 1 status | Phase scope |
|---|---|---|
| `java/` | ✅ complete | Production-ready: 31 unit + 8 E2E tests, full ListNode/TreeNode support, reflective Solution invocation, per-case worker thread with soft TLE, stdout capture |
| `python/` | ✅ complete | Mirrors Java contract; uses `inspect.signature` annotations for ListNode/TreeNode adaptation |
| `cpp/` | ✅ complete | `cpp-sandbox` orchestrator: statically extracts the Solution signature, generates a typed runner, g++-compiles it in-container, emits the D-form envelope. ListNode/TreeNode + JSON parse/serialize |
| `c/` | ✅ complete | `c-sandbox` orchestrator: reads `/job/input.json`, runs the user solution, emits the envelope |

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

## Image installation

The sandbox image is **not distributed with the repo** — it is built locally.
Contract: source → `harness-staging/` (host-precompiled) → image. See
`CLAUDE.md` § Sandbox Harness for the operating contract and
`wiki/concepts/sandbox-rebuild.md` for the full diagnostic + rebuild runbook.

The runtime base is `ulticode-sandbox:base-17` (`alpine:3.19` + openjdk17 +
python3 + gcc + g++ + musl libc), **not** Debian. The Dockerfile COPYs the
precompiled `harness-staging/{java,python,c,cpp}/` into `/opt/harness/{lang}/`.

The backend `SandboxExecutorImpl` (the hexagonal port; replaces the pre-M2a
`SandboxServiceImpl`) spawns one `docker run --rm` per submission, mounts the
user's `solution.*` + `input.json` at `/job/` (read-only), and parses the
envelope JSON from stdout.

### Build notes (host vs image)

- **alpine = musl, not glibc.** `c-sandbox` / `cpp-sandbox` run **inside the
  image**, so they must be linked against musl. A host build (Red Hat/Fedora
  glibc) produces binaries the image cannot execute (`interpreter
  /lib64/ld-linux-x86-64.so.2` vs `/lib/ld-musl-x86_64.so.1`), and `g++
  -static` additionally fails if the host lacks `libstdc++-static` /
  `glibc-static`. **Build c/cpp inside the base-17 container** (recipe in
  `CLAUDE.md` § Sandbox Harness); java (class bytecode) and python (`.py`
  source) are portable, host-build is fine.
- **Proxy environments.** `~/.docker/config.json` proxies are injected into
  every build/run container. In bridge mode the container's `127.0.0.1` is
  itself, so the proxy is unreachable and `apk add` fails. Use
  `docker build --network=host`; if the proxy returns 502 for
  `dl-cdn.alpinelinux.org`, swap the apk repo to `mirrors.aliyun.com`.
- **`build.sh` uses a fixed cp list.** A new harness module must be added to
  `build_<lang>()`'s cp list (+ `.pyc` loop) or the image silently misses it
  and every case resolves to Runtime Error.

## Safety properties (per language)

| Property | Java | Python | C/C++ |
|---|---|---|---|
| Per-case soft timeout | ✅ `Thread.interrupt()` via worker | ⚠️ wall-clock check after return | 🚧 Phase 3+ |
| Outer hard timeout | (backend `ProcessBuilder.waitFor` + `destroyForcibly`) for all langs |
| User stdout capture | ✅ `System.setOut(buffer)` | ✅ `redirect_stdout(buffer)` | 🚧 Phase 3+ |
| Cycle-safe ListNode serialize | ✅ `LIST_NODE_TRAVERSAL_CAP=100_000` | ✅ same constant | n/a |
| Harness frames hidden from RE stack | ✅ | ✅ | n/a |
