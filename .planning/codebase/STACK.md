# UltiCode Technology Stack

> Comprehensive analysis of all technologies, frameworks, and libraries used in the UltiCode project.

## Languages

| Language | Version | Usage |
|----------|---------|-------|
| **Java** | 17 (LTS) | Backend Spring Boot services, Recommendation services |
| **TypeScript** | ~6.0.3 | Frontend type safety (Vue 3 apps) |
| **JavaScript** | ES2022+ | Frontend runtime (Vue 3) |
| **Scala** | 2.13.12 | Spark ML jobs in recommendation service |
| **HTML/CSS** | - | Frontend templates and styling |
| **SQL** | - | Database migrations (Flyway) |

---

## Backend Frameworks

### Core Backend (backend-spring)

| Framework | Version | Purpose |
|-----------|---------|---------|
| **Spring Boot** | 3.2.5 | Core web framework |
| **Spring Web** | (from Spring Boot) | REST API controllers |
| **Spring Security** | (from Spring Boot) | Authentication, authorization |
| **Spring Validation** | (from Spring Boot) | Input validation |
| **Spring AOP** | 3.5.12 | Aspect-oriented programming |
| **Spring Cache** | (from Spring Boot) | Caching abstraction |
| **Spring WebSocket** | (from Spring Boot) | Real-time communication |
| **Spring Mail** | (from Spring Boot) | Email sending |

### Backend Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **MyBatis-Plus** | 3.5.16 | ORM / Database access layer |
| **Redisson** | 4.3.1 | Distributed locks, Redis client enhancement |
| **JJWT** | 0.13.0 | JWT token generation/validation |
| **SpringDoc OpenAPI** | 2.6.0 | Swagger/OpenAPI documentation |
| **MapStruct** | 1.6.3 | Object mapping (DTO to Entity) |
| **Hutool** | 5.8.44 | Java utility library |
| **OWASP Encoder** | 1.4.0 | XSS output encoding |
| **MeiliSearch SDK** | 0.20.0 | Full-text search integration |
| **OkHttp** | 5.3.2 | HTTP client for MeiliSearch |
| **Dubbo3** | 3.2.14 | RPC framework for recommendation service |
| **Testcontainers** | 1.21.4 | Integration testing with Docker |
| **Jacoco** | 0.8.12 | Code coverage |

### Recommendation Service (recommendation module)

| Framework | Version | Purpose |
|-----------|---------|---------|
| **Spring Boot** | 3.2.5 | Web framework for provider/web |
| **Dubbo3** | 3.2.14 | RPC between services |
| **Apache Spark** | 3.5.1 | Offline ML batch processing |
| **Jedis** | 5.1.0 | Redis client for feature store |
| **MyBatis** | 3.0.3 | Database access (recommendation) |
| **MySQL Connector** | 8.0.33 | Database driver |
| **Jackson** | 2.17.0 | JSON serialization |
| **Caffeine** | (via Spring Cache) | Local in-memory cache |
| **Scala Compiler** | 2.13.12 | Compiling Spark Scala jobs |

---

## Frontend Frameworks

### Console (User-facing application) - Port 9002

| Technology | Version | Purpose |
|------------|---------|---------|
| **Vue** | 3.5.25 | UI framework |
| **Vite** | 8.0.8 | Build tool and dev server |
| **TypeScript** | ~6.0.3 | Type safety |
| **Tailwind CSS** | 4.1.17 | Utility-first CSS framework |
| **Pinia** | 3.0.4 | State management |
| **Vue Router** | 5.0.4 | Client-side routing |
| **Vue I18n** | 11.3.2 | Internationalization |
| **Axios** | 1.13.2 | HTTP client |
| **Monaco Editor** | 0.52.2 | Code editor (like VS Code) |
| **ECharts** | 6.0.0 | Chart visualization |
| **Highlight.js** | 11.11.1 | Syntax highlighting |
| **KaTeX** | 0.16.25 | Math rendering |
| **Markdown-it** | 14.1.0 | Markdown parsing |
| **DOMPurify** | 3.3.3 | HTML sanitization |
| **Reka UI** | 2.6.1 | UI component primitives |
| **Radix Vue** | (via Reka UI) | Headless UI components |
| **Lucide Vue Next** | 0.552.0 | Icon library |
| **Class Variance Authority** | 0.7.1 | CSS class variance |
| **clsx** | 2.1.1 | Class name utility |
| **Tailwind Merge** | 3.4.0 | Tailwind class merging |
| **TanStack Virtual** | 3.13.18 | Virtual scrolling |
| **Unovis** | 1.6.2 | Charts and data vis |
| **Vue DnD Kit** | 1.7.0 | Drag and drop |
| **Vue Sonner** | 2.0.9 | Toast notifications |
| **STOMP.js** | 7.3.0 | WebSocket messaging |
| **SockJS Client** | 1.6.1 | WebSocket polyfill |
| **Workbox Window** | 7.4.0 | PWA service worker |
| **IDB** | 8.0.3 | IndexedDB wrapper |
| **Internationalized Date** | 3.10.0 | Date formatting |

