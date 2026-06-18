<!-- Generated: 2026-06-18 | Token estimate: ~720 -->

# Dependencies & Integrations

## Backend (backend-spring)

| Category        | Technology                  | Version |
| --------------- | --------------------------- | ------- |
| Framework       | Spring Boot                 | 3.2.5   |
| Runtime         | Java                        | 17      |
| ORM             | MyBatis-Plus                | 3.5.16  |
| DTO Mapping     | MapStruct                   | 1.6.3   |
| Auth            | jjwt                        | 0.13.0  |
| Cache/Session   | Redisson                    | 4.3.1   |
| API Docs        | SpringDoc OpenAPI           | 2.6.0   |
| Utilities       | Hutool                      | 5.8.44  |
| Search          | MeiliSearch SDK             | 0.20.0  |
| HTTP Client     | OkHttp                      | 5.3.2   |
| Security        | OWASP Encoder               | 1.4.0   |
| Testing         | JUnit 5 + Testcontainers    | 1.21.4  |
| Coverage        | JaCoCo                      | 0.8.12  |
| **Removed**     | ~~Dubbo 3.2.14~~            | —       |

> Dubbo and the entire `recommend-*` module set (recommend-api / -core /
> -feature / -provider / -web / -spark) have been removed from active scope.
> The `:20881` (Provider) and `:9005` (Web) ports are no longer published.

## Frontend — Console

| Category        | Technology                       | Version  |
| --------------- | -------------------------------- | -------- |
| Framework       | Vue                              | ^3.5.34  |
| Build           | Vite                             | ^8.0.14  |
| State           | Pinia                            | ^3.0.4   |
| Routing         | Vue Router                       | ^5.0.7   |
| CSS             | Tailwind CSS                     | ^4.3.0   |
| i18n            | vue-i18n                         | ^11.4.4  |
| UI              | shadcn-vue (reka-ui), Lucide     | latest   |
| HTTP            | Axios                            | ^1.13.2  |
| Composables     | @vueuse/core                     | ^14.1.0  |
| Sanitize        | dompurify                        | ^3.4.5   |
| Charts          | echarts                          | ^6.1.0   |
| Data viz        | @unovis/ts + @unovis/vue         | ^1.6.2   |
| DnD             | @vue-dnd-kit/core                | 1.7.0    |
| Code editor     | @monaco-editor/loader            | ^1.7.0   |
| WebSocket       | @stomp/stompjs                   | ^7.3.0   |
| Virtual list    | @tanstack/vue-virtual            | ^3.13.18 |
| KaTeX           | @mdit/plugin-katex               | ^0.25.2  |
| Icons           | @tabler/icons-vue                | ^3.36.1  |
| PWA             | vite-plugin-pwa + workbox        | latest   |
| Testing         | Vitest 4.1.7, jsdom              | —        |
| Linting         | ESLint 10.4.0                    | —        |
| Formatting      | Prettier (semi:false, singleQuote, 100c) | — |
| Type Check      | TypeScript ~6.0.3                | —        |

## Frontend — Management

| Category        | Technology                       | Version  |
| --------------- | -------------------------------- | -------- |
| Framework       | Vue                              | ^3.5.34  |
| Build           | Vite                             | ^8.0.14  |
| State           | Pinia                            | ^3.0.4   |
| Routing         | Vue Router                       | ^5.0.4   |
| CSS             | Tailwind CSS                     | ^4.3.0   |
| i18n            | vue-i18n                         | ^11.4.4  |
| HTTP            | Axios                            | ^1.16.1  |
| Composables     | @vueuse/core                     | ^14.3.0  |
| Sanitize        | dompurify                        | ^3.4.5   |
| Data viz        | @unovis/ts + @unovis/vue         | ^1.6.5   |
| Data table      | @tanstack/vue-table              | ^8.21.3  |
| DnD             | @dnd-kit/abstract + @dnd-kit/dom + @dnd-kit/modifiers | 0.1.21 / 0.1.21 / 9.0.0 |
| Forms           | @vee-validate/zod                | ^4.15.1  |
| Date            | date-fns                         | ^4.3.0   |
| KaTeX           | @mdit/plugin-katex               | ^0.25.2  |
| Icons           | @tabler/icons-vue                | ^3.44.0  |
| E2E             | Playwright                       | latest   |
| Testing         | Vitest 4.1.7                     | —        |
| Linting         | ESLint 10.4.0                    | —        |
| Type Check      | TypeScript ~6.0.3                | —        |

## Shared

| Package                | Purpose                                                |
| ---------------------- | ------------------------------------------------------ |
| `shared/auth-core`      | Vue composable: cookie/CSRF/auth-state/permission      |
| `shared/badge-config`   | Achievement / badge token config (shared by both FEs)  |
| `shared/sandbox-types`  | Shared sandbox DTO types (backend + sandbox harness)   |
| `shared/theme`          | Theme tokens + Vue composable + `public/theme-bootstrap.js` source |
| `shared/design-system`  | Legacy `style.css` only — to be retired                |

## Infrastructure (docker-compose)

| Service | Image                       | Host Port  | Container            |
| ------- | --------------------------- | ---------- | -------------------- |
| MySQL   | mysql:9.1                   | 23306      | ulticode-mysql       |
| Redis   | redis:7-alpine              | 26379      | ulticode-redis       |
| Nacos   | nacos/nacos-server:v2.3.2   | 28848      | ulticode-nacos       |
| Sandbox | ulticode-sandbox:latest     | (internal) | (per submission)     |

Backend / Console / Management run as host processes under PM2 (dev) or as
container images from GHCR (prod). Base / production compose **does not**
publish the infra ports externally. Sandbox image is built locally from
`docker/sandbox/Dockerfile` and only used when `SANDBOX_ENABLED=true`.

## Runtime Tooling

- **PM2** (`ecosystem.config.cjs`): 5 apps — 4 long-running (`ulticode-9001`, `ulticode-9002`, `ulticode-9003`, `ulticode-arthas`) + one-shot `ulticode-init-db` migration task
- **Arthas 4.2.2** (`tools/arthas-boot.jar`): runtime JVM diagnostics
  - Wrapper: `scripts/start-arthas.sh` (port 8563, three-launcher mutex: PM2 / SessionStart hook / CLI)
  - MCP endpoint: `http://localhost:8563/mcp`

## CI/CD

- GitHub Actions on push / PR to main
- Path-based change detection (backend, frontend, docker, testcontainers)
- Backend: Maven build + test (ci profile) + Flyway validation
- Frontend: lint + type-check + test
- Integration tests: MySQL 9.1 + Redis 7 via Testcontainers
- Gitleaks scan on every push
