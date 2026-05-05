# Moderation 模块代码与业务审计报告

## 审计范围
- **后端**: `backend-spring/src/main/java/com/ulticode/modules/moderation/` (Controller/Service/Entity/DTO/VO/Mapper)
- **前端**: `management/src/views/moderation/` + `management/src/api/admin/moderation.ts`
- **跨模块**: Forum/Solution/Admin 模块与 Moderation 的交互
- **数据库**: `moderation_queue`, `reports`, `moderation_actions`, `appeals`, `user_warnings`, `user_bans`

---

## ❌ Critical Issues（严重问题）

### C1. performAction() 只更新审核队列，不操作内容状态 — 伪审核
- **位置**: `ModerationServiceImpl.java:performAction()` 第166-245行
- **问题**: `DELETED`/`HIDDEN`/`RESTORED`/`DISMISSED` 操作仅更新 `moderation_queue.status` 和创建 `moderation_actions` 记录，完全不更新内容表的 `is_flagged` 字段
- **后果**: 执行删除/隐藏后，`forum_posts`/`forum_comments`/`solutions`/`solution_comments` 仍正常显示给用户；执行恢复后，被 Admin 模块 flag 的内容仍保持 flagged
- **执行**: 在 `performAction()` 的 switch 各 case 中，根据 action 类型调用对应 Mapper 更新内容的 `is_flagged` 字段（DELETED/HIDDEN → 1, RESTORED/DISMISSED → 0）

### C2. Admin flagPost 与 Moderation performAction 是两个独立系统
- **位置**: `AdminForumServiceImpl.java:174-184` (flagPost/unflagPost) vs `ModerationServiceImpl.java:166-245` (performAction)
- **问题**: Admin 模块直接操作 `forum_posts.is_flagged`，Moderation 模块完全不操作 `is_flagged`。两个系统各自维护内容状态，互不感知
- **后果**: Admin flag 的内容在 Moderation 队列中没有任何标记；Moderation 执行操作后内容状态对 Admin  Dashboard 不可见
- **执行**: 删除 `AdminForumController.flagPost/unflagPost` 端点，将其功能合并到 `ModerationController.performAction` 中，统一通过 moderation 系统管理内容状态

### C3. 前端 API 路径缺少 `/moderation` 前缀 — 全量 404
- **位置**: `management/src/api/admin/moderation.ts` 第444-522行
- **问题**: 前端调用 `/reports`、`/appeals`、`/appeals/my`、`/appeals/stats`，但后端 `ModerationController` 有 `@RequestMapping("/moderation")`，实际映射为 `/moderation/reports`、`/moderation/appeals`
- **后果**: 所有 reports/appeals 相关 API 调用返回 404，举报和申诉功能完全不可用
- **执行**: 在前端 `reportsApi` 和 `appealsApi` 的所有路径前添加 `/moderation` 前缀，或移除后端 Controller 的 `@RequestMapping("/moderation")` 改为独立路由

### C4. ReviewAppealDto 字段名不匹配 — 所有申诉审批失败
- **位置**: `management/src/api/admin/moderation.ts:307-310` (前端 `status` 字段) vs `backend-spring/.../dto/ReviewAppealDTO.java:16` (后端 `decision` 字段)
- **问题**: 前端发送 `{status: 'APPROVED', response: '...'}`，后端 DTO 期望 `decision` 字段，`@NotBlank` 校验失败
- **后果**: 管理端点击"通过申诉"或"拒绝申诉"时，后端返回 400 错误，申诉审批功能完全不可用
- **执行**: 统一字段名，将前端 `ReviewAppealDto.status` 重命名为 `decision`，或后端 `ReviewAppealDTO.decision` 重命名为 `status`

### C5. createReport/createAppeal 允许未认证调用，但依赖当前用户 ID
- **位置**: `ModerationController.java:114` (POST /reports) 和 `ModerationController.java:141` (POST /appeals)
- **问题**: 两个端点没有 `@PreAuthorize`，但分别调用 `SecurityUtil.getCurrentUserId()` 获取 reporterId/appellantId。未登录时返回 null，而数据库 `reporter_id`/`appellant_id` 是 `NOT NULL`
- **后果**: 未认证用户调用时报数据库约束 violation 或空指针异常；无法区分是系统错误还是正常业务拒绝
- **执行**: 在两个端点上添加 `@PreAuthorize("isAuthenticated()")`，或在方法内检查 `isAuthenticated()` 并返回明确的 401 错误

