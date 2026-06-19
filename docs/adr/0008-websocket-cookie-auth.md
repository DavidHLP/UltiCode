---
title: WebSocket 鉴权（仅 cookie，禁 query token）
tags: [adr, security, websocket, auth]
status: accepted
updated: 2026-06-19
date: 2026-06-xx
deciders: security-reviewer, backend
supersedes: N/A
superseded_by: N/A
---

# 0008 — WebSocket 鉴权（仅 cookie，禁 query token）

## 背景

原版 WebSocket 鉴权接受 query string 里的 `?token=<jwt>`，一个 `<script>` 元素或 `<img>` 就能通过 referer header、服务器访问日志或浏览器历史外泄。2026 Q1 由 `security-reviewer` 提出 CVE 风格的泄露。

## 决策

WebSocket（STOMP）鉴权**只**接受 `access_token` cookie：

- `WebSocketAuthInterceptor` 读 cookie，不读 query string
- STOMP CONNECT 帧里的 `Authorization` 头被忽略
- CSRF 守卫**不**适用于 STOMP（CSRF 是请求表单的关注点）
- 令牌刷新后重连复用同一个 cookie（无需手动重新认证）

## 备选方案

1. **`Sec-WebSocket-Protocol` 子协议 token** — 拒绝：照样会泄露到日志和 `Origin` 头
2. **每个 session 一次性预共享密钥** — 拒绝：运维开销；同样的暴露窗口
3. **为老客户端留一个 feature flag 允许 query token** — 拒绝："临时"flag 永远是临时的；直接拒绝这一类

## 影响

**正面** — 令牌永远不出现在 URL、服务器访问日志或 referer header 里。Cookie 的 `HttpOnly` + `Secure` 标志给它与普通 API 调用同等的保护。

**负面** — 非浏览器客户端（CLI 工具、集成测试）必须把 cookie 存到 jar 里，而不能简单地在 URL 后追加 `?token=…`。`scripts/arthas-cli.sh` 已经遵循这一模式。

**运维影响** — STOMP CONNECT 返回 401 时，原因要么是 `access_token` 过期（通过 `/auth/refresh` 刷新），要么是 cookie 缺失（CORS 配置错）。绝不是 query string 解析错误。

## 参考

- **代码**：`backend-spring/.../websocket/WebSocketAuthInterceptor.java`
- **RUNBOOK**：[`RUNBOOK.md`](../RUNBOOK.md) §4（CSRF 机制）
- **相关 ADR**：无
