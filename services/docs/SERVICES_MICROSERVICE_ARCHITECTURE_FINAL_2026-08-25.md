# UltiCode `services/` 企业级微服务架构最终评审

评审日期：2026-08-25  
评审基线：`baee04e17 fix: enforce real readiness probes and RPC policy across services`  
收口基线：`a2da6fa83 fix(dev): harden up.sh startup with rebuild flag, port preflight, port hygiene`  
本文档结构：2026-08-26 第三轮重写为三段互斥结构（已关闭或缓解 / 仍开放的企业级差距 / 生产触发条件），替代原问题标题与内嵌批注混排的旧结构
范围：`services/`、运行配置、Compose、部署脚本和架构文档  
互斥声明：与 [`SERVICES_ENTERPRISE_REMEDIATION_PLAN_2026-08-26.md`](SERVICES_ENTERPRISE_REMEDIATION_PLAN_2026-08-26.md) 互斥一致，每一事项仅出现于一个分区

## 结论

当前已经具备五个 Data Owner、两个 Worker 的多进程微服务骨架，且关键的 readiness、RPC policy、运行模式校验已经落地。但它仍不是企业级生产终态；剩余差距集中在高可用、安全隔离、跨 Owner 读模型、分布式可观测性和独立发布能力，而不是继续增加服务数量。

在项目仍只有开发环境时，不建议为了形式上的"企业级"立即引入 Kubernetes、Service Mesh、Kafka 或 Seata；先把现有 Owner、Contract 和 Worker Interface 做成可验证、可失败、可回滚的运行时边界。

---

## 一、已关闭或缓解（附当前证据）

本节所列事项已在代码或配置层落地，引用路径均可在当前仓库内验证；与"仍开放"互斥，不重复出现。

### 1. Owner 真实就绪探针（P0 假健康已修复）

Owner `/health/ready` 校验所属 DataSource（JDBC `isValid`）与 Redis `PING`，任一失败返回 503 与组件明细；原 `/health` 保留为 liveness。生产 Compose、host-health 与 DevStack 均已改用 readiness 端点作为健康门禁。Submission 维持容器内 `/actuator/health`。

证据：[`ReadinessChecks.java`](../platform/common/src/main/java/com/ulticode/common/health/ReadinessChecks.java)、`AuthReadinessController` / `AdminReadinessController` / `AppReadinessController` / `NotificationReadinessController`（`services/*/src/main/java/**/adapter/in/web/*ReadinessController.java`）、[`docker-compose.prod.yml`](../../docker-compose.prod.yml)。

### 2. Worker 就绪标记（静态 `exit 0` 已移除）

`SearchWorkerReadinessHeartbeat`（Redis ping + MeiliSearch health → 刷新 `SEARCH_READY_FILE`）与 `JudgeWorkerReadinessHeartbeat`（Redis ping → `JUDGE_READY_FILE`）取代静态健康证明；生产 Compose 以"标记 2 分钟内刷新"结合原有 Docker 能力检查作为 healthcheck。

证据：[`SearchWorkerReadinessHeartbeat.java`](../search/src/main/java/com/ulticode/search/SearchWorkerReadinessHeartbeat.java)、[`JudgeWorkerReadinessHeartbeat.java`](../judge-runtime/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerReadinessHeartbeat.java)、[`docker-compose.prod.yml`](../../docker-compose.prod.yml)。

### 3. RPC 可靠性策略统一与防漂移

全部约 90 处消费端 `@DubboReference` 统一使用 [`RpcPolicy.java`](../platform/common/src/main/java/com/ulticode/common/rpc/RpcPolicy.java) 常量（查询 `QUERY_TIMEOUT_MS=800`/`QUERY_RETRIES=1`，写入 `WRITE_TIMEOUT_MS=3000`/`WRITE_RETRIES=0`）；`RpcPolicyArchTest`（admin / app-web / submission / notification / judge 五处）以 ArchUnit 禁止裸引用与 timeout/retry 漂移。

证据：[`RpcPolicy.java`](../platform/common/src/main/java/com/ulticode/common/rpc/RpcPolicy.java)、[`RpcPolicyArchTest.java`](../admin/src/test/java/com/ulticode/admin/architecture/RpcPolicyArchTest.java)（其余四处同名）、[`../search/src/main/resources/application.yml`](../search/src/main/resources/application.yml)（全局默认 `timeout=3000 retries=0` 为写入安全边界）。

### 4. 运行模式非法组合启动失败

