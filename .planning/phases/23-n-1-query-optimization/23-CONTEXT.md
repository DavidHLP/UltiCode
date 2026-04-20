# Phase 23: N+1 Query Optimization - Context

**Gathered:** 2026-04-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Optimize list queries to use JOIN FETCH instead of N+1 lazy loads. Three target areas: contest rankings, problem list, and submission list.

</domain>

<decisions>
## Implementation Decisions

### JOIN FETCH strategy
- **D-01:** Use MyBatis-Plus `@Select` annotations with explicit JOIN FETCH for each target mapper
- **D-02:** Add `@Results` / `@Result` annotations to map joined entity fields to correct Java fields

### Contest rankings (PERF-01)
- **D-03:** ContestParticipantMapper — add `selectParticipantsWithDetails@Select JOIN FETCH user, contest` method
- **D-04:** Contest rankings page loads all participants in single query

### Problem list tags/difficulty (PERF-02)
- **D-05:** ProblemMapper — add `selectProblemListWithTags@Select JOIN FETCH tags` method
- **D-06:** Tags and difficulty loaded in same query as problem list

### Submission list problem metadata (PERF-03)
- **D-07:** SubmissionMapper or ContestSubmissionMapper — add `selectSubmissionsWithProblem@Select JOIN FETCH problem` method
- **D-08:** Problem title and difficulty metadata loaded in same query as submission list

### Entity association (supporting)
- **D-09:** Check if Problem entity has `@One`/`@Many` associations for tags — if not, add them to support JOIN FETCH
- **D-10:** Check if ContestSubmission entity has `@One` association for problem — if not, add it

### Batch fetch alternative
- **D-11:** If JOIN FETCH proves too complex, use `in` clause + batch select as fallback

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Performance requirements
- `.planning/REQUIREMENTS.md` §PERF-01 — Contest rankings JOIN FETCH
- `.planning/REQUIREMENTS.md` §PERF-02 — Problem list JOIN FETCH
- `.planning/REQUIREMENTS.md` §PERF-03 — Submission list JOIN FETCH

### MyBatis-Plus patterns
- MyBatis-Plus `BaseMapper` extends default CRUD — custom JOIN FETCH via `@Select` annotation
- Use `@Results` + `@Result` for column-to-field mapping
- LambdaQuery + join methods as alternative to raw SQL

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ContestParticipantMapper` — extends BaseMapper, no custom JOIN FETCH yet
- `ContestSubmissionMapper` — extends BaseMapper, no custom JOIN FETCH yet
- `ProblemMapper` — extends BaseMapper, no custom JOIN FETCH yet
- `SubmissionMapper` — extends BaseMapper, no custom JOIN FETCH yet

### Established Patterns
- MyBatis-Plus BaseMapper — no XML mapper files, all SQL via annotations or BaseMapper defaults
- MyBatis-Plus `lambdaQuery()` supports join operations but explicit @Select is more predictable for complex FETCH

### Integration Points
- `RankingService` — calls ContestParticipantMapper for rankings
- `ProblemService` — calls ProblemMapper for list queries
- `SubmissionService` — calls SubmissionMapper for list queries

</code_context>

<specifics>
## Specific Ideas

No specific references from prior discussion — standard MyBatis-Plus JOIN FETCH patterns apply.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 23-n-1-query-optimization*
*Context gathered: 2026-04-20*
