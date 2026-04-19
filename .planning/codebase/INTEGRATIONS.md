# 服务集成 (INTEGRATIONS)

## 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Layer                           │
├────────────────────────┬────────────────────────────────────────┤
│  Console (9002)        │  Management (9003)                    │
│  Vue 3 + Vite           │  Vue 3 + Vite                          │
│  Tailwind CSS 4        │  Tailwind CSS 4                        │
└────────────┬───────────┴──────────────────┬─────────────────────┘
             │                              │
             └──────────────┬───────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Backend (9001)                                │
│                    Spring Boot 3.2.5                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ MyBatis-Plus│  │   Redisson  │  │      Dubbo 3.2.14        │ │
│  │  ORM Layer   │  │ Redis Client│  │  (→ Recommendation Svc)  │ │
│  └──────┬────── ┘  └──────┬──────┘  └────────────┬────────────┘ │
│         │                 │                      │              │
└─────────┼─────────────────┼──────────────────────┼──────────────┘
          │                 │                      │
          ▼                 ▼                      ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────────────────┐
│    MySQL     │    │    Redis     │    │    Recommendation (Dubbo) │
│   (23306)    │    │   (26379)    │    │         (9004/9005)      │
│   9.1        │    │  7-alpine    │    │    Dubbo3 + Spark 3.5.1  │
└──────────────┘    └──────────────┘    └──────────────────────────┘
                                                ▲
                                                │
                          ┌─────────────────────┴───────────────┐
                          │           Nacos (28848)              │
                          │        Service Discovery             │
                          └─────────────────────────────────────┘
```

---

## MySQL (主数据库)

### 连接信息

```yaml
host: localhost
port: 23306
database: ulticode
user: ulticode
password: ${DB_PASSWORD}
```

### 特性

- **ORM**: MyBatis-Plus 3.5.16
- **迁移**: Flyway (通过 db-manager)
- **连接池**: HikariCP (Spring Boot 默认)
- **字符集**: UTF-8 with `characterEncoding=utf8`

### 数据分布 (Seed Data)

- **V1**: users, submissions, permissions
- **V2**: problems, tags, lists
- **V3**: contests, rankings
- **V4**: forum
- **V8**: collections
- **V9**: solutions

---

## Redis (缓存/会话/限流)

### 连接信息

```yaml
host: localhost
port: 26379
password: ${REDIS_PASSWORD}
```

### 用途

| 用途 | 实现 |
|------|------|
| 会话存储 | Redisson Session |
| 分布式锁 | Redisson Lock |
| 限流 | Bucket4j + Redisson |
| 缓存 | Spring Cache Abstraction |

### 配置

```yaml
# Spring Boot Data Redis
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:26379}
      password: ${REDIS_PASSWORD}
```

---

## Nacos (服务发现与配置)

### 连接信息

```yaml
host: localhost
port: 28848
username: ${NACOS_USERNAME}
password: ${NACOS_PASSWORD}
```

### 用途

- **服务注册**: Recommendation 服务注册到 Nacos
- **配置管理**: 分布式配置集中管理
- **模式**: 单机模式 (standalone)

### 服务发现配置

```yaml
dubbo:
  registry:
    address: nacos://${NACOS_HOST:localhost}:${NACOS_PORT:28848}
    group: DEFAULT_GROUP
    parameters:
      enable-empty-protection: "true"
```

---

## Recommendation Service (Dubbo3 + Spark)

### 服务架构

```
Backend (9001)  ──Dubbo RPC──►  Recommend-Provider (9004)
                                       │
                                       ▼
                              Nacos (28848)
                              Service Registration
                                       │
                                       ▼
                              Recommend-Web (9005)
                                       │
                                       ▼
                              Spark 3.5.1 Offline Computing
```

### Dubbo 版本

- **Provider**: Dubbo 3.2.14
- **Consumer (Backend)**: Dubbo 3.2.14

### 接口定义

- `recommend-api` 模块定义服务接口
- Backend 通过 Dubbo RPC 调用推荐服务

### Spark 组件

| 组件 | 版本 |
|------|------|
| spark-core | 3.5.1 |
| spark-sql | 3.5.1 |
| spark-mllib | 3.5.1 |
| scala-binary | 2.13 |

---

## MeiliSearch (搜索服务)

### 用途

- 问题搜索
- 全文检索

### 依赖

```xml
<dependency>
    <groupId>com.meilisearch.sdk</groupId>
    <artifactId>meilisearch-java</artifactId>
    <version>0.20.0</version>
</dependency>
```

---

## 前端集成

### API 调用

- **Base URL**: `http://localhost:9001`
- **请求库**: Axios
- **响应格式**: `{ code, message, data, traceId }`

### 认证流程

```
1. Login → JWT Token (httpOnly Cookie)
2. Refresh Token → 续期机制
3. CSRF Token → X-CSRF-Token Header
```

### 环境变量

```yaml
# Console (.env)
VITE_API_BASE_URL=http://localhost:9001

# Management (.env)
VITE_API_BASE_URL=http://localhost:9001
```

---

## Docker 容器

### 容器列表

| 容器名 | 镜像 | 端口 |
|--------|------|------|
| ulticode-mysql | mysql:9.1 | 23306 |
| ulticode-redis | redis:7-alpine | 26379 |
| ulticode-nacos | nacos/nacos-server:v2.3.2 | 28848, 29848 |

### 启动命令

```bash
# PM2 启动所有服务
pm2 start ecosystem.config.cjs

# Docker 容器独立管理
docker compose up -d
```

---

## 环境变量清单

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | localhost | MySQL 主机 |
| `DB_PORT` | 23306 | MySQL 端口 |
| `DB_NAME` | ulticode | 数据库名 |
| `DB_USER` | ulticode | 数据库用户 |
| `DB_PASSWORD` | - | 数据库密码 |
| `REDIS_HOST` | localhost | Redis 主机 |
| `REDIS_PORT` | 26379 | Redis 端口 |
| `REDIS_PASSWORD` | - | Redis 密码 |
| `NACOS_HOST` | localhost | Nacos 主机 |
| `NACOS_PORT` | 28848 | Nacos 端口 |
| `NACOS_USERNAME` | - | Nacos 用户名 |
| `NACOS_PASSWORD` | - | Nacos 密码 |
| `JWT_SECRET` | - | JWT 密钥 |
| `SPRING_PROFILES_ACTIVE` | dev | Spring 环境 |
| `RECOMMENDATION_ENABLED` | true | 推荐服务开关 |
