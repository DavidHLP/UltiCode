# External Integrations

## Overview

UltiCode integrates with multiple external services, APIs, and third-party libraries. This document catalogs all external dependencies and their usage.

---

## Authentication & OAuth

### GitHub OAuth
- **Purpose**: Social login authentication
- **Configuration**: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
- **Redirect URI**: `http://localhost:9001/auth/github/callback`
- **Status**: Configured (credentials not provided)

### Google OAuth
- **Purpose**: Social login authentication
- **Configuration**: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- **Redirect URI**: `http://localhost:9001/auth/google/callback`
- **Status**: Configured (credentials not provided)

---

## Payment Processing

### Stripe
- **Purpose**: Payment processing for premium subscriptions
- **Configuration**:
  - `STRIPE_SECRET_KEY`
  - `STRIPE_WEBHOOK_SECRET`
  - `STRIPE_PRICE_PREMIUM_MONTHLY`
  - `STRIPE_PRICE_PREMIUM_YEARLY`
- **Status**: Configured (test keys not provided)
- **Features**: Subscription billing, webhook handling

---

## Email Services

### SMTP
- **Purpose**: Transactional emails (password reset, notifications)
- **Configuration**:
  - `SMTP_HOST`
  - `SMTP_PORT` (587)
  - `SMTP_USER`
  - `SMTP_PASSWORD`
- **Status**: Configurable via `EMAIL_ENABLED` flag
- **Backend Support**: Spring Boot Mail starter

---

## Search

### MeiliSearch
- **Purpose**: Full-text search for problems, users, and content
- **Java Client**: meilisearch-java 0.20.0
- **HTTP Client**: OkHttp 5.3.2
- **Status**: Integrated in backend (host configurable)
- **Usage**: Problem search, user search, content discovery

---

## Recommendation Service

### Apache Spark
- **Purpose**: Offline batch processing for recommendation engine
- **Version**: 3.5.1
- **Language**: Scala 2.13.12
- **Components**:
  - Spark Core
  - Spark SQL
  - Spark MLlib
- **Status**: Modular component (recommend-spark)

### Dubbo3 RPC
- **Purpose**: Inter-service communication between backend and recommendation
- **Version**: 3.2.14
- **Registry**: Nacos (for service discovery)
- **Protocol**: Dubbo protocol (port 20881)
- **Status**: Integrated, requires Nacos

---

## Service Discovery & Configuration

### Nacos
- **Purpose**: Service registration, discovery, and configuration management
- **Version**: 2.3.2
- **Ports**: 28848 (HTTP), 29848 (gRPC)
- **Mode**: Standalone (dev), clustered (prod)
- **Services Registered**:
  - recommend-provider
  - recommend-web
  - backend (Spring Boot)
- **Configuration**: Shared application.yml via Nacos

---

## Code Execution & Sandboxing

### Judge Container
- **Purpose**: Secure code execution in isolated containers
- **Configuration**:
  - `JUDGE_CONTAINER_ENABLED`
  - `JUDGE_CONTAINER_IMAGE` (default: ulticode-judge:latest)
  - `JUDGE_CONTAINER_POOL_SIZE` (default: 5)
  - `JUDGE_CONTAINER_MAX_CONTAINERS` (default: 10)
  - `DOCKER_SOCKET_PATH` (/var/run/docker.sock)
- **Limits**:
  - Default time limit: 2000ms
  - Default memory limit: 256MB
- **Status**: Configurable Docker-based execution

---

## Caching & Session Store

### Redis
- **Purpose**: Distributed caching, session storage, rate limiting
- **Version**: 7-alpine
- **Ports**: 26379 (external), 6379 (internal)
- **Clients**:
  - Redisson 4.3.1 (backend-spring)
  - Jedis 5.1.0 (recommendation service)
- **Features Used**:
  - Distributed locks (Redisson)
  - Cache abstraction (Spring Cache)
  - Session management
  - Rate limiting

---

## Database

### MySQL
- **Purpose**: Primary relational database
- **Version**: 9.1
- **Port**: 23306 (external), 3306 (internal)
- **Connector**: mysql-connector-j
- **ORM**: MyBatis-Plus 3.5.16
- **Migrations**: Flyway (via db-manager Python CLI)

---

## API Documentation

