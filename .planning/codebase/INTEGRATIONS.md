# UltiCode Integrations

> Comprehensive analysis of all external and internal service integrations.

---

## 1. Database Integration

### MySQL

**Purpose:** Primary relational database for all application data

**Configuration File:** `backend-spring/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:23306}/${DB_NAME:ulticode}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: ${DB_USER:ulticode}
    password: ${DB_PASSWORD:ulticode}
```

**Connection Details:**
- Default Host: `localhost:23306` (via Docker port mapping)
- Database Name: `ulticode`
- User: `ulticode`
- Driver: `mysql-connector-j` (runtime scope)

**Modules Using MySQL:**
- `backend-spring` - All application data (users, problems, submissions, contests, etc.)
- `recommendation/recommend-feature` - Feature store data
- `recommendation/recommend-spark` - Offline batch processing

**ORM:** MyBatis-Plus 3.5.16

```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.ulticode.entity
  configuration:
    map-underscore-to-camel-case: true
```

**Migrations:** Flyway via `db-manager` Python CLI
- Migration files: `db-manager/migrations/`
- CLI: `python -m db_manager.cli migrate`

---

## 2. Cache Integration

### Redis

**Purpose:** Session storage, JWT token management, distributed locks, caching

**Configuration File:** `backend-spring/src/main/resources/application.yml`

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:26379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DB:0}
      timeout: 10000ms
      lettuce:
        pool:
          max-active: 8
          max-wait: -1ms
          max-idle: 8
          min-idle: 0
```

**Redisson Configuration (Advanced):**
```yaml
spring:
  redis:
    redisson:
      config: |
        singleServerConfig:
          address: "redis://${REDIS_HOST:localhost}:${REDIS_PORT:26379}"
          password: ${REDIS_PASSWORD:}
          database: ${REDIS_DB:0}
          connectionPoolSize: 64
          connectionMinimumIdleSize: 10
```

**Docker Service:**
```yaml
redis:
  image: redis:7-alpine
  ports:
    - "${REDIS_PORT:-26379}:6379"
  command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"]
```

**Modules Using Redis:**
- `backend-spring` - JWT sessions, distributed locks, caching
- `recommendation/recommend-provider` - Cache for recommendation results (Caffeine fallback)
- `recommendation/recommend-feature` - Feature store caching via Jedis

**Use Cases:**
1. **JWT Session Storage** - Refresh tokens stored in Redis with TTL
2. **Distributed Locks** - Redisson locks for concurrent operations
3. **Caching** - Spring Cache abstraction with Redis backend
4. **WebSocket Sessions** - Session management for real-time connections

---

## 3. Service-to-Service Communication

### Dubbo3 RPC (Recommendation Service)

**Purpose:** Communication between backend and recommendation microservice

**Configuration File:** `backend-spring/src/main/resources/application.yml`

```yaml
dubbo:
  application:
    name: ulticode-backend
    qos-enable: false
  registry:
    address: nacos://${NACOS_HOST:localhost}:${NACOS_PORT:28848}
    parameters:
      namespace: public
      group: DEFAULT_GROUP
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
  consumer:
    check: false
    timeout: 5000
    retries: 1
```

**Recommendation Provider Configuration:** `recommendation/recommend-provider/src/main/resources/application.yml`

```yaml
dubbo:
  application:
    name: recommend-provider
  protocol:
    name: dubbo
    port: 20881
  registry:
    address: nacos://${NACOS_HOST:localhost}:${NACOS_PORT:8848}
```

**Ports:**
- `recommend-provider`: 9004 (HTTP), 20881 (Dubbo RPC)
- `recommend-web`: 9005 (HTTP)

**Fallback Configuration:**
```yaml
recommendation:
  enabled: ${RECOMMENDATION_ENABLED:false}
  service-url: ${RECOMMENDATION_SERVICE_URL:}
  timeout: ${RECOMMENDATION_TIMEOUT:5000}
  nacos-enabled: ${RECOMMENDATION_NACOS_ENABLED:false}
  fallback-url: ${RECOMMENDATION_FALLBACK_URL:}
