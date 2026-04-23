# Codebase Structure

**Analysis Date:** 2026-04-22

## Directory Layout

```
/home/davidhlp/project/UltiCode-Public-Next/
├── backend-spring/           # Spring Boot backend (Java 17)
├── console/                  # User-facing Vue 3 frontend
├── management/              # Admin Vue 3 frontend
├── recommendation/          # Dubbo3 recommendation microservice
├── db-manager/              # Flyway database migration tool
├── shared/                  # Shared code (symlinked to frontends)
├── docker-compose.yml       # Development Docker services
├── docker-compose.prod.yml  # Production Docker configuration
├── ecosystem.config.cjs     # PM2 process manager config
└── .env                     # Environment variables
```

## Backend Structure

```
backend-spring/
├── src/main/java/com/ulticode/
│   ├── UlticodeBackendApplication.java  # Main entry point
│   ├── common/                          # Shared utilities
│   │   ├── annotation/                  # Custom annotations (@CurrentUser, @RequireRole, @RateLimit)
│   │   ├── aspect/                      # AOP aspects
│   │   ├── config/                      # Configuration classes
│   │   │   ├── CacheConfig.java         # Redis cache configuration
│   │   │   ├── CorsProperties.java      # CORS settings
│   │   │   ├── EnvValidationConfig.java # Environment validation
│   │   │   ├── FeatureFlagsProperties.java
│   │   │   ├── MapperConfig.java        # MyBatis mapper config
│   │   │   ├── MybatisPlusConfig.java   # MyBatis-Plus settings
│   │   │   ├── RedisConfig.java         # Redis connection config
│   │   │   ├── SecurityConfig.java      # Spring Security config
│   │   │   ├── SwaggerConfig.java       # OpenAPI/Swagger docs
│   │   │   ├── WebConfig.java           # Web MVC config
│   │   │   └── WebMvcConfig.java        # Additional web config
│   │   ├── constants/                   # Shared constants
│   │   ├── dto/                         # Common DTOs
│   │   ├── exception/                  # Exception handling
│   │   │   ├── BusinessException.java   # Domain exception
│   │   │   ├── ErrorCode.java           # Error code definitions
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── filter/                      # Servlet filters
│   │   ├── response/                    # API response wrappers
│   │   │   ├── PageResult.java          # Paginated response
│   │   │   └── Result.java              # Standard response envelope
│   │   ├── service/                    # Common services
│   │   └── util/                        # Utility classes
│   ├── modules/                         # Domain modules
│   │   ├── achievement/                 # Achievements system
│   │   ├── admin/                       # Admin functionality
│   │   ├── auth/                        # Authentication
│   │   ├── backup/                      # Database backup
│   │   ├── bookmark/                    # User bookmarks
│   │   ├── contest/                     # Contests and rankings
│   │   ├── edgeoperations/              # Edge operations
│   │   ├── email/                       # Email service
│   │   ├── follow/                     # Follow relationships
│   │   ├── forum/                       # Forum and communities
│   │   ├── i18n/                        # Internationalization
│   │   ├── moderation/                  # Content moderation
│   │   ├── monitoring/                  # System monitoring
│   │   ├── notification/                # Notifications
│   │   ├── permission/                  # Permission system
│   │   ├── problem/                     # Problems and test cases
│   │   ├── problemlist/                  # Problem lists
│   │   ├── queue/                       # Submission queue
│   │   ├── recommendation/              # Recommendation service client
│   │   ├── refreshtoken/                # Token refresh
│   │   ├── search/                      # Search functionality
│   │   ├── solution/                    # Solutions
│   │   ├── submission/                  # Code submissions and judging
│   │   ├── subscription/                # User subscriptions
│   │   ├── user/                        # User management
│   │   ├── vote/                        # Voting
│   │   └── websocket/                   # WebSocket handling
│   └── security/                        # Security layer
│       ├── AuthenticationEntryPointImpl.java
│       ├── csrf/                        # CSRF protection
│       │   ├── CsrfService.java
│       │   └── CsrfValidationFilter.java
│       ├── jwt/                         # JWT authentication
│       │   ├── JwtAuthenticationFilter.java
│       │   ├── JwtProperties.java
│       │   └── JwtTokenProvider.java
│       └── oauth/                       # OAuth integration
├── src/main/resources/
│   ├── application.yml                  # Main configuration
│   ├── application-dev.yml              # Development overrides
│   ├── application-prod.yml             # Production overrides
│   ├── application-ci.yml               # CI environment
│   └── db/                              # Database scripts
└── src/test/java/                       # Backend tests
```

