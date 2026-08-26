# `services/` 企业级修复计划（第三轮，2026-08-26）

基线：`a2da6fa83`（含第二轮修复与 Redis ACL/PM2 收口）。
本文档是唯一互斥事实源：每项问题只出现在一个状态分区（已完成 / 本轮修复 / 推迟待触发），替代
[`SERVICES_MICROSERVICE_ARCHITECTURE_FINAL_2026-08-25.md`](SERVICES_MICROSERVICE_ARCHITECTURE_FINAL_2026-08-25.md)
中"问题标题 + 分支处置批注"的混排结构（该文档由本轮 T1 重写）。

## 一、已完成（本轮收口）

### 1. 工作区未达终态（原问题 1）— 已完成

第二轮修复的 Redis Owner ACL 与 PM2 接线遗留于工作区，本轮验证后提交：

- `67609c799 fix(services): grant judge key patterns and wire per-owner redis ACL creds`
  - `users.acl` 新增 `ulticode-app`/`ulticode-submission` 对 `judge:*` 的授权（submission outbox → judge 交接 key）。
  - `ecosystem.config.cjs` 为七个 Owner 服务注入 per-Owner `REDIS_USERNAME`/`REDIS_PASSWORD`。
- `a2da6fa83 fix(dev): harden up.sh startup with rebuild flag, port preflight, port hygiene`

验证证据：

- `generate-users-acl.sh` 渲染输出与提交的 `users.acl` 逐字节一致（即 ACL 由当前 `.env` 派生，可复现）。
- 临时 Redis 实例加载 ACL 实测：八用户 AUTH+PING 全通过；`default` 无凭据被拒（NOAUTH）；`app`/`submission` 可写 `judge:*`；越权写（auth→`judge:*`、submission→`search:*`、app/judge→`csrf:*`）全部 NOPERM 拒绝。
- `node --check ecosystem.config.cjs`、`bash -n`（up.sh / generate-users-acl.sh）、双 Compose `config` 通过。
- PM2 侧密码传递链确认：`up.sh` → `load_env_file`（`set -a; source .env`）→ `pm2 startOrRestart --update-env` → `ecosystem.config.cjs` `process.env.*_REDIS_PASSWORD`。

注：主工作区剩余未提交修改全部属于 Garden design/landing 工作，与本计划无关，保持不提交。

## 二、本轮修复（worktree 并行）

### 2. 文档历史混排（原问题 2）— T1 `fix/arch-doc-restructure`

`SERVICES_MICROSERVICE_ARCHITECTURE_FINAL_2026-08-25.md` 重写为三段互斥结构（已关闭 / 仍开放 / 生产触发条件），
消除"标题仍宣称未修复 + 批注宣称已修复"的矛盾表述；`PROJECT_DOCUMENTATION.md` 中重复的 Admin/Worker
修复条目在集成阶段统一去重。

验收：文档中每个条目只出现在一个状态分区；引用路径全部存在；无"分支处置"批注残留。

### 3. 混合版本 Contract 门禁缺失（原问题 4）— T2 `feat/mixed-version-contract-gate`

现状：per-service 版本、逐服务镜像 tag、选择性 rollout/rollback 机制已落地，但 `api/*` 五个 Dubbo 契约模块
（`auth-api`/`admin-api`/`app-api`/`submission-api`/`notification-api`，275 个 Java 文件）没有机器化兼容门禁，
`PROJECT_DOCUMENTATION.md` 已明确"混合版本兼容性目前没有独立机器化门禁"。

本轮交付：

- `services/pom.xml` 新增 `contract-compat` profile：japicmp 对 `api/*` 做新旧二进制比较，绑定 verify。
- CI 新增 `_contract.yml`（reusable）并接入 `ci.yml` pipeline graph（path filter：`services/api/**`、`services/pom.xml`）；
  基线 ref 默认取最近 git tag，可在 workflow input 覆盖。
- `services/docs/CONTRACT_COMPAT_GATE.md`：基线策略、破坏性变更处置、豁免流程、已知限制。

验收：本地 `./mvnw -P contract-compat` 实跑通过（自比较零 diff；构造不兼容变更可检出）；CI YAML 语法有效。

### 4. 可观测"代码可采集"但"运营不可证明"（原问题 5）— T3 `feat/observability-ops-evidence`

