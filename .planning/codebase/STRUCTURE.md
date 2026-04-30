# UltiCode 项目结构

## 1. 根目录结构

```
UltiCode-Public-Next/
├── backend-spring/                  # Spring Boot 后端 (Java 17)
├── console/                        # 用户前端 (Vue 3, Vite)
├── management/                     # 管理后台 (Vue 3, Vite)
├── recommendation/                  # 推荐服务 (Dubbo3 + Spark)
├── db-manager/                     # 数据库迁移工具 (Flyway)
├── shared/                         # 共享资源
│
├── docker-compose.yml               # Docker 服务编排
├── docker-compose.prod.yml          # 生产环境 Docker 配置
├── ecosystem.config.cjs            # PM2 进程配置
├── .env                            # 环境变量配置
├── .env.example                    # 环境变量示例
│
├── package.json                    # 根目录 package (pnpm workspaces)
├── pnpm-lock.yaml
├── setup.sh                        # 安装脚本
│
└── .planning/                     # 项目规划目录
    ├── codebase/                   # 代码库分析
    ├── config.json                 # GSD 配置
    ├── graphs/                     # 知识图谱
    └── intel/                      # 情报文件
```

## 2. Backend Spring 结构

```
backend-spring/
├── src/main/java/com/ulticode/
│   ├── UlticodeBackendApplication.java
│   │
│   ├── common/                     # 公共组件
│   │   ├── annotation/             # 自定义注解
│   │   ├── aspect/                # AOP 切面
│   │   ├── config/                # 配置类
│   │   ├── constants/             # 常量
│   │   ├── dto/                   # 公共 DTO
│   │   ├── exception/             # 异常处理
│   │   ├── filter/                # 过滤器
│   │   ├── response/             # 响应封装
│   │   ├── service/               # 公共服务
│   │   └── util/                  # 工具类
│   │
│   ├── security/                   # 安全模块
│   │   ├── AuthenticationEntryPointImpl.java
│   │   ├── csrf/
│   │   ├── jwt/
│   │   └── oauth/
│   │
│   ├── modules/                   # 业务模块 (26个)
│   │   ├── achievement/           # 成就系统
│   │   ├── admin/                # 超级管理员
│   │   ├── auth/                  # 认证授权
│   │   ├── backup/               # 数据备份
│   │   ├── bookmark/             # 收藏功能
│   │   ├── contest/              # 竞赛系统
│   │   ├── edgeoperations/       # 边界操作
│   │   ├── email/                # 邮件服务
│   │   ├── follow/               # 关注功能
│   │   ├── forum/                # 论坛帖子
│   │   ├── i18n/                 # 国际化
│   │   ├── moderation/           # 内容审核
│   │   ├── monitoring/           # 监控告警
│   │   ├── notification/         # 通知系统
│   │   ├── permission/           # 权限管理
│   │   ├── problem/              # 题目管理
│   │   ├── problemlist/           # 题目列表
│   │   ├── queue/                # 评判队列
│   │   ├── recommendation/       # 推荐服务
│   │   ├── refreshtoken/         # Token刷新
│   │   ├── search/               # 搜索功能
│   │   ├── solution/             # 题解管理
│   │   ├── submission/           # 提交评判
│   │   ├── subscription/         # 订阅功能
│   │   ├── user/                 # 用户管理
│   │   ├── vote/                 # 投票功能
│   │   └── websocket/            # WebSocket
│   │
│   └── websocket/                 # WebSocket 配置
│
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-prod.yml
│
├── src/test/java/                  # 测试代码
├── start.cjs                       # 启动脚本
├── pom.xml                         # Maven 配置
└── .env                            # 环境变量
```

## 3. Console 前端结构

