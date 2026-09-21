# 运维、部署与恢复

本页是运维相关当前知识的唯一主题入口，合并部署、迁移、备份、监控与事件响应。实际执行以脚本、Compose、工作流和 Services runbook 为准；生产环境、权限和外部凭据不由本仓库声称拥有。

## 部署、发布与回滚

### 当前边界

本仓库没有生产环境。生产 Compose、CI/CD、远程 SSH、证书、registry、数据库和流量切换均是可执行但需部署方授权的控制面；disposable Compose/DinD 只证明仓库实现，不构成生产应用证据。

### 开发部署

开发启动命令与 mode 语义由[本地开发](DEVELOPMENT.md#本地开发)统一维护；正常 `dev-lite`/`dev-full` 都要求 `APP_SUBMISSION_ROUTING_MODE=remote` 和 `SUBMISSION_CUTOVER_COMPLETE=true`。

`docker/docker-compose.yml` 是基础配置；`docker/docker-compose.dev.yml` 只在 loopback 暴露开发端口；`docker/docker-compose.prod.yml` 不发布 MySQL、Redis、Nacos 或 backend 端口，前端仅作 HTTPS edge。不要直接用 PM2/Maven 启动 owner runtime，以免绕过 manifest、migration、readiness 和 rollback gate。

### 生产发布前

Docker Verify 与 Docker Publish 按工作流和服务隔离 GHA 构建缓存。缓存导出使用 `ignore-error=true`，仅将缓存上传作为尽力完成的加速步骤；镜像构建、推送、扫描和签名失败仍阻断任务。

Docker Publish 将 GitHub 仓库名统一转为小写，再用于镜像标签、Trivy 扫描、Cosign 签名和不可变发布清单；签名证书身份及源码 URL 保留 GitHub 原始大小写。

Trivy 扫描完成后，无论门禁是否通过，都上传独立的 `trivy-<service>` JSON 报告；扫描失败仍阻止签名和发布清单生成。排查漏洞时从该报告读取受影响包、已安装版本和修复版本。

运行镜像保留固定的基础镜像 digest，并在构建时通过 `apk upgrade --no-cache` 安装该 Alpine 分支的安全更新。后端依赖由 `services/pom.xml` 的 Spring Boot BOM 统一管理；BOM 尚未包含的安全修复使用集中版本覆盖。发布前以最终镜像扫描结果为准。

`host-deploy` 在任何 migration、Redis ACL materialization、Judge sandbox provisioning 或 Compose mutation 前检查：

- approved source commit；
- canonical migration manifest checksum；
- 必需 Compose/runbook 文件；
- 九服务 immutable digest manifest；
- 合并生产 Compose 配置、TLS/JWKS、owner DB 和必要 secret inputs；
- registry digest、Cosign、SBOM、SLSA、Trivy 证据（若由部署策略要求）。

生产 Judge 需要 `JUDGE_DOCKER_HOST`、`DOCKER_TLS_VERIFY=1`、只读 client certificate、共享绝对 workspace、固定 seccomp 和 remote/rootless daemon。生产不挂载 `docker.sock`、不使用 `DOCKER_GID`；socket 仅在显式 disposable `docker/docker-compose.judge-dev.yml --profile judge-socket` 下允许。

## 健康与回滚

发布后先写入 secret-free `PENDING_HEALTH` descriptor。`host-health` 必须逐项检查 allowlisted 服务、前端 HTTPS 和 worker readiness；任何失败都写 `FAILED` 并以非零退出，不能报告部分成功为系统成功。全部通过后才标记 `HEALTHY`。

回滚必须存在上一 descriptor，并且 source/schema checksum 与批准输入一致；继续使用 `skip_migrations=true`，不执行 schema downgrade。回滚前先停止新 writer、保留未消费 outbox/PEL/inbox，确认旧 artifact 与 schema/contract 兼容，再恢复 route。不能通过重新开放跨 Owner grant 或切回已删除 writer 回滚。

### CI/CD 入口

GitHub Actions 由 `.github/workflows/ci.yml` 编排，`ci-ok` 是稳定汇总门禁；服务矩阵在 `.github/services-matrix.json`（每个 deployable 有 `role`/`release_group`/`health` 分类），owner migration、backup、artifact integrity、health 和 rollback 由可复用 actions/runbooks 承接。完整触发条件以 workflow 为准，避免在文档复制易漂移的 job 列表。

GitLab 直连部署入口（`.gitlab-ci.yml`）已于 2026-09-03 退役禁用：旧 job 对固定路径执行 `git reset --hard` 后直接 Compose build/up，绕过 owner migration、release descriptor、immutable image policy 与 health gate，且仓库内无该 runner 仍被授权的证据。任何未来 GitLab 适配器必须消费同一 canonical preflight/descriptor 接口（`scripts/runbooks/deployment-integrity.sh`），不得恢复 reset/build/up 捷径。只读查看当前 release set 使用：

```bash
./scripts/runbooks/deployment-integrity.sh describe            # 人类可读
DEPLOYMENT_OUTPUT_FORMAT=json ./scripts/runbooks/deployment-integrity.sh describe
```

`describe` 输出 evidence=repository-static（非生产证据）；缺 commit/schema/version/digest 时 fail closed。`verify-registry` 校验 services matrix 与生产 Compose 的 backend 集合双向一致，未登记的新 deployable 会失败。

- Contract 兼容：[`services/docs/CONTRACT_COMPAT_GATE.md`](../services/docs/CONTRACT_COMPAT_GATE.md)
- Owner migration：[`数据库迁移与 Owner 收敛`](#数据库迁移与-owner-收敛)
- 监控与 release annotation：[`监控、SLO 与运行证据`](#监控slo-与运行证据)
- 生产问题与外部触发：[`services/docs/SERVICES_ISSUES.md`](../services/docs/SERVICES_ISSUES.md)

### HA 说明

`docker/docker-compose.ha.yml` 的 `ha` profile 是可审计的 stateful reference，不是默认生产 failover：包含 `mysql-replica`、Redis replica/Sentinel、`nacos-2`/`nacos-3`，但 promotion、endpoint 变更、secret rotation、Sentinel-aware client 和 RPO/RTO 仍需 operator。**本仓库不承诺 active-active HA**。配置保留 `masteruser ulticode-replication`、`sentinel auth-user`、`sentinel auth-user mymaster ulticode-sentinel`、`P3-HA-001`、`mysql-replica` 和 `redis-sentinel-1` 等控制面契约。
## 数据库迁移与 Owner 收敛

### 唯一真源

`init-db/migrations/` 是唯一 Flyway migration source。命名为 `V{14-digit timestamp}__Description.sql`；已应用 migration 永不编辑，只新增向前 migration。命令、profile、baseline、seed 和低层原语见 [`../init-db/README.md`](../init-db/README.md)。

### Owner 顺序与权限

迁移编排固定为：

```text
shared → auth → admin → app → notification → submission → post-owner controls
```

`owner-migrate` / `owner-migration-manifest.sh` 校验 owner schema/location、runtime 与 migration account 分离、依赖顺序、源文件 checksum，并以 fenced lease 防并发。runtime 账号不能拥有其他 Owner 表、global/schema `ALL`、`GRANT OPTION` 或隐式角色继承。

当前逻辑边界：Auth 持 account/credential/RBAC/refresh；App 持 profile 和 OJ/社区聚合；Admin 持治理/审计/设置/backup；Notification 持通知与投递；Submission 持提交与判题 outbox。物理独立实例不是当前前置条件，先完成 schema/account/唯一 writer。

### Expand → backfill → verify → cutover → contract

- **Expand**：新增 owner table、索引、version/updated-at 和 proof；保留兼容读路径。
- **Backfill**：以稳定主键批量、幂等、insert-only 回填；记录 count、checksum、孤儿、冲突和 checkpoint。
- **Verify**：检查 missing/extra keys、NULL-safe fields、checksum、writer 状态、权限和 reader 语义。
- **Cutover**：停止并 drain 所有 writer，显式确认后撤销旧 grants/切换 route；不得顺便做隐式全表复制。
- **Contract**：观察期、备份和 rollback 证据满足后，才删除 legacy columns/tables/contracts。

Backfill 默认 dry-run；同主键字段冲突 fail closed 并导出 TSV，不覆盖较新的 owner 行。Rollback 回到已验证 artifact，先恢复 route/grant，再按 runbook 回写新增行；不 `DROP`、`TRUNCATE` 或编辑历史 migration。

### Users 垂直拆分

Auth 只写 `users` account/status/authorization 字段；App 写 `user_profiles(account_id, ...)`。使用后续 migration 完成回填和 compatibility-column contraction；Search、Admin、Notification 通过 bounded facts/Identity contract，不做跨 Owner SQL join。软删除 account 也必须纳入 checksum 和 reconciliation。

#### Submission read owner cutover 与 schema contraction

正常 App user、contest、Problem-statistics、user-tag、generation 和 Admin Submission reads 使用 `backend-submission` owner facts；App 不再保留 Submission local mapper/projection/entity 或 duplicate DTOs。Submission provider 按 `account_id` 分组分页，单页上限 500；`createdSince=null` 为全量，非空为包含式增量窗口。Admin 通过 owner adapter 做 full/incremental reconciliation，验证 ordering、duplicates、nulls、count 和 failures，并以 lease/fence 防多副本重叠。

物理 contraction 与普通 Flyway 分开：先写 `owner_contraction_proof`，再以 backup、quiesce、parity、checksum 和 grant gates 允许 contract migration。当前 repository/disposable rehearsal 已通过（`owner-schema-contraction-contract: PASS`）；真实 production target、traffic drain、backup authority 和 cutover 仍由部署方执行。App 不再读取 Submission-owned SQL。

### Notification contraction

Notification 是 `notifications`、preferences、delivery ledger 的唯一 writer。App 只发布 intent 并保留 WebSocket relay；Admin 通过 `NotificationReconciliationReadPort` 消费 owner facts。物理迁移使用独立 `notification` schema history；默认 preflight，写入要求 `--execute` 与一次性确认 token。Rollback 先回写目标新增行，再恢复 App grants。

### 备份与证据

每次 owner migration 保存不含 secret 的 manifest、rows/checksum、privilege snapshot、lease/report 和失败 artifact。完整五 Owner 备份、加密、恢复演练、retention 和 measured RPO/RTO 见本页“备份与恢复”章节。

### 参考入口

- [`../init-db/README.md`](../init-db/README.md)
- [`../scripts/README.md`](../scripts/README.md)
- [`../scripts/runbooks/owner-migration-manifest.sh`](../scripts/runbooks/owner-migration-manifest.sh)
- [`../scripts/runbooks/owner-schema-contraction.sh`](../scripts/runbooks/owner-schema-contraction.sh)
- [`../services/docs/CONTRACT_COMPAT_GATE.md`](../services/docs/CONTRACT_COMPAT_GATE.md)
## 对象存储（RustFS）

RustFS 是开发、测试、生产共同的必需基础设施，仓库不提供本地磁盘回退。应用在启动时校验
`APP_STORAGE_S3_*`（endpoint、region、bucket、access key、secret key、TLS 开关），缺失即启动失败；
随后在**上下文刷新期间**对 bucket 做有界重试探测（`APP_STORAGE_STARTUP_PROBE_*`，默认 30×2s），仍不可用则本次启动失败，
且此时 HTTP 端口尚未对外服务；两个 owner 的 `/health/ready` 同时报告 `storage` 组件（`UP`/`DOWN`），
不会在对象存储未经验证时返回就绪。任何情况下都不会静默改写本地目录。
运行期请求若将共享状态标为 `DOWN`，后续 readiness 检查会异步合并一次有界 bucket 探测；探测成功前仍返回 503，避免健康检查风暴。

### 桶、前缀与权限

- 单一私有 bucket（默认 `ulticode`，`RUSTFS_BUCKET` 可覆盖），由一次性
  `rustfs-init` 服务幂等创建；随后 `rustfs-iam-init` 用 bootstrap root pair
  创建两个运行时用户。没有任何匿名读权限，也没有公开 bucket 策略。
- App 用户只允许 `app/avatars/*`，Admin 用户允许 `app/avatars/*` 与
  `admin/backups/*`；这两个运行时 pair 不能互换，root pair 只用于 bucket/IAM
  bootstrap 和受控迁移。
- `rustfs-iam-init` 可在同一持久卷上重复执行：已有用户走 `rc admin user passwd`，
  新用户走 `rc admin user add`，随后启用并重新绑定策略；生产 HTTPS 通过
  `RUSTFS_CA_BUNDLE` 显式传给 `rc alias set --ca-bundle`。
- 头像前缀 `app/avatars/{accountId}/{uuid}.{ext}`：浏览器通过后端只读代理
  `GET /api/users/avatars/{accountId}/{name}` 读取（允许匿名，与公开页面的头像展示一致；
  只允许该前缀、且代理校验 key 语法与账号绑定，对象名为服务端 UUID，bucket 保持私有）。
  替换头像时，旧对象的删除意图与 profile 更新在同一 App 事务写入
  `app.storage_cleanup_outbox`，由 `StorageCleanupDispatcher` 带退避重试（5 次后转
  `DEAD` 并保留日志）；删除前复核该 key 已不是当前头像。
- 备份前缀 `admin/backups/{yyyy}/{MM}/{backupId}.sql`：只能通过 `/admin/backups/**`
  的 `ADMIN`/`SUPER_ADMIN` 端点下载与恢复。
- 凭据来自 `.env`/部署密钥系统：`RUSTFS_ACCESS_KEY`/`RUSTFS_SECRET_KEY` 是
  bootstrap root pair；`RUSTFS_APP_*` 和 `RUSTFS_ADMIN_*` 是 prefix-scoped
  runtime pairs。禁止使用 RustFS 文档中的默认账号；生产不使用明文 HTTP，后端经
  `https://rustfs:9000` 访问，证书目录由 `RUSTFS_TLS_CERT_DIR` 提供且证书/CA
  需被后端 JVM 信任。
- `.env.example` deliberately leaves all RustFS credentials empty; local development
  must run `./scripts/dev/init-env.sh`, and production must provide operator-managed
  secrets.


### 卷、备份与恢复

- 数据卷 `rustfs_data` 挂载到 `/data`，容器 UID/GID 为 `10001:10001`；容器重启后对象保留。
- 卷级备份（示例，停止写入后执行）：

```bash
# Resolve the project-scoped volume by its Compose label and fail when the
# result is empty or ambiguous (never guess with `head -1`).
mapfile -t RUSTFS_VOLUMES < <(docker volume ls \
  --filter label=com.docker.compose.volume=rustfs_data --format '{{.Name}}')
if [[ ${#RUSTFS_VOLUMES[@]} -ne 1 ]]; then
  echo "expected exactly one rustfs_data volume, found ${#RUSTFS_VOLUMES[@]}" >&2
  exit 1
fi
docker run --rm -v "${RUSTFS_VOLUMES[0]}:/data:ro" -v "$PWD:/backup" alpine \
  tar czf "/backup/rustfs-data-$(date +%Y%m%d_%H%M%S).tgz" -C /data .
```

  恢复时把归档解回同一卷（保持 `10001:10001` 属主），再启动 RustFS 并运行下面的 smoke test 确认对象可读。
- 旧生产 Compose 的 named volume 也必须先纳入迁移：脚本会通过 Docker Compose volume
  label（优先匹配 `COMPOSE_PROJECT_NAME`，无项目名时要求唯一匹配）解析
  `AVATAR_UPLOAD_VOL`（默认 `app_uploads`）和 `BACKUP_VOLUME`（默认 `backup_data`）；
  卷数据目录宿主可读时直接使用挂载点，否则（典型为仅有 Docker 组权限的部署用户）改用
  固定镜像经 Docker daemon 探测卷布局，并按对象惰性提取到临时目录，无需 root。
  头像卷兼容卷根、`uploads/avatars/` 和 `avatars/` 布局，备份卷读取卷根目录。
  若 Docker 无法唯一解析 legacy volume（包括默认卷），脚本会 fail closed；也可用
  `--legacy-avatar-dir` / `--legacy-backup-dir` 指向已审计的只读提取目录。源卷和旧文件始终不删除。
- 头像迁移用原始 SQL 更新 `user_profiles.avatar`，不会自动产生
  `SearchDocumentChanged` outbox 事件。只要本次 `--apply` 更新了头像行，脚本就会
  输出 `search_backfill=required` 并以非零状态结束，不能把迁移报告为完成；先重启
  App，临时设置 `APP_SEARCH_BACKFILL_ENABLED=true` 与
  `APP_SEARCH_BACKFILL_INDEXES=users` 执行用户索引 backfill，确认 runner 完成后再用
  `--confirm-users-index-backfill`（或 `MIGRATION_SEARCH_BACKFILL_CONFIRMED=true`）
  复核迁移结果。
- 旧本地文件迁移用 `scripts/dev/migrate-object-storage.sh`：默认 dry-run；`--apply` 才上传；
  上传后校验大小/checksum 并回读对象，校验通过后才更新数据库行（`user_profiles.avatar` 与
  `backups.object_key`）；脚本从不删除旧文件，只有在迁移报告确认全部对象已校验后，operator 才可清理旧目录。
- 生产 `host-deploy` 在 ordered owner migrations 之后、pull/`up` 之前执行
  `scripts/runbooks/assert-legacy-objects-migrated.sh`：存在仍指向
  `/uploads/avatars/...` 的 `app.user_profiles` 行，或没有 `object_key` 的
  `admin.backups` COMPLETED 行时，动作 fail closed 并给出迁移命令；该门禁与迁移脚本
  使用同一组行谓词，因此“门禁通过”等于“回填没有剩余目标”。跳过 migration 的部署
  （`skip_migrations=true`，含回滚路径）同时跳过该门禁。
- 生产 `host-deploy` 在 ordered owner migrations 之前验证完整的 deploy service
  allowlist，并要求 migration subset 包含 `backend-admin`；随后记录原有
  `backend-admin` 容器 ID，使用已验证 image refs 执行 `docker compose stop`。
  `backend-admin` 使用独立的 `ADMIN_BACKUP_STOP_GRACE_PERIOD`（默认 3660s）
  和 `adminBackupExecutor` 排空旧 backup writer；默认覆盖 1800s dump、1800s
  upload 以及 60s handoff margin。`scripts/runbooks/assert-admin-backup-drained.sh`
  在迁移前拒绝仍有 `PENDING`/`IN_PROGRESS` 行的数据库。手工运行 migration
  也必须先停止并排空所有旧 writer。
- 排水或迁移前置检查失败时，动作只恢复此前确实运行的原容器；owner migration
  一旦开始，动作保持 `backend-admin` 停止并 fail closed，不把旧 image 重新启动到
  可能已部分迁移的 schema 上。应先检查 migration report，再按兼容 artifact 手工恢复。
- 特权 `post-owner` migration `V20260921120000__Copy_Legacy_Backups_To_Admin.sql`
  在对象回填前幂等地把旧 `ulticode.backups` 元数据复制到 `admin.backups`；
  随后的 `scripts/runbooks/reconcile-legacy-backups.sh` 复制一次性 Flyway copy
  之后出现且未被 `admin.backup_deletion_tombstones` 标记的 source rows；删除备份
  时先在 Admin 事务内持久化 tombstone（并记录该行的 `object_key`），避免后续
  reconciliation 复活已删除目标；提交后立即尝试删除对象，失败时
  `BackupObjectCleanup` 的定时 sweep 依据 tombstone 重试（对象删除幂等，成功后写
  `object_deleted_at`）。
  Runbook 仍拒绝 metadata conflict 和 pre-cutover target-only rows，并在 parity
  通过后写入 `admin.backup_cutover_state`。Owner-scoped 的 Admin migration 只负责
  创建/修复目标表；迁移不会删除旧行或旧文件。
- For an internal production endpoint such as `https://rustfs:9000`, the migration script's Docker
  AWS CLI joins `${COMPOSE_PROJECT_NAME:-ulticode}_object-storage`; set `MIGRATION_DOCKER_NETWORK`
  when the Compose project uses a different network. Host AWS CLI mode is intentionally limited to
  loopback endpoints.
- **部署顺序要求**：升级到本版本后，尚未迁移的旧头像与旧备份在对象上传前不可用（下载/恢复返回明确的
  NOT_FOUND，不会回退本地文件）。请在切换后立即执行迁移（先 dry-run 核对计划，再 `--apply`），并核对
  `MIGRATION_SUMMARY` 全部通过后再对外确认头像与备份功能；迁移脚本幂等，可重复执行。
  `host-deploy` 已把该回填作为 pull/`up` 之前的门禁，因此首次升级应在部署中断后按提示完成迁移再重跑部署，
  而不是先放行新 App。
- RustFS 不可用时：应用启动失败（或既有实例在请求路径上返回明确的存储错误），备份/恢复不会标记成功，
  也不会回退到本地永久目录。

### 参考

- [`../scripts/dev/migrate-object-storage.sh`](../scripts/dev/migrate-object-storage.sh)
- [`../scripts/dev/rustfs-smoke-test.sh`](../scripts/dev/rustfs-smoke-test.sh)
- [`../docker/docker-compose.yml`](../docker/docker-compose.yml)（`rustfs` /
  `rustfs-init` / `rustfs-iam-init` 服务）

## 备份与恢复

### 责任与范围

完整 Owner 备份由外部 Ops runbook 承接，不扩展 Admin HTTP backup API 为跨 Owner 业务接口。归档范围是 `ulticode` control schema 与五个 Owner schema：`auth`、`admin`、`app`、`notification`、`submission`。

`scripts/runbooks/owner-backup-restore.sh` 生成 OpenSSL 加密归档、secret-free manifest、dump SHA-256、表 rows/checksum 和 Flyway history metadata；`restore-drill` 只恢复到一次性 MySQL 目标，运行 migration validate、checksum reconciliation、schema/query smoke，并记录 measured RPO/RTO。密钥由 operator 提供（至少 32 字节），不进入 Git 或日志。

### 并发、保留与恢复

backup、restore-drill、prune 使用同一 fenced database lease（`admin:owner-backup`）并保留同机 `flock` 快速门禁。`admin.fenced_job_leases` 是临时控制状态，不计入业务 checksum 或恢复状态。Retention 只能删除匹配的归档/manifest 对。

恢复顺序：

1. 确认目标是 disposable/授权环境，保存 source commit、schema checksum、manifest 和密钥引用。
2. 解密并校验归档完整性、manifest、dump hash、Owner/schema/table 清单。
3. 按 Owner migration history 做 validate，恢复 control/Owner 数据并做 row/checksum reconciliation。
4. 运行查询、服务 readiness、队列/Inbox 和关键 API smoke；确认不会把派生 Search 索引当作业务备份。
5. 若恢复 MeiliSearch，清理 `search:doc-version:{index}` 后按 Search backfill 重建，并观察索引计数与版本单调性。

### 失败与回滚

错误密钥、缺文件、checksum mismatch、schema mismatch、lease busy、恢复目标不安全或 smoke 失败必须非零退出。生产部署 rollback 使用已验证 descriptor 和 schema-compatible artifact，不通过备份脚本做 schema downgrade。真实 off-host 存储、密钥托管、保留策略和生产 restore authority 仍是外部门禁。

### 参考

- [`../scripts/runbooks/owner-backup-restore.sh`](../scripts/runbooks/owner-backup-restore.sh)
- [`../services/docs/FENCED_LEASE_RUNBOOK.md`](../services/docs/FENCED_LEASE_RUNBOOK.md)
- [`数据库迁移与 Owner 收敛`](#数据库迁移与-owner-收敛)
- [`部署、发布与回滚`](#部署发布与回滚)
## 监控、SLO 与运行证据

### 观测面

可选的 `docker/docker-compose.observability.yml` 提供 loopback-only、digest-pinned 的 OpenTelemetry Collector、Prometheus、Alertmanager、Grafana、Tempo 和 Loki。HTTP Owner 暴露 metrics；无 HTTP 的 Judge/Search worker 通过 Micrometer OTLP 输出。日志携带 trace/span 关联，Grafana 可从 Loki 跳转 Tempo。

生产 telemetry receiver、存储、保留周期、通知 webhook、阈值调优和真实流量 SLO 由外部运维平台负责；仓库 overlay 默认不启动，也不公开 management endpoint 或 secret。

### 关键指标

- HTTP/RPC：延迟、错误、timeout、circuit-open、bulkhead saturation。
- Outbox/Inbox：oldest age、retry、dead、duplicate、lease expired。
- Worker：queue lag、PEL size、PEL oldest age、DLQ size、last success、consume failures。
- Reconciliation/backup：run、skip、failure、checksum/parity 和 lease 状态。
- Scheduler：active、queued、pool size、completed、rejected；pool 仅允许 1–16。
- JVM/resource：CPU、memory、thread、GC、connection pool 和 readiness。

`services/docs/WORKER_SLO_RUNBOOK.md` 是 Worker 指标、告警、DLQ、PEL 和恢复命令的唯一详细入口；初始阈值必须用真实 p50/p95 基线重新调优。

### 操作顺序

1. 先确认服务/worker readiness、依赖健康和最近 release descriptor。
2. 再检查 lag、PEL、DLQ、oldest age、last success 和错误日志的时间窗口。
3. 修复下游依赖、ACL、schema 或 provider，再等待现有 reclaim/retry；不要先删 stream、PEL、ledger 或 version hash。
4. 需要 replay、discard、ledger reset 或 stop-write 时，按 Worker SLO runbook 的门禁执行并保留证据。
5. 记录 trace ID、commit、digest、schema checksum、affected service 和恢复结果；不要记录 token/password/private key。

### 验证

```bash
./scripts/test/observability-contract.sh
```

可选 overlay 的 Compose、Prometheus、Alertmanager、Collector、dashboard 和 release annotation 合约由 `scripts/test/observability-contract.sh` 维护；不要把本地 overlay 通过文档描述为生产 SLO 达标。
## 事件响应

### 适用边界

本仓库没有生产环境；本文定义代码、配置和 disposable rehearsal 的响应入口，不替代部署方的 on-call、变更授权、外部 secret store 或生产恢复流程。Services 架构问题、状态和触发条件唯一登记在 [`services/docs/SERVICES_ISSUES.md`](../services/docs/SERVICES_ISSUES.md)。

### 通用步骤

1. 记录 UTC 时间、service/worker、commit/digest、schema checksum、trace ID 和可复现命令；脱敏日志，不记录凭据。
2. 先确定是代码错误、配置漂移、依赖不可用、资源饱和、数据不一致还是外部权限缺失。
3. 对写路径停止扩散：保留 Outbox/Inbox/PEL/lease，避免 flush、手工重复写或绕过 owner。
4. 修复根因后按对应 runbook replay/reclaim/retry；验证幂等、版本 fence、checksum 和健康结果。
5. 需要生产 mutation、credential rotation、traffic drain、cutover、failover、remote Judge 或 TLS 时，转交部署 authority。

### 快速分流

| 症状 | 首查 | 恢复入口 |
| --- | --- | --- |
| 登录/refresh/权限失败 | Cookie/CSRF、JWKS、Auth readiness、authz version | [安全架构](ARCHITECTURE.md#安全架构与信任边界) |
| Admin 返回 owner unavailable | RPC timeout/circuit/bulkhead、owner readiness、degradation status | [依赖韧性](../services/docs/DEPENDENCY_RESILIENCE_RUNBOOK.md) |
| Submission pending 或 stale verdict | owner receipt、generation/attempt、judge PEL/DLQ | [Worker SLO](../services/docs/WORKER_SLO_RUNBOOK.md) |
| Notification 延迟/重复 | Inbox、delivery ledger、lease、SMTP/Redis relay | [Notification runbook](../services/docs/OBSERVABILITY_RUNBOOK.md) |
| Search stale/缺失 | event version、tombstone ledger、PEL、Meili health | [Worker SLO](../services/docs/WORKER_SLO_RUNBOOK.md) |
| scheduler starvation | named executor active/queued/rejected | [Scheduler runbook](../services/docs/SCHEDULER_RUNBOOK.md) |
| deployment/rollback 不一致 | source/schema/digest descriptor、Compose config | [部署、发布与回滚](#部署发布与回滚) |
| migration parity 失败 | checkpoint、TSV conflict、grant、checksum | [数据库迁移与 Owner 收敛](#数据库迁移与-owner-收敛) |

### 状态语义

`SERVICES_ISSUES.md` 使用 `OPEN`（仓库仍可修）、`DEFERRED`（需真实指标/环境/授权）、`CLOSED`（机制已落地）和 `ACCEPTED`（有意取舍）。repository-actionable OPEN 的当前列表与关闭条件以 [`services/docs/SERVICES_ISSUES.md`](../services/docs/SERVICES_ISSUES.md) 为准；SVC-006–010 的外部触发条件仍需真实环境。不要把删除临时记录、disposable 通过或文档更新宣称为生产问题已解决。

### 参考 runbook

- [Contract compatibility](../services/docs/CONTRACT_COMPAT_GATE.md)
- [Graceful drain](../services/docs/GRACEFUL_DRAIN_RUNBOOK.md)
- [Fenced leases](../services/docs/FENCED_LEASE_RUNBOOK.md)
- [Observability](../services/docs/OBSERVABILITY_RUNBOOK.md)
- [Worker SLO](../services/docs/WORKER_SLO_RUNBOOK.md)