### Console Dev Dependencies

| Tool | Version | Purpose |
|------|---------|---------|
| **ESLint** | 9.30.1 | Linting |
| **ESLint Plugin Vue** | 9.30.0 | Vue-specific linting |
| **Prettier** | 3.8.3 | Code formatting |
| **Vue TSConfig** | 0.9.1 | TypeScript config |
| **TSConfig Node22** | 22.0.5 | Node TS config |
| **Vitest** | 4.1.4 | Unit testing |
| **Vue Test Utils** | 2.4.6 | Vue component testing |
| **JS DOM** | 29.0.2 | DOM testing |
| **Knip** | 6.4.1 | Linting/unused code |
| **Vite Plugin PWA** | 1.2.0 | PWA support |
| **Vite Plugin Vue DevTools** | 8.0.5 | Vue debugging |
| **Unplugin Icons** | 23.0.1 | Icon auto-import |
| **Jiti** | 2.6.1 | TypeScript execution |
| **Tailwind Typography** | 0.5.19 | Prose styling |
| **Iconify Lucide** | 1.2.102 | Lucide icons |
| **Iconify Radix** | 1.2.5 | Radix icons |

### Management (Admin Dashboard) - Port 9003

| Technology | Version | Purpose |
|------------|---------|---------|
| **Vue** | 3.5.26 | UI framework |
| **Vite** | 8.0.8 | Build tool |
| **TypeScript** | ~6.0.3 | Type safety |
| **Tailwind CSS** | 4.1.18 | CSS framework |
| **Pinia** | 3.0.4 | State management |
| **Vue Router** | 5.0.4 | Routing |
| **Vue I18n** | 11.3.2 | i18n |
| **Axios** | 1.13.2 | HTTP client |
| **Vee Validate** | 4.15.1 | Form validation |
| **Zod** | 3.25.76 | Schema validation |
| **Reka UI** | 2.7.0 | UI primitives |
| **TanStack Vue Table** | 8.21.3 | Table component |
| **Embla Carousel Vue** | 8.6.0 | Carousel |
| **Vue Input OTP** | 0.3.2 | OTP input |
| **Vaul Vue** | 0.4.1 | Drawer component |
| **DnD Kit Vue** | 0.0.2 | Drag and drop |
| **Date FNS** | 4.1.0 | Date utilities |
| **Highlight.js** | 11.11.1 | Syntax highlighting |
| **Markdown-it** | 14.1.0 | Markdown parsing |
| **DOMPurify** | 3.3.1 | HTML sanitization |
| **Lucide Vue Next** | 0.562.0 | Icons |
| **Class Variance Authority** | 0.7.1 | CSS variance |
| **clsx** | 2.1.1 | Class utility |

### Management Dev Dependencies

| Tool | Version | Purpose |
|------|---------|---------|
| **ESLint** | 10.2.1 | Linting |
| **ESLint Plugin Vue** | ~10.8.0 | Vue linting |
| **Prettier** | 3.8.3 | Formatting |
| **Vitest** | 4.0.15 | Testing |
| **Vitest Coverage V8** | 4.1.4 | Coverage |
| **Vue TSConfig** | 0.9.1 | TS config |
| **TSConfig Node24** | 24.0.3 | Node TS config |
| **Knip** | 6.4.1 | Linting |

---

## Databases

| Database | Version | Usage |
|----------|---------|-------|
| **MySQL** | 9.1 | Primary application database |
| **Flyway** | 10.17.0 | Database migrations (via db-manager CLI) |

---

## Caches

| Technology | Usage |
|------------|-------|
| **Redis** | Session storage, caching, distributed locks (via Redisson) |
| **Caffeine** | Local in-memory cache (recommendation provider) |
| **Spring Cache** | Caching abstraction layer |

---

## Message Queues / Real-time

| Technology | Purpose |
|------------|---------|
| **WebSocket (STOMP)** | Real-time notifications, contest updates |
| **SockJS** | WebSocket fallback |
| **Server-Sent Events** | (implied via WebSocket handlers) |

---

## Build Tools

| Tool | Usage |
|------|-------|
| **Maven** | Backend Java/Scala build (via `./mvnw` wrapper) |
| **pnpm** | Frontend package management |
| **Vite** | Frontend bundler and dev server |
| **npm-run-all2** | Running multiple npm scripts |
| **GitHub Actions** | CI/CD pipelines |

---

## DevOps / Infrastructure

### Containerization

