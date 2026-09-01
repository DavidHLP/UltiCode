# 部署、发布与回滚

## 当前边界

本仓库没有生产环境。生产 Compose、CI/CD、远程 SSH、证书、registry、数据库和流量切换均是可执行但需部署方授权的控制面；disposable Compose/DinD 只证明仓库实现，不构成生产应用证据。

## 开发部署

开发统一使用：

```bash
./scripts/dev/up.sh --mode dev-lite
./scripts/dev/up.sh --mode dev-full
```

`docker-compose.yml` 是基础配置；`docker-compose.dev.yml` 只在 loopback 暴露开发端口；`docker-compose.prod.yml` 不发布 MySQL、Redis、Nacos 或 backend 端口，前端仅作 HTTPS edge。不要直接用 PM2/Maven 启动 owner runtime，以免绕过 manifest、migration、readiness 和 rollback gate。

## 生产发布前

`host-deploy` 在任何 migration、Redis ACL materialization、Judge sandbox provisioning 或 Compose mutation 前检查：

- approved source commit；
- canonical migration manifest checksum；
- 必需 Compose/runbook 文件；
- 九服务 immutable digest manifest；
- 合并生产 Compose 配置、TLS/JWKS、owner DB 和必要 secret inputs；
- registry digest、Cosign、SBOM、SLSA、Trivy 证据（若由部署策略要求）。

生产 Judge 需要 `JUDGE_DOCKER_HOST`、`DOCKER_TLS_VERIFY=1`、只读 client certificate、共享绝对 workspace、固定 seccomp 和 remote/rootless daemon。生产不挂载 `docker.sock`、不使用 `DOCKER_GID`；socket 仅在显式 disposable `docker-compose.judge-dev.yml --profile judge-socket` 下允许。

## 健康与回滚

发布后先写入 secret-free `PENDING_HEALTH` descriptor。`host-health` 必须逐项检查 allowlisted 服务、前端 HTTPS 和 worker readiness；任何失败都写 `FAILED` 并以非零退出，不能报告部分成功为系统成功。全部通过后才标记 `HEALTHY`。

回滚必须存在上一 descriptor，并且 source/schema checksum 与批准输入一致；继续使用 `skip_migrations=true`，不执行 schema downgrade。回滚前先停止新 writer、保留未消费 outbox/PEL/inbox，确认旧 artifact 与 schema/contract 兼容，再恢复 route。不能通过重新开放跨 Owner grant 或切回已删除 writer 回滚。

## CI/CD 入口

GitHub Actions 由 `.github/workflows/ci.yml` 编排，`ci-ok` 是稳定汇总门禁；服务矩阵在 `.github/services-matrix.json`，owner migration、backup、artifact integrity、health 和 rollback 由可复用 actions/runbooks 承接。完整触发条件以 workflow 为准，避免在文档复制易漂移的 job 列表。

- Contract 兼容：[`services/docs/CONTRACT_COMPAT_GATE.md`](../../services/docs/CONTRACT_COMPAT_GATE.md)
- Owner migration：[`database-migrations.md`](database-migrations.md)
- 监控与 release annotation：[`monitoring.md`](monitoring.md)
- 生产问题与外部触发：[`../../services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)

## HA 说明

`docker-compose.ha.yml` 的 `ha` profile 是可审计的 stateful reference，不是默认生产 failover：包含 `mysql-replica`、Redis replica/Sentinel、`nacos-2`/`nacos-3`，但 promotion、endpoint 变更、secret rotation、Sentinel-aware client 和 RPO/RTO 仍需 operator。**本仓库不承诺 active-active HA**。配置保留 `masteruser ulticode-replication`、`sentinel auth-user`、`sentinel auth-user mymaster ulticode-sentinel`、`P3-HA-001`、`mysql-replica` 和 `redis-sentinel-1` 等控制面契约。
