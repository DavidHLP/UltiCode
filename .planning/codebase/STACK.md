# Technology Stack

**Analysis Date:** 2026-04-22

## Languages

**Primary:**
- Java 17 - Backend (Spring Boot)
- TypeScript ~6.0.3 - Frontend (Vue 3 applications)

**Secondary:**
- Scala 2.13.12 - Spark recommendation service

## Runtime

**Backend:**
- Java 17 (LTS)
- Maven wrapper (`./mvnw`)

**Frontend:**
- Node.js ^20.19.0 || >=22.12.0
- pnpm - Monorepo package manager

## Frameworks

**Backend:**
- Spring Boot 3.2.5 - Core application framework
- Spring Security 3.2.5 - Authentication/authorization
- Spring WebSocket 3.2.5 - Real-time communication
- MyBatis-Plus 3.5.16 - ORM layer
- Dubbo 3.2.14 - RPC framework (recommendation service)

**Frontend Console (User-facing):**
- Vue 3.5.25 - UI framework
- Vite 8.0.8 - Build tool
- Tailwind CSS 4.1.17 - CSS framework
- Pinia 3.0.4 - State management
- Vue Router 5.0.4 - Routing
- Vue I18n 11.3.2 - Internationalization

**Frontend Management (Admin):**
- Vue 3.5.26 - UI framework
- Vite 8.0.8 - Build tool
- Tailwind CSS 4.1.18 - CSS framework
- Pinia 3.0.4 - State management
- Vue Router 5.0.4 - Routing
- VeeValidate 4.15.1 + Zod 3.25.76 - Form validation

**Recommendation Service:**
- Spring Boot 3.2.5 - Provider/Web services
- Apache Spark 3.5.1 - Offline ML computations
- Dubbo 3.2.14 - Service RPC

## Key Dependencies

**Backend:**
- MySQL Connector/J - MySQL driver
- Redisson 4.3.1 - Redis client (distributed locks, caching)
- JJWT 0.13.0 - JWT token handling
- SpringDoc OpenAPI 2.6.0 - Swagger/OpenAPI docs
- Hutool 5.8.44 - Java utilities
- MapStruct 1.6.3 - Object mapping
- OWASP Encoder 1.4.0 - XSS prevention
- MeiliSearch Java 0.20.0 - Search functionality
- Testcontainers 1.11.3 - Integration testing

**Frontend Console:**
- Monaco Editor 0.52.2 - Code editor
- KaTeX 0.16.25 - Math rendering
- Highlight.js 11.11.1 - Syntax highlighting
- Markdown-it 14.1.0 - Markdown parsing
- ECharts 6.0.0 - Charts/visualization
- Unovis 1.6.2 - Data visualization
- STOMP.js 7.3.0 - WebSocket client
- SockJS 1.6.1 - WebSocket polyfill
- Axios 1.13.2 - HTTP client
- shadcn-vue + Radix Vue - UI components
- Lucide Vue Next - Icons
- @vue/dnd-kit - Drag and drop
- TanStack Vue Virtual 3.13.18 - Virtual scrolling

**Frontend Management:**
- Axios 1.13.2 - HTTP client
- Embla Carousel Vue 8.6.0 - Carousel
- Vaul Vue 0.4.1 - Drawer component
- @tanstack/vue-table 8.21.3 - Tables
- Zod 3.25.76 - Schema validation

**Recommendation:**
- MyBatis 3.0.3 - ORM (recommendation modules)
- Jedis 5.1.0 - Redis client
- Scala Spark MLlib - Machine learning

## Testing

**Backend:**
- Spring Boot Test - Unit/integration testing
- JUnit 5 - Test framework
- Mockito - Mocking
- Testcontainers - Docker-based integration tests
- JaCoCo 0.8.12 - Code coverage

**Frontend:**
- Vitest 4.1.4 - Test runner
- Vue Test Utils 2.4.6 - Vue component testing
- jsdom 29.0.2 - DOM simulation
- @vitest/coverage-v8 4.1.4 - Coverage (management only)

## Build Tools

**Backend:**
- Maven 3 - Build tool (via mvnw wrapper)
- Lombok 1.18.44 - Code generation
- MapStruct 1.6.3 - Bean mapping

**Frontend:**
- Vite 8.0.8 - Build/dev server
- TypeScript ~6.0.3 - Type checking (vue-tsc)
- ESLint 9.30.1 (console) / 10.2.1 (management) - Linting
- Prettier 3.8.3 - Code formatting

**Process Management:**
- PM2 - Service orchestration (ecosystem.config.cjs)

## Database

**Primary:**
- MySQL 9.1 - Application database
  - Port: 23306
  - Connector: mysql-connector-j
  - ORM: MyBatis-Plus 3.5.16

**Migration:**
- Flyway - SQL migration management
- db-manager CLI - Python wrapper around Flyway
  - Command: `db-manager/.venv/bin/python -m db_manager.cli migrate`

## Cache & Sessions

**Redis:**
- Redis 7-alpine - Caching, sessions, rate limiting
- Port: 26379
- Client: Redisson 4.3.1 (Spring Boot)
- Client: Jedis 5.1.0 (recommendation service)

## Service Discovery

**Nacos:**
- Nacos 2.3.2 - Service registry for Dubbo
- Port: 28848 (HTTP), 29848 (gRPC)
- Mode: Standalone

## Configuration

**Environment:**
- `backend-spring/.env` - Backend environment variables
- Vite env vars (`VITE_` prefix) - Frontend configuration

**Key configs:**
- `JWT_SECRET` - Token signing (min 32 chars)
- `DATABASE_URL` - MySQL connection string
- `REDIS_HOST/PASSWORD` - Redis connection
- `NACOS_SERVER_ADDR` - Service discovery

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

*Stack analysis: 2026-04-22*
