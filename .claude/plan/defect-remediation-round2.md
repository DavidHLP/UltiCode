# UltiCode 项目缺陷分析报告 — 第二轮

> 基于第一轮修复（commit 21cbeb6a9, 26 files）后的深度扫描结果。
> 4 个并行 agent 分别扫描 backend-spring / console / management / recommendation。
> 所有 CRITICAL/HIGH 发现已通过源码验证，排除误报。

---

## 缺陷总览

| 严重度 | 数量 | 说明 |
|--------|------|------|
| CRITICAL | 1 类 / 5 处 | Markdown 渲染 XSS |
| HIGH | 8 项 | 内存泄漏、竞态条件、信息泄露、代码质量 |
| MEDIUM | 14 项 | 缺失校验、配置、性能、错误处理 |
| LOW | 6 项 | 组件体积、无障碍、依赖版本 |
| **排除（误报/已修复）** | 5 项 | ContestTimer、TagMergeDialog、AnalyticsView、BackupServiceImpl、OAuth |

---

## Phase 1 — CRITICAL: Markdown XSS 注入（5 处）

**问题**: `renderMarkdown()` 返回的 HTML 直接绑定到 `v-html`，未经 `sanitizeHtml()` 消毒。攻击者可在问题描述、论坛帖子等 Markdown 内容中注入恶意脚本。

| # | 文件 | 行号 | 代码片段 |
|---|------|------|----------|
| 1 | `console/src/views/problems/description/DescriptionMarkdown.vue` | 97,102 | `v-html="htmlContent"` ← `renderMarkdown()` 无 sanitize |
| 2 | `console/src/views/forum/components/ThreadContent.vue` | 274 | `v-html="renderMarkdown(thread.excerpt)"` |
| 3 | `console/src/views/forum/components/ThreadContent.vue` | 335 | `v-html="renderMarkdown(media.markdown \|\| media.body)"` |
| 4 | `console/src/views/forum/components/ForumPostCard.vue` | 288 | `v-html="renderMarkdown(post.excerpt)"` |
| 5 | `console/src/views/forum/components/ForumPostCard.vue` | 352 | `v-html="renderMarkdown(media.markdown \|\| media.body)"` |
| 6 | `console/src/components/markdown/MarkdownView.vue` | 64 | `v-html="renderMarkdown(props.content)"` |

**修复方案**: 在所有 `renderMarkdown()` 调用外包裹 `sanitizeHtml()`：
```typescript
import { sanitizeHtml } from "@/utils/sanitize";
// Before: renderMarkdown(content)
// After:  sanitizeHtml(renderMarkdown(content))
```

---

## Phase 2 — HIGH: 安全与稳定性

### H-1: App.vue 全局事件监听器内存泄漏
- **文件**: `console/src/App.vue:14-19`
- **问题**: `window.addEventListener("error")` 和 `unhandledrejection` 添加后未在 `onUnmounted` 清理
- **修复**: 保存 handler 引用，在 `onUnmounted` 中 `removeEventListener`

### H-2: Recommendation — 硬编码数据库凭据
- **文件**: `recommendation/recommend-core/.../OfflineEvaluationRunner.java:50-51`
- **问题**: JavaDoc 示例中包含 `root` / `ulticode2024` 硬编码凭据
- **修复**: 移除硬编码凭据，改用 `your-username` / `your-password` 占位符

### H-3: Recommendation — Redis 无界数据加载
- **文件**: `recommendation/recommend-provider/.../RedisRecommendationStore.java:48-58`
- **问题**: `loadAvailableProblems()` 和 `loadUserProblemMatrix()` 加载全量数据到内存
- **修复**: 实现分页加载或流式读取，避免大数据集时 OOM

### H-4: Recommendation — 缓存操作竞态条件
- **文件**: `recommendation/recommend-provider/.../RecommendServiceImpl.java:59-103`
- **问题**: 缓存读写无同步机制，并发请求可能导致缓存状态不一致
- **修复**: 使用 Redis 原子操作（SETNX）或分布式锁

### H-5: Recommendation — 错误信息泄露
- **文件**: `recommendation/recommend-provider/.../RecommendServiceImpl.java:79`
- **问题**: 将完整异常 message 返回客户端，可能泄露系统内部信息
- **修复**: 返回通用错误信息，详情仅记录到服务端日志

### H-6: Recommendation — 通用异常捕获
- **文件**: `recommendation/recommend-provider/.../RecommendServiceImpl.java:77,86-88,95-97`
- **问题**: 多处 `catch(Exception e)` 吞掉具体异常类型
- **修复**: 捕获具体异常类型，分别处理