```

**Internal Dependency:**
```xml
<dependency>
  <groupId>com.ulticode</groupId>
  <artifactId>recommend-api</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## 4. Service Discovery

### Nacos

**Purpose:** Service registration and discovery for Dubbo3 microservices

**Docker Service:** `docker-compose.yml`

```yaml
nacos:
  image: nacos/nacos-server:v2.3.2
  ports:
    - "${NACOS_PORT:-28848}:8848"
    - "29848:9848"
  environment:
    MODE: standalone
    MYSQL_SERVICE_HOST: mysql
    MYSQL_SERVICE_DB_NAME: nacos_config
```

**Configuration:**
- Default Port: 28848
- Registry Address: `nacos://${NACOS_HOST:localhost}:${NACOS_PORT:28848}`
- Namespace: `public`
- Group: `DEFAULT_GROUP`

**Services Registered:**
- `recommend-provider` - Dubbo RPC service
- `recommend-web` - REST API (in some configurations)

---

## 5. Authentication & Authorization

### JWT Authentication

**Configuration File:** `backend-spring/src/main/resources/application.yml`

```yaml
jwt:
  secret: ${JWT_SECRET:}
  access-token:
    expiration: 900000  # 15 minutes
  refresh-token:
    expiration: 604800000  # 7 days
  cookie:
    access-token:
      name: access_token
      http-only: true
      secure: ${JWT_COOKIE_SECURE:false}
      same-site: lax
      path: /
      max-age: 900
    refresh-token:
      name: refresh_token
      http-only: true
      secure: ${JWT_COOKIE_SECURE:false}
      same-site: lax
      path: /
      max-age: 604800
```

**Token Flow:**
1. Login returns `access_token` (cookie) + `csrf_token` (response body)
2. Access token: 15 minutes expiry
3. Refresh token: 7 days expiry, stored in Redis
4. CSRF token required for state-changing requests

**Modules Using JWT:**
- `backend-spring` - All authenticated endpoints
- WebSocket authentication via `JwtChannelInterceptor`

### OAuth 2.0 (Social Login)

**GitHub OAuth:**
```yaml
oauth:
  github:
    client-id: ${GITHUB_CLIENT_ID:}
    client-secret: ${GITHUB_CLIENT_SECRET:}
    redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:9001/auth/github/callback}
    authorize-url: https://github.com/login/oauth/authorize
    token-url: https://github.com/login/oauth/access_token
    user-url: https://api.github.com/user
    scopes: user:email
```

**Google OAuth:**
```yaml
oauth:
  google:
    client-id: ${GOOGLE_CLIENT_ID:}
    client-secret: ${GOOGLE_CLIENT_SECRET:}
    redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:9001/auth/google/callback}
    authorize-url: https://accounts.google.com/o/oauth2/v2/auth
    token-url: https://oauth2.googleapis.com/token
    user-url: https://www.googleapis.com/oauth2/v2/userinfo
    scopes: email,profile
```

---

## 6. Search Integration

### MeiliSearch

**Purpose:** Full-text search for problems, users, and content

**Configuration File:** `backend-spring/src/main/resources/application.yml`

```yaml
meilisearch:
  enabled: ${MEILISEARCH_ENABLED:false}
  host: ${MEILISEARCH_HOST:}
  api-key: ${MEILISEARCH_API_KEY:}
```

**Library:** `meilisearch-java` v0.20.0 with OkHttp v5.3.2 client

**Modules Using MeiliSearch:**
- `backend-spring` - Search module for problem/user search

**Note:** Optional integration, disabled by default

---

## 7. Email Integration

### SMTP

**Purpose:** Password reset, notifications, email verification

**Configuration File:** `backend-spring/src/main/resources/application.yml`

