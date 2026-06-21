---
title: Sandbox Security Contract
type: concept
tags: [sandbox, security, type/concept]
status: living
updated: 2026-06-21
sources:
  - docker/sandbox/seccomp-profile.json
  - docker/sandbox/harness/
  - CLAUDE.md
aliases: [沙箱安全契约]
---

# Sandbox Security Contract

## The problem
Submitted code is adversarial. A naive `exec` lets a user read the filesystem,
open sockets, spawn processes, or break out of the container. The sandbox must
make escape impractical without breaking legitimate competitive-programming I/O.

## The decision
Two layers:

1. **seccomp** (`seccomp-profile.json`) — restricts the syscall surface at the
   kernel level. The container can't reach disallowed syscalls even if user code
   asks.
2. **Python preamble zero-import** — user code runs with **zero imports of its
   own**. `build_solution_preamble()` pre-injects *pure-compute* stdlib
   (`heapq`/`math`/`bisect`/`itertools`/`functools`/`operator`/`string`/`fractions`/
   `decimal`/`statistics`/`re`/`collections` + `deque`/`Counter`/`defaultdict`/
   `OrderedDict`/`namedtuple` + `ListNode`/`TreeNode`) and **never** injects
   `os`/`sys`/`subprocess`/`socket`/`shutil`/`ctypes`/`multiprocessing`. The exit
   guard only blocks `_exit`/`sys.exit` — the import blocklist is what actually
   enforces isolation.

## Where it lives
- `docker/sandbox/seccomp-profile.json`, `docker/sandbox/harness/{python,…}/`.

## Trade-offs
- Convenience (a user who wants `requests` is out of luck) vs. isolation. OJ
  problems need only compute + I/O framing, so the allow-list covers the 99% case.
- The blocklist is the load-bearing control; never relax it "for one feature".

## Related
[[entities/sandbox]] · [[concepts/security-invariants]] ·
[[overview/judging-pipeline-overview]]
