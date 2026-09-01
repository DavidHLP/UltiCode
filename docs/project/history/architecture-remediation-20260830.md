# Architecture remediation 2026-08-30 — closure

更新：2026-09-01

## 背景与目标

2026-08-30 用户指令授权在仓库范围内执行微服务架构整改（repository-cutover authority，非生产 authority）：收敛 Owner 边界、消除 Submission/Notification 双写与跨 Owner 权限、加固安全与控制面，并用可执行门禁防止回归。整改任务已于 2026-09-01 全部关闭；原 `.auto-flow/` 运行台账与证据随本次文档治理一并退役，保留在本分支 Git 历史。

## 完成的主要变化

- **P0 安全**（P0-SEC-001..008、ARCH-SEC-001）：完整 cookie 策略、共享 stateless double-submit CSRF、无 broad permit-all 的 fail-closed 路由、统一 JWT/JWKS 校验、RS256 非对称委托（`kid`/audience/一次性 `jti`）、Redis ACL 拒绝默认、SSH known_hosts 强制、Nacos 租户隔离与内置账号禁用；委托验证器统一到 `platform/web-security`。
- **P1 Owner 收敛**（P1-SUB-001..004、P1-NOT-001、P1-DATA-001、P1-AUDIT-001、P1-SEAM-001）：Submission/Notification 单写者；Admin rejudge 经 owner 路由；可恢复 backfill 与确认门控 cutover；Owner facts 读与 reconciliation；owner-local audit outbox + Admin 幂等 Inbox；死 Contract/Provider 清理。
- **P2 发布控制面**（P2-MIG/BACKUP/REDIS/TLS/SC/OBS/DEPLOY-001）：有序 owner migration manifest 与 post-owner 授权清理；外部加密备份/恢复 drill；运行时 ACL materialization 与重叠轮换；生产 HTTPS/HSTS profile；不可变签名镜像供应链（digest manifest + Cosign/Trivy/SBOM）；可运行 observability overlay；部署前 integrity 预检与原子 deployment descriptor。
- **P3 韧性**（P3-SCHED/LEASE/GRACE/RES/STREAM/SCALE/HA/IDENTITY/NET/JUDGE-001）：owner-local 调度器隔离；DB-clock fenced lease；`DrainGate` 优雅排空；`DependencyGuard` 熔断/舱壁；Streams 信封校验、poison 与 `D:T` tombstone；去除固定 `container_name`；HA reference profile；Dubbo workload mTLS；Compose 网络分段；Judge 生产无 Docker socket 边界。
- **门禁**（ARCH-CONTRACT-001、ARCH-DUBBO-001、TEST-COV-001、REVIEW-001/002、CLOSURE-001）：四模块 Contract 兼容门禁、provider/reference 清单、JaCoCo/V8 阈值、两轮评审与 closure 检查。

## 保留的外部边界（不属仓库内继续修复）

生产采用所需的环境、授权和证据由未来部署方负责；外部边界与触发条件统一由 Services registry 维护，本 closure 不重复维护问题状态。详见 [`services/docs/SERVICES_ISSUES.md`](../../../services/docs/SERVICES_ISSUES.md) 与 [`docs/project/known-issues.md`](../known-issues.md)。

## 验证入口

- 仓库级：`./scripts/dev/test.sh quick|full|integration`
- 架构/文档/迁移/Compose 契约与 coverage 门禁：`scripts/dev/` 对应契约脚本与 Maven JaCoCo verify
- 领域 runbook：`services/docs/`（migration、backup、ACL rotation、deploy、worker SLO 等）

## 关键提交与阶段范围

基线 `main@8b4012b3`；分支 `fix/architecture-remediation`；实现 checkpoint `1e69b5b5`（2.0.0 N-1 退役）、flow checkpoint `cef925a` / `fc31cf35`。

| 领域 | 提交 |
| --- | --- |
| P0-SEC-001..008 | `ef10d92c`、`8f061dfd`、`2974c288`、`828a941`、`05285c5`、`054b95e`、`8baac1c`、`b689e73` |
| P1-SUB/NOT/DATA/AUDIT/SEAM | `d4a493b9`、`3a8f931`、`73d9f78`、`8a521d7`、`a292367`(+`0ff5a53`)、`0aa0569`、`f223b88`、`efc12eb` |
| P2-MIG/BACKUP/REDIS/TLS/SC/OBS/DEPLOY | `3f204c1`、`c1ef9d0`、`a5a0008`、`4423fae`、`e15c34c`、`bb01971`、`80326f1`、`7320923`、`60784a5` |
| P3-SCHED/LEASE/GRACE/RES/STREAM/SCALE/HA/IDENTITY/NET/JUDGE | `5a578a7`、`d5f9866`、`aa42a66`、`e666ab5`、`4ebb418`(+`614d90f`)、`7833227`、`9b7c628`、`8f190a7`、`51efd26`、`3aef022`(+`0781f5f`) |
| 门禁与 closure | `7743d88f`、`bfd1919`、`ef74edc`、`1e69b5b5`、`cef925a` |

完整 Finding→task→commit 映射见 [`docs/archive/architecture-remediation-2026-08/remediation-traceability.md`](../../archive/architecture-remediation-2026-08/remediation-traceability.md)。

## 已知但不属于仓库内继续修复

- 本开源项目没有生产环境；disposable Compose/DinD 验证不等于 production applied，任何历史 green 结果只证明当时那次运行。

## 权威入口

- 当前状态：[`docs/project/current-status.md`](../current-status.md)
- 问题注册表：[`services/docs/SERVICES_ISSUES.md`](../../../services/docs/SERVICES_ISSUES.md)
- 决策：ADR [`0001`](../../architecture/decisions/0001-deferred-platform-expansion.md)–[`0006`](../../architecture/decisions/0006-existing-control-planes.md)