```yaml
spring.mail:
  host: ${SMTP_HOST:localhost}
  port: ${SMTP_PORT:587}
  username: ${SMTP_USER:}
  password: ${SMTP_PASSWORD:}
  properties:
    mail:
      smtp:
        auth: true
        starttls:
          enable: true
```

**App Configuration:**
```yaml
app:
  email:
    enabled: ${EMAIL_ENABLED:false}
    from-name: ${EMAIL_FROM_NAME:UltiCode}
```

**Modules Using Email:**
- `backend-spring` - Auth module for password reset

---

## 8. Payment Integration

### Stripe (Optional)

**Purpose:** Premium subscription payments

**Configuration Files:**
- `backend-spring/.env.example`
- `backend-spring/src/main/resources/application.yml`

```yaml
# Stripe Configuration
STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY:}
STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET:}
STRIPE_PRICE_PREMIUM_MONTHLY=${STRIPE_PRICE_PREMIUM_MONTHLY:}
STRIPE_PRICE_PREMIUM_YEARLY=${STRIPE_PRICE_PREMIUM_YEARLY:}
```

**Usage:** Premium subscription billing (implementation scope varies)

---

## 9. Real-time Communication

### WebSocket (STOMP)

**Purpose:** Real-time notifications, contest updates, live rankings

**Configuration:** `backend-spring/src/main/resources/application.yml`

**WebSocket Config:** `backend-spring/src/main/java/com/ulticode/modules/websocket/config/`

**Key Components:**
1. `WebSocketConfig` - STOMP endpoints
2. `JwtChannelInterceptor` - JWT authentication for WebSocket
3. `NotificationWebSocketHandler` - User notifications
4. `ContestWebSocketHandler` - Contest real-time updates

**STOMP Libraries:**
- `console`: STOMP.js v7.3.0 + SockJS v1.6.1
- `management`: Same

**Ports:** Same as HTTP (9001)

---

## 10. Code Execution

### Docker Sandbox

**Purpose:** Secure code execution for submitted solutions

**Configuration File:** `backend-spring/src/main/resources/application.yml`

```yaml
code-execution:
  sandbox:
    enabled: ${SANDBOX_ENABLED:true}
    image: ${SANDBOX_IMAGE:ulticode-sandbox:latest}
    memory: ${SANDBOX_MEMORY:256m}
    cpus: ${SANDBOX_CPUS:1.0}
    timeout: ${SANDBOX_TIMEOUT:10}
    pids-limit: ${SANDBOX_PIDS_LIMIT:128}
    seccomp-profile-path: ${SANDBOX_SECCOMP_PROFILE:docker/sandbox/seccomp-profile.json}
    languages:
      java:
        timeout-seconds: 10
        memory: 256m
      python:
        timeout-seconds: 5
        memory: 128m
      # ... other languages
```

**Supported Languages:**
- Java (10s timeout)
- Python (5s)
- C (5s)
- C++ (5s)
- Go (8s)
- Rust (8s)
- JavaScript (3s)

**Sandbox Dockerfile:** `docker/sandbox/Dockerfile`

---

## 11. CI/CD Integration

### GitHub Actions

**Workflows:** `.github/workflows/`

#### CI Pipeline (`ci.yml`)
- Backend: Maven build + test (JUnit, Testcontainers)
- Frontend: Lint + Type check + Vitest
- Docker: Build verification
- Migrations: Flyway validation

**Environment:**
```yaml
JAVA_VERSION: '17'
NODE_VERSION: '22.x'
PNPM_VERSION: '10'
```

**Services Used:**
- MySQL 9.1 (test container)
- Redis 7 (test container)

#### CD Deploy Pipeline (`cd-deploy.yml`)

**Steps:**
1. SSH to deployment server
2. Run Flyway migrations
3. Docker pull and deploy
4. Health checks

**Environments:**
- Staging
- Production

**Deployment:**
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

## 12. Logging & Monitoring

### Logging

**Configuration:** `backend-spring/src/main/resources/application.yml`

