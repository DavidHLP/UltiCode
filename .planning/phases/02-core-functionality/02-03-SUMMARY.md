---
phase: 02-core-functionality
plan: 03
subsystem: infra
tags: [docker, seccomp, sandbox, security, capabilities]

# Dependency graph
requires: []
provides:
  - docker/sandbox/seccomp-profile.json (seccomp profile blocking ptrace/mount/keyctl/unshare/setns/clone-namespaces)
  - --cap-drop ALL on Docker sandbox container
  - --security-opt seccomp=<path> on Docker sandbox container
  - DockerSandboxConfig.seccompProfilePath configuration field
  - application.yml seccomp-profile-path config with env var override
affects: [04-sessions, 05-quality]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "SCMP_ACT_ALLOW default with explicit dangerous-syscall blocks (D-15 incremental strategy)"
    - "Clone syscall masked-EQ check for namespace flags (allows thread creation)"

key-files:
  created:
    - docker/sandbox/seccomp-profile.json
  modified:
    - backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java
    - backend-spring/src/main/resources/application.yml

key-decisions:
  - "SCMP_ACT_ALLOW default with explicit blocks rather than whitelist approach (D-15: incremental, safe for all runtimes)"
  - "Clone blocked only when namespace flags present (value 2080505856), thread creation unmasked for Java/Node/Python"
  - "Fixed SCMP_ARCH_X86_32 to SCMP_ARCH_X86 (Docker/runc does not accept X86_32)"

patterns-established:
  - "Seccomp profile with _comments section documenting syscall dependencies (D-21, D-22)"
  - "Sandbox config field wired through: application.yml -> @ConfigurationProperties -> buildDockerCommand()"

requirements-completed: [SEC-04]

# Metrics
duration: 5min
completed: 2026-04-15
---

# Phase 02 Plan 03: Docker Sandbox Seccomp Hardening Summary

**Docker sandbox hardened with --cap-drop ALL and custom seccomp profile blocking ptrace/mount/keyctl/unshare/setns/clone-namespaces for all 5 supported languages**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-15T11:08:33Z
- **Completed:** 2026-04-15T11:14:14Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Created seccomp-profile.json with SCMP_ACT_ALLOW default, blocking 6 dangerous syscall groups (ptrace, mount, keyctl, unshare, setns, clone-with-namespaces)
- Added --cap-drop ALL to Docker sandbox run command, removing all Linux capabilities
- Added --security-opt seccomp=<path> flag pointing to configurable profile path
- Verified all 5 languages (JavaScript, Python, Java, C, C++) execute correctly with hardened sandbox
- Verified ptrace syscall is blocked with EPERM (errno=1)
- Verified stderr capture works for runtime errors (D-22)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create seccomp profile, update DockerSandboxConfig, and add sandbox flags to buildDockerCommand** - `214bbcefa` (feat)
2. **Task 2 (fix): Fix seccomp profile arch name and add exec to tmpfs mount** - `04bb97d8f` (fix)