## Frontend Structure (Console)

```
console/
├── src/
│   ├── main.ts                          # Vue app entry point
│   ├── App.vue                          # Root component
│   ├── api/                             # API client modules
│   │   ├── achievement.ts
│   │   ├── auth.ts
│   │   ├── bookmark.ts
│   │   ├── contest.ts
│   │   ├── edge-operations.ts
│   │   ├── follow.ts
│   │   ├── forum.ts
│   │   ├── interaction.ts
│   │   ├── notification.ts
│   │   ├── problem-detail.ts
│   │   ├── problem-list.ts
│   │   ├── problem.ts
│   │   ├── recommendation.ts
│   │   ├── search.ts
│   │   ├── solution.ts
│   │   ├── submission.ts
│   │   ├── subscription.ts
│   │   ├── topic.ts
│   │   ├── user.ts
│   │   ├── userStats.ts
│   │   ├── vote.ts
│   │   └── __tests__/                   # API tests
│   ├── components/                      # Vue components
│   │   ├── achievement/
│   │   ├── bookmark/
│   │   ├── comments/
│   │   ├── common/
│   │   ├── dashboard/
│   │   ├── edge-operations/
│   │   ├── editor/
│   │   ├── follow/
│   │   ├── LanguageSwitcher.vue
│   │   ├── markdown/
│   │   ├── notification/
│   │   ├── problem/
│   │   ├── search/
│   │   └── ui/
│   ├── composables/                     # Vue composables
│   ├── constants/                       # App constants
│   ├── env.d.ts                         # Vite env types
│   ├── i18n/                            # Internationalization
│   ├── lib/                             # Library utilities
│   ├── router/                          # Vue Router config
│   ├── shared -> ../../shared           # Symlink to shared
│   ├── stores/                          # Pinia stores
│   ├── style.css                        # Global styles
│   ├── types/                           # TypeScript types
│   ├── utils/                           # Utility functions
│   │   └── request.ts                   # Axios wrapper with Result unwrapping
│   └── views/                           # Page components
│       ├── achievements/
│       ├── auth/
│       ├── contest/
│       ├── dashboard/
│       ├── forum/
│       ├── personal/
│       ├── post-editor/
│       ├── problem-list/
│       ├── problems/
│       ├── problem-set/
│       ├── profile/
│       ├── recommendations/
│       └── users/
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## Frontend Structure (Management)

```
management/
├── src/
│   ├── main.ts                          # Vue app entry point
│   ├── App.vue                          # Root component
│   ├── api/                             # API client modules
│   │   ├── analytics.ts
│   │   ├── audit.ts
│   │   ├── auth.ts
│   │   ├── comment.ts
│   │   ├── contest.ts
│   │   ├── forum.ts
│   │   ├── moderation.ts
│   │   ├── notification.ts
│   │   ├── problem.ts
│   │   ├── solution.ts
│   │   ├── submission.ts
│   │   ├── system.ts
│   │   ├── tag.ts
│   │   └── user.ts
│   ├── components/                      # Vue components
│   │   ├── analytics/
│   │   ├── audit/
│   │   ├── dashboard/
│   │   ├── layout/
│   │   ├── problem/
│   │   ├── problems/
│   │   ├── shared/
│   │   ├── table/
│   │   └── ui/
│   ├── composables/                     # Vue composables
│   ├── constants/                       # App constants
│   ├── contexts/                        # Vue contexts
│   ├── features/                        # Feature modules
│   ├── hooks/                           # Custom hooks
│   ├── i18n/                            # Internationalization
│   ├── lib/                             # Library utilities
│   ├── pwa-register.ts                  # PWA registration
│   ├── router/                          # Vue Router config
│   ├── stores/                          # Pinia stores
│   ├── style.css                        # Global styles
│   ├── types/                           # TypeScript types
│   ├── utils/                           # Utility functions
│   └── views/                           # Page components
│       ├── account/
│       ├── analytics/
│       ├── audit/
│       ├── auth/
│       ├── billing/
│       ├── comments/
│       ├── contest/
│       ├── contests/
│       ├── dashboard/
│       ├── forum/
│       ├── moderation/
│       ├── notifications/
│       ├── problem-lists/
│       ├── problems/
│       ├── settings/
│       ├── solutions/
│       ├── submissions/
│       ├── system/
│       ├── tags/
│       └── users/
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## Module Internal Structure (Backend)