### C6. GET /appeals/{id} 对未认证用户开放，返回敏感信息
- **位置**: `ModerationController.java:155-158`
- **问题**: 该端点无任何权限注解，返回 `AppealVO`（包含 appellantId、reason、evidence、moderatorId 等敏感信息）
- **后果**: 任何人可通过遍历 ID 收集用户申诉历史、审核员信息
- **执行**: 添加 `@PreAuthorize("isAuthenticated()")`，并额外检查调用者是否为申诉人本人或具有 MODERATOR/ADMIN 角色

### C7. 用户封禁不生效 — 被 ban 用户仍可正常发帖
- **位置**: `ModerationServiceImpl.java:createUserBan()` 设置 `user.isBanned = true`，但 `ForumPostServiceImpl.java:createPost()`、`ForumCommentServiceImpl.java`、`SolutionServiceImpl.java` 等完全不检查 `isBanned`
- **问题**: 封禁用户后，创建内容的服务方法没有前置检查
- **后果**: 被封禁用户可继续发帖、评论、提交题解，封禁形同虚设
- **执行**: 在 `ForumPostServiceImpl.createPost()`、`ForumCommentServiceImpl.createComment()`、`SolutionServiceImpl.createSolution()` 方法开头添加 `if (currentUser.isBanned()) throw new BusinessException(...)`；或创建 AOP 切面统一拦截

### C8. console 前端举报功能完全未实现
- **位置**: `console/src/i18n/locales/zh-CN/forum.ts:36,146,256` 和 `en-US/forum.ts:36,150,261`
- **问题**: 有 `report: "举报"` 和 `reportSubmitted: "举报已提交"` 的翻译文本，但 console 的 Vue 组件中没有任何举报按钮或 API 调用
- **后果**: 普通用户无法举报内容，举报流程的入口不存在
- **执行**: 在 forum post/comment/solution 的详情页或操作菜单中添加举报按钮，调用 `POST /moderation/reports` API，传递 entityType、entityId、reason、category

### C9. 前端 ModeratableEntityType 包含 'problem'，但后端 resolveAuthorId 不支持
- **位置**: `management/src/api/admin/moderation.ts:8` (前端包含 'problem') vs `ModerationServiceImpl.java:470-482` (后端 switch 无 'problem' case)
- **问题**: 前端定义 `type ModeratableEntityType = 'forum_post' | 'forum_comment' | 'solution' | 'solution_comment' | 'problem'`，但后端 `resolveAuthorId()` 对 'problem' 返回 null
- **后果**: 如果前端发送 `entityType: 'problem'`，`moderation_queue.author_id` 被设为 null，数据库 `NOT NULL` 约束导致插入失败
- **执行**: 在后端 `resolveAuthorId()` 的 switch 中添加 `'problem'` case，通过 `problemMapper.selectById(entityId)` 获取 `authorId`；或从前端移除 'problem' 类型

---

## ⚠️ Risks（潜在风险）

### R1. batchAction 异常信息泄露内部细节
- **位置**: `ModerationServiceImpl.java:253` — `catch (Exception e) { errors.add(new BatchError(queueId, e.getMessage())); }`
- **问题**: 任何异常（包括 SQL 语法错误、空指针、数据库连接失败）的 `e.getMessage()` 直接返回给客户端
- **执行**: 捕获异常后只返回固定错误文案（如 "处理失败"），将原始异常记录到日志

### R2. Rate limit 按 IP 而非用户 ID，可被代理绕过
- **位置**: `ModerationController.java:113` — `@RateLimit(key = "moderation:create-report", limit = 20, period = 60)`
- **问题**: `RateLimitAspect` 使用 `ip` 生成 Redis key，同一 IP 下的所有用户共享额度；用户可通过代理切换 IP
- **执行**: 将 rate limit key 改为基于 `userId`（登录用户）或 `userId + ip`（未登录用户），移除纯 IP 限流

### R3. claimItem 存在竞态条件
- **位置**: `ModerationServiceImpl.java:341-355`
- **问题**: 先 `selectById` 检查 `assignedToId == null`，再 `assignToModerator`。两个并发请求可能都通过检查
- **执行**: 使用数据库乐观锁（version 字段）或在 update SQL 中添加 `WHERE assigned_to_id IS NULL` 条件