## Files Created/Modified
- `docker/sandbox/seccomp-profile.json` - Custom seccomp profile blocking dangerous syscalls (ptrace, mount, keyctl, unshare, setns) and clone with namespace flags; _comments section documents D-21 (wait4/times/getrusage) and D-22 (openat/write/unlink) dependencies
- `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java` - Added `seccompProfilePath` field to the config record
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` - Added `--cap-drop ALL`, `--security-opt seccomp=<path>`, and `exec` to tmpfs mount
- `backend-spring/src/main/resources/application.yml` - Added `seccomp-profile-path` config with `${SANDBOX_SECCOMP_PROFILE:docker/sandbox/seccomp-profile.json}` env var override

## Decisions Made
- **SCMP_ACT_ALLOW default with explicit blocks** (D-15 incremental strategy): Since `--security-opt seccomp=<path>` REPLACES Docker's default profile entirely, using SCMP_ACT_ERRNO as default would break all language runtimes. SCMP_ACT_ALLOW with targeted blocks is the safe approach.
- **Clone syscall masked-EQ for namespace flags only**: Unconditionally blocking clone would break Java threads, Node.js libuv worker threads, and Python threading. The mask value 2080505856 covers CLONE_NEWUSER|CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWNET|CLONE_NEWIPC|CLONE_NEWUTS.
- **SCMP_ARCH_X86 instead of SCMP_ARCH_X86_32**: Docker/runc does not recognize SCMP_ARCH_X86_32. The correct architecture identifier is SCMP_ARCH_X86.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed invalid seccomp architecture name SCMP_ARCH_X86_32**
- **Found during:** Task 2 (Docker verification tests)
- **Issue:** Plan specified `SCMP_ARCH_X86_32` in the architectures array, but Docker/runc does not recognize this identifier. All 5 language tests failed with "string SCMP_ARCH_X86_32 is not a valid arch for seccomp".
- **Fix:** Changed to `SCMP_ARCH_X86` (the correct seccomp architecture identifier for 32-bit x86 in Docker).
- **Files modified:** `docker/sandbox/seccomp-profile.json`
- **Verification:** All 5 Docker tests pass after fix.
- **Committed in:** `04bb97d8f`

**2. [Rule 1 - Bug] Added exec option to tmpfs mount for C/C++ binary execution**
- **Found during:** Task 2 (Docker verification tests)
- **Issue:** The existing `--tmpfs /tmp:rw,size=64m` lacked the `exec` option, causing "Permission denied" when executing compiled C/C++ binaries from /tmp. This was a pre-existing bug (not caused by this plan's changes) but discovered during verification.
- **Fix:** Changed to `--tmpfs /tmp:rw,exec,size=64m`.
- **Files modified:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java`
- **Verification:** C and C++ Docker tests produce correct output after fix.
- **Committed in:** `04bb97d8f`

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both auto-fixes were necessary for correctness. The seccomp arch fix prevented the profile from loading at all. The tmpfs exec fix enabled C/C++ compilation to work. No scope creep.

## Issues Encountered
- Pre-existing compilation errors in SubmissionServiceImpl.java (missing DTOs: WeeklyProgressDTO, LanguageStatsDTO, MonthlySubmissionStatsDTO) prevented `./mvnw compile` from passing cleanly. These errors are unrelated to this plan's changes and exist in uncommitted files on the main branch. Our 3 modified files compile without errors.

## User Setup Required

None - no external service configuration required beyond Docker daemon running.

## Next Phase Readiness
- Docker sandbox hardening complete (SEC-04 fulfilled)
- Seccomp profile is self-contained and requires no additional runtime dependencies
- The `time` package is NOT in the current Dockerfile but is documented in the seccomp profile _comments for when /usr/bin/time integration is added (D-21 forward-looking)

## Threat Model Compliance

| Threat ID | Mitigation | Status |
|-----------|-----------|--------|
| T-02-11 (ptrace) | Seccomp SCMP_ACT_ERRNO | Verified: errno=1 (EPERM) |
| T-02-12 (mount) | Seccomp + --cap-drop ALL | Verified: syscall blocked |
| T-02-13 (keyctl) | Seccomp SCMP_ACT_ERRNO | Configured: syscall blocked |
| T-02-14 (unshare/setns) | Seccomp + --cap-drop ALL | Configured: syscall blocked |
| T-02-15 (clone namespaces) | Seccomp masked-EQ | Configured: clone with flags blocked |
| T-02-16 (capabilities) | --cap-drop ALL | Configured: all caps dropped |
| T-02-17 (profile too aggressive) | SCMP_ACT_ALLOW default | Verified: all 5 languages work |
| T-02-19 (/usr/bin/time) | SCMP_ACT_ALLOW default | Documented: D-21 in _comments |
| T-02-20 (stderr capture) | SCMP_ACT_ALLOW default | Verified: D-22 stderr works |

---
*Phase: 02-core-functionality*
*Completed: 2026-04-15*

## Self-Check: PASSED

- FOUND: docker/sandbox/seccomp-profile.json
- FOUND: DockerSandboxConfig.java
- FOUND: CodeExecutionService.java
- FOUND: application.yml
- FOUND: 02-03-SUMMARY.md
- FOUND: 214bbcefa (feat commit)
- FOUND: 04bb97d8f (fix commit)
- FOUND: 002d2291b (docs commit)
