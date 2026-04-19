# Code Conventions

## Overview

This document defines coding conventions for the UltiCode project across all layers.

## Backend (Spring Boot)

### Technology Stack

- **Framework**: Spring Boot 3.2.5 (Java 17)
- **ORM**: MyBatis-Plus 3.5.16
- **API Docs**: SpringDoc OpenAPI 2.6.0
- **Build**: Maven with `./mvnw` wrapper

### Java Coding Standards

1. **Immutability**: Always create new objects; never mutate existing ones
2. **Error Handling**: Comprehensive try-catch with safe `unknown` narrowing
3. **Input Validation**: Use `@Valid` annotations and Bean Validation
4. **Naming**:
   - Classes: `PascalCase` (e.g., `UserService`)
   - Methods: `camelCase` (e.g., `findById`)
   - Constants: `UPPER_SNAKE_CASE`
   - DTOs:Suffix with `DTO`, `VO`, `Request`, `Response`
5. **File Organization**: Follow module structure under `com.ulticode.modules.<domain>`

### Backend Module Structure

```
com.ulticode/
├── common/           # Shared utilities, configs, exceptions
│   ├── response/      # Result wrapper, PageResult
│   ├── exception/     # GlobalExceptionHandler, BusinessException
│   └── config/       # SecurityConfig, WebConfig, RedisConfig
├── security/         # JWT filters, CSRF service
└── modules/          # Feature modules
    ├── auth/
    ├── user/
    ├── problem/
    └── ...
```

Each module contains:
- `controller/` - REST endpoints
- `service/` - Business logic
- `entity/` - Database entities (MyBatis-Plus)
- `mapper/` - MyBatis mappers
- `dto/` - Request/Response DTOs

### API Response Format

All APIs use `Result<T>` wrapper:

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

- `code: 0` = success, non-zero = error
- Frontend `request.ts` unwraps automatically

### Recommended Hooks

Configure in `~/.claude/settings.json`:
- **google-java-format**: Auto-format `.java` files after edit
- **checkstyle**: Run style checks after editing Java files
- **./mvnw compile**: Verify compilation after changes

## Frontend (Vue 3 + Vite + Tailwind)

### Technology Stack

- **Framework**: Vue 3 + TypeScript
- **Build**: Vite
- **Styling**: Tailwind CSS v4 with OKLCH colors
- **Components**: shadcn-vue + Radix Vue + Lucide icons
- **Testing**: Vitest + Playwright

### TypeScript Standards

1. **Types**: Explicit types on public APIs, infer local variables
2. **Interfaces vs Types**:
   - `interface` for object shapes that may be extended
   - `type` for unions, intersections, utility types
3. **Avoid `any`**: Use `unknown` for external input, narrow safely
4. **Immutability**: Use spread operator for updates
5. **Props**: Define with named `interface` or `type`, not `React.FC`

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Components | PascalCase | `UserCard.vue`, `ProblemList.vue` |
| Hooks | camelCase with `use` prefix | `useAuth.ts`, `useProblem.ts` |
| CSS classes | kebab-case | `problem-card`, `submit-btn` |
| Constants | UPPER_SNAKE_CASE | `MAX_SUBMISSIONS` |
| Variables | camelCase | `isLoading`, `problemList` |

### ESLint Configuration

- Config: `eslint.config.ts` (flat config)
- Extends: `@vue/eslint-config-typescript`
- Plugin: `eslint-plugin-vue`
- Ignores: `dist/`, `dist-ssr/`, `coverage/`

```typescript
// Key rules
'vue/multi-word-component-names': 'off'  // Allow single-word components
```

### Prettier Configuration

- Config: `.prettierrc.json`
- `semi: false` - No semicolons
- `singleQuote: true` - Single quotes
- `printWidth: 100` - 100 character line width

### File Organization

Organize by feature/surface, not by type:

```
src/
├── components/
│   ├── hero/
│   │   ├── Hero.tsx
│   │   └── hero.css
│   └── ui/
│       ├── Button.tsx
│       └── Card.tsx
├── hooks/
│   ├── useAuth.ts
│   └── useProblem.ts
├── api/
│   └── problem.ts
└── styles/
    └── tokens.css
```

## Git Workflow

### Commit Message Format

```
<type>: <description>

<optional body>
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`

### Pull Request Workflow

1. Analyze full commit history with `git diff [base-branch]...HEAD`
2. Draft comprehensive PR summary
3. Include test plan with TODOs
4. Push with `-u` flag if new branch
5. All automated checks (CI/CD) must pass before merge

## CI/CD

- **Backend**: Maven build with `./mvnw`
- **Frontend**: Vite build (`pnpm build`)
- **Testing**: Vitest for unit tests, Playwright for E2E