```yaml
logging:
  level:
    root: INFO
    com.ulticode: INFO
    org.springframework.security: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

**Production:** `application-prod.yml`
```yaml
logging:
  level:
    com.ulticode: INFO
    org.springframework.security: WARN
```

### Health Checks

**Spring Actuator:** `backend-spring/src/main/resources/application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

**Health Endpoint:** `http://localhost:9001/actuator/health`

---

## 13. External AI Integration

### Letta AI

**Purpose:** AI-powered features (agents, memory)

**Root Dependency:** `package.json`
```json
{
  "dependencies": {
    "@letta-ai/letta-client": "^1.10.3"
  }
}
```

**Usage:** Not fully traced in codebase; potential for AI agent features

---

## 14. Local Development Services

### PM2 Process Manager

**Configuration:** `ecosystem.config.cjs`

```javascript
module.exports = {
  apps: [
    { name: 'ulticode-9001', cwd: './backend-spring', script: 'start.cjs' },
    { name: 'ulticode-9002', cwd: './console', script: 'node_modules/vite/bin/vite.js', args: '--port 9002' },
    { name: 'ulticode-9003', cwd: './management', script: 'node_modules/vite/bin/vite.js', args: '--port 9003' },
    { name: 'ulticode-9004', cwd: './recommendation', script: 'start-provider.cjs' },
    { name: 'ulticode-9005', cwd: './recommendation', script: 'start-web.cjs' }
  ]
}
```

### Docker Compose Services

**Development (`docker-compose.yml`):**
- `mysql:9.1` - Port 23306
- `redis:7-alpine` - Port 26379
- `nacos/nacos-server:v2.3.2` - Port 28848

**Production (`docker-compose.prod.yml`):**
- All above plus:
  - `backend` - Custom image from GHCR
  - `console` - Custom image from GHCR
  - `management` - Custom image from GHCR
  - `recommend-provider` - Custom image
  - `recommend-web` - Custom image

---

## Integration Summary Table

| Integration | Type | Status | Config Location |
|------------|------|--------|-----------------|
| MySQL | Database | Required | `application.yml` |
| Redis | Cache/Session | Required | `application.yml` |
| Nacos | Service Discovery | Required | `application.yml` |
| Dubbo3 | RPC | Optional | `application.yml` |
| JWT | Authentication | Required | `application.yml` |
| GitHub OAuth | Social Login | Optional | `application.yml` |
| Google OAuth | Social Login | Optional | `application.yml` |
| MeiliSearch | Search | Optional | `application.yml` |
| SMTP | Email | Optional | `application.yml` |
| Stripe | Payments | Optional | `.env` |
| WebSocket | Real-time | Required | `websocket/` module |
| Docker Sandbox | Code Execution | Required | `application.yml` |
| Letta AI | AI Features | Optional | `package.json` |
| GitHub Actions | CI/CD | Required | `.github/workflows/` |
| Flyway | Migrations | Required | `db-manager/` |

---

## Environment Variables Reference

### Required for Development

```bash
# Database
DB_HOST=localhost
DB_PORT=23306
DB_USER=ulticode
DB_PASSWORD=ulticode
DB_NAME=ulticode

# Redis
REDIS_HOST=localhost
REDIS_PORT=26379
REDIS_PASSWORD=<password>

# JWT
JWT_SECRET=<32+ char secret>

# Nacos
NACOS_HOST=localhost
NACOS_PORT=28848
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:9002,http://localhost:9003
```

### Optional

```bash
# OAuth
GITHUB_CLIENT_ID=<id>
GITHUB_CLIENT_SECRET=<secret>
GOOGLE_CLIENT_ID=<id>
GOOGLE_CLIENT_SECRET=<secret>

# Email
SMTP_HOST=smtp.example.com
SMTP_PORT=587
EMAIL_ENABLED=false

# Search
MEILISEARCH_ENABLED=false
MEILISEARCH_HOST=

# Payments
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=

# Recommendation
RECOMMENDATION_ENABLED=false
```