### SpringDoc OpenAPI (Swagger)
- **Purpose**: Interactive API documentation
- **Version**: 2.6.0
- **UI Path**: `/swagger-ui.html`
- **API Docs**: `/api-docs`
- **Note**: Version 2.6.0 (incompatible with Spring Boot 3.2.5's springdoc 2.7.0)

---

## Frontend Dependencies

### HTTP Client
- **Axios**: 1.13.2
- **Purpose**: API requests from Vue frontends

### Real-time Communication
- **STOMP.js**: 7.3.0 (WebSocket messaging)
- **SockJS Client**: 1.6.1 (WebSocket polyfill)

### Code Editor
- **Monaco Editor**: 0.52.2
- **Purpose**: In-browser code editing (VS Code engine)

### Math Rendering
- **KaTeX**: 0.16.25
- **Purpose**: LaTeX math rendering in problem descriptions

### Syntax Highlighting
- **Highlight.js**: 11.11.1
- **Purpose**: Code syntax highlighting

### Charts
- **ECharts**: 6.0.0 (console only)
- **Unovis**: 1.6.2

### PWA
- **Workbox**: 7.4.0 (service worker)
- **Vite PWA Plugin**: 1.2.0

### Internationalization
- **Vue I18n**: 11.3.2
- **Intl Date**: 3.10.0 / 3.7.0

### Icons
- **Lucide Vue Next**: 0.552.0 / 0.562.0
- **Tabler Icons Vue**: 3.36.1
- **Unplugin Icons**: 23.0.1

### Form Validation
- **Vee Validate**: 4.15.1 (management)
- **Zod**: 3.25.76 (validation schemas)

### Storage
- **IndexedDB (idb)**: 8.0.3 (client-side storage)

---

## Build & Development Tools

### Package Managers
- **pnpm**: 9 (frontend monorepo)
- **Maven**: 3.9+ (backend via mvnw wrapper)

### Build Tools
- **Vite**: 8.0.8
- **Rollup**: Via Vite
- **Tailwind CSS**: 4.1.17 (via @tailwindcss/vite)

### Testing
- **Vitest**: 4.1.4 / 4.0.15 (frontend unit testing)
- **Spring Boot Test**: (backend testing)
- **TestContainers**: 1.21.4 (integration testing)
- **JaCoCo**: 0.8.12 (code coverage)
- **Vue Test Utils**: 2.4.6

### Linting
- **ESLint**: 9.30.1 / 10.2.1
- **Prettier**: 3.8.3
- **Knip**: 6.4.1 (unused code detection)

### Dev Server
- **Vite Dev Server**: Ports 9002 (console), 9003 (management)

---

## Third-Party Libraries (Backend)

### Security
- **OWASP Encoder**: 1.4.0 (XSS prevention)
- **jjwt**: 0.13.0 (JWT handling)

### Utilities
- **Hutool**: 5.8.44 (general Java utilities)
- **Lombok**: 1.18.44 (boilerplate reduction)
- **MapStruct**: 1.6.3 (bean mapping)

### JSON Processing
- **Jackson**: Via Spring Boot (included)
- **FastJSON**: Not used

---

## Deployment & Infrastructure

### Container Registry
- **GHCR**: GitHub Container Registry
- **Images**:
  - `ghcr.io/davidhlp/ulticode-public-next/backend`
  - `ghcr.io/davidhlp/ulticode-public-next/console`
  - `ghcr.io/davidhlp/ulticode-public-next/management`
- **Image Tagging**: SHA-based or `latest`

### Process Management
- **PM2**: Node.js process manager
- **Services Managed**:
  - ulticode-9001 (backend)
  - ulticode-9002 (console)
  - ulticode-9003 (management)
  - ulticode-9004 (recommend-provider)
  - ulticode-9005 (recommend-web)

### Web Server
- **nginx**: Alpine-based for frontend containers
- **Ports**: 8080 internal (maps to 9002/9003 external)
- **Features**: Gzip, security headers, SPA routing, API proxy

---

## Environment-Specific Configurations

### Development
- **Services**: All via docker-compose.yml
- **Hot Reload**: Vite dev server
- **Debug**: Backend on port 9001

### Production
- **Services**: Pre-built Docker images
- **Overrides**: docker-compose.prod.yml
- **Health Checks**: All services have healthchecks
- **Logging**: JSON file logging with rotation

---

## API Integration Points

### Backend API (port 9001)
- **Auth**: `/auth/*`
- **Users**: `/users/*`
- **Problems**: `/problems/*`
- **Submissions**: `/submissions/*`
- **Contests**: `/contests/*`
- **Forum**: `/forum/*`
- **Solutions**: `/solutions/*`
- **Notifications**: `/notifications/*`
- **Recommendations**: Via Dubbo RPC

### Frontend to Backend
- **Console**: Proxies `/api/*` to backend:9001
- **Management**: Proxies `/api/*` to backend:9001
- **CORS**: Configured for cross-origin requests

### Backend to Recommendation
- **Dubbo RPC**: Direct call to recommend-provider
- **Fallback**: Configurable URL on connection failure

---

## Environment Variables Summary

| Category | Variables |
|----------|-----------|
| Database | DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, MYSQL_ROOT_PASSWORD |
| Redis | REDIS_HOST, REDIS_PORT, REDIS_PASSWORD |
| Auth | JWT_SECRET, JWT_COOKIE_SECURE |
| Nacos | NACOS_HOST, NACOS_PORT, NACOS_USERNAME, NACOS_PASSWORD, NACOS_NAMESPACE, NACOS_GROUP |
| OAuth | GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, GITHUB_REDIRECT_URI, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI |
| Payment | STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, STRIPE_PRICE_* |
| Email | SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, EMAIL_ENABLED |
| Judge | JUDGE_CONTAINER_*, DOCKER_SOCKET_PATH |
| Recommendation | RECOMMENDATION_ENABLED, RECOMMENDATION_SERVICE_NAME, RECOMMENDATION_TIMEOUT, RECOMMENDATION_FALLBACK_URL |
| Frontend | VITE_API_BASE_URL |
