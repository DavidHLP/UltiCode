# P3-CONTRACT-002: API Anti-Hub and Cross-Owner Implementation Dependency Gate

## Objective

Verify that API contract modules (`services/api/*`) do not contain implementation
code and do not have cross-owner implementation dependencies (anti-Hub pattern).

## Existing Guards

| API Module | Arch Test | Implementation Leak Guard | Implementation Dependency Guard |
|---|---|---|---|
| `backend-auth-api` | `BackendAuthApiArchTest` | No Entity/Mapper/ServiceImpl/Repository | Only depends on `auth.api..`, `common..`, `java..`, `jakarta..`, `lombok..` |
| `backend-app-api` | `BackendAppApiArchTest` | No Entity/Mapper/ServiceImpl/Repository | Only depends on `app.api..`, `common..`, `submission.api..`, `domain..`, `java..`, `javax..`, `jakarta..`, `lombok..`, `jackson.annotation..`, `swagger..` |
| `backend-submission-api` | `BackendSubmissionApiArchTest` | No Entity/Mapper/ServiceImpl/Repository | Only depends on contract packages |
| `backend-notification-api` | `BackendNotificationApiArchTest` | No Entity/Mapper/ServiceImpl/Repository | Only depends on contract packages |
| `backend-judge-api` | `JudgeApiContractShapeTest` | Shape test only | Only depends on contract packages |

## Anti-Hub Verification Results

### backend-app-api → backend-submission-api dependency

**Status:** Unused at source level. The Maven dependency is declared in
`pom.xml` (line 36-38) and allowed by `BackendAppApiArchTest` (line 124:
`com.ulticode.submission.api..`), but **zero source files** in app-api main
sources import any `com.ulticode.submission.api.*` type.

**Verification:**
```
grep -rn "import com.ulticode.submission.api" services/api/app-api/src/main/java
→ 0 results
```

This dependency is a stale allowlist entry — covered by P3-CONTRACT-006.

### backend-app-api → Other Owner implementation modules

**Status:** PASS — no cross-owner implementation imports.

```
grep -rn "import com.ulticode.auth\.\|import com.ulticode.admin\.\|import com.ulticode.notification\.\|import com.ulticode.submission\." services/api/app-api/src/main/java
→ 0 results (excluding api subpackages)
```

### Cross-owner implementation imports across all API modules

**Status:** PASS — API contract modules contain only contract interfaces,
no implementation imports from Owner modules.

```
for api in auth-api app-api submission-api notification-api judge-api; do
  grep -rn "import com.ulticode\.\(auth\|admin\|app\|submission\|notification\|search\|judge\)"
    services/api/$api/src/main/java
  | grep -v "api\."
  | grep -v "/api/"
done
→ 0 results
```

## Conclusion

The anti-Hub gate passes for all API contract modules. No cross-owner
implementation dependencies exist. The only actionable item is the unused
`backend-submission-api` Maven dependency in app-api (P3-CONTRACT-006).

## Verification Commands

```bash
cd services/api/app-api && ./../../../mvnw test -B -q
cd services/api/auth-api && ./../../../mvnw test -B -q
cd services/api/submission-api && ./../../../mvnw test -B -q
cd services/api/notification-api && ./../../../mvnw test -B -q
```
