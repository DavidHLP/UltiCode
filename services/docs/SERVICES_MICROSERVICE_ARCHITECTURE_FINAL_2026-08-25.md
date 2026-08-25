# UltiCode `services/` 企业级微服务架构最终评审

评审日期：2026-08-25  
评审基线：`baee04e17 fix: enforce real readiness probes and RPC policy across services`  
范围：`services/`、运行配置、Compose、部署脚本和架构文档

## 结论

当前已经具备五个 Data Owner、两个 Worker 的多进程微服务骨架，且关键的 readiness、RPC policy、运行模式校验已经落地。但它仍不是企业级生产终态；剩余差距集中在高可用、安全隔离、跨 Owner 读模型、分布式可观测性和独立发布能力，而不是继续增加服务数量。

## 已关闭或缓解的评审问题

- Owner `/health/ready` 现在检查数据库和 Redis，失败返回 503；原 `/health` 保留为 liveness。
- Search/Judge 使用依赖心跳和就绪标记，Compose 不再使用静态 `exit 0` 作为 Worker 健康证明。
- 消费端 `@DubboReference` 统一使用 `RpcPolicy` 常量，并由 `RpcPolicyArchTest` 防止裸引用和 timeout/retry 漂移。
- `FlagCombinationValidator` 已强制 mode×flag 合法组合；`devstack-manifest.sh` 负责声明式拓扑。
- Admin 备份在生产 Compose 中使用持久卷；对象存储仍是后续生产化选项。

详细修复记录见 [`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) 的 Services review remediation 小节。

## 仍存在的企业级差距

### P0：生产基础设施仍是单节点拓扑

生产 Compose 使用单机 Nacos `standalone`，各服务没有副本策略；MySQL、Redis、MeiliSearch 也仍是单节点。当前可作为开发或单机部署方案，不能直接满足企业 HA、SLO、故障转移和滚动升级要求。

证据：[`docker-compose.prod.yml`](../../docker-compose.prod.yml)。

### P0：Judge 仍依赖宿主 Docker Socket

Judge 挂载 `/var/run/docker.sock`。即使容器配置了只读文件系统、`no-new-privileges` 和 capability drop，Docker Socket 仍接近宿主 Docker Daemon 控制权。

收敛方向：专用 Judge 节点、远程 Sandbox Executor、rootless daemon 或 VM/Kata/gVisor 等强隔离方案。

### P1：Redis 尚未形成 Owner 安全边界

服务主要共享同一 Redis 密码和 DB 0，隔离依赖 key prefix。Redis DB 编号和 key convention 都不是安全边界，OAuth state、限流、队列和缓存存在误读/误删风险。

收敛方向：Redis ACL/Owner credential，或按安全域拆分逻辑实例。

### P1：Admin 仍有同步跨 Owner 聚合和静默降级

`AdminUserEnricher` 同步合并 Auth 身份和 App profile；Provider 不可用时返回空结果。`DefaultAdminUserProjection` 在 Auth 查询不可用时返回空列表和 `total=0`，会把基础设施故障伪装成业务空数据。

证据：[`AdminUserEnricher.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java)、[`DefaultAdminUserProjection.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java)。

收敛方向：Admin 自有事件维护的管理读模型；实时读取只保留少量粗粒度批量 RPC，并显式表达 `partial`、`stale`、`unavailable`。

### P1：分布式 tracing 和 Worker SLO 尚未贯通

OTel 依赖和 OTLP 配置目前主要存在于 Auth、Admin、App；Submission、Notification、Search、Judge 尚未形成完整 HTTP → Dubbo → Redis Streams 链路。Search/Judge readiness 证明的是依赖可访问，不是队列正在前进。

需要补齐 W3C trace context、queue lag、PEL age、DLQ size、last-success、消费失败率和数据新鲜度 SLO。

### P1：Worker 横向扩容契约不完整

Search 默认 Consumer identity 为 `search-worker-1`。未来多副本需要实例唯一 Consumer 名称、消费者回收、积压重平衡和停机转移策略。

证据：[`services/search/src/main/resources/application.yml`](../search/src/main/resources/application.yml)。

### P1：尚未证明真正的独立发布

所有后端位于同一个 Maven `1.0.0` Reactor，生产部署使用统一 `IMAGE_TAG` 后整体 `docker compose pull/up`。当前没有充分的混合版本 Contract 兼容矩阵、逐服务滚动发布和独立回滚证明。

证据：[`services/pom.xml`](../pom.xml)、[`host-deploy/action.yml`](../../.github/actions/host-deploy/action.yml)。

### P2：部分本地状态仍限制水平扩展

Admin 备份已持久化，但头像上传、Judge workspace 等仍依赖本地或宿主路径。App/Worker 多副本需要对象存储或明确的共享存储 Interface。

## 建议顺序

1. 明确生产 HA、SLO 和故障域，先解决 Nacos、MySQL、Redis、MeiliSearch 单点。
2. 将 Judge 迁移到专用隔离执行节点，移除业务节点上的 Docker Socket 信任。
3. 为 Redis 增加 ACL/Owner credential，为 Search/Notification/Judge 补齐队列和消费 SLO。
4. 把 Admin 关键读路径迁移为本地事件 Projection，消除同步跨 Owner 静默降级。
5. 建立 Contract 版本兼容、混合版本发布和逐服务回滚门禁。

在项目仍只有开发环境时，不建议为了形式上的“企业级”立即引入 Kubernetes、Service Mesh、Kafka 或 Seata；先把现有 Owner、Contract 和 Worker Interface 做成可验证、可失败、可回滚的运行时边界。'

---

## 处置记录（2026-08-25 第二轮修复）

上述"仍存在的企业级差距"的落地情况（详细记录见 [`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) §8）：

| 差距 | 状态 |
| --- | --- |
| P1 Admin 同步跨 Owner 静默降级 | 已修复：依赖不可用显式 503（`UPSTREAM_UNAVAILABLE`），展示字段 partial 降级有告警，不再伪装空数据 |
| P1 Worker 横向扩容契约不完整 | 已修复：consumer 身份实例唯一化（`<group>-<hostname>`），PEL 回收 + 副本接管已验证 |
| P1 分布式 tracing 和 Worker SLO 尚未贯通 | 已补齐：四个 Worker 服务接入 OTel/OTLP；新增队列 lag、PEL、DLQ、last-success SLO 指标 |
| P1 Redis 尚未形成 Owner 安全边界 | 已落地：Redis ACL 按 Owner 用户与独立口令（生产强制区分），服务注入 `REDIS_USERNAME` |
| P0 Judge 依赖宿主 Docker Socket | 可配置隔离：`JUDGE_DOCKER_HOST`/`JUDGE_DOCKER_SOCK` 支持远程/rootless daemon 与专用节点；强隔离运行时仍属基础设施决策 |
| P1 尚未证明真正的独立发布 | 能力已具备：逐服务 `<SVC>_IMAGE_TAG` + `host-deploy` 的 `services` 子集发布/回滚 |
| P2 部分本地状态限制水平扩展 | 已收敛：头像上传经 `AvatarStorage` seam 落共享卷 `app_uploads`，对象存储可替换实现 |
| P0 生产基础设施单节点拓扑 | 保留决策：单机 Compose 无法提供真 HA；升级路径与触发条件见 PROJECT_DOCUMENTATION.md §8 |
