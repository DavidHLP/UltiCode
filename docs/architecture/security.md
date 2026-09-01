# 安全架构与信任边界

## HTTP 认证与授权

- `platform/web-security` 统一 JWT/JWKS、Cookie CSRF、委托断言、公钥加载和 replay guard；Auth、Admin、App、Notification 只保留 owner 路由策略。
- 未匹配 HTTP 路径默认拒绝。Admin 的 `/admin/**` 与 privileged methods 同时要求 `ADMIN` 或 `SUPER_ADMIN`；Moderation 的角色门禁单独声明。
- Gateway 负责路由、TLS、header 清理和基础限流，不是唯一安全边界。各 Owner 本地验证 JWT 并重建 principal。
- refresh 只接受 refresh HttpOnly cookie；access token 不能作为 refresh credential。refresh token 只存数据库 hash，并通过条件 revoke/rotate。

## Cookie 与 CSRF

Access/refresh token 保持 HttpOnly；Cookie 的 Secure、SameSite、Path、Domain 和 lifetime 在签发与删除时一致。`Secure=false` 只能由完全 local 的 `dev`、`test`、`ci` profile 显式启用，生产启动 fail closed。

所有 Cookie-authenticated unsafe methods（包括 refresh/logout）经过无服务端状态的 double-submit CSRF：header 与 `csrf_token` cookie 使用恒定时间比较。Bearer-only service-to-service 请求与 safe methods 不进入浏览器 CSRF filter。

## JWT、JWKS 与委托身份

生产 access token 使用 Auth 签发的 RS256/JWKS；资源服务只持 X509 公钥，不共享 Auth 私钥或签名 secret。接收方校验 `iss`、`aud`、`typ`、`kid`、`iat`、`nbf`、`exp`、签名和 authority。短期 access token 本地验证，Auth 不可用时普通请求仍可用未过期、未撤销的 token；登录、refresh、fresh authorization 和缺少可信状态的 WebSocket CONNECT fail closed。

Admin→Owner 的高风险 command 使用独立 RS256 delegation assertion，绑定 issuer、target audience、actor、subject、`kid`、`jti`、deadline，并以 Redis 一次性 claim 防 replay。Dubbo transport identity 与 end-user delegation 分开：生产 Triple 使用每服务 mTLS certificate，Provider 根据 TLS peer SAN 和 caller matrix 授权；attachment、`remote.application` 和请求 DTO 都不是身份来源。

## WebSocket

WebSocket 只从 handshake 的 `access_token` cookie 获取 token，不接受 query、URL 或客户端 STOMP token。CONNECT 使用统一 JWT validator，并检查 active/ban；SEND/SUBSCRIBE 在 principal/session 缺失时 fail closed。Notification 通过 Redis Pub/Sub 发布允许的 payload，App 保留 STOMP/SockJS relay。

## 访问令牌吊销边界

当前不提供即时 access-token blacklist writer。access token TTL 为 15 分钟，故既有有效 token 的最大残余窗口为 15 分钟。刷新 token revoke、HTTP ban check、WebSocket 实时 account-state 检查和 `authz_version` 事件共同限定窗口；`TokenBlacklistPort` 保持只读，不能在 App、Admin 或共享工具新增第二个 writer。

若产品未来需要“立即踢出全部会话”，先由 Auth 定义 writer-owned revoke contract、事件 identity、delivery/retry、旧 token 校验、回滚和 request-time 成本，再实施。

## Redis、部署与敏感信息

Redis ACL deny-by-default，按 Owner 限制命令、key 和 channel；运行时 ACL 在 ignored directory 原子物化，轮换使用 overlap/finalize/rollback。生产 Compose 不挂载 Docker socket；Judge 使用部署拥有的 remote/rootless Docker TLS endpoint、只读证书和共享 workspace。真实证书、私钥、密码、token、生产 endpoint 和 secret-store 状态不得提交或写入文档。

## 证据入口

- [Trust-boundary rules](../../.omp/rules/trust-boundaries-path.md)
- [Services issue registry](../../services/docs/SERVICES_ISSUES.md)
- [Dubbo mTLS 与依赖策略](../../services/docs/DEPENDENCY_RESILIENCE_RUNBOOK.md)
