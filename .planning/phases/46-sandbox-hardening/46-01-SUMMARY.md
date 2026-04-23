---
phase: 46
plan: 01
subsystem: sandbox
tags: [sandbox, security, docker, namespace-isolation]
requires: [SAND-01, SAND-02, SAND-03, SAND-04, SAND-05]
provides: [sandbox-hardening]
affects: [code-execution]
tech-stack-added: [Java record, Docker security flags]
key-files-created:
  - backend-spring/src/test/java/com/ulticode/modules/submission/service/impl/SandboxNamespaceIsolationTest.java
key-files-modified:
  - backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SandboxServiceImpl.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java
  - backend-spring/src/main/resources/application.yml
key-decisions:
  - "LanguageLimit record added to DockerSandboxConfig as inner record with timeoutSeconds and memory fields"
  - "seccomp profile volume-mounted at /seccomp-profile/seccomp-profile.json inside container"
  - "effectiveMemory/effectiveTimeout lookup pattern used for per-language overrides"
  - "Flag ordering: --tmpfs before --read-only ensures tmpfs mount succeeds"
requirements-completed: [SAND-01, SAND-02, SAND-03, SAND-04, SAND-05]
duration: ~30 min
completed: 2026-04-22T14:29:30Z
---

# Phase 46 Plan 01: Sandbox Hardening Summary

**One-liner:** Docker sandbox hardened with correct flag ordering, seccomp volume mount, per-language resource limits, and namespace isolation tests.

## What Was Built

Sandbox execution security hardened across 5 dimensions:

1. **SAND-01 — Flag ordering fixed**: `--tmpfs /tmp:rw,exec,size=64m` now placed BEFORE `--read-only` in both `buildDockerCommand()` and `buildBatchDockerCommand()`. Previously, `--read-only` made the filesystem read-only before tmpfs could be mounted.

2. **SAND-02 — Seccomp profile volume mount added**: Added `--volume $(pwd)/docker/sandbox:/seccomp-profile:ro` so the seccomp profile at `docker/sandbox/seccomp-profile.json` is accessible inside the container at `/seccomp-profile/seccomp-profile.json`. The `--security-opt` path updated to use the container path directly.

3. **SAND-03 — Per-language resource limits**: Added `LanguageLimit(int timeoutSeconds, String memory)` record to `DockerSandboxConfig`. Language-specific limits applied in both `buildDockerCommand()` and `buildBatchDockerCommand()` via lookup pattern with fallback to defaults. All 7 languages configured: java (10s/256m), python (5s/128m), c (5s/128m), cpp (5s/128m), go (8s/256m), rust (8s/256m), javascript (3s/64m).

4. **SAND-04 — tmpfs size verified**: `size=64m` already correctly present in `--tmpfs /tmp:rw,exec,size=64m`. No changes needed.

5. **SAND-05 — Namespace isolation tests**: Created `SandboxNamespaceIsolationTest` with 6 integration tests covering network isolation (external IP + host.docker.internal blocked), user isolation (uid=1000 not root), PID namespace isolation (limited process visibility), and filesystem isolation (read-only except /tmp).

## Tasks Completed

| Task | Description | Status |
|------|-------------|--------|
| SAND-01 | Fix --read-only flag ordering | ✓ |
| SAND-02 | Volume-mount seccomp profile | ✓ |
| SAND-03a | Add LanguageLimit record + config | ✓ |
| SAND-03b | Apply per-language limits in builders | ✓ |
| SAND-04 | Verify tmpfs size=64m | ✓ (no code change) |
| SAND-05 | Namespace isolation integration tests | ✓ |

## Files Changed

- `DockerSandboxConfig.java` — Added `LanguageLimit` record and `languages` map field
- `SandboxServiceImpl.java` — Fixed flag order, added seccomp volume mount, wired per-language limits
- `application.yml` — Added 7-language resource limit configuration
- `SandboxNamespaceIsolationTest.java` — 6 new integration tests

## Verification

```bash
# Build sandbox image
docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox/

# Run namespace isolation tests
cd backend-spring && ./mvnw test -Dtest=SandboxNamespaceIsolationTest
```

## Deviations from Plan

None — plan executed exactly as written.

## Next

Phase 46 complete. Ready for Phase 47 verification or next milestone work.
