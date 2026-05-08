# Fix: 500 Error on /admin/problem-lists Endpoint

## TL;DR

> **Problem**: `GET /admin/problem-lists` returns 500 Internal Server Error
> **Root Cause**: Missing `version` column in `problem_lists` table - the `ProblemList` entity has `@Version` annotation but the database migration (V28) hasn't been applied
> **Fix**: Apply the pending V28 database migration to add the `version` column
> 
> **Estimated Effort**: Quick (5 minutes)
> **Parallel Execution**: NO - single sequential task
> **Critical Path**: Apply migration → Verify fix

---

## Context

### Original Request
User reported a 500 Internal Server Error when accessing the admin problem lists page:
```
GET http://localhost:9001/admin/problem-lists?page=1&limit=10 500 (Internal Server Error)
```

### Investigation Findings

**Backend Error (from PM2 logs):**
```
java.sql.SQLSyntaxErrorException: Unknown column 'version' in 'field list'
SQL: SELECT id,name,description,author_id,is_public,is_featured,banner_tag,banner_icon,banner_theme,banner_order,version,created_at,updated_at FROM problem_lists ORDER BY created_at DESC LIMIT ?
```

**Root Cause Analysis:**
1. The `ProblemList` entity (`backend-spring/src/main/java/com/ulticode/modules/problemlist/entity/ProblemList.java:77-78`) has a `@Version` annotation for optimistic locking
2. MyBatis-Plus automatically includes the `version` column in all SELECT queries
3. The V28 migration (`db-manager/migrations/V28__problem_lists_add_version.sql`) adds this column but hasn't been applied
4. When the admin endpoint queries `problem_lists`, MyBatis-Plus generates SQL including `version` column, causing the SQL syntax error

**Error Flow:**
```
AdminProblemListController.getProblemLists() → line 36
  → AdminProblemListServiceImpl.getProblemLists() → line 42
    → problemListMapper.selectPage() → MyBatis-Plus generates SQL with version column
      → Database: Unknown column 'version' → 500 Error
```

---

## Work Objectives

### Core Objective
Apply the pending V28 database migration to add the `version` column to the `problem_lists` table, resolving the 500 error.

### Concrete Deliverables
- `version` column added to `problem_lists` table
- `GET /admin/problem-lists` endpoint returns 200 with paginated data

### Definition of Done
- [ ] Database migration V28 applied successfully
- [ ] API endpoint returns 200 with valid JSON response
- [ ] Admin problem lists page loads without errors

### Must Have
- Apply the pending migration safely
- Verify the fix works end-to-end

### Must NOT Have (Guardrails)
- Do NOT modify entity code (the entity is correct, the database is behind)
- Do NOT drop and recreate the table (data loss risk)
- Do NOT skip migration and manually patch the entity

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES (backend has test infrastructure)
- **Automated tests**: NO - this is a database migration fix, manual verification is appropriate
- **Agent-Executed QA**: YES - mandatory for verification

