# Coding Conventions

**Analysis Date:** 2026-04-22

## Naming Conventions

### Java (Backend)

**Classes, Interfaces, Records, Enums:**
- PascalCase: `UserService`, `ErrorCode`, `UserVO`

**Methods:**
- camelCase: `findById`, `updateCurrentUser`, `getUserById`

**Fields, Parameters, Local Variables:**
- camelCase: `userId`, `pageSize`, `errorCode`

**Constants (static final):**
- SCREAMING_SNAKE_CASE: `MAX_PAGE_SIZE`, `DEFAULT_TIMEOUT`

**Packages:**
- All lowercase: `com.ulticode.modules.user.service`

### TypeScript/Vue (Frontend)

**Components:**
- PascalCase: `ErrorBoundary.vue`, `LoadingOverlay.vue`

**Hooks:**
- camelCase with `use` prefix: `useLoading`, `useRetry`, `useEditorThemes`

**Functions, Variables:**
- camelCase: `authApi`, `submitSolution`, `isActive`

**Types/Interfaces:**
- PascalCase: `User`, `AuthResponse`, `ProblemDetailProps`

**CSS Classes:**
- kebab-case: `bg-primary`, `text-muted-foreground`

### SQL (Database Migrations)

**Tables:**
- snake_case: `users`, `role_permissions`, `forum_posts`

**Columns:**
- snake_case: `user_id`, `joined_at`, `is_active`

**Indexes:**
- Descriptive: `users_username_key`, `users_role_idx`

**Migration Naming:**
- `V{version}__{description}.sql`: `V1__core_schema.sql`, `V11__moderation_seed_data.sql`

## Code Style

### Java (Backend)

**Formatting:**
- Spring Boot conventions (Google Java Style underlying)
- 4-space indentation
- One public class per file

**Linting:**
- Checkstyle via Maven build

**Import Order:**
1. `java.*` packages
2. `javax.*` packages
3. Third-party (`com.*`, `org.*`)
4. `com.ulticode.*` (project imports)

### TypeScript/Vue (Frontend)

**Formatting:**
- Prettier (configured in `.prettierrc.json`)
- `semi: false`, `singleQuote: true`, `printWidth: 100`

**Linting:**
- ESLint 9.x with `eslint-plugin-vue@^9.30.0` (console)
- ESLint 10.x (management)
- Vue TsConfig recommended rules

**Import Order (via ESLint):**
1. External imports
2. Internal imports (`@/` path aliases)
3. Relative imports

**Path Aliases:**
- `@` maps to `./src` in both console and management frontends

### Vue Component Style

- Use `<script setup lang="ts">` for Composition API
- Props defined with `defineProps<PropsType>()`
- Emit defined with `defineEmits<{...}>()`
- Single word component names allowed for UI primitives: `Alert`, `Button`, `Avatar`

## API Response Format

### Backend Response Wrapper

All API responses use `Result<T>` from `backend-spring/src/main/java/com/ulticode/common/response/Result.java`:

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

**Success:** `code: 0`, `message: "success"`
**Error:** `code: non-zero`, `message: error description`

### Pagination

Use `PageResult<T>` from `backend-spring/src/main/java/com/ulticode/common/response/PageResult.java`:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  },
  "traceId": "t-1234567890"
}
```

### Frontend API Client Pattern

```typescript
// management/src/api/example.ts
import { apiGet, apiPost } from "@/utils/request";

export const exampleApi = {
  async getList(): Promise<Item[]> {
    return apiGet<Item[]>("/endpoint");
  },
  async create(data: CreateDTO): Promise<Item> {
    return apiPost<Item>("/endpoint", data);
  },
};
```

Frontend `request.ts` automatically unwraps responses, returning `response.data` directly.

## Error Handling

### Backend (Java)

**Exception Pattern:**
- `BusinessException` from `com.ulticode.common.exception.BusinessException`
- Carries `ErrorCode` enum value and trace ID
- Never expose stack traces in API responses

**ErrorCode Enum:**
Located at `backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java`

Format: `MODULE_XXXXX`
- `AUTH_*` (1xxxx) - Authentication
- `USER_*` (2xxxx) - User module
- `PROBLEM_*` (3xxxx) - Problem module
- `SUBMISSION_*` (4xxxx) - Submission module
- `SOLUTION_*` (5xxxx) - Solution module
- `FORUM_*` (6xxxx) - Forum module
- `CONTEST_*` (7xxxx) - Contest module
- `BOOKMARK_*` (8xxxx) - Bookmark module
- `PROBLEM_LIST_*` (9xxxx) - Problem list module
- Moderation (10xxxx), Search (11xxxx), Recommendation (12xxxx), etc.

**Global Exception Handler:**
- `GlobalExceptionHandler` in `com.ulticode.common.exception`
- Maps `BusinessException` to appropriate HTTP status and `Result.error()`

### Frontend (TypeScript/Vue)

**Error Handling Pattern:**
```typescript
import { getErrorMessage } from "@/utils/error";

try {
  const result = await authApi.login(credentials);
} catch (error: unknown) {
  const message = getErrorMessage(error);
  // Handle error
}
```

**No console.log in production code** - Use proper logging or error handling.

## Logging Conventions

### Backend (Java)

**Framework:** SLF4J with Logback (Spring Boot default)

**Log Levels:**
- `log.error(...)` - Errors requiring attention
- `log.warn(...)` - Warnings (e.g., invalid input)
- `log.info(...)` - Key business events
- `log.debug(...)` - Detailed debugging

**Pattern:** Include relevant context (IDs, parameters) without sensitive data.

### Frontend (TypeScript/Vue)

**No console.log statements in production code.**

Use error boundary components for component-level error handling.

## Git Commit Format

Format: `<type>: <description>`

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`

Example:
```
feat(user): add user profile avatar upload
fix(auth): resolve token refresh race condition
docs(api): update authentication documentation
```

## File Organization

### Backend Module Structure

```
backend-spring/src/main/java/com/ulticode/
├── common/           # Shared utilities, configs, exceptions
│   ├── response/     # Result<T> wrapper, PageResult
│   ├── exception/    # BusinessException, ErrorCode, GlobalExceptionHandler
│   ├── config/       # SecurityConfig, WebConfig, RedisConfig
│   └── annotation/   # @CurrentUser, @RequireRole, @RateLimit
├── modules/          # Feature modules
│   ├── auth/
│   ├── user/
│   └── ...
```

Each module contains:
- `controller/` - REST endpoints
- `service/` - Business logic
- `entity/` - Database entities (MyBatis-Plus)
- `mapper/` - MyBatis mappers
- `dto/` - Request/Response DTOs

### Frontend Structure

```
console/src/ OR management/src/
├── api/              # API client modules
├── components/        # Vue components
├── composables/       # Vue composables (hooks)
├── stores/           # Pinia stores
├── utils/            # Utility functions
└── types/            # TypeScript types
```

---

*Convention analysis: 2026-04-22*