`FlagCombinationValidator` 在 App / Submission / Judge 进程内强制 `mode × flag` 合法组合，非法组合启动即失败；`devstack-manifest.sh` 提供声明式拓扑清单（dev-lite / dev-full 的 `APP_RUNTIME_MODE` / `APP_SUBMISSION_ROUTING_MODE` / `APP_SEARCH_READ_MODE` 一致性）。

证据：[`FlagCombinationValidator.java`](../platform/judge-config/src/main/java/com/ulticode/modules/submission/config/FlagCombinationValidator.java)、[`../../scripts/dev/devstack-manifest.sh`](../../scripts/dev/devstack-manifest.sh)。

### 5. Admin 备份持久化

Admin `backup.dir` 可经 `BACKUP_DIR` 覆盖；生产 Compose 为 `backend-admin` 挂载持久卷 `backup_data:/var/lib/ulticode/backup`。对象存储仍为后续生产化选项。

证据：[`docker-compose.prod.yml`](../../docker-compose.prod.yml)。

### 6. Redis 按 Owner ACL 安全边界

静态渲染的 [`users.acl`](../../docker/redis/users.acl)（由 [`generate-users-acl.sh`](../../docker/redis/generate-users-acl.sh) 从 `*_REDIS_PASSWORD` 渲染，密码仅 SHA-256 哈希落盘；`default` 关闭）为七个 Owner 分配最小 key pattern；七个后端服务与 [`ecosystem.config.cjs`](../../ecosystem.config.cjs) 分别注入 `REDIS_USERNAME` 与对应的 `<DOMAIN>_REDIS_PASSWORD`，不再共享单一口令。

证据：[`docker/redis/users.acl`](../../docker/redis/users.acl)、[`docker/redis/generate-users-acl.sh`](../../docker/redis/generate-users-acl.sh)、[`docker-compose.prod.yml`](../../docker-compose.prod.yml)、[`ecosystem.config.cjs`](../../ecosystem.config.cjs)、[`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) §P1 Redis per-owner ACL。

### 7. Admin 跨 Owner 静默降级已消除

`DefaultAdminUserProjection` 与 `AdminUserEnricher` 在 Auth 查询不可用时抛出类型化 503 `OWNER_QUERY_UNAVAILABLE`，不再伪装成空列表 `total=0`；单一 Provider 故障时响应载荷显式标注 [`DegradationStatus`](../platform/common/src/main/java/com/ulticode/common/response/DegradationStatus.java)（`OK` / `PARTIAL` / `STALE` / `UNAVAILABLE`，`PageResult` / `AdminUserVO` 向后兼容）。

证据：[`AdminUserEnricher.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java)、[`DefaultAdminUserProjection.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java)、[`DegradationStatus.java`](../platform/common/src/main/java/com/ulticode/common/response/DegradationStatus.java)、[`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) §Admin 静默降级已修复。

### 8. 分布式 tracing 与 Worker SLO 代码采集已补齐

Submission / Notification / Search / Judge 已补充 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` + Prometheus registry，并配置 W3C 采样与 `MANAGEMENT_OTLP_TRACING_ENDPOINT`；新增 [`WorkerSloMeters.java`](../platform/common/src/main/java/com/ulticode/common/metrics/WorkerSloMeters.java)（无 Spring 注解、无 Redis client 依赖）导出队列 lag、PEL 回收、DLQ 计数等指标，已接入 Search Worker、Judge 队列适配器与 Notification inbox bridge。

证据：[`WorkerSloMeters.java`](../platform/common/src/main/java/com/ulticode/common/metrics/WorkerSloMeters.java)、[`../search/src/main/resources/application.yml`](../search/src/main/resources/application.yml)（`management.otlp.tracing.endpoint`）。

### 9. Worker 横向扩容契约（实例唯一身份 + 回收）

Search / Judge / Notification Worker 的 Consumer 身份已实例唯一化（默认 `<group>-<hostname>` 派生，`SEARCH_WORKER_CONSUMER_NAME` 等可覆盖确定值）；PEL 回收（dead-letter + claim）配合 Unacked reaper 支持副本更替，无需额外交接步骤。

证据：[`../search/src/main/resources/application.yml`](../search/src/main/resources/application.yml)（`search.worker.consumer-name` / `ready-file`）、`WorkerSloMeters` / `UnackedStreamEntriesReaper` 实现、[`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) §Worker 横向扩容契约。

### 10. 本地状态存储接缝

