# UltiCode 系统架构

## 1. 架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           UltiCode 整体架构                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                         Frontend Layer                                │   │
│  ├────────────────────────────┬───────────────────────────────────────────┤
│  │   Console (9002)           │   Management (9003)                       │
│  │   Vue 3 + Vite            │   Vue 3 + Vite                            │
│  │   用户前端                 │   管理后台                                 │
│  │   • 题目解答               │   • 用户管理                               │
│  │   • 竞赛参与               │   • 内容审核                               │
│  │   • 论坛讨论               │   • 审计日志                               │
│  │   • 个人主页              │   • 数据分析                               │
│  └────────────┬───────────────┴───────────────────────┬──────────────────┘   │
│               │                                       │                      │
│               │     ┌─────────────────────────────────┘                      │
│               │     │                                                        │
│               ▼     ▼                                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                     Backend Layer (9001)                              │   │
│  │                 Spring Boot 3.5 + MyBatis-Plus                       │   │
│  ├───────────────────────────────────────────────────────────────────────┤   │
│  │  Modules: auth | user | problem | submission | contest | forum |       │   │
│  │          solution | notification | subscription | moderation |        │   │
│  │          search | achievement | i18n | backup | email | monitoring |  │   │
│  │          vote | admin | bookmark | edgeoperations | permission |     │   │
│  │          problemlist | queue | recommendation | refreshtoken | websocket │   │
│  └──────┬────────────────┬─────────────────────┬───────────────────────┘   │
│         │                │                     │                            │
│         ▼                ▼                     ▼                            │
│  ┌────────────┐  ┌────────────┐        ┌─────────────────────┐               │
│  │   MySQL    │  │   Redis    │        │  Recommendation    │               │
│  │  (23306)   │  │  (26379)   │        │  Dubbo3 + Spark    │               │
│  │  主数据库   │  │  缓存/会话  │        │     (9004/9005)   │               │
│  └────────────┘  └────────────┘        └─────────────────────┘               │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    Docker Services                                   │   │
│  │         MySQL (23306) | Redis (26379) | Nacos (28848)               │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 2. 服务端口映射

| Service          | Port  | Description                    |
| ---------------- | ----- | ------------------------------ |
| Backend (Spring) | 9001  | 主后端 API                     |
| Console          | 9002  | 用户前端                       |
| Management       | 9003  | 管理后台                       |
| Recommend-Provider | 9004 | 推荐服务 Provider (Dubbo)      |
| Recommend-Web    | 9005  | 推荐服务 Web (Dubbo)           |
| MySQL            | 23306 | 主数据库                       |
| Redis            | 26379 | 缓存与会话                     |
| Nacos            | 28848 | 服务发现与配置                 |

## 3. 数据流架构

### 3.1 请求处理流程

```
Client (Browser)
     │
     │ HTTP Request (Cookie: JSESSIONID / access_token)
     ▼
┌─────────────────────────────────────────────────────────────┐
│  Security Filter Chain                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ CsrfFilter  │→ │ JwtFilter   │→ │ AuthenticationFilter │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  Controller Layer                                           │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ @RestController → @RequireRole → @CurrentUser            │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  Service Layer                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ MyBatis-Plus│  │ Redis Cache  │  │ Recommendation   │   │
│  │   Service   │  │   Service    │  │     Service      │   │
│  └─────────────┘  └──────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  Data Layer                                                 │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │    MySQL    │  │    Redis    │                        │
│  │  Repository │  │    Cache    │                        │
│  └──────────────┘  └──────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 认证流程

```
登录请求
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  POST /auth/login                                           │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 1. 验证用户名密码                                       ││
│  │ 2. 生成 JWT Token (access_token → httpOnly Cookie)     ││
│  │ 3. 生成 Refresh Token (refresh_token → httpOnly Cookie)││
│  │ 4. 返回 CSRF Token (response body)                      ││
│  │ 5. 存储会话到 Redis                                    ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
    │
    ▼
