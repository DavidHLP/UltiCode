# Technology Stack

**Analysis Date:** 2026-04-19

## Languages

**Primary:**
- Java 17 - Backend (Spring Boot 3.2.5)
- TypeScript ~6.0.3 - Frontend (Vue 3 applications)

**Secondary:**
- Scala 2.13.12 - Spark recommendation service
- JavaScript - Build tooling, scripts

## Runtime

**Java/JVM:**
- Java 17 (LTS)
- Maven wrapper (`./mvnw`) for build

**Node.js:**
- Node 20.19+ / >=22.12.0 (per `package.json` engines)
- pnpm as package manager (monorepo structure)

## Frameworks

**Backend:**
- Spring Boot 3.2.5 - Core framework
- MyBatis-Plus 3.5.16 - ORM/data access
- Redisson 4.3.1 - Distributed locks, Redis client
- Dubbo 3.2.14 - RPC framework (recommendation service)

**Frontend (Console & Management):**
- Vue 3.5.x - UI framework
- Vite 8.x - Build tool
- Tailwind CSS 4.1.x - Styling
- Pinia 3.0.x - State management
- Vue Router 5.x - Routing
- Vue I18n - Internationalization

**Code Editor:**
- Monaco Editor 0.52.2 - Code editing component

**Data Visualization:**
- ECharts 6.0.0 - Charts
- @unovis/vue 1.6.2 - Data visualization

**Math/Text:**
- KaTeX 0.16.25 - Math rendering
- markdown-it 14.1.0 - Markdown parsing
- highlight.js 11.11.1 - Syntax highlighting

**Recommendation Service:**
- Apache Spark 3.5.1 - Distributed computing
- Dubbo 3.2.14 - RPC framework

## Key Dependencies

**Backend Critical:**
- `com.baomidou:mybatis-plus-spring-boot3-starter` 3.5.16 - ORM
- `org.redisson:redisson-spring-boot-starter` 4.3.1 - Redis/distributed locks
- `io.jsonwebtoken:jjwt-api` 0.13.0 - JWT authentication
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` 2.6.0 - API documentation
- `cn.hutool:hutool-all` 5.8.44 - Utility library
- `org.mapstruct:mapstruct` 1.6.3 - Object mapping
- `org.owasp.encoder:encoder` 1.4.0 - XSS prevention
- `com.meilisearch.sdk:meilisearch-java` 0.20.0 - Search client

**Frontend Critical:**
- `@vueuse/core` 14.1.0 - Vue composition utilities
- `@tanstack/vue-virtual` 3.13.18 - Virtual scrolling
- `axios` 1.13.2 - HTTP client
- `zod` 3.25.76 - Schema validation (management)
- `vee-validate` 4.15.1 + `@vee-validate/zod` 4.15.1 - Form validation

**WebSocket/Real-time:**
- `@stomp/stompjs` 7.3.0 - STOMP protocol
- `sockjs-client` 1.6.1 - WebSocket fallback

## Build & Dev Tools

**Backend:**
- Maven 3.x (via `./mvnw`)
- Lombok 1.18.44 - Code generation
- MapStruct 1.6.3 - Bean mapping

**Frontend:**
- Vite 8.x - Build/dev server
- Tailwind CSS 4.x - Utility CSS
- ESLint 9.x/10.x - Linting
- Prettier 3.8.3 - Code formatting
- Vitest 4.x - Unit testing
- vue-tsc 3.x - TypeScript checking

**Process Management:**
- PM2 - Service orchestration
- Docker Compose - Container orchestration

## Configuration

**Backend (`backend-spring/src/main/resources/application.yml`):**
- `spring.redis.*` - Redis connection
- `spring.datasource.*` - MySQL connection
- `jwt.*` - JWT token configuration
- `mybatis-plus.*` - ORM settings
- `springdoc.*` - Swagger/OpenAPI settings
- `meilisearch.*` - Search configuration
- `recommendation.*` - Recommendation service settings
- `code-execution.sandbox.*` - Docker sandbox config
- `spring.mail.*` - SMTP settings
- `oauth.github.*`, `oauth.google.*` - OAuth2 config
- `dubbo.*` - Dubbo RPC settings

**Frontend Vite:**
- `VITE_API_BASE_URL` - API endpoint
- Path alias `@` -> `./src`

**Environment Files:**
- `.env` - Root environment (gitignored)
- `.env.example` - Template (committed)
- `backend-spring/.env` - Backend secrets
- `console/.env.example` - Console template
- `management/.env.example` - Management template

## Platform Requirements

**Development:**
- Docker + Docker Compose (MySQL, Redis, Nacos)
- Node.js 20+ (frontend builds)
- Java 17+ (backend)
- Maven 3.x

**Production:**
- Java 17+ runtime
- MySQL 9.x
- Redis 7.x
- Nacos 2.3.2 (if recommendation service enabled)
- Docker (for code execution sandbox)

---

*Stack analysis: 2026-04-19*
