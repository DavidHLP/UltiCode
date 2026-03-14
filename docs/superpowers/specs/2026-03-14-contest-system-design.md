# 竞赛系统重新设计方案

> **版本**: 1.1
> **日期**: 2026-03-14
> **作者**: Claude Code
> **状态**: 待审核

---

## 1. 概述

### 1.1 背景

UltiCode 平台现有竞赛系统采用 Codeforces 风格的 Elo 积分系统，计算复杂，用户理解成本高。为了降低参与门槛、提升用户体验，决定参考 LeetCode 周赛模式进行重新设计。

### 1.2 目标

- **简化积分系统**: 从 Elo 积分制改为简单的积分制（分数 + 时间）
- **支持多种竞赛类型**: 周赛、双周赛、月赛、专题赛、企业赛、校园赛
- **增强实时功能**: 实时排行榜、首杀播报、代码回放
- **完善管理功能**: 竞赛管理、评分规则配置、反作弊、数据分析

### 1.3 范围

| 模块 | 范围 |
|------|------|
| 数据库 | 重构竞赛相关模型，移除 Elo 相关字段 |
| 后端 | 重构 Contest 模块，新增实时、反作弊、分析模块 |
| 前端 | 重新设计竞赛大厅、详情页、排行榜、管理后台 |

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                          │
├─────────────┬─────────────┬─────────────┬─────────────┬─────────┤
│  竞赛大厅   │  竞赛详情   │  实时排行榜 │  赛后分析   │ 管理后台 │
└──────┬──────┴──────┬──────┴──────┬──────┴──────┬──────┴────┬────┘
       │             │             │             │           │
       ▼             ▼             ▼             ▼           ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Backend (NestJS)                          │
├─────────────┬─────────────┬─────────────┬─────────────┬─────────┤
│ ContestCore │  RankingSvc │ RealtimeSvc │ AntiCheat   │Analytics│
│   Module    │   Module    │   Module    │   Module    │ Module  │
└──────┬──────┴──────┬──────┴──────┬──────┴──────┬──────┴────┬────┘
       │             │             │             │           │
       ▼             ▼             ▼             ▼           ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Data Layer                               │
