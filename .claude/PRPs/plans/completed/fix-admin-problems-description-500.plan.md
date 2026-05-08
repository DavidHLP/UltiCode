# Plan: Fix Admin Problems Description 500 Error

## Summary
The `GET /admin/problems/{id}/description` endpoint returns HTTP 500 due to a missing `content` column in the `problem_details` database table. The `content` field was recently added to the `ProblemDetail` Java entity and is being read in `AdminProblemServiceImpl`, but no Flyway migration exists to add the corresponding database column. When MyBatis-Plus executes `SELECT` queries on `problem_details`, it includes the `content` field in the generated SQL, causing MySQL to throw "Unknown column 'content' in 'field list'".

## User Story
As an admin user,
I want to view and edit the full markdown content of a problem description,
So that I can manage comprehensive problem descriptions beyond just summaries.

## Problem → Solution
**Current state**: Admin problem description tab crashes with 500 because the backend queries a non-existent `content` column. **Desired state**: The endpoint returns description data successfully, including the `content` field when populated.

## Metadata
- **Complexity**: Small
- **Source PRD**: N/A (bug fix)
- **PRD Phase**: N/A
- **Estimated Files**: 4-6 files

---

## UX Design

### Before
```
Admin clicks "View Description" on a problem
  → UI navigates to problem-detail/description tab
  → API call GET /admin/problems/1/description
  → Backend returns 500
  → UI shows error / loading fails indefinitely
```

### After
```
Admin clicks "View Description" on a problem
  → UI navigates to problem-detail/description tab
  → API call GET /admin/problems/1/description
  → Backend returns 200 with description data (including content)
  → UI renders description form with all fields
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Description tab load | Crashes with 500 | Loads successfully | Migration fixes DB column |
| Description edit save | May fail or not persist content | Persists content to DB | Already implemented in ProblemServiceImpl |
| Public problem page | No content field in response | Content available via detail.content | Need to update public API VO |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemDetail.java` | 1-67 | Entity with the new `content` field |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemServiceImpl.java` | 47-80 | Service that reads `content` and triggers the 500 |
| P0 (critical) | `db-manager/migrations/V2__problem_schema.sql` | problem_details CREATE TABLE | Confirms `content` column is missing from original schema |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` | 420-458 | `updateProblemDetail` already writes `content` — proves save path expects it |
| P1 (important) | `management/src/views/problems/edit/EditDescriptionView.vue` | 24-71 | Frontend already sends/receives `content` |
| P2 (reference) | `backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemDetailResponse.java` | 113-127 | Public API DetailData — needs `content` field added |

---

## External Documentation

No external research needed — feature uses established internal patterns (Flyway migrations, MyBatis-Plus, Lombok DTOs).

---

## Patterns to Mirror

### DATABASE_MIGRATION
// SOURCE: db-manager/migrations/V29__fix_problem_details_encoding.sql
```sql
-- Pattern: single-column update migration for problem_details
UPDATE `problem_details` SET `constraints_json` = ... WHERE `problem_id` = 1;
```

### COLUMN_ADDITION_MIGRATION
// SOURCE: db-manager/migrations/V103__add_problem_version_table.sql (reference for ALTER TABLE)
```sql
-- Use ALTER TABLE to add a column
ALTER TABLE `table_name` ADD COLUMN `new_column` datatype constraints;
```

### DTO_FIELD_ADDITION
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/admin/dto/problem/DescriptionDataVO.java:57-62
```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public static class DetailInfo {
    private String summary;
    private String content;  // ← already exists in admin VO
    private List<String> constraintsJson;
    private List<String> hints;
}
```

### SERVICE_DATA_MAPPING
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:253-258
```java
DetailData data = new DetailData();
data.setSummary(detail.getSummary());
data.setConstraintsJson(parseJsonArray(detail.getConstraintsJson()));
data.setHints(parseJsonArray(detail.getHints()));
data.setFollowUp(detail.getFollowUp());
// Pattern: map entity field to DTO field
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `db-manager/migrations/V31__add_problem_details_content.sql` | CREATE | Add missing `content` column to `problem_details` table |
| `backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemDetailResponse.java` | UPDATE | Add `content` field to `DetailData` inner class so public API also exposes it |
| `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` | UPDATE | Map `detail.getContent()` to `data.setContent()` in `buildDetailData()` |

## NOT Building

- Do NOT modify `ProblemDetail.java` — `content` field already exists (uncommitted change)
- Do NOT modify `UpdateProblemDTO.java` — `content` field already exists (uncommitted change)
- Do NOT modify `AdminProblemServiceImpl.java` — `content` read already exists (uncommitted change)
- Do NOT modify `DescriptionDataVO.java` — `content` field already exists
- Do NOT modify frontend files — they already handle `content`
- Do NOT backfill existing rows with default content values (keep as NULL)

---

## Step-by-Step Tasks

### Task 1: Create Flyway Migration for content Column
- **ACTION**: Create `db-manager/migrations/V31__add_problem_details_content.sql`
- **IMPLEMENT**: Write an `ALTER TABLE` statement to add `content` as a nullable TEXT column after `summary`
- **MIRROR**: Follow the existing schema style from `V2__problem_schema.sql` — use utf8mb4_unicode_ci collation, place logically near `summary`
- **IMPORTS**: N/A (raw SQL)
- **GOTCHA**:
  - Must use `ALTER TABLE ... ADD COLUMN` not `MODIFY`
  - Column must be nullable (no `NOT NULL`) because existing rows have no content
  - Use `text COLLATE utf8mb4_unicode_ci` to match other text columns in the table
  - Next available version is V31 (latest tracked is V30__problem_lists_add_version.sql)
- **VALIDATE**: Check migration file exists and SQL syntax is valid

**Expected SQL:**
```sql
ALTER TABLE `problem_details`
  ADD COLUMN `content` text COLLATE utf8mb4_unicode_ci
  AFTER `summary`;
