---
title: Sandbox Rebuild Runbook
type: concept
tags: [sandbox, judging, ops, type/concept]
status: living
updated: 2026-07-06
sources:
  - docker/sandbox/
  - scripts/dev/init-env.sh
  - backend-spring/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/CodeExecutionHelperImpl.java
  - CLAUDE.md
aliases: [沙箱重建, sandbox image build]
---

# Sandbox Rebuild Runbook

> [!warning] Authoritative commands live in `CLAUDE.md` § Sandbox Harness.
> This page is the **knowledge layer**: the failure model, why each failure
> looks the way it does, and the rebuild flow at concept level. Per SCHEMA §9
> the wiki does not host the command runbook.

## The problem

The judge sandbox image (`ulticode-sandbox:latest`) is **built locally, never
distributed with the repo**. When it is missing, broken, or when docker can't
launch it, every `/submissions/run` and `/submissions/submit` call returns the
same opaque fingerprint:

```
verdict = "Runtime Error"
memory  = "0.0MB"
detail  = "Runtime error"   ← no stack, no exception type
```

The fingerprint is uniquely dangerous because it **looks like a user-code
problem but is always an infrastructure problem**. Two pieces of code conspire
to hide the real error:

1. `SandboxExecutorImpl.runBatch` maps **any** non-zero docker exit that isn't
   a compile error to `SubmissionStatus.RUNTIME_ERROR` (and fans it out across
   every case in the batch).
2. `CodeExecutionHelperImpl.sanitizeSandboxOutput` drops every line containing
   `docker` or `OCI runtime`, then returns the literal `"Runtime error"` when
   nothing survives the filter. The docker daemon's actual error ("Unable to
   find image", "no such file", seccomp path errors, …) is erased before it
   ever reaches the response.

`memory=0.0MB` is the load-bearing signal: the harness never ran the user's
code, so no peak was recorded. A genuine user-code Runtime Error always has
`memoryMb > 0` (the Python harness alone is ≈ 11 MB) and a real
`error.message` / `stack`.

## The decision

Two failure layers, two fixes. **Both** must be in place before judging works.

### Layer 1 — the image must exist

Build three tags locally: `base-17` (once per host, rarely changes) →
`ulticode-sandbox-dform:phase2` → retag `:latest`. `SANDBOX_IMAGE` in `.env`
points at `:latest`.

### Layer 2 — the seccomp path must resolve from the backend cwd

`SANDBOX_SECCOMP_PROFILE` is read by the backend JVM, whose cwd is
`backend-spring/` (set by `ecosystem.config.cjs`). So the value must be
`../docker/sandbox/seccomp-profile.json` — relative to `backend-spring/`, i.e.
the repo-root `docker/sandbox/`. A bare `docker/sandbox/...` resolves under
`backend-spring/docker/sandbox/` (which doesn't exist), and `docker run
--security-opt seccomp=<missing>` fails before the harness starts → the
Layer-1 fingerprint even when the image is fine.

`init-env.sh` and `.env.example` ship the `../` prefix by default; hand-edits
to `.env` are the usual way this regresses.

> [!note] `SANDBOX_ENABLED` is a **no-op placeholder**. Execution activates on
> `@ConditionalOnProperty("sandbox.executor")` (default `docker`), not on
> `code-execution.sandbox.enabled`. Setting it `false` does not disable
> judging.

## Why

- **alpine base, not Debian.** `Dockerfile.base` is `FROM alpine:3.19` (musl
  libc). Older comments in `Dockerfile` / `harness/README.md` / this entity
  page said "Debian bookworm" — the alpine switch was never reflected in every
  comment. (Those comments have since been corrected.)
- **host glibc ≠ image musl.** `c-sandbox` and `cpp-sandbox` are orchestrators
  that run **inside the image**. A host build (Red Hat/Fedora glibc) emits
  `interpreter /lib64/ld-linux-x86-64.so.2`; alpine provides
  `/lib/ld-musl-x86_64.so.1`. The binary won't execute. On top of that,
  `build.sh`'s `g++ -static` fails outright on hosts lacking
  `libstdc++-static` / `glibc-static`. ⇒ Build c/cpp **inside the base-17
  container**; java (bytecode) and python (`.py` source) are portable.
- **HTTP proxy + bridge network.** `~/.docker/config.json` proxies are injected
  into every build/run container. In bridge mode the container's `127.0.0.1`
  is the container itself, so the proxy is unreachable and `apk add` fails.
  `--network=host` fixes reachability; if the proxy then returns 502 for
  `dl-cdn.alpinelinux.org` (common), swap the apk repo to `mirrors.aliyun.com`
  (the same proxy usually serves it with 200).

## Where it lives

- `docker/sandbox/Dockerfile.base` — alpine base → `base-17`.
- `docker/sandbox/Dockerfile` — `FROM base-17`, COPYs `harness-staging/` into
  `/opt/harness/{lang}/`.
- `docker/sandbox/harness/build.sh` — host-side precompile + `docker build` +
  retag. **Its `build_c` / `build_cpp` assume a glibc base** — the alpine switch
  made that stale; drive c/cpp through the base-17 container instead.
- `scripts/dev/init-env.sh` / `.env.example` — `SANDBOX_*` defaults (including
  the `../` seccomp prefix and the `SANDBOX_ENABLED` placeholder comment).
- `backend-spring/.../sandbox/executor/SandboxExecutorImpl.java` — the
  non-zero-exit → `RUNTIME_ERROR` mapping.
- `backend-spring/.../service/impl/CodeExecutionHelperImpl.java` —
  `sanitizeSandboxOutput`, the line filter that masks docker errors.

## Rebuild flow (conceptual)

The authoritative commands live in `CLAUDE.md` § Sandbox Harness; this section
only names the stages so the failure modes above are actionable:

1. **base-17** — `docker build` from `Dockerfile.base` (alpine). Behind an HTTP
   proxy use `--network=host`; swap the apk repo to `mirrors.aliyun.com` if
   `dl-cdn.alpinelinux.org` 502s.
2. **java + python staging** — host-side `build.sh` (portable: bytecode / `.py`).
3. **c + cpp staging** — compile **inside** the base-17 container (musl), not
   on the host (glibc). `-static` is fine here; alpine's musl-static is
   installed.
4. **runtime image** — `docker build` from `Dockerfile` (FROM `base-17`, COPYs
   staging), then tag `:latest`.
5. **verify** — `docker run` a sample case; `memoryMb > 0` (and a non-generic
   `detail`) means the harness actually ran.

`./scripts/dev/up.sh` warns at startup if `ulticode-sandbox:latest` is missing,
so Layer-1 absence is caught before the first judge call.

## Trade-offs

- **Local build vs distribute image.** Chosen: local. The image is large
  (~920 MB; openjdk + gcc + g++ + python) and changes rarely — pushing it
  through the repo isn't worth it. Cost: every new contributor / CI host runs
  this runbook once.
- **Container-compile c/cpp vs fix `build.sh`.** Chosen: document the
  container recipe (here + `CLAUDE.md`), the minimal change that works on any
  host libc. The cleaner long-term fix is to rewrite `build.sh`'s `build_c` /
  `build_cpp` to drive `docker run base-17 gcc/g++` themselves, so a fresh
  alpine-base checkout works without the manual recipe. Known debt.
- **`SANDBOX_ENABLED` left as a no-op.** Removing it would touch every `.env`
  in the field for zero behavior change; documenting it as a placeholder is
  cheaper than deleting it.

## Related

[[entities/sandbox]] · [[concepts/sandbox-security-contract]] ·
[[overview/judging-pipeline-overview]]
