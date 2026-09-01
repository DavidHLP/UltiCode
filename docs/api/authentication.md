# 认证 API

## Cookie 流程

1. `POST /auth/login`、`/auth/register` 或 OAuth callback 在 Auth 本地验证 account/credential/state。
2. Auth 写入 hash-only refresh session，并返回 HttpOnly `access_token`、`refresh_token` 和可读 `csrf_token`。
3. 浏览器 mutation、`POST /auth/refresh` 和 logout 同时提交 `X-CSRF-Token` 与 `csrf_token` cookie。
4. refresh 只接受 refresh cookie；Auth 条件 revoke 旧 hash 并插入新 session。access token 不能充当 refresh credential。

Cookie 的 `Secure`、`SameSite`、`Path`、`Domain` 和生命周期在签发/删除时一致。Secure 默认开启，只有完全 local 的 `dev`、`test`、`ci` profile 可显式关闭。

## JWT/JWKS

生产 access token 由 Auth 以 RS256 签发，资源服务通过 JWKS/X509 公钥本地验证。接收方固定 algorithm allowlist，并校验：

```text
iss, aud, sub, iat, nbf, exp, jti, typ=at+jwt, sid, roles/authorities, authz_version
```

Auth、App、Admin、Notification 不共享签名私钥。JWKS key rotation 使用 `kid` 和重叠公钥窗口；JWKS 不可用时只允许明确、有界的 stale-key 行为，fresh authorization 失败则 fail closed。

## CSRF 与授权

共享 `CookieCsrfFilter` 覆盖所有 Cookie-authenticated unsafe methods，包括 refresh/logout，即使 access token 无效也必须校验 CSRF。Bearer/mTLS service-to-service 入口与浏览器 CSRF 分离。未匹配 route 默认拒绝；Admin `/admin/**` 和敏感方法要求 `ADMIN` 或 `SUPER_ADMIN`。

## WebSocket

WebSocket CONNECT 只从 handshake 的 `access_token` cookie 取 token，不接受 query、URL 或客户端 STOMP token。统一 JWT validator 检查 token 与 account active/ban；principal/session 缺失、revoked/expired 或状态不可确认时拒绝 CONNECT/SEND/SUBSCRIBE。

## 委托身份

Admin 调用 Owner command 使用独立 RS256 delegation assertion，绑定 target audience、actor、subject、`kid`、`jti` 和短 deadline，Owner 在 durable receipt 或写入前验证并用 Redis replay claim 防重放。Dubbo mTLS 的 transport SAN 是 service identity，不能由 invocation attachment、`remote.application` 或业务 DTO 替代。

## 吊销边界

当前不提供 access-token 即时 blacklist writer。TTL 为 15 分钟；refresh revoke、HTTP ban check、WebSocket account-state 检查与 `authz_version` 事件限定窗口。`TokenBlacklistPort` 只读。若未来要立即踢出全部会话，先增加 Auth-owned revoke contract、事件重试、旧 token 检验、回滚和成本定义。

实现与安全规则：[`../architecture/security.md`](../architecture/security.md)；服务问题与触发条件：[`../../services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)。
