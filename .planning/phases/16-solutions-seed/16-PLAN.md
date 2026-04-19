---
phase: 16
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - db-manager/migrations/V23__solutions_seed.sql
autonomous: true
requirements:
  - SOL-01
  - SOL-02
  - SOL-03

must_haves:
  truths:
    - "User can browse solutions page and see each of 32 problems has at least 1 solution"
    - "User can view a solution with Chinese comments, Markdown headings/lists, and syntax-highlighted code blocks"
    - "Every solution references a valid problem_id and user_id in the database"
    - "Medium-difficulty problems have 2-3 solutions, total ~100 solutions"
  artifacts:
    - path: "db-manager/migrations/V23__solutions_seed.sql"
      provides: "~100 solution INSERT statements with Chinese Markdown content"
      min_lines: 100
  key_links:
    - from: "V23__solutions_seed.sql"
      to: "problems table"
      via: "problem_id FK"
      pattern: "problem_id IN (1..32)"
    - from: "V23__solutions_seed.sql"
      to: "users table"
      via: "user_id FK"
      pattern: "user_id IN (user-yuki, user-alex, user-chen, user-sara, user-max, ...)"
---

<objective>
Generate ~92 new solution INSERT statements for V23__solutions_seed.sql, bringing total from 8 to ~100. Each solution has Chinese Markdown content (headings, lists, code blocks), valid FK references to 32 problems and existing users, and distributes across difficulty levels (Easy: 3-4 each, Medium: 2-3 each, Hard: 1-2 each).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@db-manager/migrations/V9__solution_schema.sql
  - Solutions table schema: id (varchar(40)), problem_id (bigint), user_id (varchar(40)), title, content, summary, language, tags (JSON), views, created_at, updated_at, is_published, published_at, published_by, is_flagged, flagged_reason, flagged_at, is_deleted, deleted_at, deleted_by
  - 8 existing solutions with IDs: sol-001 through sol-008
  - New solutions should use IDs: sol-009 through sol-100
  - Existing INSERT pattern uses `NOW(3)` for datetime(3) columns
  - All existing solutions have is_published=1, published_at=NOW(3), published_by=self, is_deleted=0

@db-manager/migrations/V17__recommendation_seed_submissions.sql
  - V17 confirmed valid user IDs: user-emma, user-yuki, user-sara, user-lily, user-max, user-alex, user-benq, user-david, user-kevin, user-scott, user-tom, u-001, u-002 (plus admin and system users)
  - V17 confirmed valid problem IDs: 1-40 (32 problems per V2 + 8 from V16)
  - Patterns: INSERT uses UUID() for submissions; solutions use named IDs

@db-manager/migrations/V2__problem_schema.sql
  - 32 problems with difficulty: Easy, Medium, Hard
  - Problem IDs: 1-40 (8 are from V16 recommendation seed, IDs 33-40 per V17 usage)
  - Need to check actual difficulty distribution from problem data

Existing solutions cover problem_ids: 1, 2, 3, 4, 5 (5 problems have 1 solution each)
Gap: 27 problems have ZERO solutions
</context>

<tasks>

<task type="auto">
  <name>Task 1: Audit existing solutions and plan distribution</name>
  <files>db-manager/migrations/V9__solution_schema.sql</files>
  <action>
Audit the 8 existing solutions (sol-001 through sol-008) from V9 to extract:
1. Which problem_ids already have solutions
2. What languages, tags, and content patterns are used

From V17__recommendation_seed_submissions.sql, extract the full list of valid user IDs.

Query the 32 problems for their difficulty levels (Easy/Medium/Hard) from V2 problem data. Distribute target ~100 solutions:
- 5 existing solutions cover 5 problems (sol-001=prob1, sol-002=prob1, sol-003=prob1, sol-004=prob1, sol-005=prob2, sol-006=prob3, sol-007=prob4, sol-008=prob5)
- Remaining 27 problems need at least 1 solution each = 27 new
- Total target: ~100, so add ~92 new solutions
- Distribution target:
  - Easy problems (8-10): 3-4 each = ~30 solutions
  - Medium problems (16-18): 2-3 each = ~40 solutions
  - Hard problems (4-6): 1-2 each = ~8 solutions
  - Total new = 92 (30+40+8=78... adjust to reach ~100)

Calculate exact per-problem counts to hit ~100 total.
  </action>
  <verify>
  <automated>grep -c "INSERT INTO \`solutions\`" db-manager/migrations/V23__solutions_seed.sql</automated>
  </verify>
  <done>Distribution table with problem_id, difficulty, solution_count, new_count ready for writing</done>
</task>

<task type="auto">
  <name>Task 2: Write V23__solutions_seed.sql migration</name>
  <files>db-manager/migrations/V23__solutions_seed.sql</files>
  <action>
Create V23__solutions_seed.sql following V9 patterns exactly:

Structure:
```sql
SET FOREIGN_KEY_CHECKS=0;
START TRANSACTION;
-- V23__solutions_seed.sql: ~92 new solutions
-- Covers 27 previously uncovered problems + additional solutions for existing 5
-- Total: ~100 solutions across 32 problems

-- [INSERT statements]
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-009',N,'user-xxx',N'...',N'...',N'...','typescript','["tag1","tag2"]',RAND_INT,NOW(3),NOW(3),1,NOW(3),'user-xxx',0,NULL,NULL,0,NULL,NULL);
-- ... repeat ~92 times
COMMIT;
SET FOREIGN_KEY_CHECKS=1;
```