App 侧新增 [`FileStoragePort.java`](../app/app-web/src/main/java/com/ulticode/app/storage/FileStoragePort.java) 存储接口（local 默认逐字节兼容旧契约；S3 兼容可选，未引入 SDK，`APP_STORAGE_TYPE` 切换）；生产 Compose 为 `backend-app` 挂载共享卷 `app_uploads:/data/uploads/avatars`。

证据：[`FileStoragePort.java`](../app/app-web/src/main/java/com/ulticode/app/storage/FileStoragePort.java)、[`docker-compose.prod.yml`](../../docker-compose.prod.yml)。

### 11. Nacos 与 Judge 的可配置化接缝

Nacos 支持 `${NACOS_MODE:-standalone}` 与 `NACOS_SERVERS` 环境变量，cluster 配置路径就绪；Judge 已透传 `DOCKER_HOST` / `DOCKER_TLS_VERIFY` / `DOCKER_CERT_PATH`，沙箱执行器可指向专用远程 daemon，socket 挂载源可经 `JUDGE_DOCKER_SOCK` 覆盖。

证据：[`docker-compose.prod.yml`](../../docker-compose.prod.yml)（Nacos `MODE` / `NACOS_SERVERS`、Judge `DOCKER_HOST` / `DOCKER_TLS_VERIFY` / `DOCKER_CERT_PATH` / `JUDGE_DOCKER_SOCK`）。

### 12. 独立发布机制（per-service 版本 + 逐服务发布/回滚）

Maven 引入 CI-friendly `<revision>`（reactor 共享）与各服务 `service.version.*`（七个 Owner/Worker 各自声明，flatten 后解析为字面量）；镜像附加 `v<version>` tag；生产 Compose 支持 `<SERVICE>_IMAGE_TAG -> IMAGE_TAG -> latest` 三级回退；`host-deploy` 支持 `services` 子集与 `service_tags` 选择性发布/回滚。

证据：[`../pom.xml`](../pom.xml)（`<revision>` / `service.version.*` / `flatten-maven-plugin`）、[`docker-compose.prod.yml`](../../docker-compose.prod.yml)（`<SERVICE>_IMAGE_TAG`）、[`.github/actions/host-deploy/action.yml`](../../.github/actions/host-deploy/action.yml)、[`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) §独立发布能力。

详细修复记录见 [`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) §8 Services review findings 2026-08-25 remediation。

---

## 二、仍开放的企业级差距（仅真实未解决项）

本节仅收录代码或单机仓库内无法闭环的事项；已关闭事项不再重述。

### 1. 生产多主机 HA 未实施

生产 Compose 仍为单机拓扑：Nacos 默认 `standalone`、固定 `container_name` 约束水平扩容、MySQL / Redis / MeiliSearch 均为单节点。无状态服务镜像可扩容，但缺少 `container_name` 去除、多副本、反向代理、以及有状态组件的集群方案（MySQL 主从/切换、Redis Sentinel/托管版、MeiliSearch 托管或双实例、Nacos 三节点集群）。

证据：[`docker-compose.prod.yml`](../../docker-compose.prod.yml)（`MODE` 默认为 `standalone`，服务含固定 `container_name`）。

### 2. Judge 节点级强隔离未实施

默认仍挂载 `/var/run/docker.sock`（dev 便利性保留），即使已配置只读文件系统、`no-new-privileges` 与 capability drop，Docker Socket 仍接近宿主 Daemon 控制权。强隔离（专用 Judge 节点、远程 rootless daemon、Kata / gVisor / VM）需节点与网络决策，尚未实施。

证据：[`docker-compose.prod.yml`](../../docker-compose.prod.yml)（Judge `volumes: ${JUDGE_DOCKER_SOCK:-/var/run/docker.sock}:/var/run/docker.sock` 与 `DOCKER_HOST` 透传并存）。

### 3. Admin 事件化读模型未实施

Admin 查询仍同步聚合 Auth 身份与 App profile，延迟与可用性串联两个 Owner；当前以 `degradationStatus=PARTIAL` 与类型化 503 显式表达降级，但仍要求调用方理解两个 Provider 的 freshness 组合。事件化本地 projection（Auth/App 发布领域事件 → Admin 消费维护本地表 → 读路径切本地 + 定时对账）尚未实施。

证据：[`AdminUserEnricher.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java)、[`DefaultAdminUserProjection.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java)。

### 4. 真实链路验证、告警与 SLO 报表尚未形成运营证据