现状：OTel/OTLP 与 `WorkerSloMeters`（`search.worker.*`、`judge.streams.*`、`notification.inbox.*`：queue lag、
PEL size/age、DLQ 计数、last-success、consume failures）已接入，但无告警阈值、无 DLQ 操作流程、无故障演练。

本轮交付（仓库内可交付的最大集合；真实链路证据仍需运行环境，见分区三）：

- `docker/prometheus/worker-slo-alerts.yml`：基于真实指标名的告警规则（积压、PEL 老化、消费失败、last-success 停摆），
  附接入说明（rule_files 挂载方式）。
- `services/docs/WORKER_SLO_RUNBOOK.md`：指标语义、告警响应、DLQ（`poison:*`）检查/重放/丢弃流程、
  积压恢复与 PEL 回收操作（对齐 Search/Judge/Notification worker 的真实实现）。
- `scripts/dev/drill-worker-failure.sh`：Worker 故障演练（默认 dry-run；显式确认后对 dev 栈执行 kill→滞留→重启→恢复验证）。

验收：`promtool check rules` 通过；脚本 `bash -n` + `--dry-run` 通过；runbook 中每个操作引用真实类/指标名。

## 三、推迟（有明确生产触发条件，不在本轮实施）

### 5. Admin 读模型仍同步耦合 Auth/App（原问题 3）— 推迟

现状（已核实）：静默降级已消除——`AdminUserEnricher` 返回 `degradationStatus=PARTIAL`，Auth 查询不可用返回类型化
503（`OWNER_QUERY_UNAVAILABLE`）。剩余差距是延迟/可用性仍串联两个 Owner，调用方需理解两个 Provider 的
freshness 组合。

推迟理由：事件化读模型要求 Auth/App 先有可靠的领域事件发布（当前无），且 Admin 查询是低频管理面操作，
PARTIAL 语义已可接受；在无生产 SLO 数据证明 RPC 聚合是瓶颈前实施属于过度设计。

生产触发条件（满足其一）：① Admin 用户列表 p99 > 1s 且归因于跨 Owner RPC；② Owner 可用性事件导致 Admin
管理面月级不可用时长超标；③ Admin 读 QPS 增长到 RPC 补偿成本高于事件投影维护成本。

届时实施草案：Auth/App 发布领域事件（Redis Streams，复用现有 integration stream 骨架）→ Admin 消费维护本地
projection 表（expand/backfill/enforce 迁移）→ 读路径切本地 + 定时对账补偿；实时批量 RPC 仅保留为回填路径。

### 6. 真实多主机 HA 未实施（原问题 6）— 推迟

现状（已核实）：Nacos `NACOS_MODE/NACOS_SERVERS` 可切 cluster；无状态服务镜像可横向扩容；但固定
`container_name`、单机 Compose、MySQL/Redis/MeiliSearch 单点依旧。**多主机 HA 物理上无法在本仓库内完成**，
需要生产基础设施决策（节点、网络、存储）。

生产触发条件：存在真实多节点生产环境与明确的服务可用性 SLO（如 99.9%），且单机维护窗口不再可接受。
届时路径：无状态服务去 `container_name` + 多副本 + 反向代理；MySQL 主从/Orchestrator；Redis Sentinel 或托管版；
MeiliSearch 托管或双实例；Nacos 三节点集群。

### 7. Judge 强隔离只有接缝（原问题 7）— 部分落地，其余推迟

现状（已核实）：`DOCKER_HOST`/`DOCKER_TLS_VERIFY`/`DOCKER_CERT_PATH` 透传与 compose override 已落地，
远程/rootless daemon 可配置；默认仍挂载本机 socket（dev 便利性保留，prod 建议文档化）。

生产触发条件：Judge 进入对外多租户生产、或沙箱逃逸被列为必须缓解的安全需求时：专用 Judge 节点 + 远程
rootless daemon + 证书轮换流程 + 网络隔离 + daemon 故障演练。届时按 `PROJECT_DOCUMENTATION.md` "Judge 沙箱
执行节点隔离"的拓扑建议实施。

## 四、明确不做

- 不拆分新服务、不引入新 MQ / Service Mesh / Kubernetes（与 2026-08-25 评审结论一致）。
- 不在无生产 SLO 数据的情况下为"形式上的企业级"引入组件。
