# Feature Landscape: Seed Data Expansion

**Domain:** Online programming platform (LeetCode-like) seed data
**Researched:** 2026-04-19
**Confidence:** HIGH (based on existing schema and seed data patterns)

## Executive Summary

The existing platform has 8 solutions (heavily concentrated on problem 1), ~400 submissions from V17 seed data, and 6 collections with 7 items. The expansion targets ~100 solutions (scattered across 32 problems), diverse submission statuses, and ~50 collections organized by category. The existing V9 and V17 migrations provide the seed data pattern to follow. All foreign key constraints are enforced -- solutions depend on problems and users, collections depend on users, collection_items reference existing problem_lists or other targets.

## Current State (Baseline)

| Entity | Current | Target | Gap |
|--------|---------|--------|-----|
| Solutions | 8 | ~100 | +92 |
| Submissions | ~400 (from V17) | diverse statuses | WA/MLE/RE/TLE underrepresented |
| Collections | 6 (17 items) | ~50 | +44 collections |
| Collection Items | 7 | many | items referencing lists/problems |

## Solutions Seed Data

### Schema Summary

`solutions` table: `id`, `problem_id`, `user_id`, `title`, `content` (markdown), `summary`, `language`, `tags` (JSON array), `views`, `is_published`, `published_at`, `published_by`, `is_flagged`, `is_deleted`.

`solution_comments` table: threaded comments with `parent_id` self-reference for nesting.

### Distribution Targets

| Problem Difficulty | Solutions per Problem | Rationale |
|-------------------|----------------------|-----------|
| Easy (problems 1-10) | 2-3 solutions | High traffic, beginner audience benefits from multiple approaches |
| Medium (problems 11-24) | 1-2 solutions | Standard coverage |
| Hard (problems 25-32) | 1-2 solutions | Fewer solvers, quality over quantity |

### Language Mix

The 5 supported languages are: TypeScript, JavaScript, Java, Python, C++. Seed data should reflect the existing distribution in V9 -- TypeScript and JavaScript dominate. Do not seed C++/Java/Python solutions for Easy problems; save them for Medium/Hard to showcase real language usage patterns.

### Content Quality Bar

- **Title**: Descriptive, includes algorithm/pattern name (e.g., "哈希表解法 -- O(n) 时间复杂度")
- **Content**: Full markdown with code block, explanation of approach, time/space complexity
- **Summary**: 1-2 sentence description (used in cards/search results)
- **Tags**: 2-4 relevant tags from existing tag vocabulary (hash-table, array, binary-search, sliding-window, etc.)
- **Views**: Realistic distribution (0-500 range), vary by solution quality
- **Code**: Must be syntactically correct, runnable starter-like code (not just comments)

### Anti-Features (Do Not Seed)

- Placeholder content ("TODO", "write solution here")
- Duplicate approaches for the same problem with no differentiation
- Solutions referencing non-existent problem_ids or user_ids
- Hardcoded timestamps that create impossible sequences (e.g., comment before solution creation)
- Solutions with `is_deleted=1` or `is_flagged=1` -- keep these clean

## Submissions Seed Data

### Status Distribution

The existing V17 seed data already establishes a skill-based distribution (beginner ~30% AC, intermediate ~60% AC, advanced ~85% AC). This is the right model.

Target overall distribution across all ~400 existing + new submissions:

| Status | Target % | Notes |
|--------|----------|-------|
| Accepted (AC) | 45-55% | Platform should feel encouraging |
| Wrong Answer (WA) | 20-30% | Most common failure mode |
| Time Limit Exceeded (TLE) | 8-12% | Common for O(n^2) attempts on Hard |
| Runtime Error (RE) | 5-10% | Edge case bugs, uninitialized variables |
| Memory Limit Exceeded (MLE) | 3-5% | O(n^2) space solutions, large arrays |
| Compilation Error (CE) | 2-5% | Syntax errors, type mismatches |
| Presentation Error (PE) | 1-2% | Output format issues |
| System Error (SE) | <1% | Judge infrastructure issues, almost never shown to users |

### Code Content

Submissions should contain plausible code:
- Include actual code snippets (not just comments)
- For WA: code that compiles but produces wrong output on edge cases
- For TLE: nested loops where an O(n) solution exists
- For RE: missing null checks, array index out of bounds, uninitialized variables
- For MLE: storing full Cartesian product in memory
- For CE: syntax errors, missing imports, wrong types
- The `code` field is a TEXT column -- can store full solutions

### Runtime and Memory Percentiles

V17 seed data includes `runtime_percentile` and `memory_percentile`. Seed realistic values:
- AC submissions: runtime 20-95%, memory 30-90%
- Non-AC submissions: runtime/memory typically 0 or very low

## Collections Seed Data

### Schema Summary

`collections`: `id`, `user_id`, `name`, `description`, `icon`, `color`, `sort_order`, `is_default`
`collection_items`: `id`, `collection_id`, `target_id`, `target_type`, `sort_order`, `note`

