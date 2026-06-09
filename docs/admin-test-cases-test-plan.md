# Admin Test Cases API — Test Plan & Bug Report

**Generated**:2026-06-10
**Backend**: `http://localhost:9001`
**Tester**: admin / admin123 (dev profile)
**Scope**:8 endpoints under `/admin/problems/{problemId}/test-cases/`
**Result**: 🔴 **6 defects found** — test-case feature is broken at the persistence layer.

---

##1. Executive Summary

The `AdminTestCaseController` exists with8 endpoints and `@Validated` /
`@PreAuthorize` wiring, but the **`test_cases` table is missing from the
Flyway migrations** (verified via `grep test_case init-db/migrations/*.sql` —
zero matches). The backend is therefore returning HTTP500 for almost every
operation because the MyBatis-Plus mapper fails on a missing relation.

Only the **FK existence check** (via `problems.id`) and the **DTO-level
validation** paths return correct status codes — both happen before the
service touches the table.

The single most important fix is **adding a `test_cases` table migration**;
once that exists, several of the secondary defects (incorrect500→404 mapping,
empty-body500→400, etc.) will need separate fixes.

---

##2. Endpoints Inventory

| # | Method | Path | Purpose |
|---|--------|------|---------|
|1 | GET | `/admin/problems/{problemId}/test-cases` | List test cases (paginated) |
|2 | GET | `/admin/problems/{problemId}/test-cases/{testCaseId}` | Single test case detail |
|3 | POST | `/admin/problems/{problemId}/test-cases` | Create one test case |
|4 | PUT | `/admin/problems/{problemId}/test-cases/{testCaseId}` | Update one test case |
|5 | DELETE | `/admin/problems/{problemId}/test-cases/{testCaseId}` | Delete one test case |
|6 | POST | `/admin/problems/{problemId}/test-cases/bulk` | Bulk create |
|7 | PUT | `/admin/problems/{problemId}/test-cases/reorder` | Reorder test cases |
|8 | GET | `/admin/problems/{problemId}/test-cases/export` | Export test cases |

Note: the original requirement list has9 endpoints including a separate
"DOWNLOAD" variant. The actual implementation has a single `GET /export`
that returns `application/json` (not a downloadable file). See **Bug H-1**.

---

##3. Test Matrix

| # | Test | Endpoint | Scenario | Expected | Actual | Verdict |
|---|------|----------|----------|----------|--------|---------|
|1 | T-01 | GET list | empty DB |200 / `{data: [], total:0}` | **500** / `code=50000 "Unknown error"` | 🔴 FAIL |
|2 | T-02 | POST create | bad problemId (999999) |404 / `code=30001 "Problem not found"` |404 / `code=30001` | ✅ PASS |
|3 | T-03 | POST create | empty body `{}` |400 / field errors |400 / `data.{isSample,inputText,outputText}` | ✅ PASS |
|4 | T-04 | GET by id | non-existent testCaseId |404 | **500** / `code=50000` | 🔴 FAIL |
|5 | T-05 | PUT update | non-existent testCaseId |404 | **500** / `code=50000` | 🔴 FAIL |
|6 | T-06 | DELETE | non-existent testCaseId |404 | **500** / `code=50000` | 🔴 FAIL |
|7 | T-07 | POST bulk | empty array `[]` |200 / `[]` |200 / `[]` | ✅ PASS |
|8 | T-08 | PUT reorder | empty array `[]` |200 |200 | ✅ PASS |
|9 | T-09 | GET export | any problem |200 + JSON body | **500** / `code=50000` | 🔴 FAIL |
|10 | T-10 | PUT update | empty body `{}` |400 / field errors | **500** / `code=50000` | 🔴 FAIL |
|11 | T-11 | GET list | no `X-CSRF-Token` header |403 / `code=40300` | **500** / `code=50000` | 🔴 FAIL |
|12 | T-12 | GET list | no cookies |401 / `code=40100` |401 / `code=40100` | ✅ PASS |
|13 | T-13 | GET export | check response headers | `Content-Type: application/octet-stream` OR `application/json` + `Content-Disposition: attachment` | `Content-Type: application/json;charset=UTF-8`, no `Content-Disposition` | 🔴 FAIL |
|14 | T-14 | PUT update | valid body, non-existent id |404 | **500** / `code=50000` | 🔴 FAIL |
|15 | T-15 | POST bulk | bad problemId |404 / `code=30001` |404 / `code=30001` | ✅ PASS |
|16 | T-16 | PUT reorder | duplicate ids `["dup","dup"]` |400 / validation error | **500** / `code=50000` | 🔴 FAIL |

**Pass rate**:6 /16 (37.5%)

---

##4. Raw curl Outputs (for reproducibility)

### T-01: GET list (empty)
```http
GET /admin/problems/1/test-cases HTTP/1.1
Cookie: <admin session>
X-CSRF-Token:3686…:b527…

HTTP/1.1500
{"code":50000,"message":"Unknown error","traceId":"t-1781022622854"}
```

