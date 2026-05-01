# Technology Stack Inventory

## Overview

UltiCode is an online programming platform (LeetCode-like) with a multi-service architecture:
- **Backend**: Spring Boot 3.2.5 (Java 17) with MyBatis-Plus
- **Frontend**: Vue 3 + Vite + Tailwind CSS (Console and Management)
- **Database**: MySQL 9.1
- **Cache**: Redis 7
- **Service Discovery**: Nacos 2.3.2
- **Recommendation**: Dubbo3 + Spark microservices

---

## Languages and Runtimes

| Component | Language | Version | Notes |
|-----------|----------|---------|-------|
| Backend | Java | 17 | LTS, eclipse-temurin |
| Recommendation Service | Java | 17 | LTS |
| Recommendation Spark | Scala | 2.13.12 | |
| Console Frontend | TypeScript | ~6.0.3 | Node ^20.19.0 |
| Management Frontend | TypeScript | ~6.0.3 | Node ^20.19.0 |
| Auth Core Shared | TypeScript | ~5.9.3 | |

---

## Backend (Spring Boot)

### Core Framework
- **Spring Boot**: 3.2.5
- **Spring Security**: Included with Spring Boot
- **Spring Web**: REST API framework
- **Spring Validation**: Bean validation (jakarta.validation)
- **Spring AOP**: 3.5.12
- **Spring Cache**: Caching abstraction
- **Spring WebSocket**: Real-time communication

### Data Access
- **MyBatis-Plus**: 3.5.16 (ORM)
- **MySQL Connector**: mysql-connector-j (runtime)
- **Redisson**: 4.3.1 (Redis client, distributed locks)

### Security
- **JWT**: jjwt 0.13.0 (authentication)
- **OWASP Encoder**: 1.4.0 (XSS output encoding)

### API Documentation
- **SpringDoc OpenAPI**: 2.6.0 (Swagger UI at /swagger-ui.html)

### Utilities
- **Hutool**: 5.8.44 (Java utility library)
- **Lombok**: 1.18.44 (code generation)
- **MapStruct**: 1.6.3 (object mapping)

### Testing
- **Spring Boot Test**: Included
- **Spring Security Test**: Included
- **TestContainers**: 1.21.4
- **JaCoCo**: 0.8.12 (code coverage)

### RPC
- **Dubbo3**: 3.2.14 (RPC framework for recommendation service)
- **Dubbo Registry Nacos**: 3.2.14

---

## Recommendation Service (Dubbo3 + Spark)

### Core
- **Spring Boot**: 3.2.5
- **Dubbo**: 3.2.14
- **Apache Spark**: 3.5.1
- **Scala**: 2.13.12

### Data
- **MySQL**: 8.0.33
- **MyBatis**: 3.0.3
- **Jedis**: 5.1.0 (Redis client)

### Modules
- recommend-api (1.0.0) - Dubbo service interfaces
- recommend-core - Core recommendation algorithms
- recommend-feature - Feature engineering
- recommend-provider - Dubbo service provider (port 20881)
- recommend-web - REST API (port 9005)
- recommend-spark - Spark offline processing

---

## Frontend (Vue 3)

### Core
- **Vue**: 3.5.25 (console) / 3.5.26 (management)
- **Vue Router**: 5.0.4
- **Pinia**: 3.0.4 (state management)
- **Vue I18n**: 11.3.2 (internationalization)

### Build
- **Vite**: 8.0.8
- **TypeScript**: ~6.0.3
- **Tailwind CSS**: 4.1.17 (@tailwindcss/vite)
- **Node**: ^20.19.0 || >=22.12.0

### UI Libraries
- **Reka UI**: 2.6.1 (console) / 2.7.0 (management)
- **Lucide Vue Next**: 0.552.0 / 0.562.0
- **Tabler Icons Vue**: 3.36.1
- **Class Variance Authority**: 0.7.1
- **Tailwind Merge**: 3.4.0

### Editor & Code Display
- **Monaco Editor**: 0.52.2
- **Highlight.js**: 11.11.1
- **Markdown-it**: 14.1.0
- **Markdown-it KaTeX**: 2.0.3
- **KaTeX**: 0.16.25

### Charts & Visualization
- **ECharts**: 6.0.0 (console only)
- **Unovis TS/Vue**: 1.6.2

### Real-time
- **STOMP.js**: 7.3.0 (WebSocket)
- **SockJS Client**: 1.6.1

### Additional
- **Axios**: 1.13.2 (HTTP client)
- **Vee Validate**: 4.15.1 (form validation, management)
- **Zod**: 3.25.76 (validation, management)
- **DOMPurify**: 3.3.3 / 3.3.1 (XSS sanitization)
- **date-fns**: 4.1.0 (management)
- **Embla Carousel Vue**: 8.6.0 (management)
- **Vue Input OTP**: 0.3.2 (management)
- **Vaul Vue**: 0.4.1 (management)
- **DnD Kit Vue**: 0.0.2 (management)
- **Vue Sonner**: 2.0.9 (toast notifications)
- **Workbox Window**: 7.4.0 (PWA)
- **TanStack Vue Virtual**: 3.13.18 (virtual scrolling)
- **TanStack Vue Table**: 8.21.3 (management)
- **Vue DND Kit Core**: 1.7.0
- **Internationalized Date**: 3.10.0 / 3.7.0