后续请求
    │
    ├─→ Cookie: access_token (httpOnly, 自动携带)
    ├─→ Header: X-CSRF-Token (防止 CSRF)
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  JwtFilter: 解析 Token → 提取用户信息 → SecurityContext   │
└─────────────────────────────────────────────────────────────┘
```

## 4. 后端模块架构

### 4.1 Backend Spring 模块列表

```
backend-spring/src/main/java/com/ulticode/
├── UlticodeBackendApplication.java    # 启动类
│
├── common/                          # 公共组件
│   ├── annotation/                   # 自定义注解 (@CurrentUser, @RequireRole, @RateLimit)
│   ├── aspect/                      # AOP 切面
│   ├── config/                      # 配置类 (Security, Redis, Web)
│   ├── constants/                   # 常量定义
│   ├── dto/                         # 公共 DTO
│   ├── exception/                   # 异常处理 (GlobalExceptionHandler, BusinessException)
│   ├── filter/                      # Servlet 过滤器
│   ├── response/                    # 统一响应 (Result<T>, PageResult)
│   ├── service/                     # 公共服务接口
│   └── util/                       # 工具类
│
├── security/                       # 安全模块
│   ├── AuthenticationEntryPointImpl.java
│   ├── csrf/                       # CSRF 防护
│   ├── jwt/                        # JWT 处理
│   └── oauth/                      # OAuth 认证
│
├── modules/                         # 业务模块 (26个)
│   ├── achievement/                 # 成就系统
│   ├── admin/                      # 超级管理员
│   ├── auth/                       # 认证授权
│   ├── backup/                     # 数据备份
│   ├── bookmark/                   # 收藏功能
│   ├── contest/                    # 竞赛系统
│   ├── edgeoperations/             # 边界操作
│   ├── email/                      # 邮件服务
│   ├── follow/                     # 关注功能
│   ├── forum/                      # 论坛帖子
│   ├── i18n/                       # 国际化
│   ├── moderation/                 # 内容审核
│   ├── monitoring/                 # 监控告警
│   ├── notification/               # 通知系统
│   ├── permission/                 # 权限管理
│   ├── problem/                   # 题目管理
│   ├── problemlist/               # 题目列表
│   ├── queue/                     # 评判队列
│   ├── recommendation/             # 推荐服务 (Dubbo Consumer)
│   ├── refreshtoken/              # Token 刷新
│   ├── search/                     # 搜索功能
│   ├── solution/                  # 题解管理
│   ├── submission/                # 提交评判
│   ├── subscription/             # 订阅功能
│   ├── user/                      # 用户管理
│   ├── vote/                      # 投票功能
│   └── websocket/                 # WebSocket 实时通信
│
└── websocket/                     # WebSocket 配置
```

### 4.2 模块标准结构

每个业务模块遵循以下标准结构（以 problem 为例）:

```
problem/
├── controller/                      # REST 控制器
│   └── ProblemController.java
├── dto/                            # 数据传输对象
│   ├── ProblemCreateDTO.java
│   ├── ProblemUpdateDTO.java
│   └── ProblemVO.java
├── entity/                         # 数据库实体
│   └── Problem.java
├── mapper/                         # MyBatis Mapper
│   └── ProblemMapper.java
└── service/                        # 业务逻辑
    ├── ProblemService.java
    └── impl/
        └── ProblemServiceImpl.java
