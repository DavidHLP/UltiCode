---
title: OJ Sandbox (D-form, ADR-002)
tags: [reference, sandbox, architecture, living]
status: living
updated: 2026-06-19
owner: sandbox
generator: ecc:update-codemaps
---

# OJ Sandbox (D-form, ADR-002)

<!-- Generated: 2026-06-19 | Languages: python/c/cpp/java | Image: ulticode-sandbox:latest | Token estimate: ~850 -->

## Purpose

Isolated, reproducible execution of user-submitted code against problem test cases. Returns structured `verdict` (AC/WA/TLE/MLE/RE/CE/SE) per test case.

## Build Pipeline (3-stage)

```
docker/sandbox/harness/{python,c,cpp,java}/     ← 源 (mounted in dev)
                       │
                       ▼  ./docker/sandbox/harness/build.sh <lang>
docker/sandbox/harness-staging/                 ← staging (fixed file list copy)
                       │
                       ▼  docker build -f docker/sandbox/Dockerfile
ulticode-sandbox-dform:phase2                   ← intermediate
                       │
                       ▼  docker tag
ulticode-sandbox:latest                          ← runtime image
```

**`build.sh` uses a fixed file manifest** — adding a new harness module (e.g. `_case_runner.py`) requires updating the `build_<lang>()` `cp` list AND the `.pyc` cycle, otherwise the image is missing files and every test case RE's.

## Verdict Pipeline

```
1. SubmissionController POST /submissions
   → SubmissionService writes:
       submissions row (generation=N, lease=NULL, lease_expires_at=NULL)
       judge_outbox row (status=PENDING)
2. JudgeOutboxPoller (background, @Scheduled)
   → SELECT … FOR UPDATE SKIP LOCKED  (fencing)
   → UPDATE submissions SET generation=generation+1, lease=…, lease_expires_at=now+ttl
   → docker run --rm -e SOLUTION_DIR=/job -v <tmp>:/job ulticode-sandbox:latest \
       python3 /opt/harness/python/main.py /job/input.json
3. Sandbox harness (in-container):
       - read input.json (problem_id, test_cases, user_code, time_limit_ms, memory_limit_kb, cpus)
       - compile (if c/cpp/java)
       - for each test_case:
           * spawn subprocess with cgroup limits (time/memory/cpus)
           * capture stdout/stderr/exit_code/wall_time/peak_mem
           * diff against expected (normalized)
       - emit verdict.json
4. Harness exits; JudgeOutboxPoller parses verdict.json
   → UPDATE submissions SET status=verdict, generation_used=…
   → INSERT submission_verdicts (per-test-case)
   → STOMP /topic/user/{id} + /topic/contest/{id} push
5. notification_intents row for "verdict ready" → delivery ledger
```

## Per-Language Harness Layout (`/opt/harness/<lang>/`)

| Lang    | Key files                                        | Notes                              |
| ------- | ------------------------------------------------ | ---------------------------------- |
| python  | `main.py`, `runner.py`, `harness.py`             | Typing pre-injected; **no `import` from user code** |
| c       | `main.c`, `runner.c`, `sandbox.h`                | seccomp + rlimit                   |
| cpp     | `main.cpp`, `runner.cpp`, `sandbox.h`            | cgroup memory cap                  |
| java    | `Main.java`, `Runner.java`                       | JDK 17 inside container            |

## Python Preamble Contract (CRITICAL)

`harness.py::build_solution_preamble()` injects into user code:
- `typing.__all__` (List/Dict/Optional/...)
- pure-compute stdlib: `heapq`, `math`, `bisect`, `itertools`, `functools`, `operator`, `string`, `fractions`, `decimal`, `statistics`, `re`, `collections`
- collections shortcuts: `deque`, `Counter`, `defaultdict`, `OrderedDict`, `namedtuple`
- data-structure stubs: `ListNode`, `TreeNode`

**NEVER inject** (breaks isolation): `os`, `sys`, `subprocess`, `socket`, `shutil`, `ctypes`, `multiprocessing`. The exit guard only intercepts `_exit`/`sys.exit` — passing these modules lets user code escape the sandbox.

User code is **zero-import** — they just write `class Solution:` / `def solve():` etc. The preamble supplies everything they need.

## Python Version Trap

- Image base: **Debian bookworm → Python 3.11** (PEP-style type annotations: eager)
- Host may be 3.14 (PEP 649 lazy)
- Local `pytest` may pass while image-side 3.11 fails (or vice versa)
- **Always** verify annotation/preamble changes via `docker run` on the image:

```bash
docker run --rm -e SOLUTION_DIR=/job -v "$TMP":/job ulticode-sandbox:latest \
  python3 /opt/harness/python/main.py /job/input.json
```

## ListNode/TreeNode Edge Case

`None` returned for empty input is normalized to `[]` (LeetCode convention) by `normalize_return_value()`. Don't treat it as `'null'` when diffing.

## Sandboxing (cgroup + seccomp)

- `pids_limit` per container
- `memory.limit_in_bytes` (cgroup v1) / `memory.max` (v2)
- `cpu.cfs_quota_us` from `cpus` column (V20260616120000)
- seccomp profile allows only stdio + exit
- `--network=none`
- Read-only root FS + writable `/job` tmpfs

## Verdict Status Codec (ADR-001)

| Code | Meaning      | UI label            |
| ---- | ------------ | ------------------- |
| AC   | Accepted     | Accepted            |
| WA   | Wrong Answer | Wrong Answer        |
| TLE  | Time Limit   | Time Limit Exceeded |
| MLE  | Memory Limit | Memory Limit Exceeded |
| RE   | Runtime Err  | Runtime Error       |
| CE   | Compile Err  | Compile Error       |
| SE   | Sandbox Err  | Sandbox Error       |

## Troubleshooting Signals

| Signal                                         | Cause                                |
| ---------------------------------------------- | ------------------------------------ |
| All verdicts `SE` + detail `Cannot fork`       | host/cgroup pressure                 |
| All verdicts `SE` + detail `Unable to find image` | image missing — rebuild            |
| All verdicts `TLE` on easy problem             | user code infinite loop / wrong algo |
| All verdicts `RE` after adding a harness file  | missing from `build_<lang>()` `cp`   |

## Sandbox Image Refresh

```bash
./docker/sandbox/harness/build.sh python   # refresh staging + rebuild image
./docker/sandbox/harness/build.sh python --no-docker   # refresh staging only
# Runtime: SANDBOX_IMAGE=ulticode-sandbox:latest in .env — new submits use new image immediately
```