target_type enum: `PROBLEM`, `SOLUTION`, `FORUM_POST`, `PROBLEM_LIST`, `SOLUTION_COMMENT`, `FORUM_COMMENT`

### Category Strategy

Target ~50 collections across these categories:

| Category | Examples | Count Target |
|----------|----------|-------------|
| Difficulty-based | "Easy 热身", "Medium 进阶", "Hard 挑战" | 6 (2 per difficulty) |
| Company-tagged | "字节跳动高频", "Meta 面试", "Amazon 必刷" | 10-15 |
| Pattern-tagged | "滑动窗口专项", "二分查找专项", "动态规划入门" | 8-10 |
| Problem-list-backed | Collections referencing existing problem_lists (list-interview-100, list-essentials, etc.) | 10-15 |
| Contest-themed | "周赛练习", "双周赛回顾" | 3-5 |
| Personal study | "面试倒计时 30 天", "寒假刷题计划" | 5-10 |

### Collection Items per Collection

- Minimum: 3 items (collections with fewer look unmaintained)
- Typical: 5-15 items
- Maximum: 30 items (beyond that should probably be split)

### Icons and Colors

Use Lucide icon names and Tailwind-compatible color names. Existing seed uses `icon: NULL, color: NULL` which is acceptable but plain. For richer collections, use real values:

Icons: `Trophy`, `Code2`, `ArrowUpDown`, `Database`, `Clock`, `Star`, `Bookmark`, `Folder`
Colors: `amber`, `sky`, `emerald`, `slate`, `rose`, `violet`

## Dependencies and Constraints

### Foreign Key Requirements

All seed data must respect foreign keys:

1. **solutions.problem_id** -> `problems.id` (32 existing problems, IDs 1-32)
2. **solutions.user_id** -> `users.id` (existing seeded users: user-yuki, user-alex, user-chen, user-sara, user-max, user-petr, user-tourist, user-lily, user-emma, user-david, user-tom, user-scott)
3. **collection_items.target_id** -> existing targets (problem_lists IDs like list-essentials, list-interview-100, list-sliding-window, list-graph-dfs, list-hard-bench, list-database, list-concurrency, list-graph-advanced)
4. **collections.user_id** -> `users.id`

### Tag Vocabulary (Existing)

Use only these existing tags to avoid FK violations:
algorithms, array, backtracking, bfs, binary-search, bit-manipulation, concurrency, database, design, dfs, divide-and-conquer, dynamic-programming, graph, greedy, hash-table, heap, intervals, linked-list, math, matrix, queue, recursion, shell, sliding-window, sorting, stack, string, tree, two-pointers, union-find

### Language Vocabulary

Use only: `typescript`, `javascript`, `java`, `python`, `cpp` (not `c`, not `c++`)

### Submission Status Vocabulary

Use exact strings from SubmissionStatusMeta: `Pending`, `Judging`, `Accepted`, `Wrong Answer`, `Time Limit Exceeded`, `Memory Limit Exceeded`, `Output Limit Exceeded`, `Runtime Error`, `Compilation Error`, `Presentation Error`, `System Error`

## Seed Data Quality Checklist

Before accepting seed data as complete:

- [ ] Solutions: All 32 problems have at least 1 solution
- [ ] Solutions: Problems 1-10 have 2-3 solutions each
- [ ] Solutions: Title + content + summary are all non-empty and substantive
- [ ] Solutions: Tags are valid (from existing vocabulary)
- [ ] Solutions: Languages are from the 5-language whitelist
- [ ] Submissions: At least 6 different status types represented
- [ ] Submissions: Code field contains actual code, not just comments
- [ ] Submissions: Timestamps are chronologically plausible
- [ ] Collections: Each has at least 3 items
- [ ] Collections: Category names are descriptive (not generic "收藏夹" for all)
- [ ] Collections: Icon and color are set (or explicitly NULL if no theme)
- [ ] All foreign key references resolve to existing records
- [ ] No orphaned records (e.g., solution_comments referencing non-existent solution_id)
- [ ] SET FOREIGN_KEY_CHECKS=0/1 wrapper present in SQL
- [ ] Migration follows V{number}_ descriptive naming convention

## Gap Analysis

| Area | Current | Target | Realistic per Sprint |
|------|---------|--------|---------------------|
| Solutions | 8 | ~100 | 20-25 per week |
| Collection items | 7 | ~300+ | 50-75 per week |
| New collections | 6 | ~50 | 8-10 per week |
| Submission statuses | AC-dominant | Balanced | Already handled by V17 |

## Sources

- db-manager/migrations/V9__solution_schema.sql (existing solution seed pattern)
- db-manager/migrations/V17__recommendation_seed_submissions.sql (submission distribution model)
- db-manager/migrations/V8__collection_schema.sql (collection/collection_item pattern)
- db-manager/migrations/V15__featured_problem_lists.sql (existing problem_lists for collection_items)
- backend-spring/modules/submission/service/impl/SubmissionServiceImpl.java (SubmissionStatusMeta enum values)
- db-manager/migrations/V2__problem_schema.sql (problem_tags, problem_lists existing data)
