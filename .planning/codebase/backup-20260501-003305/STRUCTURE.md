# UltiCode Project Structure

## Directory Tree

```
UltiCode-Public-Next/
│
├── .planning/                        # Project planning & documentation
│   ├── codebase/
│   │   ├── ARCHITECTURE.md           # System architecture & design
│   │   └── STRUCTURE.md              # This file - directory structure
│   ├── intel/                        # Project intelligence & decisions
│   └── graphs/                       # Dependency & relationship graphs
│
├── backend-spring/                   # Spring Boot backend (Java 17)
│   ├── src/
│   │   └── main/
│   │       ├── java/com/ulticode/
│   │       │   ├── UltiCodeApplication.java
│   │       │   │
│   │       │   ├── common/           # Shared infrastructure
│   │       │   │   ├── annotation/  # Custom annotations
│   │       │   │   │   ├── CurrentUser.java
│   │       │   │   │   ├── RequireRole.java
│   │       │   │   │   ├── RateLimit.java
│   │       │   │   │   └── OperateLog.java
│   │       │   │   │
│   │       │   │   ├── config/      # Configuration classes
│   │       │   │   │   ├── SecurityConfig.java
│   │       │   │   │   ├── CorsConfig.java
│   │       │   │   │   ├── RedisConfig.java
│   │       │   │   │   ├── WebSocketConfig.java
│   │       │   │   │   ├── MybatisPlusConfig.java
│   │       │   │   │   ├── SwaggerConfig.java
│   │       │   │   │   └── RedissonConfig.java
│   │       │   │   │
│   │       │   │   ├── exception/   # Exception handling
│   │       │   │   │   ├── GlobalExceptionHandler.java
│   │       │   │   │   ├── BusinessException.java
│   │       │   │   │   └── ErrorCode.java
│   │       │   │   │
│   │       │   │   ├── response/    # Response wrappers
│   │       │   │   │   ├── Result.java
│   │       │   │   │   └── PageResult.java
│   │       │   │   │
│   │       │   │   ├── util/        # Utilities
│   │       │   │   │   ├── IpUtil.java
│   │       │   │   │   ├── DateUtil.java
│   │       │   │   │   └── BeanUtil.java
│   │       │   │   │
│   │       │   │   └── constant/    # Shared constants
│   │       │   │       ├── RedisConstant.java
│   │       │   │       └── SystemConstant.java
│   │       │   │
│   │       │   ├── security/        # Authentication & authorization
│   │       │   │   ├── jwt/
│   │       │   │   │   ├── JwtTokenProvider.java
│   │       │   │   │   ├── JwtAuthenticationFilter.java
│   │       │   │   │   └── JwtTokenService.java
│   │       │   │   ├── oauth/
│   │       │   │   │   ├── OAuth2LoginConfig.java
│   │       │   │   │   ├── GithubOAuth2UserInfo.java
│   │       │   │   │   └── GoogleOAuth2UserInfo.java
│   │       │   │   ├── csrf/
│   │       │   │   │   └── CsrfService.java
│   │       │   │   └── SecurityUserDetails.java
│   │       │   │
│   │       │   ├── modules/         # Domain modules (27+ modules)
│   │       │   │   ├── auth/         # Authentication
│   │       │   │   │   ├── controller/
│   │       │   │   │   │   └── AuthController.java
│   │       │   │   │   ├── service/
│   │       │   │   │   │   ├── AuthService.java
│   │       │   │   │   │   └── AuthServiceImpl.java
│   │       │   │   │   ├── mapper/
│   │       │   │   │   │   └── UserAuthMapper.java
│   │       │   │   │   ├── entity/
│   │       │   │   │   │   └── UserAuth.java
│   │       │   │   │   └── dto/
│   │       │   │   │       ├── LoginRequest.java
│   │       │   │   │       ├── RegisterRequest.java
│   │       │   │   │       └── AuthResponse.java
│   │       │   │   │
│   │       │   │   ├── user/         # User management
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── mapper/
│   │       │   │   │   ├── entity/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── problem/      # Problem bank
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── mapper/
│   │       │   │   │   ├── entity/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── submission/   # Code submission & judge
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── mapper/
│   │       │   │   │   ├── entity/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── contest/      # Programming contests
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── mapper/
│   │       │   │   │   ├── entity/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── forum/        # Community forum
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── mapper/
│   │       │   │   │   ├── entity/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── solution/     # Problem solutions
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── mapper/
│   │       │   │   │   ├── entity/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── notification/ # Notifications
│   │       │   │   ├── subscription/# Subscriptions
│   │       │   │   ├── vote/         # Upvote/downvote
│   │       │   │   ├── bookmark/     # Bookmarks
│   │       │   │   ├── achievement/  # Badges & achievements
│   │       │   │   ├── follow/       # User follow system
│   │       │   │   ├── recommendation/# Dubbo client
│   │       │   │   ├── search/       # MeiliSearch integration
│   │       │   │   ├── moderation/  # Content moderation
│   │       │   │   ├── admin/        # Admin operations
│   │       │   │   ├── websocket/   # WebSocket handlers
│   │       │   │   ├── queue/       # Async job queue
│   │       │   │   ├── backup/      # Database backup
│   │       │   │   ├── email/       # Email service
│   │       │   │   ├── i18n/        # Internationalization
│   │       │   │   ├── permission/  # Role & permission
│   │       │   │   ├── refreshtoken/# Refresh token
│   │       │   │   ├── edgeoperations/
│   │       │   │   └── problemlist/ # Curated lists
│   │       │   │
│   │       │   └── websocket/        # WebSocket config
│   │       │       ├── WebSocketConfig.java
│   │       │       ├── NotificationService.java
│   │       │       └── WsHandshakeInterceptor.java
│   │       │
│   │       └── resources/
│   │           ├── application.yml
│   │           ├── application-dev.yml
│   │           ├── application-prod.yml
│   │           ├── mapper/           # MyBatis XML mappers
│   │           └── logback-spring.xml
│   │
│   └── test/                        # Backend tests
│       └── java/com/ulticode/
│
├── console/                         # User-facing frontend (Vue 3, port 9002)
│   ├── src/
│   │   ├── main.ts                  # App entry point
│   │   ├── App.vue                  # Root component
│   │   │
│   │   ├── api/                     # API client modules
│   │   │   ├── request.ts           # Axios instance with interceptors
│   │   │   ├── auth.ts
│   │   │   ├── user.ts
│   │   │   ├── problem.ts
│   │   │   ├── submission.ts
│   │   │   ├── contest.ts
│   │   │   ├── forum.ts
│   │   │   ├── solution.ts
│   │   │   └── notification.ts
│   │   │
│   │   ├── components/              # Shared Vue components
│   │   │   ├── common/             # Generic UI components
│   │   │   │   ├── Button.vue
│   │   │   │   ├── Input.vue
│   │   │   │   ├── Modal.vue
│   │   │   │   └── ...
│   │   │   ├── layout/              # Layout components
│   │   │   │   ├── AppHeader.vue
│   │   │   │   ├── AppSidebar.vue
│   │   │   │   └── AppFooter.vue
│   │   │   ├── problem/             # Problem-related components
│   │   │   │   ├── ProblemCard.vue
│   │   │   │   ├── ProblemList.vue
│   │   │   │   ├── CodeEditor.vue
│   │   │   │   └── TestCasePanel.vue
│   │   │   ├── contest/             # Contest components
│   │   │   │   ├── ContestCard.vue
│   │   │   │   ├── RankingTable.vue
│   │   │   │   └── Timer.vue
│   │   │   └── forum/               # Forum components
│   │   │       ├── PostCard.vue
│   │   │       ├── CommentList.vue
│   │   │       └── VoteButtons.vue
│   │   │
│   │   ├── views/                   # Page components
│   │   │   ├── home/               # Home page
│   │   │   │   └── HomePage.vue
│   │   │   ├── auth/               # Auth pages
│   │   │   │   ├── LoginPage.vue
│   │   │   │   └── RegisterPage.vue
│   │   │   ├── problem/            # Problem pages
│   │   │   │   ├── ProblemListPage.vue
│   │   │   │   ├── ProblemDetailPage.vue
│   │   │   │   └── ProblemSubmitPage.vue
│   │   │   ├── contest/            # Contest pages
│   │   │   │   ├── ContestListPage.vue
│   │   │   │   ├── ContestDetailPage.vue
│   │   │   │   └── ContestRankPage.vue
│   │   │   ├── submission/         # Submission pages
│   │   │   │   ├── SubmissionListPage.vue
│   │   │   │   └── SubmissionDetailPage.vue
│   │   │   ├── forum/              # Forum pages
│   │   │   │   ├── ForumHomePage.vue
│   │   │   │   ├── PostDetailPage.vue
│   │   │   │   └── CreatePostPage.vue
│   │   │   ├── solution/           # Solution pages
│   │   │   │   └── SolutionPage.vue
│   │   │   └── user/               # User pages
│   │   │       ├── ProfilePage.vue
│   │   │       ├── UserSubmissionsPage.vue
│   │   │       └── UserContestsPage.vue
│   │   │
│   │   ├── router/                 # Vue Router setup
│   │   │   └── index.ts
│   │   │
│   │   ├── stores/                 # Pinia stores
│   │   │   ├── auth.ts            # Auth state
│   │   │   ├── user.ts            # User state
│   │   │   ├── problem.ts         # Problem state
│   │   │   └── notification.ts     # Notification state
│   │   │
│   │   ├── composables/            # Vue composables
│   │   │   ├── useAuth.ts
│   │   │   ├── usePagination.ts
│   │   │   └── useWebSocket.ts
│   │   │
│   │   ├── utils/                 # Utility functions
│   │   │   ├── date.ts
│   │   │   ├── format.ts
│   │   │   └── validation.ts
│   │   │
│   │   └── assets/                # Static assets
│   │       ├── styles/
│   │       │   ├── main.css
│   │       │   └── variables.css
│   │       └── images/
│   │
│   ├── public/                     # Static public assets
│   │   └── favicon.ico
│   │
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.js
│   └── package.json
│
├── management/                     # Admin frontend (Vue 3, port 9003)
│   ├── src/
│   │   ├── main.ts
│   │   ├── App.vue
│   │   │
│   │   ├── api/                   # Admin API clients
│   │   │   ├── request.ts
│   │   │   ├── user.ts
│   │   │   ├── problem.ts
│   │   │   ├── contest.ts
│   │   │   ├── submission.ts
│   │   │   ├── forum.ts
│   │   │   ├── analytics.ts
│   │   │   └── system.ts
│   │   │
│   │   ├── views/                 # Admin page components
│   │   │   ├── dashboard/        # Dashboard & analytics
│   │   │   ├── user/             # User management
│   │   │   ├── problem/          # Problem management
│   │   │   ├── contest/          # Contest management
│   │   │   ├── submission/       # Submission review
│   │   │   ├── forum/            # Forum moderation
│   │   │   ├── achievement/     # Achievement config
│   │   │   └── system/           # System settings
│   │   │
│   │   ├── components/           # Admin-specific components
│   │   │   ├── layout/           # Admin layout
│   │   │   ├── tables/           # Data tables
│   │   │   ├── forms/            # Form components
│   │   │   └── charts/           # Analytics charts
│   │   │
│   │   ├── router/
│   │   ├── stores/
│   │   └── utils/
│   │
│   ├── public/
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.js
│   └── package.json
│
├── recommendation/                # Recommendation microservice (Dubbo3 + Spark)
│   ├── recommend-api/             # Shared Dubbo interfaces
│   │   └── src/main/java/
│   │       └── com/ulticode/recommend/
│   │           ├──RecommendService.java
│   │           └── dto/
│   │
│   ├── recommend-provider/        # Spark ML provider (port 9004)
│   │   └── src/main/java/
│   │       └── com/ulticode/provider/
│   │           ├── SparkEngine.java
│   │           ├── ALSModel.java
│   │           └── RecommendationServiceImpl.java
│   │
│   ├── recommend-web/             # REST API facade (port 9005)
│   │   └── src/main/java/
│   │       └── com/ulticode/web/
│   │           └── RecommendationController.java
│   │
│   └── pom.xml                    # Maven parent POM
│
├── db-manager/                    # Flyway migration manager (Python)
│   ├── src/
│   │   └── db_manager/
│   │       ├── cli.py            # CLI entry point
│   │       ├── migrate.py        # Migration runner
│   │       ├── repair.py         # Checksum repair
│   │       └── status.py         # Status checker
│   │
│   ├── migrations/                # SQL migration files
│   │   ├── V1__initial_schema.sql
│   │   ├── V2__problems_and_tags.sql
│   │   ├── V3__contests_and_rankings.sql
│   │   ├── V4__forum_and_posts.sql
│   │   ├── V5__solutions.sql
│   │   ├── V6__notifications.sql
│   │   ├── V7__achievements.sql
│   │   ├── V8__collections.sql
│   │   ├── V9__solutions_expand.sql
│   │   └── ... (27+ migration files)
│   │
│   ├── .venv/                    # Python virtual environment
│   └── requirements.txt
│
├── docker/                        # Docker configurations
│   ├── mysql/
│   │   └── init.sql             # MySQL initialization
│   ├── redis/
│   │   └── redis.conf           # Redis configuration
│   └── nginx/
│       └── nginx.conf           # Nginx reverse proxy config
│
├── docker-compose.yml             # Docker Compose for dev services
├── docker-compose.prod.yml        # Production Docker Compose
│
├── ecosystem.config.cjs           # PM2 process manager config
│
├── package.json                   # Root package.json (scripts)
├── pnpm-lock.yaml
│
├── README.md                      # Project readme
└── CLAUDE.md                      # This file - project guidance
```

