# Plan: Remove All Recommendations-Related Content

## Summary
Delete the entire recommendations subsystem from the UltiCode project. This includes the backend Spring Boot module, console frontend views/routes/API/i18n, database artifacts, Docker services, CI workflows, and environment configuration. The standalone `recommendation/` microservice directory does not exist in the current working tree (already removed or never present), but CI workflows and backend dependencies still reference it.

## User Story
As a maintainer, I want to completely remove the recommendations feature, so that the codebase is simplified and no longer carries dead code for a disabled feature.

## Problem → Solution
The recommendations feature (daily picks, weak points, challenge, similar problems) is no longer needed. It spans backend controllers, services, entities, frontend views, routing, i18n, database tables, Docker Compose services, and CI pipelines. → Remove all artifacts and references in a single coordinated cleanup.

## Metadata
- **Complexity**: XL
- **Source PRD**: N/A
- **PRD Phase**: standalone
- **Estimated Files**: 35+ files (delete ~20, modify ~15)

---

## UX Design

### Before
Users see a "Problem Recommendations" nav item in the top navigation bar and a sidebar section with four sub-items: Daily, Weak Points, Challenge, Similar. Clicking them loads `/recommendations/*` routes.

### After
No "Problem Recommendations" nav item, no sidebar section, no `/recommendations/*` routes. The feature is fully gone from the UI.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Top nav bar | "Problem Recommendations" link present | Link removed | AppLayout.vue navItems |
| Sidebar (authenticated) | Recommendation section with 4 items | Section removed | AppSidebar.vue + sidebar.data.ts |
| Router | `/recommendations/*` routes exist | Routes removed | router/index.ts |
| API calls | `GET /recommendations/daily` etc. | Endpoints removed | Backend controllers deleted |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `console/src/router/index.ts` | 224-301 | Route definition pattern to follow for removal |
| P0 | `console/src/features/sider/sidebar.data.ts` | 218-245 | Sidebar data structure pattern |
| P0 | `console/src/features/sider/AppLayout.vue` | 39-75 | Nav item definition and active state logic |
| P0 | `console/src/features/sider/AppSidebar.vue` | 46-67 | Sidebar context switching logic |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/recommendation/` | all | Backend module to delete |
| P1 | `backend-spring/pom.xml` | 210-227 | Dependency removal pattern |
| P1 | `backend-spring/src/main/resources/application.yml` | 118-124, 214+ | Config removal pattern |
| P2 | `docker-compose.prod.yml` | 156-200 | Docker service removal |
| P2 | `.github/workflows/ci.yml` | 81-85, 143-147 | CI step removal |

---

## External Documentation

No external research needed — all changes use established internal patterns (delete files, remove imports, remove enum values, drop DB tables).

---

## Patterns to Mirror

### ROUTE_REGISTRATION
// SOURCE: console/src/router/index.ts:224-258
```typescript
const recommendationRoutes: RouteRecordRaw = {
  path: "/recommendations",
  component: () => import("@/features/sider/AppLayout.vue"),
  meta: { requiresAuth: true },
  children: [ ... ],
};
```
Pattern: Remove the route object definition and its insertion into the `routes` array.

### SIDEBAR_DATA_EXPORT
// SOURCE: console/src/features/sider/sidebar.data.ts:218-245
```typescript
export const recommendationSidebarData: SidebarSection[] = [ ... ];
```
Pattern: Remove the exported data array and its consumer imports.

### SIDEBAR_CONTEXT_SWITCH
// SOURCE: console/src/features/sider/AppSidebar.vue:46-67
```typescript
const isRecommendationContext = computed(() =>
  route.path.startsWith("/recommendations"),
);
// ...
if (isRecommendationContext.value) {
  return recommendationSidebarData;
}
```
Pattern: Remove the computed property and its branch in the sidebar data selector.

### NAV_ITEM_DEFINITION
// SOURCE: console/src/features/sider/AppLayout.vue:39-56
```typescript
const navItems = computed<NavItem[]>(() => [
  // ...
  {
    label: t("recommendation.title"),
    to: { name: "recommendations-daily" },
  },
]);
```
Pattern: Remove the nav item object from the array.

### I18N_MODULE_IMPORT
// SOURCE: console/src/i18n/locales/en-US/index.ts:15,33
```typescript
import recommendation from "./recommendation";
export default { ..., recommendation };
```
Pattern: Remove the import and the object property.

### BACKEND_MODULE_DELETION
No specific pattern — delete the entire `backend-spring/src/main/java/com/ulticode/modules/recommendation/` directory.

### ERROR_CODE_REMOVAL
// SOURCE: backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java:125-131
```java
// Recommendation module (12xxxx)
RECOMMENDATION_SERVICE_UNAVAILABLE(120001, "...", HttpStatus.SERVICE_UNAVAILABLE),
RECOMMENDATION_DISABLED(120002, "...", HttpStatus.SERVICE_UNAVAILABLE),
RECOMMENDATION_INVALID_SCENARIO(120003, "...", HttpStatus.BAD_REQUEST),
RECOMMENDATION_NOT_FOUND(120004, "...", HttpStatus.NOT_FOUND),
RECOMMENDATION_USER_REQUIRED(120005, "...", HttpStatus.BAD_REQUEST),
```
Pattern: Remove all recommendation enum constants. Keep the `// Backup module (13xxxx)` comment intact.

