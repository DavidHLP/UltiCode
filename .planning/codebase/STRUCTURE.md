# UltiCode Code Organization

## Project Root Structure

```
UltiCode-Public-Next/
├── backend-spring/          # Spring Boot backend (Java 17)
├── console/                 # User-facing frontend (Vue 3)
├── management/              # Admin dashboard (Vue 3)
├── recommendation/          # Dubbo3 + Spark recommendation service
├── db-manager/              # Flyway database migration tool
├── docs/                    # Documentation
├── ecosystem.config.cjs      # PM2 configuration
├── docker-compose.yml       # Docker services (MySQL, Redis, Nacos)
└── CLAUDE.md               # Project guidance
```

## Backend Structure (`backend-spring/`)

```
backend-spring/
├── pom.xml                          # Maven configuration
├── src/main/java/com/ulticode/
│   ├── UlticodeApplication.java     # Spring Boot entry point
│   ├── common/                     # Shared components
│   │   ├── annotation/             # Custom annotations
│   │   │   ├── CurrentUser.java    # Inject current user
│   │   │   ├── RateLimit.java       # Rate limiting
│   │   │   └── RequireRole.java     # Role requirement
│   │   ├── config/                  # Configuration classes
│   │   │   ├── FeatureFlagsProperties.java
│   │   │   ├── RedisConfig.java
│   │   │   └── SwaggerConfig.java
│   │   ├── dto/                     # Shared DTOs
│   │   │   └── ApiResponse.java
│   │   ├── exception/               # Exception handling
│   │   │   └── BusinessException.java
│   │   ├── response/                # Response wrappers
│   │   │   ├── PageResult.java
│   │   │   └── Result.java
│   │   ├── service/                 # Shared services
│   │   │   └── TokenBlacklistService.java
│   │   └── util/                    # Utilities
│   │       └── SecurityUtil.java
│   ├── infrastructure/               # Infrastructure layer
│   │   └── redis/
│   │       └── CacheConstants.java
│   ├── modules/                     # Feature modules (27 modules)
│   │   ├── achievement/
│   │   ├── admin/
│   │   ├── auth/
│   │   ├── backup/
│   │   ├── bookmark/
│   │   ├── contest/
│   │   ├── edgeoperations/
│   │   ├── email/
│   │   ├── forum/
│   │   ├── i18n/
│   │   ├── monitoring/
│   │   ├── notification/
│   │   ├── permission/
│   │   ├── problemlist/
│   │   ├── problem/
│   │   ├── queue/
│   │   ├── recommendation/
│   │   ├── refreshtoken/
│   │   ├── search/
│   │   ├── solution/
│   │   ├── submission/
│   │   ├── subscription/
│   │   ├── user/
│   │   ├── vote/
│   │   └── websocket/
│   ├── security/                     # Security layer
│   │   ├── filter/                  # JWT filters
│   │   └── service/                 # Security services
│   └── websocket/                    # WebSocket config
├── src/main/resources/
│   ├── application.yml              # Spring configuration
│   └── logback-spring.xml           # Logging configuration
└── src/test/java/                    # Tests
```

### Module Internal Structure

Each feature module follows a consistent pattern:

```
module-name/
├── controller/
│   └── XxxController.java           # REST endpoints
├── dto/
│   ├── XxxQueryDTO.java             # Query parameters
│   ├── XxxCreateDTO.java            # Creation request
│   ├── XxxUpdateDTO.java            # Update request
│   ├── XxxVO.java                   # View object
│   └── XxxDTO.java                  # Data transfer object
├── entity/
│   ├── Xxx.java                     # Database entity
│   └── enums/
│       └── XxxStatus.java           # Enum values
├── event/
│   └── XxxEvent.java                # Domain events
├── mapper/
│   └── XxxMapper.java               # MyBatis mapper
└── service/
    ├── XxxService.java               # Service interface
    └── impl/
        └── XxxServiceImpl.java       # Service implementation
```

## Console Frontend Structure (`console/`)

```
console/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── src/
│   ├── main.ts                      # Entry point
│   ├── App.vue                      # Root component
│   ├── style.css                    # Global styles
│   ├── pwa-register.ts              # PWA setup
│   ├── api/                         # API clients
│   │   ├── auth.ts
│   │   ├── problem.ts
│   │   ├── submission.ts
│   │   ├── contest.ts
│   │   └── user.ts
│   ├── components/                   # Vue components
│   │   ├── ui/                      # Base UI components (shadcn-vue)
│   │   ├── problem/                 # Problem-related components
│   │   ├── editor/                  # Code editor components
│   │   └── layout/                  # Layout components
│   ├── pages/                       # Route pages
│   │   ├── home/
│   │   ├── problem/
│   │   ├── contest/
│   │   ├── submission/
│   │   └── user/
│   ├── stores/                       # Pinia stores
│   │   ├── auth.ts
│   │   ├── problem.ts
│   │   └── user.ts
│   ├── types/                        # TypeScript definitions
│   │   ├── api.ts
│   │   ├── problem.ts
│   │   └── user.ts
│   └── utils/                        # Utilities
│       ├── request.ts               # Axios wrapper
│       └── helpers.ts
├── public/                           # Static assets
└── test/                             # Tests
```