├────────────────┬────────────────┬────────────────┬──────────────┤
│  MySQL (Prisma)│  Redis (Cache) │  WebSocket     │  BullMQ      │
│                │                │  (Realtime)    │  (Jobs)      │
└────────────────┴────────────────┴────────────────┴──────────────┘
```

### 2.2 模块职责

| 模块 | 职责 | 关键功能 |
|------|------|----------|
| ContestCore | 竞赛核心管理 | CRUD、报名、签到、状态流转 |
| RankingSvc | 排名计算 | 积分计算、排名更新、排行榜冻结 |
| RealtimeSvc | 实时通信 | WebSocket、排行榜推送、首杀播报 |
| AntiCheat | 反作弊检测 | 代码相似度、时间异常、行为分析 |
| AnalyticsSvc | 数据分析 | 赛后报告、题目统计、用户表现 |

### 2.3 技术选型

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端框架 | Vue 3 + Composition API | 现有技术栈 |
| 状态管理 | Pinia | 现有技术栈 |
| 后端框架 | NestJS | 现有技术栈 |
| ORM | Prisma | 现有技术栈 |
| 数据库 | MySQL | 现有技术栈 |
| 缓存 | Redis | 现有技术栈 |
| 实时通信 | WebSocket (@nestjs/platform-socket.io) | 新增 |
| 消息队列 | BullMQ | 现有技术栈 |

### 2.4 WebSocket 架构说明

**决策**: 创建新的专用 `ContestGateway`，与现有 `NotificationGateway` 并存。

**理由**:
- 现有 `NotificationGateway` 使用 `subscribe:contest` / `unsubscribe:contest` 事件
- 竞赛实时功能需要更专业的事件（排行榜更新、首杀播报等）
- 分离关注点，避免通知系统与竞赛系统耦合

**新网关**:
```
ContestGateway (namespace: /contest)
├── join_contest     - 加入竞赛房间
├── leave_contest    - 离开竞赛房间
├── ranking_update   - 排行榜更新推送
├── first_solve      - 首杀播报
├── announcement     - 竞赛公告
├── contest_status   - 竞赛状态变更
└── submission_result - 提交结果
```

### 2.5 API 版本策略

**决策**: 新 API 使用 `/api/contests` 前缀，与现有 `/contest` 共存，通过功能开关切换。

**过渡方案**:
1. **阶段 1**: 新 API 与旧 API 并存，前端通过配置切换
2. **阶段 2**: 验证稳定后，逐步迁移用户到新 API
3. **阶段 3**: 弃用旧 API（保留足够的弃用期）

**配置示例**:
```typescript
// feature-flags.ts
export const FEATURE_FLAGS = {
  USE_NEW_CONTEST_SYSTEM: process.env.FEATURE_NEW_CONTEST === 'true',
};
```

---

## 3. 数据库设计

### 3.1 枚举定义与迁移映射

#### 3.1.1 竞赛类型 (ContestType)

**现有值**: `weekly`, `biweekly`, `special`
**新增值**: `monthly`, `themed`, `corporate`, `campus`

```prisma
enum ContestType {
  weekly     // 周赛 - 保留
  biweekly   // 双周赛 - 保留
  monthly    // 月赛 - 新增
  special    // 专题赛 - 保留，语义调整为"特殊竞赛"
  themed     // 专题赛 - 新增，替代 special 的新语义
  corporate  // 企业赛 - 新增
  campus     // 校园赛 - 新增
}
```

**迁移映射**:
| 旧值 | 新值 | 说明 |
|------|------|------|
| weekly | weekly | 保持不变 |
| biweekly | biweekly | 保持不变 |
| special | themed | 语义映射 |

#### 3.1.2 竞赛状态 (ContestStatus)

**现有值**: `upcoming`, `running`, `finished`
**新增值**: `draft`, `published`, `registering`, `ongoing`, `freezing`, `archived`

```prisma
enum ContestStatus {
  draft       // 草稿 - 新增
  published   // 已发布 - 新增
  registering // 报名中 - 新增
  upcoming    // 即将开始 - 保留
  ongoing     // 进行中 - 新增（替代 running）
  running     // 进行中 - 保留，兼容旧数据
  freezing    // 排行榜冻结 - 新增
  finished    // 已结束 - 保留
  archived    // 已归档 - 新增
}
```

**迁移映射**:
| 旧值 | 新值 | 说明 |
|------|------|------|
| upcoming | upcoming | 保持不变 |
| running | ongoing | 语义映射 |
| finished | finished | 保持不变 |

#### 3.1.3 参赛者状态 (ParticipantStatus)

**现有值**: `REGISTERED`, `STARTED`, `FINISHED`, `DISQUALIFIED`
**新增值**: `CHECKED_IN`, `PARTICIPATING`

```prisma
enum ContestParticipantStatus {
  REGISTERED    // 已报名 - 保留
  CHECKED_IN    // 已签到 - 新增
  STARTED       // 已开始 - 保留
  PARTICIPATING // 参赛中 - 新增
  FINISHED      // 已完成 - 保留
  DISQUALIFIED  // 取消资格 - 保留
}
```

### 3.2 核心模型

#### 3.2.1 竞赛主表 (Contest) - 增量更新

```prisma
model Contest {
  id                 String   @id @db.VarChar(40) @default(uuid())
  title              String   @db.VarChar(120)
  slug               String   @db.VarChar(120) @unique
  description        String?  @db.Text
  cover_image        String?  @db.VarChar(255)

  // 竞赛类型 - 扩展枚举
  contest_type       ContestType

  // 时间配置 - 保留现有字段，新增字段
  start_time         DateTime
  end_time           DateTime?  // 新增：结束时间（可从 duration_minutes 计算）
  registration_start DateTime?  // 新增：报名开始时间
  registration_end   DateTime?  // 新增：报名截止时间
  freeze_time        DateTime?  // 新增：排行榜冻结时间
  duration_minutes   Int

  // 竞赛配置 - 保留现有
  is_visible         Boolean  @default(true)
  is_rated           Boolean  @default(true)  // 保留但语义变更：是否计入积分
  is_virtual         Boolean  @default(true)  // 新增：是否支持虚拟参赛
  max_participants   Int?     // 新增：最大参赛人数

  // 评分配置 - 新增关联
  scoring_rule_id    String?  @db.VarChar(40)  // 新增：关联评分规则
  scoring_rule       ContestScoringRule? @relation(fields: [scoring_rule_id], references: [id])

  // 保留现有评分字段（兼容旧数据）
  penalty_per_wrong  Int      @default(300)
  scoring_mode       ContestScoringMode @default(SCORE)
  tie_breaker        ContestTieBreaker @default(LAST_SOLVE_TIME)

  // 状态 - 扩展枚举
  status             ContestStatus

  // 统计 - 保留现有
  registered_count   Int      @default(0)
  participant_count  Int      @default(0)
  submission_count   Int?     // 新增

  // 软删除 - 保留现有
  is_deleted         Boolean  @default(false)
  deleted_at         DateTime?
  deleted_by         String?  @db.VarChar(40)

  // 元数据 - 保留现有
  created_at         DateTime @default(now())
  updated_at         DateTime @updatedAt
  created_by         String?  @db.VarChar(40)
  rules              String?  @db.Text

  // 关联
  participants       ContestParticipant[]
  problemResults     ContestProblemResult[]
  problems           ContestProblem[]
  rankings           ContestRanking[]
  contestSubmissions ContestSubmission[]
  virtualSessions    VirtualContestSession[]
  announcements      ContestAnnouncement[]  // 新增
  firstSolveRecords  FirstSolveRecord[]     // 新增
  analytics          ContestAnalytics?      // 新增

  @@index([status, start_time])
  @@index([contest_type])
  @@index([slug])
  @@index([status, is_visible, start_time])
  @@map("contests")
}
```

#### 3.2.2 评分规则 (ContestScoringRule) - 新增

```prisma
model ContestScoringRule {
  id                  String   @id @db.VarChar(40) @default(uuid())
  name                String   @db.VarChar(100)
  description         String?  @db.Text

  // 分数配置
  base_score_per_problem Int   @default(100)
  time_bonus_per_minute  Int   @default(1)

  // 惩罚配置
  wrong_answer_penalty   Int   @default(5)   // 秒
  time_limit_penalty     Int   @default(0)

  // 奖励配置
  first_solve_bonus      Int   @default(10)
  full_score_bonus       Int   @default(0)

  is_default          Boolean  @default(false)
  is_active           Boolean  @default(true)

  contests            Contest[]

  created_at          DateTime @default(now())
  updated_at          DateTime @updatedAt

  @@map("contest_scoring_rules")
}
```

#### 3.2.3 竞赛题目关联 (ContestProblem) - 增量更新

```prisma
model ContestProblem {
  id                 String   @id @db.VarChar(40) @default(uuid())
  contest_id         String   @db.VarChar(40)
  problem_id         BigInt   // 保留 BigInt 类型，与 Problem.id 一致
  problem_index      String   @db.VarChar(10)  // "A", "B", "C", "D"
  label              String?  @db.VarChar(10)  // 新增：显示标签

  // 分数配置
  score              Int      @default(0)
  base_score         Int?     // 新增：可覆盖默认基础分
  time_bonus         Int?     // 新增：每分钟奖励（可覆盖规则默认值）
  penalty_per_wrong  Int?

  // 统计
  solved_count       Int      @default(0)
  submission_count   Int      @default(0)

  contest            Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)
  problem            Problem  @relation(fields: [problem_id], references: [id], onDelete: Cascade)
  problemResults     ContestProblemResult[]

  @@unique([contest_id, problem_index])
  @@index([contest_id])
  @@map("contest_problems")
}
```

#### 3.2.4 参赛者 (ContestParticipant) - 增量更新

```prisma
model ContestParticipant {
  id            String   @id @db.VarChar(40) @default(uuid())
  contest_id    String   @db.VarChar(40)
  user_id       String   @db.VarChar(40)
  registered_at DateTime @default(now())

  // 状态 - 扩展枚举
  status        ContestParticipantStatus @default(REGISTERED)
  checked_in_at DateTime?  // 新增：签到时间
  is_virtual    Boolean   @default(false)

  // 最终成绩 - 新增/保留
  total_score   Int       @default(0)
  total_time    Int       @default(0)  // 秒
  rank          Int?

  // 统计
  solved_count  Int       @default(0)
  attempt_count Int       @default(0)

  contest       Contest   @relation(fields: [contest_id], references: [id], onDelete: Cascade)
  user          User      @relation(fields: [user_id], references: [id], onDelete: Cascade)
  results       ContestProblemResult[]

  @@unique([contest_id, user_id])
  @@index([contest_id, rank])
  @@map("contest_participants")
}
```

#### 3.2.5 题目提交结果 (ContestProblemResult)

```prisma
model ContestProblemResult {
  id                String   @id @db.VarChar(40) @default(uuid())
  participant_id    String   @db.VarChar(40)
  contest_problem_id String  @db.VarChar(40)

  // 提交信息
  submit_count      Int      @default(0)
  accepted_at       DateTime?
  time_spent        Int      @default(0)  // 秒

  // 分数
  score             Int      @default(0)
  time_bonus        Int      @default(0)
  penalty           Int      @default(0)

  // 首杀标记
  is_first_solve    Boolean  @default(false)

  participant       ContestParticipant @relation(fields: [participant_id], references: [id], onDelete: Cascade)
  contestProblem    ContestProblem @relation(fields: [contest_problem_id], references: [id], onDelete: Cascade)

  @@unique([participant_id, contest_problem_id])
  @@map("contest_problem_results")
}
```

### 3.3 实时功能模型

#### 3.3.1 排行榜快照 (ContestRanking) - 增量更新

```prisma
model ContestRanking {
  id            String   @id @db.VarChar(40) @default(uuid())
  contest_id    String   @db.VarChar(40)
  snapshot_time DateTime @default(now())

  // 排名数据
  rankings      Json     // [{userId, score, time, solved, rank}]
  is_frozen     Boolean  @default(false)

  contest       Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)

  @@index([contest_id, snapshot_time])
  @@map("contest_rankings")
}
```

#### 3.3.2 首杀记录 (FirstSolveRecord) - 新增

```prisma
model FirstSolveRecord {
  id            String   @id @db.VarChar(40) @default(uuid())
  contest_id    String   @db.VarChar(40)
  problem_id    BigInt   // 与 Problem.id 类型一致
  user_id       String   @db.VarChar(40)
  solved_at     DateTime @default(now())
  time_spent    Int      // 秒

  contest       Contest  @relation(fields: [contest_id], references: [id])
  problem       Problem  @relation(fields: [problem_id], references: [id])
  user          User     @relation(fields: [user_id], references: [id], onDelete: Cascade)

  @@unique([contest_id, problem_id])
  @@map("first_solve_records")
}
```

#### 3.3.3 竞赛公告 (ContestAnnouncement) - 新增

```prisma
model ContestAnnouncement {
  id          String   @id @db.VarChar(40) @default(uuid())
  contest_id  String   @db.VarChar(40)
  title       String   @db.VarChar(200)
  content     String   @db.Text
  created_at  DateTime @default(now())
  is_pinned   Boolean  @default(false)

  contest     Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)

  @@index([contest_id, created_at])
  @@map("contest_announcements")
}
```

### 3.4 提交模型 (ContestSubmission) - 增量更新

**说明**: 保留 `code` 和 `language` 字段用于竞赛快照，确保即使原始 `Submission` 被修改，竞赛记录仍保持完整。

```prisma
model ContestSubmission {
  id            String   @id @db.VarChar(40) @default(uuid())
  submission_id String   @db.VarChar(40) @unique
  contest_id    String   @db.VarChar(40)
  user_id       String   @db.VarChar(40)
  problem_id    BigInt   // 与 Problem.id 类型一致

  submitted_at  DateTime @default(now())
  code          String   @db.Text  // 代码快照，用于竞赛完整性
  language      String   @db.VarChar(50)

  // 判题结果
  status        SubmissionStatus
  test_results  Json?
  runtime       Int?
  memory        Int?

  // 竞赛特有
  is_accepted   Boolean  @default(false)
  time_spent    Int      @default(0)  // 从比赛开始到提交的时间（秒）

  submission    Submission @relation(fields: [submission_id], references: [id])
  contest       Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)
  user          User     @relation(fields: [user_id], references: [id], onDelete: Cascade)
  problem       Problem  @relation(fields: [problem_id], references: [id], onDelete: Cascade)

  @@index([contest_id, user_id])
  @@index([contest_id, problem_id, submitted_at])
  @@map("contest_submissions")
}
```

### 3.5 虚拟竞赛 (VirtualContestSession) - 增量更新

```prisma
model VirtualContestSession {
  id            String   @id @db.VarChar(40) @default(uuid())
  contest_id    String   @db.VarChar(40)
  user_id       String   @db.VarChar(40)
  started_at    DateTime @default(now())
  ended_at      DateTime?

  total_score   Int      @default(0)
  total_time    Int      @default(0)
  solved_count  Int      @default(0)

  contest       Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)
  user          User     @relation(fields: [user_id], references: [id], onDelete: Cascade)

  @@index([contest_id, user_id])
  @@map("virtual_contest_sessions")
}
```

### 3.6 赛后分析 (ContestAnalytics) - 新增

```prisma
model ContestAnalytics {
  id                String   @id @db.VarChar(40) @default(uuid())
  contest_id        String   @unique @db.VarChar(40)

  // 参与统计
  total_registered   Int     @default(0)
  total_participated Int     @default(0)
  completion_rate    Float   @default(0)

  // 题目统计
  problem_stats      Json?   // 每题通过率、平均尝试次数

  // 用户分布
  score_distribution Json?   // 分数分布
  time_distribution  Json?   // 完成时间分布

  // 排行榜
  top_users          Json?   // 前100名

  generated_at       DateTime @default(now())

  contest            Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)

  @@map("contest_analytics")
}
```

### 3.7 积分系统对比

| 方面 | 旧系统 (Elo) | 新系统 (积分制) |
|------|-------------|----------------|
| 计算方式 | 复杂的 Elo 公式 | `score = baseScore + timeBonus - penalty` |
| 排名依据 | Elo 积分 | 总分数 → 总用时（秒） |
| 时间因素 | 不考虑 | 每题用时影响排名 |
| 首杀奖励 | 无 | 额外积分奖励 |
| 可配置性 | 固定规则 | 可通过 `ContestScoringRule` 配置 |
| 理解难度 | 高（需理解 Elo） | 低（简单加减法） |

---

## 4. 数据迁移策略

### 4.1 迁移原则

1. **向后兼容**: 旧数据保持可用，新功能逐步启用
2. **零停机**: 迁移过程不影响现有竞赛进行
3. **可回滚**: 保留回滚脚本，出问题可快速恢复
4. **分阶段**: 分步骤执行，每步可验证

### 4.2 迁移步骤

#### 阶段 1: 准备工作

```sql
-- 1.1 备份现有数据
CREATE TABLE contests_backup AS SELECT * FROM contests;
CREATE TABLE contest_participants_backup AS SELECT * FROM contest_participants;
CREATE TABLE contest_rankings_backup AS SELECT * FROM contest_rankings;