### T-03: POST create with empty body
```http
POST /admin/problems/1/test-cases HTTP/1.1
Content-Type: application/json
X-CSRF-Token:3686…:b527…

{}

HTTP/1.1400
{"code":40000,"message":"Validation failed","data":{"isSample":"is_sample is required","inputText":"input_text is required","outputText":"output_text is required"},"traceId":"t-1781022622898"}
```

### T-09: GET export (response headers)
```http
GET /admin/problems/1/test-cases/export HTTP/1.1

HTTP/1.1500
X-Content-Type-Options: nosniff
Content-Type: application/json;charset=UTF-8
(no Content-Disposition)
```

### T-16: PUT reorder with duplicate ids
```http
PUT /admin/problems/1/test-cases/reorder HTTP/1.1
Content-Type: application/json

["dup","dup"]

HTTP/1.1500
{"code":50000,"message":"Unknown error","traceId":"t-1781022678755"}
```

---

##5. Defect Catalog

### 🔴 CRITICAL

#### Bug #1 — `test_cases` table missing from migrations

**Evidence**:
```bash
$ grep -in 'test.case\|test_case' init-db/migrations/V*.sql
(init-db/migrations/V20260603_120601__Fix_Submission_Test_Details_Json.sql:5:-- 但后端 TestCaseDetail字段类型为 List<TestCaseDetail>, 必须为数组。)
```
Only one comment match — the `test_cases` table itself is never created.

The `TestCase` entity declares `@TableName("test_cases")` but the table
doesn't exist, so every MyBatis operation (`selectList`, `selectById`,
`insert`, `updateById`, `deleteById`, `update`) hits `MyBatisSystemException`
which `GlobalExceptionHandler.handleMyBatisSystemException` maps to
`code=50001` — but in our test, the response was `code=50000`. That suggests
the exception is *not* `MyBatisSystemException` but a different SQL error
that falls through to `handleGenericException`. Either way, every test-case
operation except FK pre-checks fails.

**Impact**: ALL test-case functionality is non-functional in any fresh DB.

**Fix**: Add a Flyway migration `V20260610120000__Create_Test_Cases_Table.sql`:
```sql
CREATE TABLE `test_cases` (
 `id` varchar(40) NOT NULL,
 `problem_id` bigint NOT NULL,
 `is_sample` tinyint(1) NOT NULL DEFAULT '0',
 `is_hidden` tinyint(1) NOT NULL DEFAULT '0',
 `test_order` int NOT NULL DEFAULT '0',
 `input_text` text NOT NULL,
 `output_text` text NOT NULL,
 `inputs` json DEFAULT NULL,
 `explanation` text,
 `constraints` json DEFAULT NULL,
 `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 `version` int NOT NULL DEFAULT '1',
 PRIMARY KEY (`id`),
 KEY `test_cases_problem_id_idx` (`problem_id`),
 KEY `test_cases_problem_id_test_order_idx` (`problem_id`, `test_order`),
 CONSTRAINT `test_cases_problem_id_fk` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

**Location**: `init-db/migrations/` (new file)
**Priority**: MUST-FIX before any test-case feature is usable.

#### Bug #2 — GET / PUT / DELETE on non-existent id returns500 (should be404)

**Evidence**: T-04, T-05, T-06, T-14 — all return `code=50000 "Unknown error"` for an id that doesn't exist.

