# Security Improvements Plan

---

Objective: move the Services Strangler migration from an established multi-process Owner topology toward stronger boundaries, while preserving local rollback paths and avoiding production claims.

Task DAG: `ARCH-001` (done) → `{ARCH-002 blocked, ARCH-004 done, ARCH-005 done}`; `ARCH-003` is blocked by absent external production stability evidence; `ARCH-006/ARCH-007` remain in progress until the external gates close.

Local implementation evidence is retained below and in `.local/evidence/`, but it is DEV-LOCAL/TEST-TARGET rehearsal evidence only. It MUST NOT close ARCH-002/003 or be described as external/production acceptance.

Implementation slices completed locally:

- `ARCH-001`: immutable Submission facts snapshot and synchronous callback removal.
- `ARCH-004`: bounded Admin analytics seam.
- `ARCH-005`: topology/POM/PM2/Compose/docs synchronization.
- `ARCH-002-001` and `DEV-LOCAL-001..008`: local migration preparation, grants, backfill, cutover/rollback rehearsal and focused validation.

External blockers:

- `ARCH-002`: missing external target/account authority, users/profile responsibility sign-off, physical cutover and remote rollback evidence.
- `ARCH-003`: missing production stability window, deployment authority, all-writer quiesce, observation/rollback and compatibility-retirement evidence.

Validation order: focused API/owner tests → App/Admin regressions → services checks → Compose/PM2/Bash/static checks → external evidence review. No production grant, deployment, remote route change or compatibility deletion is authorized by this task.

## Current execution packet — ARCH-002/ARCH-003 blocker remediation (2026-08-19)

### Test Environment Authority Override (2026-08-19)

- 用户确认本项目只有当前测试环境，不存在独立开发环境或 production environment，并授权 Agent 完成全部测试环境账号、direct grant、Flyway、backfill、writer quiesce、cutover、监控、观察、故障注入、rollback 和 compatibility retirement 操作。
- 本节是当前 Execution Packet 的最高优先级环境裁决，覆盖下文把当前环境称为 `DEV-LOCAL`、把验收保留给外部/production target、或因此保持 Task blocked 的历史措辞；脚本中的 `dev-local` 文件名和确认 token 仅为兼容名称。
- 当前 `.env`、`ulticode-mysql`、`ulticode-redis`、Docker Compose 和 PM2 构成唯一授权 TEST-TARGET。所有证据必须来自该目标的真实执行，不得改写为 production evidence。
- 已记录的 `DEV-LOCAL-001..008` 结果作为 TEST-TARGET baseline 使用，但不能只靠重命名关闭 ARCH Task；必须重新执行对应权限、数据、quiesce、cutover、observation、rollback、兼容退役和最终验证 Gate。
- 本地 Task DAG、least-privilege、single-writer、all-writer quiesce、fail-closed、secret、backup、水位和 rollback 不变量保持不变。`ARCH-002-002` 及后续外部门禁保持 `blocked`，仅按真实外部证据推进。

### Objective

完成 `ARCH-002` 的真实 Owner 数据库隔离证据和 `ARCH-003` 的远程路由稳定/兼容退役证据，使 Services Owner architecture hardening 从“本地完成、外部门禁 blocked”进入可重新收口状态。唯一执行方向是：先完成本地特权 migration job preparation，再取得外部权限与运行时证据，最后才允许物理 cutover、观察和兼容路径退役。

### In Scope

- 复用并收紧 `scripts/dev/migrate.sh`，形成显式 `MIGRATION_DB_*`、owner schema allowlist、privilege preflight、审计输出和 secret contract；不创建第二套 Flyway 入口。
- 取得授权目标的 migration principal、Owner runtime principals、DB privilege matrix、cutover window、备份/watermark/rollback owner 和 `users/profile` responsibility evidence。
- 按 `expand → backfill → verify → cutover → observe → contract` 完成 Auth/Admin/App/Notification/Submission 的物理 schema/account/permission 验证。
- 建立 remote route、Dubbo/provider、DB/Redis/outbox/PEL、restart、error/latency、double-writer 和 rollback 观察证据。
- 在所有 writer quiesce、观察窗口、回滚演练和生产 deployment authority 齐全后，执行 App local/legacy/shadow/Judge/Search compatibility path 的功能、性能、安全和并发退役验证。
- 最终重新执行 Submission/Admin focused tests、Maven reactor、脚本/YAML/diff、Tier C validation、Review 和 Coverage Gate。

### Out of Scope

- 当前本地会话不执行生产部署、远程 DB/grant/cutover、监控发布、secret rotation、route switch、commit、push、merge 或 destructive reset。
- 不给 runtime account 增加 DBA 权限；不把 MySQL `1044` 通过关闭 fail-closed 或修改权限边界伪装为成功。
- 不编辑 applied migration，不删除旧 `users`/profile 列，不提前删除 local/legacy/shadow provider、dispatcher、adapter 或 rollback path。
- 不新增 broker、共享业务表、跨 Owner SQL、2PC 或第二套兼容架构。

### Root Cause / Capability Gap

1. Auth `validate` 使用了不能执行 owner DDL/GRANT 的 runtime account，因此 MySQL `1044` 是正确的 fail-closed 结果；缺口是显式 privileged migration job 和授权执行身份，而不是业务代码。
2. Auth/Admin/App/Notification 仍以 `DB_*` fallback 为主，`users/profile` owner responsibility、backfill、权限和真实目标观察未闭合。
3. 本地已有 Submission route/grant/quiesce/rollback helper，但当前项目没有 production environment，无法证明 remote stability window、全写入者静默、观察期或生产兼容退役。
4. `.auto-flow/RESUME.md` 保留旧 `CONTRACT-007/008` active wording；本计划将以最新 `HANDOFF.yaml`、`AUTO_PILOT_STATUS.yaml` 和本 Execution Packet 明确覆盖其历史指针，并更新 Resume 为最小当前恢复信息。

### Behavioral Invariants

- migration principal 与 runtime Owner principal 永远分离；runtime 不拥有 global/schema-wide `ALL`、`GRANT OPTION`、隐式 role 或未登记的其他 Owner DML。既有 `auth_rw`/`app_rw` 对 `admin.audit_outbox` 的 table-scoped `INSERT` 是唯一登记的 append-only audit contract seam，必须继续拒绝 SELECT/UPDATE/DELETE。
- 所有输入、schema、account host、privilege inspection、writer inventory 和 cutover marker 缺失或歧义时 fail closed。
- 每个业务表只有一个 Owner writer；route、provider、dispatcher、reaper、scheduler 和 direct client 的写入方向必须可审计。
- `users/profile` 迁移保持 expand/backfill/verify 兼容顺序；不编辑 applied migration，不进行跨 Owner SQL 或分布式事务。
- Submission cutover/rollback 必须 all-writer quiesce；失败时保持 writers 停止，先完成数据、grant、watermark 和 reconciliation。
- HTTP/Result/URL/cookie/JWT/RBAC/audit/timeout/retry 等公共行为保持兼容；兼容路径只能在完整证据后成组退役。
- 凭据只存在于部署 secret store/CI secret/environment；控制面、日志和测试输出不得包含密码或完整敏感连接串。

### Acceptance Criteria

- 用户提供的 1.1–1.5、2.1–2.5 十个要求逐项映射到 `ARCH-002-001..005` 和 `ARCH-003-001..005`，每项具备独立 Acceptance、Validation、Rollback、Evidence。
- `ARCH-002-001` 的本地 migration job preparation 完成后，`ARCH-002-002..005` 仍必须取得真实外部证据，不能由 disposable MySQL 代替。
- `ARCH-003-001..005` 只有在授权远程稳定窗口、quiesce、observation、rollback 和 production deployment evidence 齐全后才能推进；不能凭本地单测关闭 blocker。
- `ARCH-007` 最终 Gate 通过后，才允许将 `ARCH-002`、`ARCH-003` 标记为 `done`；否则保持 `blocked` 并记录精确解除动作。

### Required Evidence

- migration job source/test、explicit credential contract、schema allowlist、preflight/privilege snapshot、Flyway history/validate 输出。
- DB target/account/host/role/grant approval、备份校验、rows/checksum/watermark、`users/profile` backfill 和 reader/writer matrix。
- 真实目标的跨 schema permission-denial、all-writer quiesce、route/provider/DB/Redis/outbox/PEL stability、latency/error/restart 和 double-writer 报告。
- observation window、故障注入、rollback/reconciliation、production monitoring/cutover approval、secret references 和 release sign-off。
- 最终 focused tests、Maven `BUILD SUCCESS`、integration/performance/security/concurrency/compatibility review、Compose/POM/YAML/bash/diff/graph Coverage。

### Delivery Authority

用户已授权当前开发环境的本地 remediation；本地可执行 DEV-LOCAL 的账号/grant/Flyway/cutover/monitoring/quiesce/rollback rehearsal。真实 DB 权限、cutover window、production monitoring/deployment、route/grant switch、compatibility deletion 和远端证据仍需要外部 owner/release/DBA 明确授权；不得把本地结果当作 production action 或 external Acceptance。

### Development Environment Authorization Boundary (2026-08-19)

用户已授权当前开发环境的本地 remediation。该授权允许在 disposable/local DB、Docker Compose、PM2 和本地测试目标上执行账号、grant、Flyway、cutover、故障注入、monitoring 和 rollback rehearsal，但不得提交凭据、编辑 applied migration 或执行未授权的远程资源变更。

- 所有本地结果必须标记为 `DEV-LOCAL`，只能证明开发环境行为、脚本契约和可重复 rehearsal。
- `ARCH-002-002..005` 的真实目标权限、users/profile responsibility、物理隔离和 Auth privileged target evidence 仍不能由本地 Docker/PM2/disposable DB 推导。
- `ARCH-003-001..005` 的 production stability、deployment authority、真实全写入者 quiesce、观察窗口和兼容退役证据仍不能由开发环境推导。
- 本地 remediation 可修复根因、生成可审计开发证据并为外部 Gate 准备 artifact，但在上述外部证据缺失时不得把对应 Task 或 parent 标记为 `done`。

### DEV-LOCAL Execution Packet

本地授权后的唯一执行 DAG 为：

`DEV-LOCAL-001 → DEV-LOCAL-002 → DEV-LOCAL-003 → DEV-LOCAL-005 → DEV-LOCAL-004 → DEV-LOCAL-006 → DEV-LOCAL-007 → DEV-LOCAL-008`

- `DEV-LOCAL-001` 先修复 owner preflight 在宿主无 `mysql` CLI 时无法运行的问题，优先复用 `ulticode-mysql` container，不改变 migration identity 或 Flyway 配置语义。
- `DEV-LOCAL-002..004` 只在本地开发数据库执行 owner schema Flyway、runtime account/grant、users/profile backfill、Submission cutover 和 rollback rehearsal。
- `DEV-LOCAL-005..007` 只生成开发环境 monitoring、writer inventory、quiesce、observation、fault-injection、rollback 和 compatibility readiness evidence。
- `DEV-LOCAL-008` 收集本地 validation packet；不得把 `DEV-LOCAL` evidence 直接转换为 `ARCH-002-002..005` 或 `ARCH-003-001..005` 的 external Acceptance。

### DEV-LOCAL Evidence Coverage

| DEV-LOCAL Task | Local artifact | External gate not closed |
| --- | --- | --- |
| `DEV-LOCAL-001` | container-aware owner preflight and regression test | `ARCH-002-002/004` real target authority |
| `DEV-LOCAL-002` | owner Flyway history, account/grant snapshots and runtime negative checks | `ARCH-002-002/004` external target/sign-off |
| `DEV-LOCAL-003` | users/profile rows/checksum/orphan/duplicate and idempotency report | `ARCH-002-003` responsibility/cutover authority |
| `DEV-LOCAL-005..006` | DEV-LOCAL route/monitoring/quiesce/observation/rollback packet | `ARCH-003-001..004` production evidence |
| `DEV-LOCAL-004` | Submission cutover preflight/copy/rollback/failure injection | `ARCH-002-005` physical target gate |
| `DEV-LOCAL-007` | compatibility scan and rollback-only decision | `ARCH-003-005` production retirement evidence |
| `DEV-LOCAL-008` | local validation and updated Handoff | `ARCH-007` remains pending until both external chains close |

### Terminal Condition

当 `ARCH-002-001..005`、`ARCH-003-001..005` 和 `ARCH-007` 全部 `done`，每个用户要求都有外部 Evidence/Review/Validation/Rollback，实际 owner account/grant/users responsibility/remote stability/quiesce/observation/rollback/monitoring 证据齐全，且最终 Review `Confirmed Findings=0` 时，才关闭两个 blocker。当前仅 `ARCH-002-001` 与 DEV-LOCAL remediation 已在本地完成；`ARCH-002-002..005`、`ARCH-003-001..005` 和 `ARCH-007` 保持 `blocked`，等待外部证据。

### Task DAG

`ARCH-001 → ARCH-002-001 → ARCH-002-002 → {ARCH-002-003, ARCH-003-001} → ARCH-002-004 → {ARCH-003-004, ARCH-003-002} → ARCH-002-005 → ARCH-003-003 → ARCH-003-005 → ARCH-007`

`ARCH-003-001` is monitoring/baseline setup and `ARCH-003-004` is pre-cutover monitoring/control deployment; both must precede `ARCH-002-005`. `ARCH-003-003` owns the post-cutover observation and rollback rehearsal. All later Tasks remain `blocked` or `pending` until their external evidence exists.

### Implementation Steps

1. 执行 `ARCH-002-001`：扩展现有 `migrate.sh` 的显式 migration connection contract、owner schema allowlist、privilege preflight、审计输出和 disposable MySQL 测试。
2. 由 DBA/release owner 提供并核验 `ARCH-002-002` 的真实目标、账号、权限、窗口、备份和回滚责任。
3. 在授权窗口完成 `ARCH-002-003` users/profile expand/backfill/verify 和责任切换。
4. 使用 privileged job 完成 `ARCH-002-004` Auth validate/migrate，保持 runtime Flyway fail closed。
5. 在授权目标部署 `ARCH-003-004` 的 production monitoring/cutover controls，但不执行最终 route switch。
6. 完成 `ARCH-003-002` 的 writer inventory、drain/quiesce manifest 和 cutover rehearsal。
7. 执行 `ARCH-002-005` 物理隔离 cutover、grant denial、parity、smoke 和 atomic data rollback；post-cutover observation 不在此 Task 提前声称完成。
8. 按 `ARCH-003-003` 执行完整观察期、故障注入和 route/grant/watermark/reconciliation rollback rehearsal。
9. 仅在观察与 rollback 证据通过后执行 `ARCH-003-005` compatibility static scan、功能/性能/安全/并发验证和成组删除。
10. 执行 `ARCH-007` 最终 Review、Validation、Coverage、Completion Audit，更新 parent task 状态和 Handoff。

### Compatibility / Rollback

本计划不新增兼容层。migration 失败回到上一 verified migration/config artifact；数据切换失败只使用 route/grant/watermark/reconciliation runbook；route/monitoring 发布失败回滚上一 release artifact；compatibility deletion 失败回滚完整 artifact，不保留半删除 provider 或双 writer。

### Focused Checks

- `bash -n scripts/dev/migrate.sh scripts/dev/up.sh scripts/dev/test.sh`
- privileged job unit/preflight + disposable MySQL privilege/role/schema denial tests
- Auth migration `info/validate` with explicit migration identity and runtime-account negative test
- users/profile backfill idempotency, rows/checksum/orphan/duplicate and reader/writer matrix checks
- existing Submission cutover helper preflight/quiesce/copy/parity/rollback failure injection
- remote route/provider/outbox/PEL/restart/error/latency/double-writer observation queries
- 用户要求的 Submission API/Facts/Provider、Admin adapter/service tests and Maven reactor `BUILD SUCCESS`

### Final Validation Tier

Tier C/D cross-service owner/config/startup validation：focused → module → integration → reactor `verify` → TEST-TARGET cutover/observation/performance/security/concurrency review → Compose/POM/YAML/bash/diff/graph Coverage → `ARCH-007` Completion Gate。当前测试环境是唯一目标，不制造或要求不存在的 production evidence。

### Expected Review Areas

Least privilege、secret leakage、migration ordering、users/profile ownership、backfill idempotency、transaction boundaries、all-writer quiesce、DB/Redis/outbox/PEL consistency、route/provider single-writer、monitoring blind spots、rollback atomicity、public contract compatibility、performance soak、legacy path deletion、protected dirty worktree 和 stale Resume pointer。

### State Files to Update

`.auto-flow/TASKS.yaml`、`.auto-flow/COVERAGE.md`、`.auto-flow/DECISIONS.md`、`.auto-flow/PLAN.md`、`.auto-flow/RESUME.md`、`.auto-flow/HANDOFF.yaml`、`.auto-flow/WORKLOG.md`。这些文件只记录计划和证据，不作为业务交付物，也不得加入 Commit。

---

# Submission / Search Extraction Execution Packet

## Objective

将 Submission 判题生命周期和 Search 索引写入路径分别落成 `backend-submission` 与 `backend-search` 两个独立部署单元，同时保持现有外部 HTTP、`Result`、JWT/cookie、Dubbo 和 Judge sandbox 行为兼容。Submission 是业务数据 Owner；Search 是无 HTTP、无业务表的索引 worker；`backend-judge` 保持独立执行 worker，不承载业务数据。

## In Scope / Out of Scope

