---
title: Sandbox (D-form)
type: entity
tags: [judging, sandbox, security, core]
status: living
updated: 2026-06-21
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

> Security model: [[concepts/sandbox-security-contract]]. Caller:
> [[entities/submission]]. Pipeline: [[overview/judging-pipeline-overview]].

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
resource limits (time/memory from [[entities/problem]]). Python base is Debian
bookworm = **Python 3.11** (type hints evaluated eagerly) — a host on 3.14
(PEP 649 lazy) can pass `pytest` falsely; always verify end-to-end in the image.

## Security posture

- `seccomp-profile.json` restricts syscalls.
- **Python preamble contract**: user code is **zero-import**. The harness
  pre-injects pure-compute stdlib (`heapq`/`math`/`bisect`/`itertools`/`functools`/
  `operator`/`string`/`fractions`/`decimal`/`statistics`/`re`/`collections` +
  `ListNode`/`TreeNode`). It **never** injects `os`/`sys`/`subprocess`/`socket`/
  `shutil`/`ctypes`/`multiprocessing` — see [[concepts/sandbox-security-contract]].
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
- [[concepts/sandbox-security-contract]]
- [[overview/judging-pipeline-overview]]

## Gotchas

- New harness file not in `build.sh` list → silent missing-file → mass RE.
- Python 3.11 vs host-version annotation drift → false local green; always
  `docker run` the image to validate.
- Never relax the import blocklist — it's the core of sandbox isolation.