```

### Task 2: Add content to Public API Response
- **ACTION**: Update `ProblemDetailResponse.DetailData` to include `content` field
- **IMPLEMENT**: Add `private String content;` to the `DetailData` inner class
- **MIRROR**: Follow the pattern of `summary` field — simple String with no special annotations
- **IMPORTS**: None needed
- **GOTCHA**: Do not add `@JsonProperty` unless the API contract needs a different JSON key name (use plain `content` to match admin API)
- **VALIDATE**: File compiles, field is accessible

### Task 3: Map content in Public Service Layer
- **ACTION**: Update `ProblemServiceImpl.buildDetailData()` to map `content`
- **IMPLEMENT**: After `data.setSummary(detail.getSummary())`, add `data.setContent(detail.getContent())`
- **MIRROR**: Follow the exact pattern of other field mappings in the same method
- **IMPORTS**: None needed
- **GOTCHA**: `detail.getContent()` may return null for rows without content — this is fine since `DetailData` uses `@JsonInclude(JsonInclude.Include.NON_NULL)`
- **VALIDATE**: Verify method compiles and logic is consistent with other field mappings

### Task 4: Apply Migration and Verify End-to-End
- **ACTION**: Run Flyway migration against local database and test the API
- **IMPLEMENT**:
  1. Ensure docker-compose DB is running
  2. Run `db-manager` migration script (or let Flyway auto-migrate on Spring Boot startup)
  3. Test `GET http://localhost:9001/admin/problems/1/description` returns 200
  4. Test editing and saving description content
- **MIRROR**: Existing local dev workflow
- **GOTCHA**: If using `db-manager/src/db_manager/flyway_adapter.py`, check how it resolves migration versions
- **VALIDATE**:
  - API returns 200 instead of 500
  - Response includes `detail.content` (may be null for old data)
  - Saving description with content persists correctly

---

## Testing Strategy

### Manual Validation
| Step | Action | Expected |
|---|---|---|
| 1 | Start backend and ensure Flyway migrates | No migration errors in logs |
| 2 | `GET /admin/problems/1/description` | HTTP 200, JSON with `detail.content` field |
| 3 | Open management UI, navigate to problem 1 description tab | Page loads without 500 error |
| 4 | Edit description, fill content field, save | Save succeeds, toast shows success |
| 5 | Reload description tab | Content field shows saved value |
| 6 | `GET /problems/1` (public API) | Response includes `detail.content` if populated |

### Edge Cases Checklist
- [ ] Problem with no detail row (detail is null) — should still return 200
- [ ] Problem with detail row but content is NULL — should return 200, content field omitted from JSON due to `@JsonInclude(NON_NULL)`
- [ ] Problem with content populated — should return 200 with content in response

---

## Validation Commands

### Database Migration
```bash
cd /home/david/project/UltiCode-Public-Next
# Check migration file syntax
docker compose exec db mysql -u root -p ulticode -e "SOURCE /migrations/V31__add_problem_details_content.sql"
```
EXPECT: `Query OK` or column already exists

### Backend Build
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw compile -q
```
EXPECT: Build succeeds with no errors

### API Test
```bash
curl -s http://localhost:9001/admin/problems/1/description \
  -H "Authorization: Bearer <token>" | jq .
```
EXPECT: HTTP 200, JSON contains `data.detail.content`

### Full Test Suite
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw test -q
```
EXPECT: All tests pass (no regressions)

---

## Acceptance Criteria
- [ ] Migration file `V31__add_problem_details_content.sql` created and valid
- [ ] `content` column exists in `problem_details` table after migration runs
- [ ] `GET /admin/problems/{id}/description` returns 200 instead of 500
- [ ] Public API `ProblemDetailResponse.DetailData` includes `content` field
- [ ] `ProblemServiceImpl.buildDetailData()` maps `content` from entity to DTO
- [ ] Admin UI can load, edit, and save problem description content
- [ ] Backend compiles without errors
- [ ] No regressions in existing tests

## Completion Checklist
- [ ] Code follows discovered patterns
- [ ] Error handling matches codebase style
- [ ] No hardcoded values
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Migration version conflict (V31 already exists elsewhere) | Low | Medium | Check git status and migration directory before creating file |
| MyBatis-Plus still fails after column added due to caching | Low | Low | Restart Spring Boot application to clear entity metadata cache |
| Public API consumers break if they strictly validate response schema | Low | Low | Adding a nullable field is backward compatible |

## Notes
- The root cause is 100% the missing DB column. The Java code and frontend are already correctly implemented (as uncommitted changes). This fix is purely adding the missing migration and completing the public API exposure.
- The `content` field represents the full markdown description of a problem, while `summary` is the short overview. This separation allows the admin to manage both independently.
- `ProblemServiceImpl.updateProblemDetail()` already handles saving `content` (lines 442-444), so the write path works once the column exists.
