---
status: passed
phase: 46-sandbox-hardening
requirements: [SAND-01, SAND-02, SAND-03, SAND-04, SAND-05]
created: 2026-04-23
---

# Phase 46: Sandbox Hardening — Verification

## Result: PASSED

All 5 requirements verified against codebase.

## Requirement Verification

| ID | Requirement | Status | Evidence |
|----|-------------|--------|----------|
| SAND-01 | `--tmpfs` before `--read-only` in both builders | PASS | Lines 167-168 (buildDockerCommand), 209-210 (buildBatchDockerCommand) |
| SAND-02 | Seccomp profile volume-mounted at `/seccomp-profile/` | PASS | Lines 171-172, 213-214 — `--volume $(pwd)/docker/sandbox:/seccomp-profile:ro` + `--security-opt seccomp=/seccomp-profile/seccomp-profile.json` |
| SAND-03 | LanguageLimit record with timeoutSeconds/memory; languages map; 7 entries in application.yml | PASS | DockerSandboxConfig.java:18 `record LanguageLimit(int, String)`; line 16 `Map<String, LanguageLimit> languages`; application.yml has java/python/c/cpp/go/rust/javascript |
| SAND-04 | `--tmpfs /tmp:rw,exec,size=64m` present in both builders | PASS | Lines 167, 209 — already correct, no change needed |
| SAND-05 | SandboxNamespaceIsolationTest.java with PID/network/user isolation tests | PASS | 6 `@Test` methods: networkIsolated × 2, userNamespaceIsolated, pidNamespaceIsolated × 2, filesystemIsolated |

## Must-Haves

- [x] `buildDockerCommand()` has `--tmpfs /tmp:rw,exec,size=64m` BEFORE `--read-only`
- [x] `buildBatchDockerCommand()` has `--tmpfs /tmp:rw,exec,size=64m` BEFORE `--read-only`
- [x] Seccomp profile volume-mounted into container via `--volume $(pwd)/docker/sandbox:/seccomp-profile:ro`
- [x] `--security-opt seccomp=` points to container path `/seccomp-profile/seccomp-profile.json`
- [x] Both builders have the volume mount
- [x] `LanguageLimit` record exists with `timeoutSeconds` and `memory` fields
- [x] `DockerSandboxConfig` includes `languages` map
- [x] application.yml contains all 7 language entries
- [x] Fallback to defaults when language not in map
- [x] `buildDockerCommand()` uses per-language memory and timeout
- [x] `buildBatchDockerCommand()` uses per-language memory and timeout
- [x] `--tmpfs /tmp:rw,exec,size=64m` present in both builders
- [x] Integration test class exists
- [x] PID namespace isolation test
- [x] Network namespace isolation test
- [x] User namespace isolation test

## Human Verification

Manual verification steps (requires Docker):
```bash
# Build sandbox image
docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox/

# Run tests (requires Docker daemon)
cd backend-spring && ./mvnw test -Dtest=SandboxNamespaceIsolationTest
```

## Gaps Found

None.