### POM_DEPENDENCY_REMOVAL
// SOURCE: backend-spring/pom.xml:210-227
```xml
<!-- Dubbo3 (direct call to recommendation service) -->
<dependency>
  <groupId>org.apache.dubbo</groupId>
  <artifactId>dubbo</artifactId>
  <version>${dubbo.version}</version>
</dependency>
<dependency>
  <groupId>org.apache.dubbo</groupId>
  <artifactId>dubbo-registry-nacos</artifactId>
  <version>${dubbo.version}</version>
</dependency>
<!-- Recommendation API (Dubbo service interface) -->
<dependency>
  <groupId>com.ulticode</groupId>
  <artifactId>recommend-api</artifactId>
  <version>1.0.0</version>
</dependency>
```
**GOTCHA**: Dubbo might be used by OTHER modules (check first). If Dubbo is only used for recommendation, remove all Dubbo dependencies. If other modules use Dubbo, only remove `recommend-api`.

### APPLICATION_YAML_REMOVAL
// SOURCE: backend-spring/src/main/resources/application.yml:118-124
```yaml
# Recommendation Service Configuration (optional - for personalized recommendations)
recommendation:
  enabled: ${RECOMMENDATION_ENABLED:false}
  service-url: ${RECOMMENDATION_SERVICE_URL:}
  timeout: ${RECOMMENDATION_TIMEOUT:5000}
  nacos-enabled: ${RECOMMENDATION_NACOS_ENABLED:false}
  fallback-url: ${RECOMMENDATION_FALLBACK_URL:}
```
Also remove the Dubbo configuration comment block around line 214 if it exists and is recommendation-specific.

### DOCKER_SERVICE_REMOVAL
// SOURCE: docker-compose.prod.yml:156-200
Remove the `recommend-provider:` and `recommend-web:` service blocks entirely.

---

## Files to Change

### DELETE — Backend Module
| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/config/RecommendationConfig.java` | DELETE | Configuration class for recommendation feature |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/controller/RecommendationController.java` | DELETE | REST controller for /recommendations endpoints |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/controller/RecommendationDataController.java` | DELETE | Admin controller for recommendation data sync |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/dto/GetRecommendationsDTO.java` | DELETE | Request DTO |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/dto/RecommendResponseVO.java` | DELETE | Response VO with inner classes |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/entity/DailyRecommendation.java` | DELETE | MyBatis-Plus entity |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/mapper/DailyRecommendationMapper.java` | DELETE | MyBatis mapper with @Select/@Update/@Delete |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/scheduler/RecommendationScheduler.java` | DELETE | Scheduled task for Redis sync and pre-generation |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/service/RecommendationService.java` | DELETE | Service interface |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/service/RecommendationDataService.java` | DELETE | Service for syncing data to Redis |
| `backend-spring/src/main/java/com/ulticode/modules/recommendation/service/impl/RecommendationServiceImpl.java` | DELETE | Service implementation with Dubbo RPC calls |
| `backend-spring/src/test/java/com/ulticode/modules/recommendation/service/RecommendationServiceTest.java` | DELETE | Unit tests for recommendation service |

