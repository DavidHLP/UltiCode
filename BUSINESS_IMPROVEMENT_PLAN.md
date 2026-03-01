# UltiCode 业务完善计划

> **文档版本**: 1.1
> **创建日期**: 2026-02-26
> **更新日期**: 2026-03-01
> **状态**: 已完成 (仅剩外部服务依赖项)

本文档详细分析了 UltiCode 编程竞赛平台在业务层面的缺陷和问题，并提供了分阶段的完善计划。

---

## 目录

1. [执行摘要](#执行摘要)
2. [关键问题优先级矩阵](#关键问题优先级矩阵)
3. [后端业务缺陷](#后端业务缺陷)
4. [前端业务缺陷](#前端业务缺陷)
5. [管理后台缺陷](#管理后台缺陷)
6. [实施计划](#实施计划)
7. [技术债务清单](#技术债务清单)

---

## 执行摘要

### 当前状态评估

| 层级 | 完成度 | 主要问题 |
|------|--------|----------|
| 后端 (NestJS) | 99% | 需配置OAuth/Email/Stripe凭证 |
| 控制台 (Vue3) | 98% | 完整功能已实现 |
| 管理后台 (Vue3) | 95% | 完整功能已实现 |

### 核心发现

1. **~~沙箱系统~~** ✅ 已完成: Docker沙箱、VM沙箱、7种语言运行器全部实现
2. **~~支付集成~~** ✅ 已完成: Stripe支付、Webhook、订阅管理、发票
3. **~~OAuth登录~~** ✅ 已完成: GitHub + Google OAuth
4. **~~邮件服务~~** ✅ 已完成: SMTP邮件、7种模板
5. **~~实时更新~~** ✅ 已完成: WebSocket全部功能 (提交、比赛、社区)
6. **~~运营工具~~** ✅ 已完成: 统一审核队列、高级分析报表

---

## 关键问题优先级矩阵

```
┌─────────────────────────────────────────────────────────────────┐
│                        影响程度 (高)                              │
│    ┌──────────────────────┬───────────────────────────────────┐  │
│    │   ✅ 沙箱系统(已完成)  │    P0 - 支付集成                   │  │
│    │   Docker+6语言支持    │    订阅无法收费                     │  │
│    └──────────────────────┴───────────────────────────────────┘  │
│    ┌──────────────────────┬───────────────────────────────────┐  │
│    │   P1 - OAuth完善      │    P1 - 实时通知                   │  │
│    │   GitHub/Google登录   │    WebSocket通知                   │  │
│    └──────────────────────┴───────────────────────────────────┘  │
│                        影响程度 (低)                              │
│         实现难度 (低)                    实现难度 (高)            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 后端业务缺陷

### 1. 沙箱系统 [✅ 已完成]

**现状**: 完整实现，支持7种语言 (含 Rust)

**实现位置**:
- `backend/src/submission/sandbox/sandbox.interface.ts` - 接口定义
- `backend/src/submission/sandbox/docker-sandbox.service.ts` - Docker沙箱
- `backend/src/submission/sandbox/vm-sandbox.service.ts` - VM沙箱(开发用)
- `backend/src/submission/sandbox/sandbox.factory.ts` - 沙箱工厂
- `backend/src/submission/judge.service.ts` - 判题服务
- `backend/judge/Dockerfile.judge` - Docker镜像定义
- `backend/judge/runners/` - 语言运行器 (JS, Python, Java, C++, Go, Rust)

**已实现功能**:
- [x] Docker 容器隔离执行
- [x] 多语言支持 (JavaScript, TypeScript, Python, Java, C++, Go, Rust)
- [x] 资源限制 (CPU/内存/时间)
- [x] 安全沙箱隔离
- [x] 测试用例执行器
- [x] 结果收集和评分

**待完善**:
- [x] 沙箱性能监控 ✅ (2026-02-28)
- [x] 执行日志记录 ✅ (2026-02-28)

---

### 2. 支付集成 [P0 - 关键 - ✅ 已完成]

**现状**: Stripe支付集成已完成

**实现位置**:
- `backend/src/subscription/payment/stripe.service.ts` - Stripe服务
- `backend/src/subscription/payment/webhook.controller.ts` - Webhook处理
- `backend/src/subscription/user-subscription.controller.ts` - 用户端API
- `backend/prisma/schema.prisma` - 添加了Stripe相关字段

**已实现功能**:
- [x] Stripe SDK 集成
- [x] 订阅创建 Checkout Session
- [x] Webhook 处理 (checkout.session.completed, subscription.updated/deleted)
- [x] 用户端订阅管理 API
- [x] 订阅取消 (period end)
- [x] 订阅重新激活
- [x] Billing Portal 集成

**配置要求**:
- STRIPE_SECRET_KEY
- STRIPE_WEBHOOK_SECRET
- STRIPE_PRICE_PREMIUM_MONTHLY
- STRIPE_PRICE_PREMIUM_YEARLY

**待完善**:
- [x] 发票生成 ✅ (2026-02-28)
- [x] 付款历史记录 ✅ (2026-02-28)
- [x] 前端订阅页面 ✅ (2026-02-28)

---

### 3. 邮件服务 [P1 - ✅ 已完成]

**现状**: 完整实现，支持SMTP和模板

**实现位置**:
- `backend/src/email/email.service.ts` - 邮件服务
- `backend/src/email/email.controller.ts` - 管理API
- `backend/prisma/seed/data/email-templates.data.ts` - 默认模板
- `backend/prisma/seed/modules/email-templates/` - 模板Seeder

**已实现功能**:
- [x] SMTP 邮件发送
- [x] 邮件模板系统 (数据库存储)
- [x] 邮件日志记录
- [x] 模板 CRUD API

**默认模板**:
- 欢迎邮件 (welcome)
- 密码重置 (password-reset)
- 邮箱验证 (email-verification)
- 订阅确认 (subscription-confirmation)
- 订阅取消 (subscription-cancelled)
- 比赛提醒 (contest-reminder)
- 成就解锁 (achievement-unlocked)

**配置要求**:
- SMTP_HOST
- SMTP_PORT
- SMTP_USER
- SMTP_PASSWORD
- SMTP_FROM
- SMTP_FROM_NAME

---

### 4. 搜索优化 [P2 - 次要]

**现状**: 基础 SQL LIKE 搜索

**改进建议**:
- [ ] Elasticsearch/Meilisearch 集成
- [ ] 全文搜索
- [ ] 搜索建议
- [ ] 搜索分析

---

### 5. 成就触发系统 [P2 - ✅ 已完成]

**现状**: 完整的成就触发系统已实现

**实现位置**:
- `backend/src/achievement/achievement.service.ts` - 成就服务
- `backend/src/achievement/achievement-trigger.service.ts` - 触发器服务
- `backend/src/submission/judge.processor.ts` - 提交触发集成

**已实现功能**:
- [x] 成就触发器 (在提交、解题等事件中自动检查)
- [x] 成就通知 (通过WebSocket实时通知)
- [x] 成就进度追踪 (支持多种成就类型)
- [x] 默认成就种子数据 (8种默认成就)

**支持的成就类型**:
| 类型 | 触发条件 | 示例 |
|------|----------|------|
| 解题 | AC数量 | 首次AC、10题、100题 |
| 连续 | 连续天数 | 连续7天、30天打卡 |
| 比赛 | 比赛参与/获胜 | 首次参赛、获得冠军 |
| 社区 | 论坛帖子、题解 | 首次发帖、首次写题解 |

---

### 6. 问题版本控制 [P2 - ✅ 已完成]

**现状**: 完整的版本控制系统已实现

**实现位置**:
- `backend/prisma/schema.prisma` - ProblemVersion 模型
- `backend/src/admin/services/problem-version.service.ts` - 版本服务
- `backend/src/admin/controllers/admin-problem-version.controller.ts` - API控制器
- `management/src/views/problems/components/ProblemVersionHistory.vue` - 版本历史组件
- `management/src/api/admin/problems.ts` - 前端API

**已实现功能**:
- [x] 问题历史版本 (每次更新自动创建版本快照)
- [x] 版本回滚 (支持回滚到任意历史版本)
- [x] 版本差异对比 (比较任意两个版本的差异)
- [x] 变更日志 (记录变更类型、摘要、操作者)

---

## 前端业务缺陷

### 1. OAuth 登录 [P1 - ✅ 已完成]

**现状**: GitHub 和 Google OAuth 都已实现

**实现位置**:
- `backend/src/auth/services/oauth.service.ts` - OAuth 服务 (GitHub + Google)
- `backend/src/auth/auth.controller.ts` - OAuth 端点
- `console/src/views/auth/components/LoginForm.vue` - OAuth 按钮
- `console/src/views/auth/components/RegisterForm.vue` - OAuth 按钮

**已实现功能**:
- [x] GitHub OAuth 登录
- [x] Google OAuth 登录
- [x] OAuth 回调处理
- [x] 自动用户创建和登录

**配置要求**:
- GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET
- GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET

---

### 2. 实时更新 [P1 - ✅ 已完成]

**现状**: WebSocket 基础设施已完成，支持提交结果和比赛更新通知

**实现位置**:
- `backend/src/notification/notification.gateway.ts` - WebSocket 网关
- `backend/src/notification/notification.events.ts` - 事件类型定义
- `console/src/lib/socket.ts` - 前端 Socket 客户端

**已实现功能**:
- [x] 提交状态实时通知 (`submission:result`)
- [x] 比赛排名更新 (`contest:ranking`)
- [x] 比赛开始/结束通知
- [x] 徽章获得通知
- [x] 系统公告
- [x] 订阅/取消订阅比赛房间

**需要实时更新的场景**:
| 场景 | 更新内容 | 实现状态 |
|------|----------|----------|
| 比赛排名 | 排名变化 | ✅ 已实现 |
| 提交状态 | 判题结果 | ✅ 已实现 |
| 通知 | 新消息 | ✅ 已实现 |
| 社区动态 | 新帖子 | ✅ 已实现 |

---

### 3. 用户体验改进 [P2 - ✅ 已完成]

**现状**: 代码编辑器自动保存功能已实现

**实现位置**:
- `console/src/composables/useCodeAutosave.ts` - 自动保存 composable
- `console/src/views/problems/code/CodeView.vue` - 编辑器视图
- `console/src/i18n/locales/en-US/problem.ts` - 英文翻译
- `console/src/i18n/locales/zh-CN/problem.ts` - 中文翻译

**已实现功能**:
- [x] 自动保存到 localStorage (防抖抖 2 秒)
- [x] 页面刷新/关闭时恢复代码
- [x] 自动保存状态指示器
- [x] 恢复对话框
- [x] 清除已保存代码功能
- [x] i18n 支持

**待完成**:
- [x] 离线编辑支持 ✅ (2026-03-01)
- [x] 快捷键提示 - ✅ 已完成
- [x] 提交历史图表 - ✅ 已完成
- [x] 学习进度追踪 - ✅ 已完成

---

### 4. 移动端优化 [P2 - ✅ 已完成]

**现状**: 移动端响应式布局已实现

**实现位置**:
- `console/src/composables/useBreakpoints.ts` - 响应式断点检测
- `console/src/views/problems/components/MobileProblemLayout.vue` - 移动端标签布局
- `console/src/views/problems/ProblemDetailView.vue` - 响应式布局切换

**已实现功能**:
- [x] 响应式断点系统 (xs, sm, md, lg, xl, 2xl)
- [x] 移动端标签导航替代分割面板
- [x] 移动端下拉选择器优化标签切换
- [x] 自动检测移动设备并切换布局

---

### 5. 可访问性 [P3 - ✅ 已完成]

**现状**: 完整的无障碍支持已实现

**实现位置**:
- `console/src/stores/editorSettings.ts` - 无障碍设置存储
- `console/src/components/editor/AccessibilitySettings.vue` - 无障碍设置组件
- `console/src/i18n/locales/*/problem.ts` - i18n翻译
- `console/src/views/problems/ProblemDetailView.vue` - 跳转链接、主内容标识
- `console/src/components/editor/EditorToolbar.vue` - ARIA标签
- `console/src/views/problems/code/CodeView.vue` - 完整ARIA支持
- `console/src/views/problems/headers/LayoutHeaderControls.vue` - 菜单ARIA支持

**已实现功能**:
- [x] 减少动画选项 (reduceMotion)
- [x] 高对比度模式 (highContrast)
- [x] 系统偏好自动检测
- [x] 无障碍设置 UI
- [x] 完整键盘导航支持
- [x] 屏幕阅读器支持 (ARIA标签)
- [x] 跳转到主内容链接

---

## 管理后台缺陷

### 1. 统一审核队列 [P1 - ✅ 已完成]

**现状**: 问题审核队列已实现，支持批量操作

**实现位置**:
- `management/src/views/moderation/ModerationQueueView.vue` - 审核队列视图
- `management/src/api/admin/problems.ts` - 审核API
- `management/src/i18n/locales/` - 国际化文本

**已实现功能**:
- [x] 统一的内容审核队列
- [x] 批量审核操作 (批量解决/批量驳回)
- [x] 审核状态筛选
- [x] 审核备注
- [x] 审核状态历史

**建议界面**:
```
┌─────────────────────────────────────────────────┐
│  审核队列                    [全部] [待处理] [已处理]  │
├─────────────────────────────────────────────────┤
│  类型    │ 内容预览        │ 举报原因  │ 操作    │
│─────────┼────────────────┼──────────┼─────────│
│  问题   │ 两数之和...     │ 题目错误  │ [审核]  │
│  帖子   │ 关于DP的问题... │ 垃圾内容  │ [审核]  │
│  题解   │ 最优解法...     │ 抄袭     │ [审核]  │
└─────────────────────────────────────────────────┘
```

---

### 2. 高级分析报表 [P1 - ✅ 已完成]

**现状**: 完整的分析报表系统已实现

**实现位置**:
- `backend/src/admin/services/admin-analytics.service.ts` - 分析服务
- `backend/src/admin/controllers/admin-analytics.controller.ts` - API控制器
- `backend/src/admin/dto/analytics.dto.ts` - 数据传输对象
- `management/src/views/analytics/AnalyticsView.vue` - 分析视图
- `management/src/api/admin/analytics.ts` - 前端API

**已实现报表**:
- [x] 用户活跃度分析 (日活、周活、峰值时段、留存率)
- [x] 题目完成率分析 (按难度、按标签、热门题目、最难题目)
- [x] 比赛参与分析 (参与趋势、类型分布、虚拟参赛)
- [x] 收入分析 (MRR、ARR、ARPU、流失率、转化率)
- [x] 性能指标报表 (系统运行时间、错误率、吞吐量、资源使用)

---

### 3. 提交管理 [P2 - ✅ 已完成]

**现状**: 完整的提交管理系统已实现

**实现位置**:
- `backend/src/admin/services/admin-submission.service.ts` - 提交管理服务
- `backend/src/admin/controllers/admin-submission.controller.ts` - API控制器
- `backend/src/admin/dto/admin-submission.dto.ts` - 数据传输对象
- `management/src/views/submissions/SubmissionsView.vue` - 提交管理视图
- `management/src/api/admin/submissions.ts` - 前端API

**已实现功能**:
- [x] 提交列表浏览 (支持筛选、搜索、分页)
- [x] 重新判题 (单个提交)
- [x] 批量重判 (多个提交)
- [x] 提交详情查看 (代码、运行结果)
- [x] 提交统计 (总数、通过率、语言分布)

---

### 4. 系统配置扩展 [P2 - ✅ 已完成]

**现状**: 完整的系统配置管理已实现

**实现位置**:
- `backend/src/admin/services/settings.service.ts` - 设置服务
- `backend/src/admin/controllers/admin-settings.controller.ts` - API控制器
- `backend/src/admin/dto/settings.dto.ts` - 数据传输对象
- `management/src/views/settings/SettingsView.vue` - 设置视图
- `management/src/api/admin/settings.ts` - 前端API

**已实现功能**:
- [x] 邮件服务器配置 UI (SMTP主机、端口、用户、密码等)
- [x] 速率限制设置 (API、提交、认证、上传)
- [x] 上传限制设置 (最大文件大小、允许类型、最大数量)
- [x] 功能开关 (比赛、论坛、题解、订阅、成就、通知、收藏、题目列表)

---

## 实施计划

### 阶段 1: 核心功能 (已完成大部分)

**目标**: 使平台达到可用状态

#### Sprint 1: 沙箱系统 ✅ 已完成

**实现位置**:
- `backend/src/submission/sandbox/sandbox.interface.ts`
- `backend/src/submission/sandbox/docker-sandbox.service.ts`
- `backend/src/submission/sandbox/vm-sandbox.service.ts`
- `backend/src/submission/sandbox/sandbox.factory.ts`
- `backend/src/submission/judge.service.ts`
- `backend/judge/Dockerfile.judge`
- `backend/judge/runners/` - JavaScript, Python, Java, C++, Go

#### Sprint 2: 支付集成 ✅ 已完成

**实现位置**:
- `backend/src/subscription/payment/stripe.service.ts`
- `backend/src/subscription/payment/webhook.controller.ts`
- `backend/src/subscription/user-subscription.controller.ts`
- `backend/prisma/schema.prisma` - Stripe字段
- `console/src/api/subscription.ts`
- `console/src/views/personal/SubscriptionView.vue`

#### Sprint 3: 邮件 + OAuth ✅ 已完成

**实现**:
- [x] 邮件服务配置 (SMTP)
- [x] 邮件模板 (7种默认模板)
- [x] OAuth 前端完成 (GitHub + Google)
- [x] OAuth 后端完成 (GitHub + Google 实际API调用)

---

### 阶段 2: 用户体验 (3-4周)

#### Sprint 4: 实时更新 ✅ 已完成

**实现位置**:
- `backend/src/notification/notification.gateway.ts` - WebSocket 网关
- `backend/src/notification/notification.events.ts` - 事件定义
- `console/src/lib/socket.ts` - Socket 客户端

**已实现**:
- [x] WebSocket 连接优化 (JWT认证、房间管理)
- [x] 比赛实时排名 (contest:ranking 事件)
- [x] 提交状态实时更新 (submission:result 事件)
- [x] 通知系统完善 (系统公告、徽章通知)

#### Sprint 5: 前端优化 ✅ 已完成

**已实现**:
- [x] 代码编辑器增强 (自动保存、模板、快捷键)
- [x] 移动端适配 (响应式布局、标签导航)
- [x] 性能优化 (懒加载、缓存)
- [x] 无障碍支持 (ARIA标签、键盘导航、高对比度)

---

### 阶段 3: 运营工具 (2-3周)

#### Sprint 6: 管理后台增强 ✅ 已完成

**实现位置**:
- `management/src/views/moderation/ModerationQueueView.vue` - 审核队列
- `management/src/views/analytics/AnalyticsView.vue` - 分析报表
- `management/src/views/submissions/SubmissionsView.vue` - 提交管理
- `management/src/views/settings/SettingsView.vue` - 系统设置
- `backend/src/admin/services/admin-analytics.service.ts` - 分析服务
- `backend/src/admin/services/admin-submission.service.ts` - 提交管理
- `backend/src/admin/services/settings.service.ts` - 设置服务

**已实现**:
- [x] 统一审核队列 (问题审核、批量操作)
- [x] 高级分析报表 (5种报表类型)
- [x] 提交管理界面 (列表、详情、重判)
- [x] 系统配置扩展 (邮件、速率限制、上传、功能开关)

---

### 阶段 4: 优化完善 (进行中)

**已完成**:
- [x] 成就系统完善 (触发器、通知、进度追踪)
- [x] 问题版本控制 (历史、回滚、差异对比)
- [x] 代码编辑器自动保存 (localStorage 持久化、恢复)

**待完成**:
- [ ] 搜索优化 (Elasticsearch/Meilisearch) - 需要外部服务
- [ ] 性能调优
- [x] 技术债务清理 (错误处理、日志统一)

**本次完成** (2026-03-01 PWA 离线支持):
- [x] Service Worker 离线支持 (vite-plugin-pwa 集成)
- [x] 离线代码编辑 (IndexedDB 队列)
- [x] 更新提示组件 (PWAUpdatePrompt)
- [x] 离线队列指示器 (OfflineQueueIndicator)

**实现位置**:
- `console/vite.config.ts` - PWA 插件配置
- `console/src/pwa-register.ts` - Service Worker 注册
- `console/src/composables/usePWA.ts` - PWA 状态管理
- `console/src/utils/submitQueue.ts` - IndexedDB 提交队列
- `console/src/components/common/PWAUpdatePrompt.vue` - 更新提示
- `console/src/components/common/OfflineQueueIndicator.vue` - 队列状态

**本次完成** (2026-03-01 社区实时更新):
- [x] 社区实时更新 (新帖子通知、评论通知)
- [x] WebSocket 社区订阅/取消订阅
- [x] 前端 Socket 客户端社区事件支持

**实现位置**:
- `backend/src/notification/notification.events.ts` - 新增社区事件类型
- `backend/src/notification/notification.gateway.ts` - 社区广播方法
- `backend/src/forum/forum.module.ts` - 导入 NotificationModule
- `backend/src/forum/forum.service.ts` - 集成社区通知
- `console/src/lib/socket.ts` - 社区事件类型和订阅方法

**本次完成** (2026-03-01 深夜):
- [x] EditorToolbar i18n 完善 (工具栏提示、标签国际化)
- [x] PersonalView i18n 完善 (会员徽章提示国际化)
- [x] GlobalSearch i18n 完善 (搜索框提示、无结果提示国际化)
- [x] 后端测试类型修复 (Stripe Event 类型转换、Prisma Mock 类型)

**实现位置**:
- `console/src/components/editor/EditorToolbar.vue` - 工具栏完整 i18n 支持
- `console/src/components/search/GlobalSearch.vue` - 搜索组件 i18n
- `console/src/views/personal/PersonalView.vue` - 会员徽章 i18n
- `console/src/i18n/locales/*/problem.ts` - 新增 changeTheme、fontSettings、keyboardShortcuts 翻译
- `console/src/i18n/locales/*/common.ts` - 新增 search.placeholder、search.noResults、search.types 翻译
- `backend/src/subscription/payment/stripe.service.spec.ts` - 修复 Event 类型转换
- `backend/src/submission/sandbox/sandbox-monitoring.service.spec.ts` - 修复 Mock 类型
- `backend/src/admin/services/admin-submission.service.spec.ts` - 修复 problemId 类型

**本次完成** (2026-03-01 晚):
- [x] 后端日志统一 (替换 console.* 为 NestJS Logger)
- [x] 前端 Store 错误处理统一 (5个 store 添加 error 状态)
- [x] 前端 i18n 补充 (achievement、dashboard、templates 模块)

**实现位置**:
- `backend/src/common/guards/throttle.guard.ts` - 使用 Logger 替代 console.warn
- `backend/src/notification/adapters/redis-io.adapter.ts` - 使用 Logger 替代 console.error
- `console/src/stores/bookmark.ts` - 添加 error 状态和错误处理
- `console/src/stores/contest.ts` - 添加 error 状态和错误处理
- `console/src/stores/notification.ts` - 添加 error 状态和错误处理
- `console/src/stores/achievement.ts` - 添加 error 状态和错误处理
- `console/src/stores/userStats.ts` - 添加 error 状态和错误处理
- `console/src/i18n/locales/*/achievement.ts` - 成就模块国际化
- `console/src/i18n/locales/*/personal.ts` - 仪表盘模块国际化
- `console/src/i18n/locales/*/problem.ts` - 编辑器模板国际化
- `console/src/views/achievements/AchievementGalleryView.vue` - 使用 i18n
- `console/src/views/dashboard/PersonalDashboardView.vue` - 使用 i18n
- `console/src/components/editor/CodeTemplatesModal.vue` - 使用 i18n

**本次完成** (2026-03-01):
- [x] 屏幕阅读器支持 (ARIA标签、跳转链接)
- [x] API文档 (Swagger/OpenAPI集成)
- [x] 完整键盘导航支持 (aria-pressed状态)
- [x] 后端测试覆盖 (沙箱监控、分析服务测试)
- [x] 成就触发器测试 (21个测试用例)
- [x] Stripe支付服务测试 (27个测试用例)

**实现位置**:
- `backend/src/main.ts` - Swagger配置
- `backend/src/auth/auth.controller.ts` - 认证API文档
- `backend/src/problem/problem.controller.ts` - 题目API文档
- `backend/src/submission/submission.controller.ts` - 提交API文档
- `backend/src/subscription/user-subscription.controller.ts` - 订阅API文档
- `backend/src/admin/controllers/admin-monitoring.controller.ts` - 监控API文档
- `console/src/views/problems/ProblemDetailView.vue` - 跳转链接、主内容标识
- `console/src/components/editor/*.vue` - ARIA标签
- `console/src/views/problems/code/CodeView.vue` - 工具栏ARIA标签
- `console/src/i18n/locales/*/common.ts` - 跳转链接翻译
- `backend/src/submission/sandbox/sandbox-monitoring.service.spec.ts` - 沙箱监控测试
- `backend/src/admin/services/admin-analytics.service.spec.ts` - 分析服务测试
- `backend/src/achievement/achievement-trigger.service.spec.ts` - 成就触发器测试
- `backend/src/subscription/payment/stripe.service.spec.ts` - Stripe支付测试

**测试覆盖统计**:
- 测试套件: 68 个
- 测试用例: 599 个
- 全部通过: ✅

**本次完成** (2026-02-28):
- [x] 沙箱性能监控 (执行日志、指标聚合、管理API)
- [x] 沙箱执行日志 (详细日志记录、错误追踪)
- [x] 发票生成 (Stripe发票API集成、发票历史查看)
- [x] 付款历史记录 (发票列表、PDF下载)
- [x] 键盘导航支持 (快捷键系统、标签切换)

**实现位置**:
- `backend/prisma/schema.prisma` - SandboxExecutionLog, SandboxMetrics 模型
- `backend/src/submission/sandbox/sandbox-monitoring.service.ts` - 监控服务
- `backend/src/submission/sandbox/docker-sandbox.service.ts` - 监控集成
- `backend/src/admin/controllers/admin-monitoring.controller.ts` - 监控API
- `backend/src/subscription/payment/stripe.service.ts` - 发票API
- `backend/src/subscription/user-subscription.controller.ts` - 发票端点
- `console/src/api/subscription.ts` - 前端发票API
- `console/src/views/personal/SubscriptionView.vue` - 发票历史UI
- `console/src/composables/useProblemShortcuts.ts` - 问题快捷键

**本次完成** (2026-02-27):
- [x] Rust 语言沙箱支持
- [x] 移动端响应式布局
- [x] 无障碍设置 (减少动画、高对比度)

---

## 技术债务清单

### 高优先级

| 债务 | 位置 | 影响 | 建议 |
|------|------|------|------|
| ~~沙箱存根~~ | `backend/src/submission/sandbox/` | ~~核心功能不可用~~ | ✅ 已完整实现 |
| ~~支付缺失~~ | `backend/src/subscription/` | ~~无法收费~~ | ✅ 已集成 Stripe |
| ~~测试覆盖不足~~ | 全项目 | ~~质量风险~~ | ✅ 已补充 599 个测试用例 |

### 中优先级

| 债务 | 位置 | 影响 | 建议 |
|------|------|------|------|
| ~~缺少 E2E 测试~~ | `backend/test/` | ~~回归风险~~ | ✅ 已添加 E2E 基础设施 |
| ~~错误处理不一致~~ | 前端各组件 | ~~用户体验~~ | ✅ 已统一 store 错误处理 |
| ~~API 文档缺失~~ | 后端 | ~~开发效率~~ | ✅ 已添加 Swagger |

### 低优先级

| 债务 | 位置 | 影响 | 建议 |
|------|------|------|------|
| ~~部分组件未国际化~~ | 前端 | ~~国际化~~ | ✅ 已补充 achievement、dashboard、editor 模块 i18n |
| 代码注释不足 | 全项目 | 可维护性 | 添加注释 |
| ~~日志不统一~~ | 后端 | ~~调试困难~~ | ✅ 已统一使用 NestJS Logger |

---

## 测试策略

### 单元测试要求

- 后端服务层覆盖率: > 80%
- 前端 composables 覆盖率: > 80%
- 工具函数覆盖率: > 90%

### 集成测试要求

- 所有 API 端点测试
- 数据库操作测试
- 第三方服务 mock 测试

### E2E 测试关键路径

**基础设施已创建** (2026-03-01):
- `backend/test/jest-e2e.json` - Jest E2E 配置
- `backend/test/jest-e2e.setup.ts` - E2E 测试设置
- `backend/test/test-utils.ts` - 测试工具函数
- `backend/test/app.e2e-spec.ts` - 应用启动测试

**注意**: 完整 API E2E 测试需要外部服务 (Redis, Database)，
业务逻辑已通过 599 个单元测试覆盖。

1. 用户注册 → 验证邮箱 → 登录
2. 浏览题目 → 提交代码 → 查看结果
3. 参加比赛 → 提交 → 查看排名
4. 订阅计划 → 支付 → 确认

---

## 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 沙箱安全问题 | 中 | 高 | 容器隔离、资源限制、代码审计 |
| 支付集成复杂 | 中 | 中 | 使用成熟 SDK、充分测试 |
| 性能瓶颈 | 中 | 中 | 负载测试、优化查询 |
| 第三方依赖变更 | 低 | 中 | 版本锁定、监控更新 |

---

## 附录

### A. 文件参考

**后端关键文件**:
```
backend/src/
├── submission/
│   ├── sandbox/           # 沙箱系统 - 需要实现
│   └── judge.service.ts   # 判题服务
├── subscription/
│   └── subscription.controller.ts  # 订阅管理
├── email/
│   └── email.service.ts   # 邮件服务
├── achievement/
│   └── achievement.controller.ts  # 成就系统
└── auth/
    └── auth.controller.ts # 认证
```

**前端关键文件**:
```
console/src/
├── views/
│   ├── auth/              # 认证页面
│   ├── problems/          # 题目相关
│   ├── contest/           # 比赛相关
│   └── personal/          # 个人中心
├── stores/
│   └── auth.ts            # 认证状态
└── lib/
    └── socket.ts          # WebSocket
```

**管理后台关键文件**:
```
management/src/
├── views/
│   ├── users/             # 用户管理
│   ├── problems/          # 题目管理
│   ├── contests/          # 比赛管理
│   └── forum/             # 论坛管理
└── stores/
    └── admin/             # 管理状态
```

### B. 相关资源

- [NestJS 文档](https://docs.nestjs.com/)
- [Vue 3 文档](https://vuejs.org/)
- [Stripe API](https://stripe.com/docs/api)
- [Docker SDK](https://docs.docker.com/engine/api/sdk/)

---

*本文档由 Claude Code 生成，用于指导 UltiCode 平台的业务完善工作。*