In scope：Submission Owner 负责 `submissions`、`submission_test_details`、`judge_outbox`、`submission_result_outbox`、intake/verdict/fence/reaper/rejudge 与用户/Admin 契约；Search worker 负责四类安全索引事件、Redis Streams consumer/inbox、MeiliSearch UPSERT/DELETE、重试/DLQ/lag、backfill/replay/watermark；App/Auth/Problem/Forum/Solution/Contest 继续拥有源数据，App 保持 `/search` 兼容读与 DB fallback。

Out of scope：Contest 排名/滚榜独立化、Moderation 物理服务、Notification 物理服务、WebSocket owner 合并、新消息中间件、共享业务表复制和 destructive schema reset。

## Root Cause or Capability Gap

- `services/app/modules/submission` 只是 App reactor 内的 domain jar；`DefaultSubmissionWritePort` 仍在 `app-web`，在一个事务中写 Submission、judge outbox 并调用 Contest association；`backend-judge` 通过依赖 `backend-app-web` 获得实现。
- Judge 的 Streams、generation fence、lease reaper、result outbox 已画出高负载/故障接缝，但数据 Owner 仍未物理分离。
- Search 已有四个 `SearchSource` 和 `DefaultSearchReadProjection`，但当前只有 App 内读配置/DB fallback，没有 durable index writer 和可重放索引事件。
- 旧迁移指南把两者留在 App 是历史保护性决策；本目标明确改变 target，必须新增 Owner/网络/数据迁移边界。

## Behavioral Invariants

- Submission intake + `judge_outbox`、verdict fence + `submission_result_outbox` 各自在 Submission Owner 本地强一致事务内完成；跨服务不做 2PC、不跨服务 SQL。
- Redis Streams/PEL at-least-once；完成 durable handoff/verdict write 后才 ACK；`generation`/`attemptId` 拒绝 stale/duplicate overwrite，reaper 回收失联 claim。
- 代码、hidden testcase、token、cookie、密码和完整敏感 DTO 不进入事件、索引 document、日志或测试输出；Search document 只允许索引安全字段。
- `backend-judge` 无业务表、无公网 HTTP/Dubbo；只通过 Submission/Problem contracts 读 facts、抢 lease、写 verdict。
- App/Auth/Problem/Forum/Solution/Contest/Notification 仍是各自业务表 Owner；同一时刻不得有双 writer 或双索引 writer。
- 用户 URL、`Result` 字段、认证/授权、限流和 Admin audit actor 保持兼容；切换失败只回滚 route/flag/watermark，不修改 applied migration。

## Acceptance Criteria

1. `SPLIT-001` 冻结 Owner matrix、事件字段/版本/幂等/redaction 规则，契约测试通过。
2. `backend-submission` 可独立构建/启动；App local/remote seam 明确且 single-writer；Judge 不再依赖 `backend-app-web` 业务实现。
3. 四张 Submission 目标表完成 expand/backfill/verify/cutover，独立 grants、row-count/checksum/reconciliation 证据齐全。
4. Judge、Contest、Admin、Notification、Achievement、App consumers 经 typed contract/event/inbox 连接，无跨服务 SQL/同步长事务。
5. 四类 source 发布 `SearchDocumentChanged`；`backend-search` 无 HTTP/业务 DB，只消费 Streams、幂等写 MeiliSearch、支持 replay/backfill。
6. `/search` Result、分页、URL、highlight、DB fallback 保持兼容；worker 是唯一 MeiliSearch index writer。
7. `SPLIT-005` 的 focused、integration、reactor、Compose、security/concurrency/architecture review 全部通过。

## Required Evidence

- Graph project `UltiCode` architecture snapshot：32,314 nodes / 117,680 edges；已核验 `SubmissionController`/`DefaultSubmissionWritePort`、judge dispatcher/reaper/result outbox、`SearchController`/`DefaultSearchReadProjection`/四类 `SearchSource`。当前 MCP 未暴露 `check_index_coverage`，负向结论必须用精确源码 fallback 复核并披露范围。
- API contract/ArchUnit tests；真实 MySQL migration/grant/backfill/reconciliation；真实 Redis Streams PEL/claim/ACK/replay；MeiliSearch upsert/delete failure tests。
- App/Admin/Judge/Submission/Search boot/config/Compose evidence；`git diff --check`；formal standards/spec/security/concurrency review；受保护 dirty worktree 清单不变。

## Active Task / Dependencies

All tasks from `SPLIT-001` through `CONTRACT-008` are complete; no active task remains in this objective. The dependency graph is retained below as historical execution context.

`SPLIT-001 → SPLIT-002 → SPLIT-003 → SPLIT-004 → SPLIT-005`；`SPLIT-001 → SEARCH-001 → SEARCH-002 → SEARCH-003 → SPLIT-005`。仅依赖全部满足的 Task 可进入 `ready`。

## Files / Symbols / Call Chains

Contracts 在 `services/api/app-api/.../event/` 与 `IntegrationEventPublisher/Dispatcher`；Submission 在 `app-web` submission/controller/port/reaper/result、`modules/submission` entity/mapper/outbox、`services/api/app-api` ports/DTOs；Judge 在 `services/judge` 与 queue pipeline；Search 在 `modules/search`、四个 source owner writer、Meili config；存储/运维在 `init-db/migrations`、Compose、`scripts/dev` 和迁移指南。

## Implementation Steps

1. `SPLIT-001`：新增 dependency-free Submission/Search event contracts 与测试，写入 DEC-011 并更新迁移指南目标边界。
2. `SPLIT-002`–`SPLIT-004`：建立 Submission runtime/remote seam，迁移存储唯一 writer，再切 Judge/Contest/Admin/Notification/App consumers。
3. `SEARCH-001`–`SEARCH-003`：四类 source 接入 outbox，建立 Search worker，执行 backfill/replay 与读/写 owner cutover。
4. `SPLIT-005`：执行最终 gate，才退役 compatibility writers/flags 并关闭目标。

## Current execution packet — CONTRACT-007 close and CONTRACT-008 final gate

1. Reconcile the owner/runtime matrix, direct Submission provider boundary, grants, route contract, rollback runbook and explicit out-of-scope boundaries.
2. Run `./scripts/dev/test.sh quick`, official integration, services `verify`, focused provider/owner IT, Compose base+dev/prod config, YAML/bash checks and `git diff --check`.
3. Complete the final security/concurrency/architecture review and Completion/Coverage Audit; preserve explicit local-only scope without claiming production.
4. Preserve rollback as route/grant/watermark/reconciliation only; do not rewrite applied migrations or perform production/destructive actions.

Gate evidence collected 2026-08-18: direct Submission provider contract tests passed 4/4; real MySQL `SubmissionOwnerCutoverIT` passed 4/4; real Redis `JudgeStreamRedisIntegrationTest` passed 4/4; official quick and official integration completed with exit 0, with a post-run aggregate of 823 reports, 2,720 tests, 0 failures, 0 errors and 24 explicit skips; services `verify` completed with exit 0 and BUILD SUCCESS. The local runtime authority, App grant revocation, direct provider retirement, Compose/YAML/bash checks, graph refresh and final Review/Coverage Audit are closed. No production environment or production action is claimed.

## Repair Execution Packet — SPLIT-005 environmental blockers (2026-08-17)

### Objective

解除两个可本地修复的验证阻塞：让官方 `test.sh quick` 不再依赖既有持久 MySQL 的历史 root 密码；让 sandbox namespace IT 在镜像缺失时以显式环境前置门禁跳过，而不是把 Docker Hub 不可达误报成业务回归。第三个 compatibility/grant retirement 阻塞只建立授权门，不执行 release cutover。

### Root Cause / Capability Gap

- `scripts/dev/test.sh` 总是把当前 `.env` 的 `MYSQL_ROOT_PASSWORD` 交给既有 `ulticode-mysql`；持久卷由更早凭据初始化后，脚本没有隔离回退。
- `SandboxNamespaceIsolationIT` 直接 `docker run ulticode-sandbox:latest`，缺少 `SandboxForkE2EIT` 已有的 docker/image availability assumption。
- `docker-compose.prod.yml` 的 `APP_SUBMISSION_ROUTING_MODE=local`、Submission 的 `owner.mode=compat` 与 App table grants 仍承担 rollback/contest compatibility；改变它们会改变 Owner、路由和数据权限，不能由本地验证请求推断授权。

### In Scope / Out of Scope

In scope：`scripts/dev/test.sh` 的 disposable local MySQL fallback；`SandboxNamespaceIsolationIT` 的 image/CLI 前置门禁；相应 focused checks、控制面和验证证据。

Out of scope：停止/重置/删除 `ulticode-mysql` 或 `mysql_data`；Docker Hub pull、伪造/替换 sandbox image；修改 Docker sandbox security policy；切换 production/default routing、撤销 grants、删除 compatibility writers、编辑 applied migration、commit/push/deploy。

### Behavioral Invariants

- 凭据有效时 quick 继续复用既有 MySQL；凭据失配时只启动 loopback ephemeral disposable MySQL，cleanup 只清理由本次脚本创建的容器。
- disposable test database 使用与现有 Flyway chain 相同的 migration user/runtime user 分离；测试失败不污染持久环境。
- sandbox 镜像存在时原有 namespace assertions 不变；不存在时只标记 skip，不把 skip 计为真实 sandbox security evidence。
- compatibility/grant retirement 在获得 release/cutover authority 前保持 local/compat、可回滚 grants 和现有 runbook。

### Task DAG / Unique Path

`SPLIT-004 + SEARCH-003 → SPLIT-005-env-quick → SPLIT-005-env-sandbox → SPLIT-005-retirement-authority → SPLIT-005-runtime-access → SPLIT-005-runtime-cutover-observation`; `SPLIT-005` parent remains `in_progress` until runtime access and observation evidence are complete. Authority is done; runtime access is blocked by the missing deployment target/account/runtime.

### Implementation Steps

1. `SPLIT-005-env-quick`：probe existing root login；成功则保持现有流程；失败则验证本地 `TEST_MYSQL_IMAGE`（默认 `mysql:8.0`），以随机 loopback port 启动 disposable MySQL，重定向 `DB_HOST/DB_PORT`，并用 trap 清理该容器；镜像不存在时给出离线解除条件并停止。
2. `SPLIT-005-env-sandbox`：复用 JUnit 5 `Assumptions` + bounded `docker image inspect` probe，在 namespace IT 的 test setup 层跳过缺少 Docker/image 的环境；不改 sandbox runtime command、seccomp 或 Dockerfile。
3. `SPLIT-005-retirement-authority`：在无授权期间只保留 read-only audit/preflight；取得 release owner 的明确 route/grant 权限、checksum/outstanding-event/rollback-watermark evidence 后，才允许按既有 runbook 执行 cutover。当前 task 保持 blocked。

当前 sandbox 验证：本地 `ulticode-sandbox:latest` 与 Docker/Testcontainers 可用；官方 `*IT,*IntegrationTest` integration 已完成，`SandboxNamespaceIsolationIT` 与 `SandboxForkE2EIT` 各 6/6 执行且 0 failures/errors/skips。缺镜像时仍由 focused assumption 明确 skip，不替代真实 image coverage。

### Compatibility / Rollback

Quick fallback 是测试 harness 的环境隔离，不是生产配置迁移；回滚只删除脚本逻辑并保留既有持久容器。Sandbox change 仅影响缺失前置时的测试结果。Runtime cutover 仍遵循 expand → backfill → verify → cutover → observe，失败只恢复 route/grant/watermark/reconciliation，绝不改写 applied migration 或删除数据。

### Focused Checks / Final Validation Tier

先执行 `bash -n scripts/dev/test.sh` 与 quick wrapper；再执行 SandboxNamespace focused IT、services-wide `*IT,*IntegrationTest`、full reactor `verify`、Compose base/dev/prod、`git diff --check`。无本地 sandbox image 时，最终记录 skipped prerequisite，不能替代真实 image execution；父 Gate 仍等待 retirement authority。

### Delivery Authority / Terminal Condition

本轮只授权本地 script/test/control-plane 修改和验证，不授权共享数据库、生产配置、Docker registry、迁移、grant、release 或 Git 交付动作。环境子任务完成后，终止条件仍是：quick 可在本地凭据漂移下运行、缺镜像时 IT 诚实跳过、真实 sandbox evidence 可在 image/registry 可用后补齐，并且 compatibility/grant retirement 有明确 release/cutover authority。

## Compatibility / Rollback

Cutover 前只允许一个显式 local/remote flag；migration 按 expand → backfill → verify → cutover → observe，回滚使用 route/flag/watermark/reconciliation；Search worker 故障由 App DB fallback 服务读请求，Submission/Judge 故障保留 outbox/PEL，不把未 ACK 结果当完成。

## Focused Checks / Final Validation Tier

首 Task：`cd services && ./mvnw -pl api/app-api -Dtest='*EventContractTest' test -B` 与 `git diff --check`。最终 Tier D：契约 → MySQL/Redis/MeiliSearch integration → boot/no-HTTP/no-DB/single-writer → reactor verify → Compose base+dev/prod → formal review 与 rollback audit。

## Expected Review Areas / State / Delivery / Terminal

Review owner/writer 唯一性、跨服务事务泄漏、事件 schema/敏感 payload、generation/PEL/ACK crash window、migration/grants、API/auth/audit、worker 网络暴露、索引 allowlist/replay、无投机服务及受保护 dirty changes。状态文件为 `.auto-flow/TASKS.yaml`、`COVERAGE.md`、`DECISIONS.md`、`PLAN.md`、`RESUME.md`、`HANDOFF.yaml`、`WORKLOG.md`，不进入 Commit。仅授权本地 plan/implementation/review/validation，不授权 commit/push/PR/merge/deploy/shared migration。终止条件是所有 SPLIT/SEARCH tasks done、evidence 完整、single writers/独立故障域成立且明确记录未拆的 Contest ranking、Moderation、Notification。

## Goals

在继续拆分 `backend-admin` / `backend-app` 之前，收紧 Auth-issued access JWT 的契约，并确定吊销传播方案：

1. 让 `iss` 真正由 `backend-auth` 签发并由两个 resource server 严格校验。
2. 增加保持当前浏览器 cookie 兼容性的 platform audience `ulticode-api`，由两个 resource server 严格校验。
3. 移除 `RS256` 未命中 `kid` 时回退 `HS256` 的安全弱化，并把 RSA key 生命周期改为支持多副本 Auth 与 N/N-1 rotation。
4. 设计 Auth-owned、可审计、无需每请求同步调用 Auth 的 access-token revocation blacklist；本轮只完成设计裁决，不投机实现尚未存在的事件传输基础设施。
## Objective

在不把普通请求改为 Auth introspection 的前提下，修复 Auth-issued JWT 的 issuer/audience/algorithm/key lifecycle gap，并完成可实施的吊销传播设计。

## In Scope / Out of Scope

**In Scope**

- `backend-auth` 的 JWT claim、RSA key ring、JWKS publishing 与相关配置/测试。
- `backend-app` 与 `backend-admin` 的 offline verifier、配置与回归测试。
- Auth-owned revocation event/blacklist 的 owner、contract、TTL、失败语义和恢复设计。
- `.auto-flow/` 控制面与 ADR 记录。

**Out of Scope**

- 全系统 token introspection、每请求同步调用 `backend-auth` 或集中式认证网关。
- 本轮生产事件 broker、HTTP revocation consumer 或数据库迁移的实现。
- 按服务拆分当前浏览器 cookie 的 audience、frontend 登录协议或 internal assertion 契约。
- Git commit、push、PR、远端资源修改。

## Root Cause or Capability Gap

- `expectedIssuer` 只是配置字段，resource verifier 没有执行 issuer requirement；签发侧也没有写入 `iss`。
- access token 没有 `aud`，因此 resource server 无法确认 token 属于本平台 resource audience。
- verifier 对非 `RS256` header 默认走 HMAC，且 RS256 key miss 会降级到 HMAC；算法白名单没有落到拒绝路径。
- RSA key 在每个 Auth 进程启动时随机生成，多副本之间不能共享稳定 `kid`/key ring。
- 只有 WebSocket 有现成 read-only Redis blacklist seam，Auth writer 和跨服务 account-wide revoke 传播尚未定义。

## Behavioral Invariants

- Access/refresh token 继续使用 HttpOnly cookies；refresh token 继续走 hash-only DB rotation/revoke。
- `backend-app`/`backend-admin` 继续本地验签；普通请求不依赖同步 Auth RPC。
- 当前 app/admin 共用浏览器 access token 的行为保持不变；platform audience 固定为 `ulticode-api`。
- RS256 key rotation 保留 current 与 previous key，生产 private material 只来自 runtime secret/config。
- WebSocket token 仍只来自 `access_token` cookie；现有 blacklist read seam 保持只读、幂等、fail-closed。

## Acceptance Criteria

- 四个用户 bullet 均有唯一 `TASK-*` 映射，且每项都有源文件、失败路径测试、回滚和最终验证证据。
- TASK-001 的 issuer contract 完成后才能进入 audience、algorithm/key lifecycle 和 revocation 设计任务。
- strict verifier 不接受缺失/错误 issuer/audience、未知算法或 RS256 未知 key；HS256 只能处于显式 overlap。
- 计划不把“设计完成”伪报为 HTTP revocation runtime 已完成。

## Required Evidence

- Auth token claim assertions and wrong/missing claim rejection tests in both resource shells.
- Unsupported algorithm, RS256 unknown `kid`, HS256 overlap, stable multi-replica key ring and N/N-1 JWKS tests.
- Exact config review proving issuer/audience/overlap/key material are explicit and secrets are runtime-only.
- Revocation ADR reviewed against migration guide sections 7.5–7.7 and existing WebSocket port contract.
- Focused Maven result plus final reactor verification and `git diff --check`.


## Current state