Each backend module follows a consistent pattern:

```
modules/<name>/
├── controller/           # REST endpoints (@RestController)
│   └── *Controller.java
├── service/               # Business logic (@Service)
│   └── *Service.java
├── entity/                # Database entities (MyBatis-Plus)
│   └── *Entity.java
├── mapper/                # MyBatis mappers (@Mapper)
│   └── *Mapper.java
└── dto/                   # Request/Response DTOs
    ├── *Request.java
    └── *Response.java
```

## Key File Locations

**Entry Points:**
- Backend: `backend-spring/src/main/java/com/ulticode/UlticodeBackendApplication.java`
- Console: `console/src/main.ts`
- Management: `management/src/main.ts`

**Configuration:**
- Backend main: `backend-spring/src/main/resources/application.yml`
- Backend env: `backend-spring/.env`
- PM2: `ecosystem.config.cjs`
- Docker Compose dev: `docker-compose.yml`
- Docker Compose prod: `docker-compose.prod.yml`

**Core Logic:**
- Backend common: `backend-spring/src/main/java/com/ulticode/common/config/`
- Backend security: `backend-spring/src/main/java/com/ulticode/security/`
- Backend modules: `backend-spring/src/main/java/com/ulticode/modules/`

**Database Migrations:**
- Location: `db-manager/migrations/`
- Naming: `V{version}__{description}.sql`
- Managed by: Flyway via Python CLI

## Where to Add New Code

**New Backend Feature Module:**
1. Create directory: `backend-spring/src/main/java/com/ulticode/modules/<name>/`
2. Add subdirectories: `controller/`, `service/`, `entity/`, `mapper/`, `dto/`
3. Register in module structure following existing patterns
4. Add Spring Boot auto-configuration if needed

**New API Endpoint (Backend):**
1. Add controller to appropriate module: `modules/<name>/controller/`
2. Follow existing naming: `*Controller.java`
3. Use `@RestController` and appropriate `@RequestMapping`

**New Frontend Page:**
- Console pages: `console/src/views/<category>/`
- Management pages: `management/src/views/<category>/`

**New API Client (Frontend):**
- Console: `console/src/api/<name>.ts`
- Management: `management/src/api/<name>.ts`

**New Vue Component:**
- Console: `console/src/components/<category>/`
- Management: `management/src/components/<category>/`

**New Shared Code:**
- Add to `shared/` directory
- Symlinked to both frontends via `console/src/shared` and `management/src/shared`

## Database Migration Location

```
db-manager/
├── migrations/           # Flyway SQL migrations
│   ├── V1__initial_schema.sql
│   ├── V2__problems_and_tags.sql
│   └── ...
├── db_manager/           # Python CLI tool
└── .venv/                # Python virtual environment
```

## Special Directories

**shared/** - Symlinked shared code used by both frontends
**db-manager/** - Python-based Flyway migration manager (uses own venv)
**infrastructure/** - Deployment infrastructure code
**docs/** - Documentation

---

*Structure analysis: 2026-04-22*