| Image | Version | Usage |
|-------|---------|-------|
| **eclipse-temurin 17-jdk-alpine** | Build stage for backend |
| **eclipse-temurin 17-jre-alpine** | Runtime for backend |
| **node:22-alpine** | Build stage for frontends |
| **nginx:alpine** | Production frontend serving |
| **mysql:9.1** | Development database |
| **redis:7-alpine** | Development Redis |
| **nacos/nacos-server** | v2.3.2 - Service discovery |

### Orchestration

| Tool | Purpose |
|------|---------|
| **Docker Compose** | Local development services |
| **PM2** | Process manager for local development |
| **GitHub Actions** | CI/CD pipelines |

### Process Manager

| Service | Port | Command |
|---------|------|---------|
| Backend (Spring Boot) | 9001 | PM2 ecosystem |
| Console (Vite) | 9002 | PM2 ecosystem |
| Management (Vite) | 9003 | PM2 ecosystem |
| Recommend Provider | 9004 | PM2 ecosystem |
| Recommend Web | 9005 | PM2 ecosystem |

---

## Testing

### Backend Testing

| Framework | Purpose |
|-----------|---------|
| **JUnit 5** | Unit testing (via Spring Boot Test) |
| **Mockito** | Mocking |
| **Testcontainers** | Integration testing with MySQL, Redis |
| **Jacoco** | Code coverage |

### Frontend Testing

| Framework | Purpose |
|----------|---------|
| **Vitest** | Unit/Component testing |
| **Vue Test Utils** | Vue component testing |
| **JS DOM** | DOM environment |
| **Vitest Coverage V8** | Coverage reporting |

---

## API Documentation

| Tool | Path |
|------|------|
| **SpringDoc OpenAPI** | `/api-docs` (Swagger UI at `/swagger-ui.html`) |

---

## Service Discovery

| Technology | Version | Purpose |
|-----------|---------|---------|
| **Nacos** | 2.3.2 | Service registration and discovery for Dubbo3 |

---

## Authentication & Security

| Technology | Purpose |
|-----------|---------|
| **JWT** | Access tokens (15min) + Refresh tokens (7 days) |
| **Spring Security** | Authentication/Authorization framework |
| **httpOnly Cookies** | Secure token storage |
| **CSRF** | Protection via CSRF tokens |
| **OAuth 2.0** | GitHub and Google social login |

---

## External API Integrations

| Service | Purpose | Configuration |
|---------|---------|---------------|
| **GitHub OAuth** | Social login | `GITHUB_CLIENT_ID/SECRET` |
| **Google OAuth** | Social login | `GOOGLE_CLIENT_ID/SECRET` |
| **MeiliSearch** | Full-text search | Optional, via `MEILISEARCH_HOST` |
| **Letta AI** | AI features | Root `package.json` dependency |
| **Stripe** | Payment processing | Optional, for premium subscriptions |

---

## Project Structure Summary

```
UltiCode/
├── backend-spring/          # Spring Boot 3.2.5 (Java 17) - Port 9001
│   ├── src/main/java/com/ulticode/modules/  # Feature modules
│   │   ├── auth/           # Authentication
│   │   ├── user/           # User management
│   │   ├── problem/        # Problems & submissions
│   │   ├── contest/        # Contests
│   │   ├── submission/     # Code execution
│   │   ├── websocket/     # Real-time
│   │   ├── recommendation/ # (references Dubbo service)
│   │   ├── vote/           # Voting
│   │   ├── subscription/   # Subscriptions
│   │   ├── moderation/     # Content moderation
│   │   ├── backup/         # Database backup
│   │   ├── notification/    # Notifications
│   │   ├── search/         # Search
│   │   └── follow/         # Social features
│   └── src/main/resources/
│       ├── application.yml          # Main config
│       ├── application-dev.yml     # Dev overrides
│       ├── application-prod.yml    # Prod overrides
│       └── application-ci.yml      # CI overrides
├── recommendation/         # Dubbo3 + Spark microservices
│   ├── recommend-api/       # Dubbo service interfaces
│   ├── recommend-core/      # Core recommendation algorithms
│   ├── recommend-feature/   # Feature engineering
│   ├── recommend-provider/  # Dubbo service impl (port 9004)
│   ├── recommend-web/       # REST API (port 9005)
│   └── recommend-spark/     # Spark offline jobs
├── console/                # Vue 3 + Vite (port 9002)
│   └── src/
├── management/            # Vue 3 + Vite admin (port 9003)
│   └── src/
├── shared/                # Shared frontend code
├── db-manager/           # Flyway migration tool (Python)
├── docker/                # Docker configs
│   └── sandbox/          # Code execution sandbox
├── .github/workflows/    # CI/CD pipelines
│   ├── ci.yml            # Continuous integration
│   ├── cd-deploy.yml     # Deployment
│   └── cd-rollback.yml   # Rollback
├── docker-compose.yml     # Development services
├── docker-compose.prod.yml # Production override
└── ecosystem.config.cjs   # PM2 process definitions
```
