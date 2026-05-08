# Fix Truncated Solution Content for Two Sum + Global Scan

## TL;DR

> **Quick Summary**: Fix truncated solution content for "两数之和" (Two Sum) by replacing database content with full markdown from migration files (V13 for sol-001~004, V23 for sol-009~011), then perform a global scan to identify other solutions with similar truncation issues.
>
> **Deliverables**:
> - V28 patch migration with UPDATE/INSERT statements for 7 Two Sum solutions
> - Global scan report documenting all truncated solutions across the database
> - Verification commands confirming full content is served via API
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 3 waves
> **Critical Path**: Task 1 → Task 2 → Task 3 → F1-F4

---

## Context

### Original Request
User reported that the "两数之和" (Two Sum) solution data is too sparse - only one sentence, no code, no explanation. Screenshot showed: "Javascript 算法 JS 哈希表 使用原生 Map 对象。 map javascript 0 0 102 0 评论 分享 收藏 评论 加入讨论... 暂无评论"

### Interview Summary
**Key Discussions**:
- **Fix scope**: Fix existing 4 solutions (sol-001 to sol-004) + add missing 3 (sol-009 to sol-011)
- **Data source**: Use V13 enriched content for sol-001~004, V23 content for sol-009~011
- **Fix method**: Create new patch migration (V28), do NOT modify existing migration files
- **Global scan**: Check all solutions for similar truncation issues

**Research Findings**:
- V9__solution_schema.sql: Contains INSERT with full content for sol-001~008 (lines 67-74)
- V13__solution_enrich_content.sql: Contains UPDATE with enriched content for sol-001~008 (lines 5-27)
- V23__solutions_seed.sql: Contains INSERT for sol-009~105 (1860 lines, 97 solutions across 40 problems)
- Database currently only has 4 solutions (sol-001~004) with severely truncated content (2-3 lines instead of full markdown)
- V23 data was never applied to the database

### Metis Review
**Identified Gaps** (addressed):
- **Gap**: Should we modify existing migrations or create new patch? → **Resolved**: Create V28 patch migration
- **Gap**: What if sol-009~011 already exist? → **Resolved**: Use INSERT ... ON DUPLICATE KEY UPDATE
- **Gap**: How to verify fix worked? → **Resolved**: API curl commands with content length checks
- **Gap**: Scope of global scan? → **Resolved**: Scan all solutions, document findings, fix only where migration provides full content
- **Gap**: Transaction safety? → **Resolved**: Wrap all writes in single transaction

---

## Work Objectives

### Core Objective
Replace truncated solution content in the database with full markdown content from migration files, and identify other solutions with similar truncation issues.

### Concrete Deliverables
1. **V28 patch migration file** (`db-manager/migrations/V28__fix_two_sum_solutions.sql`)
   - UPDATE statements for sol-001~004 with full content from V13
   - INSERT ... ON DUPLICATE KEY UPDATE for sol-009~011 from V23
2. **Global scan report** documenting all truncated solutions
3. **Verification evidence** showing API returns full content

### Definition of Done
- [ ] All 7 Two Sum solutions have full markdown content with code blocks
- [ ] API returns content with length > 500 chars for each solution
- [ ] Global scan report identifies all solutions with truncated content
- [ ] No existing user data (views, votes, comments) is lost

### Must Have
- Full markdown content with code blocks for sol-001~011
- Transaction-wrapped database writes
- API verification confirming fix

### Must NOT Have (Guardrails)
- **MUST NOT** modify V9, V13, V23 migration files (history must remain intact)
- **MUST NOT** touch `views`, `votes`, `bookmarks`, `comments` columns
- **MUST NOT** apply full V23 (only sol-009~011 for problem_id=1)
- **MUST NOT** delete or recreate solutions (preserve IDs and metadata)

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: YES (MySQL + Flyway migrations)
- **Automated tests**: None (data fix, not code change)
- **Agent-Executed QA**: MANDATORY for all tasks

### QA Policy
Every task MUST include agent-executed QA scenarios.
Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Database**: Use Bash (mysql client) - Query content length, verify code blocks exist
- **API**: Use Bash (curl) - Send requests, assert response fields and content length

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation - can start immediately):
├── Task 1: Create V28 patch migration for sol-001~004 (UPDATE from V13)
└── Task 2: Create V28 patch migration for sol-009~011 (INSERT/UPDATE from V23)

Wave 2 (After Wave 1 - apply and verify):
├── Task 3: Apply V28 migration and verify Two Sum solutions
└── Task 4: Global scan - identify all truncated solutions

Wave 3 (After Wave 2 - documentation):
├── Task 5: Generate scan report and document findings

Wave FINAL (After ALL tasks - 4 parallel reviews):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
-> Present results -> Get explicit user okay

