# ADR-0011：拓扑收敛、Contract 所有权与深 Module

- 状态：`Proposed — plan persisted, implementation pending`
- 日期：2026-09-05
- 关联计划：[`ulticode-topology-contract-module-convergence-plan.md`](../plans/ulticode-topology-contract-module-convergence-plan.md)
- 当前默认：`distributed`
- 条件拓扑：`Core`

## Context

当前源码确认了几类相互关联但不应通过继续拆服务解决的问题：

- Core 启用 Owner child context 时，同一 classpath 的重叠 package scan 会造成跨 Owner Bean 泄漏；启用 Owner 的 wiring smoke 在 Bean 装配阶段失败。
- `app-api` 混合 provider-owned remote Contract、consumer-owned outbound port 和 App 内部 Seam；`UserNotificationReadPort` 的实际实现/调用路径属于 Notification→Auth，而不是 App provider。
- `ContestSubmissionPort` 同时承载失效的同步 Submission mutation、Contest fact query 和 event 兼容语义；Submission 的主路径已经是单写 Outbox/Event。
- Admin 已有 `AdminUserDetailQuery` 和 `AdminUserEnricher` 等深 Module 方向，但列表 N+1、顺序扇出、预算和 typed degradation 仍需收敛。
- App 已有 Problem、Contest、Moderation 私有 Maven Module，但当前没有足够证据支持新增物理 deployable；一人开源项目需要保持低本地验证和运维成本。

## Decisions

1. `distributed` 继续作为本地、CI、disposable 和文档的唯一默认拓扑。
2. `Core` 只保留为 `CONDITIONAL` 实验。它必须通过 child load isolation、local/remote Adapter parity 和 disposable journey Gate；最终结果只能是 `PROMOTE_LATER`、`RETAIN_TEMPORARILY_WITH_EXPIRY` 或 `REMOVE_CORE_EXPERIMENT`。Gate 失败默认进入删除分支，不能无限期保留。
3. Contract 所有权按 capability、数据事实、Provider、失败语义和版本责任裁决，不按调用方包名裁决。Consumer-owned outbound port 不自动进入公开 API Module；`app-api` 默认不得成为其他 API 的 Hub。
4. `UserNotificationReadPort` 按计划优先从 `app-api` 内部化到 Notification；Auth 继续拥有真正的 recipient remote Contract。若后续发现其他真实 consumer，必须走 consumer-specific 兼容分支。
5. `ContestSubmissionPort` 先删除失效同步 mutation，再按方法级调用闭包保留或内部化有效 fact query；不把一个混合 Interface 拆成三个新的公开远程 Interface，不恢复 Submission 双写。
6. Admin 优先深化同步编排：批量 Contract、bounded parallel、timeout/cancel、typed degradation、freshness 和 metrics。Admin event read model 只有在量化触发条件满足且不会形成第二数据真相时重新评估。
7. App 优先深化同一进程内的 private Module。Forum、Solution 等候选只有在真实业务/缺陷变更触发并通过 deletion test、Locality、事务、依赖和测试面评分后才 pilot；证据不足即 `NO-GO`。
8. `static` 和 `unit` 继续作为零基础设施贡献者入口；完整环境和 Core journey 按 scope 条件运行，不把 Core 成本转嫁给所有贡献者。
9. 共享 MySQL、Redis、Nacos 和单机参考拓扑是当前接受项，不在本计划中生成数据库集群、Redis 集群、Nacos HA、Kubernetes、Kafka、Service Mesh、Seata 或企业级 HA 整改。

## Consequences

### Positive

- 先解决 Module/Interface/Implementation 的真实边界，再决定是否值得改变进程边界。
- distributed 的默认成本和行为保持稳定，Core 的实验成本、失败语义和退出机制可见。
- Contract 迁移可以逐类型进行，避免大规模包移动和 API binary break。
- Admin 和 App 的复杂度优先被深 Module 隐藏，不把同步编排泄漏给页面调用方。

### Costs and risks

- Core 若要保留，类加载或等价隔离是额外的 Core-only 复杂度；若收益不足，必须删除实验而不是继续堆排除项。
- Contract ownership 迁移会带来一段 deprecated bridge 和编译兼容成本。
- Admin 的 batch/parallel 需要显式 budget 和 Provider 保护，否则会把 N+1 变成并发洪峰。
- 计划需要持续防止 current-status、issue registry、ADR 和计划互相复制事实。

## Non-decisions

本 ADR 不批准：默认拓扑切换、Core 物理合并、任何新业务进程、Admin event projection、生产 HA、生产 mixed-version、远端 Judge、数据库迁移或实现代码变更。具体任务、依赖、验收和验证命令以关联计划为准。

## Evidence anchors

- [`services/core/CoreOwnerContextManager.java`](../../../services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java:217)；[`CoreOwnerBootConfigurations.java`](../../../services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java:21)
- [`services/docs/SERVICES_ISSUES.md`](../../../services/docs/SERVICES_ISSUES.md:30)
- [`services/api/app-api/pom.xml`](../../../services/api/app-api/pom.xml:12)
- [`UserNotificationReadPort.java`](../../../services/api/app-api/src/main/java/com/ulticode/app/api/service/UserNotificationReadPort.java:16)
- [`ContestSubmissionPort.java`](../../../services/api/app-api/src/main/java/com/ulticode/app/api/service/ContestSubmissionPort.java:3)
- [`DefaultAdminUserDetailQuery.java`](../../../services/admin/src/main/java/com/ulticode/modules/admin/query/DefaultAdminUserDetailQuery.java:164)
- [`AppModuleSplitAdmissionGateTest.java`](../../../services/app/app-web/src/test/java/com/ulticode/app/architecture/AppModuleSplitAdmissionGateTest.java:8)
- [`scripts/dev/test.sh`](../../../scripts/dev/test.sh:19)
