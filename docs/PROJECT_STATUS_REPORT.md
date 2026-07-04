# UltiCode 项目进度报告

> 生成时间：2026-06-19 | 生成工具：Claude Code

---

## 一、项目概览

**UltiCode** 是一个在线编程评测平台 (Online Judge)，支持代码提交、自动判题、竞赛系统、论坛讨论等完整功能。

| 维度 | 数据 |
|------|------|
| 后端源文件 | 713 个 Java 文件，26 个业务模块 |
| 前端源文件 | Console 349 个 Vue 文件 (13 模块)，Management 479 个 Vue 文件 (19 模块) |
| 共享包 | 5 个 (@ulticode/auth-core, sandbox-types, badge-config, theme, design-system) |
| 数据库迁移 | 34 个 Flyway 迁移脚本 (2026-06-02 ~ 2026-06-17) |
| 测试文件 | 后端 120 个测试文件 |
| 开发周期 | 约 2.5 周密集迭代 |

---

## 二、技术栈

### 后端

| 层 | 技术 | 版本 |
|---|---|---|
| 框架 | Spring Boot | 3.2.5 |
| 语言 | Java | 17 |
| ORM | MyBatis-Plus | 3.5.16 |
| 数据库 | MySQL | 9.1 |
| 缓存/分布式锁 | Redis + Redisson | 4.3.1 |
| 认证 | Spring Security + JWT (jjwt) | 0.13.0 |
| API 文档 | SpringDoc OpenAPI (Swagger) | 2.6.0 |
| 搜索 | MeiliSearch | 0.20.1 (可选，默认关闭) |
| WebSocket | Spring Boot WebSocket | - |
| 邮件 | Spring Boot Mail | - |
| 监控 | Actuator + Micrometer Prometheus | - |
| 本地缓存 | Caffeine | - |
| 数据库迁移 | Flyway | - |
| 工具库 | Hutool 5.8.44, MapStruct 1.6.3, Lombok 1.18.46 | - |
| 测试 | JUnit 5, Testcontainers (MySQL+Redis), Mockito | - |
| 覆盖率 | JaCoCo 0.8.14 (阈值: 5%行/2%分支) | - |

### 前端 (Console + Management 共享)

| 层 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue 3 (Composition API) | 3.5 |
| 语言 | TypeScript | ~6.0 |
| 构建工具 | Vite | 8 |
| 状态管理 | Pinia | 3 |
| 路由 | vue-router | 5 |
| 国际化 | vue-i18n | 11 |
| CSS | Tailwind CSS | 4 |
| UI 组件 | reka-ui (headless), Lucide icons | 2 |
| HTTP | Axios | - |
| 表单校验 | Zod | 3.25 |
| 测试 | Vitest 4, @vue/test-utils, jsdom | - |
| Lint | ESLint 10, Prettier 3.8 | - |
| 包管理 | pnpm (workspace) | - |

### Console 独有依赖
- Monaco Editor (代码编辑器)
- markdown-it + KaTeX (Markdown/数学公式渲染)
- highlight.js (代码高亮)
- echarts / @unovis (数据可视化)
- @stomp/stompjs + sockjs-client (WebSocket 实时通知)
- workbox-window (PWA/Service Worker)

### Management 独有依赖
- @tanstack/vue-table (表格组件)
- vee-validate + @vee-validate/zod (表单校验)
- echarts + vue-echarts (分析图表)
- Playwright (E2E 测试)

---

## 三、架构与模块

### 后端模块清单 (26 个模块)

| 模块 | 文件数 | 职责 |
|---|---|---|
| admin | 145 | 管理后台、系统管理、用户/题目审核 |
| contest | 59 | 竞赛生命周期 (虚拟/真实)、评分、排行榜、Rating |
| submission | 53 | 代码提交、沙箱调度、判题结果处理 |
| problem | 43 | 题目 CRUD、测试用例、判题源配置 |
| moderation | 41 | 内容审核工作流 |
| notification | 34 | 通知分发 (传统 + ADR-004 intent-based) |
| forum | 32 | 讨论论坛 |
| problemlist | 28 | 题单/学习计划 |
| websocket | 26 | 实时推送 (排行榜、通知) |
| solution | 21 | 用户题解/题解编辑 |
| queue | 20 | 判题队列管理 (RQueue + Redisson Streams, ADR-003) |
| achievement | 20 | 成就/游戏化 |
| bookmark | 19 | 书签 |
| email | 15 | 邮件发送 (SMTP) |
| subscription | 12 | 用户订阅/计划管理 |
| auth | 12 | 认证 (JWT Cookie, OAuth GitHub/Google) |
| user | 11 | 用户资料、设置 |
| backup | 11 | 数据备份/导出 |
| vote | 9 | 投票 |
| monitoring | 9 | 健康检查、慢查询、Prometheus 指标 |
| i18n | 9 | 国际化 |
| follow | 8 | 关注/取关 |
| search | 7 | MeiliSearch 集成 |
| edgeoperations | 6 | 边缘操作工具 |
| permission | 5 | 权限/RBAC |
| refreshtoken | 3 | Refresh Token 管理 |

