# Phase 23: N+1 Query Optimization - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-20
**Phase:** 23-n-1-query-optimization
**Areas discussed:** JOIN FETCH strategy, Contest rankings, Problem list, Submission list

---

## JOIN FETCH strategy

| Option | Description | Selected |
|--------|-------------|----------|
| @Select annotation with JOIN FETCH | MyBatis-Plus explicit SQL annotation | ✓ |
| XML mapper files | Separate XML mapper files for complex JOINs | |
| lambdaQuery() join methods | MyBatis-Plus lambda join syntax | |

**User's choice:** @Select annotation (recommended)
**Notes:** MyBatis-Plus BaseMapper — no XML mapper files in project. Most predictable for complex FETCH.

---

## Contest rankings (PERF-01)

| Option | Description | Selected |
|--------|-------------|----------|
| selectParticipantsWithDetails JOIN FETCH | New method on ContestParticipantMapper with user/contest JOIN | ✓ |
| Modify existing ranking query | Add JOIN to existing ranking method | |

**User's choice:** selectParticipantsWithDetails method (recommended)
**Notes:** Contests ranked by participants — need user details + contest info in single query.

---

## Problem list tags/difficulty (PERF-02)

| Option | Description | Selected |
|--------|-------------|----------|
| selectProblemListWithTags JOIN FETCH | New method on ProblemMapper with tags JOIN | ✓ |
| Batch select after main query | N+1 alternative, less optimal | |

**User's choice:** selectProblemListWithTags JOIN FETCH (recommended)
**Notes:** Problem list page loads tags and difficulty in single query.

---

## Submission list problem metadata (PERF-03)

| Option | Description | Selected |
|--------|-------------|----------|
| selectSubmissionsWithProblem JOIN FETCH | New method on SubmissionMapper with problem JOIN | ✓ |
| Batch select problem metadata | N+1 alternative | |

**User's choice:** selectSubmissionsWithProblem JOIN FETCH (recommended)
**Notes:** Submission list shows problem title/difficulty — need single query.

---

## Entity association decisions

**User's choice:** Check and add @One/@Many associations as needed to support JOIN FETCH
**Notes:** D-09, D-10 — entity associations may need to be explicit for JOIN FETCH to work.

---

## Deferred Ideas

None — discussion stayed within phase scope

