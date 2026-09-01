# 配置与环境变量

## 来源与边界

`.env` 是本地部署密管输入，由 `./scripts/dev/init-env.sh` 生成并被 Git 忽略；`.env.example` 与 `.env.test.example` 是可提交模板，不包含可用凭据。实现配置以各服务 `application.yml`、Compose 和 `ecosystem.config.cjs` 为准。

## 常用变量

| 变量 | 用途 | 规则 |
| --- | --- | --- |
| `DB_HOST/PORT/USER/PASSWORD/NAME` | 兼容基础 MySQL 连接 | 仅作为明确 owner 配置未提供时的 dev fallback |
| `AUTH_*`、`ADMIN_*`、`APP_*`、`SUBMISSION_*`、`NOTIFICATION_*` | Owner 数据库连接 | 生产必须显式提供 owner host、端口、库、用户和密码 |
| `REDIS_HOST/PORT/USERNAME/PASSWORD` | Redis 连接 | ACL principal 按 Owner 分开，命令/key/channel 受限 |
| `JWT_SECRET` | 仅 local compatibility profile 的 HMAC secret | 至少 32 字符；生产 access token 使用 RS256/JWKS |
| `NACOS_SERVER_ADDR` / `NACOS_SERVERS` | 服务发现 | dev 使用显式 standalone；prod/HA 使用 cluster peer list |
| `NACOS_USERNAME/PASSWORD` 及 `*_NACOS_*` | Nacos workload identity | 每个 workload 使用独立、namespace-scoped 账号；内置账号禁用 |
| `DUBBO_NAMESPACE` | Dubbo 环境隔离 | prod 必须非空，不能静默回退到 dev |
| `CORS_ALLOWED_ORIGINS` / `FRONTEND_URL` | 浏览器来源与链接 | 生产使用部署域名，不能使用 wildcard |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `Secure=false` 只允许全 local profile |
| `JUDGE_DOCKER_HOST` / `JUDGE_DOCKER_CERT_DIR` / `SANDBOX_HOST_DIR` | 生产 Judge remote/rootless Docker TLS | 证书由外部 secret store 提供；不提交到 Git |
| `REDIS_ACL_DIR` | 运行时 ACL 目录 | ignored、原子物化；不把 hash snapshot 放入 Git |
| `TLS_CERT_DIR` | 前端生产 TLS 证书挂载 | secret mount；开发 HTTP 不需要 |

Owner migration 与生产 Compose 还要求 `SUBMISSION_CUTOVER_COMPLETE=true`、独立 migration principal、`DUBBO_MTLS_CERT_DIR` 和外部 OTLP collector 等变量。缺失必需变量时应 fail closed，而不是生成默认凭据。

## 安全规则

- 不在源码、文档、日志、命令输出或提交中打印 password、token、private key、certificate 内容。
- Access/refresh token 使用 HttpOnly cookie；refresh token 只存 hash。
- `JWT_COOKIE_SECURE=false` 只能在明确的 `dev`、`test`、`ci` profile；生产启动拒绝混合绕过。
- 生产 Compose 不发布 MySQL、Redis、Nacos 或 backend 端口；开发仅 loopback。
- 改 `.env` 后通过 `./scripts/dev/up.sh --mode dev-lite --skip-install` 重新加载，不直接绕过 manifest 启动服务。

完整可用字段见 [`.env.example`](../../.env.example) 和 [本地开发](local-setup.md)；不要在本文复制模板的全部变量。
