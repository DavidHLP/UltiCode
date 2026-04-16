---
phase: 08-testing
reviewed: 2026-04-17T01:09:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - console/src/api/__tests__/auth.spec.ts
  - console/src/api/__tests__/problem-detail.spec.ts
  - console/src/stores/__tests__/auth.spec.ts
  - management/vitest.config.ts
  - management/src/api/admin/__tests__/problems.spec.ts
  - management/src/stores/admin/__tests__/problems.spec.ts
  - backend-spring/src/main/java/com/ulticode/UlticodeBackendApplication.java
  - backend-spring/src/main/java/com/ulticode/common/config/MapperConfig.java
  - backend-spring/src/test/java/com/ulticode/modules/submission/service/CodeExecutionServiceTest.java
  - backend-spring/src/test/java/com/ulticode/modules/auth/controller/AuthControllerTest.java
  - backend-spring/src/test/java/com/ulticode/modules/problem/controller/ProblemControllerTest.java
findings:
  critical: 1
  warning: 3
  info: 4
  total: 8
status: issues_found
---

# Phase 08: Code Review Report

**Reviewed:** 2026-04-17T01:09:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed 11 files: 3 backend source changes (MapperConfig extraction, application class cleanup), 2 new backend controller tests, 4 new frontend test files (console auth API, console problem-detail API, console auth store, management problems API, management problems store), and 1 new vitest config.

The most significant finding is a **duplicate `@MapperScan` annotation** in `MapperConfig` and `MybatisPlusConfig` -- both declare the same scan path, which causes mapper beans to be registered twice. This is not a runtime crash but is wasteful and could cause subtle issues with bean proxying.

Test quality is generally good: proper isolation via `vi.mock`, Pinia store tests create fresh stores per test, backend tests correctly use `@WebMvcTest` with `addFilters=false` and `excludeFilters` for MapperConfig. However, several areas need attention.

## Critical Issues

### CR-01: Duplicate `@MapperScan` in MapperConfig and MybatisPlusConfig

**File:** `backend-spring/src/main/java/com/ulticode/common/config/MapperConfig.java:14` and `backend-spring/src/main/java/com/ulticode/common/config/MybatisPlusConfig.java:16`

**Issue:** Both `MapperConfig` (newly extracted in this phase) and `MybatisPlusConfig` (pre-existing) declare `@MapperScan("com.ulticode.modules.*.mapper")`. This causes MyBatis mapper interfaces to be scanned and registered as beans twice. While Spring typically deduplicates beans with the same name, the double scan is wasteful and can cause unexpected behavior with proxy creation, AOP, or conditional bean loading.

```java
// MapperConfig.java (NEW)
@MapperScan("com.ulticode.modules.*.mapper")
public class MapperConfig {
}

// MybatisPlusConfig.java (PRE-EXISTING)
@MapperScan("com.ulticode.modules.*.mapper")
public class MybatisPlusConfig {
    // ... interceptor beans
}
```

**Fix:** Remove `@MapperScan` from one of the two classes. Since `MybatisPlusConfig` already had the annotation before this phase, the cleaner approach is to remove it from the new `MapperConfig`:

```java
// MapperConfig.java -- remove @MapperScan, keep only as marker for excludeFilters
@Configuration
// Do NOT add @MapperScan here -- it's already in MybatisPlusConfig
public class MapperConfig {
}
```

However, if the intent is for `MapperConfig` to be the sole owner of `@MapperScan` (so it can be excluded in `@WebMvcTest`), then remove it from `MybatisPlusConfig` instead. The key point is that only one class should have `@MapperScan`.

## Warnings

### WR-01: Management vitest.config.ts missing `setupFiles`

**File:** `management/vitest.config.ts:7-9`

**Issue:** The management vitest config does not specify `setupFiles`, while the console vitest config specifies `"./test/setup.ts"`. If management tests need global setup (e.g., DOM polyfills, mock cleanup), they may fail silently or behave inconsistently. This is not currently causing failures but is a maintenance risk.

```typescript
// management/vitest.config.ts
test: {
  environment: 'jsdom',
  globals: true,
  root: fileURLToPath(new URL('./src', import.meta.url)),
  // Missing: setupFiles
},
```

**Fix:** Add a `setupFiles` entry if management tests need global setup, or add an explicit empty value for documentation:

```typescript
test: {
  environment: 'jsdom',
  globals: true,
  root: fileURLToPath(new URL('./src', import.meta.url)),
  setupFiles: [],  // No global setup needed
},
```

### WR-02: Auth store test -- `logout()` does not assert `status` was `loading` during async call

**File:** `console/src/stores/__tests__/auth.spec.ts:278-299`

**Issue:** The logout tests check the final state (`user` is null, `status` is `ready`) but do not verify the intermediate `loading` state. The actual `logout()` method sets `status = "loading"` at the start (line 287 of `auth.ts`). While this is not a bug, it means the test does not fully verify the state machine transition that the login test carefully validates (lines 44-65).