### R4. createReport 重复检查存在竞态条件
- **位置**: `ModerationServiceImpl.java:267-270`
- **问题**: 先 `count` 检查是否已举报，再 `insert`。并发请求可能产生重复报告
- **执行**: 在 `reports` 表添加唯一索引 `(reporter_id, entity_type, entity_id)`，让数据库保证唯一性

### R5. batchAction 返回类型前后端不匹配
- **位置**: `management/src/api/admin/moderation.ts:423-428` (期望 `results: Array<{id, success, error}>`) vs `ModerationServiceImpl.java:257-261` (返回 `successCount/errorCount/errors: List<BatchError>`)
- **问题**: 前端期望逐条结果数组，后端返回聚合统计。前端无法正确显示每条记录的失败原因
- **执行**: 统一返回格式。推荐前端适配后端：将 `BatchActionResult` 改为 `{successCount: number, errorCount: number, errors: Array<{queueId: string, error: string}>}`

### R6. 权限模型前后端不一致
- **位置**: `management/src/router/index.ts` (前端 `PERM.MODERATE_PROBLEM` — permission-based) vs `ModerationController.java` (后端 `@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")` — role-based)
- **问题**: 前端基于权限（action + resource），后端基于角色。一个用户可能有权限但无角色，或有角色但前端不放行
- **执行**: 统一为 permission-based。后端添加 `@PreAuthorize("hasPermission('PROBLEM', 'MODERATE')")` 替换 role-based，或前端改为检查角色 `ROLE_MODERATOR`

### R7. durationDays 无范围校验
- **位置**: `PerformModerationActionDTO.java` / `CreateUserBanDTO.java`
- **问题**: `durationDays` 可传入负数、0 或极大值（如 999999）
- **执行**: 添加 `@Min(1) @Max(3650)` 校验

---

## 🧹 Redundancy（冗余逻辑）

### RD1. 前后端枚举重复定义
- **位置**: 后端 `ModerationActionType.java` (DELETED, HIDDEN, RESTORED, DISMISSED, RESOLVED, WARNED, BANNED) vs 前端 `management/src/api/admin/moderation.ts:15-25`
- **问题**: 两端各自维护相同枚举值，修改时需同步两处
- **执行**: 后端提供 `/moderation/enums` 接口返回枚举定义，前端动态获取；或生成共享 types 包

### RD2. VO 构建中重复查询 User
- **位置**: `ModerationServiceImpl.java:toQueueVO()`、`toReportVO()`、`toAppealVO()` 各自调用 `userService.getUserById()`
- **问题**: 批量查询时，每条记录都触发一次用户查询，N+1 问题
- **执行**: 使用 `userService.listByIds(collectedUserIds)` 批量查询，或 MyBatis-Plus 的 `selectBatchIds`

### RD3. 前端 moderation store 过度拆分
- **位置**: `management/src/stores/admin/moderation/` 下 queue.ts / reports.ts / appeals.ts / actions.ts
- **问题**: 4 个文件每个都不到 100 行，各自维护 loading/error/state，代码重复
- **执行**: 合并为单个 `moderationStore.ts`，按模块组织 state 而非文件拆分

### RD4. PaginatedResponse 接口同时支持两种格式
- **位置**: `management/src/api/admin/moderation.ts:270-285`
- **问题**: 接口同时定义 `total/page/limit/totalPages` (flat) 和 `meta: {...}` (nested)，代码需同时处理两种结构
- **执行**: 统一后端返回格式，只保留 flat 结构，删除 meta 嵌套

### RD5. performAction switch 的 default 分支为死代码
- **位置**: `ModerationServiceImpl.java:224-227`
- **问题**: 前面第166-168行已检查 `validActions.contains(action)`，不在集合中的 action 会提前抛异常，default 永远不会执行
- **执行**: 删除 default 分支，或将前面的 `validActions.contains` 检查移除，依赖 switch default 处理非法值

### RD6. updateReportsStatus 使用循环 update 而非批量更新
- **位置**: `ModerationServiceImpl.java:523-530`
- **问题**: 先 `selectList` 查出所有 reports，再 `forEach` 逐个 `updateById`
- **执行**: 改为 `reportMapper.update(null, new UpdateWrapper<Report>().eq("queue_id", queueId).set("status", status))`