```
console/
├── src/
│   ├── api/                        # API 客户端 (20个)
│   │   ├── __tests__/
│   │   ├── achievement.ts
│   │   ├── auth.ts
│   │   ├── bookmark.ts
│   │   ├── contest.ts
│   │   ├── edge-operations.ts
│   │   ├── follow.ts
│   │   ├── forum.ts
│   │   ├── interaction.ts
│   │   ├── notification.ts
│   │   ├── problem.ts
│   │   ├── problem-detail.ts
│   │   ├── problem-list.ts
│   │   ├── recommendation.ts
│   │   ├── search.ts
│   │   ├── solution.ts
│   │   ├── submission.ts
│   │   ├── subscription.ts
│   │   ├── topic.ts
│   │   ├── user.ts
│   │   ├── userStats.ts
│   │   └── vote.ts
│   │
│   ├── components/                 # Vue 组件
│   │   ├── achievement/
│   │   ├── bookmark/
│   │   ├── comments/
│   │   ├── common/
│   │   ├── dashboard/
│   │   ├── editor/
│   │   ├── edge-operations/
│   │   ├── follow/
│   │   ├── markdown/
│   │   ├── notification/
│   │   ├── problem/
│   │   ├── search/
│   │   ├── ui/
│   │   └── LanguageSwitcher.vue
│   │
│   ├── views/                     # 页面
│   │   ├── achievements/
│   │   ├── auth/
│   │   ├── contest/
│   │   ├── dashboard/
│   │   ├── forum/
│   │   ├── personal/
│   │   ├── problem-list/
│   │   ├── problems/
│   │   ├── profile/
│   │   ├── recommendations/
│   │   └── users/
│   │
│   ├── stores/                    # Pinia 状态
│   │   ├── achievement.ts
│   │   ├── auth.ts
│   │   ├── bookmark.ts
│   │   ├── contest.ts
│   │   ├── editorSettings.ts
│   │   ├── headerStore.ts
│   │   ├── notification.ts
│   │   ├── problemEditorStore.ts
│   │   ├── recommendation.ts
│   │   └── userStats.ts
│   │
│   ├── router/                    # 路由
│   │   └── index.ts
│   │
│   ├── composables/               # Vue Composables
│   ├── constants/                 # 常量
│   ├── contexts/                  # Vue Contexts
│   ├── features/                  # 功能模块
│   ├── hooks/                    # Vue Hooks
│   ├── i18n/                     # 国际化
│   ├── types/                    # TypeScript 类型
│   └── utils/                    # 工具函数
│
│   ├── App.vue
│   ├── main.ts
│   ├── style.css
│   └── pwa-register.ts
│
├── public/
├── package.json
└── vite.config.ts
```

## 4. Management 前端结构

```
management/
├── src/
│   ├── api/                       # API 客户端
│   │   ├── admin.ts
│   │   └── auth.ts
│   │
│   ├── components/                # Vue 组件
│   │   ├── account/
│   │   ├── analytics/
│   │   ├── audit/
│   │   ├── auth/
│   │   ├── comments/
│   │   ├── contest/
│   │   ├── contests/
│   │   ├── dashboard/
│   │   ├── forum/
│   │   ├── moderation/
│   │   ├── notifications/
│   │   ├── problem-lists/
│   │   ├── problems/
│   │   ├── settings/
│   │   ├── solutions/
│   │   ├── submissions/
│   │   ├── system/
│   │   ├── tags/
│   │   └── users/
│   │
│   ├── views/                     # 页面
│   │   ├── account/
│   │   ├── analytics/
│   │   ├── audit/
│   │   ├── auth/
│   │   ├── billing/
│   │   ├── comments/
│   │   ├── contest/
│   │   ├── contests/
│   │   ├── dashboard/
│   │   ├── forum/
│   │   ├── moderation/
│   │   ├── notifications/
│   │   ├── problem-lists/
│   │   ├── problems/
│   │   ├── settings/
│   │   ├── solutions/
│   │   ├── submissions/
│   │   ├── system/
│   │   ├── tags/
│   │   └── users/
│   │
│   ├── stores/                   # Pinia 状态
│   ├── router/                   # 路由
│   ├── composables/              # Vue Composables
│   ├── constants/                # 常量
│   ├── i18n/                    # 国际化
│   ├── types/                   # TypeScript 类型
│   └── utils/                   # 工具函数
│
│   ├── App.vue
│   ├── main.ts
│   └── style.css
│
├── shared/                       # 共享资源 (符号链接)
├── public/
├── package.json
└── vite.config.ts
```

## 5. Recommendation 服务结构

```
recommendation/
├── recommend-api/                # API 接口定义
├── recommend-core/                # 核心逻辑
├── recommend-feature/             # 特性模块
├── recommend-provider/             # Dubbo Provider (9004)
├── recommend-spark/               # Spark ML 计算
├── recommend-web/                 # Dubbo Web (9005)
├── docs/
├── pom.xml
├── start-provider.cjs
├── start-web.cjs
└── run-evaluation.sh
```

## 6. Database Manager 结构

```
db-manager/
├── migrations/                     # Flyway SQL 迁移 (V1-V27+)
│   ├── V1__core_schema.sql
│   ├── V2__problem_schema.sql
│   ├── V3__contest_schema.sql
│   ├── V4__forum_schema.sql
│   ├── V5__subscription_schema.sql
│   ├── V6__moderation_schema.sql
│   ├── V7__recommendation_schema.sql
│   ├── V8__collection_schema.sql
│   ├── V9__solution_schema.sql
│   ├── V10__daily_recommendations_feedback.sql
│   └── ... (V10 - V27+)
│
├── flyway/                        # Flyway 配置
├── src/                          # Python 源码
├── .venv/                        # Python 虚拟环境
├── pyproject.toml
└── README.md
```