## Module Directory Pattern

Each backend module follows a consistent 4-layer structure:

```
module-name/
├── controller/      # REST API endpoints (@RestController)
│   └── XxxController.java
├── service/         # Business logic
│   ├── XxxService.java        # Interface
│   └── XxxServiceImpl.java   # Implementation
├── mapper/          # MyBatis-Plus data access
│   └── XxxMapper.java
├── entity/          # Database entity (@Entity, @TableName)
│   └── Xxx.java
└── dto/             # Data transfer objects
    ├── XxxRequest.java       # Incoming request DTO
    └── XxxResponse.java      # Outgoing response DTO
```

## Key File Naming Conventions

| Type              | Suffix            | Example                          |
|-------------------|-------------------|----------------------------------|
| Controller        | `Controller.java` | `ProblemController.java`        |
| Service Interface | `Service.java`    | `ProblemService.java`           |
| Service Impl      | `ServiceImpl.java`| `ProblemServiceImpl.java`       |
| Mapper            | `Mapper.java`    | `ProblemMapper.java`             |
| Entity            | (no suffix)       | `Problem.java`                 |
| Request DTO       | `Request.java`   | `CreateProblemRequest.java`    |
| Response DTO      | `Response.java`  | `ProblemDetailResponse.java`    |
| Config            | `Config.java`    | `SecurityConfig.java`           |
| Exception         | `Exception.java` | `BusinessException.java`       |
| Constant          | `Constant.java`  | `RedisConstant.java`            |

