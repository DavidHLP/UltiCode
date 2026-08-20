# Decisions

## Architecture review execution direction (2026-08-20)

### Context
评审报告确认 UltiCode services/ 已处于 Owner/Worker 与 Contract seam 成形的 Strangler migration 收敛阶段。当前目标只有 development/TEST-TARGET；无需新增物理服务或基础设施。四项候选分别是 Search DLQ 原子性、Submission 读侧 locality、Admin dashboard 查询经济性和 canonical development 配置。

### Decision
按以下唯一顺序实施：
1. Search worker 将 exhausted retry 的 `XADD DLQ` 与 source `XACK` 收敛到幂等 Redis 原子状态转移，并用 disposable Redis crash-window regression 证明 PEL/DLQ/ACK 状态。
2. Submission Projection 先实现页面级 facts/users 批量读取，本地完成 VO shaping；不引入事件化 read model。
3. 账号统计事实归 Auth，提供 bounded summary seam；Admin 只负责 Dashboard 输出 Projection。
4. runtime 默认只接受显式 Owner 配置，generic `DB_*` 仅保留 migration bootstrap；dev-full 为显式 opt-in，rollback source seam 不删除。
5. 完成 focused/module/integration/security/formal review 与控制面审计后，闭合 ARCH-REVIEW-005。

### Alternatives
- 不拆更多 Contest/Moderation/Notification services：缺乏独立 writer/seam 证据，增加 process/contract/startup 成本。
- 不引入 RocketMQ/Seata/Kubernetes/Service Mesh：已有 Outbox/Inbox/Redis Streams 覆盖当前开发可靠性需求。
- 不把 development evidence 表述为 production acceptance：当前无 production authority，外部部署/观察/回滚责任不在本任务授权内。

### Consequences
- 变更集中在现有 deep modules、contracts、adapters、tests 和 startup/config seams；不改变 writer、schema、migration 或公共 HTTP envelope。
- 失败转移、跨 Owner 读取和 runtime 配置的边界更可测量；Admin/Submission 不再隐藏无界远程 fan-out。
- 开发配置迁移期兼容性会从默认 runtime interface 中收窄，但 migration bootstrap 与 source-level rollback 保留。

### Affected tasks
ARCH-REVIEW-001, ARCH-REVIEW-002, ARCH-REVIEW-003, ARCH-REVIEW-004, ARCH-REVIEW-005.