### DELETE — Console Frontend
| File | Action | Justification |
|---|---|---|
| `console/src/views/recommendations/RecommendationsView.vue` | DELETE | Main recommendations page view |
| `console/src/views/recommendations/components/ProblemCard.vue` | DELETE | Problem card component for recommendations |
| `console/src/views/recommendations/components/RecommendationResultList.vue` | DELETE | Result list component |
| `console/src/views/recommendations/components/SimilarProblemSearch.vue` | DELETE | Similar problem search component |
| `console/src/views/recommendations/components/TagFilter.vue` | DELETE | Tag filter component |
| `console/src/api/recommendation.ts` | DELETE | API functions for recommendation endpoints |
| `console/src/stores/recommendation.ts` | DELETE | Pinia store with caching logic |
| `console/src/stores/__tests__/recommendation.spec.ts` | DELETE | Store unit tests |
| `console/src/types/recommendation.ts` | DELETE | TypeScript type definitions |
| `console/src/i18n/locales/en-US/recommendation.ts` | DELETE | English i18n for recommendations |
| `console/src/i18n/locales/zh-CN/recommendation.ts` | DELETE | Chinese i18n for recommendations |

### DELETE — CI/CD
| File | Action | Justification |
|---|---|---|
| `.github/workflows/ci-recommendation.yml` | DELETE | Standalone CI for recommendation microservice |

### MODIFY — Backend
| File | Action | Justification |
|---|---|---|
| `backend-spring/pom.xml` | UPDATE | Remove recommend-api dependency; evaluate whether Dubbo dependencies should also be removed |
| `backend-spring/src/main/resources/application.yml` | UPDATE | Remove recommendation config block (lines ~118-124) and Dubbo comment |
| `backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java` | UPDATE | Remove recommendation error codes (lines ~126-131) |

### MODIFY — Console Frontend
| File | Action | Justification |
|---|---|---|
| `console/src/router/index.ts` | UPDATE | Remove recommendationRoutes definition (~lines 224-258) and its usage in routes array (~line 301) |
| `console/src/features/sider/sidebar.data.ts` | UPDATE | Remove recommendationSidebarData export (~lines 218-245) and any imports of it |
| `console/src/features/sider/AppSidebar.vue` | UPDATE | Remove isRecommendationContext computed and recommendationSidebarData branch (~lines 11, 49-51, 63-65) |
| `console/src/features/sider/AppLayout.vue` | UPDATE | Remove recommendation nav item (~lines 52-55) and isActiveNav special case for recommendations (~lines 64-69) |
| `console/src/i18n/locales/en-US/index.ts` | UPDATE | Remove recommendation import and export property |
| `console/src/i18n/locales/zh-CN/index.ts` | UPDATE | Remove recommendation import and export property |
| `console/src/i18n/locales/en-US/sidebar.ts` | UPDATE | Remove `recommendation` keys (~line 69) |
| `console/src/i18n/locales/zh-CN/sidebar.ts` | UPDATE | Remove `recommendation` keys (~line 69) |

### MODIFY — Docker / DevOps
| File | Action | Justification |
|---|---|---|
| `docker-compose.prod.yml` | UPDATE | Remove `recommend-provider` and `recommend-web` service blocks |
| `.github/workflows/ci.yml` | UPDATE | Remove "Build recommend-api" steps in both backend-build and backend-test jobs |
| `.github/dependabot.yml` | UPDATE | Remove `".github/workflows/ci-recommendation.yml"` from the ignore list |
| `ecosystem.config.cjs` | UPDATE | Remove `RECOMMENDATION_ENABLED: 'true'` from ulticode-9001 env |
| `.env` | UPDATE | Remove `RECOMMENDATION_SERVICE_NAME=recommend-web` line |
| `.env.example` | UPDATE | Remove `RECOMMENDATION_SERVICE_NAME=recommend-web` line |