```

## 5. 前端架构

### 5.1 Console (用户前端)

```
console/src/
├── api/                            # API 客户端
│   ├── __tests__/
│   ├── achievement.ts
│   ├── auth.ts
│   ├── bookmark.ts
│   ├── contest.ts
│   ├── edge-operations.ts
│   ├── follow.ts
│   ├── forum.ts
│   ├── interaction.ts
│   ├── notification.ts
│   ├── problem.ts
│   ├── problem-detail.ts
│   ├── problem-list.ts
│   ├── recommendation.ts
│   ├── search.ts
│   ├── solution.ts
│   ├── submission.ts
│   ├── subscription.ts
│   ├── topic.ts
│   ├── user.ts
│   ├── userStats.ts
│   └── vote.ts
│
├── components/                     # 公共组件
│   ├── achievement/
│   ├── bookmark/
│   ├── comments/
│   ├── common/
│   ├── dashboard/
│   ├── editor/
│   ├── edge-operations/
│   ├── follow/
│   ├── markdown/
│   ├── notification/
│   ├── problem/
│   ├── search/
│   ├── ui/
│   └── LanguageSwitcher.vue
│
├── views/                         # 页面组件
│   ├── achievements/
│   ├── auth/
│   ├── contest/
│   ├── dashboard/
│   ├── forum/
│   ├── personal/
│   ├── problem-list/
│   ├── problems/
│   ├── profile/
│   ├── recommendations/
│   └── users/
│
├── stores/                        # Pinia 状态管理
│   ├── achievement.ts
│   ├── auth.ts
│   ├── bookmark.ts
│   ├── contest.ts
│   ├── editorSettings.ts
│   ├── headerStore.ts
│   ├── notification.ts
│   ├── problemEditorStore.ts
│   ├── recommendation.ts
│   └── userStats.ts
│
├── router/                        # 路由配置
│   └── index.ts
│
├── composables/                  # Vue Composables
├── constants/                     # 常量定义
├── contexts/                      # Vue Contexts
├── features/                     # 功能模块
├── hooks/                        # Vue Hooks
├── i18n/                        # 国际化资源
├── types/                        # TypeScript 类型
├── utils/                        # 工具函数
│
├── App.vue
├── main.ts
├── style.css
└── pwa-register.ts
```

### 5.2 Management (管理后台)

```
management/src/
├── api/                           # API 客户端
│   ├── admin.ts
│   └── auth.ts
│
├── components/                    # 公共组件
│   ├── account/
│   ├── analytics/
│   ├── audit/
│   ├── auth/
│   ├── comments/
│   ├── contest/
│   ├── contests/
│   ├── dashboard/
│   ├── forum/
│   ├── moderation/
│   ├── notifications/
│   ├── problem-lists/
│   ├── problems/
│   ├── settings/
│   ├── solutions/
│   ├── submissions/
│   ├── system/
│   ├── tags/
│   └── users/
│
├── views/                        # 页面组件
│   ├── account/
│   ├── analytics/
│   ├── audit/
│   ├── auth/
│   ├── billing/
│   ├── comments/
│   ├── contest/
│   ├── contests/
│   ├── dashboard/
│   ├── forum/
│   ├── moderation/
│   ├── notifications/
│   ├── problem-lists/
│   ├── problems/
│   ├── settings/
│   ├── solutions/
│   ├── submissions/
│   ├── system/
│   ├── tags/
│   └── users/
│
├── stores/                       # Pinia 状态管理
├── router/                       # 路由配置
├── composables/                 # Vue Composables
├── constants/                    # 常量定义
├── i18n/                        # 国际化资源
├── types/                       # TypeScript 类型
└── utils/                       # 工具函数
│
├── App.vue
├── main.ts
└── style.css
```

## 6. 推荐服务架构 (Dubbo3 + Spark)

```
recommendation/
├── recommend-api/                 # API 接口定义
├── recommend-core/               # 核心逻辑
├── recommend-feature/            # 特性模块
├── recommend-provider/           # Dubbo Provider (端口 9004)
├── recommend-spark/              # Spark ML 计算
├── recommend-web/                # Dubbo Web (端口 9005)
├── pom.xml
└── README.md
```

## 7. 数据库架构

### 7.1 Flyway 迁移版本

| Version | Description                    |
| ------- | ------------------------------ |
| V1     | 核心用户表 (users/submissions/permissions) |
| V2     | 题目系统 (problems/tags/lists) |
| V3     | 竞赛系统 (contests/rankings)   |
| V4     | 论坛系统 (forum)               |
| V5     | 订阅系统 (subscription)         |
| V6     | 审核系统 (moderation)          |
| V7     | 推荐系统 (recommendation)      |
| V8     | 收藏集 (collections)           |
| V9     | 题解 (solutions)               |
| V10+   | 增量优化                       |

### 7.2 db-manager 工具

```
db-manager/
├── migrations/                    # SQL 迁移文件
├── flyway/                       # Flyway 配置
├── src/                          # Python 源码
├── .venv/                        # Python 虚拟环境
├── pyproject.toml
└── README.md
```

## 8. 关键配置文件

| File | Purpose |
| ---- | ------- |
| `.env` | 主环境变量配置 |
| `docker-compose.yml` | Docker 服务编排 |
| `ecosystem.config.cjs` | PM2 进程管理 |
| `backend-spring/.env` | Spring Boot 配置 |
| `db-manager/.venv/bin/python -m db_manager.cli` | 数据库迁移CLI |

## 9. Docker 服务

```yaml
services:
  mysql:
    image: mysql:9.1
    port: 23306
    volumes: mysql_data

  redis:
    image: redis:7-alpine
    port: 26379
    volumes: redis_data

  nacos:
    image: nacos/nacos-server:v2.3.2
    port: 28848
    volumes: nacos_logs
