# 测试与质量

## 统一入口

```bash
./scripts/dev/test.sh static       # 只读、零基础设施的结构门禁
./scripts/dev/test.sh unit         # static + 前端单测 + -Punit 后端门禁（deny 环境，无 *IT）
./scripts/dev/test.sh quick        # static + unit 兼容别名（已弃用，新代码请用全名）
./scripts/dev/test.sh full-local   # 容器 + 迁移 + Maven verify + 前端测试（原 quick 的重型语义）
./scripts/dev/test.sh full         # full-local + 前端构建、i18n 与依赖审计
./scripts/dev/test.sh integration  # full-local + Testcontainers/Sandbox/owner migration 安全演练
```

`static` 不调用 Docker、数据库、服务、Testcontainers、Maven verify 或 `pnpm install`，可用 deny-shim 自证（见 `scripts/test/zero-infra-validation-contract.sh`）；缺 shellcheck 等可选工具时跳过并提示。`full-local` 保留原 `quick` 的完整覆盖（MySQL/Redis、owner migration、Maven verify/JaCoCo、Console/Management coverage 与类型检查）。`full` 追加前端构建、i18n 与依赖审计；`integration` 追加 Testcontainers、数据库/Redis/Sandbox 相关集成测试。后端 `unit` 使用根 POM 的 `unit` profile（Surefire 排除 `*IT`/`*IntegrationTest` 含嵌套类，不传 `-Dtest` 选择器）；wrapper 剥离基础设施凭据并以 deny 环境实证（deny 运行 5786 测试零失败、零 Testcontainers、零 IT），零基础设施自证见 `scripts/test/zero-infra-validation-contract.sh`（含 unit deny 阶段）。具体阶段由 `scripts/dev/test.sh` 实现，不在本页复制脚本内部逻辑。

## Core 与 distributed 验证边界

| 层级 | 环境/副作用 | 能证明什么 | 明确不能证明 |
| --- | --- | --- | --- |
| `static` | 零基础设施、静态脚本与配置 | 依赖方向、allowlist、负向契约 | Owner 启动、数据库、消息、业务可用 |
| `unit` | deny 环境、`-Punit`、排除 `*IT` | 单元逻辑、fail-closed、生命周期交接 | 真实 Owner wiring、持久化、网络 |
| `core` | Core Maven parent/config/readiness smoke；Owner contexts disabled | Core parent、显式扫描、数据源工厂、readiness 语义 | enabled-owner bean graph、业务 HTTP/WS、数据库/Redis |
| enabled-owner wiring | disposable Owner artifacts + MySQL/Redis as required | 选定 Owner 的真实 child wiring 与 local Adapter 注入 | 完整业务旅程、生产 SLO/HA |
| distributed journey | disposable `app-journey` scope、seeded account/problem | login → Problem read → Bookmark write → ordinary-user denial | Core parity、Judge sandbox、生产流量 |
| Core journey | 当前不可执行；Core 只有 readiness HTTP | — | 任何业务 journey 或同构结论 |

第一条代表性业务旅程固定为：`POST /auth/login`、`GET /problems/{id}`、
`POST /bookmarks/quick`、普通用户 `POST /problems` 得到 403/typed denial。
Submission→Judge→result 不属于这条首旅程。Core 的 readiness smoke 不得代替
enabled-owner wiring 或业务 journey。

### 按路径选择验证

| 变更范围 | 必需验证 |
| --- | --- |
| Core registry/assembly/lifecycle/local Adapter | `static` + `core-profile-contract.sh` + Core `-Punit`；若改变 enabled wiring，再运行 disposable wiring |
| Owner business module/endpoint | 对应 Owner `-Punit`、contract gate；涉及真实 journey 时运行命名 distributed scope |
| shared contract/platform/security | 相关 contract gate + distributed Owner tests；Core 受影响时追加 Core contract/smoke |
| 无法分类或跨边界变更 | 采用保守路径：distributed backend/contract checks + Core contract/smoke，不能只跑 static |

上述映射是验证路由，不代表每层已在本工作区执行；命令结果必须分别记录
`PASS`、`FAIL`、`NOT_RUN` 或 `BLOCKED_EXTERNAL`。

## 按触碰面验证

| 触碰面 | 最小命令 |
| --- | --- |
| Java 后端 | `(cd services && ./mvnw verify -B)` |
| 后端集成 | `(cd services && ./mvnw -Dtest='*IT' test -B)` |
| Console | `pnpm --dir apps/console lint && pnpm --dir apps/console type-check && pnpm --dir apps/console test:coverage && pnpm --dir apps/console build` |
| Management | `pnpm --dir apps/management lint && pnpm --dir apps/management type-check && pnpm --dir apps/management test:coverage && pnpm --dir apps/management validate:i18n-keys && pnpm --dir apps/management build` |
| `packages/auth-core` | `pnpm --dir packages/auth-core test:coverage && pnpm --dir packages/auth-core type-check` |
| migration/Compose | `docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml config >/dev/null` 与 `git diff --check` |
| 架构/文档 | `bash scripts/dev/architecture-contract-test.sh` 与 `bash scripts/dev/docs-contract-test.sh` |

## 测试约定

- `*IT.java` 是集成测试；普通 Maven test 排除它们，显式 `-Dtest='*IT'` 或 `integration` 才运行。
- 测试行为、边界、错误语义、幂等、并发和安全不变量；不要用空测试或 `@Disabled` 代替验证。
- 后端使用 JUnit 5、Mockito、Testcontainers、JaCoCo；前端使用 Vitest/V8，关键用户路径使用 Playwright。
- 共享包或 contract 变更需验证两个前端（若被双方消费）以及 API shape/兼容性门禁。
- 覆盖率报告是 CI/本地产物，不等于生产流量覆盖；阈值由 POM、package 配置和 `scripts/test/coverage-contract.sh` 维护。

## 安全与迁移门禁

架构门禁同时检查 Owner 单写者、JWT/CSRF、Redis ACL、Nacos identity、Dubbo mTLS、Compose 网络、Judge sandbox、Streams replay、migration preflight、contract compatibility 和文档关键事实。迁移脚本默认 dry-run 或 preflight；任何 backfill、contraction、rollback、credential 或生产动作都必须显式确认并保留可审计输出。

## 验证结果语义

`Repository Implemented`、`Locally Validated`、`Staging Validated`、`Production Applied` 不可混用。本仓库没有生产环境；disposable Compose/DinD 只能证明仓库侧行为。当前 42 项 architecture remediation 已在仓库范围完成，生产部署、真实流量、证书、外部 telemetry、HA failover 与远程 Judge 仍由部署方验证。
