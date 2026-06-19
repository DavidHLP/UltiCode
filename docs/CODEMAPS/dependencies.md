---
title: Dependencies & External Services
tags: [reference, devops, architecture, living]
status: living
updated: 2026-06-19
owner: devops
generator: ecc:update-codemaps
---

# Dependencies & External Services

<!-- Generated: 2026-06-19 | Backend pom: 32 deps | Console pkg: 33+29 | Management pkg: 30+25 | Token estimate: ~850 -->

## Infrastructure (Docker Compose)

| Service     | Image                          | Dev Port  | Prod     | Notes                                     |
| ----------- | ------------------------------ | --------- | -------- | ----------------------------------------- |
| MySQL       | `mysql:9.1`                    | 23306     | internal | Loopback-only in dev; not in prod compose |
| Redis       | `redis:7-alpine`                | 26379     | internal | Sessions, CSRF, cache, outbox lease        |
| Nacos       | `nacos/nacos-server:v2.3.2`    | 28848     | internal | Config center + service discovery          |
| Backend     | `ghcr.io/.../backend:latest`   | 9001      | 9001     | Spring Boot fat-jar                        |
| Console     | `ghcr.io/.../console:latest`   | 9002      | 80/443   | Vue 3 SPA behind nginx                     |
| Management  | `ghcr.io/.../management:latest`| 9003      | 80/443   | Vue 3 SPA behind nginx                     |
| Sandbox     | `ulticode-sandbox:latest`      | n/a       | n/a      | Built from `docker/sandbox/` (D-form)      |

**Prod compose** (`docker-compose.prod.yml`) only exposes frontend + backend + Nacos; MySQL/Redis stay on internal network. **Dev override** (`docker-compose.dev.yml`) binds infra ports to `127.0.0.1` only.

## Backend (`backend-spring/pom.xml`, Spring Boot 3.2.5)

**Core**: spring-boot-starter-web · json · validation · security · data-redis · websocket · aop · mail · cache · actuator
**Observability**: micrometer-registry-prometheus
**Persistence**: mybatis-plus-spring-boot3-starter (3.5.16) · mybatis-plus-jsqlparser · mysql-connector-j · redisson-spring-boot-starter (4.3.1)
**Auth**: jjwt-api / jjwt-impl / jjwt-jackson (0.13.0)
**Search**: meilisearch-java
**API docs**: springdoc-openapi-starter-webmvc-ui (2.6.0)
**Caching**: caffeine
**Utilities**: lombok · hutool-all · mapstruct (1.6.3) · encoder · okhttp
**Test**: spring-boot-starter-test · spring-security-test · testcontainers (junit-jupiter, mysql, redis)

Build plugins: `spring-boot-maven-plugin` · `lombok` · `maven-compiler-plugin` (with `lombok-mapstruct-binding`) · `mapstruct-processor` · `jacoco-maven-plugin` · `maven-surefire-plugin` (excludes `*IT.java`)

## Frontend — Console (`console/package.json`)

**Runtime**: vue 3.5.34 · vue-router 5.0.7 · pinia 3.0.4 · vue-i18n 11.4.4 · axios 1.13 · @vueuse/core 14 · zod 3.25
**Editor + Realtime**: monaco-editor 0.55 · @monaco-editor/loader · @stomp/stompjs 7 · sockjs-client · @vue-dnd-kit/core 1.7 · @tanstack/vue-virtual 3
**UI**: @tailwindcss/vite 4 · tailwindcss 4 · reka-ui 2.9 · lucide-vue-next · @tabler/icons-vue · class-variance-authority · tailwind-merge · tw-animate-css
**Content**: markdown-it 14 · @mdit/plugin-katex · katex · highlight.js · dompurify
**Charts / Data**: echarts 6 · @unovis/vue · @unovis/ts · @internationalized/date · idb 8
**PWA**: vite-plugin-pwa · workbox-window 7

**Dev**: vite 8 · @vitejs/plugin-vue 6 · @vitejs/plugin-vue-jsx · typescript ~6 · vue-tsc 3 · eslint 10 · eslint-plugin-vue 10 · prettier 3.8 · vitest 4 · @vue/test-utils 2 · jsdom 29 · knip 6 · unplugin-icons · vite-plugin-vue-devtools 8 · @tailwindcss/typography · @iconify-json/{lucide,radix-icons}

## Frontend — Management (`management/package.json`)

**Runtime**: vue 3.5.34 · vue-router 5 · pinia 3 · vue-i18n 11 · axios · @vueuse/core · zod · @internationalized/date · @mdit/plugin-katex
**UI**: same Tailwind v4 / reka-ui / lucide / @tabler/icons-vue / cva / tailwind-merge / tw-animate-css
**Tables / DnD**: @tanstack/vue-table 8 · dnd-kit-vue · @dnd-kit/{abstract,dom,modifiers}
**Forms / Validation**: vee-validate 4 · @vee-validate/zod
**Dialogs / Menus**: embla-carousel-vue · vaul-vue · vue-input-otp
**Content / Charts**: markdown-it · highlight.js · katex · dompurify · echarts 6 · vue-echarts 8 · @unovis/vue

**Dev**: vite 8 · typescript ~6 · vue-tsc 3 · eslint 10 · prettier 3.8 · vitest 4 · @vue/test-utils 2 · jsdom · @playwright/test 1.60 · knip · vite-plugin-vue-devtools

## Shared Packages (`shared/*/package.json`)

| Package        | npm name                       | Purpose                                  |
| -------------- | ------------------------------ | ---------------------------------------- |
| `auth-core`    | `@ulticode/auth-core`          | Cookie/CSRF/auth-state composable        |
| `theme`        | `@ulticode/theme`              | DOM theme tokens, typography density     |
| `design-system`| `@ulticode/design-system`      | Shared CSS tokens (post-typography sync) |
| `badge-config` | `@ulticode/badge-config`       | Achievement/rating badge maps            |
| `sandbox-types`| `@ulticode/sandbox-types`      | Typed OJ sandbox DTOs (verdict, results) |

## Runtime Tooling

- **Java 17** (managed by vfox, see `.vfox.toml`)
- **Node** `^20.19.0 || >=22.12.0` (Vite 8 / pnpm 10 baseline)
- **pnpm 10** with per-package lockfiles (never install from root)
- **PM2** (`ecosystem.config.cjs`): 5 apps (4 long-lived + 1 one-shot)
- **Arthas 4.2.2** (downloaded to `tools/arthas-boot.jar`, gitignored) — MCP STATELESS via `infrastructure/arthas/arthas.properties` → `~/.arthas/lib/4.2.2/arthas/arthas.properties`
- **Sandbox harness** — Python 3.11 (Debian bookworm base, NOT host 3.14)

## CI (`.github/workflows/`)

- `backend-spring/**` or `init-db/**` → Maven ci + Flyway validation + Gitleaks
- `console/**` or `shared/**` → Console lint + type-check + test + prod-dep audit
- `management/**` or `shared/**` → Management lint + type-check + test + i18n audit + prod-dep audit
- `docker/**` → Docker build verification
- Fresh MySQL 9.1 container applies migrations on PR

## Notes / Gotchas

- `tools/arthas-boot.jar` is **gitignored** — fresh clone must re-download (or `codegraph init` will skip it)
- `pnpm dev` runs lint+type-check+format+test BEFORE Vite — bypass with direct `vite` for already-reviewed trees
- Backend DTO enum fields are raw `String` (TS enums defined in shared/); prefer backend enum migration when touching DTOs
- Management DataTable i18n requires both camelCase AND snake_case keys under `table.columnNames` in `management/src/i18n/locales/*/modules/table.ts`