### CREATE — Database Migration
| File | Action | Justification |
|---|---|---|
| `db-manager/migrations/V{N}__drop_recommendation_tables.sql` | CREATE | Drop `DailyRecommendation` / `daily_recommendations` table and any recommendation-specific indexes |

---

## NOT Building
- **ForumPost.recommendation field**: The forum module has a JSON column `recommendation` in `ForumPost.java` (line 77-78). This may be a forum-specific feature (e.g., trending/recommended posts) unrelated to the recommendation subsystem. Do NOT remove without explicit confirmation.
- **Dubbo framework removal**: Dubbo dependencies in `pom.xml` might be used by other modules. Only remove `recommend-api`; leave `dubbo` and `dubbo-registry-nacos` unless verification proves they are unused.
- **Migration file deletion**: Do NOT delete old migrations (V7, V10, V16, V17). Flyway migrations are immutable history. Instead, create a new migration to drop the table.
- **Problem/solution seed data**: V16 and V17 seed general problems and submissions. Keep these migrations — they are not recommendation-specific (merely seeded for recommendation testing).

---

## Step-by-Step Tasks

### Task 1: Delete Backend Recommendation Module
- **ACTION**: Delete the entire `backend-spring/src/main/java/com/ulticode/modules/recommendation/` directory (11 Java files).
- **IMPLEMENT**: `rm -rf backend-spring/src/main/java/com/ulticode/modules/recommendation/`
- **MIRROR**: Backend module deletion pattern.
- **IMPORTS**: N/A
- **GOTCHA**: Ensure no other Java files outside this directory import from it (verified: none found except ErrorCode which only defines enum values).
- **VALIDATE**: `./mvnw compile -B` in backend-spring should still compile (after dependent references are also cleaned).

### Task 2: Delete Backend Recommendation Tests
- **ACTION**: Delete `backend-spring/src/test/java/com/ulticode/modules/recommendation/`.
- **IMPLEMENT**: `rm -rf backend-spring/src/test/java/com/ulticode/modules/recommendation/`
- **VALIDATE**: `./mvnw test` still passes.

### Task 3: Remove Recommendation Error Codes
- **ACTION**: Open `backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java` and remove the 5 recommendation enum constants (lines ~126-131).
- **IMPLEMENT**: Delete the block from `// Recommendation module (12xxxx)` through `RECOMMENDATION_USER_REQUIRED(...)`.
- **MIRROR**: Error code removal pattern.
- **GOTCHA**: Leave the `// Backup module (13xxxx)` comment intact.
- **VALIDATE**: File compiles.

### Task 4: Remove Backend Dependencies and Config
- **ACTION**: Update `backend-spring/pom.xml` to remove the `recommend-api` dependency. Evaluate Dubbo dependencies.
- **IMPLEMENT**: Remove lines:
  ```xml
  <!-- Recommendation API (Dubbo service interface) -->
  <dependency>
    <groupId>com.ulticode</groupId>
    <artifactId>recommend-api</artifactId>
    <version>1.0.0</version>
  </dependency>
  ```
  Check if Dubbo is used elsewhere: `grep -rn "DubboReference\|dubbo" backend-spring/src/main/java/ --include="*.java" | grep -v "modules/recommendation"`. If no matches, also remove the Dubbo dependencies.
- **ACTION**: Update `backend-spring/src/main/resources/application.yml` to remove the recommendation config block (~lines 118-124) and any Dubbo comment.
- **VALIDATE**: `./mvnw compile -B` passes.

### Task 5: Delete Console Frontend Views, API, Store, Types
- **ACTION**: Delete recommendation-related Vue views, components, API, store, and type files.
- **IMPLEMENT**:
  ```bash
  rm -rf console/src/views/recommendations/
  rm console/src/api/recommendation.ts
  rm console/src/stores/recommendation.ts
  rm console/src/stores/__tests__/recommendation.spec.ts
  rm console/src/types/recommendation.ts
  rm console/src/i18n/locales/en-US/recommendation.ts
  rm console/src/i18n/locales/zh-CN/recommendation.ts
  ```
