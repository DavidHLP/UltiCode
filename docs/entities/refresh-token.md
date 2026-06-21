---
title: Refresh Token（刷新令牌）
tags: [entity, auth, security]
status: living
updated: 2026-06-21
owner: auth
aliases: [刷新令牌, refresh token]
sources:
  - backend-spring/src/main/java/com/ulticode/modules/refreshtoken/
  - backend-spring/src/main/java/com/ulticode/security/jwt/
  - init-db/migrations/V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql
---

# Refresh Token（刷新令牌）

> 刷新令牌的存储与轮换模型：**hash-only DB**（永不存明文、不可恢复），issue / rotate / revoke 全走数据库条件更新。决策背景 [[0003-refresh-token-hash-only-storage]]。

## 实体（`refreshtoken/entity/RefreshToken.java`）

| 字段 | 说明 |
| --- | --- |
| `tokenHash` | **SHA-256 hash**（varchar 64）。DB 只存 hash，明文令牌只在创建时返回客户端一次 |
| `rotatedAt` | 上次轮换时间 |
| `is_revoked` | 是否已吊销（MyBatis 映射 `is_revoked` 列） |

## Mapper（`RefreshTokenMapper`）

关键方法是**条件更新** `revokeIfActive`：

```sql
UPDATE refresh_tokens
SET is_revoked = 1, rotated_at = CURRENT_TIMESTAMP(3)
WHERE id = #{tokenId} AND is_revoked = 0
```

返回受影响行数；`!= 1` 即已被吊销/轮换（竞态并发安全）。继承 `BaseMapper<RefreshToken>` 提供标准查询。

## Service（`RefreshTokenService`）

- `createToken` —— `DigestUtil.sha256Hex(token)` 算 hash 入库（测试断言「只存 hash，长度 64」）。
- `validateAndRotate` —— 校验有效则**吊销旧 token + 发新 token**（rotate）。
- `revokePresentedToken(token)` —— 按 hash 反查并吊销。
- `revokeToken(tokenId)` / `revokeAllUserTokens(userId)` —— 单个 / 全量吊销。

## 安全加固迁移（`V20260606130000`）

1. `DELETE FROM refresh_tokens` —— 作废所有 legacy 明文令牌。
2. `DROP COLUMN token`（明文）+ `MODIFY token_hash varchar(64) NOT NULL` + `ADD UNIQUE KEY refresh_tokens_token_hash_key`。
3. **锁死 12 个文档化 seed 账号**（`admin` / `admin_two` / `super_root` / `super_vp` / `mike_mod` / `nina_mod` / `alice_coder` / `bob_dev` / `carol_wu` / `david_chen` / `eva_zhang` / `frank_lee`）：密码改成不可用 hash、`is_active=0`、`is_banned=1`、清 reset token。

## JWT 与 Cookie（`security/jwt/`）

- `JwtTokenProvider` / `JwtAuthenticationFilter` / `JwtProperties` —— access token（jjwt 0.13.0）。
- **access + refresh 都在 HttpOnly cookie**。
- **refresh 接口不接收 access token**（防止 access 泄露被用来无限续期）。
- OAuth state 绑定 HttpOnly cookie，Redis 原子消费。

## 关联

- **为什么 hash-only** → [[0003-refresh-token-hash-only-storage]]
- **CSRF（与 cookie 认证配套）** → `security/csrf/`（双 token Redis 生命周期，待建专属页）
- **WebSocket 鉴权只认 access cookie** → 安全不变式（见 [`CLAUDE.md`](../../CLAUDE.md) Security Invariants）
