---
title: 0003 — Refresh Token 用 Hash-only DB 存储
tags: [decision, auth, security]
status: accepted
updated: 2026-06-21
deciders: security
sources:
  - init-db/migrations/V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql
  - backend-spring/src/main/java/com/ulticode/modules/refreshtoken/
  - backend-spring/src/main/java/com/ulticode/security/jwt/
---

# 0003 — Refresh Token 用 Hash-only DB 存储

## 背景（Context）

refresh token 是长期凭据，泄露即账号被盗。早期 `refresh_tokens` 表存了明文 `token` 列（便于按 token 查询），等于把所有长期凭据明文落库——一次 DB 泄露全部沦陷。同时文档化 seed 账号（`admin`/`alice_coder`…）的密码公开，是另一独立风险。

## 决策（Decision）

1. **只存 hash**：`DROP COLUMN token`，`token_hash varchar(64) NOT NULL` + `UNIQUE`，存 SHA-256（`DigestUtil.sha256Hex`）。明文令牌只在创建时返回客户端一次，**不可恢复**。
2. **DB 驱动的 issue/rotate/revoke**：`revokeIfActive` 用条件更新 `WHERE id=#{tokenId} AND is_revoked=0`（返回行数=竞态安全）；`validateAndRotate` 吊销旧 token + 发新。
3. **access + refresh 都在 HttpOnly cookie**；refresh 接口**不接收 access token**（防 access 泄露被用来无限续期）。
4. **锁死 12 个文档化 seed 账号**：密码改不可用 hash、`is_active=0`、`is_banned=1`、清 reset token；初始管理员只由 opt-in `AdminBootstrapRunner`（正常启动禁用）创建。

## 替代方案（Alternatives）

- **Redis 存 refresh token**：易过期但持久化弱、DB 泄露面已存在则不解决根问题。否决——DB hash-only 同时给可审计性。
- **加密存储（可逆）**：密钥泄露仍可恢复全部 token。否决——hash-only 不可恢复更安全。
- **只改存储不锁 seed 账号**：留下公开密码后门。否决——同迁移一并处理。

## 后果（Consequences）

- ✅ DB 泄露不直接暴露可用凭据（hash 不可逆）。
- ✅ 轮换/吊销原子、竞态安全。
- ✅ 关闭公开 seed 后门；dev-only bootstrap 隔离。
- ⚠️ 无法「按明文查 token」——所有查询改走 hash；`validateAndRotate` 客户端必须持有原明文。
- ⚠️ 既有用户全部需重新登录（`DELETE FROM refresh_tokens` 作废旧令牌）。

## 参考

- 实体 → [[refresh-token]]
- 安全不变式 → [`CLAUDE.md`](../../CLAUDE.md) Security Invariants
