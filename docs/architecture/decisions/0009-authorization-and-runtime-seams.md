# ADR-0009：权限与执行 Seam 收敛

- 状态：Accepted（仓库实现）；Core + Judge 默认切换 Deferred
- 日期：2026-09-03

## Context

Admin 旧权限写入把 Auth 的角色权限和 direct 权限合成扁平集合，再通过全量替换写回。该 Interface 丢失 direct/role provenance 与 `expiresAt`，并可能删除角色继承权限。代码执行 Interface 同时承担 App caller port、Judge wire contract 和 Judge runtime implementation slot。

## Decision

1. Auth 以 `AuthorizationMutationService.mutatePermission(PermissionMutationCommand)` 暴露单条 direct delta。Auth-owned Implementation 在一个 receipt 事务内完成 actor trust、账号/version 校验、direct row grant/revoke、`authz_version` CAS、审计 outbox 和幂等回放。角色权限只读，revoke 不删除角色来源；角色编辑使用独立 `RoleMutationService.changeRole(ChangeRoleCommand)`。
2. App 的 `/run` 只依赖 App-private `InteractiveCodeRunner`。App Adapter 将 HTTP DTO 映射到 Judge-owned `JudgeRunService`；Judge runtime 的执行 Implementation 只使用 runtime-private request/result model 和内部 `SandboxExecutor`。Judge0 如试点只能位于 `SandboxExecutor` 后方。
3. App-only interface 归还其逻辑 Module 的私有 Seam；`app-api` 只保留真实跨 Owner contract。删除测试、catalog 和 shell gate 共同锁定所有权。
4. 七进程 distributed profile 仍是默认 reference 与回滚路径。`services/core`
   已提供 opt-in parent，用于显式 Owner assembly、五组数据源/事务和边界
   验证；但 local Adapter parity、同进程业务路由与完整 disposable journey
   尚未完成，因此不切换默认。Core 的性能、成本、HA 和 blast radius 仍未被
   本 ADR 证明。

## Consequences

- 权限 mutation 的 Interface 更深：调用方只表达一个 delta，复杂的 CAS、幂等、审计和事件可靠性集中在 Auth Module，获得更高 Leverage 与 Locality。
- App/ Judge 的 Seam 所有权清晰，但 Judge API 变更需要 provider-first 的兼容发布；运行 verdict 仍是结果，不应伪装成 transport error。
- App-private interface 的移动会触发 Maven/测试 import 更新；不保留 alias，避免伪造跨进程 Leverage。
- Core 的性能、成本、HA 和 blast radius 未被本 ADR 证明；不得将设计目标写成生产事实。

## Rollback

权限与 contract rollback 使用部署方保留的上一份完整 release descriptor，并保持 outbox、receipt、PEL 和 schema 不降级。Core 若未来试点失败，先停止新 local adapters/consumers，再切回七进程 descriptor；禁止恢复已删除的全量权限 writer 或 App Docker fallback。

## Evidence

- Auth command/workflow/provider：`services/api/auth-api`、`services/auth/authorization`、`services/auth/dubbo/provider`
- App/Judge contract：`services/api/judge-api`、`services/app/app-web/.../InteractiveCodeRunner`、`services/judge-runtime/.../runtime`
- Ownership gate：`scripts/test/api-contract-boundary-contract.sh`
- 当前状态与外部触发条件：`docs/project/current-status.md`、`services/docs/SERVICES_ISSUES.md`
