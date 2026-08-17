# Resume

- Active task: SPLIT-005 final Phase Gate（Contest association 已迁为显式 submitContest + SubmissionCreated durable inbox）；SEARCH-003、SPLIT-003、SPLIT-004 已完成，最终验证证据已收集但兼容退役 gate 仍未闭合
- Branch: main @ 3b9020c01（工作区保留两处验证用测试修复；不 commit）
- Objective: 将 submission 判题生命周期与 search 索引 worker 拆分为独立的微服务。
- Dependencies: SPLIT-001 → SPLIT-002(done) → SPLIT-003(done) → SPLIT-004(done) → SPLIT-005; SPLIT-001 → SEARCH-001(done) → SEARCH-002(done) → SEARCH-003(done) → SPLIT-005
- Completed: SPLIT-001/002/003/004；SPLIT-003-slice-7 explicit contest command + durable association handoff；SEARCH-001/002/003（21ad12a4d/c81bfab1c/16cecd994/d09411d19）
- In progress: SPLIT-005 final Phase Gate (services-wide verification, compatibility retirement audit, security/concurrency/architecture review)
- Next ready: none；SPLIT-005-env-quick 已完成；SPLIT-005-env-sandbox blocked on Testcontainers/Docker runtime stability；SPLIT-005-retirement-authority blocked pending explicit release/cutover authority
- Environment: Docker 可用但无外网；本轮最终门禁使用的 disposable MySQL/Redis 环境已清理，既有 `ulticode-mysql` 持久卷未触碰；redis:7.2-alpine 本地 tag；`.env` 已含 MEILI_MASTER_KEY；Compose 含 meilisearch + backend-search 服务
- Protected boundaries: 不编辑已应用 migration；`.auto-flow/` 不 commit；auth 叶子 Provider 禁 app-api；search.worker.enabled 默认关（SEARCH-003 后启用）；不删除既有 mysql_data 卷
- Evidence: HANDOFF.yaml + WORKLOG.md（SPLIT slice-4..9、SPLIT-003-slice-7、SEARCH-001/002/003、2026-08-17 isolated services test/verify/frontend checks）；SPLIT-005-env-quick 已以 disposable MySQL 修复官方 quick；sandbox focused IT 6/6 skipped（无镜像）且全量 IT 卡在 Testcontainers MySQL 启动；兼容退役 gate 已记录
- Rollback: worker 停用即回退（事件留在 stream PEL/outbox 可重放）；模块随 commit 回滚
