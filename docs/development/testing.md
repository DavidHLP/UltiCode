# 测试与质量

## 统一入口

```bash
./scripts/dev/test.sh quick
./scripts/dev/test.sh full
./scripts/dev/test.sh integration
```

`quick` 覆盖后端 verify/JaCoCo、共享包、Console/Management coverage 与类型检查；`full` 追加前端构建、i18n 和依赖审计；`integration` 追加 Testcontainers、数据库/Redis/Sandbox 相关集成测试。具体阶段由 `scripts/dev/test.sh` 实现，不在本页复制脚本内部逻辑。

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
