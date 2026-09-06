# ADR-0011：拓扑收敛、Contract 所有权与深 Module

- 状态：`Implemented — durable rules retained; Core outcome recorded in ADR-0012`
- 日期：2026-09-05
- 关联决策：[`ADR-0012`](0012-core-topology-three-way-decision.md)
- 当前状态与问题：[`current-status.md`](../../project/current-status.md)、[`SERVICES_ISSUES.md`](../../../services/docs/SERVICES_ISSUES.md)
- 当前默认：`distributed`
- 条件拓扑：`Core`

## Context

当前源码确认了几类相互关联但不应通过继续拆服务解决的问题：

- Core 启用 Owner child context 时，同一 classpath 的重叠 package scan 会造成跨 Owner Bean 泄漏；启用 Owner 的 wiring smoke 在 Bean 装配阶段失败。当前选择显式装配，不把 class/resource isolation 写成契约。
- `app-api` 曾混合 provider-owned remote Contract、consumer-owned outbound port 和 App 内部 Seam；`UserNotificationReadPort` 已迁入 Notification，Auth 继续拥有 recipient remote Contract。
- `ContestSubmissionPort` 曾同时承载失效的同步 Submission mutation、Contest fact query 和 event 兼容语义；当前失效 mutation 已移除，Submission 主路径保持单写 Outbox/Event。
- Admin 已有 `AdminUserDetailQuery` 和 `AdminUserEnricher` 等深 Module；列表/编排的预算、扇出和 typed degradation 继续由现有同步 Module 负责，不构成新增进程依据。
- App 已有 Problem、Contest、Moderation 私有 Maven Module，但当前没有足够证据支持新增物理 deployable；一人开源项目需要保持低本地验证和运维成本。

## Decisions

1. `distributed` 继续作为本地、CI、disposable 和文档的唯一默认拓扑。
2. `Core` 只保留为有期限的 `CONDITIONAL` 实验。当前契约是显式 Owner allowlist、local/remote Adapter parity 和 disposable journey Gate；`CoreOwnerClassLoaders` 只是 parent-first 的 TCCL/生命周期辅助，不提供 sibling class/resource isolation。最终结果只能是 `PROMOTE_LATER`、`RETAIN_TEMPORARILY_WITH_EXPIRY` 或 `REMOVE_CORE_EXPERIMENT`；当前结果与 expiry 由 ADR-0012 记录。
3. Contract 所有权按 capability、数据事实、Provider、失败语义和版本责任裁决，不按调用方包名裁决。Consumer-owned outbound port 不自动进入公开 API Module；`app-api` 默认不得成为其他 API 的 Hub。
4. `UserNotificationReadPort` 已内部化到 Notification；Auth 继续拥有真正的 recipient remote Contract。若后续发现其他真实 consumer，必须走 consumer-specific 兼容分支。
5. `ContestSubmissionPort` 已移除失效同步 mutation，Submission 主路径保持 owner-owned Outbox/Event；不把一个混合 Interface 拆成新的公开远程 Interface，不恢复 Submission 双写。
6. Admin 继续使用同步深 Module、批量 Contract、bounded parallel、timeout/cancel、typed degradation、freshness 和 metrics。Admin event read model 只有在量化触发条件满足且不会形成第二数据真相时重新评估。
7. App 继续深化同一进程内的 private Module。Forum、Solution 等候选只有在真实业务/缺陷变更触发并通过 deletion test、Locality、事务、依赖和测试面评分后才 pilot；证据不足即 `NO-GO`。
8. `static` 和 `unit` 继续作为零基础设施贡献者入口；完整环境和 Core journey 按 scope 条件运行，不把 Core 成本转嫁给所有贡献者。
9. 共享 MySQL、Redis、Nacos 和单机参考拓扑是当前接受项，不在本计划中生成数据库集群、Redis 集群、Nacos HA、Kubernetes、Kafka、Service Mesh、Seata 或企业级 HA 整改。

## Consequences

### Positive

- 先解决 Module/Interface/Implementation 的真实边界，再决定是否值得改变进程边界。
- distributed 的默认成本和行为保持稳定，Core 的实验成本、失败语义和退出机制可见。
- Contract 迁移可以逐类型进行，避免大规模包移动和 API binary break。
- Admin 和 App 的复杂度优先被深 Module 隐藏，不把同步编排泄漏给页面调用方。

### Costs and risks

- Core 若要保留，任何额外的类加载或等价隔离都是 Core-only 复杂度；若收益不足，必须删除实验而不是继续堆排除项。
- Contract ownership 迁移曾带来 deprecated bridge 和编译兼容成本；后续新增跨 Owner Contract 仍需遵守同一所有权规则。
- Admin 的 batch/parallel 需要显式 budget 和 Provider 保护，否则会把 N+1 变成并发洪峰。
- 文档角色需要持续防止 current-status、issue registry、ADR 和 evidence 互相复制事实。

## Non-decisions

本 ADR 不批准：默认拓扑切换、Core 物理合并、任何新业务进程、Admin event projection、生产 HA、生产 mixed-version、远端 Judge 或数据库迁移。实现状态与当前问题以 current-status、SERVICES_ISSUES、现有 evidence 文档和可执行门禁为准。

## Evidence anchors

- [`services/core/CoreOwnerContextManager.java`](../../../services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java:217)；[`CoreOwnerBootConfigurations.java`](../../../services/core/src/main/java/com/ulticode/core/CoreOwnerBootConfigurations.java:21)
- [`services/docs/SERVICES_ISSUES.md`](../../../services/docs/SERVICES_ISSUES.md:30)
- [`services/api/app-api/pom.xml`](../../../services/api/app-api/pom.xml:12)
- [`UserNotificationReadPort.java`](../../../services/notification/src/main/java/com/ulticode/notification/recipient/UserNotificationReadPort.java:17)
- [`ContestSubmissionPort.java`](../../../services/api/app-api/src/main/java/com/ulticode/app/api/service/ContestSubmissionPort.java:3)
- [`DefaultAdminUserDetailQuery.java`](../../../services/admin/src/main/java/com/ulticode/modules/admin/query/DefaultAdminUserDetailQuery.java:164)
- [`AppModuleSplitAdmissionGateTest.java`](../../../services/app/app-web/src/test/java/com/ulticode/app/architecture/AppModuleSplitAdmissionGateTest.java:8)
- [`scripts/dev/test.sh`](../../../scripts/dev/test.sh:19)
- [`ADR-0012`](0012-core-topology-three-way-decision.md)
