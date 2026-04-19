# Coding Conventions

**Analysis Date:** 2026-04-19

## Languages

**Backend:**
- Java 17 - Spring Boot 3.2.5

**Frontend:**
- TypeScript ~6.0.3 - Vue 3 (Console and Management)
- CSS Framework: Tailwind CSS v4 with `@tailwindcss/vite` plugin

## Formatting

**Frontend (ESLint + Prettier):**
- ESLint 9.x (Console), 10.x (Management) with `@vue/eslint-config-typescript`
- Prettier configuration (`management/.prettierrc.json`):
  - `semi: false`
  - `singleQuote: true`
  - `printWidth: 100`

**Backend (Java):**
- No explicit formatter configured in pom.xml
- Lombok for reducing boilerplate
- MapStruct for DTO mapping

## Naming Conventions

**Files:**
- Vue components: `PascalCase.vue` (e.g., `UserProfileView.vue`, `DataTable.vue`)
- TypeScript files: `camelCase.ts` (e.g., `useRetry.ts`, `auth.spec.ts`)
- Java classes: `PascalCase.java`

**Functions/Variables:**
- TypeScript: `camelCase`
- Java: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE` (both)

**Types/Interfaces:**
- TypeScript: `PascalCase` (e.g., `interface UserProfile`, `type UserRole`)
- Java: `PascalCase` (classes, interfaces, records)

**Path Aliases:**
- Frontend: `@` maps to `./src/` (e.g., `@/stores/auth`, `@/components/ui/button`)

## Import Organization

**Frontend (Vue/TypeScript):**
```typescript
// 1. Vue core
import { ref, computed, watch } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";

// 2. External libraries
import axios from "axios";
import { useDebounceFn } from "@vueuse/core";

// 3. Internal - aliased (@)
import { useAuthStore } from "@/stores/auth";
import { apiGet, apiPost } from "@/utils/request";
import type { User } from "@/types/auth";

// 4. UI components
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

// 5. Icons
import { Trophy, Flame, Target } from "lucide-vue-next";
```

## Error Handling

**Backend (Spring Boot):**
- `Result<T>` wrapper class for all API responses
- Structure: `{ code: number, message: string, data: T, traceId: string }`
- `code: 0` indicates success; non-zero indicates error
- `BusinessException` with `ErrorCode` enum for domain errors
- Global exception handler in `com.ulticode.common.exception`

**Frontend (Vue/TypeScript):**
- Stores handle error states with `status: 'idle' | 'loading' | 'ready' | 'error'`
- API errors propagate as thrown exceptions
- CSRF token management for authentication errors

## State Management

**Frontend (Pinia):**
- Stores in `src/stores/` directory
- Setup stores using `defineStore` with composition API
- Example: `useAuthStore`, `useRecommendationStore`

## Component Patterns

**Vue Components:**
- Single File Components (`.vue` files) with `<script setup lang="ts">`
- Props defined with `defineProps<Props>()` or `withDefaults`
- Emits defined with `defineEmits<Emits>()`

**UI Components:**
- Base components in `src/components/ui/` (Button, Dialog, etc.)
- Compound components organized in subdirectories (e.g., `accordion/Accordion.vue`)
- Feature-specific components in `src/components/{feature}/`

## API Patterns

**Frontend Request Utility:**
```typescript
// src/utils/request.ts
import { apiGet, apiPost } from "@/utils/request";

// Usage
const user = await apiGet<User>("/users/u-admin-001/stats");
await apiPost("/auth/login", { username, password });
```

**Response Format:**
```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

## Logging

**Backend:**
- SLF4J with Logback (Spring Boot default)
- Structured logging with trace IDs for request tracking

**Frontend:**
- No `console.log` statements in production code
- PWA update prompts handled via `virtual:pwa-register`

## Module Structure

**Backend Module Pattern:**
```
backend-spring/src/main/java/com/ulticode/
├── common/           # Shared utilities, configs, exceptions
│   ├── response/     # Result wrapper, PageResult
│   ├── exception/    # GlobalExceptionHandler, BusinessException
│   ├── config/       # SecurityConfig, WebConfig, RedisConfig
│   └── annotation/   # @CurrentUser, @RequireRole, @RateLimit
├── modules/          # Feature modules
│   ├── auth/         # Authentication, login, OAuth
│   ├── user/         # User CRUD, profile
│   └── ...
└── security/         # JWT filters, CSRF service
```

---

*Convention analysis: 2026-04-19*