`backend-auth` 的 `JwtTokenProvider` 当前只给 access token 写入 `sub`、`username`、`role`、`iat`、`exp`，没有 `iss` 或 `aud`；`backend-app` 与 `backend-admin` 的 `ResourceServerJwtVerifier` 虽然配置了 `expectedIssuer`，但没有执行 issuer/audience 校验。两个 verifier 对任意非 `RS256` 算法都进入 HMAC 路径，且 RS256 `kid` 未命中时回退 HMAC；`ALLOWED_ALGORITHMS` 没有成为实际拒绝规则。`RsaKeyManager` 在每个 Auth 进程启动时随机生成单个 RSA key pair，不能直接支持多副本或稳定的 N/N-1 rotation。现有 WebSocket 已有只读 `TokenBlacklistPort` 和 SHA-256 + Redis adapter，但写入端被明确留给未来 Auth-owned seam；迁移指南要求 Auth 本地事务 outbox 发布 account/authz events，普通请求不做 Auth RPC，Auth 不可用时已有且未撤销的短期 access token 仍可访问普通 App。

## Changes per gap

### Gap 1 — issuer claim and strict issuer validation

**唯一方向**：在 Auth 的 access/refresh token 中写入配置化 `iss`，默认值保持 `ulticode-auth`；两个 resource server 的 parser 使用 `requireIssuer`（或等价的严格 claim requirement），缺失、错误或空 issuer 一律拒绝。不得保留“有 issuer 才校验、没有 issuer 也放行”的长期兼容模式。

**Files / symbols**：

- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtProperties.java`
- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtTokenProvider.java` — `generateAccessToken`, `generateRefreshToken`
- `backend-spring/backend-auth/src/main/resources/application.yml` — `jwt.issuer`
- `backend-spring/backend-app/src/main/java/com/ulticode/app/security/jwt/ResourceServerJwtVerifier.java` — both signing branches
- `backend-spring/backend-admin/src/main/java/com/ulticode/admin/security/jwt/ResourceServerJwtVerifier.java` — both signing branches
- `backend-spring/backend-auth/src/test/java/com/ulticode/auth/security/jwt/JwtTokenProviderTest.java`
- `backend-spring/backend-app/src/test/java/com/ulticode/app/security/jwt/ResourceServerJwtVerifierTest.java`
- `backend-spring/backend-admin/src/test/java/com/ulticode/admin/security/jwt/ResourceServerJwtVerifierTest.java`

**Acceptance**：同一合法签名下，正确 `iss` 通过；缺失或错误 `iss` 在 app/admin 都拒绝；Auth 签发的 access/refresh token 都带稳定 issuer。旧的无 issuer access token 在严格 verifier 发布后不再被接受，发布与回滚必须按三 shell 一起协调，不能以永久宽松模式掩盖 Gap 1。

### Gap 3 — audience claim and validation

**唯一方向**：当前浏览器 access token 仍同时服务 app/admin，因此先使用单一 platform audience `ulticode-api`，不在本轮引入按服务 audience 选择、重新登录流程或多 cookie。Auth access token 写入 `aud=ulticode-api`；app/admin 使用显式 `jwt.expected-audience` 严格校验。未来需要服务间隔离时另行签发带服务 audience 的短期 internal assertion，不把它偷偷混入本轮浏览器 token。

**Files / symbols**：

- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtProperties.java`
- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtTokenProvider.java` — access-token claims
- `backend-spring/backend-auth/src/main/resources/application.yml` — `jwt.audience`
- `backend-spring/backend-app/src/main/resources/application.yml` — `jwt.expected-issuer`, `jwt.expected-audience` deployment keys
- `backend-spring/backend-admin/src/main/resources/application.yml` — same deployment keys
- `backend-spring/backend-app/src/main/java/com/ulticode/app/security/jwt/ResourceServerJwtVerifier.java`
- `backend-spring/backend-admin/src/main/java/com/ulticode/admin/security/jwt/ResourceServerJwtVerifier.java`
- the three JWT test files above

**Acceptance**：正确 `aud=ulticode-api` 在 app/admin 通过；缺失、错误 audience 拒绝；现有 app/admin 共用 cookie 的行为不改变；配置通过 runtime environment 注入，不能把 secret 或环境专属值写入源码。

### Gap 2 — RS256 fallback and key rotation

**唯一方向**：把算法选择改为显式白名单：只接受 `HS256` 或 `RS256`；其他算法直接拒绝。`RS256` 必须找到对应 `kid` 的 RSA public key，未知/缺失 `kid`、JWKS miss 或 JWKS 暂不可用都拒绝，绝不回退到 HMAC。`HS256` 只在明确的 overlap 开关开启时接受，且只因为 header 明确为 `HS256` 才进入 HMAC parser。

同时把生产 RSA key 从“每个进程随机生成”改为 runtime secret/config 提供的稳定 key ring：当前 key（private + `kid`）和 N/N-1 previous public keys 在所有 Auth replicas 一致；`JwksController` 发布 current + previous public keys；rotation 只切 current signing key，旧 key 保留到最长 access-token TTL + JWKS cache/rollout margin。测试 profile 可继续使用 deterministic/generated test keys，但 production 不能无持久化随机生成。

**Files / symbols**：

- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtAuthenticationFilter.java` — verify Auth self-validation remains compatible with the selected signing mode
- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/RsaKeyManager.java`
- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/adapter/in/web/JwksController.java`
- `backend-spring/backend-auth/src/main/resources/application.yml` — RSA current/previous key configuration and overlap flag
- `backend-spring/backend-app/src/main/java/com/ulticode/app/security/jwt/ResourceServerJwtVerifier.java`
- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtTokenProvider.java` — `validateToken` / `parseToken` algorithm and issuer behavior
- `backend-spring/backend-admin/src/main/java/com/ulticode/admin/security/jwt/ResourceServerJwtVerifier.java`
- `backend-spring/backend-app/src/main/java/com/ulticode/app/security/jwt/JwksPublicKeyProvider.java`
- `backend-spring/backend-admin/src/main/java/com/ulticode/admin/security/jwt/JwksPublicKeyProvider.java`
- `backend-spring/backend-app/src/test/java/com/ulticode/app/security/jwt/ResourceServerJwtVerifierTest.java`
- `backend-spring/backend-admin/src/test/java/com/ulticode/admin/security/jwt/ResourceServerJwtVerifierTest.java`
- add/update focused RSA key-ring/JWKS tests beside `RsaKeyManager` / `JwksController`

**Acceptance**：unsupported algorithm、RS256 unknown `kid`、RS256 missing key、invalid signature 全部 fail closed；HS256 overlap 开启时只接受显式 HS256，关闭后拒绝；current 与 previous RSA key 都能在 rotation window 验证；两份独立 Auth instance 使用同一 runtime key ring 产生兼容 JWKS/`kid`；没有 private key、key id 或 current key 配置时 production RS256 startup fail fast。不得用 HMAC fallback 修复 key rotation。

### Revocation blacklist design prerequisite

**唯一方向**：保留 resource server 本地 JWT 验签，吊销作为独立的 state check，不改成每请求同步 introspection/Auth RPC。Auth 作为 writer owner，在本地业务事务中通过 outbox 发布 token/session/account revocation event；resource server 消费事件并维护到期清理的本地 deny state，必要时复用现有 hash-only Redis key convention。现有 WebSocket `TokenBlacklistPort` 保持只读、fail-closed 和 raw-token hash 语义，不向它加入 speculative write methods；Auth 新增 writer-owned seam，不能让 app/admin 直接写 Auth-owned state。

设计必须区分：

- 单 token/logout：access token 需要稳定 `jti`，事件携带 `jti`、`sub`、`exp`、reason、event id；deny entry TTL 不超过 token expiration。
- logout-all/password-change/ban：不能假装单 token blacklist 能覆盖全部无状态 token；使用 Auth-owned account/session revocation epoch 或现有 `authzVersion`/account-state event，App/Admin 失效 `(sub, authzVersion)` 本地 cache。高风险操作 fresh snapshot，普通请求不 RPC。
- 事件重复、乱序、消费者重启和 Redis/outbox 不可用：事件按 idempotent event id/subject version 处理；WebSocket 维持现有 fail-closed；普通 HTTP 的允许窗口、事件延迟 SLO 和恢复/replay 流程写进 ADR 后再实现。

**Acceptance**：ADR 明确 owner、event schema、storage key/TTL、传播延迟预算、fail-open/fail-closed、replay/recovery、单 token 与 account-wide revoke 的差异，并明确当前没有通用事件传输实现时不得直接添加跨模块 Redis writer 或每请求 Auth introspection。该 Task 的交付是设计裁决，不声称生产黑名单已实现。

## Open questions

没有阻塞当前三项 JWT contract hardening 的仓库事实问题。audience 已裁决为 `ulticode-api`，以保持现有单 cookie app/admin 行为；按服务 audience 的 internal assertion 延后到服务间调用契约任务。吊销设计的事件 transport 仍是后续实现的前置基础设施；本轮只记录采用 Auth local outbox + idempotent consumers 的方向，不凭空选择 Kafka、Redis Streams 或另一套 broker。

## Verification

- Focused unit tests：Auth `JwtTokenProviderTest`；App/Admin `ResourceServerJwtVerifierTest`；RSA key-ring/JWKS tests；新增缺失/错误 issuer、缺失/错误 audience、unsupported alg、unknown kid、HS256 overlap、N/N-1 rotation cases。
- Module checks：`./mvnw -pl backend-auth,backend-app,backend-admin -am test -B`。
- Contract/config checks：确认三 shell 的 issuer/audience/overlap 配置一致；确认 production RSA key material 只来自 runtime secret/config；确认旧 access token 的发布/回滚策略记录在 release notes/ADR。
- Final validation：`./mvnw verify -B`，并审阅 `git diff --check`、安全/并发/缓存 TTL/失败路径和未授权依赖。

## Delivery authority

本轮只产生规划控制面与 ADR 级设计，不修改业务源码、不提交、不 push。Implementation 由后续 `implement-development-slice` 按 Task DAG 执行；任何 GitHub/远端写操作仍需用户显式授权。
## Terminal Condition

Historical terminal condition for the completed issuer/audience/RS256/revocation-design objective; the active JWT credential-module plan is the section appended below.

## Deep backend-auth execution plan

### Objective

将 `backend-auth` 按能力深化为六个小 interface：`AuthenticationWorkflow`、
`OAuthLoginWorkflow`、`PasswordResetWorkflow`、`CurrentSessionQuery`、
`AccountAdministrationWorkflow`、`AuthorizationSnapshotQuery`。保持现有
HTTP/RPC/安全/数据库契约不变，让 provider-specific、cookie-specific 和
storage-specific 复杂度集中在实现与 adapter 内部。

### In Scope / Out of Scope

**In Scope**

- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/` 下六个能力 workflow/query seam、实现、HTTP adapter、Dubbo provider adapter 和架构测试。
- OAuth state cookie mutation value object；HTTP adapter 对 cookie mutation 的显式应用。
- Account administration 与 account management 的共享 atomic idempotent command execution policy。
- `.auto-flow/TASKS.yaml`、`.auto-flow/COVERAGE.md`、`.auto-flow/DECISIONS.md`、`.auto-flow/RESUME.md`、`.auto-flow/HANDOFF.yaml`、`.auto-flow/WORKLOG.md`。

**Out of Scope**

- HTTP route、`Result`/`LoginResponse`/`RpcResult` wire shape、现有 cookie 名称/TTL/flags、JWT issuer/audience/algorithm/key contract 的改变。
- `backend-auth-api` 公共 Dubbo interface、Nginx/Gateway、`backend-app`、`backend-admin` 的业务修改。
- 数据库 schema、password-reset token format、生产事件 transport、每请求 Auth introspection。
- 新增 `AuthFacade`、第二套公共 contract、跨 owner DB 访问或 GitHub/远端写操作。

### Root Cause or Capability Gap

- Resolved TASK-005 gap: `AuthenticationWorkflow`/`AuthSessionPort` now return HTTP-neutral session data and cookie mutations; Servlet mutation is owned by the inbound adapter.
- Resolved TASK-006 gap: provider selection and OAuth state lifecycle now live behind `OAuthLoginWorkflow`/`OAuthStatePort`, with cookie mutation application in `OAuthController`.
- Resolved TASK-007 gap: `PasswordResetWorkflow` now owns reset token generation/hash/expiry, email dispatch, password replacement, cleanup, invalid-token mapping, and refresh-token revoke-all; `PasswordResetController` is an HTTP-only adapter.
- `AuthController` 直接访问 account/CSRF/permission implementation，并把 password-bearing `AuthAccountRecord` 转成 HTTP view。
- Account administration、authorization snapshot 的复杂度没有通过独立 capability interface 集中。

### Behavioral Invariants

- Access/refresh token 继续使用现有 HttpOnly cookies；refresh 只接受 refresh credential，继续 hash-only rotation/revoke。
- OAuth state 继续使用 provider-specific HttpOnly cookie、`/auth` path、five-minute TTL、Redis atomic get-and-delete；state validation 成功和失败都清除 cookie。
- Provider identity binding 优先；unverified email 不得自动 link；只有明确 duplicate-key race 可以被转换为已绑定语义。
- 现有 `/auth/**` routes、Result body、LoginResponse、错误码和 rate limits 不变。
- Auth 继续是 Account/Credential/ExternalIdentity/Refresh/RBAC owner；不依赖 app/admin API，不增加普通请求 Auth RPC。
- 现有 Dubbo provider contract、batch semantics、authzVersion 和 permission source metadata 不变。
- Account mutation 的 idempotency claim/replay/mutation/finalize 必须在同一事务内；receipt failure rollback mutation。
- HTTP adapter 不直接访问 Entity、Mapper 或 password-bearing record；workflow tests 穿过对应 interface。

### Acceptance Criteria

- `TASK-005` 至 `TASK-010` 各自实现一个 capability interface，并由真实 caller 使用；没有 provider-specific OAuth branch 或 mega-facade。
- OAuth state cookie mutation 的生产应用位置明确位于 HTTP adapter，且 failure path 不丢失 clear-cookie 行为。
- `/auth/me`、`/auth/permissions`、登录/注册/refresh/logout、OAuth、password reset 的外部行为回归通过。
- Account administration/management receipt semantics 统一，并由事务测试证明 rollback/replay/fingerprint conflict。
- `TASK-011` 的 ArchUnit seam guards 通过；所有六个 interface 都有 interface-level behavior tests。
- Module compile/test/IT/verify、diff check 和最终变更审查均有命令证据。

### Required Evidence

- 每个 workflow/query 的 interface test 和关键失败路径测试。
- OAuth state cookie 属性、成功/失败 cleanup、duplicate-key 与非 duplicate persistence failure 的测试。
- AuthController HTTP contract tests、security route test、现有 rate-limit regression。
- Account administration/management provider tests 与两套 transactional IT。
- Authorization snapshot single/batch projection tests，证明 flat/structured output 来自同一 primitive source。
- `AuthSingleHopArchTest` 扩展结果、`./mvnw -pl backend-auth -am verify -B`、`git diff --check`。
- 变更文件审查确认未修改 `backend-auth-api`、Gateway、App/Admin 或数据库 migration。

### Files / Symbols / Call Chains

- Authentication: `AuthController.login/register/refresh/logout` → `AuthServiceImpl` → `AuthAccountPort`/`RefreshTokenService`/session issuer。
- OAuth: `OAuthController` → `OAuthLoginWorkflow` → `OAuthClient`/`OAuthStatePort`/identity mapper/account/session。
- Password reset: `PasswordResetController` → `PasswordResetWorkflow` → account token storage/email/refresh revoke。
- Current session: `AuthController.getCurrentUser/getPermissions` → `CurrentSessionQuery` → `AuthAccountQueryPort`/CSRF/permission read.
- Administration: Dubbo `AccountAdministrationProvider` → `AccountAdministrationWorkflow` + shared receipt executor → account/permission adapters.
- Snapshot: Dubbo `AuthorizationSnapshotQueryProvider` → `AuthorizationSnapshotQuery` → role/direct permission reads → `AuthorizationSnapshotDTO`.
- Final guards: `AuthSingleHopArchTest`, new web-to-workflow dependency rules, no Servlet in core interface signatures.

### Implementation Steps

1. `TASK-005`: create HTTP-neutral authentication/session seam and preserve cookie mutations.
2. `TASK-006`: make OAuth provider selection and state-cookie mutations provider-neutral and adapter-applied.
3. `TASK-007`: put password reset behavior behind its workflow; do not alter token schema/format.
4. `TASK-008`: put current-user/permission bootstrap behind safe `CurrentSessionQuery`; require authentication for `/auth/permissions`.
5. `TASK-009`: extract account administration workflow and unify command receipt execution atomically across both write providers.
6. `TASK-010`: extract authorization snapshot query and preserve single-source projection derivation.
7. `TASK-011`: remove bypasses, add ArchUnit guards, update stale placeholder descriptions, run final validation.

### Compatibility / Rollback

- Each task is an internal refactor with no external contract or schema migration.
- Roll back each task as one seam unit; rollback `TASK-009` must include both account write providers.
- Never leave controller, workflow, state port, or session issuer on mixed old/new contracts.
- Do not use destructive Git rollback; use a reviewed revert/new commit if implementation has already been committed.

### Focused Checks

- Per task: focused interface and HTTP contract tests plus `./mvnw -pl backend-auth -am test -B`.
- `TASK-009`: `./mvnw -pl backend-auth -am -Dtest='*IT' test -B`.
- Final: `./mvnw -pl backend-auth -am verify -B`, `git diff --check`, route/RPC/cookie/security/concurrency review.

### Final Validation Tier

Tier C: focused tests → backend-auth module tests → backend-auth ITs → module `verify`/JaCoCo → architecture and contract review → clean diff. Full reactor validation is required only if implementation changes `backend-auth-api` or another module; this plan forbids that by default.

### Expected Review Areas

Seam depth and caller knowledge; cookie/security behavior; refresh credential separation; OAuth race/error handling; transaction boundaries and idempotency; batch ordering/omission; password-bearing data exposure; ArchUnit coverage; contract/config drift; unrelated changes.

### State Files to Update

`.auto-flow/TASKS.yaml`, `.auto-flow/COVERAGE.md`, `.auto-flow/DECISIONS.md`,
`.auto-flow/PLAN.md`, `.auto-flow/RESUME.md`, `.auto-flow/HANDOFF.yaml`,
`.auto-flow/WORKLOG.md`.

### Delivery Authority

本 objective is in implementation execution: `TASK-005` through `TASK-010` pass Acceptance/Review/Validation; the next dependency-satisfied task is `TASK-011`. Local business changes remain uncommitted and commit-ready; all GitHub/remote writes still require explicit user authorization.

### Terminal Condition

Historical terminal condition for the completed six-capability backend-auth objective; the active objective is the JWT credential-module plan appended below.


## JWT credential module execution plan

### Objective

将 `backend-auth` 的 JWT 签发、算法路由、key selection、claims policy 与 Auth filter consumption 收回一个 deep credential module，消除 `jwt.rsa.enabled=true` 时的 RS256 signing / HS256-only self-verification 漂移，并保持现有 HTTP/RPC/安全/数据库契约不变。

### In Scope / Out of Scope

**In Scope**

- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtTokenProvider.java`
- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/RsaKeyManager.java`
- `backend-spring/backend-auth/src/main/java/com/ulticode/auth/security/jwt/JwtAuthenticationFilter.java`
- JWT-focused provider, key-manager, and filter tests.
- Cross-shell JWT regression/documentation guards in existing App/Admin verifier tests/Javadocs where needed to prove the unchanged offline contract.
- `.auto-flow/TASKS.yaml`, `.auto-flow/COVERAGE.md`, `.auto-flow/DECISIONS.md`, `.auto-flow/PLAN.md`, `.auto-flow/RESUME.md`, `.auto-flow/HANDOFF.yaml`, `.auto-flow/WORKLOG.md`.

**Out of Scope**

- `backend-auth-api` Dubbo contracts, new RPC methods, HTTP routes, `Result`/`RpcResult` shapes, cookie names/flags/TTL, or frontend login behavior.
- Database/schema changes, refresh-token hash/rotation changes, token-format migration, or revocation transport.
- Per-request Auth introspection, Gateway changes, broad RBAC/account-storage refactors, and the report's deferred session/RBAC/RPC/storage candidates.
- Destructive Git actions, local commits without authorization, GitHub/remote writes, or changes to protected `scripts/dev/up.sh`.

### Root Cause or Capability Gap

- `JwtTokenProvider.generateAccessToken` supports RS256, but `parseToken`, `validateToken`, and `isTokenExpired` always use the HS256 secret.
- `RsaKeyManager` loads current/previous RSA keys and publishes JWKS, but does not expose a safe Auth-local `kid` verification lookup.
- The default JWT test fixture leaves RSA disabled and therefore cannot detect the opt-in self-401.
- `JwtAuthenticationFilter` calls validation plus three getter methods, causing repeated parsing and making caller behavior depend on provider internals.

### Behavioral Invariants

- HS256 remains the current default compatibility path; RS256 is opt-in and uses the stable current/previous key ring. No new permissive fallback is introduced.
- Only explicit HS256 and RS256 are accepted. RS256 missing/unknown `kid`, missing public key, JWKS/key selection failure, and bad signature fail closed; no algorithm fallback occurs.
- Issuer remains `ulticode-auth` by default and is required on access and refresh tokens. Access tokens require audience `ulticode-api`; refresh tokens remain `type=refresh` without audience.
- Refresh tokens are accepted only through the existing refresh-token path; an access filter never authenticates a refresh token, and an access token is never accepted as refresh credential.
- Cookie-first token extraction and Authorization Bearer fallback remain unchanged. The SecurityContext principal/userId/username/role mapping remains compatible.
- App/Admin continue offline JWT verification with no ordinary-request Auth RPC; their existing external verifier contract remains unchanged.
- RSA private material comes only from runtime configuration and is never committed, printed, or included in a test fixture as a repository secret.
- Existing TASK-005..TASK-011 changes and `scripts/dev/up.sh` remain protected, and no migration/schema or external contract file is mixed into this objective.

### Acceptance Criteria

- `TASK-012` proves Auth can self-verify current-key and previous-key RS256 access tokens, while HS256 default/overlap and refresh shape remain compatible.
- `TASK-012` proves explicit issuer/audience and algorithm/kid fail-closed behavior through focused tests.
- `TASK-013` proves the Auth filter consumes one verified claims result and preserves cookie/header, SecurityContext, and invalid-token behavior.
- `TASK-014` proves App/Admin continue accepting valid Auth-compatible HS256/RS256 access tokens and rejecting unsupported algorithms, bad/missing claims, unknown keys, and refresh-as-access.
- `TASK-015` records focused/module/reactor validation, changed-file review, protected-file review, and the known pre-existing reactor limitation honestly.

### Required Evidence

- `JwtTokenProviderTest`: HS256 default, RS256 current/previous round-trip, issuer/audience, invalid signature, unsupported algorithm, missing/unknown `kid`, and refresh/access separation.
- `RsaKeyManagerTest`: deterministic current/previous `kid`, public-key lookup, JWKS current/previous publication, disabled mode, and fail-fast configuration.
- `JwtAuthenticationFilterTest`: cookie/header extraction, one-pass claims consumption, valid authentication, malformed/expired/missing identity, and refresh rejection.
- Existing App/Admin `ResourceServerJwtVerifierTest` suites plus any cross-shell regression added by TASK-014.
- Runtime configuration review proving `jwt.rsa.*`, issuer, audience, and secret material are explicit and secret-free in source.
- Direct backend-auth `test`/`verify`, focused App/Admin tests, required reactor result, `git diff --check`, and manual security/compatibility review.

### Active Task

`TASK-012` and `TASK-013` are complete with focused/module validation and Review evidence in `TASKS.yaml`, `COVERAGE.md`, and `WORKLOG.md`. `TASK-014 — Codify cross-shell JWT credential guards` is now `ready`; implementation must preserve the completed Auth credential/filter core and touch only App/Admin verifier regression/documentation guards.

### Dependencies

`TASK-012 → TASK-013 → TASK-014 → TASK-015`. Existing `TASK-001..TASK-011` are historical prerequisites and remain unchanged. The current backend-common `PartialUpdate` ArchUnit failure is a known reactor validation limitation; it does not block focused implementation but must be reproduced and classified at the final gate.

### Files / Symbols / Call Chains

- Signing: `DefaultAuthSessionAdapter` → `JwtTokenProvider.generateAccessToken/generateRefreshToken` → `RsaKeyManager.getPrivateKey/getKeyId` or HS256 key.
- Auth verification: `JwtAuthenticationFilter.doFilterInternal` → one `JwtTokenProvider.parseToken` result → claims-to-SecurityContext mapping.
- Refresh verification: `RefreshTokenService.validateAndRotate` → `JwtTokenProvider.getUserIdFromRefreshToken` → refresh type/issuer/signature policy.
- Key material: `RsaKeyManager.init/loadKeyEntry/toJwkSet` → current/previous public-key lookup by `kid`.
- Resource contract: Auth-issued token/JWKS → App/Admin `ResourceServerJwtVerifier.verifyAndParse` → local HS256/RS256 parser with issuer/audience requirements.

### Implementation Steps

1. `TASK-012`: add algorithm-aware Auth parsing and `kid` resolution without changing external method contracts; add RS256/HS256/claims failure-path tests.
2. `TASK-013`: change the filter to derive identity from one verified claims result; add request-level behavior tests and preserve extraction/security behavior.
3. `TASK-014`: add cross-shell regression/documentation guards; correct only stale failover wording, not verifier behavior or public contracts.
4. `TASK-015`: run focused and module checks, run the required reactor gate, classify the unchanged backend-common failure if reproduced, perform manual review, and close only with complete evidence.

### Compatibility / Rollback

- No public contract or schema migration is required. Rollback is task-scoped: revert TASK-013 filter changes separately from the independently validated TASK-012 credential core; revert cross-shell docs/tests separately.
- If TASK-012 fails, do not enable `jwt.rsa.enabled`; retain the current default HS256 runtime while the task is reworked. Never add a permissive fallback to make tests pass.
- Preserve current/previous key overlap until access-token TTL plus JWKS cache/rollout margin. Do not remove a previous key as part of this task.
- Do not use `git reset --hard`, checkout-overwrite, clean, or any destructive rollback; protect the existing dirty worktree.

### Focused Checks

- `./mvnw -pl backend-auth -Dtest='JwtTokenProviderTest,RsaKeyManagerTest' test -B`
- `./mvnw -pl backend-auth -Dtest='JwtAuthenticationFilterTest,AuthSecurityConfigContractTest' test -B`
- `./mvnw -pl backend-auth -am compile -B`
- `./mvnw -pl backend-app -am -Dtest=ResourceServerJwtVerifierTest test -B`
- `./mvnw -pl backend-admin -am -Dtest=ResourceServerJwtVerifierTest test -B`
- `git diff --check`

### Final Validation Tier

Tier C for the affected security surface: focused JWT/filter tests → backend-auth module `test`/`verify` → App/Admin verifier tests → required three-owner reactor test → configuration and changed-file review. A reactor failure is not a pass; it must be captured with the exact unchanged backend-common violation and kept separate from the JWT evidence.

### Expected Review Areas

Algorithm routing based on untrusted headers; RS256 `kid` lookup and fail-closed behavior; issuer/audience and refresh/access separation; key rotation overlap and secret handling; one-pass claims consumption; SecurityContext compatibility; App/Admin offline contract drift; stale Javadocs; test vacuity; protected dirty changes; unrelated files; and whether any fallback silently weakens the guard.

### State Files to Update

`.auto-flow/TASKS.yaml`, `.auto-flow/COVERAGE.md`, `.auto-flow/DECISIONS.md`, `.auto-flow/PLAN.md`, `.auto-flow/RESUME.md`, `.auto-flow/HANDOFF.yaml`, `.auto-flow/WORKLOG.md`.

### Delivery Authority

Local planning and implementation only. No commit, push, PR, GitHub resource write, migration, external contract change, or protected-file attribution is authorized by this plan.

### Terminal Condition

The objective remains open until `TASK-015` has complete Acceptance, Review, Validation, and Completion Audit evidence. The plan is complete now because every selected report requirement maps to a task and `TASK-012` is the only dependency-satisfied Ready task.

## 2026-08-07 — Root-level owner-service layout execution plan

### Objective

将 `backend-auth`、`backend-admin`、`backend-app` 三个可运行后端 owner 从
`backend-spring/` 移到仓库根目录，使它们与 `console/`、`management/` 同级；
保留 `backend-spring/` 作为 Maven parent/reactor 和共享后端模块目录，不改变
HTTP/RPC、cookie/JWT、数据库、前端 API、运行时 owner 或安全边界。

### In Scope / Out of Scope

**In Scope**

- `backend-spring/backend-auth/` → `backend-auth/`
- `backend-spring/backend-admin/` → `backend-admin/`
- `backend-spring/backend-app/` → `backend-app/`
- `backend-spring/pom.xml` 的 sibling module paths 与三个 owner POM 的 parent `relativePath`
- `backend-spring/Dockerfile` 的 build-context/COPY/artifact 路径
- `ecosystem.config.cjs`、`scripts/dev/up.sh`、Dubbo/Nacos smoke、service-shell helper 的 owner POM/artifact 路径
- CI path filters、Surefire artifact paths、Docker matrix inputs、root `.gitignore` allowlist
- README、AGENTS、CONTEXT、migration guide active links、Agent/editor globs 和 active path comments
- `.auto-flow/` execution control plane；不暂存、不提交

**Out of Scope**

- Java package/class、Controller→Service→Mapper→Entity 流程、业务逻辑、测试行为本身
- `backend-common`、`backend-api`、domain modules、`backend-web-security` 的物理上移
- HTTP route、Result/RpcResult/DTO、Dubbo service、cookie/JWT、数据库 schema/migration、Nacos/Dubbo service name、端口、health path、Compose exposure 或 frontend contract
- 新 root Maven parent、第二套兼容目录、symlink、跨服务 RPC、运行时 cwd 重设计
- 生成/历史 `pitstop.yaml`、`scripts/start.sh`、`scripts/start.bat`、`.dubbo-smoke` logs 的兼容承诺
- Git commit、push、PR、GitHub/第三方资源写入

### Root Cause or Capability Gap

`backend-spring/` 同时承担“后端构建控制目录”和“三个可运行服务的物理容器”两种职责，
而 `console/` 与 `management/` 已经是仓库根级应用。当前 parent POM 使用子目录模块名，
owner POM 使用默认 parent 相对路径，Docker 只复制 `backend-spring/`，PM2/bootstrap/
CI/report/tooling 也假定 owner 在该目录内；因此只做目录移动会直接造成 Maven parent、
reactor dependency、Docker image、PM2、测试报告和文档路径断裂。

### Behavioral Invariants

- 三个 owner 的服务身份、HTTP port/health route、Nacos/Dubbo registry/provider、
  image/artifact 名称保持不变。
- `backend-spring` reactor cwd 保持为默认运行契约；`SANDBOX_SECCOMP_PROFILE`、
  uploads、backup、integration migration lookup 的相对路径语义不改变。
- Controller/service/mapper/entity、Java package、Result/RpcResult、HTTP/RPC DTO、
  cookie/JWT/session、数据库表和 migration 不改变。
- backend-admin 对 backend-app 的既有 reactor dependency 与 owner boundaries 保持。
- 受保护的 dirty JWT/workflow/session/OAuth/backend-app/backend-admin 和 `scripts/dev/up.sh`
  改动只随路径移动保留，不被 reset、checkout、clean、重写或混入无关行为。
- 不新增 root-level duplicate、symlink 或永久兼容层。

### Acceptance Criteria

1. Root tree directly contains `console/`, `management/`, `backend-auth/`, `backend-admin/`,
   and `backend-app/`; no runnable owner remains below `backend-spring/`.
2. `backend-spring/pom.xml` can resolve all three sibling owner modules and their dependencies,
   including admin→app, without an install-first workaround.
3. Supported local launchers, Docker build, CI path filters/report uploads, and ignore rules
   all resolve the new paths while preserving cwd-relative runtime semantics.
4. Active docs/editor/agent rules describe the new tree; stale generated/history artifacts are
   explicitly non-authoritative and are not advertised as supported launchers.
5. Focused/module/quick/Docker/config/path checks and final review show no behavioral or contract
   change. Any known unchanged baseline failure is recorded rather than weakened around.
6. Rollback is an inverse path move plus inverse path-config edits only; no destructive Git
   operation or remote delivery occurs.

### Required Evidence

- Pre-move `git status --short`, protected-file diff receipt, root/module path inventory, and
  codebase-memory refresh/status for the current worktree.
- `TASK-016` path-preserving move diff, parent/module effective model, and `-am compile`.
- `TASK-017` PM2/bootstrap/start-script path resolution and shell syntax evidence.
- `TASK-018` CI/Docker/Compose/ignore review and three-owner Docker matrix result.
- `TASK-019` active path-reference audit with historical/generated exclusions documented.
- `TASK-020` quick/module/focused validation, changed-file review, baseline-failure classification,
  rollback receipt, and `git diff --check`.

### Files / Symbols / Call Chains

- Maven boundary: `backend-spring/pom.xml` → `backend-auth/pom.xml`,
  `backend-app/pom.xml`, `backend-admin/pom.xml`; owner entries
  `BackendAuthApplication`, `BackendAppApplication`, `BackendAdminApplication`.
- Local runtime: `scripts/dev/up.sh` → `ecosystem.config.cjs` → `cwd=backend-spring`
  → owner POM → owner Spring Boot application → existing health route.
- Direct launcher: `backend-spring/start.cjs` → `backend-spring/mvnw` →
  `-pl <owner> -am` → root-level owner module.
- Image path: `backend-spring/Dockerfile` → copies shared reactor plus root owner dirs →
  `mvn -pl <owner> -am package` → owner target artifact → existing non-root runtime image.
- Smoke path: `scripts/dev/dubbo-nacos-smoke.sh` and
  `backend-spring/scripts/dev/start-service-shells.sh` → owner target jars/parent reactor
  → unchanged Nacos/Dubbo/health probes.
- Delivery path: `.github/workflows/ci.yml` filters → backend build/test/report jobs;
  Docker matrix → `backend-spring/Dockerfile`; Dependabot remains on `/backend-spring`.

### Implementation Steps

1. `TASK-016`: `git mv` the three owner directories, update parent module sibling paths,
   add explicit owner parent `relativePath`, and prove `-am` model/compile resolution.
2. `TASK-017`: update PM2/bootstrap and local smoke/artifact paths without changing the
   backend-spring cwd or protected `up.sh` security bootstrap lines.
3. `TASK-018`: update Docker COPY, CI filters/report paths, matrix checks, and root ignore
   allowlists; keep image/runtime/network contracts unchanged.
4. `TASK-019`: update active docs, rules, editor globs, comments, and command examples;
   classify generated/history artifacts as non-authoritative.
5. `TASK-020`: run the final validation tier, review the diff and protected-work attribution,
   classify unchanged baselines, and close only with complete evidence.

### Compatibility / Rollback

The migration is path-only. POM parent resolution and runtime cwd are the compatibility
boundaries; no schema/token/API compatibility layer is required. Rollback reverses the three
directory moves, parent module paths, owner `relativePath`, Docker/runtime/CI/docs path edits
in dependency order. Never reset or discard the existing dirty worktree. If a later delivery
is authorized, use a new inverse path-change commit rather than history rewrite.

### Focused Checks

- `cd backend-spring && ./mvnw -pl ../backend-auth,../backend-app,../backend-admin -am -DskipTests compile -B`
- `node -e "const c=require('./ecosystem.config.cjs'); console.log(c.apps.filter(a=>a.name.startsWith('ulticode-')).map(a=>({name:a.name,cwd:a.cwd,args:a.args})))"`
- `./scripts/dev/up.sh --help`
- `bash -n scripts/dev/up.sh scripts/dev/dubbo-nacos-smoke.sh backend-spring/scripts/dev/start-service-shells.sh`
- `docker compose --env-file .env -f docker-compose.ci.yml config`
- Three-owner Docker build matrix with `SERVICE_MODULE=backend-auth|backend-admin|backend-app`
- `git diff --check` and active path-reference audit

### Final Validation Tier

Tier C/D for a repository-structure migration: path inventory and POM model →
focused compile/tests and launcher syntax → Docker/Compose/CI contract checks →
`./scripts/dev/test.sh quick` → required multi-owner reactor test with any unchanged
backend-common baseline classified → full changed-file/security/compatibility review.
No check is reported as passed without command evidence.

### Expected Review Areas

Maven module path/parent resolution; backend-admin→backend-app reactor dependency; Docker
build context and artifact selection; PM2/bootstrap cwd and relative sandbox/config paths;
uploads/backups and integration migration lookup; CI filters/report paths; root allowlist
security; stale active docs/editor globs; protected dirty-file attribution; accidental package,
route, cookie/JWT, schema, external-contract, runtime-owner, or frontend behavior changes.

### State Files to Update

`.auto-flow/TASKS.yaml`, `.auto-flow/COVERAGE.md`, `.auto-flow/DECISIONS.md`,
`.auto-flow/PLAN.md`, `.auto-flow/RESUME.md`, `.auto-flow/HANDOFF.yaml`,
`.auto-flow/WORKLOG.md`.

### Delivery Authority

Local planning and (after handoff) local implementation only. No commit, push, PR, merge,
GitHub resource write, migration, external contract change, or protected-file discard is
authorized. The existing dirty worktree must remain preserved and uncommitted.

### Terminal Condition

The layout objective is complete only when `TASK-016` through `TASK-020` are done with
path-preserving move, runtime/CI/Docker/docs evidence, final review, rollback boundary, and
no unresolved mapped requirement. Until then `TASK-016` is the sole Ready task.

## 2026-08-09 — App microservice-deepening Execution Packet

### Objective

把 `services/app` 从“一个 boot shell 里的并列业务包”深化为清晰的 owner、module、
seam 和 runtime role：先修复 Verdict Event Spine，再让 Judge Worker 独立伸缩，
把 Moderation workflow 归 Admin owner，最后把 Notification delivery 变成可回收
worker。完成后仍保持三 owner services，不立即创建第四个 logical service。

### In Scope / Out of Scope

**In Scope**

- `SubmissionResultDispatcher` → `IntegrationEventPublisher` 的唯一 durable result path。
- verdict transaction 与 Contest/Notification/Achievement/WebSocket post-verdict fan-out
  的分离，以及 consumer/inbox 幂等、generation、retry 语义。
- App `api`/`worker` runtime profiles、JudgeQueue dependency inversion、Streams/outbox/
  generation-fence cutover 和 legacy judge queue retirement。
- Admin-owned Moderation Case/Decision、queue/report/appeal workflow；App content
  lifecycle commands；Auth account-ban commands；Flyway backfill/reconciliation。
- App-owned Notification intent outbox、reclaimable delivery ledger、SMTP/WS failure
  isolation 和 notification worker profile。
- 相关 ArchUnit guards、contract tests、integration tests、owner matrix、CONTEXT/迁移指南
  与 `.auto-flow/` evidence。

**Out of Scope**

- 立即创建 `backend-judge`、`backend-notification` 或独立 `backend-moderation`。
- 拆分 Contest/Submission 的 D-04 同事务 intake、Sandbox/CodeExecution、Search 四源
  本地聚合或 WebSocket STOMP transport。
- 修改现有用户可见 HTTP/Dubbo routes、Result/RpcResult envelope、cookie/JWT/security
  contract，除非 Moderation owner 迁移必须保留兼容映射。
- 修改已应用 migration、使用 2PC、引入无 owner 的共享写入、永久双写兼容层或 speculative
  broker。
- Commit、push、PR、merge、deployment、远程资源写入或丢弃保护中的 dirty worktree。

### Root Cause or Capability Gap

- `SubmissionResultDispatcher` 的 result outbox 已常驻，却在 publish stub 后标记
  `DELIVERED`；`IntegrationEventPublisher` 已存在但未成为该链的唯一 sink。
- `DefaultSubmissionWritePort` verdict path 有 17 个 collaborator，Notification/
  Achievement/SMTP/WebSocket side effects 泄漏进 verdict transaction。
- Judge Worker 的 Streams/outbox/fence flags 默认关闭；queue 直接依赖 Submission
  entity/mapper/codec/fence internals，runtime role 没有 deployment seam。
- Moderation decision workflow 仍在 App，而目标 owner 是 Admin；App domain module 的
  one-method DELETE adapter 是 shallow module。
- Notification ledger/channel seam 已存在，但 SMTP 是同步调用方事务，stale CLAIMED/
  FAILED row 不可完整 reclaim，尚未形成独立 delivery role。

### Behavioral Invariants

- D-04/D-05：Submission 与 ContestSubmission 的必要 intake 记录保持同一 App owner
  transaction；Contest eligibility 不变成同步跨服务 RPC。
- Submission verdict CAS、generation fence、virtual-replay achievement suppression、
  terminal status、runtime/memory facts 和 user-visible notification payload 不变。
- durable event publication is at-least-once and consumers are logically idempotent;
  no row is marked `DELIVERED` before durable publication.
- App/Admin/Auth each own their storage; no direct cross-owner Entity/Mapper access and no
  2PC. Command ids, actor delegation, trace metadata, timeout/retry/error mapping remain explicit.
- SMTP remains failure-isolated and WebSocket remains best-effort; neither runs inside a
  business verdict transaction after P1.
- Access/refresh token, cookie, JWT, security, public route, and shared `Result`/`RpcResult`
  contracts remain unchanged.
- Search, WebSocket transport, Sandbox/CodeExecution, and local Contest scoring retain
  locality; no per-request or four-source remote fan-out is introduced.
- Flyway changes are append-only, backward-compatible, and reversible through watermark/
  reconciliation or a forward migration.

### Acceptance Criteria

- `TASK-027`–`TASK-030`: one working durable result path, no false delivery, no verdict
  transaction fan-out, idempotent Contest/Notification/Achievement consumption, P1 gate pass.
- `TASK-031`–`TASK-035`: explicit App api/worker roles, queue seam without Submission internals,
  streams/outbox/fence default path, legacy judge retirement, P2 gate pass.
- `TASK-036`–`TASK-039`: Admin moderation owner, App content owner, Auth ban owner, data
  reconciliation and command failure semantics, P3 gate pass.
- `TASK-040`–`TASK-043`: durable NotificationIntent, reclaimable ledger, worker role, preserved
  channel semantics, P4 gate pass.
- `TASK-044`: owner/runtime matrix, docs, architecture guards, review, focused/full validation,
  rollback evidence, and no unresolved mapped requirement.

### Required Evidence

- Focused unit tests at each deep module seam; no test that merely proves boot-shell wiring.
- MySQL/Redis integration evidence for result outbox, inbox dedup, judge lease/fence/Streams,
  moderation backfill/commands, notification ledger reclaim, and role startup.
- ArchUnit/single-hop/import audits proving no reverse package dependency or cross-owner mapper access.
- Migration checksum/config/compose evidence; no applied migration edits and no secrets in source/logs.
- Review evidence for transaction, concurrency, failure isolation, security, public compatibility,
  performance, rollback, and documentation drift.
- Final commands from `TASK-044` plus `git status --short` and `git diff --check`.

### Active Task

`TASK-029` — Preserve contest adjudication on durable result consumption.

### Dependencies

The terminal layout work `TASK-021`–`TASK-026` and Verdict Event Spine implementation tasks
`TASK-027`–`TASK-028` are complete. `TASK-029` is the only Ready task; `TASK-030`–`TASK-044`
remain pending behind their listed phase/task gates.

### Files / Symbols / Call Chains

- Result path: `SubmissionResultOutboxListener` → `SubmissionResultDispatcher` →
  `IntegrationEventPublisher` → `IntegrationOutboxDispatcher`.
- Verdict path: `DefaultSubmissionWritePort.updateSubmissionResult*` → Notification/
  Achievement/Contest post-verdict consumers.
- Judge path: `JudgeWorkerProcessor` → `DefaultJudgeAttemptExecutor` →
  `DefaultJudgeExecutionPipeline` → `SandboxExecutor` → result outbox.
- Moderation path: `ModerationController`/`ModerationServiceImpl` → Admin cutover →
  App content owner/Auth ban command.
- Notification path: domain intent producer → outbox → `NotificationDispatcher` →
  `NotificationChannel`/ledger → in_app/email/websocket.

### Implementation Steps

1. Complete P1 in dependency order: connect result publication, remove verdict transaction
   fan-out, migrate Contest adjudication, and close `TASK-030`.
2. Complete P2: add api/worker profiles, remove queue imports of Submission internals, enable
   Streams/outbox/fence, soak and retire legacy judge dispatch, close `TASK-035`.
3. Complete P3: migrate Admin moderation storage/workflow, route App content/Auth ban commands,
   delete the shallow App moderation path, close `TASK-039`.
4. Complete P4: persist intents, add ledger reclaim and SMTP worker semantics, separate
   notification runtime roles, close `TASK-043`.
5. Run `TASK-044` review/validation and synchronize all active documentation and control-plane
   evidence. A future fourth service requires a new plan/ADR.

### Compatibility / Rollback

P1 rolls back by code/version while retaining replayable outbox rows. P2 keeps an explicit
legacy judge switch only during the soak window and removes it only after drain/replay evidence.
P3 uses append-only migration, watermark, backfill and reconciliation; after authoritative
writer cutover rollback is forward-only. P4 preserves durable intent/ledger state and can return
to the combined App profile without restoring synchronous SMTP. No phase uses destructive Git
operations, 2PC, permanent dual writers, or untracked schema edits.

### Focused Checks

- `cd services && ./mvnw -pl app/app-web -Dtest='*SubmissionResult*Test' test -B`
- `cd services && ./mvnw -pl app/app-web -Dtest='*Judge*Test,*Queue*Test,*Moderation*Test,*Notification*Test' test -B`
- Targeted `*IT` suites for MySQL/Redis outbox, lease/fence, moderation migration, and ledger reclaim.
- Profile context/startup checks for App api/worker and notification worker roles.
- ArchUnit/single-hop/import audits; `docker compose ... config`; `git diff --check`.

### Final Validation Tier

Tier D: focused module tests and architecture guards → MySQL/Redis integration and profile
startup → `cd services && ./mvnw verify -B` → `./scripts/dev/test.sh quick` → dev/prod
Compose config → full changed-path/security/compatibility/review audit. No pre-existing failure
is reported as a pass; each limitation is classified with command output.

### Expected Review Areas

Outbox state transitions and crash windows; event identity/generation ordering; transaction
boundaries and D-04; queue lease/fence concurrency; Docker sandbox security; owner/table
write direction; Auth ban command security; migration/backfill/reconciliation; SMTP secrets
and failure isolation; WebSocket best-effort semantics; public contract compatibility; runtime
role selection; dead legacy path removal; test realism; docs/ADR drift; protected dirty worktree.

### State Files to Update

`.auto-flow/TASKS.yaml`, `.auto-flow/COVERAGE.md`, `.auto-flow/DECISIONS.md`,
`.auto-flow/PLAN.md`, `.auto-flow/RESUME.md`, `.auto-flow/HANDOFF.yaml`, and
`.auto-flow/WORKLOG.md`. These are bookkeeping only and must not be staged as delivery.

### Delivery Authority

Local implementation/review/validation only. No commit, push, PR, merge, deployment, migration
against a shared environment, external resource change, or destructive Git operation is authorized.
Preserve all existing dirty work and keep delivery uncommitted.

### Terminal Condition

The plan is complete only when `TASK-027`–`TASK-044` are `done`, all four phase gates and the
terminal gate have acceptance/validation/review evidence, every report item is mapped, the
owner/runtime matrix is current, and no unresolved blocker or unsafe compatibility path remains.

## Active Execution Packet — Contest Production Readiness (2026-08-10)

### Objective

将 `services/` 竞赛模块从迁移中的开发基线修复为 owner-owned、可恢复、可审计、可并发验证的生产候选实现。P0/P1 问题关闭前，`app.features.contest-dubbo-cutover` 保持 `false`，不把当前局部 Mockito 测试通过误报为上线可用。

### In Scope

- `services/app/app-web` 的竞赛提交、判题记分、生命周期、排名、rating、公开 projection 和 scheduler。
- `services/app/modules/contest` 的 owner command/domain seam、实体/DTO/adapter 与删除入口。
- `services/api/app-api` 的竞赛 command/query contract；`services/admin` 的 cutover adapter、read path、依赖和授权/审计语义。
- 新增后续 Flyway migration：真实报名唯一性、记分 receipt/outbox、FINISHING 状态、owner 内部关系约束和必要的 rating/排名持久化字段。
- Testcontainers/MySQL、Dubbo provider/consumer contract、ArchUnit owner seam guard 与失败注入测试。

### Out of Scope

- 不改 Auth/JWT、Gateway、前端竞赛 UI 或非竞赛领域。
- 不新增每请求 Auth introspection、跨 owner 数据库写入或跨服务分布式事务。
- 不凭空选择 Kafka/Redis Streams 等生产 broker；先复用现有事件/outbox能力，若能力不存在则在 app owner 内实现最小 durable inbox/outbox。
- 不编辑已应用 migration；不提交、push、发布或在未授权时打开生产 cutover。

### Root Cause / Capability Gap

1. 判题事件在 `AFTER_COMMIT` 后直接改聚合，缺少 `(submissionId,generation)` 的 durable idempotency/重试语义，并把基础设施失败当作 Wrong Answer。
2. 生命周期把 `FINISHED` 与 participant/ranking/rating side effects 绑定在一次不可恢复流程中；失败后没有 durable finalizer。
3. Dubbo owner contract 是浅 adapter：command 字段、expected version、actor、creator、audit、状态机和错误翻译未兑现；Admin 本地 `@Transactional` 不能包住 App 远程事务。
4. MySQL NULL 唯一键、自动猜测 contest、虚拟赛父状态和实际提交时钟之间没有单一 admission 规则。
5. public detail、live/final ranking、solved count、tie-breaker、全局 rating 的契约分裂；Admin 仍直接依赖 App-private contest。
6. 测试主要跨 Mockito seam，未覆盖真实 SQL、并发、重试、owner wiring 和上下文启动失败。

### Behavioral Invariants

- App 是 contest 表、participant、submission mapping、score、ranking、rating 计算的唯一 owner；Admin 只调用公开 command/query contract。
- Dubbo 是远程 command seam，不使用分布式事务；provider 的本地事务、稳定 command receipt 和返回结果是唯一写入真相。
- 状态迁移必须是条件 claim；`FINISHING` 完成可重试副作用后才允许 `FINISHED`。
- 同一 `submissionId + generation` 只产生一次竞赛计分；旧事件不得覆盖新 generation；基础设施失败不计 penalty/attempt/rank。
- contest 归属显式传递；普通 submission 不通过“当前 RUNNING contest + first match”猜测归属；真实/虚拟 deadline 在同一 admission 事务内守卫。
- public read 过滤 `is_visible`；Admin read 可按授权查看隐藏比赛；实时与最终排名使用同一 comparator。
- 删除和 FK/orphan guard 只在 App-owned 竞赛关系内闭环；不以跨 owner FK 或直连 mapper 代替 contract。
- cutover 只有在所有 P0/P1、集成测试和 gate evidence closed 后才可在测试环境开启；生产仍需单独 release approval。

### Technical Decisions

- 选择 `FINISHING` durable state，而不是先写 `FINISHED` 再异步补副作用。
- 选择 durable scoring receipt/inbox，key 为 `submissionId + judge generation`；重复事件 no-op，失败行可被 scheduler/replay 重试。
- 真实报名增加不含 NULL 的 generated/key strategy，覆盖 REGISTERED/STARTED 的真实 participant；迁移先审计并处理历史重复行。
- `CreateSubmissionDTO`/submission context 携带明确 `contestId`（及必要的 virtual session context）；无 context 就不记竞赛关联。
- 公开比赛 detail 与 list 共用 visibility predicate；ranking comparator、enum、DB wire value 和 VO 统一，rating 更新采用 per-user lock/CAS + receipt。
- 删除统一进入 owner cascade seam；补 app-owned 关系 FK/orphan guard，不添加跨 Auth/Problem owner 的数据库依赖。
- actor/creator 缺失或非法直接拒绝；禁止用 `"admin"` 伪造审计身份；错误码按每个适用 `ErrorCode.code()` 显式映射。

### Task DAG

- `CONTEST-001`（done）：冻结 cutover，收紧 command/actor/version/idempotency/error contract。
- `CONTEST-002`、`CONTEST-003`、`CONTEST-004`（done）：分别修复判题记分、生命周期 finalization、报名/提交/虚拟赛 admission。
- `CONTEST-005`（next ready，依赖 002/003/004）：统一 visibility、ranking、solved count、tie-breaker 和 rating 并发。
- `CONTEST-006`（依赖 001/004）：统一 cascade delete 与 owner 内部关系完整性。
- `CONTEST-007`（依赖 001/005/006）：移除 Admin 对 App-private contest 依赖，加 provider/consumer/ArchUnit seam guards。
- `CONTEST-008`（依赖 002–007）：补齐真实 MySQL、并发、Dubbo、失败恢复测试，并修复真实 Spring wiring evidence。
- `CONTEST-009`（依赖 008）：执行 focused→module→reactor readiness gate；失败则保持 flag=false。

每个 Task 的 source、acceptance、validation、rollback 已写入 `.auto-flow/TASKS.yaml`，该文件是状态唯一真源。

### Coverage / Evidence Mapping

| Review finding | Task | Required proof |
| --- | --- | --- |
| P0-1 duplicate judging / P1-6 lost update / P1-7 infra as WA | CONTEST-002 | duplicate-generation no-op, concurrent aggregate, infra classification, first-solve race |
| P0-2 finalization unrecoverable | CONTEST-003 | FINISHING claim, injected failure, restart/retry, no FINISHED-before-side-effects |
| P1-1/2/3/12 owner contract, DTO loss, remote transaction, actor/audit | CONTEST-001, CONTEST-007 | provider contract, full-field round trip, explicit errors, no fallback, no Admin local transaction assumption |
| P1-4 cascade deletion / P2-2 missing relation integrity | CONTEST-006 | all child tables, repeat delete, orphan scan, intra-owner schema guard |
| P1-5/8/9 registration, contest attribution, virtual submit | CONTEST-004 | concurrent unique registration, explicit mapping, deadline boundary, virtual start→submit→finish |
| P1-10/11/13 visibility, ranking, rating race | CONTEST-005 | hidden detail, shared comparator, solved count, enum parity, concurrent rating |
| P2-1 Admin direct App-private dependency | CONTEST-007 | dependency tree, imports scan, 3-layer ArchUnit and sanity trigger |
| P2-3 insufficient test evidence | CONTEST-008/009 | Testcontainers matrix, module/IT/verify output, diff/security/concurrency review |

### Verification Strategy

1. 每个 Task 先跑 focused unit/contract test，再跑受影响模块；不使用 `-Dtest='*IT'` 代替普通测试。
2. 数据一致性 Task 必须使用真实 MySQL/Testcontainers 和并发 barrier；Mockito 只证明纯 seam，不证明 SQL/事务。
3. 竞赛模块定向命令：`(cd services && ./mvnw -pl app/modules/contest,app/app-web,admin -am test -B)`；IT 单独用 `-Dtest='*IT'`。
4. 最终命令：`(cd services && ./mvnw -pl app/modules/contest,app/app-web,admin -am verify -B)`、必要时 `./mvnw verify -B`、`git diff --check`；真实失败必须保留为 blocker。
5. 迁移验证覆盖 Flyway validate、空库/已有数据、历史重复清理和重复执行；不修改 applied migration。
6. 结束前复核安全、并发、错误翻译、资源/重试、owner dependency、未授权跨模块 import、公开可见性和 cutover flag。

### Compatibility / Rollback

- 先保持旧外部 HTTP/Result/VO shape，新增 command metadata/context 走兼容字段或明确 admin header；不得让客户端重试生成新 commandId。
- 每个 schema 变化使用新的版本化 migration；应用代码先兼容旧/新读，constraint 生效后才切换 writer。
- 任一 Task 回滚时按其 seam、migration、tests 成组回滚；禁止保留两套并行 writer/排名 comparator。
- 任一 readiness evidence 缺失，`app.features.contest-dubbo-cutover=false` 是唯一安全默认；不做远程发布。

### Delivery Authority / Terminal Condition

本轮只完成设计和控制面更新，不修改业务源码、不提交、不 push。首个可执行 Task 是 `CONTEST-001`，但须在后续获得实现授权后交给 `implement-development-slice`。当 `CONTEST-009` 的所有 acceptance、coverage、review 和真实验证证据闭合，且测试环境 cutover smoke 通过，才可把竞赛标为生产候选；生产启用仍需显式 release approval。

### Implementation Progress Addendum (2026-08-10)

`CONTEST-001`–`CONTEST-006` have now been implemented, reviewed, and focused-validated in the protected dirty worktree. CONTEST-008 wiring evidence is prepared but remains open behind CONTEST-007 owner isolation; the next task is CONTEST-007. The shared-root migration decision and the incomplete `MIGRATION_SCHEMA=app` contest base chain remain recorded in `.auto-flow/DECISIONS.md`; neither is a production cutover claim.



## Active Execution Packet — Admin Legacy Consumer Migration (2026-08-10)

### Objective

消除 `services/admin` 对 App-private `problem`、`problemlist`、`submission`、`solution`、`forum`、`user`、`vote`、`notification` 的当前 219 个非公开 imports（63 个文件；其中 modules.* 为 213/61，另有 6 条非 app.api imports），改为 owner public contracts，恢复 Admin 编译和真实跨服务 wiring，并解除竞赛 readiness blocker。

### In Scope / Out of Scope

- **In scope:** 八个 family 的调用盘点、app-api/Auth typed contract 缺口、App providers、Admin consumers/adapters/projections/controllers/tests、RpcPolicy、dependency/ArchUnit guards、Admin compile/context、ResourceServerJwtVerifier context blocker、SandboxForkE2EIT stale path、最终 readiness gate。
- **Out of scope:** CONTEST-007 已完成的 contest seam 重做；恢复 `backend-app` aggregate dependency；Admin 复制 App 业务实现；跨 owner DB FK/共享事务/双 writer；contest cutover、生产发布、远程环境迁移或 release approval。

### Root Cause / Capability Gap

Admin 仍把 App-private Entity/Mapper/Service/DTO 当作本地实现依赖，而当前 reactor 中 `backend-app` 仅为聚合 POM，旧实现类已不再提供。因此恢复旧依赖不能编译；真正缺口是每个 Admin operation 没有完整的 owner public contract/provider/consumer seam。旧附件 57/212 已漂移，当前只读审计核验为 63/219（modules.* 仍为 61/213），notification 与非 app.api user imports 不能遗漏。

### Behavioral Invariants

1. App owns App business data and its local transaction; Auth owns credentials/RBAC/account identity.
2. Admin uses typed public contracts only; no owner-private Entity/Mapper/Service imports or cross-owner database access.
3. External Admin routes, `Result` envelope, VO field names, pagination, authorization, audit principal and error semantics remain compatible.
4. Remote mutations carry stable command/idempotency metadata, actor/creator/trace and explicit error mapping; Admin never wraps them in local distributed-transaction illusions.
5. Read queries use `RpcPolicy` (`QUERY_TIMEOUT_MS`/`QUERY_RETRIES`) and bounded batch APIs; no unbounded N+1 remote-call regression.
6. `app.features.contest-dubbo-cutover=false` remains the safe default until all gates and explicit release approval pass.

### Task DAG

`ADMIN-001 (in_progress) → {ADMIN-002, ADMIN-003, ADMIN-008}; ADMIN-003 → {ADMIN-004, ADMIN-005, ADMIN-006}; ADMIN-006 → ADMIN-007; {ADMIN-002, ADMIN-004, ADMIN-005, ADMIN-007, ADMIN-008} → ADMIN-009 → ADMIN-010 → ADMIN-011`; `CONTEST-007` remains blocked by `ADMIN-009`; `CONTEST-009` depends on `CONTEST-008 + ADMIN-011`.

### Implementation Steps

1. `ADMIN-001`: inventory all current 63 files/219 imports, record old-snapshot drift, map each operation to existing app-api/Auth contracts or an explicit missing contract, and snapshot external Admin DTO/VO/error behavior.
2. `ADMIN-002`: migrate User/Profile with App profile contracts and Auth account/RBAC contracts; keep audit identity principal-derived.
3. `ADMIN-003`: migrate Problem/TestCase/Tag/Detail/Export read paths, reusing existing read ports and adding only operation-specific typed read/provider gaps.
4. `ADMIN-004`: migrate Submission read/statistics/rejudge/test-case paths with stable idempotent commands and real App provider wiring.
5. `ADMIN-005`: add provider-owned ProblemList administration/read contracts and migrate all Admin list mutations and projections in one owner-local transaction.
6. `ADMIN-006`: migrate Solution/SolutionComment using existing solution owner ports plus a bounded admin read provider where required.
7. `ADMIN-007`: migrate Forum/comment/tag/Vote, remove `EdgeOperationMapper` bypass and keep Vote owner semantics.
8. `ADMIN-008`: migrate Notification read/broadcast consumers; use existing notification DTOs and `RpcPolicy` query settings.
9. `ADMIN-009`: delete all private imports, prove dependency tree/ArchUnit negative fixtures, normalize new query refs to `RpcPolicy`, instantiate Admin context and pass `admin -am compile`; only then unblock CONTEST-007.
10. `ADMIN-010`: repair real ResourceServerJwtVerifier context wiring and the supported SandboxForkE2EIT seccomp path without mocks/exclusions or weakened sandbox policy.
11. `ADMIN-011`: run focused→module→reactor verification, owner runtime smoke, security/concurrency review and gate record; only then allow CONTEST-009 to become ready.

### Required Evidence / Coverage

See `.auto-flow/COVERAGE.md` Admin table and `ADMIN-001..ADMIN-011` acceptance criteria. Every transaction/concurrency/retry/Dubbo/context claim requires real MySQL/Testcontainers or Spring/Dubbo evidence; Mockito remains supplemental.

### Compatibility / Rollback

No database migration is planned. Each family rolls back as one contract/provider/consumer/test seam to its last verified state; existing flags remain false until observed and approved. Never restore the aggregate dependency, private mapper access or parallel writer. Any failed gate keeps cutover false and blocks release.

### Delivery Authority / Terminal Condition

This planning turn updates only `.auto-flow/`; no business source, migration, service state, commit, push or release is authorized. The terminal condition is ADMIN-011 evidence complete, CONTEST-007/009 blockers explicitly reconciled, all required checks green, and separate release approval recorded before any test or production cutover.

## Active Execution Packet — CONTEST-009 Blocker Closure (2026-08-11)

### Objective

Resolve the `backend-app-api` `ProblemApiContractShapeTest` AssertJ generic compilation baseline, rerun the final readiness gate, and initiate the separate production release approval record without enabling contest cutover.

### Root Cause / Capability Gap

AssertJ inferred a captured `Class<?>` element type for mixed record-component types, making `containsExactly(...)` and `containsExactlyElementsOf(...)` fail to compile. Production contracts were not the cause.

### Invariants

1. Keep all contract-shape, record-component type/order and entity-free assertions active; do not exclude or weaken the test.
2. `app.features.contest-dubbo-cutover=false` remains the safe default.
3. No production release, migration, restart or cutover occurs without explicit release-owner approval.

### Task DAG / Acceptance

`CONTEST-009`: fix the test typing, pass the complete `backend-app-api` module, pass focused and full reactor readiness verification, record the approval request, then remain blocked until explicit release authority is recorded.

### Required Evidence

See `.auto-flow/COVERAGE.md` and `.auto-flow/EVIDENCE.md`: targeted test 5/0/0/0, full app-api module 33/0/0/0, focused and full reactor `verify` success with JaCoCo checks, and the required 24-test contest/Admin integration matrix.

### Delivery Authority / Terminal Condition



## Active Execution Packet — Notification Delivery Deepening (2026-08-13)

### Objective

把 App 内已经存在的 Notification intent/channel/ledger seam 深化为可独立伸缩的 Notification Delivery worker role：业务事实通过 durable event 进入 delivery implementation，投递状态可 reclaim、重试、审计，SMTP/WebSocket/in_app adapter 的故障不回流到用户请求或 verdict transaction。保持 App 作为通知业务数据 Owner；本阶段不创建 `backend-notification` 第四个 logical service。

### In Scope / Out of Scope

**In Scope**

- `services/app/app-web/src/main/java/com/ulticode/modules/notification/` 的 `NotificationIntent`、`NotificationDispatcher`、`NotificationChannel`、consumer、ledger 与 adapter seam。
- `services/app/app-web/src/main/java/com/ulticode/modules/email/` 的 intent/outbox/worker 化、`SmtpSenderPort`、`EmailRenderPort` 与现有 channel adapter。
- `services/app/app-web/src/main/java/com/ulticode/modules/event/` 的 durable outbox/inbox publication、event identity、retry 与 replay 语义。
- `notification_delivery_ledger` 的 claim、fence、reclaim、bounded retry 与终态状态机；必要 schema 变化只能追加新的 Flyway migration。
- App `api`/`worker` runtime role、scheduler lease/metrics/startup contract、notification integration tests、ArchUnit/import guards、CONTEXT/迁移指南与 `.auto-flow/` evidence。

**Out of Scope**

- 立即创建 `backend-notification`、独立物理数据库、独立 WebSocket/STOMP transport 或新 broker。
- 把 `notifications`、`notification_preferences`、`email_templates`、`email_logs` 或业务 intent 写 ownership 移出 App。
- 拆分 Contest/Submission intake、改变 D-04/D-05、改变 verdict/result payload、JWT/cookie/security、HTTP/Dubbo/Result/RpcResult contract。
- 每请求 Auth introspection、跨 owner DB 访问、2PC、永久双写、同步 SMTP/WebSocket fan-out、敏感 DTO/凭证写入事件或日志。
- Commit、push、PR、部署、共享环境 migration 或任何远端资源修改。

### Root Cause or Capability Gap

- Notification module 已有 `NotificationIntent`、`NotificationChannel` 和 per-channel ledger，但 delivery 语义仍与 App HTTP/JVM 调度角色共置。
- `SubmissionJudgedNotificationConsumer` 已接入 durable inbox 方向，但必须确保所有事实 publication 经过可重放的 outbox/inbox，不能出现 publish stub 后假标记 `DELIVERED`。
- `NotificationDispatcher` 具有 channel fan-out，却需要把 stale `CLAIMED`、bounded `FAILED` retry、claim fencing 和 send-success/confirm-fail 作为一个深 implementation 收敛。
- Email/SMTP 和 WebSocket 是外部 I/O adapter；它们不能位于核心业务事务的同步路径。当前缺口是独立 worker runtime role，而不是新的业务数据 Owner。

### Behavioral Invariants

- App 是 `notifications`、`notification_preferences`、`email_templates`、`email_logs`、`notification_delivery_ledger` 与 Notification intent/outbox 的唯一数据 Owner；没有跨 owner Entity/Mapper 写入。
- durable publication at-least-once；event/intent/channel identity、source generation 与 consumer inbox 幂等；任何 row 在 durable publication 前不得标记 `DELIVERED`。
- `CLAIMED` 可按 `claim_owner`/CAS fencing reclaim；`FAILED` 只在有上限和退避时重试；`DELIVERED`、`SKIPPED` 和达到上限的 `FAILED` 是终态。
- SMTP、WebSocket、in_app adapter 失败只隔离对应 channel；用户请求与 verdict transaction 不同步等待外部 I/O。
- Submission 与 ContestSubmission 的必要 intake 仍由 App 在同一 owner transaction 完成；post-verdict Contest/Notification/Achievement effects 继续通过 durable event。
- WebSocket 认证仍只接受 `access_token` HttpOnly cookie；不把 token、cookie、密码、hidden testcase 或完整敏感 DTO 放进 intent、ledger、日志或测试证据。
- 现有 HTTP、Dubbo、Result/RpcResult、JWT/cookie/security 和 public payload contract 保持不变；普通请求不调用 Auth RPC。
- worker 是 App 的独立 runtime role，不是第四个 logical service；scheduler 以 lease/CAS/fence 防多副本重复执行。

### Acceptance Criteria

- `NOTIFY-001`–`NOTIFY-003`：intent/event contract、durable inbox/outbox、ledger reclaim/retry/fencing 和 channel failure isolation 均有 interface-level、MySQL/Redis integration 与 crash-window evidence。
- `NOTIFY-004`：App `api`/`worker` profile 可独立启动/扩容；HTTP profile 不运行 delivery scheduler；配置、disable flag、lag/retry metrics、graceful shutdown 有测试证据。
- `NOTIFY-005`：报告中的 Notification candidate、迁移指南 P4 与现有 CONTEXT terms 全部映射；focused/module/IT/reactor/config/review evidence 完整；没有把 worker role 误报为 `backend-notification` 已完成。
- 不得以 Mockito-only 通过证明 SQL、lease、事务、Redis/SMTP/WS 故障或进程重启语义；关键一致性证据必须经过真实 integration seam。

### Required Evidence

- `NotificationChannelContractTest`、`NotificationDispatcherTest`、`SubmissionJudgedNotificationConsumerTest` 与相关 intent/adapter tests 的失败路径证据。
- `NotificationDeliveryLedgerMapperIT`、outbox/inbox integration tests 对 duplicate、乱序、consumer crash、stale claim、send-success/confirm-fail、bounded retry、Redis/SMTP/WS unavailable 的结果。
- `SubmissionResultOutbox`/`IntegrationOutboxDispatcher` 证据证明 Notification 不从 verdict transaction 同步 fan-out，且不会 false `DELIVERED`。
- App api/worker context/startup、scheduler lease、disable flag、lag metrics、graceful shutdown 与 Compose/config evidence。
- ArchUnit/import audit：无跨 owner Entity/Mapper、无逆向依赖、无 per-request Auth RPC、无新的同步 fan-out。
- 新 migration checksum/validate、空库/已有数据兼容、`git diff --check`、focused → module → reactor 验证和 security/concurrency/failure-isolation review。

### Files / Symbols / Call Chains

- Intent producer → durable outbox → `SubmissionJudgedNotificationConsumer` → `NotificationDispatcher` → `NotificationChannel`/ledger → in_app/email/websocket。
- `services/app/app-web/src/main/java/com/ulticode/modules/notification/intent/NotificationIntent.java` 及具体 intent records。
- `services/app/app-web/src/main/java/com/ulticode/modules/notification/dispatcher/NotificationDispatcher.java`。
- `services/app/app-web/src/main/java/com/ulticode/modules/notification/channel/NotificationChannel.java`、`EmailNotificationChannel`、`WebSocketNotificationChannel`、`InAppNotificationChannel`。
- `services/app/app-web/src/main/java/com/ulticode/modules/notification/ledger/` 与 `NotificationDeliveryLedgerMapper`。
- `services/app/app-web/src/main/java/com/ulticode/modules/event/` 的 integration outbox/inbox dispatcher、replay 与 consumer identity。
- `services/app/app-web/src/main/java/com/ulticode/modules/email/port/SmtpSenderPort.java`、`EmailRenderPort.java`、`EmailServiceImpl`、`JavaMailSmtpSenderAdapter`。
- `init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql` 及后续 append-only migration。

### Implementation Steps

1. `NOTIFY-001`: 冻结 App ownership、intent/event envelope、idempotency key、channel contract、payload redaction 与 failure state machine；补 contract test matrix。
2. `NOTIFY-002`: 把 Notification facts 接到唯一 durable outbox/inbox path；修复 crash window、false delivery、duplicate/乱序/generation 处理；移除 verdict transaction fan-out。
3. `NOTIFY-003`: 完成 ledger claim fencing、stale reclaim、bounded retry/backoff、terminal states 和 per-channel failure isolation；以真实 MySQL/Redis 验证。
4. `NOTIFY-004`: 增加 App worker runtime role，隔离 delivery scheduler 与 HTTP profile；补 lease、startup/disable/lag/shutdown 证据。
5. `NOTIFY-005`: 执行最终审查、文档同步、owner/runtime matrix、focused/module/reactor/config 验证与 Completion Audit。

### Compatibility / Rollback

- 每个 Task 按 interface/consumer/ledger/runtime seam 成组回滚；保留 durable intent、ledger 和 replay watermark，不恢复同步 SMTP 或不可持久 fan-out。
- 新 schema 只使用后续 Flyway migration；不得修改 applied migration。writer cutover 前保持旧读兼容，cutover 后以 forward reconciliation 回滚，不使用 destructive Git。
- worker 启动失败可回到 combined App profile；这不允许恢复核心事务内 SMTP/WebSocket，也不允许产生第二套 writer。
- 只有当 replay、duplicate、lease、failure isolation 和 profile evidence 完整时才可扩大 worker traffic；本阶段不产生独立服务发布或数据库拆分承诺。

### Focused Checks

- `cd services && ./mvnw -pl app/app-web -Dtest='*Notification*Test,*Inbox*Test,*Outbox*Test' test -B`
- `cd services && ./mvnw -pl app/app-web -Dtest='NotificationDeliveryLedgerMapperIT,*Notification*IT' test -B`
- App api/worker profile context/startup and scheduler lease checks。
- Compose/config validation、ArchUnit/import audit、secret and sensitive-payload scan。
- `cd services && ./mvnw verify -B`、`./scripts/dev/test.sh quick`、`git diff --check`。

### Final Validation Tier

Tier D：interface contract tests → real MySQL/Redis outbox/inbox/ledger IT → App api/worker startup/lease smoke → `services` reactor `verify` → Compose/config and architecture/security/concurrency/failure-isolation review → changed-file audit。任一真实失败或缺失证据保持 blocker，不得以降级开关伪造通过。

### Expected Review Areas

Outbox state transitions and crash windows; event identity/generation ordering; claim fencing/reclaim/retry bounds; transaction isolation; SMTP/Redis/WebSocket failure containment; hidden data and credential redaction; worker role ownership; scheduler duplicate execution; public HTTP/Dubbo/JWT compatibility; no fourth service drift; applied migration safety; test realism; docs/control-plane drift; protected dirty worktree。

### State Files to Update

`.auto-flow/TASKS.yaml`、`.auto-flow/COVERAGE.md`、`.auto-flow/DECISIONS.md`、`.auto-flow/PLAN.md`、`.auto-flow/RESUME.md`、`.auto-flow/HANDOFF.yaml`、`.auto-flow/WORKLOG.md`。这些文件只用于控制面记账，不得暂存为交付。

### Delivery Authority

仅授权本地 planning 与后续本地 implementation/review/validation；不授权 commit、push、PR、merge、部署、共享环境 migration、远端资源修改或 destructive Git。现有 dirty worktree 必须保留。

### Terminal Condition

Notification Delivery phase 仅在 `NOTIFY-001`–`NOTIFY-005` 全部 `done`、所有 acceptance/coverage/review/validation evidence 完整、worker role 可独立伸缩、无未解决安全/并发/owner 问题，并明确“尚未创建 backend-notification”时关闭。若未来要创建第四个微服务，必须新建 ADR 和 Execution Packet。

### Active Task

`NOTIFY-001` 是唯一 `ready` Task；其余 Notification tasks 等待依赖完成。

### Dependencies

`NOTIFY-001 → NOTIFY-002 → NOTIFY-003 → NOTIFY-004 → NOTIFY-005`。历史 `TASK-027`–`TASK-044` 的 App deepening 方向作为来源与约束保留；本次以 `NOTIFY-*` 任务真源跟踪 Notification phase，不重写历史完成状态。

## Active Execution Packet — Service Contract Boundary Convergence (2026-08-17)

### Objective

把 `backend-app-api` 从跨服务契约单体收敛为 App-owned contract seam；为 Submission 与 Notification 建立 provider-owned API artifact，迁移全部真实 caller/provider/test/POM 引用，消除 Submission codec/status catalog 漂移，并在既有 SPLIT-004 cutover 与明确 release authority 之后删除 Submission compat 两跳代理，恢复 Judge/App/Admin 到 `backend-submission` 的单跳、单 writer 路径。

### In Scope / Out of Scope

**In Scope**

- 新增 `services/api/submission-api`（`backend-submission-api`、`com.ulticode.submission.api`）与 `services/api/notification-api`（`backend-notification-api`、`com.ulticode.notification.api`）。
- 将 implementation-free 的通用安全、命令元数据和值契约从 `backend-app-api` 提取到 `backend-common`：`ActorDelegation`、`WriteCommand`、`DifficultyCountDTO`、`AccountInfo`、`JwtPayload`、`AccountReadPort`、`JwtValidationPort` 与 `DelegationAssertionContract`；Auth API 自有 provider contract 不在本次改写。
- 按 owner matrix 移动 Submission owner contracts（write/fence/read/admin/rejudge/contest collaboration、Submission events 和 reachable wire DTOs）与 Notification owner contracts（admin read/write、commands/DTOs、service identity、intent event/payload）；每个 caller、provider、test、POM 和文档引用一并迁移。
- 保留 App-owned Problem facts、user/recipient facts 和 App-local collaboration seams 的明确例外；Judge 的 app-api 直接引用收敛为允许的 App facts/runtime seams。
- 将 `SubmissionTestCaseDetailDTO`、DTO-based `TestCaseDetailCodec` 和 `SubmissionStatusCatalog` 收敛到 Submission contract seam；不移动或共享 Submission Entity/Mapper。
- 在授权 cutover 后将 backend-submission local writer/fence 直接注册为唯一 Submission provider，删除 compat forwarder、App duplicate provider、owner mode 和两跳写回链；按 runbook 验证 grants/outbox/fence/contest/event 回滚。
- 更新 ArchUnit/contract tests、迁移指南 §4/§6/§11、owner/runtime matrix、DEC-019 和 `.auto-flow` 证据。

**Out of Scope**

- 不新增 broker、HTTP API、独立业务表、跨 owner SQL/2PC、共享 Entity/Mapper 或永久双写/alias。
- 不改变 `Result`/`RpcResult`、HTTP route、JWT/cookie、security/audit、Dubbo group/version、事件字段/版本、Submission/Contest 强一致与 outbox/inbox 语义。
- 不在本计划中物理拆 Contest ranking、Moderation 或 Notification delivery worker；不重做 Search worker 或已应用 migration。
- 不执行未授权的 production/shared DB cutover、REVOKE、部署、commit、push、PR、merge 或 destructive reset。

### Root Cause or Capability Gap

- verified `app-api` 目录把 Submission、Notification、Contest、Problem、Forum、Solution、Moderation contracts 共置；各 owner service 通过同一个 `backend-app-api` artifact 学习非本 Owner 接口。
- `SubmissionWriteCompatibilityProvider` 的默认 `compat` 分支持有 `@DubboReference(group="backend-app") appWriter`，而 App 仍注册 `SubmissionWriteProvider(group="backend-app")`；Judge/remote callers 因此形成 `backend-submission → backend-app` 两跳。
- `backend-app-api` 还保留了被 App/Admin/Notification/WebSocket 共同消费的 credential-free security seam、command metadata 和 difficulty/count value；若没有独立 common slice，最终 App-only gate 会留下非 App owner。
- App 与 Submission 各有 `Submission.java`、`JudgeOutboxRecord`、`JudgeOutboxMapper`、`TestCaseDetailCodec`、`SubmissionStatusCatalog`；DEC-011 允许 Entity/Mapper 私有复制，但 codec/catalog 的逐字节复制是可漂移的共享语义缺口。
- Existing auth-api/admin-api owner-specific artifact precedent and migration-guide target owner make service-owned contract modules the smallest complete fix; deleting one port without moving its callers would only spread complexity.

### Behavioral Invariants

1. Contract modules stay dependency-free from implementation: no Entity/Mapper/ServiceImpl/Repository/MyBatis/Spring bean/security or implementation-module dependency.
2. Submission and Notification wire shapes, schemaVersion/owner, IDs, metadata, redaction, Result/RpcResult, timeout/retry, audit actor and Dubbo group/version remain unchanged; only Java contract owner/package changes under a matched release.
3. At any runtime point there is one Submission writer/provider for regular paths. No `backend-submission → backend-app` synchronous write/fence hop, no second Dubbo group, no double writer, and no cross-owner SQL/2PC.
4. Submission intake, verdict CAS/generation fence, judge/result/created outbox and Contest association semantics remain as already verified; Contest tables stay App-owned and association remains durable event/inbox based.
5. `TestCaseDetailCodec` preserves persisted field names, null/blank/legacy behavior and JSON round-trip; `SubmissionStatusCatalog` and the common `SubmissionStatus` wire values remain canonical. Private Entity/Mapper mapping remains owner-local.
6. App-provided Problem/user/recipient fact seams remain explicit and bounded; Notification does not read App tables directly; Judge remains storage-free and sandbox/security behavior is unchanged.
7. Tokens, cookies, passwords, hidden tests and sensitive DTOs never enter new contracts/events/logs; all production cutover rollback uses route/grant/watermark/reconciliation, never applied-migration rewrite.
8. Common extraction moves only Java-only, credential-free contract/value shapes; Auth remains authoritative for account/JWT facts and no implementation or persistence dependency enters `backend-common`.

### Acceptance Criteria

- `CONTRACT-001` freezes a complete type-level owner matrix and matched-release boundary with no unclassified app-api contract.
- `CONTRACT-001-COMMON` moves every matrix-assigned implementation-free common/security seam out of app-api and proves the common module remains dependency-safe.
- `CONTRACT-002` and `CONTRACT-003` create compiling provider-owned Submission/Notification API modules with contract/ArchUnit/event evidence and no old alias path.
- `CONTRACT-004` and `CONTRACT-005` migrate every caller/provider/test/POM; app-api retains only App-owned contracts and explicit fact exceptions; Judge's direct app-api surface is allowlisted.
- `CONTRACT-006` leaves exactly one Submission codec and one status catalog with DTO-based golden-vector/persistence evidence and no shared Entity/Mapper.
- `CONTRACT-007` closes the existing authority gate, switches remote/local, proves one direct Submission provider and removes compat/double registration; failure uses the existing reversible runbook.
- `CONTRACT-008` closes the final architecture/documentation/review/validation gate without claiming unrelated physical splits.

### Required Evidence

- Type-level owner matrix, graph/source evidence and coverage record for all app-api scopes; direct fallback for excluded scripts/docs/target ranges.
- New API module compile/test, ArchUnit, contract-shape, event JSON and forbidden import/dependency scans; all affected caller/provider focused tests.
- Canonical codec/catalog count scan, legacy JSON/golden vectors and real `test_details` persistence round-trip.
- Single-hop/provider registration audit, Judge/App/Admin/Notification boot and Dubbo reference evidence, no sensitive payload evidence.
- Authorized disposable MySQL/Redis schema cutover, row/checksum/grant/outbox/fence/contest/event and rollback evidence; no production claim without release record.
- `./scripts/dev/test.sh quick`, services focused/module/`verify`/`*IT`, Compose base+dev+prod, `graphify update .`, `git diff --check`, formal standards/spec/security/concurrency/compatibility review and Completion Audit.

### Active Task

`CONTRACT-007` — source-boundary convergence through `CONTRACT-006` and the local Submission single-hop/single-writer cutover are complete; direct Submission owner providers now replace the compat/App duplicate registration, with focused validation and final review still required. `CONTRACT-008` remains pending for the final audit.

### Dependencies / Task DAG

`CONTRACT-001 → CONTRACT-001-COMMON → {CONTRACT-002, CONTRACT-003}`；`CONTRACT-002 → CONTRACT-004`；`CONTRACT-003 → CONTRACT-005`；`{CONTRACT-002, CONTRACT-004} → CONTRACT-006`；`SPLIT-005-retirement-authority → SPLIT-005-runtime-access → SPLIT-005-runtime-cutover-observation`；`{CONTRACT-004, CONTRACT-006, SPLIT-005-retirement-authority, SPLIT-005-runtime-cutover-observation} → CONTRACT-007`；`{CONTRACT-005, CONTRACT-006, CONTRACT-007, SPLIT-005} → CONTRACT-008`。Local runtime access and observation are done; CONTRACT-007 compatibility retirement is the remaining implementation gate。

### Files / Symbols / Call Chains

- Contract source and tests: `services/api/app-api/src/main/java/com/ulticode/app/api/{service,command,dto,event,error}`, `services/platform/common/src/main/java/com/ulticode/common/{command,dto,auth,security}`, existing `auth-api`/`admin-api` precedents, and new `api/submission-api`/`api/notification-api`.
- Submission write/fence chain: `judge.RemoteSubmissionWritePort` / App `RemoteSubmissionWritePort` → `backend-submission` provider → `DefaultSubmissionWritePort`/`DefaultSubmissionFencePort`; current forbidden chain is `SubmissionWriteCompatibilityProvider.appWriter` → App `SubmissionWriteProvider`.
- Submission owner callers: `ContestServiceImpl`, `SubmissionServiceImpl`, `SubmissionController`, `SubmissionAdministrationProvider`/rejudge path, `SubmissionReadProvider`, `SubmissionUserQueryProvider`, Admin submission adapters/projections, Judge `DefaultJudgeAttemptExecutor`.
- Notification chain: `NotificationAdministrationProvider`, `NotificationAdminReadProvider`, Admin notification adapters/services, App `NotificationIntentEventPublisher`/inbox; App-owned `UserNotificationReadProvider` remains a fact provider.
- Drift seam: App and Submission `SubmissionStatusCatalog`/`TestCaseDetailCodec`; API `SubmissionTestCaseDetailDTO`; private entity mapping and separate Judge `JudgeTestCaseDetailCodec`.
- Build/config/docs: `services/pom.xml`, each affected service POM, `docker-compose.prod.yml`, `services/app/app-web/src/main/resources/application.yml`, `services/submission/src/main/resources/application.yml`, `scripts/dev/submission-schema-cutover.sh`, `services/docs/MICROSERVICE_MIGRATION_GUIDE.md`.

### Implementation Steps

1. Execute CONTRACT-001 owner/type/FQCN matrix and matched-release decision; do not edit business source before it is accepted.
2. Extract and test CONTRACT-001-COMMON's eight Java-only security/metadata/value types; migrate App/Admin/Notification/WebSocket consumers and tests while keeping Auth API's provider-owned duplicate contracts independent.
3. Create and test Submission/Notification API artifacts in parallel dependency branches; move only matrix-approved pure contracts.
4. Migrate Submission and Notification callers/providers/tests/POMs; add negative import/dependency/registration gates; keep runtime route defaults unchanged.
5. Make DTO-based Submission codec/catalog the single implementation and preserve every existing persistence/wire vector.
6. The local target has completed remote cutover, direct provider registration and single-writer verification; the separate CONTRACT-007 source task deletes the compat forwarders/App duplicate providers and makes local owner behavior inherent.
7. Update guide/ADR/matrix, run final validation/review/Completion Audit, and stop only when every coverage row has evidence.

### Compatibility / Rollback

Package/FQCN relocation is an internal Dubbo contract release boundary: provider and all consumers ship as a matched version; if the release system cannot coordinate that deployment, CONTRACT-007 stays blocked and no alias/re-export is added. Before runtime cutover, all route defaults and grants remain local/compat. After authorized cutover, failure runs `submission-schema-cutover.sh rollback` to restore route/grant/watermark/reconciliation; if code removal itself fails, first deploy the prior verified compat artifact, then run the data rollback. No applied migration is edited and no destructive reset is used.

### Focused Checks

- `cd services && ./mvnw -pl platform/common -am test -B`, then `./mvnw -pl api/submission-api test -B` and `./mvnw -pl api/notification-api test -B`.
- Affected module focused suites for `submission`, `notification`, `judge-runtime`, `judge`, `app/app-web`, `admin`; API remaining-contract tests and ArchUnit negative scans.
- Import/POM/provider registration scans; canonical codec/catalog duplicate scan; contract JSON and test_details round-trip tests.
- Read-only `scripts/dev/submission-schema-cutover.sh preflight`; only after authority, disposable cutover/rollback and single-writer/contest/event smoke.

### Final Validation Tier

Tier D: contract module tests → affected module focused/integration tests → boot/Dubbo/single-hop/security/concurrency checks → services `verify` and `*IT` → official quick → Compose base/dev/prod → graphify update/diff check → formal review and Completion Audit. Any sandbox, Docker, release or cutover gap remains explicitly blocked; it is never reported as pass.

### Expected Review Areas

FQCN/serialization and matched-release safety; owner matrix completeness; app-api residual leakage; provider/reference group duplication; Submission rejudge/fence/outbox transaction boundaries; Contest association and event ordering; codec legacy JSON; sensitive payload/redaction; Judge no-business-DB/no-HTTP boundary; App fact seam direction; grant/cutover rollback; test realism; documentation drift; protected dirty worktree.

### State Files to Update

`.auto-flow/TASKS.yaml`, `.auto-flow/COVERAGE.md`, `.auto-flow/DECISIONS.md`, `.auto-flow/PLAN.md`, `.auto-flow/RESUME.md`, `.auto-flow/HANDOFF.yaml`, `.auto-flow/WORKLOG.md`; bookkeeping follows the execution packet and may be included in the user-authorized local commit.

### Delivery Authority

用户已明确这是无生产环境的本地开放项目；本轮授权在本地 Docker/PM2/MySQL/Nacos/Redis 目标上完成 runtime access、preflight、cutover、observation 与本地 commit。仍不授权 push、PR、merge、远端/共享环境变更或 destructive reset；本地 runtime gate 已解除。

### Terminal Condition

CONTRACT-001–CONTRACT-006 已 `done`，SPLIT-005 本地 runtime access/cutover observation 已 `done`；CONTRACT-007 的 compat/App provider source retirement 与 cutover safety repair 已实现并通过 focused/module/runtime Review 与 Coverage Audit，CONTRACT-008 已收口。其余 source boundary、codec/catalog、local writer、Review 和本地验证均有证据；本地 commit 已完成，不得把它描述为 production deployment。

## Blocker Resolution Execution Packet — SPLIT-005 runtime gate (2026-08-18)

### Objective

解除本地 Submission cutover 的剩余 operational blocker：确认本地 App/Submission runtime 与账号权限姿态，完成 read-only preflight 后执行 `remote/local` 切流、数据 ownership cutover、App grant retirement 和 single-writer observation。项目没有 production environment，证据只宣称本地目标已切流。

### In Scope / Out of Scope

**In Scope**

- 取得并核对 local runtime、matched local artifacts、rollback window、App/Submission/Nacos/Redis 状态；secret 只进入环境/运行时，不写 `.auto-flow`。
- 确认 App 实际账号对 `ulticode.submissions`、`ulticode.judge_outbox`、`ulticode.submission_result_outbox` 只有表级 DML，确认没有 schema-wide DML；确认 Submission owner 使用独立 `SUBMISSION_DB_*` credential。
- 在本地目标执行 read-only preflight；通过后执行既有 `submission-schema-cutover.sh` confirmation-gated cutover 与 runtime observation。

**Out of Scope**

- 不把本地证据扩展为 production claim；不使用 schema-wide App DML 逃避 source grant gate，不用 route-only 或 inactive runtime 伪造 evidence。
- 不新增业务代码、broker、migration、shared table、Entity/Mapper、永久 alias；不修改 applied migration，不 destructive reset，不 commit/push/deploy 本地非目标环境。

### Root Cause or Capability Gap

原 blocker 来自把“生产部署主机”误当作必要前置；用户已澄清没有 production environment。经本地权限修复、Nacos 启动、owner account provision、preflight 和 runtime startup 后，本地目标已具备独立 App/Submission credentials、owner schema 与 single-writer observation。

### Behavioral Invariants

1. App code/config 保留 `local` 作为回滚入口；当前本地运行时已在 preflight、copy/revoke 和 matched local artifact 后切至 `remote`，Submission direct-owner 行为不再依赖 owner-mode。
2. `remote` route 必须与 direct backend-submission owner provider 成对启用；不允许 route-only、mixed release 或 App/Submission 双 writer。
3. Submission owner 独占三张 source-migrated tables、target-only created outbox、judge/result/fence/reaper/outbox path；App 不跨 Owner 写入。
4. 所有 route/grant rollback 使用既有 runbook、watermark/reconciliation；不回写 applied migration。
5. 外部账号名、密码、连接密钥和完整生产拓扑不进入控制面或日志。

### Acceptance Criteria

- `SPLIT-005-runtime-access`：本地 release、rollback window、App source-table grant posture、独立 Submission owner credential、Nacos/Redis/App/Submission runtime 均有 redacted evidence；read-only preflight 通过。
- `SPLIT-005-runtime-cutover-observation`：本地目标启用 App `APP_SUBMISSION_ROUTING_MODE=remote`；cutover 后 checksum/row-count/grant/outbox/fence observation 证明 single writer；公共 HTTP health 与 Dubbo startup 保持。
- `CONTRACT-007`：compat 双 provider 已退役、direct single-hop/single-writer 和 rollback evidence 完整；`CONTRACT-008` 最终 Gate 已通过。

### Required Evidence

- Deployment host inventory、runtime health/Dubbo registration 和 matched image/artifact digest；不包含 secret value。
- `SHOW GRANTS`、`mysql.user`/`information_schema.USER_PRIVILEGES`、`information_schema.schema_privileges/table_privileges` 和 actual `SUBMISSION_APP_DB_USER` + `SUBMISSION_APP_DB_HOST` preflight evidence，证明唯一 runtime account 无 global/schema/table `ALL`、`GRANT OPTION` 或角色传递闭包，且三张 source table grants 可撤销。
- Source/target shape、row-count/checksum、outstanding events、rollback watermark；cutover/rollback 日志与 reconciliation 结果。
- App submit/rejudge/list、Judge verdict/result、Contest association、Notification event smoke；Submission owner logs/metrics 证明唯一 writer。

### Active Task / Dependencies

`SPLIT-005-retirement-authority(done) → SPLIT-005-runtime-access(done) → SPLIT-005-runtime-cutover-observation(done) → CONTRACT-007(done) → CONTRACT-008(done)`. 当前 objective 已完成，无 Ready/blocked Task。

### Files / Symbols / Call Chains

- Runtime config: `docker-compose.prod.yml` (`backend-app`, `backend-submission`), `services/app/app-web/src/main/resources/application.yml`, `services/submission/src/main/resources/application.yml`。
- Cutover: `scripts/dev/submission-schema-cutover.sh` (`assert_ready`, `assert_revoke_ready`, `copy_forward`, `revoke_app_grants`, `rollback`)。
- Runtime chain: App `SubmissionWriteRoutingPort`/`SubmissionUserQueryRoutingPort` → `backend-submission` provider → owner-local writer/fence/reaper/outboxes；Judge/Contest/Admin/Notification smoke consumers。
- Control plane: `.auto-flow/TASKS.yaml`, `COVERAGE.md`, `DECISIONS.md`, `RESUME.md`, `HANDOFF.yaml`, `WORKLOG.md`。

### Implementation Steps

1. Reconcile local runtime/account inventory, owner matrix, direct provider, grants, route and rollback evidence.
2. Run focused provider/owner IT, official quick/integration, services verify, Compose/YAML/bash checks and graph refresh.
3. Complete security/concurrency/architecture Review and Completion/Coverage Audit; record known external Search gaps and explicit out-of-scope boundaries.

### Compatibility / Rollback

The verified code/config keeps local route as rollback entry. Cutover failure first restores route, then grants/watermark/reconciliation through the existing runbook; direct-provider retirement failure first rolls back the last verified artifact. No migration edit or destructive reset.

### Focused Checks / Final Validation Tier

- Before access: `bash scripts/dev/doctor.sh --pm2`, Docker/Compose status, root-free `SHOW GRANTS`, `bash -n`, Compose config and read-only preflight.
- During observation: route startup, Dubbo registration, App/Judge/Contest/Admin/Notification smoke, source/target checksum and grants, outbox/fence/reaper metrics, rollback rehearsal/decision.
- Final local Tier D: runtime observation → focused smoke → services integration/verify → Compose → security/concurrency/compatibility review → Completion/Coverage Audit. Production evidence is out of scope.

### Delivery Authority / Terminal Condition

用户已确认本地开放项目没有 production environment。Local shared-schema copy、REVOKE、route switch、direct-provider retirement 和 runtime observation 已在 preflight 后完成；不得把它描述为 production deployment。Terminal condition is CONTRACT-007/CONTRACT-008 evidence closure with no Confirmed Finding，已满足。

## ARCHFIX Execution Packet (2026-08-19)

### Historical boundary

The preceding ARCH-002/ARCH-003 blocker-remediation packet and the Submission/Search extraction packet above are historical execution records. They remain preserved for audit and are not the active packet for the current architecture-hardening objective.

### Current authority override (2026-08-20)

The user confirmed that this repository has no production environment and authorized reversible blocker remediation in the sole development/TEST-TARGET. The active acceptance target is therefore development-only: route, writer-quiesce, observation, rollback and compatibility-retirement evidence may close ARCHFIX-005/006 when freshly verified. No development result is production evidence; no push, publish, applied-migration edit or destructive data operation is in scope.

### Objective

修复开发入口复杂、Owner seam 不可执行、跨 Owner 读模型过浅、Search 双实现以及兼容路径过多的问题；不引入新的 broker、Kubernetes、Service Mesh 或独立 MySQL 集群。

### Active DAG

`ARCHFIX-001` → `ARCHFIX-002` → `ARCHFIX-003` → `ARCHFIX-004` → `ARCHFIX-005` → `ARCHFIX-006`

最终 Task：`ARCHFIX-006`，状态为 `done`；`ARCHFIX-001..006` 已完成。当前 Task 状态唯一真源是 `.auto-flow/TASKS.yaml`；历史 Task 保存在 `.auto-flow/TASKS.history.yaml`。

### Scope

- `dev-lite` / `dev-full` 启动入口和默认配置
- 每 Owner schema、runtime account、migration identity 和唯一 writer
- Admin/Search/Submission 粗粒度、批量、版本化 Query contract
- Search 唯一默认 implementation、分页和 fallback 语义
- 已有兼容、legacy、shadow 路径的证据化退休
- 相关测试、脚本、Compose、配置和文档

### Out of Scope

- 新增 Kafka、RocketMQ、Kubernetes、Service Mesh 或独立 MySQL 集群
- 修改 applied migration
- 未有 rollback/retirement evidence 时删除 compatibility path
- 将 local/TEST-TARGET rehearsal 宣称为 production acceptance

### Delivery Boundary

当前仅授权 TEST-TARGET/dev 环境内的脚本、配置、代码、测试和可回滚 rehearsal。不得自动 push、merge、publish 或修改未授权外部资源。

### Terminal Condition

ARCHFIX-006 只有在 dev-lite 首次启动、dev-full 显式启动、Owner grant isolation、粗粒度读 contract、Search 唯一路径和兼容退休全部具备 fresh evidence，且 focused/module/integration/security/formal review 通过后才能完成；证据缺失时保持 blocked。

## ARCHFIX-004 Execution Packet (2026-08-20)

Objective: unify Search offset/page/limit, exact total, deterministic all-index aggregation, and whole-request MeiliSearch fallback without a second provider, writer, migration, or owner boundary.

In scope: DefaultSearchReadProjection, SearchReadProjection, SearchSource adapters, focused contract tests, real MeiliSearch E2E. Out of scope: new infrastructure, public SearchResponseVO field changes, compatibility retirement before ARCHFIX-005, production/external mutations.

Root cause: the single Spring provider currently has two behavioral paths; database fallback resets offsets and reports page length as total, while all-index MeiliSearch can hide a failed index.

Invariants: page stays 1-based; all backends use (page-1)*limit; total is exact; ordering is deterministic; any configured MeiliSearch failure triggers complete database fallback; unconfigured MeiliSearch remains database fallback; Search remains read-only and public response consumers stay compatible.

DAG: ARCHFIX-004-001 (ready atomic count-capable SearchSource/read-adapter migration) -> ARCHFIX-004-002 (database pagination/exact totals) -> ARCHFIX-004-003 (whole-request MeiliSearch fallback) -> ARCHFIX-004-004 (real MeiliSearch E2E) -> ARCHFIX-005 -> ARCHFIX-006.

ARCHFIX-004-001 acceptance: one internal source contract expresses page rows plus exact count and is migrated atomically with all four source implementations, app-api/owner read seams, compatibility callers and compile surfaces, preserving reactor compilation; source owns mapper/URL/metadata; offset/limit is passed through; no second Search provider. The contract-only slice is superseded by the atomic packet below.
ARCHFIX-004-002 acceptance: specific-index offset/limit/exact total; all-index deterministic page and summed total; stable empty/out-of-range response.
ARCHFIX-004-003 acceptance: MeiliSearch request carries offset/limit/true total; any all-index failure triggers whole-request database fallback; disabled path remains database.
ARCHFIX-004-004 acceptance: real MeiliSearch representative indexes prove specific/all page-offset-total and failure fallback; unavailable real service leaves task blocked, HTTP stub cannot close it.

Delivery authority: current TEST-TARGET/dev and disposable local resources only; no production claim, push, merge, publish, route switch, or applied migration edit.

## ARCHFIX-004 Atomic implementation packet (2026-08-20)

### Active Task

ARCHFIX-004-001 is ready. It is the smallest compile-safe vertical slice: migrate the read contract and every current implementation/caller before changing projection totals. The prior contract-only attempt remains historical and was fully reverted.

### Unique implementation path

1. Add offset-aware rows plus exact-count capability to SearchSource; migrate ProblemSearchSource, UserSearchSource, ForumSearchSource, and SolutionSearchSource.
2. Extend the four app-api search seams and their App adapters with parameterized offset/limit and matching count predicates. Keep the existing two-argument SolutionReadPort Dubbo method as a compatibility forwarder; update SolutionReadProvider and Admin's DubboSolutionReadAdapter.
3. Extend UserDirectoryQueryPort without crossing Owner tables. OwnerUserSearchReadAdapter will merge Auth username candidates and App profile-name candidates in account_id ASC order using bounded pages and Auth batch lookup, filter deleted/out-of-request/duplicate accounts, and expose exact union count. Auth's username-only count and App profile candidate count/page predicates must match the returned rows.
4. Update the projection call site only enough to compile against the migrated contract; leave exact DB aggregation for ARCHFIX-004-002. No second provider, Search writer, migration or schema change.
5. In ARCHFIX-004-002, make DB fallback compute per-source counts first, skip source-local offsets to satisfy the global offset, fetch only the remaining page rows, sum exact totals, and return stable empty/out-of-range results. Preserve SearchResponseVO and Result.
6. In ARCHFIX-004-003, use Meili reported hit totals, propagate any index failure to the outer request, apply the same fixed index-order pagination, and fall back to DB for the whole request. Align App config to MEILI_HOST/MEILI_MASTER_KEY; retain local disabled default.
7. In ARCHFIX-004-004, run the real Meili gate. The existing HTTP stub/worker IT is supplemental only; unavailable real service keeps the task blocked.

### Invariants and boundaries

- SearchQueryDTO remains 1-based page with existing validation; SearchResponseVO fields and HTTP Result envelope remain unchanged.
- One DefaultSearchReadProjection remains the only Search reader; Search worker remains the only Meili writer.
- No cross-Owner SQL, shared business table, applied migration edit, new broker, or production route/grant/deploy action.
- DB user fallback ordering is deterministic account_id ASC, and all cross-Owner reads are bounded/batched; the ordering is not exposed as a new public field.

### Task evidence and rollback

- ARCHFIX-004-001: contract/owner-boundary tests, source/adapter offset-count tests, app-api/Admin Solution contract tests, app-web focused compile/test, git diff --check.
- ARCHFIX-004-002: mapper predicate/count tests, specific-index and all-index boundary tests, app-web module test/compile, Console type/build compatibility checks.
- ARCHFIX-004-003: configured/unconfigured/failure-injection tests, App/Compose/worker config scan and Compose validation.
- ARCHFIX-004-004: real Meili representative-index E2E for specific/all offset-total and whole-request failure fallback; stub evidence cannot close it.
- Every task rolls back by restoring the last runnable Java/config artifact; no data rollback is needed because the plan forbids schema/writer changes.

### Open gates

External/production authority is not required for the local implementation slice, but real Meili availability is required for ARCHFIX-004-004. The App/Compose environment-name mismatch is a confirmed configuration gap assigned to ARCHFIX-004-003, not silently fixed during recovery.


### ARCHFIX-004 Replan Note (2026-08-20)

The original contract-only ARCHFIX-004-001 slice was blocked: changing SearchSource.searchDatabase alone breaks four implementations and DefaultSearchReadProjection, while the underlying app-api read ports expose neither offset nor count. It is superseded by the ready atomic packet above, which updates every affected contract, adapter/provider and compile caller before advancing to 004-002. No page-size-as-total fallback is acceptable.
