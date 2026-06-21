---
title: Virtual Contest（虚拟比赛）
tags: [concept, contest, virtual, rating]
status: living
updated: 2026-06-21
owner: contest
aliases: [虚拟比赛, virtual contest]
sources:
  - backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestSchedulerServiceImpl.java
  - backend-spring/src/test/java/com/ulticode/modules/contest/service/impl/ContestSchedulerServiceImplVirtualSessionTest.java
  - console/src/views/contest/components/VirtualContestTimer.vue
  - init-db/migrations/V20260617140000__Contest_Real_Unique_And_Session_Length.sql
---

# Virtual Contest（虚拟比赛）

> 选手在正式比赛结束后，仍能以「个人计时 session」重打同一套题，**成绩与 rating 独立于正式赛**。解决「错过比赛开始时间」的体验问题，同时不污染正式排名。

## 个人 session 隔离

`ContestSchedulerService`（见 [[contest]]）为虚拟参赛者维护**独立的开始/结束时间**——选手点「虚拟参加」那一刻起算，按比赛原始时长计时（迁移 `V20260617140000__Contest_Real_Unique_And_Session_Length` 约束 session 时长）。前端 `console/.../VirtualContestTimer.vue` 展示这个个人倒计时。

- 正式赛选手共享一个窗口；虚拟选手每人一个窗口。
- `ContestSchedulerServiceImplVirtualSessionTest` 锁定 session 边界行为。

## Rating 隔离（`isRated` 门控）

只有**正式**且 `isRated` 的比赛结果才计入全局 rating / 段位（`RatingCalculationService`，见 [[contest]]）。虚拟参赛的成绩**不计入** `GlobalRanking` 的 rating——否则选手可以反复刷分。这是「体验」与「公平」的边界。

## Cancel / Replay

虚拟比赛支持取消与重打（个人 session 可重建），不像正式赛 `CANCELLED` 后不可逆。对应状态机走 `ContestParticipantStatus`（`REGISTERED/STARTED/FINISHED/DISQUALIFIED`）。

## 关联

- 比赛全景 → [[contest]]
- 比赛提交复用判题 → [[submission]]
- 评分模式（SCORE/ICPC/IOI）→ [[contest]] 枚举
