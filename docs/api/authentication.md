# 认证 API

本文只描述浏览器认证请求的顺序；Cookie、JWT/JWKS、CSRF、WebSocket、委托身份和吊销边界的唯一权威说明是[安全架构](../architecture/security.md)。

## Cookie 流程

1. `POST /auth/login`、`POST /auth/register` 或 OAuth callback 在 Auth 本地验证 account、credential 和 state。
2. Auth 创建 hash-only refresh session，并返回 HttpOnly `access_token`、`refresh_token` 与可读 `csrf_token`。
3. 浏览器调用其他 mutation、`POST /auth/refresh` 或 logout 时，按[安全架构](../architecture/security.md)提交 Cookie 与 CSRF header。
4. refresh 只使用 refresh cookie；Auth 条件 revoke 旧 session 并插入新 session。

## 服务入口

- Auth HTTP owner：`/auth/**`。
- 浏览器通常经前端 Nginx 的 `/api/auth/**` 访问 Auth；不要把内部服务端口当作公网 API。
- WebSocket 使用 App 的 `/ws/**` relay；其握手认证规则见[安全架构](../architecture/security.md)。

## 相关契约

- [API 与内部契约](overview.md)
- [安全架构与信任边界](../architecture/security.md)
- [Services issue registry](../../services/docs/SERVICES_ISSUES.md)
