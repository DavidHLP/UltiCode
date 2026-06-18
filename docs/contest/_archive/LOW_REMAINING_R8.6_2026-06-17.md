# LOW (F-35~F-47) 收口状态

> **作用**：R8.6 落地后，12 项 LOW（F-35~F-47）的最终状态。
> **创建**：2026-06-17
> **来源**：[EXECUTION_PLAN_R8.md §7](../_archive/EXECUTION_PLAN_R8_2026-06-17.md)

---

## 收口表

| # | Finding | 状态 | 落地位置 | 备注 |
|---|---------|------|----------|------|
| F-35 | 决策类 | ✅ | `ADR-010-cancel-state-and-virtual-replay-boundary.md` | FINISHED 边界已记录 |
| F-36 | 虚拟赛元数据 | ✅ | `VirtualContestSessionVO` 字段补全（见 R8.6 commit）| startedAt/isReplay/originalContestStatus |
| F-37 | 虚拟赛重放历史 | ✅ | `VirtualContestHistoryVO` 新增 | `/api/contest/virtual/history` 端点 + `MyContests.vue` "我的虚拟赛" tab |
| F-38 | 决策类 | ✅ | `ADR-010` | CANCELLED 不允许开虚拟 |
| F-39 | TS null vs undefined | ✅ | contest store strict null check | vue-tsc --noEmit 通过 |
| F-40 | loading/空态 i18n | ✅ | `i18n/locales/{en,zh}/modules/contest.ts` | 新增 `contest.*.empty/loading` 系列 |
| F-41 | 错误页 i18n | ✅ | `i18n/locales/{en,zh}/modules/contest.ts` | 新增 `contest.*.error.*` 系列 |
| F-42 | 倒计时刷新 | ✅ | 文档化于 `docs/contest/CONTEXT.md` 备注 | R6.4 + R7.4 + R8.4 已统一 |
| F-43 | WS 鉴权失败 | ✅ | R7.3 `rejected` 事件通道 + toast | R6.4 `ContestSubscribeAuthInterceptor` + R7.3 UI |
| F-44 | WS 心跳 | ✅ | R7.3 `connectionStatus === "reconnecting"` 状态机 | 心跳超时走 reconnecting |
| F-45 | 文档归档 | ✅ | 本文件 | LOW_REMAINING.md |
| F-46 | 多 tab 互锁 UX | ✅ | R3.3 后端 FOR UPDATE + R8.6 i18n 文案 | "您已在另一个标签页开始虚拟赛" |
| F-47 | WS reconnect UX | ✅ | R7.3 + R8.4 banner | "网络不稳定，正在重连..." |

---

## R9 候选（剩余微优化）

| # | 项 | 估时 |
|---|----|------|
| 1 | F-40 i18n key 扩展（更多边界 case） | 0.5 人日 |
| 2 | F-39 strict null check 全 codebase 跑一次 | 0.5 人日 |
| 3 | F-46 multi-tab 检测加 localStorage 跨标签广播 | 1 人日 |

---

## 与 R8 部署同步

R8 完成后，12 项 LOW 全部 ✅。模块 v4.1 完结。

> 本文件归档于 `docs/contest/completed/`，与 R6/R7/R8 plans 一起作为历史执行证据。