### 前端视图模块

**Console (用户端, 13 模块):**
achievements, auth, contest, dashboard, forum, personal, post-editor, problem-list, problem-set, problems, profile, submissions, users

**Management (管理端, 19 模块):**
account, analytics, audit, auth, comments, contest, contests, dashboard, forum, moderation, notifications, problem-lists, problems, settings, solutions, submissions, system, tags, users

### 共享包

| 包名 | 用途 |
|---|---|
| @ulticode/auth-core | 共享认证逻辑 (axios-based, Vue peer dep) |
| @ulticode/sandbox-types | 沙箱/判题子系统 TypeScript 类型定义 |
| @ulticode/badge-config | 徽章语义颜色、SemanticBadge 组件 |
| @ulticode/theme | 共享主题系统，跨前端主题同步 |
| design-system | 设计系统 (文档/资源) |

---

## 四、数据库迁移状态

**34 个 Flyway 迁移脚本**，从 2026-06-02 到 2026-06-17：

| 阶段 | 时间 | 内容 |
|---|---|---|
| Schema 创建 | 06-02 | 核心表结构 + 管理员种子数据 |
| 测试数据 | 06-03 | 题目、审计日志、题单、用户、题解、论坛、提交 |
| 数据对齐 | 06-04 | 管理员 ID 对齐、竞赛测试数据、全局排名 |
| 安全加固 | 06-06 | 安全化 Refresh Token + 锁定种子账号 |
| 功能迭代 | 06-08~06-17 | 审计日志修复、测试用例表、权限过期列、竞赛评分加固、通知账本等 |

---

## 五、基础设施配置

### Docker 服务

| 服务 | 镜像 | 用途 | 开发端口 |
|---|---|---|---|
| MySQL 9.1 | mysql:9.1 | 主数据库 (utf8mb4_unicode_ci) | 127.0.0.1:23306 |
| Redis 7 | redis:7-alpine | 缓存/会话存储 | 127.0.0.1:26379 |
| Nacos v2.3.2 | nacos/nacos-server:v2.3.2 | 服务发现/配置中心 | 127.0.0.1:28848 |

> 生产环境网络为 `internal: true`，不暴露端口。开发覆盖 (`docker-compose.dev.yml`) 仅绑定 localhost。

### PM2 服务

| 进程 | 端口 | 角色 |
|---|---|---|
| ulticode-9001 | 9001 | Spring Boot 后端 |
| ulticode-9002 | 9002 | Console 前端 (Vite dev server) |
| ulticode-9003 | 9003 | Management 前端 (Vite dev server) |
| ulticode-init-db | - | Flyway 迁移 (一次性) |
| ulticode-arthas | 8563 | Arthas MCP 诊断服务 |

---

## 六、当前运行状态

### ⚠️ 服务未启动

| 服务 | 状态 |
|---|---|
| PM2 | ❌ 未运行 |
| Docker (MySQL) | ❌ 未运行 |
| Docker (Redis) | ❌ 未运行 |
| Docker (Nacos) | ❌ 未运行 |
| Docker (RabbitMQ) | ⬆️ 运行中 (非项目依赖) |
| Docker (RocketMQ) | ❌ 已退出 (非项目依赖) |

**当前没有任何项目服务在运行。**

---

## 七、最近开发活动

### Git 提交记录 (最近 20 次)

最近的开发集中在 **竞赛系统 (Contest)** 的前端实现，经历了 R1~R10 共 10 轮结构化代码审查和功能迭代：