### Dev Tools
- **ESLint**: 9.30.1 (console) / 10.2.1 (management)
- **ESLint Plugin Vue**: 9.30.0 / ~10.8.0
- **Prettier**: 3.8.3
- **Vitest**: 4.1.4 / 4.0.15 (testing)
- **Vue Test Utils**: 2.4.6
- **JSDOM**: 29.0.2
- **Knip**: 6.4.1 (linting)
- **Vite Plugin PWA**: 1.2.0
- **Vite Vue Devtools**: 8.0.5
- **Unplugin Icons**: 23.0.1
- **Iconify Lucide**: 1.2.102
- **Iconify Radix**: 1.2.5
- **Tailwind Typography**: 0.5.19

---

## Databases

### MySQL
- **Version**: 9.1 (docker container)
- **Port**: 23306
- **Default Database**: ulticode
- **Driver**: mysql-connector-j

### Redis
- **Version**: 7-alpine
- **Port**: 26379
- **Client**: Redisson 4.3.1 (backend), Jedis 5.1.0 (recommendation)

---

## Service Discovery

### Nacos
- **Version**: 2.3.2
- **Port**: 28848 (HTTP), 29848 (gRPC)
- **Mode**: Standalone
- **Purpose**: Service registration and configuration management

---

## Container & Orchestration

### Docker Images
| Service | Base Image | Port |
|---------|------------|------|
| Backend | eclipse-temurin:17-jre-alpine | 9001 |
| Console | nginx:alpine | 9002 (8080 internal) |
| Management | nginx:alpine | 9003 (8080 internal) |
| Recommend Provider | (Spring Boot JAR) | 9004 / 20881 |
| Recommend Web | (Spring Boot JAR) | 9005 |

### Docker Compose
- **MySQL**: 9.1
- **Redis**: 7-alpine
- **Nacos**: 2.3.2

### Process Management
- **PM2**: Node.js process manager (ecosystem.config.cjs)
- **Docker Wrapper**: docker-wrapper.cjs

---

## Build Tools

### Backend
- **Maven**: 3.9+ (via mvnw wrapper)
- **Maven Compiler Plugin**: Java 17 target
- **Spring Boot Maven Plugin**: JAR packaging

### Frontend
- **pnpm**: 9 (package manager)
- **Vite**: 8.0.8 (build tool)
- **Rollup**: Bundler (via Vite)

### Shared
- **Node.js**: 20.19.0+ / 22.12.0+
- **corepack**: For pnpm enablement

---

## Project Structure

```
UltiCode-Public-Next/
├── backend-spring/           # Spring Boot backend (Java 17)
│   ├── src/main/java/
│   ├── src/test/java/
│   ├── pom.xml
│   └── Dockerfile
├── console/                 # User-facing Vue 3 frontend
│   ├── src/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── Dockerfile
├── management/              # Admin Vue 3 frontend
│   ├── src/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── Dockerfile
├── recommendation/         # Dubbo3 + Spark recommendation service
│   ├── recommend-api/       # Dubbo interfaces
│   ├── recommend-core/      # Core algorithms
│   ├── recommend-feature/   # Feature engineering
│   ├── recommend-provider/  # Dubbo provider
│   ├── recommend-web/       # REST API
│   ├── recommend-spark/     # Spark jobs
│   └── pom.xml
├── shared/                  # Shared TypeScript modules
│   └── auth-core/
├── docker/                  # Docker utilities
├── db-manager/             # Database migration tool (Python)
├── docker-compose.yml       # Development services
├── docker-compose.prod.yml  # Production services
└── ecosystem.config.cjs     # PM2 configuration
```

---

## Environment Configuration

### Key Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| DB_HOST | MySQL host | localhost |
| DB_PORT | MySQL port | 23306 |
| DB_USER | Database user | ulticode |
| DB_PASSWORD | Database password | (required) |
| REDIS_HOST | Redis host | localhost |
| REDIS_PORT | Redis port | 26379 |
| REDIS_PASSWORD | Redis password | (required) |
| JWT_SECRET | JWT signing secret | (required) |
| NACOS_HOST | Nacos host | localhost |
| NACOS_PORT | Nacos port | 28848 |
| NACOS_USERNAME | Nacos username | nacos |
| NACOS_PASSWORD | Nacos password | nacos |
| VITE_API_BASE_URL | Backend API URL | http://localhost:9001 |

---

## Ports Reference

| Service | Port | Protocol |
|---------|------|----------|
| Backend (Spring Boot) | 9001 | HTTP |
| Console Frontend | 9002 | HTTP (Vite Dev) |
| Management Frontend | 9003 | HTTP (Vite Dev) |
| Recommend Provider | 9004 | Dubbo (20881) |
| Recommend Web | 9005 | HTTP |
| MySQL | 23306 | TCP |
| Redis | 26379 | TCP |
| Nacos | 28848 | HTTP |
| Nacos gRPC | 29848 | gRPC |