**Fix:** Capture the status during the async operation:

```typescript
it("calls apiPost with /auth/logout and clears user state", async () => {
  const store = useAuthStore();
  store.$patch({ user: mockUser });
  vi.mocked(apiPost).mockImplementation(
    () => new Promise((resolve) => setTimeout(() => resolve(undefined), 5))
  );

  const logoutPromise = store.logout();
  expect(store.status).toBe("loading");  // intermediate state
  await logoutPromise;
  expect(store.status).toBe("ready");    // final state (after clearUser)
});
```

### WR-03: ProblemControllerTest does not verify request body deserialization for createProblem

**File:** `backend-spring/src/test/java/com/ulticode/modules/problem/controller/ProblemControllerTest.java:178-194`

**Issue:** The `createProblem_success` test sends a hardcoded JSON string `"{\"title\":\"Test Problem\",\"slug\":\"test-problem\",\"difficulty\":\"Easy\"}"` but never verifies that the controller correctly deserializes it into a `CreateProblemDTO`. The mock `when(problemService.createProblem(any())).thenReturn(problemVO)` accepts any argument, so even if the JSON field names or types were wrong, the test would still pass. This is less rigorous than the `AuthControllerTest.login_success()` test which uses `objectMapper.writeValueAsString(loginDTO)`.

**Fix:** Create a proper `CreateProblemDTO` object and serialize it, matching the pattern used in `AuthControllerTest`:

```java
@Test
@DisplayName("POST /problems should return 200 (admin auth tested in integration)")
void createProblem_success() throws Exception {
    ProblemVO problemVO = new ProblemVO();
    problemVO.setId(1L);
    problemVO.setTitle("Test Problem");

    when(problemService.createProblem(any())).thenReturn(problemVO);

    CreateProblemDTO createDTO = new CreateProblemDTO();
    createDTO.setTitle("Test Problem");
    createDTO.setSlug("test-problem");
    createDTO.setDifficulty("Easy");

    mockMvc.perform(post("/problems")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createDTO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
}
```

## Info

### IN-01: Console auth API test mocks `apiGet`/`apiPost` but never tests error paths

**File:** `console/src/api/__tests__/auth.spec.ts:1-100`

**Issue:** All 6 tests in the auth API test suite only verify happy paths. There are no tests for network errors, 401 responses, or malformed responses. The auth store test does cover error paths, so this is low risk, but the API layer test could catch request-construction bugs that the store test would not.

**Fix:** Add at least one error test per method that matters:

```typescript
describe("login", () => {
  it("propagates network errors from apiPost", async () => {
    vi.mocked(apiPost).mockRejectedValue(new Error("Network error"));
    await expect(authApi.login(credentials)).rejects.toThrow("Network error");
  });
});
```

### IN-02: `problem-detail.spec.ts` tests only routing logic, not data transformation

**File:** `console/src/api/__tests__/problem-detail.spec.ts:1-73`

**Issue:** All 6 tests verify that `apiGet` is called with the correct URL, but none verify that `mapProblemDetail()` transforms the backend response correctly. The mock for `mapProblem` returns a hardcoded object, bypassing all transformation logic. This means bugs in the mapping (e.g., field name mismatches between `BackendProblemResponse` and `ProblemDetail`) would go undetected.

**Fix:** Add at least one test that verifies the returned `ProblemDetail` structure:

```typescript
it("maps backend response to ProblemDetail correctly", async () => {
  const result = await fetchProblemDetailById(1);
  expect(result).toHaveProperty("content");
  expect(result).toHaveProperty("testCases");
  expect(result).toHaveProperty("languages");
});
```

### IN-03: Management problems store test -- `fetchProblems` error test does not verify `loading` state

**File:** `management/src/stores/admin/__tests__/problems.spec.ts:117-126`

**Issue:** The error test for `fetchProblems` checks `store.error` and `store.loading` after the call completes, but does not verify that `loading` was `true` during the async operation. This is consistent with the other tests in this file, so it is a minor consistency observation.

**Fix:** Consider adding intermediate state assertions similar to the console auth store pattern.

### IN-04: `CodeExecutionServiceTest` uses `ReflectionTestUtils` and raw reflection to test private method

**File:** `backend-spring/src/test/java/com/ulticode/modules/submission/service/CodeExecutionServiceTest.java:115-127`

**Issue:** The `BuildDockerCommand` nested class uses `Class.getDeclaredMethod()` and `setAccessible(true)` to test the private `buildDockerCommand()` method. While `ReflectionTestUtils` is imported, the test uses raw reflection instead. This works but is slightly less idiomatic than using `ReflectionTestUtils.invokeMethod()`. Additionally, testing private methods directly is generally discouraged -- testing through the public `execute()` method would be more maintainable.

**Fix:** Consider using `ReflectionTestUtils.invokeMethod()` for consistency with the import, or better yet, extract `buildDockerCommand` to a package-private or `@VisibleForTesting` method.

---

_Reviewed: 2026-04-17T01:09:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