```

## 10. PM2 服务配置

```javascript
// ecosystem.config.cjs
apps: [
  { name: 'ulticode-9001', cwd: './backend-spring', port: 9001 },
  { name: 'ulticode-9002', cwd: './console', port: 9002 },
  { name: 'ulticode-9003', cwd: './management', port: 9003 },
  { name: 'ulticode-9004', cwd: './recommendation', port: 9004 },
  { name: 'ulticode-9005', cwd: './recommendation', port: 9005 },
]
```

## 11. 响应格式

### 统一 API 响应

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

### 分页响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "size": 20,
    "current": 1
  }
}
```

## 12. 技术栈

| Layer          | Technology                                    |
|----------------|-----------------------------------------------|
| Backend        | Spring Boot 3.5, Java 17                   |
| ORM            | MyBatis-Plus 3.5.x                          |
| Auth           | JWT (jjwt 0.13.x), Spring Security           |
| Cache/Locks    | Redisson 4.3.x                               |
| Service Mesh   | Dubbo3 3.3.x, Nacos 2.3.x                   |
| Search         | MeiliSearch                                   |
| Frontends      | Vue 3, Vite, Tailwind CSS v4                 |
| Recommendation | Dubbo3 + Apache Spark (optional)              |
| Database       | MySQL 9.x, Flyway migrations                  |
| Process Mgmt   | PM2                                           |
| Containers     | Docker, Docker Compose                        |

## 13. 认证与授权

### JWT + CSRF 架构

```
Request
    │
    ▼
┌───────────────┐
│ JWT Filter   │ Extract token from cookie or Authorization header
└───────┬───────┘
        │ Valid token?
        ▼
┌───────────────┐
│ Security     │ Set Authentication in SecurityContext
│ Context      │ (ROLE_USER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
└───────┬───────┘
        │
        ▼
┌───────────────┐
│ @PreAuthorize │ Method-level authorization
│ or permitAll()│
└───────────────┘
```

### 公开端点（无需认证）

- `GET /problems/**` — 题目读取
- `GET /contest/**` — 竞赛查看
- `GET /submissions/statuses` — 状态元数据
- `GET /api/solutions/**` — 题解读取
- `GET /forum/posts/**` — 论坛读取
- `POST /auth/login`, `/auth/register`, `/auth/refresh`
- `WS /ws/**` — WebSocket 握手

### 角色权限

| Role          | Access Level                                      |
|---------------|--------------------------------------------------|
| Anonymous     | 公开端点                                         |
| USER          | 个人数据、提交记录、论坛发帖                     |
| ADMIN         | 用户管理、题目管理、内容审核                     |
| SUPER_ADMIN   | 完全访问权限，包括系统配置                       |