### QA Policy
Every task MUST include agent-executed QA scenarios. Evidence saved to `.sisyphus/evidence/`.

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Single Task - Apply Migration):
└── Task 1: Apply V28 migration and verify fix [quick]
```

### Dependency Matrix
- **Task 1**: No dependencies, can start immediately

### Agent Dispatch Summary
- **Task 1**: `quick` agent - simple migration application and verification

---

## TODOs

- [x] 1. Apply V28 Database Migration and Verify Fix

  **What to do**:
  - Run the pending V28 migration using db-manager CLI
  - Verify the migration applied successfully
  - Test the API endpoint returns 200
  - Verify the admin problem lists page loads correctly

  **Must NOT do**:
  - Do NOT modify the ProblemList entity code
  - Do NOT manually alter the table structure outside of migrations
  - Do NOT skip the migration process

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple database migration application with verification
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential only
  - **Blocks**: None
  - **Blocked By**: None (can start immediately)

  **References** (CRITICAL - Be Exhaustive):

  **Pattern References** (existing code to follow):
  - `db-manager/migrations/V28__problem_lists_add_version.sql` - The pending migration file
  - `backend-spring/src/main/java/com/ulticode/modules/problemlist/entity/ProblemList.java:77-78` - Entity with @Version annotation

  **API/Type References** (contracts to implement against):
  - `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminProblemListController.java:34-38` - The failing endpoint

  **External References** (libraries and frameworks):
  - Flyway migration documentation for db-manager CLI usage

  **WHY Each Reference Matters**:
  - `V28__problem_lists_add_version.sql`: This migration adds the missing `version` column
  - `ProblemList.java:77-78`: Shows the `@Version` annotation that causes MyBatis-Plus to include `version` in queries
  - `AdminProblemListController.java:34-38`: The endpoint that fails due to the missing column

  **Acceptance Criteria**:
  - [x] Migration applied: `db-manager info` shows V28 as applied
  - [x] Column exists: `DESCRIBE problem_lists` shows `version` column
  - [x] API returns 200: `curl /admin/problem-lists?page=1&limit=10` returns success response
  - [x] Data is correct: Response contains problem lists with proper fields

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Verify migration applied successfully
    Tool: Bash (docker exec mysql)
    Preconditions: Database is running, backend is running
    Steps:
      1. Run: docker exec ulticode-mysql mysql -u root -p"CHANGE_ME_root_password" -e "USE ulticode; DESCRIBE problem_lists;"
      2. Verify output contains "version" column with type INT
    Expected Result: Column "version" exists in problem_lists table
    Failure Indicators: Column missing, access denied, or table doesn't exist
    Evidence: .sisyphus/evidence/task-1-migration-applied.txt

  Scenario: Verify API endpoint returns 200
    Tool: Bash (curl)
    Preconditions: Backend running on port 9001, user authenticated
    Steps:
      1. Login: curl -X POST http://localhost:9001/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' -c /tmp/cookies.txt
      2. Call API: curl -s http://localhost:9001/admin/problem-lists?page=1&limit=10 -b /tmp/cookies.txt
      3. Verify response code is 0 (success) and data contains items array
    Expected Result: {"code":0,"message":"success","data":{"items":[...],"total":N,"page":1,"pageSize":10}}
    Failure Indicators: code != 0, 500 error, or empty data
    Evidence: .sisyphus/evidence/task-1-api-response.json

  Scenario: Verify admin page loads without errors
    Tool: Playwright (browser automation)
    Preconditions: Management frontend running on port 9003, backend running
    Steps:
      1. Navigate to http://localhost:9003/admin/problem-lists
      2. Login with admin/admin123 if prompted
      3. Wait for data table to load
      4. Verify no error banners or 500 error messages visible
    Expected Result: Page shows problem lists table with data, no errors
    Failure Indicators: Error banner visible, empty table with error message, or 500 error toast
    Evidence: .sisyphus/evidence/task-1-page-load.png
  ```

  **Evidence to Capture**:
  - [ ] Database schema after migration: task-1-migration-applied.txt
  - [ ] API response: task-1-api-response.json
  - [ ] Screenshot of admin page: task-1-page-load.png

  **Commit**: YES
  - Message: `fix(db): apply V28 migration to add version column to problem_lists`
  - Files: `db-manager/migrations/` (if any checksum changes)
  - Pre-commit: N/A - database migration only

---

## Final Verification Wave (MANDATORY)

- [x] F1. **Plan Compliance Audit** — `oracle`
  Verify that:
  - The `version` column exists in `problem_lists` table
  - The API endpoint returns 200 with valid data
  - No entity code was modified (only migration applied)
  Output: `Must Have [3/3] | Must NOT Have [3/3] | VERDICT: APPROVE`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Check that the migration file is valid SQL and follows project conventions.
  Output: `Migration Valid [YES/NO] | VERDICT: APPROVE`

---

## Commit Strategy

- **1**: `fix(db): apply V28 migration to add version column to problem_lists`

---

## Success Criteria

### Verification Commands
```bash
# Check migration status
cd db-manager && .venv/bin/python -m db_manager.cli info

# Verify column exists
docker exec ulticode-mysql mysql -u root -p"CHANGE_ME_root_password" -e "USE ulticode; DESCRIBE problem_lists;" | grep version

# Test API endpoint
curl -s http://localhost:9001/admin/problem-lists?page=1&limit=10 -b /tmp/cookies.txt | jq '.code'
# Expected: 0
```

### Final Checklist
- [x] V28 migration applied successfully
- [x] `version` column exists in `problem_lists` table
- [x] `GET /admin/problem-lists` returns 200 with data
- [x] Admin problem lists page loads without errors
- [x] No entity code was modified