Critical Path: Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → F1-F4 → user okay
```

### Dependency Matrix

- **Task 1**: None → Blocks: Task 3
- **Task 2**: None → Blocks: Task 3
- **Task 3**: Task 1, Task 2 → Blocks: Task 4
- **Task 4**: Task 3 → Blocks: Task 5
- **Task 5**: Task 4 → Blocks: F1-F4

---

## TODOs

- [x] 1. Create V28 patch migration - UPDATE sol-001~004 with V13 content

  **What to do**:
  - Create `db-manager/migrations/V28__fix_two_sum_solutions.sql`
  - Extract full content for sol-001~004 from V13__solution_enrich_content.sql
  - Write UPDATE statements replacing truncated content with full markdown
  - Include proper markdown with code blocks, explanations, complexity analysis

  **Must NOT do**:
  - Do NOT modify V9, V13, or V23 files
  - Do NOT touch views, votes, bookmarks columns
  - Do NOT delete existing solutions

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Straightforward SQL file creation with content copy
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 2)
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 3
  - **Blocked By**: None

  **References**:
  - `db-manager/migrations/V13__solution_enrich_content.sql:5-16` - Full content for sol-001~004
  - `db-manager/migrations/V9__solution_schema.sql:67-70` - Current truncated content in DB

  **Acceptance Criteria**:
  - [ ] V28 migration file created at correct path
  - [ ] Contains UPDATE statements for sol-001, sol-002, sol-003, sol-004
  - [ ] Each UPDATE includes full markdown content with code blocks
  - [ ] File wraps in SET FOREIGN_KEY_CHECKS=0/1

  **QA Scenarios**:

  ```
  Scenario: Verify migration file exists and has correct structure
    Tool: Bash
    Preconditions: None
    Steps:
      1. ls db-manager/migrations/V28__fix_two_sum_solutions.sql
      2. grep -c "UPDATE.*solutions.*SET.*content" db-manager/migrations/V28__fix_two_sum_solutions.sql
    Expected Result: File exists, contains 4 UPDATE statements
    Evidence: .sisyphus/evidence/task-1-migration-file.txt
  ```

  **Commit**: YES
  - Message: `fix(db): add V28 migration to fix truncated Two Sum solution content`
  - Files: `db-manager/migrations/V28__fix_two_sum_solutions.sql`

- [x] 2. Create V28 patch migration - INSERT/UPDATE sol-009~011 from V23

  **What to do**:
  - Add INSERT ... ON DUPLICATE KEY UPDATE statements for sol-009~011
  - Extract content from V23__solutions_seed.sql (lines 7-93)
  - Include all fields: id, problem_id, user_id, title, content, summary, language, tags, views

  **Must NOT do**:
  - Do NOT apply other solutions from V23 (only sol-009~011)
  - Do NOT modify V23 file

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: SQL INSERT statements with content from existing file
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 1)
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 3
  - **Blocked By**: None

  **References**:
  - `db-manager/migrations/V23__solutions_seed.sql:7-93` - INSERT statements for sol-009~011

  **Acceptance Criteria**:
  - [ ] V28 migration contains INSERT statements for sol-009, sol-010, sol-011
  - [ ] Uses ON DUPLICATE KEY UPDATE pattern
  - [ ] Includes full content with code blocks

  **QA Scenarios**:

  ```
  Scenario: Verify INSERT statements exist for sol-009~011
    Tool: Bash
    Preconditions: Task 1 completed (file exists)
    Steps:
      1. grep -c "sol-009\|sol-010\|sol-011" db-manager/migrations/V28__fix_two_sum_solutions.sql
      2. grep -c "ON DUPLICATE KEY UPDATE" db-manager/migrations/V28__fix_two_sum_solutions.sql
    Expected Result: Contains references to all 3 solution IDs and uses ON DUPLICATE KEY UPDATE
    Evidence: .sisyphus/evidence/task-2-insert-statements.txt
  ```

  **Commit**: YES (groups with Task 1)

- [x] 3. Apply V28 migration and verify Two Sum solutions

  **What to do**:
  - Run `cd db-manager && .venv/bin/python -m db_manager.cli migrate`
  - Verify migration applied successfully
  - Query database to confirm content length increased
  - Call API to verify full content is returned

  **Must NOT do**:
  - Do NOT run clean --force (would delete all data)
  - Do NOT skip transaction check

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Database operation with verification steps
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 4
  - **Blocked By**: Task 1, Task 2

  **References**:
  - `db-manager/AGENTS.md` - Migration commands and pitfalls
  - `AGENTS.md` - Database connection and curl examples

  **Acceptance Criteria**:
  - [ ] Migration runs without errors
  - [ ] `db-manager info` shows V28 as applied
  - [ ] Database query shows content length > 500 chars for sol-001~011
  - [ ] API returns full markdown with code blocks

  **QA Scenarios**:

  ```
  Scenario: Verify migration applied and content is full
    Tool: Bash (mysql + curl)
    Preconditions: Task 1, Task 2 completed
    Steps:
      1. cd db-manager && .venv/bin/python -m db_manager.cli migrate
      2. docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT id, LENGTH(content) as len FROM solutions WHERE id IN ('sol-001','sol-002','sol-003','sol-004','sol-009','sol-010','sol-011')"
      3. curl -s http://localhost:9001/api/problems/1/solutions | jq '.data[] | {id, contentLength: (.content | length)}'
    Expected Result: All 7 solutions have content length > 500, API returns full content
    Evidence: .sisyphus/evidence/task-3-migration-applied.txt

  Scenario: Verify API returns code blocks
    Tool: Bash (curl)
    Preconditions: Backend running
    Steps:
      1. curl -s http://localhost:9001/api/solutions/sol-001 | jq '.data.content' | grep -c "```"
    Expected Result: Content contains markdown code blocks (count >= 2)
    Evidence: .sisyphus/evidence/task-3-api-code-blocks.txt
  ```

  **Commit**: NO (data change, not code change)

- [x] 4. Global scan - identify all truncated solutions

  **What to do**:
  - Query all solutions comparing content length against expected
  - Identify solutions with placeholder content ("使用标准算法思路解决。")
  - Compare database content against migration files
  - Document findings in report

  **Must NOT do**:
  - Do NOT fix other solutions (only document for follow-up)
  - Do NOT modify any data

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Investigation and analysis task
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 5
  - **Blocked By**: Task 3

  **References**:
  - `db-manager/migrations/V23__solutions_seed.sql` - All solution content for comparison

  **Acceptance Criteria**:
  - [ ] Query returns all solutions with content length < 200 chars
  - [ ] Report identifies which solutions have placeholder content
  - [ ] Report notes which solutions have full content in migrations but truncated in DB

  **QA Scenarios**:

  ```
  Scenario: Scan for truncated solutions
    Tool: Bash (mysql)
    Preconditions: Task 3 completed
    Steps:
      1. docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT id, problem_id, LENGTH(content) as len, LEFT(content, 50) as preview FROM solutions WHERE LENGTH(content) < 200 ORDER BY problem_id, id"
    Expected Result: List of all truncated solutions with IDs and content previews
    Evidence: .sisyphus/evidence/task-4-truncated-solutions.txt

  Scenario: Count placeholder solutions
    Tool: Bash (mysql)
    Preconditions: Task 3 completed
    Steps:
      1. docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT COUNT(*) as placeholder_count FROM solutions WHERE content LIKE '%使用标准算法思路解决%'"
    Expected Result: Count of solutions with placeholder text
    Evidence: .sisyphus/evidence/task-4-placeholder-count.txt
  ```

  **Commit**: NO (investigation only)

- [x] 5. Generate scan report and document findings

  **What to do**:
  - Create markdown report with findings from Task 4
  - Document which solutions are truncated
  - Note which have full content available in migrations
  - Provide recommendations for follow-up fixes

  **Must NOT do**:
  - Do NOT include sensitive data (user info, passwords)
  - Do NOT modify existing files

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: Documentation and report generation
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocks**: F1-F4
  - **Blocked By**: Task 4

  **Acceptance Criteria**:
  - [ ] Report file created at `.sisyphus/evidence/solution-content-scan-report.md`
  - [ ] Contains list of all truncated solutions
  - [ ] Includes recommendations for next steps

  **QA Scenarios**:

  ```
  Scenario: Verify report exists and is readable
    Tool: Bash
    Preconditions: Task 4 completed
    Steps:
      1. ls .sisyphus/evidence/solution-content-scan-report.md
      2. wc -l .sisyphus/evidence/solution-content-scan-report.md
    Expected Result: Report file exists with content
    Evidence: .sisyphus/evidence/task-5-report-exists.txt
  ```

  **Commit**: NO (evidence file)

---

## Final Verification Wave

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [x] F1. **Plan Compliance Audit** — `oracle` ✅ APPROVE (V9 revert verified)
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, curl endpoint, run command). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .sisyphus/evidence/. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high` ✅ APPROVE
  Review V28 migration file for: SQL syntax errors, proper escaping, transaction safety, FOREIGN_KEY_CHECKS wrapping. Check for AI slop: excessive comments, inconsistent formatting.
  Output: `SQL Syntax [PASS/FAIL] | Transaction Safety [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high` ✅ PASS (6/6)
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test edge cases: duplicate key handling, content encoding, API response format. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep` ✅ PASS
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Task 1+2**: `fix(db): add V28 migration to fix truncated Two Sum solution content` - `db-manager/migrations/V28__fix_two_sum_solutions.sql`

---

## Success Criteria

### Verification Commands
```bash
# Check all 7 solutions have full content
curl -s http://localhost:9001/api/problems/1/solutions | jq '.data[] | {id, title, contentLength: (.content | length), hasCode: (.content | contains("```"))}'

# Expected output: all 7 solutions with contentLength > 500 and hasCode = true

# Check no other solutions were modified
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT id, LENGTH(content) as len FROM solutions WHERE id NOT LIKE 'sol-00%' AND id LIKE 'sol-%' ORDER BY id LIMIT 5"

# Expected: Other solutions unchanged
```

### Final Checklist
- [ ] All 7 Two Sum solutions have full markdown content with code blocks
- [ ] V28 migration applied successfully (shown in `db-manager info`)
- [ ] No existing user data (views, votes, comments) was lost
- [ ] Global scan report generated
- [ ] All QA scenarios passed with evidence
- [ ] No modifications to V9, V13, V23 migration files
