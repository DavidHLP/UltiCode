# UltiCode Directory Structure

## Root Level

```
UltiCode-Public-Next/
├── backend-spring/          # Spring Boot backend (JAR)
├── console/                # User-facing Vue 3 frontend
├── management/             # Admin dashboard Vue 3 frontend
├── recommendation/         # Dubbo3 + Spark recommendation service
├── db-manager/            # Flyway migration CLI (Python)
├── shared/                 # Shared TypeScript packages
├── docker/                 # Docker initialization scripts
├── docs/                   # Documentation
├── .env                    # Environment configuration
├── docker-compose.yml      # Local dev infrastructure
├── docker-compose.prod.yml # Production Docker Compose
├── ecosystem.config.cjs    # PM2 process configuration
├── package.json           # Root npm scripts (pnpm)
├── CLAUDE.md              # Project guidance
├── PLAN.md                # Project plan
└── README.md              # Project readme
```

## Backend Structure

```
backend-spring/
├── src/main/java/com/ulticode/
│   ├── UlticodeBackendApplication.java
│   ├── common/                    # Shared utilities
│   │   ├── annotation/            # Custom annotations
│   │   ├── aspect/               # AOP aspects
│   │   ├── config/               # Spring configurations
│   │   ├── constants/            # Constants
│   │   ├── dto/                  # Shared DTOs
│   │   ├── exception/             # Exception handling
│   │   ├── filter/               # Servlet filters
│   │   ├── response/             # Result<T> wrapper, PageResult
│   │   ├── service/              # Shared services
│   │   └── util/                # Utility classes
│   ├── modules/                  # Domain modules
│   │   ├── auth/                 # controller, service, mapper, entity, dto
│   │   ├── user/
│   │   ├── problem/
│   │   ├── submission/
│   │   ├── contest/
│   │   ├── forum/
│   │   ├── solution/
│   │   ├── notification/
│   │   ├── subscription/
│   │   ├── moderation/
│   │   ├── search/
│   │   ├── achievement/
│   │   ├── i18n/
│   │   ├── vote/
│   │   ├── bookmark/
│   │   ├── follow/
│   │   ├── admin/
│   │   ├── recommendation/
│   │   ├── edgeoperations/
│   │   ├── queue/
│   │   ├── refreshtoken/
│   │   ├── problemlist/
│   │   ├── permission/
│   │   └── backup/
│   ├── security/                 # JWT filters, security config
│   └── websocket/                # WebSocket endpoints
├── src/main/resources/            # Application properties, mybatis mappings
├── src/test/java/                 # Backend tests
├── pom.xml                        # Maven configuration
├── Dockerfile                     # Container build
├── .env                           # Backend env vars
└── start-backend.sh               # Startup script
```

### Module Structure Convention

Each domain module follows a consistent pattern:
```
module-name/
├── controller/      # REST endpoints
├── service/         # Business logic
├── mapper/          # MyBatis mappers
├── entity/         # Database entities
└── dto/            # Request/Response DTOs
```

## Frontend Structure

Both `console/` and `management/` share similar Vue 3 + Vite structures:

```
console/ (or management/)
├── src/
│   ├── api/               # API client functions (axios wrappers)
│   │   ├── auth.ts
│   │   ├── problem.ts
│   │   ├── submission.ts
│   │   ├── contest.ts
│   │   └── ...
│   ├── components/        # Reusable Vue components
│   ├── composables/        # Vue composables (useXxx hooks)
│   ├── views/              # Page components
│   ├── router/             # Vue Router configuration
│   ├── stores/             # Pinia stores
│   ├── utils/              # Utility functions
│   ├── types/              # TypeScript type definitions
│   ├── i18n/               # Internationalization
│   ├── constants/          # App constants
│   ├── assets/              # Static assets
│   ├── contexts/            # Vue context providers
│   ├── hooks/               # Custom hooks
│   ├── features/            # Feature-specific code
│   ├── lib/                 # Third-party library wrappers
│   ├── main.ts              # App entry point
│   ├── App.vue              # Root component
│   └── style.css            # Global styles
├── public/                  # Static public assets
├── dist/                    # Build output
├── index.html               # HTML entry
├── package.json             # Dependencies
├── vite.config.ts           # Vite configuration
├── tsconfig.json            # TypeScript config
├── Dockerfile               # Container build
├── nginx.conf               # Nginx config for production
└── .env.example             # Environment template
```

## Shared Packages

```
shared/
├── auth-core/               # JWT auth utilities
│   ├── src/
│   │   └── index.ts
│   └── package.json
├── api-utils/               # API utilities
│   └── src/
├── types/                   # Shared TypeScript types
│   └── src/
└── (each published as npm package)
```

## Database Management

```
db-manager/
├── migrations/               # Flyway SQL migrations (V1__, V2__, etc.)
├── flyway/                  # Flyway configuration
├── src/                     # Python CLI source
├── .venv/                   # Python virtual environment
├── pyproject.toml           # Python dependencies
├── README.md                # Usage instructions
└── db_manager/cli.py        # CLI entry point
```

## Recommendation Service

```
recommendation/
├── recommend-api/           # Dubbo service interface (JAR published to Maven)
├── recommend-core/          # Core recommendation logic
├── recommend-feature/       # Feature engineering
├── recommend-spark/          # Spark ML jobs
├── recommend-provider/       # Dubbo service provider
├── recommend-web/            # Dubbo web gateway
├── pom.xml                  # Maven multi-module project
└── README.md
```

## Docker

```
docker/
├── initdb/                  # MySQL initialization scripts
└── sandbox/                 # Sandbox environment configs
```

## Configuration Files

| File                  | Purpose                                    |
|-----------------------|--------------------------------------------|
| `.env`                | Environment variables (not committed)       |
| `.env.example`       | Template for environment variables          |
| `ecosystem.config.cjs`| PM2 service definitions                    |
| `docker-compose.yml`  | Local dev infrastructure (MySQL, Redis, Nacos) |
| `docker-compose.prod.yml` | Production Docker Compose             |
| `docker-wrapper.cjs` | Node script for Docker management          |
| `Dockerfile`          | Multi-stage Docker build                   |
| `nginx.conf`         | Nginx configuration for frontends           |