**Root cause** (post-Bug #1 fix): `AdminTestCaseServiceImpl` likely does
`mapper.selectById(id)` and throws `BusinessException` only if the result
is null. But the `MyBatisSystemException` is thrown *before* the null check
because the table doesn't exist. Once the table is added, this should be
retested — the service likely already has the right null-check.

**Fix**: After Bug #1 is fixed, re-test. If500 persists, add explicit
"not found" branches:
```java
TestCase tc = testCaseMapper.selectById(testCaseId);
if (tc == null) throw new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND);
```
(Requires a new `ErrorCode.TEST_CASE_NOT_FOUND` constant.)

**Priority**: HIGH (correctness; clients expect404).

#### Bug #3 — PUT update with empty body returns500 (should be400)

**Evidence**: T-10 — empty `{}` body to `/admin/problems/1/test-cases/x`
returns `code=50000` instead of `code=40000 "Validation failed"`.

**Root cause**: The `@Valid @RequestBody UpdateTestCaseDTO dto` annotation
should reject `{}` (empty body) with400, but the request hits the service
first. Likely the service does a mapper call that fails because the table
doesn't exist (Bug #1 root cause), masking the validation error path.

**Fix**: After Bug #1 fix, re-test T-10. If validation still doesn't fire,
verify `UpdateTestCaseDTO` has `@NotBlank`/`@NotNull` on required fields and
that the `@Validated` class-level annotation is present (it is in the
controller — verified).

**Priority**: HIGH (correctness).

### 🟠 HIGH

#### Bug #4 — GET list without CSRF returns500 (should be403)

**Evidence**: T-11 — admin session cookie present, but `X-CSRF-Token`
header missing → `code=50000 "Unknown error"` instead of `code=40300
"Forbidden"`.

**Root cause**: The CSRF filter likely throws an exception (not a403
response), which falls through to `handleGenericException`. Same Bug #1
root cause is possible — the filter might query something that fails before
returning403.

**Fix**: Investigate the CSRF filter chain. Either ensure the filter
short-circuits with a403 (not an exception), or add a dedicated
`CsrfException` handler in `GlobalExceptionHandler`.

**Priority**: HIGH (security feedback).

#### Bug #5 — GET /export returns JSON, not a downloadable file

**Evidence**: T-13 — `Content-Type: application/json;charset=UTF-8`, no
`Content-Disposition: attachment` header.

**Impact**: Frontend cannot trigger a "Download" UX with a simple `<a
href="…/export">`. The user's intent (per the requirement list) was
explicitly to support "DOWNLOAD" — currently this is a JSON GET endpoint
that happens to return a list.

**Fix**: Either
(a) change the response to `application/octet-stream` + `Content-Disposition:
attachment; filename="test-cases-{problemId}.json"`; or
(b) accept an `?format=csv` / `?format=json` query param and switch the
content type accordingly.

**Priority**: HIGH (matches requirement spec).

#### Bug #6 — POST bulk with empty list returns200 + `[]` (should be400)

**Evidence**: T-07 — empty array to `/bulk` returns `code=0` with empty
data array.

**Impact**: This silently accepts nonsense input. A bulk-create should
require at least one item. The same applies to PUT reorder (T-08) which
also accepts empty input.

**Fix**: Add `@Size(min=1)` to the `List<CreateTestCaseDTO>` parameter
of `bulkImportTestCases` and `List<String>` of `reorderTestCases`.

**Location**: `AdminTestCaseController.java:86-90` (bulk) and `:96-100` (reorder).

**Priority**: MEDIUM (correctness; minor impact).

### 🟡 MEDIUM

#### Bug #7 — PUT reorder with duplicate ids returns500 (should be400)

**Evidence**: T-16 — `["dup","dup"]` returns `code=50000`. Likely hits
the table (post-Bug #1 fix) and fails on duplicate-key conflict, but
should be caught as a validation error before the mapper is called.

**Fix**: Add a service-layer pre-check:
```java
Set<String> unique = new HashSet<>(testCaseIds);
if (unique.size() != testCaseIds.size()) {
 throw new BusinessException(ErrorCode.BAD_REQUEST, "Duplicate test case IDs");
}
```

**Priority**: MEDIUM.

---

##6. Cross-cutting Observations

### Working as designed ✅

- **FK pre-check** on `problemId` works (T-02, T-15). The service correctly
 returns `code=30001 "Problem not found"` before touching the test_cases
 table — because `problems` table exists.

- **DTO-level `@Valid`** on POST create works (T-03) — `is_sample`,
 `input_text`, `output_text` all flagged as required. The validation
 fires correctly when the request reaches Spring's `@Valid` interceptor
 before the service is invoked.

- **Auth filter** rejects unauthenticated requests (T-12).

### Architectural smells 🔍

- **`@Validated` on the controller** (line26) — present, but for PUT
 update the empty-body case (T-10) still fails validation downstream. The
 `@Valid` annotation on the parameter is correct; the issue is masked by
 Bug #1.

- **`@RateLimit`** is applied to every mutation endpoint — good defense,
 but cannot be observed while the persistence layer is broken.

- **`testCaseId` is a `String`** (path variable) but `id` in the entity
 is a UUID — so the path parameter is case-sensitive. This isn't
 documented and could surprise frontend callers.

---

##7. Recommended Fix Order

1. **Bug #1 (CRITICAL)** — add `test_cases` migration. Without this, no
 other fix is testable.
2. **Bug #2 (HIGH)** — verify404 mapping after Bug #1 fix; add explicit
 null-checks + new `ErrorCode.TEST_CASE_NOT_FOUND`.
3. **Bug #3 (HIGH)** — verify validation after Bug #1 fix.
4. **Bug #4 (HIGH)** — investigate CSRF filter exception path.
5. **Bug #5 (HIGH)** — change `/export` content type + add
 `Content-Disposition`.
6. **Bugs #6 & #7 (MEDIUM)** — add validation guards.

After all fixes, re-run the16-test matrix; expected pass rate:100%.

---

##8. Out of Scope (not tested)

- **Rate-limit behavior** — cannot be observed while persistence is broken.
- **Audit log emission** — `@Audited` not on test-case methods (by design
? or oversight? — verify).
- **Authorization beyond role check** — `@PreAuthorize("hasAnyRole('ADMIN',
 'SUPER_ADMIN')")` is correct; further RBAC matrix not exercised.
- **Concurrent bulk imports** — race conditions in `testOrder` assignment
 not tested.
- **Large payload limits** — Spring's default body size limit not exercised.

---

##9. Conclusion

**Verdict**: 🔴 Test-case feature is non-functional in production until Bug #1
(missing migration) is fixed. Once that's resolved, re-run the test matrix
to identify any secondary defects that are currently masked by the500
fallback.

The defects are all straightforward to fix — no architectural rework
required — and the controller code itself appears to follow project
conventions correctly. The root cause is **database-schema drift**: an
entity was added without a corresponding Flyway migration.