| 提交 | 类型 | 描述 |
|---|---|---|
| 8424883 | refactor | 提取 ContestProblemDock 组件 |
| 4c59f36 | fix | ContestReviewPanel i18n key 修复 |
| 675e06e | fix | useContestSocket 初始化时序修复 |
| c9866a3 | fix | ProblemContext inject 陷阱修复 |
| 5f63bf7 | fix | ProblemContextKey provide 顺序修复 |
| 6e5398a | chore | 添加 vitest/vue test-utils 等 devDeps |
| b9145ec | fix | 代码审查发现的问题修复 |
| eeb03d1 | feat | 赛后复盘面板 (Post-game review) |
| bfad859 | feat | 公告铃铛 + 按状态折叠规则 |
| 5ee5089 | feat | 竞赛题目 Shell + 提交反馈 |
| cd8e5e6 | feat | 竞赛题目列表行操作按钮 |
| a4f5e91 | fix | 竞赛页面导航替换为竞赛作用域 |
| aba5381 | fix | 虚拟会话绑定到 contest.id |
| d219bd8 | fix | 竞赛结束时显示题解 Tab |
| 96abcaf | fix | 竞赛进行中隐藏题解 Tab |
| c835c06 | fix | finish API 参数对齐 + 清除会话缓存 |
| d184786 | docs | Arthas MCP 实战文档 |
| be1e92f | fix | finishVirtualContest NPE 修复 |
| 8604e04 | fix | finishVirtualContest 幂等性修复 |
| 10fc3c8 | docs | R10 收口文档同步 |

### 开发特征

- **审查驱动**: 每轮 (R1-R10) 包含 HIGH/MED/LOW 严重级别的审查修复 + 功能提交 + 文档收口
- **ADR 驱动架构演进**: ADR-003 (判题队列)、ADR-004 (通知系统)、ADR-006 (评分引擎) 都通过 Feature Flag 保守推进
- **49 个审查发现** 在 R7 单轮中关闭，表明严格的代码审查纪律

---

## 八、架构风险与技术债

| 风险 | 级别 | 说明 |
|---|---|---|
| ADR-003 迁移未完成 | 中 | 判题队列从 RQueue 迁移到 Redisson Streams，Feature Flag 默认关闭 (legacy 路径活跃) |
| ADR-004 迁移未完成 | 中 | 通知系统迁移到 intent-based 分发器，Feature Flag 默认关闭 |
| JaCoCo 阈值过低 | 低 | 5% 行/2% 分支覆盖率，实际为象征性执行 |
| 沙箱 D-form 重构 | 低 | 配置中引用了 commit SHA 用于回滚，表明这是重大重构 |

---

## 九、如何启动项目

### 前置条件
- Java 17
- Node.js ^20.19.0 || >=22.12.0
- pnpm 10
- Docker & Docker Compose

### 一键启动
```bash
# 1. 首次运行：生成随机凭据写入 .env
./scripts/dev/init-env.sh

# 2. 启动基础设施、迁移、安装依赖、启动应用
./scripts/dev/up.sh

# 后续启动（依赖未变时）
./scripts/dev/up.sh --skip-install
```

### 手动启动
```bash
# 1. 启动基础设施
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis nacos

# 2. 运行数据库迁移
pm2 restart ulticode-init-db

# 3. 启动所有应用
pm2 start ecosystem.config.cjs

# 4. 查看状态
pm2 status
pm2 logs
```

### 访问地址
| 服务 | 地址 |
|---|---|
| 后端 API | http://localhost:9001 |
| Console 前端 | http://localhost:9002 |
| Management 前端 | http://localhost:9003 |
| Nacos 控制台 | http://localhost:28848/nacos |
| Arthas MCP | http://localhost:8563/mcp |

### 开发账号
- 管理员: `admin` / `admin123` (仅 dev profile)

---

## 十、建议下一步

1. **启动项目**: 运行 `./scripts/dev/up.sh` 启动完整开发环境
2. **了解竞赛系统**: 最近的开发集中在竞赛模块，建议先熟悉 `backend-spring/modules/contest/` 和 `console/src/views/contest/`
3. **关注 ADR 迁移**: ADR-003 (判题队列) 和 ADR-004 (通知系统) 有未完成的架构迁移，Feature Flag 默认关闭
4. **代码审查**: 项目有严格的 10 轮审查历史 (R1-R10)，建议阅读 `docs/` 下的审查文档了解上下文

---

*报告结束。如有疑问，可运行 `/start` 继续对话。*