## 7. Shared 目录结构

```
shared/
├── api-utils/                     # API 工具函数
├── auth-core/                     # 认证核心逻辑
│   ├── index.ts
│   ├── client.ts
│   ├── errors.ts
│   ├── hooks.ts
│   ├── store.ts
│   └── types.ts
└── types/                        # 共享类型定义
```

## 8. 配置目录

```
.planning/
├── codebase/
│   ├── ARCHITECTURE.md           # 系统架构文档
│   └── STRUCTURE.md              # 本文档
├── config.json                   # GSD 项目配置
├── graphs/                       # 知识图谱
└── intel/                        # 情报文件
```

## 9. Docker 配置

```
docker/
└── initdb/                       # MySQL 初始化脚本

docker-compose.yml                 # 开发环境 Docker 配置
docker-compose.prod.yml           # 生产环境 Docker 配置
docker-wrapper.cjs                 # PM2 Docker 包装脚本
.dockerignore
```

## 10. 根目录配置文件

| File | Description |
| ---- | ----------- |
| `.env` | 主环境变量 (数据库、Redis、JWT、Nacos、推荐服务) |
| `.env.example` | 环境变量模板 |
| `package.json` | pnpm workspaces 根配置 |
| `ecosystem.config.cjs` | PM2 进程管理配置 |
| `docker-compose.yml` | Docker 服务编排 (MySQL/Redis/Nacos) |
| `setup.sh` | 快速安装脚本 |
| `AGENTS.md` | AI 代理指导文档 |
| `PLAN.md` | 项目计划文档 |
| `entities.json` | 实体定义 |
| `mempalace.yaml` | 内存管理配置 |
| `SECURITY.md` | 安全配置文档 |

## 11. 后端模块标准结构

每个后端模块遵循标准 Maven/Spring 结构:

```
module-name/
├── controller/
│   └── XxxController.java        # REST 控制器
├── dto/
│   ├── XxxCreateDTO.java
│   ├── XxxUpdateDTO.java
│   └── XxxVO.java
├── entity/
│   └── Xxx.java                  # MyBatis-Plus 实体
├── mapper/
│   └── XxxMapper.java             # MyBatis Mapper
└── service/
    ├── XxxService.java           # 服务接口
    └── impl/
        └── XxxServiceImpl.java   # 服务实现
```

## 12. 构建与运行命令

```bash
# 安装前端依赖
pnpm install

# 启动所有服务 (PM2)
pm2 start ecosystem.config.cjs

# 仅后端
cd backend-spring && ./mvnw spring-boot:run

# Console 前端 (开发)
cd console && pnpm run dev

# Management 前端 (开发)
cd management && pnpm run dev

# 运行数据库迁移
cd db-manager && .venv/bin/python -m db_manager.cli migrate

# 构建前端
cd console && pnpm build
cd management && pnpm build

# 构建后端
cd backend-spring && ./mvnw package -DskipTests
```

## 13. 数据库迁移顺序

迁移文件遵循版本号顺序 (V1 → V27+):

| Version | Content                                                   |
|---------|-----------------------------------------------------------|
| V1      | 用户表、权限、认证                                        |
| V2      | 题目、标签、难度                                         |
| V3      | 竞赛、排名、参与                                        |
| V4      | 论坛、帖子、评论                                        |
| V5      | 题解                                                     |
| V6      | 通知                                                     |
| V7      | 成就、徽章                                               |
| V8      | 收藏集                                                   |
| V9+     | 扩展功能、索引、数据扩展                                 |

## 14. 关键文件命名约定

| Type              | 后缀            | Example                          |
| -----------------| ----------------| --------------------------------|
| Controller        | `Controller.java` | `ProblemController.java`        |
| Service Interface | `Service.java`   | `ProblemService.java`           |
| Service Impl      | `ServiceImpl.java`| `ProblemServiceImpl.java`       |
| Mapper            | `Mapper.java`    | `ProblemMapper.java`             |
| Entity            | (无后缀)         | `Problem.java`                 |
| Request DTO       | `Request.java`   | `CreateProblemRequest.java`     |
| Response DTO      | `Response.java`  | `ProblemDetailResponse.java`     |
| Config            | `Config.java`    | `SecurityConfig.java`           |
| Exception         | `Exception.java` | `BusinessException.java`        |
| Constant          | `Constant.java`  | `RedisConstant.java`            |
