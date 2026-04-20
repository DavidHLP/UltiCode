# Phase 25: Large File Refactoring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-20
**Phase:** 25-Large File Refactoring
**Areas discussed:** ForumServiceImpl split, CodeExecutionService extraction, ContestServiceImpl split

---

## ForumServiceImpl Split (REF-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Facade delegation | Keep ForumService interface + impl as facade, new focused services for post/comment/vote | ✓ |
| Independent interfaces | Replace ForumService entirely with 3 separate interfaces | |
| Single massive impl | Keep as-is, just extract helper methods | |

**User's choice:** Facade delegation — preserve controller contract while distributing logic
**Notes:** Three focused services (ForumPostService, ForumCommentService, ForumVoteService) each with own mapper

---

## CodeExecutionService Extraction (REF-02)

| Option | Description | Selected |
|--------|-------------|----------|
| SandboxService + CodeExecutionHelper | Extract security/cgroup ops to SandboxService, language parsing to CodeExecutionHelper | ✓ |
| Two equal services | Split into CodeExecutionService + SandboxService as peer services | |
| Single helper class | Extract everything into one helper, keep CodeExecutionService as orchestrator | |

**User's choice:** SandboxService + CodeExecutionHelper split
**Notes:** SandboxService handles security constraints (cgroup, seccomp); CodeExecutionHelper handles per-language logic

---

## ContestServiceImpl Split (REF-03)

| Option | Description | Selected |
|--------|-------------|----------|
| RankingService + SchedulerService | Use existing RankingServiceImpl, extract scheduling to new ContestSchedulerService | ✓ |
| Three equal services | Split ranking, scheduling, and core contest into peer services | |
| Extract ranking only | Move only ranking to RankingService, keep rest in ContestServiceImpl | |

**User's choice:** RankingService + ContestSchedulerService
**Notes:** RankingServiceImpl already exists; ContestSchedulerService is new, handles time-driven auto-status updates

---

## Claude's Discretion

- Exact package location for new forum service interfaces
- Whether CodeExecutionHelper is a @Service or plain @Component
- Whether to use @RequiredArgsConstructor (Lombok) or explicit constructors
- Whether ForumServiceImpl facade methods delegate synchronously or can be made async

## Deferred Ideas

None — all three splits discussed and decided within phase scope.