- **VALIDATE**: `cd console && pnpm type-check` should report errors from remaining references (which we fix in next tasks).

### Task 6: Remove Console Router Routes
- **ACTION**: Open `console/src/router/index.ts`. Remove `recommendationRoutes` definition (~lines 224-258) and its insertion in the routes array (~line 301: `recommendationRoutes,`).
- **MIRROR**: Route registration pattern.
- **GOTCHA**: Ensure no other file imports `recommendationRoutes` from this file.
- **VALIDATE**: `pnpm type-check` — no router errors.

### Task 7: Remove Console Sidebar and Navigation References
- **ACTION**: Update three files to remove recommendation sidebar data and nav items.
- **IMPLEMENT**:
  1. `console/src/features/sider/sidebar.data.ts`: Remove `recommendationSidebarData` export (~lines 218-245).
  2. `console/src/features/sider/AppSidebar.vue`: Remove `recommendationSidebarData` import, `isRecommendationContext` computed, and its branch in `currentSidebarData`.
  3. `console/src/features/sider/AppLayout.vue`: Remove the recommendation nav item from `navItems` array, and remove the special `isActiveNav` case for recommendations.
- **MIRROR**: Sidebar data export, context switch, nav item definition patterns.
- **VALIDATE**: `pnpm type-check`.

### Task 8: Remove Console i18n References
- **ACTION**: Update i18n index files and sidebar locale files.
- **IMPLEMENT**:
  1. `console/src/i18n/locales/en-US/index.ts`: Remove `import recommendation from "./recommendation"` and the `recommendation` property in the exported object.
  2. `console/src/i18n/locales/zh-CN/index.ts`: Same as above.
  3. `console/src/i18n/locales/en-US/sidebar.ts`: Remove `recommendation: { ... }` block.
  4. `console/src/i18n/locales/zh-CN/sidebar.ts`: Remove `recommendation: { ... }` block.
- **VALIDATE**: `pnpm type-check` and run dev server to check for `[intlify] Not found '...' key` warnings.

### Task 9: Create Database Migration to Drop Recommendation Table
- **ACTION**: Create a new Flyway migration to drop the `DailyRecommendation` table.
- **IMPLEMENT**: Create `db-manager/migrations/V{next}__drop_recommendation_tables.sql`:
  ```sql
  SET FOREIGN_KEY_CHECKS=0;

  DROP TABLE IF EXISTS `DailyRecommendation`;

  SET FOREIGN_KEY_CHECKS=1;
  ```
  Use the next available migration number (check `db-manager/migrations/` for the highest V number).
- **GOTCHA**: The table might be named `DailyRecommendation` (PascalCase in entity) or `daily_recommendations` (the actual MySQL table name from V7 migration). Use `DROP TABLE IF EXISTS` for both variants.
- **VALIDATE**: `db-manager validate` and `db-manager migrate --dry-run`.

### Task 10: Remove Docker and CI References
- **ACTION**: Update Docker Compose and CI configurations.
- **IMPLEMENT**:
  1. `docker-compose.prod.yml`: Remove `recommend-provider:` and `recommend-web:` service blocks.
  2. `.github/workflows/ci.yml`: Remove both "Build recommend-api" steps.
  3. `.github/workflows/ci-recommendation.yml`: Delete the entire file.
  4. `.github/dependabot.yml`: Remove `".github/workflows/ci-recommendation.yml"` from ignore list.
- **VALIDATE**: No broken YAML syntax.

### Task 11: Remove Environment and PM2 Config
- **ACTION**: Update `.env`, `.env.example`, and `ecosystem.config.cjs`.
- **IMPLEMENT**:
  1. `.env`: Remove `RECOMMENDATION_SERVICE_NAME=recommend-web` line.
  2. `.env.example`: Remove the same line.
  3. `ecosystem.config.cjs`: Remove `RECOMMENDATION_ENABLED: 'true'` from the `ulticode-9001` app env.
- **VALIDATE**: N/A

---

## Testing Strategy

