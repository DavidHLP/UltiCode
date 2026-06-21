---
title: 后端模块全景
tags: [mirror, architecture, backend]
status: living
updated: 2026-06-21
owner: architect
---

<!-- mirror: 手写 -->

# 后端模块全景

> Spring Boot 3.2.5 / Java 17 / MyBatis-Plus 3.5.16。包根 `com.ulticode`。分层 `controller → service(impl) → mapper → entity`，DTO 经 MapStruct。本页数据由 `find` 统计 controller/serviceImpl/mapper/entity 计数得出（2026-06-21）。

## 非模块包

| 包 | 内容 |
| --- | --- |
| `common/` | annotation / aspect / config / dto / exception / filter / metrics / response / service / util / validation |
| `security/` | `csrf/`（CsrfService、CsrfValidationFilter）、`jwt/`（JwtTokenProvider、JwtAuthenticationFilter、JwtProperties）、`oauth/`（OAuthProperties）、`AuthenticationEntryPointImpl` |
| `infrastructure/redis` | RedisService 封装 |
| `websocket/` | STOMP 端点（鉴权只认 access cookie） |

## 业务模块（25 个，按规模）

> 列 = controller / serviceImpl / mapper / entity 计数。

| 模块 | ctrl | svc | map | ent | 关键说明 |
| --- | --- | --- | --- | --- | --- |
| **contest** | 3 | 6 | 9 | 15 | 最重：6 service 含 scheduler/scoring/ranking/rating/scoringRule，见 [[contest]] |
| **admin** | 15 | 17 | 4 | 2 | 最大 controller 面：管理后台聚合 |
| **problem** | 3 | 3 | 9 | 9 | 题库 + 测试用例 |
| **moderation** | 1 | 1 | 6 | 11 | 审核队列 / 举报 / 申诉 |
| **forum** | 1 | 4 | 6 | 6 | 帖子 / 评论 / 互动 |
| **notification** | 1 | 2 | 3 | 5 | 见 [[notification-idempotency]] |
| **solution** | 2 | 2 | 3 | 3 | 题解 |
| **submission** | 2 | 1 | 1 | 1 | 判题入口，见 [[submission]] |
| **subscription** | 2 | 1 | 1 | 1 | 订阅 |
| **problemlist** | 1 | 1 | 4 | 4 | 题单 |
| **bookmark** | 1 | 1 | 2 | 3 | 收藏 |
| **backup** | 1 | 1 | 1 | 3 | 备份 |
| **vote** | 1 | 1 | 1 | 3 | 投票 |
| **email** | 1 | 1 | 2 | 2 | 邮件 |
| **achievement** | 1 | 2 | 2 | 2 | 成就 |
| **i18n** | 1 | 1 | 1 | 1 | 国际化 |
| **follow** | 1 | 1 | 1 | 1 | 关注 |
| **user** | 1 | 1 | 1 | 1 | 用户 |
| **queue** | 0 | 1 | 1 | 1 | 判题队列，无 controller（内部消费），见 [[judge-queue]] |
| **search** | 1 | 1 | 0 | 0 | Meilisearch |
| **auth** | 1 | 1 | 0 | 0 | 登录/注册/OAuth/重置，见 [[refresh-token]] |
| **edgeoperations** | 1 | 1 | 0 | 0 | 边缘操作（点赞等） |
| **monitoring** | 1 | 1 | 0 | 0 | 监控 |
| **refreshtoken** | 0 | 0 | 1 | 1 | 内部服务，见 [[refresh-token]] |
| **permission** | 0 | 0 | 2 | 2 | 权限 |
| **websocket** | 0 | 0 | 0 | 0 | STOMP 端点 |

## 三大重点模块链路

- **判题**：`submission` → `queue`（[[judge-queue]]）→ `submission/sandbox`（[[sandbox-d-form]]）→ 终态 → `SubmissionJudgedEvent` → `contest`（排名）/`achievement`/`notification`。
- **比赛**：`contest`（[[contest]]）+ `virtual` session（[[virtual-contest]]）。
- **认证**：`auth` + `security/jwt` + `refreshtoken`（[[refresh-token]]）+ `security/csrf`。

端到端见 [[judging-pipeline]]，前端对应见 [[frontend-apps]]。

## 技术栈要点

MyBatis-Plus 3.5.16 + jsqlparser、jjwt 0.13.0、redisson 4.3.1、MapStruct 1.6.3、testcontainers 1.21.4、springdoc-openapi、Hutool、Meilisearch 0.20.1、encoder 1.4.0（密码）、Caffeine、micrometer-prometheus、actuator。
