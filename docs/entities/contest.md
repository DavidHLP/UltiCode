---
title: Contest（比赛）
tags: [entity, contest, scoring, rating, virtual]
status: living
updated: 2026-06-21
owner: contest
aliases: [比赛, contest]
sources:
  - backend-spring/src/main/java/com/ulticode/modules/contest/entity/
  - backend-spring/src/main/java/com/ulticode/modules/contest/entity/enums/
  - backend-spring/src/main/java/com/ulticode/modules/contest/service/
  - init-db/migrations/V20260617120000__Contest_Scoring_Hardening.sql
  - init-db/migrations/V20260617130000__Contest_Slug_Unique.sql
  - init-db/migrations/V20260617140000__Contest_Real_Unique_And_Session_Length.sql
---

# Contest（比赛）

> 比赛（含正式赛与虚拟赛）的完整数据模型与服务全景：参与者、题目、单题结果、比赛提交、排名、段位、评分规则、调度。

比赛是后端最重的模块（`contest/`：3 controller / 6 service / 9 mapper / 15 entity）。虚拟比赛的隔离机制见 [[virtual-contest]]。

## 实体模型（`contest/entity/`）

```
Contest ──┬── ContestProblem ──── ContestProblemResult（每选手每题）
          │        │
          ├── ContestSubmission（指向 submission，见 [[submission]]）
          │
          ├── ContestParticipant ── (status: REGISTERED/STARTED/FINISHED/DISQUALIFIED)
          │
          ├── ContestAnnouncement
          ├── FirstSolveRecord（一血）
          ├── GlobalRanking（全局排名 + rating）
          └── ScoringRule（评分规则）
```

| 实体 | 记什么 |
| --- | --- |
| `Contest` | 比赛本体：状态、类型、评分模式、slug、是否计 rating |
| `ContestParticipant` | 选手参赛记录 + 参赛状态 |
| `ContestProblem` | 比赛含哪些题（多对一） |
| `ContestProblemResult` | 某选手某题的得分 / 罚时 / 通过状态 |
| `ContestSubmission` | 比赛内的提交（反查索引 `submission_id`，见下） |
| `FirstSolveRecord` | 一血记录 |
| `GlobalRanking` | 全局排名 + rating（段位由此派生） |
| `ScoringRule` | 评分规则定义 |
| `ContestAnnouncement` | 比赛公告 |

## 枚举（`contest/entity/enums/`）

| 枚举 | 值 |
| --- | --- |
| `ContestStatus` | `DRAFT`、`UPCOMING`、`RUNNING`、`FINISHED`、`CANCELLED` |
| `ContestScoringMode` | `SCORE`、`ICPC`、`IOI` |
| `ContestType` | `ICPC`、`IOI`、`CUSTOM` |
| `ContestParticipantStatus` | `REGISTERED`、`STARTED`、`FINISHED`、`DISQUALIFIED` |
| `ContestTieBreaker` | 同分打破规则 |
| `RatingTitle` | `NEWBIE`→`PUPIL`→`SPECIALIST`→`EXPERT`→`CANDIDATE_MASTER`→`MASTER`→`INTERNATIONAL_MASTER`→`GRANDMASTER`→`INTERNATIONAL_GRANDMASTER`→`LEGENDARY_GRANDMASTER`（10 级，Codeforces 风格） |

## 服务层（`contest/service/`，6 个）

| 服务 | 职责 |
| --- | --- |
| `ContestService` / `ContestServiceImpl` | 比赛 CRUD、参赛、状态流转 |
| `ContestSchedulerService` | **调度 + virtual session**：比赛开始/结束触发；虚拟比赛按选手个人 session 计时（见 [[virtual-contest]]） |
| `ContestScoringService` | 按评分模式（SCORE/ICPC/IOI）算分 |
| `RankingService` | 实时 / 最终排名 |
| `RatingCalculationService` | rating 计算（仅 `isRated` 比赛计入全局段位） |
| `ScoringRuleService` | 评分规则配置 |

前端管理端有对应的比赛创建向导（`management/.../contests/wizard/` 6 步：BasicInfo/Schedule/Problems/ScoringRule/Review）与评分规则编辑器（`ScoringRuleForm`、`ScoringRuleSelector`）。

## 评分硬化（性能索引）

迁移 `init-db/migrations/V20260617120000__Contest_Scoring_Hardening.sql` 是**纯加索引**（不动数据，对生产安全）：

- `idx_contest_submissions_submission_id` —— `submissionId → 比赛提交`反查（P0-1）
- `idx_global_rankings_user_id_rating` —— rating 预加载 HashMap 的覆盖索引（P1-5）
- `idx_global_rankings_username` —— 分析页按用户名查

另有 `V20260617130000__Contest_Slug_Unique`（slug 唯一）、`V20260617140000__Contest_Real_Unique_And_Session_Length`（正式比赛唯一性 + session 时长约束）。

## 关联

- **虚拟比赛如何隔离** → [[virtual-contest]]
- **比赛提交复用判题链路** → [[submission]] + [[codemap/judging-pipeline]]
- **判题完成后驱动排名更新** → 经 `SubmissionJudgedEvent`（见 [[submission]]）