### RD7. DTO 重复 import
- **位置**: `ModerationServiceImpl.java` 第8-9行与第12-13行重复 import `dto.*` 和 `entity.*`
- **执行**: 删除重复 import 行

---

## 🧠 Design Improvements（架构优化建议）

### D1. 在 Moderation 模块中统一内容状态管理
- **执行**: 删除 `AdminForumController` 的 `flagPost/unflagPost` 端点，将内容状态变更统一收敛到 `ModerationController.performAction()`。performAction 根据 action 类型操作对应内容表的 `is_flagged` 字段，并记录 `flagged_reason` 和 `flagged_at`

### D2. 创建内容创建前置拦截器统一检查封禁状态
- **执行**: 创建 `BanCheckAspect` 切面，注解 `@CheckBan` 标注在 `ForumPostServiceImpl.createPost()`、`ForumCommentServiceImpl.createComment()`、`SolutionServiceImpl.createSolution()` 上，切面内检查 `currentUser.isBanned()`，避免在每个服务中重复写检查逻辑

### D3. 实现 console 前端举报功能闭环
- **执行**: 
  1. 在 console 的 forum/solution 详情页添加举报按钮（调用 `POST /moderation/reports`）
  2. 后端 `createReport` 在插入 report 后，自动将对应内容加入 `moderation_queue`（如果不在队列中）
  3. 管理端审核后，`performAction` 同步更新内容 `is_flagged` 状态
  4. console 前端根据 `is_flagged` 隐藏或标记被处理的内容

### D4. 为 moderation_queue 添加数据库级状态约束
- **执行**: 添加 CHECK 约束确保 `status` 只能是 `PENDING`, `UNDER_REVIEW`, `RESOLVED`, `DISMISSED`，避免脏数据

### D5. 统一 API 返回结构为扁平分页
- **执行**: 后端所有分页接口返回 `{data: [], total: N, page: N, limit: N, totalPages: N}`，删除嵌套的 `meta` 对象，前端删除双重解析逻辑

### D6. 为 reports 表添加唯一索引防止重复举报
- **执行**: `CREATE UNIQUE INDEX uk_reports_reporter_entity ON reports (reporter_id, entity_type, entity_id);`，删除 Java 代码中的先 count 后 insert 逻辑

### D7. 使用事务包裹 batchAction 的批量操作
- **执行**: 在 `ModerationServiceImpl.batchAction()` 上添加 `@Transactional`，确保批量处理中部分失败时整体回滚，或改为逐条处理但每条独立事务并收集错误

### D8. 统一前后端权限校验为 permission-based
- **执行**: 
  1. 后端：所有 moderation 端点改用 `@PreAuthorize("hasPermission('MODERATION', 'READ')")` 等
  2. 前端：保留 `PERM.MODERATE_PROBLEM` 等 permission 检查
  3. 用户角色到权限的映射在数据库或配置中心维护

### D9. 为 claimItem 添加数据库级防并发
- **执行**: 在 `moderation_queue` 表添加 `version` 字段（乐观锁），`assignToModerator` 时 `UPDATE moderation_queue SET assigned_to_id = ?, version = version + 1 WHERE id = ? AND version = ? AND assigned_to_id IS NULL`

### D10. 将 RateLimit key 改为用户维度
- **执行**: 修改 `RateLimitAspect.generateKey()`，优先使用 `SecurityUtil.getCurrentUserId()` 生成 key，未登录时再 fallback 到 IP

---

## 审计总结

| 类别 | 数量 | 优先级 |
|------|------|--------|
| ❌ Critical Issues | 9 | 立即修复 |
| ⚠️ Risks | 7 | 高优修复 |
| 🧹 Redundancy | 7 | 技术债清理 |
| 🧠 Design Improvements | 10 | 架构优化 |

**最高优先级修复顺序**:
1. C3 (API 路径 404) — 所有 reports/appeals 功能不可用
2. C4 (ReviewAppealDto 字段不匹配) — 申诉审批不可用
3. C1 (performAction 不操作内容状态) — 审核形同虚设
4. C7 (封禁不生效) — 安全机制失效
5. C8 (console 举报未实现) — 业务闭环断裂
6. C5/C6 (未认证端点) — 安全漏洞
7. C9 (problem 类型不支持) — 数据库约束错误
8. C2 (两套 flagging 系统) — 数据不一致
