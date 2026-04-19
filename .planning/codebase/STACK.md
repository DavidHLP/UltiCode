# 技术栈总览 (STACK)

## 项目结构

```
UltiCode-Public-Next/
├── backend-spring/        # Spring Boot 后端服务
├── console/               # Vue 3 用户前端
├── management/            # Vue 3 管理后台
├── recommendation/        # Dubbo3 + Spark 推荐服务
├── shared/auth-core/      # 共享认证核心库
└── db-manager/            # Flyway 数据库迁移管理
```

---

## 后端 (Backend Spring)

| 组件 | 版本 | 说明 |
|------|------|------|
| **Spring Boot** | 3.2.5 | 主框架 |
| **Java** | 17 | 运行时 |
| **MyBatis-Plus** | 3.5.16 | ORM 框架 |
| **MySQL Connector** | 8.0.33 | MySQL 驱动 |
| **Redisson** | 4.3.1 | Redis 客户端 (分布式锁) |
| **JWT (jjwt)** | 0.13.0 | Token 认证 |
| **SpringDoc OpenAPI** | 2.6.0 | Swagger 文档 |
| **Dubbo** | 3.2.14 | RPC 框架 (调用推荐服务) |
| **Hutool** | 5.8.44 | Java 工具库 |
| **Lombok** | 1.18.44 | 编译时注解 |
| **MapStruct** | 1.6.3 | 对象映射 |
| **MeiliSearch** | 0.20.0 | 搜索引擎 |
| **OkHttp** | 5.3.2 | HTTP 客户端 |
| **OWASP Encoder** | 1.4.0 | XSS 防护 |
| **Testcontainers** | 1.11.3 | 测试容器 |

### 后端模块 (25+)

`auth`, `user`, `problem`, `submission`, `contest`, `forum`, `solution`, `notification`, `subscription`, `moderation`, `search`, `achievement`, `i18n`, `backup`, `email`, `monitoring`, `vote`, `admin`, `bookmark`, `edgeoperations`, `permission`, `problemlist`, `queue`, `recommendation`, `refreshtoken`, `websocket`

---

## 前端 (Console & Management)

| 组件 | Console | Management |
|------|---------|------------|
| **Vue** | 3.5.26 | 3.5.26 |
| **Vite** | 8.x | 8.x |
| **Tailwind CSS** | 4.1.18 | 4.1.18 |
| **TypeScript** | ~6.0.3 | ~5.9.3 |
| **Pinia** | 3.0.4 | 3.0.4 |
| **Vue Router** | 5.0.4 | 5.0.4 |
| **vue-i18n** | 10.0.8 | 10.0.8 |
| **ESLint** | 10.2.1 | 10.2.1 |
| **Prettier** | 3.8.3 | 3.8.3 |
| **Vitest** | 4.0.15 | 4.0.15 |

### Console 特有依赖

- `@monaco-editor/loader` - 代码编辑器
- `highlight.js` - 代码高亮
- `markdown-it` + `markdown-it-katex` - Markdown 渲染
- `vue-sonner` - 通知提示
- `katex` - 数学公式

### Management 特有依赖

- `@unovis/ts` + `@unovis/vue` - 数据可视化
- `@dnd-kit/*` - 拖拽排序
- `@tanstack/vue-table` - 表格组件
- `embla-carousel-vue` - 轮播组件

---

## 推荐服务 (Recommendation)

| 组件 | 版本 | 说明 |
|------|------|------|
| **Dubbo** | 3.2.14 | RPC 框架 |
| **Spark** | 3.5.1 | 大数据计算 |
| **Scala** | 2.13.12 | Spark 语言 |
| **Java** | 17 | 运行时 |

### 推荐服务模块

- `recommend-api` - API 接口定义
- `recommend-core` - 核心算法
- `recommend-feature` - 特征工程
- `recommend-provider` - Dubbo 服务提供者
- `recommend-web` - Dubbo 服务消费者
- `recommend-spark` - Spark 离线计算

---

## 数据库与缓存

| 服务 | 版本 | 端口 | 说明 |
|------|------|------|------|
| **MySQL** | 9.1 | 23306 | 主数据库 |
| **Redis** | 7-alpine | 26379 | 缓存/会话/限流 |
| **Nacos** | 2.3.2 | 28848 | 服务发现/配置中心 |

---

## 基础设施

| 工具 | 版本 | 说明 |
|------|------|------|
| **PM2** | latest | 进程管理器 |
| **Docker Compose** | latest | 容器编排 |
| **Flyway** | via db-manager | 数据库迁移 |
| **Node.js** | 20.19.0 / 22.12.0+ | 前端运行时 |
| **pnpm** | latest | 前端包管理器 |
| **Maven** | 3.x | Java 构建工具 |

---

## 端口映射

| 服务 | 端口 |
|------|------|
| Backend (Spring Boot) | 9001 |
| Console Frontend | 9002 |
| Management Frontend | 9003 |
| Recommend-Provider | 9004 |
| Recommend-Web | 9005 |
| MySQL | 23306 |
| Redis | 26379 |
| Nacos | 28848 |
| Nacos SDK Port | 29848 |
