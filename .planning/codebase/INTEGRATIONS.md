# UltiCode Integrations

> Third-party and internal service integrations for UltiCode platform.

---

## Database Integration

### MySQL

**Purpose:** Primary relational database for all application data

**Docker Service:** `docker-compose.yml`
```yaml
mysql:
  image: mysql:9.1
  ports:
    - "23306:3306"
```

**Connection Details:**
- Host: `localhost:23306` (via Docker port mapping)
- Database: `ulticode`
- User: `ulticode`
- Driver: `mysql-connector-j`

**Modules Using MySQL:**
- `backend-spring` - All application data
- `recommendation/recommend-feature` - Feature store
- `recommendation/recommend-spark` - Offline processing

**ORM:** MyBatis-Plus 3.5.16

**Migration Tool:** Flyway via `db-manager` Python CLI

---

## Cache Integration

### Redis

**Purpose:** Session storage, JWT tokens, distributed locks, caching

**Docker Service:** `docker-compose.yml`
```yaml
redis:
  image: redis:7-alpine
  ports:
    - "26379:6379"
```

**Use Cases:**
1. JWT refresh token storage with TTL
2. Distributed locks via Redisson
3. Spring Cache abstraction
4. WebSocket session management

**Client:** Redisson 4.3.1 for distributed locks

---

## Service Discovery

### Nacos

**Purpose:** Service registration and discovery for Dubbo3 RPC

**Docker Service:** `docker-compose.yml`
```yaml
nacos:
  image: nacos/nacos-server:v2.3.2
  ports:
    - "28848:8848"
```

**Configuration:**
- Default Port: 28848
- Namespace: `public`
- Group: `DEFAULT_GROUP`
- Credentials: `nacos/nacos`

**Services Registered:**
- `recommend-provider` (Dubbo RPC)
- `recommend-web` (REST API)

---

## RPC Communication

### Dubbo3

**Purpose:** RPC communication between backend and recommendation service

**Configuration:** `backend-spring/src/main/resources/application.yml`

**Ports:**
- `recommend-provider`: 9004 (HTTP), 20881 (Dubbo)
- `recommend-web`: 9005 (HTTP)

**Fallback:** Direct HTTP fallback to port 28081

---

## Authentication

### JWT Authentication

**Token Configuration:**
- Access token expiry: 15 minutes
- Refresh token expiry: 7 days
- Storage: Redis + httpOnly cookies
- Algorithm: HS256

**CSRF Protection:** X-CSRF-Token header required for state-changing requests

### OAuth 2.0

**GitHub OAuth:**
- Authorization URL: `https://github.com/login/oauth/authorize`
- Token URL: `https://github.com/login/oauth/access_token`
- User URL: `https://api.github.com/user`
- Scopes: `user:email`

**Google OAuth:**
- Authorization URL: `https://accounts.google.com/o/oauth2/v2/auth`
- Token URL: `https://oauth2.googleapis.com/token`
- User URL: `https://www.googleapis.com/oauth2/v2/userinfo`
- Scopes: `email,profile`

---

## Search Integration

### MeiliSearch

**Purpose:** Full-text search for problems and users

**SDK:** `meilisearch-java` 0.20.0 with OkHttp 5.3.2

**Status:** Optional (disabled by default)

**Environment Variables:**
- `MEILISEARCH_ENABLED`
- `MEILISEARCH_HOST`
- `MEILISEARCH_API_KEY`

---

## Email Integration

### SMTP

**Purpose:** Password reset, email verification, notifications

**Library:** Spring Mail (Jakarta Mail)

**Configuration:**
- Default Port: 587 (TLS)
- Status: Disabled by default

**Environment Variables:**
- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USER`
- `SMTP_PASSWORD`
- `EMAIL_ENABLED`

---

## Payment Integration

### Stripe

**Purpose:** Premium subscription payments

**Status:** Optional

**Environment Variables:**
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `STRIPE_PRICE_PREMIUM_MONTHLY`
- `STRIPE_PRICE_PREMIUM_YEARLY`

---

## Real-time Communication

### WebSocket (STOMP)

**Purpose:** Real-time notifications, contest updates, live rankings

**Frontend Libraries:**
- `STOMP.js` 7.3.0
- `SockJS Client` 1.6.1

**Authentication:** JWT via `JwtChannelInterceptor`

**Endpoints:**
- `/ws` - WebSocket handshake
- `/topic` - Public subscriptions
- `/queue` - User-specific messages

---

## Code Execution

### Docker Sandbox

**Purpose:** Secure isolated code execution for submitted solutions

**Configuration:** `application.yml`
```yaml
code-execution:
  sandbox:
    enabled: true
    image: ulticode-judge:latest
    memory: 256m
    timeout: 10s
```

**Supported Languages:**
| Language | Timeout | Memory |
|----------|---------|--------|
| Java | 10s | 256m |
| Python | 5s | 128m |
| C/C++ | 5s | 128m |
| Go | 8s | 256m |
| Rust | 8s | 256m |
| JavaScript | 3s | 64m |

---

## CI/CD Integration

### GitHub Actions

**Workflow Files:** `.github/workflows/`

**CI Pipeline:**
- Java 17 (Temurin)
- Node 22.x
- pnpm 10
- Maven build + test
- Frontend lint + type-check + test

**Test Infrastructure:**
- Testcontainers for MySQL 9.1
- Testcontainers for Redis 7

---

## Process Management

### PM2

**Configuration:** `ecosystem.config.cjs`

**Services:**
| Service | Port | Working Directory |
|---------|------|-------------------|
| ulticode-9001 | 9001 | ./backend-spring |
| ulticode-9002 | 9002 | ./console |
| ulticode-9003 | 9003 | ./management |
| ulticode-9004 | 9004 | ./recommendation |
| ulticode-9005 | 9005 | ./recommendation |

---

## Environment Configuration

### Configuration Source
All environment variables are managed in the root `.env` file.

### Required Variables
```bash
# Database
DB_HOST=localhost
DB_PORT=23306
DB_USER=ulticode
DB_PASSWORD=ulticode
DB_NAME=ulticode
MYSQL_ROOT_PASSWORD=root

# Redis
REDIS_HOST=localhost
REDIS_PORT=26379
REDIS_PASSWORD=ulticode_redis

# JWT
JWT_SECRET=<32+ character secret>

# Nacos
NACOS_HOST=localhost
NACOS_PORT=28848
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos

# Frontend
VITE_API_BASE_URL=http://localhost:9001
```

### Optional Variables
```bash
# OAuth
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# Email
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USER=
SMTP_PASSWORD=
EMAIL_ENABLED=false

# Payments
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=

# Recommendation
RECOMMENDATION_ENABLED=true
RECOMMENDATION_TIMEOUT=5000
```

---

## Integration Summary

| Integration | Type | Required | Config Location |
|------------|------|----------|-----------------|
| MySQL | Database | Yes | `application.yml` |
| Redis | Cache | Yes | `application.yml` |
| Nacos | Service Discovery | Yes | `application.yml` |
| Dubbo3 | RPC | Yes | `application.yml` |
| JWT | Authentication | Yes | `application.yml` |
| GitHub OAuth | Social Login | No | `application.yml` |
| Google OAuth | Social Login | No | `application.yml` |
| MeiliSearch | Search | No | `application.yml` |
| SMTP | Email | No | `application.yml` |
| Stripe | Payments | No | `.env` |
| WebSocket | Real-time | Yes | Code config |
| Docker Sandbox | Code Execution | Yes | `application.yml` |
| GitHub Actions | CI/CD | Yes | `.github/workflows/` |
| Flyway | Migrations | Yes | `db-manager/` |