For each solution:
- ID: sol-009 through sol-100 (sequential, no gaps, no overlap with sol-001..sol-008)
- problem_id: Valid from 1-32 (verify against V2 problem IDs)
- user_id: Pick from validated user list (rotate through ~10-12 users to distribute)
- title: Chinese title with algorithm/method name, max 255 chars
- content: Chinese Markdown with at minimum:
  - ## 题目理解 (section heading)
  - ## 解题思路 (section heading)
  - ### 方法一 / ### 方法二 (subsections)
  - - 列表项 with Chinese explanations
  - ```typescript / ```javascript / ```python code blocks with Chinese inline comments
  - Complexity analysis at bottom
- summary: 1-2 sentence Chinese summary, max ~200 chars
- language: one of typescript, javascript, python, java, cpp, go, bash, sql
- tags: JSON array of 2-4 relevant tags per solution
- views: Random 10-500 (lower for newer, higher for older dates)
- created_at: Dates from 2025-11 to 2026-03 (distributed)
- updated_at: Same or slightly after created_at
- All soft-delete and flag fields: 0/NULL as per published solution

Content generation rules per problem:
- Match algorithm to problem type (e.g., two-sum -> hash map, sliding window, DP)
- At least one solution per problem with detailed code block
- At least one solution per problem with complexity analysis
- Multiple solutions per problem should offer different approaches (brute force -> optimized)
- Use authentic LeetCode-style Chinese commentary
- Include inline Chinese comments in code blocks (// 遍历数组, // 检查差值, etc.)

Problem-specific content targets:
- problem_id 1 (两数之和): hash map, two-pass, brute force solutions
- problem_id 2 (无重复字符): sliding window, set-based
- problem_id 6 (合并表): SQL JOIN solutions
- problem_id 9 (最大子数组和): Kadane, brute force
- problem_id 13 (验证回文): two-pointer, reversed string
- problem_id 20 (爬楼梯): DP, Fibonacci
- problem_id 33 (有效括号): stack, counter
- problem_id 40 (反转链表): iterative, recursive
- Cover all 27 uncovered problems with at least 1 solution each
  </action>
  <verify>
  <automated>grep -c "^INSERT INTO \`solutions\`" db-manager/migrations/V23__solutions_seed.sql</automated>
  </verify>
  <done>SQL file exists with ~92 INSERT statements, all referencing valid problem_ids 1-32 and user_ids from validated user list</done>
</task>

<task type="auto">
  <name>Task 3: Verify FK integrity and SQL validity</name>
  <files>db-manager/migrations/V23__solutions_seed.sql</files>
  <action>
Run validation checks on V23__solutions_seed.sql:

1. Count INSERT statements - should be ~92:
   grep -c "^INSERT INTO \`solutions\`" V23__solutions_seed.sql

2. Verify all problem_ids are valid (1-32):
   Extract all problem_id values, check against known valid IDs from V2

3. Verify all user_ids are valid (from V1 user list):
   Extract all user_id values, check against known valid user IDs

4. Verify no duplicate IDs:
   Extract all solution IDs, ensure no overlap with sol-001..sol-008 and no duplicates

5. Verify all INSERT lines have correct column count (20 columns):
   Count backticks and values in each INSERT

6. Verify NOW(3) usage for datetime(3) columns (created_at, updated_at, published_at)

7. Verify JSON tags are valid (array syntax with double quotes)

8. Verify Chinese content is preserved (check for common Chinese characters)

If any check fails, fix the migration file.
  </action>
  <verify>
  <automated>grep -c "^INSERT INTO \`solutions\`" db-manager/migrations/V23__solutions_seed.sql && echo "~92 expected"</automated>
  </verify>
  <done>All INSERT statements pass FK validity checks: problem_ids in 1-32 range, user_ids from V1 user list, no duplicate IDs</done>
</task>

</tasks>

<verification>
## Source Coverage Audit

| Source | Item | Coverage |
|--------|------|----------|
| ROADMAP.md | ~100 solutions, 1-3 per problem | Task 1+2: ~92 new INSERTs |
| SOL-01 | Each problem 1+ solution, Medium 2-3 | Task 1 distribution, Task 2 INSERTs |
| SOL-02 | Chinese + Markdown + code blocks | Task 2: Content template |
| SOL-03 | Valid FK (user_id, problem_id) | Task 3: FK validation |
</verification>

<success_criteria>
- V23__solutions_seed.sql exists at db-manager/migrations/
- File contains ~92 new INSERT statements (total solutions = 8 existing + 92 new = ~100)
- All problem_ids reference valid problem IDs from V2 (1-32)
- All user_ids reference valid user IDs from V1 (user-yuki, user-alex, user-chen, user-sara, user-max, user-emma, user-lily, user-scott, user-tom, user-david, user-kevin, user-benq, u-001, u-002, etc.)
- All solution IDs are unique and non-overlapping with sol-001..sol-008
- Content includes Chinese Markdown: ## headings, - lists, ```language code blocks
- Content includes Chinese inline comments in code blocks
- File follows Flyway migration structure: SET FOREIGN_KEY_CHECKS=0, START TRANSACTION, COMMIT, SET FOREIGN_KEY_CHECKS=1
- Datetime columns use NOW(3)
- JSON tags use valid array syntax
</success_criteria>

<output>
After completion, create `.planning/phases/16-solutions-seed/16-01-SUMMARY.md`
</output>
