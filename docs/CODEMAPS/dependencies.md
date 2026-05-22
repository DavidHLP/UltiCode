<!-- Generated: 2026-05-23 | Token estimate: ~750 -->

# Dependencies & Integrations

## Backend (backend-spring)

| Category | Technology | Version |
|----------|-----------|---------|
| Framework | Spring Boot | 3.2.5 |
| Runtime | Java | 17 |
| ORM | MyBatis-Plus | 3.5.16 |
| DTO Mapping | MapStruct | 1.6.3 |
| Auth | jjwt | 0.13.0 |
| Cache/Session | Redisson | 4.3.1 |
| API Docs | SpringDoc OpenAPI | 2.6.0 |
| RPC | Dubbo | 3.2.14 |
| Utilities | Hutool | 5.8.44 |
| Search | MeiliSearch SDK | 0.20.0 |
| HTTP Client | OkHttp | 5.3.2 |
| Security | OWASP Encoder | 1.4.0 |
| Testing | JUnit 5 + Testcontainers | 1.21.4 |
| Coverage | JaCoCo | 0.8.12 |

## Frontend (console + management)

| Category | Technology | Version |
|----------|-----------|---------|
| Framework | Vue | 3.5.x |
| Build | Vite | 8.0.x |
| State | Pinia | 3.0.4 |
| Routing | Vue Router | 5.0.4 |
| CSS | Tailwind CSS | v4.1.x |
| i18n | vue-i18n | 11.3.2 |
| UI | shadcn-vue (reka-ui), Radix Vue, Lucide icons |
| HTTP | Axios |
| PWA | vite-plugin-pwa + workbox (console only) |
| Testing | Vitest 4.x, jsdom |
| E2E | Playwright (management only) |
| Linting | ESLint 9.x (console) / 10.x (management) |
| Formatting | Prettier (semi: false, singleQuote, printWidth: 100) |
| Type Check | TypeScript ~6.0.3 |

## Shared

| Package | Version | Notes |
|---------|---------|-------|
| shared/auth-core | 0.0.1 | TS ~5.9.3, Vue composable for auth |

## Infrastructure

| Service | Image | Port |
|---------|-------|------|
| MySQL | mysql:9.1 | 23306 |
| Redis | redis:7-alpine | 26379 |
| Nacos | nacos:v2.3.2 | 28848 |

## Recommendation System

| Module | Purpose |
|--------|---------|
| recommend-api | Dubbo service interfaces + DTOs |
| recommend-core | Recall (CF/Hot/Content/ColdStart) → Rank (RuleRankStrategy) → Re-rank (Diversity/Freshness) |
| recommend-feature | ProblemFeatureExtractor, UserFeatureExtractor, FeatureStore |
| recommend-provider | Dubbo service provider (:20881), RedisRecommendationStore |
| recommend-web | REST API gateway (:9005) |
| recommend-spark | Apache Spark 3.5.1 offline batch (CFTrainingJob, SimilarityJob, OfflineFeatureJob) |

## Production Docker (docker-compose.prod.yml)

- Images from GHCR: `ghcr.io/davidhlp/ulticode-public-next/...`
- Backend: 1G RAM / 2 CPU, actuator healthcheck
- Console/Management: 256M / 0.5 CPU each
- Recommend-provider (:20881) + recommend-web (:9005) on `app-network`
- All services: `restart: unless-stopped`, JSON log driver with rotation
- **Known issue**: `app-network` referenced but not defined in compose files

## CI/CD

- GitHub Actions on push/PR to main
- Path-based change detection (backend, frontend, docker, testcontainers)
- Backend: Maven build + test (ci profile) + Flyway validation
- Frontend: lint + type-check + test
- Integration tests: MySQL 9.1 + Redis 7 via Testcontainers
