<!-- Generated: 2026-05-19 | Files scanned: 2106 | Token estimate: ~650 -->

# Dependencies & Integrations

## Backend (Spring Boot 3.2.5 / Java 17)

| Category | Dependency | Version |
|----------|-----------|---------|
| ORM | MyBatis-Plus | 3.5.16 |
| DTO Mapping | MapStruct | 1.6.3 |
| Auth | jjwt | 0.13.0 |
| Cache | Redisson | 4.3.1 |
| API Docs | SpringDoc OpenAPI | 2.6.0 |
| RPC | Dubbo | 3.2.14 |
| Service Discovery | Nacos | 2.3.2 |
| Testing | JUnit 5, Testcontainers (MySQL, Redis), JaCoCo |

## Frontend (Vue 3.5 / TypeScript ~6)

| Category | Dependency | Version |
|----------|-----------|---------|
| Build | Vite | 8 |
| State | Pinia | 3 |
| Router | Vue Router | 5 |
| UI | shadcn-vue (reka-ui), Radix Vue | — |
| Icons | Lucide | — |
| i18n | vue-i18n | 11 |
| HTTP | Axios | — |
| CSS | Tailwind CSS | v4 |
| PWA | vite-plugin-pwa + workbox | — |
| Testing | Vitest 4, jsdom, Playwright (mgmt) | — |
| Linting | ESLint 9/10 (flat), Prettier | — |

## Recommendation System

| Component | Tech |
|-----------|------|
| API | recommend-api (Dubbo interfaces) |
| Core | recommend-core (recall → rank → rerank pipeline) |
| Feature | recommend-feature (user/problem feature extraction) |
| Provider | recommend-provider (Dubbo :20881) |
| Web | recommend-web (REST :9005) |
| Spark | recommend-spark (Scala 2.13, CF training, similarity) |

## External Services

- **OAuth**: GitHub, Google (login)
- **Email**: SMTP via EmailService
- **Sandbox**: Docker-based code execution (docker/initdb/)

## Docker Infrastructure

`docker-compose.yml`: MySQL 9.1, Redis 7, Nacos 2.3.2
`docker-compose.prod.yml`: Production overrides
Non-root containers (`appuser:appgroup`), multi-stage builds.