### H-7: Backend — Forum 查询缺失分页
- **文件**: `backend-spring/.../forum/service/impl/ForumServiceImpl.java` (652 lines)
- **问题**: 帖子/评论列表查询缺少分页参数，可能返回过量数据
- **修复**: 为所有列表查询添加 `Pageable` 参数

### H-8: Management — Deep watcher 性能问题
- **文件**: `management/src/views/moderation/AppealsView.vue:109`, `ReportsView.vue:128`
- **问题**: 对大型分页对象使用 `deep: true` watcher，触发不必要的重渲染
- **修复**: 改为浅层 watcher 或仅 watch 特定属性

---

## Phase 3 — MEDIUM: 校验、配置与性能

### M-1: Recommendation — RecommendRequest.size 缺少上限
- **文件**: `recommendation/recommend-api/.../RecommendRequest.java`
- **修复**: 添加 `@Max(100)` 注解，防止 DoS

### M-2: Recommendation — Redis 无连接池配置
- **文件**: `recommendation/recommend-provider/src/main/resources/application.yml`
- **修复**: 添加 lettuce/jedis 连接池参数

### M-3: Recommendation — Redis 操作静默失败
- **文件**: `recommendation/recommend-provider/.../RedisRecommendationStore.java:106-138`
- **修复**: 实现熔断器或降级策略，失败时记录警告而非静默返回空集合

### M-4: Recommendation — 缺少 TLS 配置
- **问题**: Redis / Nacos / Dubbo 均无 TLS，生产环境数据明文传输
- **修复**: 在生产 profile 中添加 TLS 配置

### M-5: Backend — Admin/Moderation 端点缺少速率限制
- **修复**: 为 admin 和 moderation 相关端点添加 `@RateLimit` 注解

### M-6: Backend — 缺少乐观锁
- **问题**: 多个实体的并发更新无版本控制
- **修复**: 在关键实体添加 `@Version` 字段

### M-7: Backend — God Class 重构
- **文件**: ForumServiceImpl(652行), ContestServiceImpl(630行), ModerationServiceImpl(577行)
- **修复**: 拆分为更小的专职 Service

### M-8: Console — main.ts 类型断言
- **文件**: `console/src/main.ts:73`
- **修复**: 添加类型守卫替代 `as unknown as` 断言

### M-9: Console — Notification Store 竞态
- **文件**: `console/src/stores/notification.ts:196-219`
- **修复**: 使用取消引用模式防止 unsubscribe 后仍更新状态

### M-10: Management — ProblemsListView 1224 行
- **修复**: 拆分为子组件（过滤器、表格、操作栏）

### M-11: Management — 路由缺少懒加载
- **文件**: `management/src/router/index.ts`
- **修复**: 使用 `defineAsyncComponent` 或动态 import

### M-12: Management — 47+ 处通用 catch 块
- **修复**: 对 API 调用实现统一的错误类型处理

### M-13: Management — API 失败无用户反馈
- **文件**: `management/src/stores/admin/users.ts` 等
- **修复**: 添加错误 toast 通知

### M-14: Management — 无全局 Error Boundary
- **修复**: 实现 Vue error boundary 组件包裹关键区域

---

## Phase 4 — LOW: 代码质量与维护性

| # | 模块 | 文件 | 问题 |
|---|------|------|------|
| L-1 | Console | ProblemListsView.vue (1356行) | 组件过大 |
| L-2 | Console | ContestDetailView.vue (1039行) | 组件过大 |
| L-3 | Console | 多个动态内容组件 | 缺少 ARIA 标签 |
| L-4 | Recommendation | pom.xml | Spring Boot 3.2.5 / Dubbo 3.2.14 版本偏旧 |
| L-5 | Backend | WebSocket 服务 | 缺少全面的错误响应机制 |
| L-6 | Backend | 多个 Controller | 分页参数命名不一致 |

---

## 执行优先级建议

```
┌─────────────────────────────────────────────────────┐
│  P0  Phase 1 (CRITICAL)   — XSS 注入 6 处          │  ← 立即修复
│  P1  Phase 2 (HIGH)       — 内存泄漏 + 安全        │  ← 本轮修复
│  P2  Phase 3 (MEDIUM)     — 校验 + 配置 + 性能     │  ← 按需修复
│  P3  Phase 4 (LOW)        — 代码质量               │  ← 技术债务
└─────────────────────────────────────────────────────┘
```

## 复杂度评估

| Phase | 影响范围 | 复杂度 | 预估改动 |
|-------|---------|--------|---------|
| Phase 1 | Console 4 文件 | LOW | ~6 行 import + 6 行 wrap |
| Phase 2 | 4 模块 8 文件 | MEDIUM | ~50-80 行 |
| Phase 3 | 4 模块 14+ 文件 | HIGH | ~200+ 行 |
| Phase 4 | 4 模块多文件 | HIGH | 重构级别 |
