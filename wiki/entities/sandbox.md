---
title: Sandbox (D-form)
type: entity
tags: [judging, sandbox, security, core, type/entity]
status: living
updated: 2026-07-06
sources:
  - docker/sandbox/
  - backend-spring/src/main/java/com/ulticode/modules/submission/sandbox/
  - CLAUDE.md
aliases: [沙箱, D-form]
---

# Sandbox (D-form)

The isolated code-execution image that runs each submission's test cases. A
D-form sandbox: source → staging → image, built once and reused per submit
(`SANDBOX_IMAGE=ulticode-sandbox:latest`). Secured by seccomp + a hardened
language harness.

> Caller: [[entities/submission]]. Pipeline: [[overview/judging-pipeline-overview]].

## Three-layer build

```
docker/sandbox/harness/{c,cpp,java,python}/   ← source
            │  harness/build.sh
            ▼
docker/sandbox/harness-staging/               ← precompiled (.pyc etc.)
            │  Dockerfile COPY staging → /opt/harness/{lang}/
            ▼
image ulticode-sandbox:{phase2,latest}        ← what actually runs
```

- The image contains **staging**, not source. Changing harness source requires a
  rebuild: `./docker/sandbox/harness/build.sh <lang>` (refresh staging + rebuild +
  re-tag `:latest`).
- `build.sh` uses a **fixed file list** — adding a harness module means adding it
  to `build_<lang>()`'s cp list (+ `.pyc` loop), or the image misses it → every
  case returns RE.

## Per-language harness

C, C++, Java, Python. Each compiles then runs against the supplied cases under
resource limits (time/memory from [[entities/problem]]). Image base is
**alpine 3.19** = Python **3.11.14** / openjdk **17.0.14** / gcc+g++ 13.2.1
(**musl libc**, not glibc). Python type hints evaluate eagerly on 3.11; a host
on 3.14 (PEP 649 lazy) can pass `pytest` falsely — always verify end-to-end in
the image. Because the base is musl, the C/C++ orchestrators must be compiled
inside the base-17 container (a host-glibc build won't run); see
`CLAUDE.md` § Sandbox Harness for the rebuild runbook.

## Security posture

- `seccomp-profile.json` restricts syscalls.
- **Python preamble contract**: user code is **zero-import**. The harness
  pre-injects pure-compute stdlib (`heapq`/`math`/`bisect`/`itertools`/`functools`/
  `operator`/`string`/`fractions`/`decimal`/`statistics`/`re`/`collections` +
  `ListNode`/`TreeNode`). It **never** injects `os`/`sys`/`subprocess`/`socket`/
  `shutil`/`ctypes`/`multiprocessing` — the import blocklist is what enforces
  isolation (`AGENTS.md` § Security Invariants).
- Exit guard only blocks `_exit`/`sys.exit`; the import blocklist is what enforces
  isolation.

## Conventions

- Linked-list/tree problems return `None` → normalized to `[]` (LeetCode style)
  by `normalize_return_value()`.
- Each submit spins a fresh container; history is immutable (rebuild affects new
  submissions only).

## Source files

- `docker/sandbox/` (Dockerfile, seccomp-profile.json, harness/, build.sh).
- `backend-spring/.../modules/submission/sandbox/` (executor, profiles, limits).

## Cross-links

- [[entities/submission]] · [[entities/judge-queue]]
- [[overview/judging-pipeline-overview]]

## Gotchas

- **Missing/broken image → masked Runtime Error.** `SandboxExecutorImpl` maps
  any non-zero exit (that isn't a compile error) to `RUNTIME_ERROR`, and
  `sanitizeSandboxOutput` drops lines containing `docker`/`OCI runtime`. So an
  absent image, a missing seccomp file, or any docker-level failure surfaces as
  `verdict=Runtime Error` + `memory=0.0MB` + `detail="Runtime error"` with no
  real trace. See `CLAUDE.md` § Sandbox Harness for the diagnostic + rebuild.
- **`SANDBOX_SECCOMP_PROFILE` resolves against the backend cwd
  (`backend-spring/`), not the repo root.** `.env` must use
  `../docker/sandbox/seccomp-profile.json`; a bare `docker/sandbox/...` makes
  `docker run` fail on a missing seccomp file → the masked-RE fingerprint above.
- **`SANDBOX_ENABLED` is a no-op placeholder.** Execution activates on
  `sandbox.executor` (default `docker`), not `code-execution.sandbox.enabled`.
- **alpine = musl.** Host-glibc `c-sandbox`/`cpp-sandbox` won't run in the
  image; compile them inside the base-17 container. `build.sh`'s cpp `-static`
  step also needs host `libstdc++-static`/`glibc-static` which Red Hat lacks.
- New harness file not in `build.sh` cp list → silent missing-file → mass RE.
- Python 3.11 vs host-version annotation drift → false local green; always
  `docker run` the image to validate.
- Never relax the import blocklist — it's the core of sandbox isolation.