### Backend Compilation
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Compile backend | `./mvnw compile -B` | BUILD SUCCESS | After all backend changes |
| Run backend tests | `./mvnw test -Dtest='!*IT' -B` | All pass | No recommendation tests |

### Frontend Checks
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Type check console | `pnpm type-check` | Zero errors | After all console changes |
| Lint console | `pnpm lint` | No errors | — |
| Run console tests | `pnpm test` | All pass | — |

### Database Validation
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Validate migrations | `db-manager validate` | No errors | New migration registered |
| Dry-run migration | `db-manager migrate --dry-run` | Shows drop table | — |

### Edge Cases Checklist
- [ ] No `recommendation` string references remain in compiled frontend code (use `grep -rn "recommend" console/src --include="*.ts" --include="*.vue"`)
- [ ] No `recommendation` module references remain in backend Java code (use `grep -rn "recommendation\|Recommend" backend-spring/src/main/java/ --include="*.java"`)
- [ ] No broken router links (navigating to `/recommendations` should 404, not crash)
- [ ] i18n keys for recommendation no longer produce console warnings
- [ ] Backend starts without `recommendation.enabled` property

---

## Validation Commands

### Backend
```bash
cd backend-spring
./mvnw compile -B
./mvnw test -Dtest='!*IT' -B
```
EXPECT: BUILD SUCCESS, all tests pass

### Console Frontend
```bash
cd console
pnpm type-check
pnpm lint
pnpm test
```
EXPECT: Zero type errors, zero lint errors, all tests pass

### Database
```bash
cd db-manager
db-manager validate
db-manager migrate --dry-run
```
EXPECT: Valid, drop table statement shown in dry-run

### Full Verification
```bash
grep -rn "recommendation\|Recommend" backend-spring/src/main/java/ --include="*.java" | grep -v "forum/"
grep -rn "recommendation\|Recommend" console/src --include="*.ts" --include="*.vue"
```
EXPECT: No matches (except possibly forum-related `recommendation` JSON field)

---

## Acceptance Criteria
- [ ] All backend recommendation module files deleted
- [ ] All console recommendation views, API, store, types, i18n deleted
- [ ] Router, sidebar, and navigation no longer reference recommendations
- [ ] Backend compiles and tests pass
- [ ] Console type-checks, lints, and tests pass
- [ ] Database migration created to drop recommendation table
- [ ] Docker Compose recommendation services removed
- [ ] CI recommendation workflow removed, main CI updated
- [ ] Environment and PM2 config cleaned
- [ ] No `[intlify] Not found` warnings at runtime

## Completion Checklist
- [ ] Code follows discovered patterns
- [ ] Error handling matches codebase style
- [ ] Tests follow test patterns
- [ ] No hardcoded values
- [ ] Documentation updated (CLAUDE.md references to recommendation ports/services should be removed if any)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Dubbo used by other modules | Medium | High | Before removing Dubbo deps from pom.xml, grep for `DubboReference` and `dubbo` across ALL backend Java files |
| Hidden frontend references in lazy-loaded chunks | Medium | Medium | After deletion, run full type-check and dev build to catch any dynamic imports |
| Database foreign key constraints prevent DROP TABLE | Low | High | Use `DROP TABLE IF EXISTS` with `SET FOREIGN_KEY_CHECKS=0/1` |
| Other CI files reference recommendation artifacts | Low | Medium | Search `.github/` for "recommend" before finishing |
| ForumPost.recommendation field is actually needed | Low | Medium | Do NOT remove without user confirmation; document as ambiguous |

## Notes
- The standalone `recommendation/` directory (containing Dubbo provider, Spark jobs, recommend-api, recommend-web) does NOT exist in the current working tree. Only CI workflows and pom.xml still reference it. This plan cleans up those dangling references.
- The `ForumPost` entity has a `recommendation` JSON field (line 77-78). This is ambiguous — it might store "recommended/trending post" metadata for the forum module, which is conceptually different from the problem recommendation subsystem. Do not remove without confirming with the user.
- Migration V16 and V17 seed general problems and submissions. They were created for recommendation engine testing but the data they seed is valid for the overall application. Do not delete these migrations; they are part of Flyway's immutable history.
