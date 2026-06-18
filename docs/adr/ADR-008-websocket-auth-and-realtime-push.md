# ADR-008: WebSocket auth + realtime push (F-04 / F-13 / F-17 / F-18)

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (R6.4 / R6.5 已实施，2026-06-17) |
| **日期 (Date)** | 2026-06-17 |
| **作者 (Author)** | DavidHLP |
| **来源** | [REVIEW_V3.md](../contest/REVIEW_V3.md) §3 P0 / F-04 / F-13 / F-17 / F-18 |
| **执行计划** | [_archive/EXECUTION_PLAN_R6 Round 6.4](../contest/_archive/EXECUTION_PLAN_R6_2026-06-17.md#round-64--ws-全栈f-04--f-13--f-17--f-18) |
| **关联代码** | `websocket/interceptor/ContestSubscribeAuthInterceptor.java` (R6.4 新增), `websocket/config/WebSocketChannelConfig.java` (interceptor 链), `console/src/composables/contest/useContestSocket.ts` (前端 socket), `console/src/views/contest/ContestRankingsView.vue` (R6.4 接入), `console/src/views/contest/components/VirtualContestTimer.vue` (R6.4 F-13) |

---

## 1. Context

PRD §1.3 把 F-04 (`useContestSocket.joinContest` 零调用)、F-17 (WS join 不校验注册)、F-18 (WS 卸载即丢回调) 列为 CRITICAL/HIGH。F-13 (客户端计时器 visibilitychange) 是 HIGH。

R1-R5 没碰 WS 路径。本 ADR 一次性收口 F-04 / F-13 / F-17 / F-18 四个 finding。

## 2. Decision

### 2.1 F-17 SUBSCRIBE-frame authorization

新增 `ContestSubscribeAuthInterceptor`（R6.4），在 `JwtChannelInterceptor` 之后运行（保证 session 有 user）。`preSend` 拦截 `StompCommand.SUBSCRIBE`，解析 destination：

- `/topic/contest/{id}` 或 `/topic/contest/{id}/...` → 检查 `contest_participants` 是否存在 (contest_id, user_id) 行（任何 status：REGISTERED / STARTED / FINISHED，包括 is_virtual=1 的虚拟会话）
- 不存在 → `throw new WebSocketAuthenticationException(ErrorCode.FORBIDDEN, ...)`
- 存在 → 放行

Spring STOMP adapter 把异常映射为 STOMP ERROR frame，client 收到错误后断连。

**为什么用 channel interceptor 而不是 message handler**：在消息到达 `@MessageMapping` 之前拦截，避免不必要的业务方法调用；同时统一错误格式。

### 2.2 F-04 前端 wire-up

`ContestRankingsView` 是 R6 第一个接入 `useContestSocket` 的 view（live ranking 推送）。其他 views（`ContestDetailView` / `ContestHomeView`）可增量接入。

### 2.3 F-18 unmount cleanup

`ContestRankingsView.onUnmounted` 调用 `unsubscribeRanking` + `leaveContest`：
- `unsubscribeRanking` 清掉 store listener
- `leaveContest` 通知服务端 unsubscribe；服务端 `ContestRoomManager` 引用计数递减，0 时关闭 STOMP session

`VirtualContestTimer.onUnmounted` 同时 `removeEventListener("visibilitychange")`（F-13）。

### 2.4 F-13 visibilitychange

`VirtualContestTimer` 监听 `document.visibilitychange`：
- 切到 hidden：记录 `pausedAt = Date.now()`
- 切回 visible：若虚拟 deadline 已过 → `finishVirtualContest`（best-effort）；否则**不**修改 endsAt（避免与 Pinia store 写入冲突）
- 服务端 R6.2 / F-07 是硬截止：用户切后台超时的提交会被 409 拒绝

## 3. Consequences

### 3.1 Positive

- 未报名用户 STOMP 订阅被服务端拒绝（安全）
- Live ranking 推送接通（用户体验）
- 组件 unmount 清理（无连接泄漏）
- 后台标签页不污染虚拟赛时间（虽然 endsAt 不动，但用户知道已超时）

### 3.2 Negative

- F-13 endsAt 不调整：用户切后台回来看到的"剩余时间"是真实剩余时间（含后台时长），可能看起来"突然变少"。**R7 候选**：在 store 暴露 writable setter 让 view 修改 endsAt
- 增量接入：ContestDetailView / ContestHomeView 暂未接 R6.4 改造，R7 一并接入

## 4. Validation

- [x] `useContestSocket` 在 `ContestRankingsView` mount 时调 `joinContest`，unmount 时调 `leaveContest`
- [x] `ContestSubscribeAuthInterceptor` 在 `WebSocketChannelConfig.configureClientInboundChannel` 注册
- [x] `VirtualContestTimer` 监听 `visibilitychange` + `onUnmounted` 清监听
- [x] 单元测试 `useContestSocket.spec.ts` 已有（pre-existing）

## 5. References

- [REVIEW_V3.md](../contest/REVIEW_V3.md) §3 F-04 / F-13 / F-17 / F-18
- [_archive/EXECUTION_PLAN_R6 Round 6.4](../contest/_archive/EXECUTION_PLAN_R6_2026-06-17.md)
- [CLAUDE.md §Security Invariants](../../CLAUDE.md) — WebSocket 鉴权**只接受** `access_token` cookie
- ADR-007 §6 — R3.3 FOR UPDATE 实施偏差
