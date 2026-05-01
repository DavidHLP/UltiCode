# Coding Conventions

This document describes the coding conventions and patterns used in the UltiCode codebase.

## Languages & Frameworks

| Layer | Language | Framework/Tool |
|-------|----------|----------------|
| Backend | Java 17 | Spring Boot 3.2.5, MyBatis-Plus |
| Recommendation | Java 17 | Dubbo3, Spark |
| Console Frontend | TypeScript | Vue 3, Vite, Tailwind CSS v4 |
| Management Frontend | TypeScript | Vue 3, Vite, Tailwind CSS v4 |

---

## Backend (Java/Spring Boot)

### Project Structure

```
backend-spring/src/main/java/com/ulticode/
├── common/           # Shared utilities, configs, exceptions
│   ├── response/     # Result wrapper, PageResult
│   ├── exception/    # GlobalExceptionHandler, BusinessException
│   ├── config/       # SecurityConfig, WebConfig, RedisConfig
│   └── annotation/   # @CurrentUser, @RequireRole, @RateLimit
├── security/         # JWT filters, CSRF service
├── modules/          # Feature modules (auth, user, problem, submission, contest, etc.)
├── websocket/        # Real-time communication
```

### Java Coding Style

- **Formatter**: google-java-format (via hooks)
- **Checkstyle**: Google Java Style guide
- **Java Version**: 17
- **Indent**: 2 or 4 spaces (match project standard)

### Naming Conventions

| Element | Convention |
|---------|------------|
| Classes/Interfaces/Records | PascalCase |
| Methods/Fiels/Parameters | camelCase |
| Static final constants | SCREAMING_SNAKE_CASE |
| Packages | lowercase, reverse domain (`com.ulticode.module.service`) |

### Key Patterns

- **Service Layer**: Business logic in service classes; keep controllers and repositories thin
- **Constructor Injection**: Always use constructor injection over field injection
- **DTOs**: Use Java records for DTOs and value types (Java 16+)
- **Repository Pattern**: MyBatis-Plus with mapper interfaces

### Backend Module Structure (per feature)

Each module typically contains:
- `controller/` - REST endpoints
- `service/` - Business logic
- `entity/` - Database entities
- `mapper/` - MyBatis mappers
- `dto/` - Request/Response DTOs

---

## Frontend (Vue 3/TypeScript)

### Project Structure

```
console/ (or management/)/
├── src/
│   ├── api/           # API client functions
│   ├── components/    # Vue components
│   ├── composables/    # Vue composables (hooks)
│   ├── stores/        # Pinia stores
│   ├── router/        # Vue Router config
│   ├── views/         # Page components
│   ├── types/         # TypeScript type definitions
│   ├── utils/         # Utility functions
│   └── i18n/          # Internationalization
```

### TypeScript Style

- **Explicit Types**: Add parameter and return types to exported functions
- **Interfaces vs Types**: Use `interface` for object shapes; `type` for unions, intersections
- **No `any`**: Use `unknown` for external input, then narrow safely
- **Immutability**: Use spread operator for immutable updates

### Naming Conventions

| Element | Convention |
|---------|------------|
| Components | PascalCase (`.vue` files) |
| Composables | camelCase, prefixed with `use` (e.g., `useAuth`) |
| Types/Interfaces | PascalCase |
| Variables/Functions | camelCase |
| Constants | SCREAMING_SNAKE_CASE |

### Vue Component Patterns

- Use `<script setup lang="ts">` for Vue 3 components
- Define component props with named `interface`
- Type callback props explicitly
- Use shadcn-vue + Radix Vue for UI components

### Formatting (Frontend)

- **Prettier**: `semi: false`, `singleQuote: true`, `printWidth: 100`
- **ESLint**: Flat config with `@vue/eslint-config-typescript`
- **EditorConfig**: 2-space indent, UTF-8 charset, LF line endings

### ESLint Configuration

```javascript
// Key rules from eslint.config.ts
'vue/multi-word-component-names': ['error', { ignores: ['Accordion', 'Alert', 'Avatar', ...] }]
```

---

## Shared Conventions

### API Response Format

All API responses use the `Result<T>` wrapper:

```json
{
  "code": 0,           // 0 = success, non-zero = error
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

### Error Handling

- Backend: BusinessException with ErrorCode enum
- Frontend: Request utility unwraps responses, returns `response.data` directly
- Use try-catch with async/await, narrow `unknown` errors safely

### Authentication Flow

- JWT tokens stored in httpOnly cookies
- CSRF token required for state-changing requests (POST, PUT, PATCH, DELETE)
- Frontend reads CSRF from localStorage, sends as `X-CSRF-Token` header

---

## Database

- **ORM**: MyBatis-Plus
- **Migrations**: Flyway (managed by `db-manager` Python CLI)
- **Naming**: Snake_case for MySQL columns, camelCase for Java fields
- **Migrations Location**: `db-manager/migrations/`

### Migration File Naming

```
V{version}__{description}.sql
```

Example: `V1__users_and_submissions.sql`

---

## Dependencies

### Backend Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.2.5 | Application framework |
| MyBatis-Plus | 3.5.16 | ORM |
| Redisson | 4.3.1 | Distributed locks, Redis |
| jjwt | 0.13.0 | JWT handling |
| Lombok | 1.18.44 | Boilerplate reduction |
| MapStruct | 1.6.3 | DTO mapping |
| Hutool | 5.8.44 | Java utilities |
| springdoc | 2.6.0 | OpenAPI/Swagger |

### Frontend Key Dependencies

| Dependency | Purpose |
|------------|---------|
| Vue 3.5 | UI framework |
| Pinia | State management |
| Vue Router | Routing |
| Tailwind CSS v4 | Styling |
| Axios | HTTP client |
| Zod | Schema validation |
| VeeValidate | Form validation |