## Frontend Directory Pattern

```
frontend/
├── src/
│   ├── api/           # API client functions (one file per domain)
│   ├── components/   # Reusable Vue components (grouped by domain)
│   ├── views/        # Page-level components (route views)
│   ├── router/       # Vue Router configuration
│   ├── stores/       # Pinia state management
│   ├── composables/  # Vue composition API utilities
│   ├── utils/        # Pure utility functions
│   └── assets/       # Static assets (styles, images)
├── public/           # Copied as-is to build output
├── index.html        # HTML entry point
└── vite.config.ts    # Vite bundler configuration
```

## Configuration Files

| File                          | Purpose                                      |
|-------------------------------|----------------------------------------------|
| `backend-spring/.env`          | Backend environment variables                |
| `backend-spring/src/main/resources/application.yml` | Spring Boot configuration |
| `console/vite.config.ts`      | Vite dev server & build config              |
| `management/vite.config.ts`   | Management app Vite config                  |
| `ecosystem.config.cjs`        | PM2 process definitions                      |
| `docker-compose.yml`           | Dev Docker services                          |
| `db-manager/migrations/*.sql` | Flyway database migrations                   |

## Build & Run Commands

```bash
# Install frontend dependencies
pnpm install

# Start all services (PM2)
pm2 start ecosystem.config.cjs

# Backend only
cd backend-spring && ./mvnw spring-boot:run

# Console frontend (dev)
cd console && pnpm run dev

# Management frontend (dev)
cd management && pnpm run dev

# Run migrations
cd db-manager && .venv/bin/python -m db_manager.cli migrate

# Build frontends
cd console && pnpm build
cd management && pnpm build

# Backend build
cd backend-spring && ./mvnw package -DskipTests
```

## Database Migrations Order

Migrations follow chronological versioning (V1 → V27+):

| Version | Content                                                   |
|---------|-----------------------------------------------------------|
| V1      | Users, permissions, authentication                        |
| V2      | Problems, tags, difficulty levels                         |
| V3      | Contests, rankings, participation                         |
| V4      | Forum, posts, comments                                   |
| V5      | Solutions                                                 |
| V6      | Notifications                                             |
| V7      | Achievements, badges                                      |
| V8      | Collections                                               |
| V9+     | Additional features, indexes, data expansions             |
