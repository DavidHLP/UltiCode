---
title: Admin Comment-Read Port
type: concept
tags: [admin, forum, solution, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/admin/port/AdminCommentReadPort.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/port/adapter/AdminCommentReadAdapter.java
aliases: [ADR-0008, AdminCommentReadPort, AdminReadModel Forum Phase]
---

# Admin Comment-Read Port

> [!note] This page is the landed record of **ADR-0008 — Admin 评论
> 视图 Read Port 提取 (AdminCommentReadPort)**. Per [SCHEMA §3](../SCHEMA.md)
> the project keeps no separate `decisions/` dir &mdash; an ADR folds
> into `concepts/`.

## The problem

`AdminCommentServiceImpl` mixed two responsibilities:

1. **Comment CRUD itself** (flag / unflag / delete / bulk) &mdash;
   operates directly on `ForumCommentMapper` + `SolutionCommentMapper`,
   which is the legitimate core of admin comment management, not
   leakage.
2. **Cross-module enrichment for the comment view**: rendering
   `AdminCommentVO` needs three cross-module reads &mdash; author
   profile (user), parent post title (forum post), parent solution
   title (solution). Before this ADR those were done via 3 of the 5
   mapper injections:

   - `user.mapper.UserMapper` &mdash; `selectById` / `selectBatchIds`
     (author profile: username / avatar)
   - `forum.mapper.ForumPostMapper` &mdash; `selectById` /
     `selectBatchIds` (post title)
   - `solution.mapper.SolutionMapper` &mdash; `selectById` /
     `selectBatchIds` (solution title)

   Scattered across the `getForumComments` / `getSolutionComments` /
   `getAllComments` list paths + the `getComment` single path, plus
   three private batch-load helpers (`batchLoadUsers` /
   `batchLoadPosts` / `batchLoadSolutions`).

This is the third cross-module direct-connection the AdminReadModel
seam left behind after `AdminSubmissionReadPort` (dashboard global)
and `AdminUserStatsReadPort` (per-user stats). The
`AdminSubmissionReadPort` Javadoc states
*"Future phases add admin reads for user, contest, and forum"* &mdash;
this ADR lands the forum dimension (on the comment side).

`AdminUserStatsReadPort` returns primitive `long` / `int` &mdash; it
is per-user-counter semantics; comment enrichment needs entity
summaries (id / username / avatar) + string titles, a different
shape, so forcing it through that port would break its cohesion (ISP).

The deletion test confirms: deleting the port forces
`AdminCommentServiceImpl` to reach back into 3 mappers + re-write 3
batch-load helpers + re-write the 4 null guards in
`forumToAdminVO` / `solutionToAdminVO` (`post != null ? post.getTitle()
: null`, etc.) &mdash; complexity concentrates in the adapter rather
than shifting.

## The decision

Extract **`AdminCommentReadPort`** as a deep module, encapsulating
"cross-module reads for the admin comment view's enrichment" on the
read side:

```
admin/port/AdminCommentReadPort.java              // interface (3 methods + AuthorSummary record)
admin/port/adapter/AdminCommentReadAdapter.java   // the only @Component adapter
```

Interface shape (returns typed views, never entities):

```java
Map<String, AuthorSummary> findAuthorSummariesByIds(Set<String> userIds);
Map<String, String>        findForumPostTitlesByIds(Set<String> postIds);
Map<String, String>        findSolutionTitlesByIds(Set<String> solutionIds);

record AuthorSummary(String id, String username, String avatar) {}
```

- **Typed views, not entities**: `AuthorSummary` + plain `String`
  titles free `AdminCommentServiceImpl` from importing `User` /
  `ForumPost` / `Solution`. This is the deep module's leverage
  (contrast `AdminSubmissionReadPort` returning `Submission`: there
  the submission is admin's own operation target, so the entity is
  legitimate; here the user / post / solution are enrichment only,
  and returning the entity would shift rather than concentrate the
  coupling).
- **`AuthorSummary` is intentionally independent of
  `AdminCommentVO.AuthorInfo`**: the port is an architectural seam,
  the VO is a front-end contract, and the two evolve for different
  reasons. Decoupling keeps the port from being held hostage by VO
  field changes. `AdminCommentServiceImpl` does one extra
  `new AdminCommentVO.AuthorInfo(s.id(), s.username(), s.avatar())`
  mapping; the cost is acceptable.
- **Empty-input short-circuit**: the adapter returns `Map.of()` on
  `Set.isEmpty()` without touching the mapper &mdash; empty lists do
  not trigger pointless queries.
- **null-value tolerance**: a post / solution title may be `null`,
  and `Collectors.toMap` would NPE; the adapter accumulates with a
  manual `HashMap`, preserving null values, so callers can tell
  "entity missing" (key not in map) apart from "entity present, title
  null" (key in map, value null).
- **The adapter is the only place in admin that touches those 3
  mappers**: cross-module dependencies move from the ServiceImpl to
  a single adapter.

`AdminCommentServiceImpl` becomes:

- 3 dependencies removed (`UserMapper` / `ForumPostMapper` /
  `SolutionMapper`);
- 1 new `commentReadPort` dependency (constructor 5 → 3);
- the 3 batch-load helpers deleted;
- `forumToAdminVO` / `solutionToAdminVO` signature changes from
  `(comment, User, ForumPost)` to
  `(comment, AuthorSummary, String postTitle)`;
- `getComment` single path wraps the id in `Set.of(id)` to share the
  batch port (keeps the interface narrow).

**Write side untouched**: flag / unflag / delete / bulk still
operate directly on `ForumCommentMapper` + `SolutionCommentMapper`
&mdash; those target entities are the legitimate core of admin
comment CRUD, not cross-module leakage. This is what keeps the port
interface narrow: only the read-side enrichment moves.

Test responsibility re-divide:

- **Adapter unit test** (new `AdminCommentReadAdapterTest`): pins
  the empty-input short-circuit, the entity→view forced conversion,
  missing-id absent from the map, and null-title tolerance (the last
  one is critical &mdash; it pins the `Collectors.toMap` NPE
  regression point).
- **ServiceImpl unit test**: mocks `AdminCommentReadPort` (returning
  AuthorSummary / title maps), no longer stands up 3 cross-module
  mapper mocks; constructor shrinks from 5 mocks to 3.

## Where it lives

- `admin/port/AdminCommentReadPort.java` &mdash; interface, owned by
  admin.
- `admin/port/adapter/AdminCommentReadAdapter.java` &mdash; the only
  adapter; injects `UserMapper` + `ForumPostMapper` +
  `SolutionMapper`.
- `admin/service/impl/AdminCommentServiceImpl.java` &mdash;
  constructor 5 → 3 mappers, three batch-load helpers deleted.
- `admin/service/impl/AdminCommentServiceImplTest.java` &mdash;
  mocks shrink from 5 to 3; assembly tests now rely on the port's
  typed views.

## Consequences

**Positive**

- `AdminCommentServiceImpl` no longer imports
  `user.mapper` / `forum.mapper.ForumPostMapper` /
  `solution.mapper.SolutionMapper`, and no longer imports the
  `User` / `ForumPost` / `Solution` entities. Cross-module coupling
  is fully concentrated in the adapter. The AdminReadModel seam
  closes on the forum (comment) dimension.
- The list / single / all-paths + 4 null guards in VO assembly move
  to the adapter (empty map + missing key); `forumToAdminVO` in the
  ServiceImpl collapses to `author != null ? ... : null`.
- Enrichment gets an independent test surface: the adapter covers
  the boundary cases (empty / null / missing), the ServiceImpl
  covers port→VO assembly, no more entanglement.
- Naming / package / test conventions are byte-for-byte aligned with
  `AdminSubmissionReadPort` / `AdminUserStatsReadPort`; the next
  contest / other dimension can be lifted the same way.

**Negative / trade-offs**

- The adapter holds 3 cross-module mapper dependencies
  (user + forum + solution). Acceptable: the adapter is a boundary
  class whose job is to translate admin's typed read request into
  mapper calls; mappers are stateless tools.
- Single consumer (only `AdminCommentServiceImpl`). Accepted anyway:
  value comes from concentration + test surface + AdminReadModel
  seam progress. If the forum / solution modules' own admin read
  paths later need these summaries, the port is in place.
- The `getComment` single path wraps the id in `Set.of(id)` and pays
  one extra `Map.get`. Acceptable: avoids a separate set of
  single-id methods that would widen the interface.

## Rejected alternatives

- **Push the enrichment methods into the existing
  `AdminUserStatsReadPort`**. Rejected: that port is per-user-counter
  semantics (`long` / `int`); mixing author summaries + post /
  solution titles breaks its ISP, and post / solution titles are
  unrelated to user stats.
- **Return entities (`User` / `ForumPost` / `Solution`) instead of
  typed views**. Rejected: the ServiceImpl would still import the
  entities, write `post.getTitle()`, and hold null guards &mdash;
  leverage shifts rather than concentrates, the deletion test fails.
- **Extract only the user portion, reuse an existing user read
  model**. Rejected: the user module currently exposes no author-
  summary port for admin, and forum-post / solution-title have
  nowhere else to live. The three are co-evolving for comment
  enrichment, so splitting them would spawn three micro-ports and
  violate the deep-module concentration rule.
- **Move the write side (flag / unflag / delete) through the port
  too**. Rejected: the writes target admin's own comment-CRUD
  entities (`ForumComment` / `SolutionComment`), not cross-module
  leakage; pushing them in would mix read + write in one interface
  and dilute its meaning. Read / write are kept separate, and the
  write side keeps direct mapper operations.

## Related

- `admin/port/AdminSubmissionReadPort.java` &mdash; AdminReadModel
  seam phase 1
- `admin/port/AdminUserStatsReadPort.java` &mdash; phase 2
  (per-user stats, see [[archive/concepts/admin-user-stats-read-port]])
- [[archive/concepts/admin-user-stats-read-port]] &mdash; ADR-0007, the
  per-user-dimension sibling
- [[archive/concepts/achievement-projection]] / [[archive/concepts/moderation-projection]] /
  [[archive/concepts/problem-detail-port]] &mdash; sibling port / projection
  extractions