OTel 依赖与 `WorkerSloMeters` 指标（queue lag、PEL age/size、DLQ size、last-success、消费失败率、数据新鲜度）仅为代码可采集；缺少在真实运行环境中的端到端链路贯通验证、告警阈值调优、DLQ 操作闭环与 SLO 报表。仓库内可交付的告警规则、Runbook 与故障演练脚本仍需生产流量与运营数据校准。

证据：[`WorkerSloMeters.java`](../platform/common/src/main/java/com/ulticode/common/metrics/WorkerSloMeters.java)（指标定义）、[`../search/src/main/resources/application.yml`](../search/src/main/resources/application.yml)（`management.otlp.tracing.endpoint` 仅为采集端配置）。

### 5. 混合版本发布历史尚未积累

Per-service 版本、逐服务镜像 tag、选择性 rollout/rollback 与契约兼容门禁的机制已具备（见第一部分），但尚未在真实发布中积累混合版本并存、逐服务滚动与独立回滚的历史证据；`backend-search` 镜像尚未纳入发布矩阵亦为已知缺口。

证据：[`../pom.xml`](../pom.xml)（`service.version.*`）、[`docker-compose.prod.yml`](../../docker-compose.prod.yml)（`<SERVICE>_IMAGE_TAG`）、[`.github/actions/host-deploy/action.yml`](../../.github/actions/host-deploy/action.yml)。

---

## 三、生产触发条件（每条开放差距何时必须实施）

触发条件与 [`SERVICES_ENTERPRISE_REMEDIATION_PLAN_2026-08-26.md`](SERVICES_ENTERPRISE_REMEDIATION_PLAN_2026-08-26.md) §三"推迟"一致；满足其一即启动对应实施。

### 1. 多主机 HA

当存在真实多节点生产环境并提出明确的服务可用性 SLO（如 99.9%），且单机维护窗口不再可接受时，必须实施。届时路径：无状态服务去 `container_name` + 多副本 + 反向代理；MySQL 主从/切换；Redis Sentinel 或托管版；MeiliSearch 托管或双实例；Nacos 三节点集群。

### 2. Judge 节点级强隔离

当 Judge 进入对外多租户生产，或沙箱逃逸被列为必须缓解的安全需求时，必须实施。届时路径：专用 Judge 节点 + 远程 rootless daemon + 证书轮换流程 + 网络隔离 + daemon 故障演练，按 [`PROJECT_DOCUMENTATION.md`](../../PROJECT_DOCUMENTATION.md) "Judge 沙箱执行节点隔离"拓扑实施，并通过 compose override 移除本机 `docker.sock` 挂载。

### 3. Admin 事件化读模型

满足其一即实施：① Admin 用户列表 p99 > 1s 且归因于跨 Owner RPC；② Owner 可用性事件导致 Admin 管理面月级不可用时长超标；③ Admin 读 QPS 增长到 RPC 补偿成本高于事件投影维护成本。届时草案：Auth/App 发布领域事件（Redis Streams，复用现有 integration stream 骨架）→ Admin 消费维护本地 projection 表（expand / backfill / enforce 迁移）→ 读路径切本地 + 定时对账补偿；实时批量 RPC 仅保留为回填路径。

### 4. 真实链路验证、告警与 SLO 报表

当进入生产运营阶段并需要以运行数据证明可观测性时，必须实施。触发信号：首次生产发布后有真实流量可用于端到端 trace（HTTP → Dubbo → Redis Streams）贯通验证、告警阈值（积压、PEL 老化、消费失败、last-success 停摆）基于真指标调优、DLQ（`poison:*`）操作与积压恢复演练、以及按 SLO 的报表输出。运营证据需在运行环境中产生，仓库内规则与 Runbook 仅为接线基础。

### 5. 混合版本发布历史

按发布节奏自然积累，无需单独触发；每次按服务独立发版（bump `service.version.*` → 推送 `v<version>` → 选择性 rollout/rollback）即产生一条混合版本证据。契约破坏性变更需整 reactor 同步升级，数据库迁移保持单向向前兼容。

---

## 附：与修复计划的一致性

本文档三段分区与 [`SERVICES_ENTERPRISE_REMEDIATION_PLAN_2026-08-26.md`](SERVICES_ENTERPRISE_REMEDIATION_PLAN_2026-08-26.md) 的"已完成 / 本轮修复 / 推迟待触发"互斥划分一致；该计划的 T2（混合版本 Contract 门禁）与 T3（可观测运营证据）机制在本文件第一部分列为"已关闭或缓解"的机制部分，其"仍需运行环境验证"的剩余差距在本文件第二、三部分列为开放项与触发条件。
