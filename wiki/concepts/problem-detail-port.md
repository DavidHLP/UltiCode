---
title: Problem Detail Port
type: concept
tags: [problem, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/problem/port/ProblemDetailPort.java
  - backend-spring/src/main/java/com/ulticode/modules/problem/port/DefaultProblemDetailPort.java
aliases: [ADR-0006, ProblemDetailPort, Problem Detail Deep Module]
---

# Problem Detail Port

> [!note] This page is the landed record of **ADR-0006 — Problem 模块
> 提取 ProblemDetailPort 深模块**. Per [SCHEMA §3](../SCHEMA.md) the
> project keeps no separate `decisions/` dir &mdash; an ADR folds into
> `concepts/`.

## The problem

`ProblemServiceImpl` had its read side lifted into `ProblemProjection`
in an earlier round, but the write side still held a **111-LOC
`updateProblemDetail` 巨方法** (lines 212–322). It bundled four
mutually orthogonal low-level entity writes:

1. `ProblemDetail` upsert (selectOne → field-level conditional update
   → insert/updateById);
2. `ProblemLanguage` rebuild (delete + `findByValue` validate + batch
   insert);
3. `ProblemExample` rebuild (parse JSON → delete → batch insert);
4. `ProblemTagRelation` rebuild (lookup by label → delete relations →
   batch insert).

Plus the helper `updateProblemLanguages` (26 LOC), totalling **137 LOC
of low-level entity work** sitting bare in the service. It is neither
a state machine (the state machine only mutates the `problems` row) nor
a projection (projection is read-only). It was a forgotten "write-side
domain" &mdash; when `ProblemProjection` was extracted, the matching
write side was not moved with it.

The deletion test confirms it: deleting these 137 LOC concentrates
complexity (each of the four satellite writes has a single home +
`ProblemServiceImpl` returns to a pure state machine) rather than
shifting it.

## The decision

Extract a **`ProblemDetailPort`** deep module that owns the write
lifecycle of the `problem_details` row and its three satellite tables.

```
problem/port/ProblemDetailPort.java          // interface
problem/port/DefaultProblemDetailPort.java   // the only adapter
```

Interface shape (a single method, mirroring the
`SubmissionWritePort.submit(userId, CreateSubmissionDTO)` precedent
where the port accepts a controller DTO):

```java
void applyDetailUpdate(Long problemId, Problem problem, UpdateProblemDTO updateDTO);
```

- Takes `Problem` (not just `id`): the new `ProblemDetail` row needs
  `problem.slug` denormalized (the `problem_details.slug` column is
  NOT NULL).
- `@Transactional` lives on the adapter method (mirroring
  `DefaultSubmissionWritePort`); `ProblemServiceImpl.updateProblem`
  keeps its own `@Transactional`, and Spring `PROPAGATION_REQUIRED`
  joins the port into the outer transaction &mdash; atomicity is
  preserved.

`ProblemServiceImpl` becomes:

- 6 dependencies removed (5 mappers + `ObjectMapper`);
- 1 new `problemDetailPort` dependency;
- `updateProblem` body replaces the `updateProblemDetail(...)` call
  with `problemDetailPort.applyDetailUpdate(...)`;
- the two private helpers (`updateProblemDetail` /
  `updateProblemLanguages`) deleted.

## Where it lives

- `problem/port/ProblemDetailPort.java` &mdash; interface.
- `problem/port/DefaultProblemDetailPort.java` &mdash; the only
  adapter; injects the four satellite mappers
  (`ProblemDetailMapper` / `ProblemExampleMapper` /
  `ProblemLanguageMapper` / `ProblemTagMapper` /
  `ProblemTagRelationMapper`) + the shared `ObjectMapper`.
- `problem/service/impl/ProblemServiceImpl.java` &mdash; 409 → ~250
  LOC; back to a pure state machine (CRUD on `problems` + premium
  guard + cross-module `findById/findBySlug/toVO` facade).

## Consequences

**Positive**

- `ProblemServiceImpl` returns to a pure state machine: 409 → ~250
  LOC, only CRUD on `problems` + premium guard + cross-module
  find/toVO facade.
- Each of the 4 satellite write branches gets an independent test
  surface (mock 5 mappers + real `ObjectMapper`); the write path no
  longer needs `ProblemMapper` / `ProblemVersionService` /
  `ProblemProjection` stood up to be exercised.
- Symmetric with `ProblemProjection` (read side): the problem module
  now has a complete read/write seam pair. Naming / package structure
  align with `submission/port/`.

**Negative / trade-offs**

- Single consumer (only `ProblemServiceImpl`; `AdminProblemServiceImpl`
  is read-only + bulk-edit only mutates difficulty, never the
  detail). Accepted: the value is concentration + test surface, not
  duplication removal. If admin / import paths later need detail
  writes, the port is already in place.
- `Problem` entity crosses the seam (only to denormalize slug).
  Acceptable: the port and the state machine live in the same module,
  and `Problem` is not a cross-domain coupling.
- The four mappers are now held by both `DefaultProblemProjection`
  (read) and `DefaultProblemDetailPort` (write) &mdash; this is the
  expected read/write split, and mappers are stateless tools.

## Rejected alternatives

- **Wrap an extra `ProblemDetailUpdate` record** (don't accept
  `UpdateProblemDTO`). Rejected: `SubmissionWritePort.submit(userId,
  CreateSubmissionDTO)` already set the precedent of "port accepts
  controller DTO"; an extra record type adds a file and forces the
  service to do a DTO→record translation, with no offsetting gain.
- **Split into 4 port methods** (`upsertDetail` / `rebuildLanguages`
  / `rebuildExamples` / `rebuildTagRelations`). Rejected: the four
  writes must commit atomically in a single transaction &mdash; all
  or nothing. Splitting forces the caller to call 4 methods inside
  one transaction, which widens the interface and leaks the rebuild
  semantics.
- **Rewire `AdminProblemServiceImpl` through the port too.**
  Rejected: admin currently does not write the detail path, so there
  is no duplication to remove; keeping it as-is avoids a
  no-payoff change.

## Related

- [[concepts/module-layering]] &mdash; Projection / Port / Inspector
  pattern
- `submission/port/SubmissionWritePort.java` &mdash; port + DTO
  argument precedent
- `problem/projection/ProblemProjection.java` &mdash; the symmetric
  read-side module
- [[concepts/achievement-projection]] &mdash; ADR-0005, ADR-0004, the
  same Projection extraction shape