-- 1.2 创建新枚举值（MySQL 5.7+）
ALTER TABLE contests MODIFY COLUMN contest_type ENUM(
  'weekly', 'biweekly', 'monthly', 'special', 'themed', 'corporate', 'campus'
);
```

#### 阶段 2: 创建新表

```sql
-- 2.1 创建评分规则表
CREATE TABLE contest_scoring_rules (
  id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description TEXT,
  base_score_per_problem INT DEFAULT 100,
  time_bonus_per_minute INT DEFAULT 1,
  wrong_answer_penalty INT DEFAULT 5,
  first_solve_bonus INT DEFAULT 10,
  is_default BOOLEAN DEFAULT FALSE,
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2.2 插入默认规则
INSERT INTO contest_scoring_rules (id, name, description, is_default) VALUES
('default-weekly', '标准周赛规则', 'LeetCode 风格的简单积分规则', TRUE),
('default-icpc', 'ICPC 规则', 'ACM/ICPC 风格的罚时规则', FALSE);

-- 2.3 创建首杀记录表
CREATE TABLE first_solve_records (...);

-- 2.4 创建竞赛公告表
CREATE TABLE contest_announcements (...);

-- 2.5 创建赛后分析表
CREATE TABLE contest_analytics (...);
```

#### 阶段 3: 修改现有表

```sql
-- 3.1 为 contests 表添加新字段
ALTER TABLE contests
  ADD COLUMN end_time DATETIME NULL,
  ADD COLUMN registration_start DATETIME NULL,
  ADD COLUMN registration_end DATETIME NULL,
  ADD COLUMN freeze_time DATETIME NULL,
  ADD COLUMN is_virtual BOOLEAN DEFAULT TRUE,
  ADD COLUMN max_participants INT NULL,
  ADD COLUMN scoring_rule_id VARCHAR(40) NULL,
  ADD COLUMN submission_count INT DEFAULT 0;

-- 3.2 计算并填充 end_time
UPDATE contests SET end_time = DATE_ADD(start_time, INTERVAL duration_minutes MINUTE);

-- 3.3 为 contest_participants 表添加新字段
ALTER TABLE contest_participants
  ADD COLUMN checked_in_at DATETIME NULL,
  ADD COLUMN is_virtual BOOLEAN DEFAULT FALSE,
  ADD COLUMN total_score INT DEFAULT 0,
  ADD COLUMN total_time INT DEFAULT 0,
  ADD COLUMN attempt_count INT DEFAULT 0;

-- 3.4 为 contest_problems 表添加新字段
ALTER TABLE contest_problems
  ADD COLUMN label VARCHAR(10) NULL,
  ADD COLUMN base_score INT NULL,
  ADD COLUMN time_bonus INT NULL;

-- 3.5 更新 label 字段
UPDATE contest_problems SET label = problem_index WHERE label IS NULL;
```

#### 阶段 4: 数据迁移

```sql
-- 4.1 迁移竞赛类型
UPDATE contests SET contest_type = 'themed' WHERE contest_type = 'special';

-- 4.2 迁移竞赛状态
-- running -> ongoing（仅对新系统有效，旧数据保持不变）
-- 通过应用层逻辑处理

-- 4.3 为现有竞赛关联默认评分规则
UPDATE contests SET scoring_rule_id = 'default-weekly' WHERE scoring_rule_id IS NULL;

-- 4.4 迁移排行榜数据到新格式
-- 保持现有 rankings JSON 格式，添加新字段
```

### 4.3 回滚脚本

```sql
-- 回滚到迁移前状态
DROP TABLE IF EXISTS contest_analytics;
DROP TABLE IF EXISTS contest_announcements;
DROP TABLE IF EXISTS first_solve_records;
DROP TABLE IF EXISTS contest_scoring_rules;

ALTER TABLE contests
  DROP COLUMN end_time,
  DROP COLUMN registration_start,
  DROP COLUMN registration_end,
  DROP COLUMN freeze_time,
  DROP COLUMN is_virtual,
  DROP COLUMN max_participants,
  DROP COLUMN scoring_rule_id,
  DROP COLUMN submission_count;

ALTER TABLE contest_participants
  DROP COLUMN checked_in_at,
  DROP COLUMN is_virtual,
  DROP COLUMN total_score,
  DROP COLUMN total_time,
  DROP COLUMN attempt_count;

ALTER TABLE contest_problems
  DROP COLUMN label,
  DROP COLUMN base_score,
  DROP COLUMN time_bonus;
```

### 4.4 历史数据处理

| 数据类型 | 处理方式 |
|----------|----------|
| 已结束竞赛 | 保持原样，不迁移到新积分系统 |
| 进行中竞赛 | 使用旧系统完成，新功能不启用 |
| 未来竞赛 | 可选择使用新系统或旧系统 |

### 4.5 Elo 数据保留

```sql
-- 保留 Elo 相关字段但不使用
-- GlobalRanking 表保持不变，作为历史记录
-- User.contest_rating 字段保留但停止更新

-- 创建历史归档表（可选）
CREATE TABLE elo_rating_history AS
SELECT * FROM global_rankings;
```

---

## 5. API 设计

### 5.1 模块结构

```
backend/src/contest/
├── contest.module.ts
├── contest.controller.ts          # 竞赛CRUD
├── contest.service.ts
├── participation/
│   ├── participation.controller.ts
│   └── participation.service.ts
├── ranking/
│   ├── ranking.controller.ts
│   └── ranking.service.ts
├── submission/
│   ├── submission.controller.ts
│   └── submission.service.ts
├── realtime/
│   ├── contest.gateway.ts         # 新增：专用竞赛网关
│   └── realtime.service.ts
├── scoring/
│   └── scoring.service.ts
├── anticheat/
│   ├── anticheat.service.ts
│   └── anticheat.controller.ts
└── analytics/
    ├── analytics.controller.ts
    └── analytics.service.ts
```

### 5.2 REST API 端点

#### 5.2.1 公开端点

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/contests` | 竞赛列表 |
| GET | `/api/contests/:slug` | 竞赛详情 |
| GET | `/api/contests/:slug/problems` | 竞赛题目 |
| GET | `/api/contests/:slug/announcements` | 竞赛公告 |
| GET | `/api/contests/:slug/ranking` | 实时排行榜 |
| GET | `/api/contests/:slug/ranking/user/:userId` | 用户排名 |

#### 5.2.2 参赛端点

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/contests/:slug/register` | 报名竞赛 |
| POST | `/api/contests/:slug/checkin` | 签到 |
| DELETE | `/api/contests/:slug/withdraw` | 退出竞赛 |
| POST | `/api/contests/:slug/submit` | 提交代码 |
| GET | `/api/contests/:slug/submissions` | 我的提交列表 |
| GET | `/api/contests/:slug/submissions/:id` | 提交详情 |

#### 5.2.3 虚拟竞赛端点

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/contests/:slug/virtual/start` | 开始虚拟竞赛 |
| POST | `/api/contests/:slug/virtual/end` | 结束虚拟竞赛 |

#### 5.2.4 管理端点 ⚠️ 新增功能

| 方法 | 路径 | 描述 | 状态 |
|------|------|------|------|
| GET | `/api/admin/contests` | 管理员竞赛列表 | 扩展现有 |
| POST | `/api/admin/contests` | 创建竞赛 | 扩展现有 |
| PUT | `/api/admin/contests/:id` | 更新竞赛 | 扩展现有 |
| DELETE | `/api/admin/contests/:id` | 删除竞赛 | 扩展现有 |
| POST | `/api/admin/contests/:id/publish` | 发布竞赛 | **新增** |
| POST | `/api/admin/contests/:id/problems` | 添加题目 | 扩展现有 |
| PUT | `/api/admin/contests/:id/problems/:order` | 更新题目 | **新增** |
| DELETE | `/api/admin/contests/:id/problems/:order` | 删除题目 | **新增** |
| POST | `/api/admin/contests/:id/announcements` | 发布公告 | **新增** |
| GET | `/api/admin/scoring-rules` | 评分规则列表 | **新增** |
| POST | `/api/admin/scoring-rules` | 创建规则 | **新增** |
| PUT | `/api/admin/scoring-rules/:id` | 更新规则 | **新增** |

### 5.3 WebSocket 事件

#### 5.3.1 ContestGateway (namespace: /contest)

**客户端 → 服务端**:
```typescript
@SubscribeMessage('join_contest')
handleJoinContest(client: Socket, payload: { contestId: string }): void

@SubscribeMessage('leave_contest')
handleLeaveContest(client: Socket, payload: { contestId: string }): void
```

**服务端 → 客户端**:
```typescript
@SubscribeMessage('ranking_update')
emitRankingUpdate(data: RankingUpdateData): void

@SubscribeMessage('first_solve')
emitFirstSolve(data: FirstSolveData): void

@SubscribeMessage('announcement')
emitAnnouncement(data: AnnouncementData): void

@SubscribeMessage('contest_status')
emitContestStatus(data: ContestStatusData): void

@SubscribeMessage('submission_result')
emitSubmissionResult(data: SubmissionResultData): void
```

### 5.4 响应格式

#### 5.4.1 成功响应

```typescript
interface ApiResponse<T> {
  success: true;
  data: T;
  meta?: {
    total?: number;
    page?: number;
    limit?: number;
    hasMore?: boolean;
  };
}
```

#### 5.4.2 错误响应

```typescript
interface ApiErrorResponse {
  success: false;
  error: {
    code: string;        // 错误代码，如 "CONTEST_REGISTRATION_CLOSED"
    message: string;     // 用户友好的错误消息
    details?: any;       // 额外错误详情（仅开发环境）
  };
}
```

#### 5.4.3 竞赛错误代码

| 错误代码 | HTTP 状态 | 描述 |
|----------|-----------|------|
| `CONTEST_NOT_FOUND` | 404 | 竞赛不存在 |
| `CONTEST_REGISTRATION_CLOSED` | 400 | 报名已截止 |
| `CONTEST_ALREADY_REGISTERED` | 400 | 已报名 |
| `CONTEST_NOT_REGISTERED` | 400 | 未报名 |
| `CONTEST_FULL` | 400 | 竞赛人数已满 |
| `CONTEST_ALREADY_CHECKED_IN` | 400 | 已签到 |
| `CONTEST_NOT_STARTED` | 400 | 竞赛未开始 |
| `CONTEST_ENDED` | 400 | 竞赛已结束 |
| `CONTEST_SUBMISSION_TIMEOUT` | 400 | 提交超时 |
| `VIRTUAL_SESSION_EXISTS` | 400 | 已有进行中的虚拟竞赛 |
| `SUBMISSION_RATE_LIMITED` | 429 | 提交频率超限 |

### 5.5 限流规范

| 端点类型 | 限制 | 说明 |
|----------|------|------|
| 竞赛提交 | 10次/分钟 | 每个用户每道题 |
| 排行榜刷新 | 1次/秒 | 每个竞赛 |
| WebSocket 消息 | 100条/分钟 | 每个连接 |
| API 通用 | 1000次/分钟 | 每个用户 |

### 5.6 服务层接口

```typescript
// 积分计算服务
interface ScoringService {
  calculateScore(
    problem: ContestProblem,
    submission: ContestSubmission,
    rule: ContestScoringRule
  ): number;

  calculateTotalScore(participant: ContestParticipant): number;

  updateRanking(contestId: string): Promise<void>;
}

// 反作弊服务
interface AntiCheatService {
  detectSimilarity(submission: ContestSubmission): Promise<SimilarityReport>;

  checkTimeAnomaly(submission: ContestSubmission): boolean;

  reportSuspiciousActivity(contestId: string, userId: string): Promise<void>;
}

// 数据分析服务
interface AnalyticsService {
  generateContestReport(contestId: string): Promise<ContestAnalytics>;

  calculateProblemStats(contestId: string): Promise<ProblemStats[]>;

  getUserPerformanceHistory(userId: string): Promise<PerformanceHistory>;
}
```

---

## 6. 前端设计

### 6.1 目录结构

```
console/src/
├── views/contest/
│   ├── ContestListView.vue
│   ├── ContestDetailView.vue
│   ├── ContestProblemView.vue
│   ├── ContestRankingView.vue
│   ├── ContestAnalysisView.vue
│   └── components/
│       ├── ContestCard.vue
│       ├── ContestTimer.vue
│       ├── ContestStatusBadge.vue
│       ├── ContestTypeTag.vue
│       ├── ProblemSelector.vue
│       ├── RankingTable.vue
│       ├── RankingEntry.vue
│       ├── FirstSolveNotification.vue
│       ├── ContestAnnouncement.vue
│       ├── VirtualContestDialog.vue
│       └── SubmissionStatusBadge.vue
│
├── stores/contest/
│   ├── contestStore.ts
│   ├── rankingStore.ts
│   └── submissionStore.ts
│
├── api/contest/
│   ├── contest.ts
│   ├── ranking.ts
│   └── submission.ts
│
├── composables/contest/
│   ├── useContestTimer.ts
│   ├── useContestSocket.ts
│   └── useContestSubmission.ts
│
└── i18n/locales/
    ├── zh-CN.ts
    └── en-US.ts
```

### 6.2 页面设计

#### 6.2.1 竞赛大厅

```
┌─────────────────────────────────────────────────────────────────┐
│  🏆 竞赛                              [进行中] [即将开始] [已结束] │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 📅 第 123 场周赛                    [进行中] 🔴 LIVE    │   │
│  │ 剩余 01:23:45                                           │   │
│  │ 已报名 1,234 人 · 题目 4 道                             │   │
│  │                                    [进入比赛]           │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 📅 第 45 场双周赛                  [即将开始] ⏰ 2天后  │   │
│  │ 2026-03-16 20:00 - 22:00                               │   │
│  │ 已报名 856 人 · 预计 4 道                               │   │
│  │                                    [已报名 ✓]          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 🏢 字节跳动校园赛                    [已结束]           │   │
│  │ 2026-03-10 14:00 - 17:00                               │   │
│  │ 参赛 2,345 人 · 完成 1,234 人                          │   │
│  │                              [查看排名] [虚拟参赛]      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

#### 6.2.2 竞赛详情页

```
┌─────────────────────────────────────────────────────────────────┐
│  第 123 场周赛                              [状态: 进行中]      │
│  ⏱️ 剩余 01:23:45    👥 1,234 人    📝 4 道题                  │
├─────────────────────────────────────────────────────────────────┤
│  [题目] [排行榜] [公告] [我的提交]                              │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                          │
│  │  A   │ │  B   │ │  C   │ │  D   │                          │
│  │ ✓ 100│ │ ✗ 0   │ │ - -  │ │ - -  │                          │
│  └──────┘ └──────┘ └──────┘ └──────┘                          │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ A. 两数之和                                      100 分  │   │
│  │ 简单 · 通过率 78%                                        │   │
│  │                                                         │   │
│  │ 给定一个整数数组 nums 和一个整数目标值 target...         │   │
│  │                                                         │   │
│  │ 示例 1:                                                  │   │
│  │ 输入: nums = [2,7,11,15], target = 9                    │   │
│  │ 输出: [0,1]                                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                        [开始答题]              │
└─────────────────────────────────────────────────────────────────┘
```

#### 6.2.3 实时排行榜

```
┌─────────────────────────────────────────────────────────────────┐
│  🏆 排行榜                              [刷新] [冻结中 ❄️]      │
├─────────────────────────────────────────────────────────────────┤
│  排名  用户           总分    时间      A      B      C      D   │
├─────────────────────────────────────────────────────────────────┤
│   🥇  1  alice         400   45:23   ✓05:12  ✓12:34  ✓27:45  - │
│   🥈  2  bob           350   52:11   ✓03:45  ✓15:22  ✓38:44  - │
│   🥉  3  charlie       300   48:09   ✓08:23  ✓19:56  ✗(3)   - │
│      4  david         250   55:30   ✓06:11  ✗(5)    -      -  │
│  ────────────────────────────────────────────────────────────── │
│     87  你 (me)          100   23:45   ✓23:45  -      -      - │
│                                                                 │
│  Legend: ✓=通过 时间 | ✗=(尝试次数) | -=未尝试                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  🔔 首杀播报                                                    │
│  ├─ 🎉 alice 首杀 A 题！(05:12)                                │
│  ├─ 🎉 bob 首杀 B 题！(15:22)                                  │
│  └─ 🎉 alice 首杀 C 题！(27:45)                                │
└─────────────────────────────────────────────────────────────────┘
```

#### 6.2.4 管理后台

```
┌─────────────────────────────────────────────────────────────────┐
│  竞赛管理                                    [+ 创建竞赛]       │
├─────────────────────────────────────────────────────────────────┤
│  筛选: [全部类型 ▼] [全部状态 ▼]     搜索: [____________] [🔍] │
├─────────────────────────────────────────────────────────────────┤
│  标题          类型     状态       时间          参赛人数  操作  │
├─────────────────────────────────────────────────────────────────┤
│  第123场周赛   周赛     进行中     03-14 20:00   1,234    [...]  │
│  第45场双周赛  双周赛   报名中     03-16 20:00   856      [...]  │
│  字节校园赛    企业赛   已结束     03-10 14:00   2,345    [...]  │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 核心组件接口

#### 6.3.1 Pinia Store

```typescript
// stores/contest/contestStore.ts
interface ContestState {
  currentContest: Contest | null;
  contests: Contest[];
  myParticipation: Participation | null;
  ranking: RankingEntry[];
  loading: boolean;
  error: ApiError | null;
}

interface ContestActions {
  fetchContests(filters: ContestFilters): Promise<void>;
  fetchContestDetail(slug: string): Promise<void>;
  registerContest(slug: string): Promise<void>;
  checkInContest(slug: string): Promise<void>;
  submitCode(slug: string, data: SubmissionData): Promise<SubmissionResult>;
  connectRealtime(slug: string): void;
  disconnectRealtime(): void;
}
```

#### 6.3.2 Composables

```typescript
// composables/contest/useContestSocket.ts
interface ContestSocket {
  connect(contestId: string): void;
  disconnect(): void;
  onRankingUpdate(callback: (data: RankingData) => void): void;
  onFirstSolve(callback: (data: FirstSolveData) => void): void;
  onAnnouncement(callback: (data: Announcement) => void): void;
  onSubmissionResult(callback: (data: SubmissionResult) => void): void;
}

// composables/contest/useContestTimer.ts
interface ContestTimer {
  timeRemaining: Ref<number>;
  status: Ref<'before' | 'ongoing' | 'frozen' | 'ended'>;
  formattedTime: ComputedRef<string>;
  start(startTime: Date, endTime: Date): void;
  stop(): void;
}
```

### 6.4 国际化

```typescript
const contestI18n = {
  // 标题
  'contest.title': '竞赛',
  'contest.list.upcoming': '即将开始',
  'contest.list.ongoing': '进行中',
  'contest.list.ended': '已结束',

  // 状态
  'contest.status.draft': '草稿',
  'contest.status.published': '已发布',
  'contest.status.registering': '报名中',
  'contest.status.ongoing': '进行中',
  'contest.status.finished': '已结束',

  // 类型
  'contest.type.weekly': '周赛',
  'contest.type.biweekly': '双周赛',
  'contest.type.monthly': '月赛',
  'contest.type.themed': '专题赛',
  'contest.type.corporate': '企业赛',
  'contest.type.campus': '校园赛',

  // 操作
  'contest.action.register': '报名',
  'contest.action.checkin': '签到',
  'contest.action.enter': '进入比赛',
  'contest.action.virtual': '虚拟参赛',
  'contest.action.withdraw': '退出',

  // 排行榜
  'contest.ranking.title': '排行榜',
  'contest.ranking.frozen': '排行榜已冻结',
  'contest.ranking.rank': '排名',
  'contest.ranking.score': '分数',
  'contest.ranking.time': '用时',

  // 首杀
  'contest.firstSolve.title': '首杀播报',
  'contest.firstSolve.message': '{user} 首杀 {problem} 题！',

  // 提交状态
  'contest.submission.accepted': '通过',
  'contest.submission.wrong': '答案错误',
  'contest.submission.timeLimit': '超时',
  'contest.submission.memoryLimit': '内存超限',
  'contest.submission.runtimeError': '运行错误',
  'contest.submission.compileError': '编译错误',

  // 错误消息
  'contest.error.registrationClosed': '报名已截止',
  'contest.error.alreadyRegistered': '您已报名此竞赛',
  'contest.error.contestFull': '竞赛人数已满',
  'contest.error.notStarted': '竞赛尚未开始',
  'contest.error.ended': '竞赛已结束',
  'contest.error.rateLimited': '提交过于频繁，请稍后再试',

  // 表单
  'contest.form.title': '标题',
  'contest.form.slug': '标识',
  'contest.form.type': '类型',
  'contest.form.startTime': '开始时间',
  'contest.form.duration': '持续时长',
  'contest.form.scoringRule': '评分规则',

  // 验证
  'contest.validation.titleRequired': '请输入竞赛标题',
  'contest.validation.slugRequired': '请输入竞赛标识',
  'contest.validation.slugInvalid': '标识只能包含小写字母、数字和连字符',
  'contest.validation.problemsRequired': '请至少添加一道题目',
};
```

---

## 7. 实现计划

### 7.1 阶段划分

| 阶段 | 内容 | 预计工时 | 依赖 |
|------|------|----------|------|
| 1 | 数据库迁移 | 2-3 天 | 无 |
| 2 | 后端核心模块 | 3-4 天 | 阶段 1 |
| 3 | 实时功能 (WebSocket) | 2-3 天 | 阶段 1 |
| 4 | 前端页面 | 4-5 天 | 阶段 2, 3 |
| 5 | 管理后台 | 2-3 天 | 阶段 2 |
| 6 | 反作弊 & 分析 | 2-3 天 | 阶段 2 |
| 7 | 测试 & 优化 | 2-3 天 | 阶段 4, 5, 6 |

### 7.2 依赖关系

```
阶段1 (数据库) ─┬─> 阶段2 (后端核心) ─┬─> 阶段4 (前端页面)
                │                      │
                └─> 阶段3 (实时功能) ──┘
                                       │
阶段5 (管理后台) ──────────────────────┤
                                       │
阶段6 (反作弊&分析) ───────────────────┴─> 阶段7 (测试优化)
```

### 7.3 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 数据迁移丢失 | 高 | 低 | 先备份，分步迁移，保留旧字段 |
| WebSocket 连接不稳定 | 中 | 中 | 实现自动重连，降级到轮询 |
| 高并发排行榜更新 | 中 | 中 | 使用 Redis 缓存，批量更新 |
| 反作弊误判 | 中 | 中 | 设置阈值，人工复核机制 |
| API 兼容性问题 | 中 | 低 | 功能开关，渐进式迁移 |

---

## 8. 测试策略

### 8.1 单元测试

- 积分计算服务 (`ScoringService.calculateScore`)
- 排名排序逻辑 (`RankingService.sortParticipants`)
- 状态流转逻辑 (`ContestService.transitionStatus`)
- 时间计算工具 (`useContestTimer`)

### 8.2 集成测试

- 竞赛 CRUD API
- 报名/签到流程
- 提交判题流程
- 排行榜更新
- WebSocket 连接与事件

### 8.3 E2E 测试

- 完整参赛流程（报名 → 签到 → 答题 → 提交 → 查看排名）
- 实时排行榜更新
- 虚拟竞赛流程
- 管理员创建竞赛

### 8.4 性能测试

- 1000 并发用户排行榜更新
- 100 并发提交处理
- WebSocket 连接数压力测试

---

## 9. 部署注意事项

### 9.1 前置条件

1. **数据库备份**: 执行完整备份
2. **功能开关配置**: 设置 `FEATURE_NEW_CONTEST=false` 初始状态
3. **WebSocket 负载均衡**: 确保支持 WebSocket 长连接（sticky sessions）

### 9.2 部署步骤

1. 部署数据库迁移脚本
2. 部署后端新代码（功能开关关闭）
3. 验证迁移结果
4. 逐步开启功能开关
5. 部署前端新代码
6. 监控系统指标

### 9.3 监控指标

| 指标 | 阈值 | 告警级别 |
|------|------|----------|
| WebSocket 连接数 | > 10000 | Warning |
| 排行榜更新延迟 | > 5s | Warning |
| 提交队列深度 | > 100 | Warning |
| API 错误率 | > 1% | Critical |

---

## 附录

### A. 旧模型清理清单

需要移除或重构的模型/字段（迁移完成后）：
- `GlobalRanking` 模型（Elo 相关）- 保留作为历史记录
- `ContestRanking.ratingChange` 字段 - 弃用但不删除
- `User.contestRating` 字段 - 弃用但不删除
- `rating.service.ts` 中的 Elo 计算逻辑 - 保留但停止调用

### B. 相关文件

- 现有模型: `/backend/prisma/schema.prisma`
- 现有服务: `/backend/src/contest/`
- 现有前端: `/console/src/views/contest/`
- 现有管理后台: `/management/src/views/contests/`

### C. 参考资源

- LeetCode 周赛规则: https://leetcode.com/contest/
- Codeforces 比赛系统: https://codeforces.com/contests
- AtCoder 比赛系统: https://atcoder.jp/contests

---

## 变更历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0 | 2026-03-14 | 初始设计 |
| 1.1 | 2026-03-14 | 根据审核反馈添加：迁移策略、枚举映射、API版本策略、WebSocket架构、错误处理、限流规范 |
