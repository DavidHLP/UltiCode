# Requirements: UltiCode v1.3 Core Features

**Milestone:** v1.3 Core Features
**Status:** Active
**Created:** 2026-04-18

## Active Requirements

### 判题系统 (JUDGE)

- [ ] **JUDGE-01**: 实现 Judge Worker — 后台定时轮询 Redis `judge_queue`，取出 JudgeJob，调用 CodeExecutionService 执行全部测试用例，写入判题结果到 Submission 实体，更新状态为 Accepted/Wrong Answer/TLE/MLE/RE 等。当前提交后永远停留在 Pending，这是平台的核心阻塞问题。
- [ ] **JUDGE-02**: 修复语言支持不一致 — SubmissionServiceImpl 接受 13 种语言但 CodeExecutionService 只支持 5 种（JS, Python, Java, C, C++）。将提交接口限制为 5 种受支持语言，或从下拉列表中移除不受支持的语言选项。
- [ ] **JUDGE-03**: Docker 沙箱添加内存使用测量 — 当前 execute() 方法始终返回 "0KB" 内存。通过 cgroup 统计或 /usr/bin/time 获取实际内存消耗。
- [ ] **JUDGE-04**: 提交状态变更 WebSocket 推送 — 当前前端通过轮询 GET /submissions/{id} 检查判题结果。利用已有 WebSocket 基础设施，在 Judge Worker 完成判题后主动推送状态变更。

### 竞赛系统 (CONTEST)

- [ ] **CONTEST-01**: 添加 ContestProblem 实体/Mapper — 数据库 `contest_problems` 表已存在但无 Java 实体。创建 ContestProblem entity + mapper，实现题目关联到竞赛的服务层逻辑（创建竞赛时批量插入、查询竞赛题目列表）。
- [ ] **CONTEST-02**: 添加 ContestSubmission 实体/Mapper — 数据库 `contest_submissions` 表已存在但无 Java 实体。创建 entity + mapper，在竞赛期间提交代码时同步记录到 contest_submissions。
- [ ] **CONTEST-03**: 实现竞赛状态自动调度器 — 使用 @Scheduled 定时任务，检查 contests 表中 start_time/end_time 已过但状态未更新的竞赛，自动转换 UPCOMING→RUNNING→FINISHED，并触发 RealtimeService.emitContestStatus()。
- [ ] **CONTEST-04**: 实现 Rating 计算引擎 — 数据库 global_rankings 表有 rating_before/rating_after 字段和 10 级称号体系，但无计算逻辑。实现 Codeforces 风格的 Elo 变体 rating 计算，竞赛结束后批量更新参赛者 rating。
- [ ] **CONTEST-05**: 补全 Admin 竞赛 API — Management 前端调用 POST /admin/contests/{id}/start 和 /end 但后端只有 2 个只读端点。添加 start、end、update、delete 端点到 AdminContestController。
- [ ] **CONTEST-06**: 将 WebSocket 接入 ContestDetailView — useContestSocket composable 已构建完善（STOMP + SockJS + 自动重连），但 ContestDetailView 仅使用 30 秒轮询。替换为 WebSocket 实时排名推送。
- [ ] **CONTEST-07**: 添加竞赛公告 CRUD API — contest_announcements 表和 WebSocket emitAnnouncement() 已存在，但无 REST 端点。添加公告的创建、更新、删除、列表端点。

### 题目浏览 (PROB)

- [ ] **PROB-01**: 添加随机题目端点 — Console 前端 fetchRandomProblem() 调用 GET /problems/random 但后端无此端点。实现随机返回一道已发布题目。
- [ ] **PROB-02**: 计算并返回题目通过率 — problems 表有 acceptance_rate 列但无计算逻辑。基于 submissions 表的 accepted/total 比例计算并更新。
- [ ] **PROB-03**: Admin 批量操作 API — Management 前端定义了 bulkAction() 和 bulkEdit() 但后端无对应端点。实现批量发布/取消发布/删除/编辑（难度、isPremium）。
- [ ] **PROB-04**: 扩展 CreateProblemDTO — 当前 DTO 只接受 slug/title/difficulty/isPremium/isPublished，但 Admin 创建页需要同时设置 summary、content、examples、constraints、hints、languages、tags。扩展 DTO 和服务层。

### 用户中心 (USER)

- [ ] **USER-01**: 添加全局排名 — UserStatsPanel 显示 Global Rank 但 UserStatsDTO 无此字段。基于 global_rankings 表的 rating 排名计算用户排名。
- [ ] **USER-02**: 添加通过率 — 前端显示 Acceptance Rate 但后端不返回。基于 submissions 表计算 accepted_count/total_count。
- [ ] **USER-03**: 添加公开用户主页 — 后端 GET /{id} 已存在但前端仅有 /personal（当前用户）。添加 /users/:id 路由和公开资料页面。
- [ ] **USER-04**: 修复成就 API 路径不匹配 — 前端调用 `/achievements/my` 和 `/achievements/points`，后端服务 `/achievements/user/me` 和 `/achievements/user/me/points`。统一路径。
- [ ] **USER-05**: 添加提交计数到用户统计 — UserProfile 接口定义了 submission_count 但后端不返回。添加总提交数到 UserStatsDTO。

## Future Requirements

*Deferred from this milestone:*

- Version history system for problems (6 endpoints)
- Problem import/export
- Flag/moderation system for problems
- Contest check-in feature
- Contest freeze time logic
- Social profile sharing / meta tags
- Following/followers social graph
- User comparison feature

## Out of Scope

- **Recommendation service (Dubbo/Spark):** Optional microservice, not required for core functionality
- **Forum module enhancements:** Working as-is, no critical gaps
- **Solution module enhancements:** Working as-is
- **Notification/Subscription module:** Already functional
- **Email delivery improvements:** Working as-is
- **Monitoring dashboards:** Deferred to v2 (MON-01, MON-02)
- **Advanced CI features:** Deferred to v2 (ADVCI-01 through ADVCI-03)
- **Additional sandbox languages (Go, Rust, C#, PHP, Ruby, Swift, Kotlin, TypeScript):** Require new Docker images and wrapper scripts — significant effort, defer to v2

## Traceability

| REQ-ID | Phase | Status |
|--------|-------|--------|
| JUDGE-01 | Phase 12 | Pending |
| JUDGE-02 | Phase 12 | Pending |
| JUDGE-03 | Phase 12 | Pending |
| JUDGE-04 | Phase 14 | Pending |
| CONTEST-01 | Phase 13 | Pending |
| CONTEST-02 | Phase 13 | Pending |
| CONTEST-03 | Phase 14 | Pending |
| CONTEST-04 | Phase 14 | Pending |
| CONTEST-05 | Phase 13 | Pending |
| CONTEST-06 | Phase 14 | Pending |
| CONTEST-07 | Phase 13 | Pending |
| PROB-01 | Phase 15 | Pending |
| PROB-02 | Phase 15 | Pending |
| PROB-03 | Phase 15 | Pending |
| PROB-04 | Phase 15 | Pending |
| USER-01 | Phase 15 | Pending |
| USER-02 | Phase 15 | Pending |
| USER-03 | Phase 15 | Pending |
| USER-04 | Phase 15 | Pending |
| USER-05 | Phase 15 | Pending |