## Management Frontend Structure (`management/`)

```
management/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── src/
│   ├── App.vue                      # Root component
│   ├── env.d.ts                     # Type declarations
│   ├── style.css                    # Global styles
│   ├── api/                         # API clients
│   │   ├── admin.ts
│   │   ├── user.ts
│   │   ├── problem.ts
│   │   └── analytics.ts
│   ├── components/                   # Vue components
│   │   ├── ui/                      # Base UI components
│   │   ├── admin/                   # Admin-specific components
│   │   └── layout/                  # Layout components
│   ├── pages/                       # Route pages
│   │   ├── dashboard/
│   │   ├── users/
│   │   ├── problems/
│   │   ├── contests/
│   │   ├── audit/
│   │   └── analytics/
│   ├── stores/                       # Pinia stores
│   │   └── admin.ts
│   └── utils/                        # Utilities
│       ├── request.ts
│       └── helpers.ts
└── public/                           # Static assets
```

## Recommendation Service Structure (`recommendation/`)

```
recommendation/
├── pom.xml                          # Parent POM
├── recommend-api/                   # Dubbo API definitions
│   ├── pom.xml
│   └── src/main/java/
│       └── com/ulticode/recommend/api/
│           ├── DubboUserRecommendService.java
│           └── model/
├── recommend-core/                  # Core algorithms
│   ├── pom.xml
│   └── src/main/java/
│       └── com/ulticode/recommend/core/
│           ├── algorithm/           # Recommendation algorithms
│           └── similarity/          # Similarity calculations
├── recommend-feature/               # Feature engineering
│   ├── pom.xml
│   └── src/main/java/
│       └── com/ulticode/recommend/feature/
├── recommend-provider/              # Dubbo provider (port 9004)
│   ├── pom.xml
│   └── src/main/java/
│       └── com/ulticode/recommend/provider/
├── recommend-spark/                 # Spark ML jobs
│   ├── pom.xml
│   └── src/main/java/
│       └── com/ulticode/recommend/spark/
└── recommend-web/                  # Dubbo consumer (port 9005)
    ├── pom.xml
    └── src/main/java/
        └── com/ulticode/recommend/web/
```

## Database Manager Structure (`db-manager/`)

```
db-manager/
├── cli.py                          # Main CLI entry point
├── flyway/                          # Flyway configuration
├── migrations/                      # Flyway SQL migrations
│   ├── V1__initial_schema.sql
│   ├── V2__problems_and_tags.sql
│   ├── V3__contests_and_rankings.sql
│   ├── V4__forum.sql
│   ├── V8__collections.sql
│   └── V9__solutions.sql
├── src/
│   └── db_manager/
│       ├── cli.py                  # CLI commands
│       └── migrator.py             # Migration runner
└── .venv/                           # Python virtual environment
```

## Module划分 (Module Division)

### Core Modules (用户直接使用)
| Module | Description |
|--------|-------------|
| `problem` | 题目管理：创建、编辑、标签、难度 |
| `submission` | 代码提交：提交、判题、结果 |
| `contest` | 竞赛管理：创建比赛、排名、参与 |
| `user` | 用户管理：注册、登录、资料 |

### Social Features (社区功能)
| Module | Description |
|--------|-------------|
| `forum` | 论坛：帖子、评论、板块 |
| `solution` | 题解：解题思路、代码 |
| `vote` | 投票：点赞、点踩 |
| `bookmark` | 收藏：收藏夹、收藏题目 |

### Platform Features (平台功能)
| Module | Description |
|--------|-------------|
| `achievement` | 成就系统：徽章、积分、进度 |
| `subscription` | 订阅：关注用户、标签 |
| `notification` | 通知：站内通知 |
| `search` | 搜索：全文检索 |

### Admin Features (管理功能)
| Module | Description |
|--------|-------------|
| `admin` | 管理后台：仪表盘、审核、数据统计 |
| `moderation` | 内容审核：举报处理 |
| `backup` | 数据备份：备份管理 |

### Infrastructure (基础设施)
| Module | Description |
|--------|-------------|
| `auth` | 认证：登录、注册、OAuth |
| `permission` | 权限：角色、权限检查 |
| `refreshtoken` | Token刷新 |
| `edgeoperations` | 实时判题 |
| `queue` | 消息队列 |
| `websocket` | WebSocket通信 |
| `email` | 邮件服务 |
| `i18n` | 国际化 |
| `monitoring` | 监控健康检查 |

### Integration (集成服务)
| Module | Description |
|--------|-------------|
| `recommendation` | 推荐服务：问题推荐 |
| `problemlist` | 题目列表：精选列表 |
