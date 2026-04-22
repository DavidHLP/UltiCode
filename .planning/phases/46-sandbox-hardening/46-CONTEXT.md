# Phase 46: Sandbox Hardening - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Code execution sandbox is hardened with correct bubblewrap/docker invocation, per-language resource limits, and namespace isolation verification.

**This phase delivers:**
- Fixed flag ordering in Docker sandbox command
- Correct seccomp profile volume mounting
- Per-language distinct timeout and memory limits
- /tmp tmpfs size enforcement
- Integration test validating namespace isolation

</domain>

<decisions>
## Implementation Decisions

### Flag Ordering Bug (SAND-01)
- **D-01:** `--read-only` flag must come AFTER `--tmpfs` flag — current code has `--read-only` before `--tmpfs` at lines 151-152 in SandboxServiceImpl.java, which causes the tmpfs mount to fail

### Seccomp Profile Mounting (SAND-02)
- **D-02:** Seccomp profile must be volume-mounted into container — add `$(pwd)/docker/sandbox:/seccomp-profile:ro` volume mount so the container can access the seccomp profile at the path specified in `--security-opt seccomp=...`

### Per-Language Resource Limits (SAND-03)
- **D-03:** Add per-language limits via new `LanguageLimit` record in DockerSandboxConfig — distinct timeout (seconds) and memory limits per language
- **D-04:** Recommended limits: Java (10s, 256m), Python (5s, 128m), C/C++ (5s, 128m), Go (8s, 256m), Rust (8s, 256m), JavaScript (3s, 64m)

### Tmpfs Size Enforcement (SAND-04)
- **D-04:** `/tmp` mounted as tmpfs with `size=64m` — already correct in code (line 152), no change needed

### Namespace Isolation Test (SAND-05)
- **D-05:** Integration test validates that user/pid/network namespaces are separate from host — test creates a submission, executes it, and verifies: (1) process is not visible in host pid namespace, (2) network is truly isolated (cannot ping host or external IPs)

### Configuration Approach
- **D-06:** Language limits stored in `application.yml` under `code-execution.sandbox.languages` as a map, loaded into DockerSandboxConfig record

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §SAND-01, SAND-02, SAND-03, SAND-04, SAND-05 — Phase 46 acceptance criteria
- `.planning/ROADMAP.md` §Phase 46 — Phase goal and success criteria

### Backend Code
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SandboxServiceImpl.java` — Current sandbox Docker command builder (lines 142-195)
- `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java` — Current config record (lines 6-15)
- `docker/sandbox/Dockerfile` — Sandbox container image definition
- `docker/sandbox/seccomp-profile.json` — Seccomp profile for sandbox isolation

### Existing Test Patterns
- `backend-spring/src/test/java/com/ulticode/modules/submission/` — Existing submission tests

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- **DockerSandboxConfig record**: Already structured with individual fields — can be extended with language-specific limits map
- **SandboxServiceImpl**: Already builds Docker commands as `List<String>` — easy to inject per-language overrides

### Established Patterns
- **Docker command construction**: Uses `ArrayList<>(List.of(...))` pattern — fluent and readable
- **Process execution**: Standard Java ProcessBuilder pattern with `waitFor()` timeout

### Bugs Found (from code analysis)
- **Flag ordering bug**: Line 151 `--read-only` comes before line 152 `--tmpfs` — wrong order causes tmpfs mount failure
- **Missing volume mount**: Line 155 references `sandboxConfig.seccompProfilePath()` but the host path is never volume-mounted into the container

</codebase_context>

<specifics>
## Specific Ideas

No specific references from prior discussion — Phase 46 is the first to address sandbox hardening.

</specifics>

<deferred>
## Deferred Ideas

None — Phase 46 scope is well-defined by SAND-01 through SAND-05.

</deferred>

---

*Phase: 46-sandbox-hardening*
*Context gathered: 2026-04-22*
