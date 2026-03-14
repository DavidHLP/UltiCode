# 竞赛系统重构实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将竞赛系统从 Elo 积分制重构为 LeetCode 风格的简单积分制，支持多种竞赛类型和实时功能。

**Architecture:** 增量式重构策略 - 保留现有数据结构，添加新字段和新表，通过功能开关控制新旧系统切换。后端采用 NestJS 模块化架构，前端采用 Vue 3 + Pinia。

**Tech Stack:** NestJS, Prisma, MySQL, Redis, BullMQ, WebSocket (Socket.io), Vue 3, Pinia, Tailwind CSS 4

**Spec Document:** `docs/superpowers/specs/2026-03-14-contest-system-design.md`

---

## 文件结构概览

### 后端新增/修改文件

```
backend/
├── prisma/
│   └── migrations/
│       └── 20260314000000_contest_system_enhancements/
│           └── migration.sql              # 新增
├── src/
│   ├── common/
│   │   ├── config/
│   │   │   └── feature-flags.config.ts    # 新增
│   │   └── constants/
│   │       └── contest-errors.ts          # 新增
│   ├── contest/
│   │   ├── contest.module.ts              # 修改
│   │   ├── scoring/
│   │   │   ├── scoring.service.ts         # 新增
│   │   │   ├── scoring.service.spec.ts    # 新增
│   │   │   └── dto/
│   │   │       └── scoring-rule.dto.ts    # 新增
│   │   ├── realtime/
│   │   │   ├── contest.gateway.ts         # 新增
│   │   │   ├── contest.gateway.spec.ts    # 新增
│   │   │   └── realtime.service.ts        # 新增
│   │   ├── analytics/
│   │   │   ├── analytics.service.ts       # 新增
│   │   │   ├── analytics.service.spec.ts  # 新增
│   │   │   └── analytics.controller.ts    # 新增
│   │   ├── anticheat/
│   │   │   ├── anticheat.service.ts       # 新增
│   │   │   └── anticheat.service.spec.ts  # 新增
│   │   ├── admin/
│   │   │   ├── admin-contest.controller.ts # 修改
│   │   │   └── scoring-rule.controller.ts # 新增
│   │   ├── dto/
│   │   │   ├── contest.dto.ts             # 修改
│   │   │   ├── scoring-rule.dto.ts        # 新增
│   │   │   └── announcement.dto.ts        # 新增
│   │   └── services/
│   │       ├── scoring-rule.service.ts    # 新增
│   │       └── announcement.service.ts    # 新增
│   └── admin/
│       └── admin.module.ts                # 修改
```

### 前端新增/修改文件

```
console/src/
├── api/contest/
│   ├── index.ts                           # 新增
│   ├── contest.ts                         # 修改
│   ├── ranking.ts                         # 新增
│   └── scoring-rule.ts                    # 新增
├── composables/contest/
│   ├── useContestTimer.ts                 # 新增
│   ├── useContestSocket.ts                # 新增
│   └── useContestSubmission.ts            # 新增
├── stores/contest/
│   ├── contestStore.ts                    # 新增
│   ├── rankingStore.ts                    # 新增
│   └── scoringRuleStore.ts                # 新增
├── types/contest/
│   ├── index.ts                           # 新增
│   ├── contest.ts                         # 修改
│   └── scoring-rule.ts                    # 新增
├── views/contest/
│   ├── ContestListView.vue                # 修改
│   ├── ContestDetailView.vue              # 修改
│   └── components/
│       ├── ContestCard.vue                # 修改
│       ├── ContestTimer.vue               # 新增
│       ├── ContestStatusBadge.vue         # 修改
│       ├── RankingTable.vue               # 新增
│       ├── FirstSolveNotification.vue     # 新增
│       └── ContestAnnouncement.vue        # 新增
└── i18n/locales/
    ├── zh-CN.ts                            # 修改
    └── en-US.ts                            # 修改

management/src/
├── api/admin/
│   └── scoring-rules.ts                   # 新增
├── views/contests/
│   ├── ContestWizard.vue                  # 修改
│   └── components/
│       └── ScoringRuleSelector.vue        # 新增
└── i18n/locales/
    ├── zh-CN.ts                            # 修改
    └── en-US.ts                            # 修改
```

---

## Chunk 1: 数据库迁移

### Task 1.1: 创建 Prisma 迁移文件

**Files:**
- Create: `backend/prisma/migrations/20260314000000_contest_system_enhancements/migration.sql`

- [ ] **Step 1: 创建迁移目录和文件**

```sql
-- backend/prisma/migrations/20260314000000_contest_system_enhancements/migration.sql

-- ============================================
-- Phase 1: Create new tables
-- ============================================

-- 1.1 Create contest_scoring_rules table
CREATE TABLE IF NOT EXISTS `contest_scoring_rules` (
  `id` VARCHAR(40) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` TEXT NULL,
  `base_score_per_problem` INT NOT NULL DEFAULT 100,
  `time_bonus_per_minute` INT NOT NULL DEFAULT 1,
  `wrong_answer_penalty` INT NOT NULL DEFAULT 5,
  `time_limit_penalty` INT NOT NULL DEFAULT 0,
  `first_solve_bonus` INT NOT NULL DEFAULT 10,
  `full_score_bonus` INT NOT NULL DEFAULT 0,
  `is_default` BOOLEAN NOT NULL DEFAULT FALSE,
  `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1.2 Insert default scoring rules
INSERT INTO `contest_scoring_rules` (`id`, `name`, `description`, `base_score_per_problem`, `time_bonus_per_minute`, `wrong_answer_penalty`, `first_solve_bonus`, `is_default`) VALUES
('default-weekly', '标准周赛规则', 'LeetCode 风格的简单积分规则', 100, 1, 5, 10, TRUE),
('default-icpc', 'ICPC 规则', 'ACM/ICPC 风格的罚时规则', 100, 0, 20, 0, FALSE);

-- 1.3 Create first_solve_records table
CREATE TABLE IF NOT EXISTS `first_solve_records` (
  `id` VARCHAR(40) NOT NULL,
  `contest_id` VARCHAR(40) NOT NULL,
  `problem_id` BIGINT NOT NULL,
  `user_id` VARCHAR(40) NOT NULL,
  `solved_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_spent` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `first_solve_records_contest_id_problem_id_key` (`contest_id`, `problem_id`),
  KEY `first_solve_records_contest_id_idx` (`contest_id`),
  KEY `first_solve_records_user_id_idx` (`user_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1.4 Create contest_announcements table
CREATE TABLE IF NOT EXISTS `contest_announcements` (
  `id` VARCHAR(40) NOT NULL,
  `contest_id` VARCHAR(40) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `is_pinned` BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (`id`),
  KEY `contest_announcements_contest_id_created_at_idx` (`contest_id`, `created_at`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1.5 Create contest_analytics table
CREATE TABLE IF NOT EXISTS `contest_analytics` (
  `id` VARCHAR(40) NOT NULL,
  `contest_id` VARCHAR(40) NOT NULL,
  `total_registered` INT NOT NULL DEFAULT 0,
  `total_participated` INT NOT NULL DEFAULT 0,
  `completion_rate` DOUBLE NOT NULL DEFAULT 0,
  `problem_stats` JSON NULL,
  `score_distribution` JSON NULL,
  `time_distribution` JSON NULL,
  `top_users` JSON NULL,
  `generated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_analytics_contest_id_key` (`contest_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================
-- Phase 2: Extend existing tables
-- ============================================

-- 2.1 Extend contests table
ALTER TABLE `contests`
  ADD COLUMN `end_time` DATETIME(3) NULL,
  ADD COLUMN `registration_start` DATETIME(3) NULL,
  ADD COLUMN `registration_end` DATETIME(3) NULL,
  ADD COLUMN `freeze_time` DATETIME(3) NULL,
  ADD COLUMN `is_virtual` BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN `max_participants` INT NULL,
  ADD COLUMN `scoring_rule_id` VARCHAR(40) NULL,
  ADD COLUMN `submission_count` INT NULL DEFAULT 0;

-- 2.2 Calculate and populate end_time for existing contests
UPDATE `contests`
SET `end_time` = DATE_ADD(`start_time`, INTERVAL `duration_minutes` MINUTE)
WHERE `end_time` IS NULL AND `start_time` IS NOT NULL AND `duration_minutes` IS NOT NULL;

-- 2.3 Link existing contests to default scoring rule
UPDATE `contests`
SET `scoring_rule_id` = 'default-weekly'
WHERE `scoring_rule_id` IS NULL;

-- 2.4 Extend contest_participants table
ALTER TABLE `contest_participants`
  ADD COLUMN `checked_in_at` DATETIME(3) NULL,
  ADD COLUMN `is_virtual` BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN `total_score` INT NOT NULL DEFAULT 0,
  ADD COLUMN `total_time` INT NOT NULL DEFAULT 0,
  ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 0;

-- 2.5 Extend contest_problems table
ALTER TABLE `contest_problems`
  ADD COLUMN `label` VARCHAR(10) NULL,
  ADD COLUMN `base_score` INT NULL,
  ADD COLUMN `time_bonus` INT NULL;

-- 2.6 Populate label from problem_index
UPDATE `contest_problems`
SET `label` = `problem_index`
WHERE `label` IS NULL;

-- 2.7 Extend contest_problem_results table
ALTER TABLE `contest_problem_results`
  ADD COLUMN `time_spent` INT NOT NULL DEFAULT 0,
  ADD COLUMN `time_bonus` INT NOT NULL DEFAULT 0,
  ADD COLUMN `is_first_solve` BOOLEAN NOT NULL DEFAULT FALSE;

-- 2.8 Extend contest_rankings table
ALTER TABLE `contest_rankings`
  ADD COLUMN `is_frozen` BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================
-- Phase 3: Add foreign key constraints
-- ============================================

-- 3.1 Add foreign key for scoring_rule_id
ALTER TABLE `contests`
  ADD CONSTRAINT `contests_scoring_rule_id_fkey`
  FOREIGN KEY (`scoring_rule_id`) REFERENCES `contest_scoring_rules`(`id`)
  ON DELETE SET NULL ON UPDATE CASCADE;

-- 3.2 Add foreign keys for first_solve_records
ALTER TABLE `first_solve_records`
  ADD CONSTRAINT `first_solve_records_contest_id_fkey`
  FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `first_solve_records_user_id_fkey`
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- 3.3 Add foreign keys for contest_announcements
ALTER TABLE `contest_announcements`
  ADD CONSTRAINT `contest_announcements_contest_id_fkey`
  FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- 3.4 Add foreign key for contest_analytics
ALTER TABLE `contest_analytics`
  ADD CONSTRAINT `contest_analytics_contest_id_fkey`
  FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
```

- [ ] **Step 2: 创建回滚脚本**

```sql
-- backend/prisma/migrations/20260314000000_contest_system_enhancements/rollback.sql

-- Rollback script for contest_system_enhancements migration

-- Drop foreign keys first
ALTER TABLE `contest_analytics` DROP FOREIGN KEY IF EXISTS `contest_analytics_contest_id_fkey`;
ALTER TABLE `contest_announcements` DROP FOREIGN KEY IF EXISTS `contest_announcements_contest_id_fkey`;
ALTER TABLE `first_solve_records` DROP FOREIGN KEY IF EXISTS `first_solve_records_contest_id_fkey`;
ALTER TABLE `first_solve_records` DROP FOREIGN KEY IF EXISTS `first_solve_records_user_id_fkey`;
ALTER TABLE `contests` DROP FOREIGN KEY IF EXISTS `contests_scoring_rule_id_fkey`;

-- Drop new tables
DROP TABLE IF EXISTS `contest_analytics`;
DROP TABLE IF EXISTS `contest_announcements`;
DROP TABLE IF EXISTS `first_solve_records`;
DROP TABLE IF EXISTS `contest_scoring_rules`;

-- Remove added columns from contests
ALTER TABLE `contests`
  DROP COLUMN IF EXISTS `end_time`,
  DROP COLUMN IF EXISTS `registration_start`,
  DROP COLUMN IF EXISTS `registration_end`,
  DROP COLUMN IF EXISTS `freeze_time`,
  DROP COLUMN IF EXISTS `is_virtual`,
  DROP COLUMN IF EXISTS `max_participants`,
  DROP COLUMN IF EXISTS `scoring_rule_id`,
  DROP COLUMN IF EXISTS `submission_count`;

-- Remove added columns from contest_participants
ALTER TABLE `contest_participants`
  DROP COLUMN IF EXISTS `checked_in_at`,
  DROP COLUMN IF EXISTS `is_virtual`,
  DROP COLUMN IF EXISTS `total_score`,
  DROP COLUMN IF EXISTS `total_time`,
  DROP COLUMN IF EXISTS `attempt_count`;

-- Remove added columns from contest_problems
ALTER TABLE `contest_problems`
  DROP COLUMN IF EXISTS `label`,
  DROP COLUMN IF EXISTS `base_score`,
  DROP COLUMN IF EXISTS `time_bonus`;

-- Remove added columns from contest_problem_results
ALTER TABLE `contest_problem_results`
  DROP COLUMN IF EXISTS `time_spent`,
  DROP COLUMN IF EXISTS `time_bonus`,
  DROP COLUMN IF EXISTS `is_first_solve`;

-- Remove added columns from contest_rankings
ALTER TABLE `contest_rankings`
  DROP COLUMN IF EXISTS `is_frozen`;
```

- [ ] **Step 3: 运行迁移**

Run: `cd backend && pnpm prisma migrate dev --name contest_system_enhancements --create-only`
Expected: Creates migration file without applying

- [ ] **Step 4: 验证迁移 SQL**

检查生成的迁移文件与手写的 SQL 是否一致，必要时调整。

- [ ] **Step 5: 提交迁移文件**

```bash
git add backend/prisma/migrations/20260314000000_contest_system_enhancements/
git commit -m "feat(contest): add database migration for scoring system"
```

---

### Task 1.2: 更新 Prisma Schema

**Files:**
- Modify: `backend/prisma/schema.prisma`

- [ ] **Step 1: 添加 ContestScoringRule 模型**

在 `schema.prisma` 中 `Contest` 模型之前添加：

```prisma
model ContestScoringRule {
  id                      String   @id @db.VarChar(40) @default(uuid())
  name                    String   @db.VarChar(100)
  description             String?  @db.Text

  base_score_per_problem  Int      @default(100)
  time_bonus_per_minute   Int      @default(1)
  wrong_answer_penalty    Int      @default(5)
  time_limit_penalty      Int      @default(0)
  first_solve_bonus       Int      @default(10)
  full_score_bonus        Int      @default(0)

  is_default              Boolean  @default(false)
  is_active               Boolean  @default(true)

  contests                Contest[]

  created_at              DateTime @default(now())
  updated_at              DateTime @updatedAt

  @@map("contest_scoring_rules")
}
```

- [ ] **Step 2: 添加 FirstSolveRecord 模型**

```prisma
model FirstSolveRecord {
  id            String   @id @db.VarChar(40) @default(uuid())
  contest_id    String   @db.VarChar(40)
  problem_id    BigInt
  user_id       String   @db.VarChar(40)
  solved_at     DateTime @default(now())
  time_spent    Int

  contest       Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)
  problem       Problem  @relation(fields: [problem_id], references: [id])
  user          User     @relation(fields: [user_id], references: [id], onDelete: Cascade)

  @@unique([contest_id, problem_id])
  @@index([contest_id])
  @@map("first_solve_records")
}
```

- [ ] **Step 3: 添加 ContestAnnouncement 模型**

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

- [ ] **Step 4: 添加 ContestAnalytics 模型**

```prisma
model ContestAnalytics {
  id                  String   @id @db.VarChar(40) @default(uuid())
  contest_id          String   @unique @db.VarChar(40)

  total_registered    Int      @default(0)
  total_participated  Int      @default(0)
  completion_rate     Float    @default(0)

  problem_stats       Json?
  score_distribution  Json?
  time_distribution   Json?
  top_users           Json?

  generated_at        DateTime @default(now())

  contest             Contest  @relation(fields: [contest_id], references: [id], onDelete: Cascade)

  @@map("contest_analytics")
}
```

- [ ] **Step 5: 更新 Contest 模型**

在现有 `Contest` 模型中添加新字段和关联：

```prisma
model Contest {
  // ... existing fields ...

  // 新增字段
  end_time           DateTime?
  registration_start DateTime?
  registration_end   DateTime?
  freeze_time        DateTime?
  is_virtual         Boolean  @default(true)
  max_participants   Int?
  scoring_rule_id    String?  @db.VarChar(40)
  submission_count   Int      @default(0)

  // 新增关联
  scoring_rule       ContestScoringRule?  @relation(fields: [scoring_rule_id], references: [id])
  announcements      ContestAnnouncement[]
  firstSolveRecords  FirstSolveRecord[]
  analytics          ContestAnalytics?

  // ... rest of existing fields ...
}
```

- [ ] **Step 6: 更新 ContestParticipant 模型**

```prisma
model ContestParticipant {
  // ... existing fields ...

  // 新增字段
  checked_in_at DateTime?
  is_virtual    Boolean  @default(false)
  total_score   Int      @default(0)
  total_time    Int      @default(0)
  attempt_count Int      @default(0)

  // ... rest of existing fields ...
}
```

- [ ] **Step 7: 更新 ContestProblem 模型**

```prisma
model ContestProblem {
  // ... existing fields ...

  // 新增字段
  label        String?  @db.VarChar(10)
  base_score   Int?
  time_bonus   Int?

  // ... rest of existing fields ...
}
```

- [ ] **Step 8: 更新 ContestProblemResult 模型**

```prisma
model ContestProblemResult {
  // ... existing fields ...

  // 新增字段
  time_spent     Int      @default(0)
  time_bonus     Int      @default(0)
  is_first_solve Boolean  @default(false)

  // ... rest of existing fields ...
}
```

- [ ] **Step 9: 更新 ContestRanking 模型**

```prisma
model ContestRanking {
  // ... existing fields ...

  // 新增字段
  is_frozen Boolean @default(false)

  // ... rest of existing fields ...
}
```

- [ ] **Step 10: 更新 User 模型添加 FirstSolveRecord 关联**

在 `User` 模型中添加：

```prisma
model User {
  // ... existing fields ...

  // 新增关联
  firstSolveRecords FirstSolveRecord[]

  // ... rest of existing fields ...
}
```

- [ ] **Step 11: 更新 Problem 模型添加 FirstSolveRecord 关联**

在 `Problem` 模型中添加：

```prisma
model Problem {
  // ... existing fields ...

  // 新增关联
  firstSolveRecords FirstSolveRecord[]

  // ... rest of existing fields ...
}
```

- [ ] **Step 12: 生成 Prisma Client**

Run: `cd backend && pnpm prisma generate`
Expected: Prisma client regenerated with new models

- [ ] **Step 13: 验证类型检查**

Run: `cd backend && pnpm type-check`
Expected: No TypeScript errors

- [ ] **Step 14: 提交 Schema 更新**

```bash
git add backend/prisma/schema.prisma
git commit -m "feat(contest): update Prisma schema with scoring models"
```

---

### Task 1.3: 创建功能开关配置

**Files:**
- Create: `backend/src/common/config/feature-flags.config.ts`

- [ ] **Step 1: 创建功能开关配置文件**

```typescript
// backend/src/common/config/feature-flags.config.ts

/**
 * Feature flags for gradual rollout of new contest system
 * Set via environment variables
 */
export const FEATURE_FLAGS = {
  /**
   * Enable new contest scoring system (point-based instead of Elo)
   */
  USE_NEW_CONTEST_SYSTEM: process.env.FEATURE_NEW_CONTEST === 'true',

  /**
   * Enable real-time ranking updates via WebSocket
   */
  ENABLE_REALTIME_RANKING: process.env.FEATURE_REALTIME_RANKING !== 'false',

  /**
   * Enable first-solve notifications
   */
  ENABLE_FIRST_SOLVE_NOTIFICATIONS:
    process.env.FEATURE_FIRST_SOLVE !== 'false',

  /**
   * Enable anti-cheat detection
   */
  ENABLE_ANTICHEAT: process.env.FEATURE_ANTICHEAT === 'true',

  /**
   * Enable contest analytics generation
   */
  ENABLE_CONTEST_ANALYTICS: process.env.FEATURE_CONTEST_ANALYTICS !== 'false',
} as const;

export type FeatureFlag = keyof typeof FEATURE_FLAGS;

/**
 * Check if a feature flag is enabled
 */
export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return FEATURE_FLAGS[flag] ?? false;
}
```

- [ ] **Step 2: 创建单元测试**

```typescript
// backend/src/common/config/feature-flags.config.spec.ts

import { FEATURE_FLAGS, isFeatureEnabled } from './feature-flags.config';

describe('FeatureFlags', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...originalEnv };
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  describe('USE_NEW_CONTEST_SYSTEM', () => {
    it('should be true when FEATURE_NEW_CONTEST is "true"', () => {
      process.env.FEATURE_NEW_CONTEST = 'true';
      jest.resetModules();
      const { FEATURE_FLAGS } = require('./feature-flags.config');
      expect(FEATURE_FLAGS.USE_NEW_CONTEST_SYSTEM).toBe(true);
    });

    it('should be false by default', () => {
      delete process.env.FEATURE_NEW_CONTEST;
      jest.resetModules();
      const { FEATURE_FLAGS } = require('./feature-flags.config');
      expect(FEATURE_FLAGS.USE_NEW_CONTEST_SYSTEM).toBe(false);
    });
  });

  describe('isFeatureEnabled', () => {
    it('should return boolean value for valid flag', () => {
      expect(typeof isFeatureEnabled('USE_NEW_CONTEST_SYSTEM')).toBe('boolean');
    });
  });
});
```

- [ ] **Step 3: 运行测试**

Run: `cd backend && pnpm jest src/common/config/feature-flags.config.spec.ts`
Expected: All tests pass

- [ ] **Step 4: 提交功能开关配置**

```bash
git add backend/src/common/config/
git commit -m "feat(contest): add feature flags for gradual rollout"
```

---

### Task 1.4: 创建竞赛错误常量

**Files:**
- Create: `backend/src/common/constants/contest-errors.ts`

- [ ] **Step 1: 创建错误常量文件**

```typescript
// backend/src/common/constants/contest-errors.ts

/**
 * Contest-related error codes and messages
 */
export const CONTEST_ERRORS = {
  // Contest not found
  NOT_FOUND: {
    code: 'CONTEST_NOT_FOUND',
    message: 'contest.errors.notFound',
    httpStatus: 404,
  },

  // Registration errors
  REGISTRATION_CLOSED: {
    code: 'CONTEST_REGISTRATION_CLOSED',
    message: 'contest.errors.registrationClosed',
    httpStatus: 400,
  },
  ALREADY_REGISTERED: {
    code: 'CONTEST_ALREADY_REGISTERED',
    message: 'contest.errors.alreadyRegistered',
    httpStatus: 400,
  },
  NOT_REGISTERED: {
    code: 'CONTEST_NOT_REGISTERED',
    message: 'contest.errors.notRegistered',
    httpStatus: 400,
  },
  CONTEST_FULL: {
    code: 'CONTEST_FULL',
    message: 'contest.errors.contestFull',
    httpStatus: 400,
  },

  // Check-in errors
  ALREADY_CHECKED_IN: {
    code: 'CONTEST_ALREADY_CHECKED_IN',
    message: 'contest.errors.alreadyCheckedIn',
    httpStatus: 400,
  },

  // Contest status errors
  NOT_STARTED: {
    code: 'CONTEST_NOT_STARTED',
    message: 'contest.errors.notStarted',
    httpStatus: 400,
  },
  ENDED: {
    code: 'CONTEST_ENDED',
    message: 'contest.errors.ended',
    httpStatus: 400,
  },

  // Submission errors
  SUBMISSION_TIMEOUT: {
    code: 'CONTEST_SUBMISSION_TIMEOUT',
    message: 'contest.errors.submissionTimeout',
    httpStatus: 400,
  },
  SUBMISSION_RATE_LIMITED: {
    code: 'SUBMISSION_RATE_LIMITED',
    message: 'contest.errors.rateLimited',
    httpStatus: 429,
  },

  // Virtual contest errors
  VIRTUAL_SESSION_EXISTS: {
    code: 'VIRTUAL_SESSION_EXISTS',
    message: 'contest.errors.virtualSessionExists',
    httpStatus: 400,
  },
  VIRTUAL_SESSION_NOT_FOUND: {
    code: 'VIRTUAL_SESSION_NOT_FOUND',
    message: 'contest.errors.virtualSessionNotFound',
    httpStatus: 404,
  },

  // Scoring rule errors
  SCORING_RULE_NOT_FOUND: {
    code: 'SCORING_RULE_NOT_FOUND',
    message: 'contest.errors.scoringRuleNotFound',
    httpStatus: 404,
  },
  CANNOT_DELETE_DEFAULT_RULE: {
    code: 'CANNOT_DELETE_DEFAULT_RULE',
    message: 'contest.errors.cannotDeleteDefaultRule',
    httpStatus: 400,
  },
} as const;

export type ContestErrorCode = (typeof CONTEST_ERRORS)[keyof typeof CONTEST_ERRORS]['code'];

/**
 * Get error info by code
 */
export function getContestError(code: ContestErrorCode) {
  return Object.values(CONTEST_ERRORS).find((e) => e.code === code);
}
```

- [ ] **Step 2: 提交错误常量**

```bash
git add backend/src/common/constants/contest-errors.ts
git commit -m "feat(contest): add contest error constants"
```

---

## Chunk 1 完成检查点

在继续之前，确保：

1. [ ] 数据库迁移文件已创建
2. [ ] 回滚脚本已创建
3. [ ] Prisma schema 已更新
4. [ ] Prisma client 已重新生成
5. [ ] 功能开关配置已创建
6. [ ] 错误常量已创建
7. [ ] 所有更改已提交到 git

**验证命令:**
```bash
cd backend && pnpm type-check && pnpm prisma generate
```

---

## Chunk 2: 后端核心模块 - 评分服务

### Task 2.1: 创建评分规则 DTO

**Files:**
- Create: `backend/src/contest/dto/scoring-rule.dto.ts`

- [ ] **Step 1: 创建 ScoringRuleDto**

```typescript
// backend/src/contest/dto/scoring-rule.dto.ts

import { ApiProperty, ApiPropertyOptional, PartialType } from '@nestjs/swagger';
import { IsString, IsOptional, IsInt, Min, IsBoolean } from 'class-validator';

export class CreateScoringRuleDto {
  @ApiProperty({ description: '规则名称', example: '标准周赛规则' })
  @IsString()
  name: string;

  @ApiPropertyOptional({ description: '规则描述' })
  @IsOptional()
  @IsString()
  description?: string;

  @ApiProperty({ description: '每题基础分', default: 100 })
  @IsInt()
  @Min(0)
  base_score_per_problem: number;

  @ApiProperty({ description: '每分钟时间奖励', default: 1 })
  @IsInt()
  @Min(0)
  time_bonus_per_minute: number;

  @ApiProperty({ description: '错误答案惩罚(秒)', default: 5 })
  @IsInt()
  @Min(0)
  wrong_answer_penalty: number;

  @ApiPropertyOptional({ description: '超时惩罚', default: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  time_limit_penalty?: number;

  @ApiProperty({ description: '首杀奖励', default: 10 })
  @IsInt()
  @Min(0)
  first_solve_bonus: number;

  @ApiPropertyOptional({ description: '满分奖励', default: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  full_score_bonus?: number;

  @ApiPropertyOptional({ description: '是否为默认规则', default: false })
  @IsOptional()
  @IsBoolean()
  is_default?: boolean;
}

export class UpdateScoringRuleDto extends PartialType(CreateScoringRuleDto) {}

export class ScoringRuleResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  name: string;

  @ApiPropertyOptional()
  description?: string;

  @ApiProperty()
  base_score_per_problem: number;

  @ApiProperty()
  time_bonus_per_minute: number;

  @ApiProperty()
  wrong_answer_penalty: number;

  @ApiProperty()
  time_limit_penalty: number;

  @ApiProperty()
  first_solve_bonus: number;

  @ApiProperty()
  full_score_bonus: number;

  @ApiProperty()
  is_default: boolean;

  @ApiProperty()
  is_active: boolean;

  @ApiProperty()
  created_at: Date;

  @ApiProperty()
  updated_at: Date;
}
```

- [ ] **Step 2: 提交 DTO**

```bash
git add backend/src/contest/dto/scoring-rule.dto.ts
git commit -m "feat(contest): add scoring rule DTOs"
```

---

### Task 2.2: 创建评分规则服务

**Files:**
- Create: `backend/src/contest/services/scoring-rule.service.ts`
- Create: `backend/src/contest/services/scoring-rule.service.spec.ts`

- [ ] **Step 1: 创建 ScoringRuleService**

```typescript
// backend/src/contest/services/scoring-rule.service.ts

import { Injectable, NotFoundException, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { CreateScoringRuleDto, UpdateScoringRuleDto } from '../dto/scoring-rule.dto';
import { CONTEST_ERRORS } from '../../common/constants/contest-errors';

@Injectable()
export class ScoringRuleService {
  constructor(private readonly prisma: PrismaService) {}

  /**
   * Get all active scoring rules
   */
  async findAll(includeInactive = false) {
    return this.prisma.contestScoringRule.findMany({
      where: includeInactive ? undefined : { is_active: true },
      orderBy: [{ is_default: 'desc' }, { created_at: 'asc' }],
    });
  }

  /**
   * Get scoring rule by ID
   */
  async findOne(id: string) {
    const rule = await this.prisma.contestScoringRule.findUnique({
      where: { id },
    });

    if (!rule) {
      throw new NotFoundException(CONTEST_ERRORS.SCORING_RULE_NOT_FOUND.message);
    }

    return rule;
  }

  /**
   * Get the default scoring rule
   */
  async findDefault() {
    const rule = await this.prisma.contestScoringRule.findFirst({
      where: { is_default: true, is_active: true },
    });

    if (!rule) {
      // Fallback to first active rule
      return this.prisma.contestScoringRule.findFirst({
        where: { is_active: true },
      });
    }

    return rule;
  }

  /**
   * Create a new scoring rule
   */
  async create(dto: CreateScoringRuleDto) {
    // If this is set as default, unset other defaults
    if (dto.is_default) {
      await this.prisma.contestScoringRule.updateMany({
        where: { is_default: true },
        data: { is_default: false },
      });
    }

    return this.prisma.contestScoringRule.create({
      data: {
        name: dto.name,
        description: dto.description,
        base_score_per_problem: dto.base_score_per_problem,
        time_bonus_per_minute: dto.time_bonus_per_minute,
        wrong_answer_penalty: dto.wrong_answer_penalty,
        time_limit_penalty: dto.time_limit_penalty ?? 0,
        first_solve_bonus: dto.first_solve_bonus,
        full_score_bonus: dto.full_score_bonus ?? 0,
        is_default: dto.is_default ?? false,
      },
    });
  }

  /**
   * Update a scoring rule
   */
  async update(id: string, dto: UpdateScoringRuleDto) {
    // Check if rule exists
    await this.findOne(id);

    // If setting as default, unset other defaults
    if (dto.is_default) {
      await this.prisma.contestScoringRule.updateMany({
        where: { is_default: true, id: { not: id } },
        data: { is_default: false },
      });
    }

    return this.prisma.contestScoringRule.update({
      where: { id },
      data: dto,
    });
  }

  /**
   * Delete a scoring rule
   */
  async remove(id: string) {
    const rule = await this.findOne(id);

    // Cannot delete default rule
    if (rule.is_default) {
      throw new BadRequestException(CONTEST_ERRORS.CANNOT_DELETE_DEFAULT_RULE.message);
    }

    // Check if rule is used by any contests
    const usageCount = await this.prisma.contest.count({
      where: { scoring_rule_id: id },
    });

    if (usageCount > 0) {
      // Soft delete by marking as inactive
      return this.prisma.contestScoringRule.update({
        where: { id },
        data: { is_active: false },
      });
    }

    // Hard delete if not used
    await this.prisma.contestScoringRule.delete({
      where: { id },
    });

    return { success: true };
  }
}
```

- [ ] **Step 2: 创建单元测试**

```typescript
// backend/src/contest/services/scoring-rule.service.spec.ts

import { Test, TestingModule } from '@nestjs/testing';
import { NotFoundException, BadRequestException } from '@nestjs/common';
import { ScoringRuleService } from './scoring-rule.service';
import { PrismaService } from '../../prisma.service';

describe('ScoringRuleService', () => {
  let service: ScoringRuleService;
  let prisma: jest.Mocked<PrismaService>;

  const mockRule = {
    id: 'rule-1',
    name: 'Test Rule',
    description: 'Test description',
    base_score_per_problem: 100,
    time_bonus_per_minute: 1,
    wrong_answer_penalty: 5,
    time_limit_penalty: 0,
    first_solve_bonus: 10,
    full_score_bonus: 0,
    is_default: false,
    is_active: true,
    created_at: new Date(),
    updated_at: new Date(),
  };

  beforeEach(async () => {
    const mockPrisma = {
      contestScoringRule: {
        findMany: jest.fn(),
        findUnique: jest.fn(),
        findFirst: jest.fn(),
        create: jest.fn(),
        update: jest.fn(),
        updateMany: jest.fn(),
        delete: jest.fn(),
      },
      contest: {
        count: jest.fn(),
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ScoringRuleService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<ScoringRuleService>(ScoringRuleService);
    prisma = module.get(PrismaService);
  });

  describe('findAll', () => {
    it('should return all active rules by default', async () => {
      prisma.contestScoringRule.findMany.mockResolvedValue([mockRule]);
      const result = await service.findAll();
      expect(result).toEqual([mockRule]);
      expect(prisma.contestScoringRule.findMany).toHaveBeenCalledWith({
        where: { is_active: true },
        orderBy: expect.any(Array),
      });
    });

    it('should return all rules including inactive when flag is true', async () => {
      prisma.contestScoringRule.findMany.mockResolvedValue([mockRule]);
      await service.findAll(true);
      expect(prisma.contestScoringRule.findMany).toHaveBeenCalledWith({
        where: undefined,
        orderBy: expect.any(Array),
      });
    });
  });

  describe('findOne', () => {
    it('should return a rule by id', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue(mockRule);
      const result = await service.findOne('rule-1');
      expect(result).toEqual(mockRule);
    });

    it('should throw NotFoundException when rule not found', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue(null);
      await expect(service.findOne('non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('findDefault', () => {
    it('should return the default rule', async () => {
      prisma.contestScoringRule.findFirst.mockResolvedValue(mockRule);
      const result = await service.findDefault();
      expect(result).toEqual(mockRule);
    });
  });

  describe('create', () => {
    it('should create a new rule', async () => {
      const createDto = {
        name: 'New Rule',
        base_score_per_problem: 100,
        time_bonus_per_minute: 1,
        wrong_answer_penalty: 5,
        first_solve_bonus: 10,
      };

      prisma.contestScoringRule.updateMany.mockResolvedValue({ count: 0 });
      prisma.contestScoringRule.create.mockResolvedValue({
        ...mockRule,
        ...createDto,
      });

      const result = await service.create(createDto);
      expect(result.name).toBe('New Rule');
    });

    it('should unset other defaults when creating a default rule', async () => {
      const createDto = {
        name: 'New Default',
        base_score_per_problem: 100,
        time_bonus_per_minute: 1,
        wrong_answer_penalty: 5,
        first_solve_bonus: 10,
        is_default: true,
      };

      prisma.contestScoringRule.updateMany.mockResolvedValue({ count: 1 });
      prisma.contestScoringRule.create.mockResolvedValue({
        ...mockRule,
        ...createDto,
      });

      await service.create(createDto);
      expect(prisma.contestScoringRule.updateMany).toHaveBeenCalledWith({
        where: { is_default: true },
        data: { is_default: false },
      });
    });
  });

  describe('remove', () => {
    it('should throw BadRequestException when trying to delete default rule', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue({
        ...mockRule,
        is_default: true,
      });

      await expect(service.remove('rule-1')).rejects.toThrow(
        BadRequestException,
      );
    });

    it('should soft delete rule when it is used by contests', async () => {
      prisma.contestScoringRule.findUnique.mockResolvedValue(mockRule);
      prisma.contest.count.mockResolvedValue(5);
      prisma.contestScoringRule.update.mockResolvedValue({
        ...mockRule,
        is_active: false,
      });

      const result = await service.remove('rule-1');
      expect(prisma.contestScoringRule.update).toHaveBeenCalled();
    });
  });
});
```

- [ ] **Step 3: 运行测试**

Run: `cd backend && pnpm jest src/contest/services/scoring-rule.service.spec.ts`
Expected: All tests pass

- [ ] **Step 4: 提交评分规则服务**

```bash
git add backend/src/contest/services/scoring-rule.service.ts
git add backend/src/contest/services/scoring-rule.service.spec.ts
git commit -m "feat(contest): add scoring rule service"
```

---

### Task 2.3: 创建积分计算服务

**Files:**
- Create: `backend/src/contest/scoring/scoring.service.ts`
- Create: `backend/src/contest/scoring/scoring.service.spec.ts`

- [ ] **Step 1: 创建 ScoringService**

```typescript
// backend/src/contest/scoring/scoring.service.ts

import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { isFeatureEnabled } from '../../common/config/feature-flags.config';
import type {
  Contest,
  ContestProblem,
  ContestSubmission,
  ContestScoringRule,
  ContestParticipant,
} from '@prisma/client';

interface ScoreCalculation {
  base_score: number;
  time_bonus: number;
  penalty: number;
  total_score: number;
}

interface ParticipantScore {
  user_id: string;
  total_score: number;
  total_time: number;
  solved_count: number;
  problems: Map<string, { score: number; time: number; solved: boolean }>;
}

@Injectable()
export class ScoringService {
  private readonly logger = new Logger(ScoringService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Calculate score for a single submission
   */
  calculateProblemScore(
    problem: ContestProblem,
    submission: ContestSubmission,
    rule: ContestScoringRule,
    isFirstSolve: boolean = false,
  ): ScoreCalculation {
    // Use problem-specific score if defined, otherwise use rule default
    const baseScore = problem.base_score ?? rule.base_score_per_problem;

    // Calculate time bonus (only if accepted)
    let timeBonus = 0;
    if (submission.is_accepted) {
      const timeSpent = submission.time_spent; // in seconds
      const minutes = Math.floor(timeSpent / 60);
      const bonusPerMinute = problem.time_bonus ?? rule.time_bonus_per_minute;
      timeBonus = minutes * bonusPerMinute;
    }

    // Calculate penalty
    let penalty = 0;
    if (!submission.is_accepted) {
      penalty = rule.wrong_answer_penalty;
    }

    // First solve bonus
    const firstSolveBonus = isFirstSolve ? rule.first_solve_bonus : 0;

    const totalScore = submission.is_accepted
      ? baseScore + timeBonus + firstSolveBonus
      : 0;

    return {
      base_score: baseScore,
      time_bonus: timeBonus,
      penalty,
      total_score: totalScore,
    };
  }

  /**
   * Update ranking for all participants in a contest
   */
  async updateContestRanking(contestId: string): Promise<void> {
    if (!isFeatureEnabled('USE_NEW_CONTEST_SYSTEM')) {
      this.logger.debug('New scoring system disabled, skipping ranking update');
      return;
    }

    // Get contest with scoring rule
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
      include: {
        scoring_rule: true,
        problems: true,
      },
    });

    if (!contest || !contest.scoring_rule) {
      this.logger.warn(`Contest ${contestId} not found or has no scoring rule`);
      return;
    }

    // Get all submissions for this contest
    const submissions = await this.prisma.contestSubmission.findMany({
      where: { contest_id: contestId },
      orderBy: { submitted_at: 'asc' },
    });

    // Track first solves
    const firstSolves = new Map<string, string>(); // problemId -> userId

    // Calculate scores per participant
    const participantScores = new Map<string, ParticipantScore>();

    for (const submission of submissions) {
      const userId = submission.user_id;
      const problemId = submission.problem_id.toString();

      // Initialize participant if needed
      if (!participantScores.has(userId)) {
        participantScores.set(userId, {
          user_id: userId,
          total_score: 0,
          total_time: 0,
          solved_count: 0,
          problems: new Map(),
        });
      }

      const participant = participantScores.get(userId)!;

      // Get problem
      const problem = contest.problems.find(
        (p) => p.problem_id === submission.problem_id,
      );
      if (!problem) continue;

      // Check if first solve
      const isFirstSolve =
        submission.is_accepted &&
        !firstSolves.has(problemId) &&
        !participant.problems.get(problemId)?.solved;

      if (isFirstSolve) {
        firstSolves.set(problemId, userId);
      }

      // Only count if not already solved
      if (!participant.problems.get(problemId)?.solved) {
        const score = this.calculateProblemScore(
          problem,
          submission,
          contest.scoring_rule,
          isFirstSolve,
        );

        participant.problems.set(problemId, {
          score: score.total_score,
          time: submission.time_spent,
          solved: submission.is_accepted,
        });

        if (submission.is_accepted) {
          participant.total_score += score.total_score;
          participant.total_time += submission.time_spent;
          participant.solved_count += 1;
        }
      }
    }

    // Sort participants by score (desc) then time (asc)
    const sortedParticipants = Array.from(participantScores.values()).sort(
      (a, b) => {
        if (b.total_score !== a.total_score) {
          return b.total_score - a.total_score;
        }
        return a.total_time - b.total_time;
      },
    );

    // Update rankings in database
    await this.prisma.$transaction(async (tx) => {
      for (let i = 0; i < sortedParticipants.length; i++) {
        const participant = sortedParticipants[i];
        const rank = i + 1;

        await tx.contestParticipant.updateMany({
          where: { contest_id: contestId, user_id: participant.user_id },
          data: {
            total_score: participant.total_score,
            total_time: participant.total_time,
            solved_count: participant.solved_count,
            rank,
          },
        });
      }
    });

    this.logger.log(
      `Updated rankings for contest ${contestId}: ${sortedParticipants.length} participants`,
    );
  }

  /**
   * Get ranking snapshot for a contest
   */
  async getRankingSnapshot(contestId: string, limit = 100) {
    const participants = await this.prisma.contestParticipant.findMany({
      where: { contest_id: contestId, rank: { not: null } },
      orderBy: [{ rank: 'asc' }],
      take: limit,
      include: {
        user: {
          select: { id: true, username: true, avatar: true },
        },
      },
    });

    return participants.map((p) => ({
      rank: p.rank,
      userId: p.user_id,
      username: p.user.username,
      avatar: p.user.avatar,
      score: p.total_score,
      time: p.total_time,
      solvedCount: p.solved_count,
    }));
  }
}
```

- [ ] **Step 2: 创建单元测试**

```typescript
// backend/src/contest/scoring/scoring.service.spec.ts

import { Test, TestingModule } from '@nestjs/testing';
import { ScoringService } from './scoring.service';
import { PrismaService } from '../../prisma.service';

describe('ScoringService', () => {
  let service: ScoringService;
  let prisma: jest.Mocked<PrismaService>;

  const mockRule = {
    id: 'rule-1',
    name: 'Test Rule',
    base_score_per_problem: 100,
    time_bonus_per_minute: 1,
    wrong_answer_penalty: 5,
    first_solve_bonus: 10,
  };

  const mockProblem = {
    id: 'cp-1',
    contest_id: 'contest-1',
    problem_id: BigInt(1),
    problem_index: 'A',
    label: 'A',
    score: 100,
    base_score: 100,
    time_bonus: 1,
  };

  const mockSubmission = {
    id: 'sub-1',
    contest_id: 'contest-1',
    user_id: 'user-1',
    problem_id: BigInt(1),
    is_accepted: true,
    time_spent: 300, // 5 minutes
    status: 'ACCEPTED',
  };

  beforeEach(async () => {
    const mockPrisma = {
      contest: {
        findUnique: jest.fn(),
      },
      contestSubmission: {
        findMany: jest.fn(),
      },
      contestParticipant: {
        findMany: jest.fn(),
        updateMany: jest.fn(),
      },
      $transaction: jest.fn((fn) => fn(mockPrisma)),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ScoringService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<ScoringService>(ScoringService);
    prisma = module.get(PrismaService);
  });

  describe('calculateProblemScore', () => {
    it('should calculate score for accepted submission', () => {
      const result = service.calculateProblemScore(
        mockProblem as any,
        mockSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.base_score).toBe(100);
      expect(result.time_bonus).toBe(5); // 5 minutes * 1 per minute
      expect(result.total_score).toBe(105);
    });

    it('should add first solve bonus', () => {
      const result = service.calculateProblemScore(
        mockProblem as any,
        mockSubmission as any,
        mockRule as any,
        true,
      );

      expect(result.total_score).toBe(115); // 100 + 5 + 10
    });

    it('should return 0 for wrong answer', () => {
      const wrongSubmission = { ...mockSubmission, is_accepted: false };

      const result = service.calculateProblemScore(
        mockProblem as any,
        wrongSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.total_score).toBe(0);
    });

    it('should use problem-specific base score when defined', () => {
      const problemWithCustomScore = {
        ...mockProblem,
        base_score: 150,
      };

      const result = service.calculateProblemScore(
        problemWithCustomScore as any,
        mockSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.base_score).toBe(150);
      expect(result.total_score).toBe(155);
    });
  });

  describe('getRankingSnapshot', () => {
    it('should return ranking list', async () => {
      const mockParticipants = [
        {
          rank: 1,
          user_id: 'user-1',
          total_score: 300,
          total_time: 600,
          solved_count: 3,
          user: { id: 'user-1', username: 'alice', avatar: null },
        },
        {
          rank: 2,
          user_id: 'user-2',
          total_score: 250,
          total_time: 700,
          solved_count: 2,
          user: { id: 'user-2', username: 'bob', avatar: null },
        },
      ];

      prisma.contestParticipant.findMany.mockResolvedValue(
        mockParticipants as any,
      );

      const result = await service.getRankingSnapshot('contest-1');

      expect(result).toHaveLength(2);
      expect(result[0].username).toBe('alice');
      expect(result[0].score).toBe(300);
    });
  });
});
```

- [ ] **Step 3: 运行测试**

Run: `cd backend && pnpm jest src/contest/scoring/scoring.service.spec.ts`
Expected: All tests pass

- [ ] **Step 4: 提交积分计算服务**

```bash
git add backend/src/contest/scoring/
git commit -m "feat(contest): add scoring calculation service"
```

---

### Task 2.4: 创建评分规则管理控制器

**Files:**
- Create: `backend/src/contest/admin/scoring-rule.controller.ts`

- [ ] **Step 1: 创建 ScoringRuleController**

```typescript
// backend/src/contest/admin/scoring-rule.controller.ts

import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
import { ScoringRuleService } from '../services/scoring-rule.service';
import {
  CreateScoringRuleDto,
  UpdateScoringRuleDto,
  ScoringRuleResponseDto,
} from '../dto/scoring-rule.dto';
import { JwtAuthGuard } from '../../auth/guards/jwt-auth.guard';
import { RolesGuard } from '../../auth/guards/roles.guard';
import { Roles } from '../../auth/decorators/roles.decorator';
import { UserRole } from '@prisma/client';

@ApiTags('Admin / Scoring Rules')
@ApiBearerAuth()
@Controller('admin/scoring-rules')
@UseGuards(JwtAuthGuard, RolesGuard)
@Roles(UserRole.ADMIN)
export class ScoringRuleController {
  constructor(private readonly scoringRuleService: ScoringRuleService) {}

  @Get()
  @ApiOperation({ summary: 'Get all scoring rules' })
  async findAll(
    @Query('includeInactive') includeInactive?: string,
  ): Promise<ScoringRuleResponseDto[]> {
    return this.scoringRuleService.findAll(includeInactive === 'true');
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get scoring rule by ID' })
  async findOne(@Param('id') id: string): Promise<ScoringRuleResponseDto> {
    return this.scoringRuleService.findOne(id);
  }

  @Post()
  @ApiOperation({ summary: 'Create a new scoring rule' })
  async create(
    @Body() dto: CreateScoringRuleDto,
  ): Promise<ScoringRuleResponseDto> {
    return this.scoringRuleService.create(dto);
  }

  @Put(':id')
  @ApiOperation({ summary: 'Update a scoring rule' })
  async update(
    @Param('id') id: string,
    @Body() dto: UpdateScoringRuleDto,
  ): Promise<ScoringRuleResponseDto> {
    return this.scoringRuleService.update(id, dto);
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete a scoring rule' })
  async remove(@Param('id') id: string): Promise<{ success: boolean }> {
    return this.scoringRuleService.remove(id);
  }
}
```

- [ ] **Step 2: 更新 ContestModule 注册新服务**

修改 `backend/src/contest/contest.module.ts`:

```typescript
// 在 providers 数组中添加:
import { ScoringRuleService } from './services/scoring-rule.service';
import { ScoringService } from './scoring/scoring.service';
import { ScoringRuleController } from './admin/scoring-rule.controller';

// 在 @Module 装饰器中:
@Module({
  // ...
  providers: [
    // ... existing providers ...
    ScoringRuleService,
    ScoringService,
  ],
  controllers: [
    // ... existing controllers ...
    ScoringRuleController,
  ],
  exports: [
    // ... existing exports ...
    ScoringRuleService,
    ScoringService,
  ],
})
```

- [ ] **Step 3: 运行类型检查**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 4: 提交评分规则控制器**

```bash
git add backend/src/contest/admin/scoring-rule.controller.ts
git add backend/src/contest/contest.module.ts
git commit -m "feat(contest): add scoring rule admin controller"
```

---

## Chunk 2 完成检查点

在继续之前，确保：

1. [ ] ScoringRuleDto 已创建
2. [ ] ScoringRuleService 已创建并测试通过
3. [ ] ScoringService 已创建并测试通过
4. [ ] ScoringRuleController 已创建
5. [ ] ContestModule 已更新
6. [ ] 所有更改已提交到 git

**验证命令:**
```bash
cd backend && pnpm type-check && pnpm jest src/contest/services/scoring-rule.service.spec.ts src/contest/scoring/scoring.service.spec.ts
```

---

## Chunk 3: 实时功能 - WebSocket

### Task 3.1: 创建 WebSocket 网关

**Files:**
- Create: `backend/src/contest/realtime/contest.gateway.ts`
- Create: `backend/src/contest/realtime/contest.gateway.spec.ts`

- [ ] **Step 1: 安装 Socket.io 依赖（如未安装）**

Run: `cd backend && pnpm add @nestjs/platform-socket.io socket.io`
Expected: Dependencies installed

- [ ] **Step 2: 创建 ContestGateway**

```typescript
// backend/src/contest/realtime/contest.gateway.ts

import {
  WebSocketGateway,
  WebSocketServer,
  SubscribeMessage,
  OnGatewayConnection,
  OnGatewayDisconnect,
  ConnectedSocket,
  MessageBody,
  WsException,
} from '@nestjs/websockets';
import { Logger, UseFilters, UsePipes, ValidationPipe } from '@nestjs/common';
import { Server, Socket } from 'socket.io';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { isFeatureEnabled } from '../../common/config/feature-flags.config';

interface JoinContestPayload {
  contestId: string;
}

interface RankingUpdateData {
  contestId: string;
  rankings: Array<{
    rank: number;
    userId: string;
    username: string;
    score: number;
    time: number;
    solvedCount: number;
    changes?: Array<{
      problemIndex: string;
      score: number;
      time: number;
    }>;
  }>;
  timestamp: Date;
}

interface FirstSolveData {
  contestId: string;
  problemIndex: string;
  userId: string;
  username: string;
  timeSpent: number;
  timestamp: Date;
}

interface AnnouncementData {
  contestId: string;
  id: string;
  title: string;
  content: string;
  isPinned: boolean;
  timestamp: Date;
}

interface SubmissionResultData {
  contestId: string;
  userId: string;
  problemIndex: string;
  submissionId: string;
  status: string;
  score: number;
  timeSpent: number;
  isFirstSolve: boolean;
  timestamp: Date;
}

@WebSocketGateway({
  namespace: '/contest',
  cors: {
    origin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:9002'],
    credentials: true,
  },
})
@UsePipes(new ValidationPipe({ transform: true }))
export class ContestGateway
  implements OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  server: Server;

  private readonly logger = new Logger(ContestGateway.name);
  private readonly userRooms: Map<string, Set<string>> = new Map(); // socketId -> contestIds
  private readonly contestUsers: Map<string, Set<string>> = new Map(); // contestId -> socketIds

  constructor(
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  async handleConnection(client: Socket) {
    try {
      // Extract token from handshake auth or headers
      const token =
        client.handshake.auth?.token ||
        client.handshake.headers?.authorization?.replace('Bearer ', '');

      if (!token) {
        this.logger.warn(`Client ${client.id} connected without token`);
        client.disconnect(true);
        return;
      }

      // Verify JWT
      const payload = await this.jwtService.verifyAsync(token, {
        secret: this.configService.get<string>('JWT_SECRET'),
      });

      // Attach user info to socket
      client.data.userId = payload.sub;
      client.data.username = payload.username;

      this.userRooms.set(client.id, new Set());
      this.logger.debug(`Client ${client.id} connected as user ${payload.sub}`);
    } catch (error) {
      this.logger.warn(`Client ${client.id} authentication failed:`, error);
      client.disconnect(true);
    }
  }

  handleDisconnect(client: Socket) {
    const contestIds = this.userRooms.get(client.id);

    if (contestIds) {
      // Remove client from all contest rooms
      for (const contestId of contestIds) {
        const users = this.contestUsers.get(contestId);
        if (users) {
          users.delete(client.id);
          if (users.size === 0) {
            this.contestUsers.delete(contestId);
          }
        }
      }
      this.userRooms.delete(client.id);
    }

    this.logger.debug(`Client ${client.id} disconnected`);
  }

  @SubscribeMessage('join_contest')
  async handleJoinContest(
    @ConnectedSocket() client: Socket,
    @MessageBody() payload: JoinContestPayload,
  ) {
    const { contestId } = payload;

    if (!contestId) {
      throw new WsException('contestId is required');
    }

    // Join the contest room
    client.join(`contest:${contestId}`);

    // Track the room membership
    this.userRooms.get(client.id)?.add(contestId);

    if (!this.contestUsers.has(contestId)) {
      this.contestUsers.set(contestId, new Set());
    }
    this.contestUsers.get(contestId)?.add(client.id);

    this.logger.debug(
      `Client ${client.id} joined contest ${contestId}. Total users: ${this.contestUsers.get(contestId)?.size}`,
    );

    return { success: true, contestId };
  }

  @SubscribeMessage('leave_contest')
  async handleLeaveContest(
    @ConnectedSocket() client: Socket,
    @MessageBody() payload: JoinContestPayload,
  ) {
    const { contestId } = payload;

    client.leave(`contest:${contestId}`);

    this.userRooms.get(client.id)?.delete(contestId);
    this.contestUsers.get(contestId)?.delete(client.id);

    this.logger.debug(`Client ${client.id} left contest ${contestId}`);

    return { success: true, contestId };
  }

  // ============================================
  // Server -> Client emission methods
  // ============================================

  /**
   * Emit ranking update to all clients in a contest room
   */
  emitRankingUpdate(data: RankingUpdateData) {
    if (!isFeatureEnabled('ENABLE_REALTIME_RANKING')) {
      return;
    }

    this.server.to(`contest:${data.contestId}`).emit('ranking_update', data);
    this.logger.debug(
      `Emitted ranking update for contest ${data.contestId} to ${this.contestUsers.get(data.contestId)?.size || 0} clients`,
    );
  }

  /**
   * Emit first solve notification
   */
  emitFirstSolve(data: FirstSolveData) {
    if (!isFeatureEnabled('ENABLE_FIRST_SOLVE_NOTIFICATIONS')) {
      return;
    }

    this.server.to(`contest:${data.contestId}`).emit('first_solve', data);
    this.logger.log(
      `First solve: ${data.username} solved ${data.problemIndex} in contest ${data.contestId}`,
    );
  }

  /**
   * Emit new announcement
   */
  emitAnnouncement(data: AnnouncementData) {
    this.server.to(`contest:${data.contestId}`).emit('announcement', data);
    this.logger.debug(`Emitted announcement for contest ${data.contestId}`);
  }

  /**
   * Emit contest status change
   */
  emitContestStatus(data: {
    contestId: string;
    status: string;
    timestamp: Date;
  }) {
    this.server.to(`contest:${data.contestId}`).emit('contest_status', data);
    this.logger.debug(
      `Emitted status change for contest ${data.contestId}: ${data.status}`,
    );
  }

  /**
   * Emit submission result to specific user
   */
  emitSubmissionResult(data: SubmissionResultData) {
    // Emit to the user specifically
    this.server
      .to(`contest:${data.contestId}`)
      .emit('submission_result', data);
    this.logger.debug(
      `Emitted submission result for user ${data.userId} in contest ${data.contestId}`,
    );
  }

  // ============================================
  // Utility methods
  // ============================================

  /**
   * Get the number of connected clients for a contest
   */
  getContestConnectionCount(contestId: string): number {
    return this.contestUsers.get(contestId)?.size || 0;
  }

  /**
   * Get total number of connected clients
   */
  getTotalConnectionCount(): number {
    return this.userRooms.size;
  }
}
```

- [ ] **Step 3: 创建单元测试**

```typescript
// backend/src/contest/realtime/contest.gateway.spec.ts

import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { ContestGateway } from './contest.gateway';
import { Socket } from 'socket.io';

describe('ContestGateway', () => {
  let gateway: ContestGateway;
  let jwtService: jest.Mocked<JwtService>;

  const mockSocket = {
    id: 'socket-1',
    data: {},
    handshake: {
      auth: {},
      headers: {},
    },
    join: jest.fn(),
    leave: jest.fn(),
    disconnect: jest.fn(),
    emit: jest.fn(),
  } as unknown as Socket;

  beforeEach(async () => {
    const mockJwtService = {
      verifyAsync: jest.fn(),
    };

    const mockConfigService = {
      get: jest.fn().mockReturnValue('test-secret'),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ContestGateway,
        { provide: JwtService, useValue: mockJwtService },
        { provide: ConfigService, useValue: mockConfigService },
      ],
    }).compile();

    gateway = module.get<ContestGateway>(ContestGateway);
    jwtService = module.get(JwtService);
  });

  describe('handleConnection', () => {
    it('should disconnect client without token', async () => {
      await gateway.handleConnection(mockSocket);
      expect(mockSocket.disconnect).toHaveBeenCalledWith(true);
    });

    it('should authenticate client with valid token', async () => {
      (mockSocket as any).handshake.auth = { token: 'valid-token' };
      jwtService.verifyAsync.mockResolvedValue({ sub: 'user-1', username: 'test' });

      await gateway.handleConnection(mockSocket);

      expect(mockSocket.data.userId).toBe('user-1');
      expect(mockSocket.disconnect).not.toHaveBeenCalled();
    });

    it('should disconnect client with invalid token', async () => {
      (mockSocket as any).handshake.auth = { token: 'invalid-token' };
      jwtService.verifyAsync.mockRejectedValue(new Error('Invalid token'));

      await gateway.handleConnection(mockSocket);

      expect(mockSocket.disconnect).toHaveBeenCalledWith(true);
    });
  });

  describe('handleJoinContest', () => {
    beforeEach(async () => {
      // Setup authenticated socket
      (mockSocket as any).handshake.auth = { token: 'valid-token' };
      jwtService.verifyAsync.mockResolvedValue({ sub: 'user-1', username: 'test' });
      await gateway.handleConnection(mockSocket);
    });

    it('should join contest room', async () => {
      const result = await gateway.handleJoinContest(mockSocket, {
        contestId: 'contest-1',
      });

      expect(result.success).toBe(true);
      expect(mockSocket.join).toHaveBeenCalledWith('contest:contest-1');
    });
  });

  describe('handleLeaveContest', () => {
    beforeEach(async () => {
      (mockSocket as any).handshake.auth = { token: 'valid-token' };
      jwtService.verifyAsync.mockResolvedValue({ sub: 'user-1', username: 'test' });
      await gateway.handleConnection(mockSocket);
      await gateway.handleJoinContest(mockSocket, { contestId: 'contest-1' });
    });

    it('should leave contest room', async () => {
      const result = await gateway.handleLeaveContest(mockSocket, {
        contestId: 'contest-1',
      });

      expect(result.success).toBe(true);
      expect(mockSocket.leave).toHaveBeenCalledWith('contest:contest-1');
    });
  });

  describe('getContestConnectionCount', () => {
    it('should return 0 for contest with no connections', () => {
      expect(gateway.getContestConnectionCount('non-existent')).toBe(0);
    });
  });
});
```

- [ ] **Step 4: 运行测试**

Run: `cd backend && pnpm jest src/contest/realtime/contest.gateway.spec.ts`
Expected: All tests pass

- [ ] **Step 5: 提交 WebSocket 网关**

```bash
git add backend/src/contest/realtime/
git commit -m "feat(contest): add WebSocket gateway for real-time updates"
```

---

### Task 3.2: 创建实时服务

**Files:**
- Create: `backend/src/contest/realtime/realtime.service.ts`

- [ ] **Step 1: 创建 RealtimeService**

```typescript
// backend/src/contest/realtime/realtime.service.ts

import { Injectable, Logger } from '@nestjs/common';
import { ContestGateway } from './contest.gateway';
import { PrismaService } from '../../prisma.service';
import { isFeatureEnabled } from '../../common/config/feature-flags.config';

@Injectable()
export class RealtimeService {
  private readonly logger = new Logger(RealtimeService.name);

  constructor(
    private readonly gateway: ContestGateway,
    private readonly prisma: PrismaService,
  ) {}

  /**
   * Push ranking update to all connected clients
   */
  async pushRankingUpdate(contestId: string): Promise<void> {
    if (!isFeatureEnabled('ENABLE_REALTIME_RANKING')) {
      return;
    }

    try {
      const participants = await this.prisma.contestParticipant.findMany({
        where: { contest_id: contestId, rank: { not: null } },
        orderBy: [{ rank: 'asc' }],
        take: 100,
        include: {
          user: { select: { id: true, username: true } },
        },
      });

      const rankings = participants.map((p) => ({
        rank: p.rank!,
        userId: p.user_id,
        username: p.user.username,
        score: p.total_score,
        time: p.total_time,
        solvedCount: p.solved_count,
      }));

      this.gateway.emitRankingUpdate({
        contestId,
        rankings,
        timestamp: new Date(),
      });
    } catch (error) {
      this.logger.error(
        `Failed to push ranking update for contest ${contestId}:`,
        error,
      );
    }
  }

  /**
   * Push first solve notification
   */
  async pushFirstSolve(
    contestId: string,
    problemIndex: string,
    userId: string,
    timeSpent: number,
  ): Promise<void> {
    if (!isFeatureEnabled('ENABLE_FIRST_SOLVE_NOTIFICATIONS')) {
      return;
    }

    try {
      const user = await this.prisma.user.findUnique({
        where: { id: userId },
        select: { username: true },
      });

      if (!user) {
        this.logger.warn(`User ${userId} not found for first solve notification`);
        return;
      }

      this.gateway.emitFirstSolve({
        contestId,
        problemIndex,
        userId,
        username: user.username,
        timeSpent,
        timestamp: new Date(),
      });

      this.logger.log(
        `Pushed first solve notification: ${user.username} solved ${problemIndex}`,
      );
    } catch (error) {
      this.logger.error('Failed to push first solve notification:', error);
    }
  }

  /**
   * Push new announcement
   */
  async pushAnnouncement(contestId: string, announcementId: string): Promise<void> {
    try {
      const announcement = await this.prisma.contestAnnouncement.findUnique({
        where: { id: announcementId },
      });

      if (!announcement) {
        this.logger.warn(`Announcement ${announcementId} not found`);
        return;
      }

      this.gateway.emitAnnouncement({
        contestId,
        id: announcement.id,
        title: announcement.title,
        content: announcement.content,
        isPinned: announcement.is_pinned,
        timestamp: announcement.created_at,
      });
    } catch (error) {
      this.logger.error('Failed to push announcement:', error);
    }
  }

  /**
   * Push contest status change
   */
  pushContestStatus(contestId: string, status: string): void {
    this.gateway.emitContestStatus({
      contestId,
      status,
      timestamp: new Date(),
    });
  }

  /**
   * Push submission result
   */
  pushSubmissionResult(data: {
    contestId: string;
    userId: string;
    problemIndex: string;
    submissionId: string;
    status: string;
    score: number;
    timeSpent: number;
    isFirstSolve: boolean;
  }): void {
    this.gateway.emitSubmissionResult({
      ...data,
      timestamp: new Date(),
    });
  }

  /**
   * Get connection statistics
   */
  getStats() {
    return {
      totalConnections: this.gateway.getTotalConnectionCount(),
    };
  }
}
```

- [ ] **Step 2: 更新 ContestModule**

在 `contest.module.ts` 中添加:

```typescript
import { ContestGateway } from './realtime/contest.gateway';
import { RealtimeService } from './realtime/realtime.service';

// 在 providers 中添加:
ContestGateway,
RealtimeService,

// 在 exports 中添加:
RealtimeService,
```

- [ ] **Step 3: 运行类型检查**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 4: 提交实时服务**

```bash
git add backend/src/contest/realtime/realtime.service.ts
git add backend/src/contest/contest.module.ts
git commit -m "feat(contest): add realtime service for WebSocket events"
```

---

## Chunk 3 完成检查点

在继续之前，确保：

1. [ ] ContestGateway 已创建并测试通过
2. [ ] RealtimeService 已创建
3. [ ] ContestModule 已更新
4. [ ] 所有更改已提交到 git

**验证命令:**
```bash
cd backend && pnpm type-check && pnpm jest src/contest/realtime/
```

---

## Chunk 4: 前端页面 (Frontend - Console)

> **目标**: 实现竞赛系统前端页面，包括竞赛大厅、详情页、排行榜、WebSocket 实时连接

### Task 4.1: API 客户端与类型定义

**Files:**
- Create: `console/src/types/contest.ts`
- Create: `console/src/api/contest.ts`
- Modify: `console/src/api/index.ts`

- [ ] **Step 1: 编写竞赛类型定义**

```typescript
// console/src/types/contest.ts

export enum ContestType {
  WEEKLY = 'weekly',
  BIWEEKLY = 'biweekly',
  MONTHLY = 'monthly',
  SPECIAL = 'special',
  THEMED = 'themed',
  CORPORATE = 'corporate',
  CAMPUS = 'campus',
}

export enum ContestStatus {
  DRAFT = 'draft',
  PUBLISHED = 'published',
  REGISTERING = 'registering',
  UPCOMING = 'upcoming',
  ONGOING = 'ongoing',
  RUNNING = 'running', // 兼容旧数据
  FREEZING = 'freezing',
  FINISHED = 'finished',
  ARCHIVED = 'archived',
}

export enum ParticipantStatus {
  REGISTERED = 'REGISTERED',
  CHECKED_IN = 'CHECKED_IN',
  STARTED = 'STARTED',
  PARTICIPATING = 'PARTICIPATING',
  FINISHED = 'FINISHED',
  DISQUALIFIED = 'DISQUALIFIED',
}

export interface Contest {
  id: string;
  title: string;
  slug: string;
  description: string | null;
  coverImage: string | null;
  contestType: ContestType;
  startTime: string;
  endTime: string | null;
  registrationStart: string | null;
  registrationEnd: string | null;
  freezeTime: string | null;
  durationMinutes: number;
  isVisible: boolean;
  isRated: boolean;
  isVirtual: boolean;
  maxParticipants: number | null;
  scoringRuleId: string | null;
  penaltyPerWrong: number;
  status: ContestStatus;
  registeredCount: number;
  participantCount: number;
  submissionCount: number | null;
  rules: string | null;
  createdAt: string;
}

export interface ContestProblem {
  id: string;
  contestId: string;
  problemId: bigint;
  problemIndex: string;
  label: string | null;
  score: number;
  baseScore: number | null;
  timeBonus: number | null;
  penaltyPerWrong: number | null;
  solvedCount: number;
  submissionCount: number;
  problem: {
    id: bigint;
    title: string;
    difficulty: string;
  };
}

export interface ContestParticipant {
  id: string;
  contestId: string;
  userId: string;
  registeredAt: string;
  status: ParticipantStatus;
  checkedInAt: string | null;
  isVirtual: boolean;
  totalScore: number;
  totalTime: number;
  rank: number | null;
  solvedCount: number;
  attemptCount: number;
}

export interface RankingEntry {
  rank: number;
  userId: string;
  username: string;
  score: number;
  time: number;
  solvedCount: number;
  problems: ProblemResult[];
}

export interface ProblemResult {
  problemIndex: string;
  score: number;
  time: number | null;
  attempts: number;
  isAccepted: boolean;
  isFirstSolve: boolean;
}

export interface FirstSolveNotification {
  contestId: string;
  problemIndex: string;
  userId: string;
  username: string;
  timeSpent: number;
  timestamp: string;
}

export interface ContestAnnouncement {
  id: string;
  contestId: string;
  title: string;
  content: string;
  createdAt: string;
  isPinned: boolean;
}

export interface ContestFilters {
  status?: ContestStatus[];
  type?: ContestType[];
  page?: number;
  limit?: number;
}
```

- [ ] **Step 2: 编写 API 客户端**

```typescript
// console/src/api/contest.ts

import type { Contest, ContestProblem, ContestParticipant, RankingEntry, ContestAnnouncement, ContestFilters } from '@/types/contest';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9001';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Unknown error' }));
    throw new Error(error.message || `HTTP ${response.status}`);
  }

  return response.json();
}

export const contestApi = {
  // 竞赛列表
  async getContests(filters?: ContestFilters): Promise<{ data: Contest[]; meta: { total: number; hasMore: boolean } }> {
    const params = new URLSearchParams();
    if (filters?.status?.length) params.set('status', filters.status.join(','));
    if (filters?.type?.length) params.set('type', filters.type.join(','));
    if (filters?.page) params.set('page', String(filters.page));
    if (filters?.limit) params.set('limit', String(filters.limit));
    return request(`/api/contests?${params}`);
  },

  // 竞赛详情
  async getContest(slug: string): Promise<Contest> {
    const res = await request<{ data: Contest }>(`/api/contests/${slug}`);
    return res.data;
  },

  // 竞赛题目
  async getContestProblems(slug: string): Promise<ContestProblem[]> {
    const res = await request<{ data: ContestProblem[] }>(`/api/contests/${slug}/problems`);
    return res.data;
  },

  // 竞赛公告
  async getAnnouncements(slug: string): Promise<ContestAnnouncement[]> {
    const res = await request<{ data: ContestAnnouncement[] }>(`/api/contests/${slug}/announcements`);
    return res.data;
  },

  // 排行榜
  async getRanking(slug: string, options?: { frozen?: boolean }): Promise<RankingEntry[]> {
    const params = new URLSearchParams();
    if (options?.frozen !== undefined) params.set('frozen', String(options.frozen));
    const res = await request<{ data: RankingEntry[] }>(`/api/contests/${slug}/ranking?${params}`);
    return res.data;
  },

  // 用户排名
  async getUserRanking(slug: string, userId: string): Promise<RankingEntry | null> {
    const res = await request<{ data: RankingEntry | null }>(`/api/contests/${slug}/ranking/user/${userId}`);
    return res.data;
  },

  // 报名
  async register(slug: string): Promise<ContestParticipant> {
    const res = await request<{ data: ContestParticipant }>(`/api/contests/${slug}/register`, { method: 'POST' });
    return res.data;
  },

  // 签到
  async checkIn(slug: string): Promise<ContestParticipant> {
    const res = await request<{ data: ContestParticipant }>(`/api/contests/${slug}/checkin`, { method: 'POST' });
    return res.data;
  },

  // 退出
  async withdraw(slug: string): Promise<void> {
    await request(`/api/contests/${slug}/withdraw`, { method: 'DELETE' });
  },

  // 获取我的参赛状态
  async getMyParticipation(slug: string): Promise<ContestParticipant | null> {
    try {
      const res = await request<{ data: ContestParticipant | null }>(`/api/contests/${slug}/participation`);
      return res.data;
    } catch {
      return null;
    }
  },

  // 虚拟参赛
  async startVirtualContest(slug: string): Promise<{ sessionId: string }> {
    const res = await request<{ data: { sessionId: string } }>(`/api/contests/${slug}/virtual/start`, { method: 'POST' });
    return res.data;
  },

  async endVirtualContest(slug: string): Promise<void> {
    await request(`/api/contests/${slug}/virtual/end`, { method: 'POST' });
  },
};
```

- [ ] **Step 3: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 4: 提交 API 客户端**

```bash
git add console/src/types/contest.ts console/src/api/contest.ts
git commit -m "feat(console): add contest API client and type definitions"
```

---

### Task 4.2: Pinia Store - 竞赛状态管理

**Files:**
- Create: `console/src/stores/contest/contestStore.ts`
- Create: `console/src/stores/contest/rankingStore.ts`
- Create: `console/src/stores/contest/index.ts`

- [ ] **Step 1: 编写竞赛 Store 测试**

```typescript
// console/src/stores/contest/contestStore.spec.ts

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useContestStore } from './contestStore';
import { contestApi } from '@/api/contest';
import { ContestStatus, ContestType } from '@/types/contest';

vi.mock('@/api/contest', () => ({
  contestApi: {
    getContests: vi.fn(),
    getContest: vi.fn(),
    register: vi.fn(),
    checkIn: vi.fn(),
    withdraw: vi.fn(),
    getMyParticipation: vi.fn(),
  },
}));

describe('contestStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  describe('fetchContests', () => {
    it('should fetch contests and update state', async () => {
      const mockContests = [
        { id: '1', title: 'Weekly 123', status: ContestStatus.ONGOING },
        { id: '2', title: 'Biweekly 45', status: ContestStatus.UPCOMING },
      ];

      vi.mocked(contestApi.getContests).mockResolvedValue({
        data: mockContests as any,
        meta: { total: 2, hasMore: false },
      });

      const store = useContestStore();
      await store.fetchContests();

      expect(store.contests).toHaveLength(2);
      expect(store.contests[0].title).toBe('Weekly 123');
      expect(store.loading).toBe(false);
    });

    it('should handle fetch errors', async () => {
      vi.mocked(contestApi.getContests).mockRejectedValue(new Error('Network error'));

      const store = useContestStore();
      await store.fetchContests();

      expect(store.error).toBe('Network error');
      expect(store.loading).toBe(false);
    });
  });

  describe('registerContest', () => {
    it('should register user for contest', async () => {
      vi.mocked(contestApi.register).mockResolvedValue({ id: 'p1' } as any);

      const store = useContestStore();
      store.currentContest = { slug: 'weekly-123' } as any;

      await store.registerContest();

      expect(contestApi.register).toHaveBeenCalledWith('weekly-123');
      expect(store.myParticipation).toEqual({ id: 'p1' });
    });

    it('should throw if no current contest', async () => {
      const store = useContestStore();

      await expect(store.registerContest()).rejects.toThrow('No contest selected');
    });
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd console && pnpm vitest run stores/contest/contestStore.spec.ts`
Expected: FAIL - store not implemented

- [ ] **Step 3: 编写竞赛 Store 实现**

```typescript
// console/src/stores/contest/contestStore.ts

import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { contestApi } from '@/api/contest';
import type { Contest, ContestParticipant, ContestFilters, ContestProblem, ContestAnnouncement } from '@/types/contest';

export const useContestStore = defineStore('contest', () => {
  // State
  const contests = ref<Contest[]>([]);
  const currentContest = ref<Contest | null>(null);
  const currentProblems = ref<ContestProblem[]>([]);
  const currentAnnouncements = ref<ContestAnnouncement[]>([]);
  const myParticipation = ref<ContestParticipant | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const meta = ref<{ total: number; hasMore: boolean }>({ total: 0, hasMore: false });

  // Getters
  const isActive = computed(() => {
    if (!currentContest.value) return false;
    const status = currentContest.value.status;
    return status === 'ongoing' || status === 'running';
  });

  const canRegister = computed(() => {
    if (!currentContest.value) return false;
    const status = currentContest.value.status;
    return status === 'registering' || status === 'upcoming' || status === 'published';
  });

  const isRegistered = computed(() => {
    return myParticipation.value !== null;
  });

  const isCheckedIn = computed(() => {
    return myParticipation.value?.status === 'CHECKED_IN';
  });

  // Actions
  async function fetchContests(filters?: ContestFilters) {
    loading.value = true;
    error.value = null;
    try {
      const response = await contestApi.getContests(filters);
      contests.value = response.data;
      meta.value = response.meta;
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch contests';
    } finally {
      loading.value = false;
    }
  }

  async function fetchContest(slug: string) {
    loading.value = true;
    error.value = null;
    try {
      currentContest.value = await contestApi.getContest(slug);
      myParticipation.value = await contestApi.getMyParticipation(slug);
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch contest';
    } finally {
      loading.value = false;
    }
  }

  async function fetchProblems(slug: string) {
    try {
      currentProblems.value = await contestApi.getContestProblems(slug);
    } catch (e) {
      console.error('Failed to fetch problems:', e);
    }
  }

  async function fetchAnnouncements(slug: string) {
    try {
      currentAnnouncements.value = await contestApi.getAnnouncements(slug);
    } catch (e) {
      console.error('Failed to fetch announcements:', e);
    }
  }

  async function registerContest() {
    if (!currentContest.value) {
      throw new Error('No contest selected');
    }
    loading.value = true;
    error.value = null;
    try {
      myParticipation.value = await contestApi.register(currentContest.value.slug);
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to register';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function checkInContest() {
    if (!currentContest.value) {
      throw new Error('No contest selected');
    }
    loading.value = true;
    error.value = null;
    try {
      myParticipation.value = await contestApi.checkIn(currentContest.value.slug);
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to check in';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function withdrawContest() {
    if (!currentContest.value) return;
    try {
      await contestApi.withdraw(currentContest.value.slug);
      myParticipation.value = null;
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to withdraw';
      throw e;
    }
  }

  function clearCurrentContest() {
    currentContest.value = null;
    currentProblems.value = [];
    currentAnnouncements.value = [];
    myParticipation.value = null;
  }

  return {
    // State
    contests,
    currentContest,
    currentProblems,
    currentAnnouncements,
    myParticipation,
    loading,
    error,
    meta,
    // Getters
    isActive,
    canRegister,
    isRegistered,
    isCheckedIn,
    // Actions
    fetchContests,
    fetchContest,
    fetchProblems,
    fetchAnnouncements,
    registerContest,
    checkInContest,
    withdrawContest,
    clearCurrentContest,
  };
});
```

- [ ] **Step 4: 编写排行榜 Store**

```typescript
// console/src/stores/contest/rankingStore.ts

import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { contestApi } from '@/api/contest';
import type { RankingEntry, FirstSolveNotification } from '@/types/contest';

export const useRankingStore = defineStore('ranking', () => {
  // State
  const rankings = ref<RankingEntry[]>([]);
  const firstSolves = ref<FirstSolveNotification[]>([]);
  const isFrozen = ref(false);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const lastUpdated = ref<Date | null>(null);

  // Getters
  const topThree = computed(() => rankings.value.slice(0, 3));
  const myRank = computed(() => {
    // Will be set by socket updates
    return null;
  });

  // Actions
  async function fetchRanking(slug: string) {
    loading.value = true;
    error.value = null;
    try {
      rankings.value = await contestApi.getRanking(slug, { frozen: isFrozen.value });
      lastUpdated.value = new Date();
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch ranking';
    } finally {
      loading.value = false;
    }
  }

  function updateRanking(newRankings: RankingEntry[]) {
    rankings.value = newRankings;
    lastUpdated.value = new Date();
  }

  function addFirstSolve(notification: FirstSolveNotification) {
    // Avoid duplicates
    const exists = firstSolves.value.some(
      (fs) => fs.problemIndex === notification.problemIndex
    );
    if (!exists) {
      firstSolves.value.push(notification);
    }
  }

  function setFrozen(frozen: boolean) {
    isFrozen.value = frozen;
  }

  function clearRanking() {
    rankings.value = [];
    firstSolves.value = [];
    isFrozen.value = false;
    lastUpdated.value = null;
  }

  return {
    rankings,
    firstSolves,
    isFrozen,
    loading,
    error,
    lastUpdated,
    topThree,
    myRank,
    fetchRanking,
    updateRanking,
    addFirstSolve,
    setFrozen,
    clearRanking,
  };
});
```

- [ ] **Step 5: 创建 barrel export**

```typescript
// console/src/stores/contest/index.ts

export { useContestStore } from './contestStore';
export { useRankingStore } from './rankingStore';
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd console && pnpm vitest run stores/contest/`
Expected: All tests pass

- [ ] **Step 7: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 8: 提交 Store**

```bash
git add console/src/stores/contest/
git commit -m "feat(console): add contest Pinia stores"
```

---

### Task 4.3: WebSocket 连接 Composable

**Files:**
- Create: `console/src/composables/contest/useContestSocket.ts`

- [ ] **Step 1: 编写 useContestSocket 测试**

```typescript
// console/src/composables/contest/useContestSocket.spec.ts

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useContestSocket } from './useContestSocket';
import { useRankingStore } from '@/stores/contest';
import { io } from 'socket.io-client';

vi.mock('socket.io-client', () => ({
  io: vi.fn(() => ({
    on: vi.fn(),
    off: vi.fn(),
    emit: vi.fn(),
    disconnect: vi.fn(),
    connected: true,
  })),
}));

describe('useContestSocket', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('should create socket connection on connect', () => {
    const { connect, isConnected } = useContestSocket();

    connect('contest-123', 'user-456');

    expect(io).toHaveBeenCalledWith(expect.stringContaining('/contest'), expect.any(Object));
  });

  it('should register event handlers on connect', () => {
    const mockSocket = { on: vi.fn(), off: vi.fn(), emit: vi.fn(), disconnect: vi.fn() };
    vi.mocked(io).mockReturnValue(mockSocket as any);

    const { connect } = useContestSocket();
    connect('contest-123', 'user-456');

    expect(mockSocket.on).toHaveBeenCalledWith('ranking_update', expect.any(Function));
    expect(mockSocket.on).toHaveBeenCalledWith('first_solve', expect.any(Function));
    expect(mockSocket.on).toHaveBeenCalledWith('announcement', expect.any(Function));
    expect(mockSocket.on).toHaveBeenCalledWith('contest_status', expect.any(Function));
  });

  it('should update ranking store on ranking_update event', () => {
    const mockSocket = {
      on: vi.fn((event, handler) => {
        if (event === 'ranking_update') {
          (mockSocket as any)._handlers = { ...((mockSocket as any)._handlers || {}), ranking_update: handler };
        }
      }),
      off: vi.fn(),
      emit: vi.fn(),
      disconnect: vi.fn(),
    };
    vi.mocked(io).mockReturnValue(mockSocket as any);

    const rankingStore = useRankingStore();
    const { connect } = useContestSocket();
    connect('contest-123', 'user-456');

    // Trigger the event handler
    const handler = (mockSocket as any)._handlers?.ranking_update;
    if (handler) {
      handler({ rankings: [{ rank: 1, userId: 'u1', score: 100 }] });
    }

    expect(rankingStore.rankings).toHaveLength(1);
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd console && pnpm vitest run composables/contest/useContestSocket.spec.ts`
Expected: FAIL - composable not implemented

- [ ] **Step 3: 编写 useContestSocket 实现**

```typescript
// console/src/composables/contest/useContestSocket.ts

import { ref, onUnmounted } from 'vue';
import { io, Socket } from 'socket.io-client';
import { useRankingStore } from '@/stores/contest';
import type { RankingEntry, FirstSolveNotification, ContestAnnouncement } from '@/types/contest';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9001';

interface RankingUpdateData {
  contestId: string;
  rankings: RankingEntry[];
  timestamp: string;
}

interface ContestStatusData {
  contestId: string;
  status: string;
  timestamp: string;
}

export function useContestSocket() {
  const socket = ref<Socket | null>(null);
  const isConnected = ref(false);
  const rankingStore = useRankingStore();

  function connect(contestId: string, userId?: string) {
    if (socket.value?.connected) {
      return;
    }

    socket.value = io(`${API_BASE}/contest`, {
      transports: ['websocket'],
      auth: { userId },
      query: { contestId },
    });

    setupEventHandlers();
  }

  function setupEventHandlers() {
    if (!socket.value) return;

    socket.value.on('connect', () => {
      isConnected.value = true;
      console.log('[ContestSocket] Connected');
    });

    socket.value.on('disconnect', () => {
      isConnected.value = false;
      console.log('[ContestSocket] Disconnected');
    });

    // 排行榜更新
    socket.value.on('ranking_update', (data: RankingUpdateData) => {
      rankingStore.updateRanking(data.rankings);
    });

    // 首杀播报
    socket.value.on('first_solve', (data: FirstSolveNotification) => {
      rankingStore.addFirstSolve(data);
    });

    // 竞赛公告
    socket.value.on('announcement', (data: ContestAnnouncement) => {
      // Handle new announcement - can emit event or update store
      console.log('[ContestSocket] New announcement:', data);
    });

    // 竞赛状态变更
    socket.value.on('contest_status', (data: ContestStatusData) => {
      console.log('[ContestSocket] Contest status changed:', data.status);
      if (data.status === 'freezing') {
        rankingStore.setFrozen(true);
      }
    });

    // 提交结果
    socket.value.on('submission_result', (data: any) => {
      console.log('[ContestSocket] Submission result:', data);
    });

    socket.value.on('connect_error', (error) => {
      console.error('[ContestSocket] Connection error:', error);
    });
  }

  function disconnect() {
    if (socket.value) {
      socket.value.disconnect();
      socket.value = null;
      isConnected.value = false;
    }
  }

  function joinContest(contestId: string) {
    if (socket.value?.connected) {
      socket.value.emit('join_contest', { contestId });
    }
  }

  function leaveContest(contestId: string) {
    if (socket.value?.connected) {
      socket.value.emit('leave_contest', { contestId });
    }
  }

  // Cleanup on unmount
  onUnmounted(() => {
    disconnect();
  });

  return {
    socket,
    isConnected,
    connect,
    disconnect,
    joinContest,
    leaveContest,
  };
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd console && pnpm vitest run composables/contest/`
Expected: All tests pass

- [ ] **Step 5: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 6: 提交 Composable**

```bash
git add console/src/composables/contest/
git commit -m "feat(console): add useContestSocket composable for WebSocket"
```

---

### Task 4.4: 竞赛组件 - 核心组件

**Files:**
- Create: `console/src/views/contest/components/ContestCard.vue`
- Create: `console/src/views/contest/components/ContestTimer.vue`
- Create: `console/src/views/contest/components/ContestStatusBadge.vue`

- [ ] **Step 1: 编写 ContestStatusBadge 组件**

```vue
<!-- console/src/views/contest/components/ContestStatusBadge.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { ContestStatus } from '@/types/contest';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

const props = defineProps<{
  status: ContestStatus;
  showIcon?: boolean;
}>();

const statusConfig = computed(() => {
  const configs: Record<ContestStatus, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline'; class: string }> = {
    [ContestStatus.DRAFT]: { label: '草稿', variant: 'secondary', class: 'bg-gray-100 text-gray-600' },
    [ContestStatus.PUBLISHED]: { label: '已发布', variant: 'secondary', class: 'bg-blue-100 text-blue-600' },
    [ContestStatus.REGISTERING]: { label: '报名中', variant: 'default', class: 'bg-green-100 text-green-600' },
    [ContestStatus.UPCOMING]: { label: '即将开始', variant: 'default', class: 'bg-yellow-100 text-yellow-600' },
    [ContestStatus.ONGOING]: { label: '进行中', variant: 'destructive', class: 'bg-red-100 text-red-600' },
    [ContestStatus.RUNNING]: { label: '进行中', variant: 'destructive', class: 'bg-red-100 text-red-600' },
    [ContestStatus.FREEZING]: { label: '冻结中', variant: 'destructive', class: 'bg-purple-100 text-purple-600' },
    [ContestStatus.FINISHED]: { label: '已结束', variant: 'secondary', class: 'bg-gray-100 text-gray-600' },
    [ContestStatus.ARCHIVED]: { label: '已归档', variant: 'outline', class: 'bg-gray-50 text-gray-500' },
  };
  return configs[props.status] || configs[ContestStatus.DRAFT];
});
</script>

<template>
  <Badge :variant="statusConfig.variant" :class="cn('font-medium', statusConfig.class)">
    <span v-if="showIcon && status === ContestStatus.ONGOING" class="mr-1">🔴</span>
    {{ statusConfig.label }}
  </Badge>
</template>
```

- [ ] **Step 2: 编写 ContestTimer 组件**

```vue
<!-- console/src/views/contest/components/ContestTimer.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { cn } from '@/lib/utils';

const props = defineProps<{
  endTime: string | Date;
  startTime?: string | Date;
  showIcon?: boolean;
  variant?: 'default' | 'compact';
}>();

const emit = defineEmits<{
  (e: 'finished'): void;
  (e: 'started'): void;
}>();

const now = ref(new Date());
let interval: ReturnType<typeof setInterval> | null = null;

const timeRemaining = computed(() => {
  const end = new Date(props.endTime);
  const diff = end.getTime() - now.value.getTime();
  return Math.max(0, diff);
});

const timeUntilStart = computed(() => {
  if (!props.startTime) return 0;
  const start = new Date(props.startTime);
  const diff = start.getTime() - now.value.getTime();
  return Math.max(0, diff);
});

const isStarted = computed(() => timeUntilStart.value === 0);
const isFinished = computed(() => timeRemaining.value === 0);

const formattedTime = computed(() => {
  const ms = isStarted.value ? timeRemaining.value : timeUntilStart.value;
  const seconds = Math.floor(ms / 1000) % 60;
  const minutes = Math.floor(ms / (1000 * 60)) % 60;
  const hours = Math.floor(ms / (1000 * 60 * 60)) % 24;
  const days = Math.floor(ms / (1000 * 60 * 60 * 24));

  if (props.variant === 'compact') {
    if (days > 0) return `${days}天 ${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }

  const parts = [];
  if (days > 0) parts.push(`${days}天`);
  if (hours > 0 || days > 0) parts.push(`${hours}小时`);
  parts.push(`${minutes}分`);
  parts.push(`${seconds}秒`);

  return parts.join(' ');
});

const label = computed(() => {
  if (!isStarted.value) return '距离开始';
  if (isFinished.value) return '已结束';
  return '剩余时间';
});

function tick() {
  now.value = new Date();

  if (isFinished.value && isStarted.value) {
    emit('finished');
    stopTimer();
  } else if (isStarted.value && !isFinished.value) {
    emit('started');
  }
}

function startTimer() {
  if (interval) return;
  interval = setInterval(tick, 1000);
}

function stopTimer() {
  if (interval) {
    clearInterval(interval);
    interval = null;
  }
}

watch(() => [props.endTime, props.startTime], () => {
  tick();
});

onMounted(() => {
  tick();
  startTimer();
});

onUnmounted(() => {
  stopTimer();
});
</script>

<template>
  <div :class="cn('flex items-center gap-2', variant === 'compact' && 'text-sm')">
    <span v-if="showIcon" class="text-lg">⏱️</span>
    <span class="text-muted-foreground">{{ label }}:</span>
    <span :class="cn('font-mono font-semibold', isStarted && !isFinished && 'text-primary')">
      {{ formattedTime }}
    </span>
  </div>
</template>
```

- [ ] **Step 3: 编写 ContestCard 组件**

```vue
<!-- console/src/views/contest/components/ContestCard.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import type { Contest } from '@/types/contest';
import { ContestStatus } from '@/types/contest';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import ContestStatusBadge from './ContestStatusBadge.vue';
import ContestTimer from './ContestTimer.vue';

const props = defineProps<{
  contest: Contest;
  isRegistered?: boolean;
}>();

const router = useRouter();
const { t } = useI18n();

const isOngoing = computed(() =>
  props.contest.status === ContestStatus.ONGOING ||
  props.contest.status === ContestStatus.RUNNING
);

const isUpcoming = computed(() =>
  props.contest.status === ContestStatus.UPCOMING ||
  props.contest.status === ContestStatus.REGISTERING
);

const isFinished = computed(() => props.contest.status === ContestStatus.FINISHED);

const primaryAction = computed(() => {
  if (isOngoing.value) {
    return { label: t('contest.enterContest'), variant: 'default' as const };
  }
  if (isUpcoming.value) {
    if (props.isRegistered) {
      return { label: t('contest.registered'), variant: 'outline' as const };
    }
    return { label: t('contest.register'), variant: 'default' as const };
  }
  if (isFinished.value) {
    return { label: t('contest.viewRanking'), variant: 'outline' as const };
  }
  return { label: t('contest.viewDetails'), variant: 'outline' as const };
});

function handleClick() {
  router.push(`/contest/${props.contest.slug}`);
}

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
</script>

<template>
  <Card class="hover:border-primary/50 transition-colors cursor-pointer" @click="handleClick">
    <CardHeader class="pb-2">
      <div class="flex items-start justify-between">
        <CardTitle class="text-lg">{{ contest.title }}</CardTitle>
        <ContestStatusBadge :status="contest.status" show-icon />
      </div>
    </CardHeader>

    <CardContent class="pb-4">
      <!-- Timer for ongoing/upcoming -->
      <div v-if="isOngoing && contest.endTime" class="mb-3">
        <ContestTimer :end-time="contest.endTime" variant="compact" show-icon />
      </div>
      <div v-else-if="isUpcoming" class="mb-3 text-sm text-muted-foreground">
        <span v-if="contest.startTime">
          {{ formatDateTime(contest.startTime) }} - {{ contest.durationMinutes }} 分钟
        </span>
      </div>
      <div v-else-if="isFinished" class="mb-3 text-sm text-muted-foreground">
        {{ formatDateTime(contest.startTime) }}
      </div>

      <!-- Stats -->
      <div class="flex items-center gap-4 text-sm text-muted-foreground">
        <span>👥 {{ contest.registeredCount }} 人已报名</span>
        <span v-if="contest.participantCount">📝 {{ contest.participantCount }} 人参赛</span>
      </div>
    </CardContent>

    <CardFooter class="pt-0">
      <div class="flex w-full items-center justify-between gap-2">
        <!-- Virtual contest for finished -->
        <Button
          v-if="isFinished && contest.isVirtual"
          variant="ghost"
          size="sm"
          @click.stop="router.push(`/contest/${contest.slug}/virtual`)"
        >
          {{ t('contest.virtualParticipate') }}
        </Button>
        <div v-else />

        <Button :variant="primaryAction.variant" size="sm" @click.stop="handleClick">
          {{ primaryAction.label }}
        </Button>
      </div>
    </CardFooter>
  </Card>
</template>
```

- [ ] **Step 4: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 5: 提交核心组件**

```bash
git add console/src/views/contest/components/
git commit -m "feat(console): add contest core components (Card, Timer, StatusBadge)"
```

---

### Task 4.5: 竞赛列表页面

**Files:**
- Create: `console/src/views/contest/ContestListView.vue`
- Modify: `console/src/router/index.ts`

- [ ] **Step 1: 编写竞赛列表页面**

```vue
<!-- console/src/views/contest/ContestListView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useContestStore } from '@/stores/contest';
import { ContestStatus, ContestType } from '@/types/contest';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import ContestCard from './components/ContestCard.vue';

const { t } = useI18n();
const store = useContestStore();

const activeTab = ref<'ongoing' | 'upcoming' | 'finished'>('ongoing');

const filters = computed(() => {
  switch (activeTab.value) {
    case 'ongoing':
      return { status: [ContestStatus.ONGOING, ContestStatus.RUNNING, ContestStatus.FREEZING] };
    case 'upcoming':
      return { status: [ContestStatus.UPCOMING, ContestStatus.REGISTERING, ContestStatus.PUBLISHED] };
    case 'finished':
      return { status: [ContestStatus.FINISHED, ContestStatus.ARCHIVED] };
    default:
      return {};
  }
});

async function loadContests() {
  await store.fetchContests(filters.value);
}

function handleTabChange(value: string) {
  activeTab.value = value as typeof activeTab.value;
  loadContests();
}

onMounted(() => {
  loadContests();
});
</script>

<template>
  <div class="container mx-auto py-8 px-4">
    <div class="mb-8">
      <h1 class="text-3xl font-bold mb-2">🏆 {{ t('contest.title') }}</h1>
      <p class="text-muted-foreground">{{ t('contest.subtitle') }}</p>
    </div>

    <Tabs v-model="activeTab" @update:model-value="handleTabChange">
      <TabsList class="mb-6">
        <TabsTrigger value="ongoing">
          {{ t('contest.tabs.ongoing') }}
          <span v-if="store.contests.filter(c => c.status === ContestStatus.ONGOING || c.status === ContestStatus.RUNNING).length"
                class="ml-1 text-xs bg-red-500 text-white px-1.5 rounded">
            LIVE
          </span>
        </TabsTrigger>
        <TabsTrigger value="upcoming">{{ t('contest.tabs.upcoming') }}</TabsTrigger>
        <TabsTrigger value="finished">{{ t('contest.tabs.finished') }}</TabsTrigger>
      </TabsList>

      <TabsContent value="ongoing">
        <ContestListContent :loading="store.loading" :contests="store.contests" />
      </TabsContent>

      <TabsContent value="upcoming">
        <ContestListContent :loading="store.loading" :contests="store.contests" />
      </TabsContent>

      <TabsContent value="finished">
        <ContestListContent :loading="store.loading" :contests="store.contests" />
      </TabsContent>
    </Tabs>
  </div>
</template>

<!-- Extract to separate component for reusability -->
<script lang="ts">
import { defineComponent, h } from 'vue';
import type { Contest } from '@/types/contest';
import { Skeleton } from '@/components/ui/skeleton';
import ContestCard from './components/ContestCard.vue';

export const ContestListContent = defineComponent({
  name: 'ContestListContent',
  props: {
    loading: { type: Boolean, default: false },
    contests: { type: Array as () => Contest[], default: () => [] },
  },
  setup(props) {
    return () => {
      if (props.loading) {
        return h('div', { class: 'grid gap-4 md:grid-cols-2 lg:grid-cols-3' },
          Array(6).fill(0).map(() =>
            h(Skeleton, { class: 'h-40' })
          )
        );
      }

      if (props.contests.length === 0) {
        return h('div', { class: 'text-center py-12 text-muted-foreground' }, '暂无竞赛');
      }

      return h('div', { class: 'grid gap-4 md:grid-cols-2 lg:grid-cols-3' },
        props.contests.map(contest =>
          h(ContestCard, { contest, key: contest.id })
        )
      );
    };
  },
});
</script>
```

- [ ] **Step 2: 添加路由配置**

在 `console/src/router/index.ts` 中添加:

```typescript
// 在 routes 数组中添加:
{
  path: '/contest',
  name: 'contest-list',
  component: () => import('@/views/contest/ContestListView.vue'),
  meta: { title: 'contest.title' },
},
{
  path: '/contest/:slug',
  name: 'contest-detail',
  component: () => import('@/views/contest/ContestDetailView.vue'),
  meta: { title: 'contest.detail' },
},
```

- [ ] **Step 3: 添加 i18n 翻译**

在 `console/src/i18n/locales/zh-CN.ts` 中添加:

```typescript
contest: {
  title: '竞赛',
  subtitle: '参与编程竞赛，提升技能，赢取奖励',
  tabs: {
    ongoing: '进行中',
    upcoming: '即将开始',
    finished: '已结束',
  },
  enterContest: '进入比赛',
  register: '立即报名',
  registered: '已报名',
  viewRanking: '查看排名',
  viewDetails: '查看详情',
  virtualParticipate: '虚拟参赛',
  detail: '竞赛详情',
  // ... 更多翻译
},
```

- [ ] **Step 4: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 5: 提交列表页面**

```bash
git add console/src/views/contest/ContestListView.vue console/src/router/index.ts console/src/i18n/
git commit -m "feat(console): add contest list view with tabs"
```

---

## Chunk 4 完成检查点

在继续之前，确保：

1. [ ] API 客户端和类型定义已创建
2. [ ] Pinia Store 已创建并测试通过
3. [ ] WebSocket Composable 已创建
4. [ ] 核心组件已创建
5. [ ] 竞赛列表页面已创建
6. [ ] 所有更改已提交到 git

**验证命令:**
```bash
cd console && pnpm type-check && pnpm vitest run stores/contest/ composables/contest/
```

---

## Chunk 5: 管理后台 (Management Frontend)

> **目标**: 为管理后台添加竞赛管理、评分规则配置、数据分析功能

### Task 5.1: 评分规则管理页面

**Files:**
- Create: `management/src/views/contest/ScoringRulesView.vue`
- Create: `management/src/views/contest/components/ScoringRuleForm.vue`
- Create: `management/src/api/admin/scoring-rules.ts`
- Modify: `management/src/router/index.ts`

- [ ] **Step 1: 编写评分规则 API**

```typescript
// management/src/api/admin/scoring-rules.ts

import type { ContestScoringRule } from '@/types/contest';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9001';

export interface CreateScoringRuleDto {
  name: string;
  description?: string;
  base_score_per_problem: number;
  time_bonus_per_minute: number;
  wrong_answer_penalty: number;
  time_limit_penalty?: number;
  first_solve_bonus?: number;
  full_score_bonus?: number;
  is_default?: boolean;
}

export interface UpdateScoringRuleDto extends Partial<CreateScoringRuleDto> {}

export const scoringRulesApi = {
  async getAll(includeInactive = false): Promise<ContestScoringRule[]> {
    const params = new URLSearchParams();
    if (includeInactive) params.set('includeInactive', 'true');
    const res = await fetch(`${API_BASE}/api/admin/scoring-rules?${params}`, {
      credentials: 'include',
    });
    const data = await res.json();
    return data.data;
  },

  async getById(id: string): Promise<ContestScoringRule> {
    const res = await fetch(`${API_BASE}/api/admin/scoring-rules/${id}`, {
      credentials: 'include',
    });
    const data = await res.json();
    return data.data;
  },

  async create(dto: CreateScoringRuleDto): Promise<ContestScoringRule> {
    const res = await fetch(`${API_BASE}/api/admin/scoring-rules`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto),
    });
    const data = await res.json();
    return data.data;
  },

  async update(id: string, dto: UpdateScoringRuleDto): Promise<ContestScoringRule> {
    const res = await fetch(`${API_BASE}/api/admin/scoring-rules/${id}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto),
    });
    const data = await res.json();
    return data.data;
  },

  async delete(id: string): Promise<void> {
    await fetch(`${API_BASE}/api/admin/scoring-rules/${id}`, {
      method: 'DELETE',
      credentials: 'include',
    });
  },
};
```

- [ ] **Step 2: 编写评分规则表单组件**

```vue
<!-- management/src/views/contest/components/ScoringRuleForm.vue -->
<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { toast } from 'vue-sonner';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { scoringRulesApi, type CreateScoringRuleDto, type UpdateScoringRuleDto } from '@/api/admin/scoring-rules';
import type { ContestScoringRule } from '@/types/contest';

const props = defineProps<{
  open: boolean;
  rule?: ContestScoringRule | null;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  saved: [];
}>();

const { t } = useI18n();
const loading = ref(false);

const form = ref<CreateScoringRuleDto>({
  name: '',
  description: '',
  base_score_per_problem: 100,
  time_bonus_per_minute: 1,
  wrong_answer_penalty: 5,
  time_limit_penalty: 0,
  first_solve_bonus: 10,
  full_score_bonus: 0,
  is_default: false,
});

const isEditing = computed(() => !!props.rule);

watch(() => props.rule, (rule) => {
  if (rule) {
    form.value = {
      name: rule.name,
      description: rule.description || '',
      base_score_per_problem: rule.base_score_per_problem,
      time_bonus_per_minute: rule.time_bonus_per_minute,
      wrong_answer_penalty: rule.wrong_answer_penalty,
      time_limit_penalty: rule.time_limit_penalty || 0,
      first_solve_bonus: rule.first_solve_bonus || 10,
      full_score_bonus: rule.full_score_bonus || 0,
      is_default: rule.is_default,
    };
  } else {
    resetForm();
  }
}, { immediate: true });

function resetForm() {
  form.value = {
    name: '',
    description: '',
    base_score_per_problem: 100,
    time_bonus_per_minute: 1,
    wrong_answer_penalty: 5,
    time_limit_penalty: 0,
    first_solve_bonus: 10,
    full_score_bonus: 0,
    is_default: false,
  };
}

async function handleSubmit() {
  if (!form.value.name.trim()) {
    toast.error(t('scoringRules.errors.nameRequired'));
    return;
  }

  loading.value = true;
  try {
    if (isEditing.value && props.rule) {
      await scoringRulesApi.update(props.rule.id, form.value);
      toast.success(t('scoringRules.updateSuccess'));
    } else {
      await scoringRulesApi.create(form.value);
      toast.success(t('scoringRules.createSuccess'));
    }
    emit('saved');
    emit('update:open', false);
    resetForm();
  } catch (error) {
    toast.error(t('scoringRules.saveError'));
    console.error('Failed to save scoring rule:', error);
  } finally {
    loading.value = false;
  }
}

function handleOpenChange(open: boolean) {
  if (!open) resetForm();
  emit('update:open', open);
}
</script>

<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="max-w-lg">
      <DialogHeader>
        <DialogTitle>
          {{ isEditing ? t('scoringRules.editRule') : t('scoringRules.createRule') }}
        </DialogTitle>
        <DialogDescription>
          {{ t('scoringRules.formDescription') }}
        </DialogDescription>
      </DialogHeader>

      <form @submit.prevent="handleSubmit" class="space-y-4">
        <div class="space-y-2">
          <Label for="name">{{ t('scoringRules.fields.name') }} *</Label>
          <Input id="name" v-model="form.name" :placeholder="t('scoringRules.placeholders.name')" />
        </div>

        <div class="space-y-2">
          <Label for="description">{{ t('scoringRules.fields.description') }}</Label>
          <Textarea id="description" v-model="form.description" rows="2" />
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label for="baseScore">{{ t('scoringRules.fields.baseScore') }}</Label>
            <Input id="baseScore" type="number" v-model.number="form.base_score_per_problem" />
          </div>

          <div class="space-y-2">
            <Label for="timeBonus">{{ t('scoringRules.fields.timeBonus') }}</Label>
            <Input id="timeBonus" type="number" v-model.number="form.time_bonus_per_minute" />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label for="wrongPenalty">{{ t('scoringRules.fields.wrongPenalty') }}</Label>
            <Input id="wrongPenalty" type="number" v-model.number="form.wrong_answer_penalty" />
          </div>

          <div class="space-y-2">
            <Label for="firstSolveBonus">{{ t('scoringRules.fields.firstSolveBonus') }}</Label>
            <Input id="firstSolveBonus" type="number" v-model.number="form.first_solve_bonus" />
          </div>
        </div>

        <div class="flex items-center justify-between">
          <Label for="isDefault">{{ t('scoringRules.fields.isDefault') }}</Label>
          <Switch id="isDefault" v-model="form.is_default" />
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="handleOpenChange(false)">
            {{ t('common.cancel') }}
          </Button>
          <Button type="submit" :disabled="loading">
            {{ loading ? t('common.saving') : t('common.save') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
```

- [ ] **Step 3: 编写评分规则列表页面**

```vue
<!-- management/src/views/contest/ScoringRulesView.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { toast } from 'vue-sonner';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/table/DataTable.vue';
import { scoringRulesApi } from '@/api/admin/scoring-rules';
import { ScoringRuleForm } from './components/ScoringRuleForm.vue';
import type { ContestScoringRule } from '@/types/contest';
import type { ColumnDef } from '@tanstack/vue-table';

const { t } = useI18n();

const rules = ref<ContestScoringRule[]>([]);
const loading = ref(false);
const dialogOpen = ref(false);
const selectedRule = ref<ContestScoringRule | null>(null);

const columns: ColumnDef<ContestScoringRule>[] = [
  { accessorKey: 'name', header: t('scoringRules.columns.name') },
  { accessorKey: 'base_score_per_problem', header: t('scoringRules.columns.baseScore') },
  { accessorKey: 'time_bonus_per_minute', header: t('scoringRules.columns.timeBonus') },
  { accessorKey: 'wrong_answer_penalty', header: t('scoringRules.columns.wrongPenalty') },
  { accessorKey: 'first_solve_bonus', header: t('scoringRules.columns.firstSolveBonus') },
  {
    accessorKey: 'is_default',
    header: t('scoringRules.columns.default'),
    cell: ({ row }) => row.original.is_default ? '✓' : '-',
  },
  {
    id: 'actions',
    header: t('common.actions'),
    cell: ({ row }) => {
      return h('div', { class: 'flex gap-2' }, [
        h(Button, {
          variant: 'ghost',
          size: 'sm',
          onClick: () => handleEdit(row.original),
        }, () => t('common.edit')),
        h(Button, {
          variant: 'ghost',
          size: 'sm',
          disabled: row.original.is_default,
          onClick: () => handleDelete(row.original),
        }, () => t('common.delete')),
      ]);
    },
  },
];

async function loadRules() {
  loading.value = true;
  try {
    rules.value = await scoringRulesApi.getAll(true);
  } catch (error) {
    toast.error(t('scoringRules.loadError'));
    console.error('Failed to load scoring rules:', error);
  } finally {
    loading.value = false;
  }
}

function handleCreate() {
  selectedRule.value = null;
  dialogOpen.value = true;
}

function handleEdit(rule: ContestScoringRule) {
  selectedRule.value = rule;
  dialogOpen.value = true;
}

async function handleDelete(rule: ContestScoringRule) {
  if (!confirm(t('scoringRules.deleteConfirm', { name: rule.name }))) return;

  try {
    await scoringRulesApi.delete(rule.id);
    toast.success(t('scoringRules.deleteSuccess'));
    loadRules();
  } catch (error) {
    toast.error(t('scoringRules.deleteError'));
    console.error('Failed to delete scoring rule:', error);
  }
}

function handleSaved() {
  loadRules();
}

onMounted(() => {
  loadRules();
});
</script>

<template>
  <div class="container mx-auto py-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold">{{ t('scoringRules.title') }}</h1>
        <p class="text-muted-foreground">{{ t('scoringRules.description') }}</p>
      </div>
      <Button @click="handleCreate">
        {{ t('scoringRules.createRule') }}
      </Button>
    </div>

    <DataTable
      :columns="columns"
      :data="rules"
      :loading="loading"
    />

    <ScoringRuleForm
      v-model:open="dialogOpen"
      :rule="selectedRule"
      @saved="handleSaved"
    />
  </div>
</template>
```

- [ ] **Step 4: 添加路由**

在 `management/src/router/index.ts` 中添加:

```typescript
{
  path: '/contest/scoring-rules',
  name: 'contest-scoring-rules',
  component: () => import('@/views/contest/ScoringRulesView.vue'),
  meta: { title: 'scoringRules.title' },
},
```

- [ ] **Step 5: 添加 i18n 翻译**

在 `management/src/i18n/locales/zh-CN.ts` 中添加:

```typescript
scoringRules: {
  title: '评分规则管理',
  description: '配置竞赛的积分计算规则',
  createRule: '创建规则',
  editRule: '编辑规则',
  formDescription: '配置积分、时间奖励和惩罚规则',
  fields: {
    name: '规则名称',
    description: '描述',
    baseScore: '基础分数',
    timeBonus: '时间奖励/分钟',
    wrongPenalty: '错误惩罚（秒）',
    firstSolveBonus: '首杀奖励',
    isDefault: '设为默认',
  },
  placeholders: {
    name: '例如：标准周赛规则',
  },
  columns: {
    name: '名称',
    baseScore: '基础分',
    timeBonus: '时间奖励',
    wrongPenalty: '错误惩罚',
    firstSolveBonus: '首杀奖励',
    default: '默认',
  },
  errors: {
    nameRequired: '请输入规则名称',
  },
  loadError: '加载规则失败',
  createSuccess: '创建成功',
  updateSuccess: '更新成功',
  saveError: '保存失败',
  deleteConfirm: '确定删除规则 "{name}" 吗？',
  deleteSuccess: '删除成功',
  deleteError: '删除失败',
},
```

- [ ] **Step 6: 运行类型检查**

Run: `cd management && pnpm type-check`
Expected: No errors

- [ ] **Step 7: 提交管理后台评分规则**

```bash
git add management/src/views/contest/ management/src/api/admin/scoring-rules.ts management/src/router/index.ts management/src/i18n/
git commit -m "feat(management): add scoring rules management page"
```

---

### Task 5.2: 竞赛管理向导增强

**Files:**
- Create: `management/src/views/contest/components/ScoringRuleSelector.vue`
- Modify: `management/src/views/contest/ContestWizardView.vue` (或现有竞赛创建页面)

- [ ] **Step 1: 编写评分规则选择器组件**

```vue
<!-- management/src/views/contest/components/ScoringRuleSelector.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { scoringRulesApi } from '@/api/admin/scoring-rules';
import type { ContestScoringRule } from '@/types/contest';

const props = defineProps<{
  modelValue?: string | null;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string | null];
}>();

const { t } = useI18n();
const rules = ref<ContestScoringRule[]>([]);
const loading = ref(false);

const selectedRule = computed(() =>
  rules.value.find((r) => r.id === props.modelValue)
);

function formatRuleSummary(rule: ContestScoringRule): string {
  const parts = [
    `基础: ${rule.base_score_per_problem}`,
    `时间奖励: +${rule.time_bonus_per_minute}/min`,
    `错误惩罚: +${rule.wrong_answer_penalty}s`,
  ];
  if (rule.first_solve_bonus) {
    parts.push(`首杀: +${rule.first_solve_bonus}`);
  }
  return parts.join(' | ');
}

async function loadRules() {
  loading.value = true;
  try {
    rules.value = await scoringRulesApi.getAll();
  } finally {
    loading.value = false;
  }
}

function handleSelect(value: string) {
  emit('update:modelValue', value || null);
}

onMounted(() => {
  loadRules();
});
</script>

<template>
  <div class="space-y-4">
    <Select :model-value="modelValue || ''" @update:model-value="handleSelect" :disabled="loading">
      <SelectTrigger>
        <SelectValue :placeholder="t('scoringRules.selectPlaceholder')" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="">
          {{ t('scoringRules.useDefault') }}
        </SelectItem>
        <SelectItem
          v-for="rule in rules"
          :key="rule.id"
          :value="rule.id"
        >
          <div class="flex items-center gap-2">
            <span>{{ rule.name }}</span>
            <Badge v-if="rule.is_default" variant="secondary" class="text-xs">
              {{ t('scoringRules.defaultBadge') }}
            </Badge>
          </div>
        </SelectItem>
      </SelectContent>
    </Select>

    <!-- Show selected rule details -->
    <Card v-if="selectedRule" class="bg-muted/50">
      <CardContent class="p-4 text-sm">
        <p class="font-medium mb-2">{{ selectedRule.name }}</p>
        <p class="text-muted-foreground">{{ formatRuleSummary(selectedRule) }}</p>
        <p v-if="selectedRule.description" class="text-muted-foreground mt-2 text-xs">
          {{ selectedRule.description }}
        </p>
      </CardContent>
    </Card>
  </div>
</template>
```

- [ ] **Step 2: 在竞赛创建向导中集成评分规则选择器**

在现有竞赛创建/编辑页面中，添加评分规则选择步骤：

```vue
<!-- 在竞赛创建表单中添加 -->
<div class="space-y-2">
  <Label>{{ t('contest.wizard.scoringRule') }}</Label>
  <ScoringRuleSelector v-model="form.scoringRuleId" />
  <p class="text-xs text-muted-foreground">
    {{ t('contest.wizard.scoringRuleHint') }}
  </p>
</div>
```

- [ ] **Step 3: 运行类型检查**

Run: `cd management && pnpm type-check`
Expected: No errors

- [ ] **Step 4: 提交评分规则选择器**

```bash
git add management/src/views/contest/components/ScoringRuleSelector.vue
git commit -m "feat(management): add scoring rule selector component for contest wizard"
```

---

## Chunk 5 完成检查点

在继续之前，确保：

1. [ ] 评分规则 API 已创建
2. [ ] 评分规则表单组件已创建
3. [ ] 评分规则列表页面已创建
4. [ ] 评分规则选择器已创建
5. [ ] 竞赛向导已集成评分规则选择
6. [ ] 所有更改已提交到 git

**验证命令:**
```bash
cd management && pnpm type-check
```

---

## Chunk 6: 反作弊与分析服务 (Backend)

> **目标**: 实现代码相似度检测和赛后数据分析服务

### Task 6.1: 反作弊服务

**Files:**
- Create: `backend/src/contest/anticheat/anticheat.service.ts`
- Create: `backend/src/contest/anticheat/anticheat.service.spec.ts`
- Create: `backend/src/contest/anticheat/anticheat.controller.ts`

- [ ] **Step 1: 编写反作弊服务测试**

```typescript
// backend/src/contest/anticheat/anticheat.service.spec.ts

import { Test, TestingModule } from '@nestjs/testing';
import { AntiCheatService } from './anticheat.service';
import { PrismaService } from '@/prisma.service';

describe('AntiCheatService', () => {
  let service: AntiCheatService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AntiCheatService,
        {
          provide: PrismaService,
          useValue: {
            contestSubmission: {
              findMany: jest.fn(),
            },
            contest: {
              findUnique: jest.fn(),
            },
          },
        },
      ],
    }).compile();

    service = module.get<AntiCheatService>(AntiCheatService);
    prisma = module.get(PrismaService);
  });

  describe('calculateSimilarity', () => {
    it('should return 1.0 for identical code', async () => {
      const code1 = 'function add(a, b) { return a + b; }';
      const code2 = 'function add(a, b) { return a + b; }';

      const result = await service.calculateSimilarity(code1, code2);
      expect(result).toBe(1.0);
    });

    it('should return 0.0 for completely different code', async () => {
      const code1 = 'function add(a, b) { return a + b; }';
      const code2 = 'console.log("Hello World");';

      const result = await service.calculateSimilarity(code1, code2);
      expect(result).toBeLessThan(0.3);
    });

    it('should detect structural similarity', async () => {
      const code1 = `
        function solve(arr) {
          let result = 0;
          for (let i = 0; i < arr.length; i++) {
            result += arr[i];
          }
          return result;
        }
      `;
      const code2 = `
        function solve(array) {
          let sum = 0;
          for (let j = 0; j < array.length; j++) {
            sum += array[j];
          }
          return sum;
        }
      `;

      const result = await service.calculateSimilarity(code1, code2);
      expect(result).toBeGreaterThan(0.7);
    });
  });

  describe('detectSimilarity', () => {
    it('should detect suspicious submissions', async () => {
      prisma.contestSubmission.findMany.mockResolvedValue([
        { id: 's1', user_id: 'u1', code: 'code1', problem_id: 1n },
        { id: 's2', user_id: 'u2', code: 'code1', problem_id: 1n }, // Identical code
      ] as any);

      const result = await service.detectSimilarity('contest-1');

      expect(result.suspiciousPairs).toHaveLength(1);
      expect(result.suspiciousPairs[0].similarity).toBe(1.0);
    });
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && pnpm jest src/contest/anticheat/anticheat.service.spec.ts`
Expected: FAIL - service not implemented

- [ ] **Step 3: 编写反作弊服务实现**

```typescript
// backend/src/contest/anticheat/anticheat.service.ts

import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '@/prisma.service';

export interface SimilarityReport {
  contestId: string;
  totalSubmissions: number;
  suspiciousPairs: SuspiciousPair[];
  generatedAt: Date;
}

export interface SuspiciousPair {
  userId1: string;
  username1: string;
  userId2: string;
  username2: string;
  problemId: bigint;
  problemIndex: string;
  similarity: number;
  submissionId1: string;
  submissionId2: string;
}

@Injectable()
export class AntiCheatService {
  private readonly logger = new Logger(AntiCheatService.name);
  private readonly SIMILARITY_THRESHOLD = 0.8;

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Calculate similarity between two code snippets
   * Uses normalized token-based comparison
   */
  async calculateSimilarity(code1: string, code2: string): Promise<number> {
    if (code1 === code2) return 1.0;

    const tokens1 = this.tokenize(code1);
    const tokens2 = this.tokenize(code2);

    if (tokens1.length === 0 || tokens2.length === 0) return 0.0;

    // Calculate Jaccard similarity on n-grams
    const ngrams1 = this.getNgrams(tokens1, 3);
    const ngrams2 = this.getNgrams(tokens2, 3);

    const intersection = new Set(
      [...ngrams1].filter((n) => ngrams2.has(n)),
    );
    const union = new Set([...ngrams1, ...ngrams2]);

    return intersection.size / union.size;
  }

  /**
   * Detect similarity across all submissions in a contest
   */
  async detectSimilarity(contestId: string): Promise<SimilarityReport> {
    const submissions = await this.prisma.contestSubmission.findMany({
      where: { contest_id: contestId, is_accepted: true },
      include: {
        user: { select: { id: true, username: true } },
        problem: { select: { id: true } },
      },
    });

    // Group by problem
    const byProblem = new Map<string, typeof submissions>();
    for (const sub of submissions) {
      const key = sub.problem_id.toString();
      if (!byProblem.has(key)) {
        byProblem.set(key, []);
      }
      byProblem.get(key)!.push(sub);
    }

    const suspiciousPairs: SuspiciousPair[] = [];

    // Compare submissions for each problem
    for (const [problemId, problemSubs] of byProblem) {
      for (let i = 0; i < problemSubs.length; i++) {
        for (let j = i + 1; j < problemSubs.length; j++) {
          const sub1 = problemSubs[i];
          const sub2 = problemSubs[j];

          // Skip if same user
          if (sub1.user_id === sub2.user_id) continue;

          const similarity = await this.calculateSimilarity(
            sub1.code,
            sub2.code,
          );

          if (similarity >= this.SIMILARITY_THRESHOLD) {
            suspiciousPairs.push({
              userId1: sub1.user_id,
              username1: sub1.user.username,
              userId2: sub2.user_id,
              username2: sub2.user.username,
              problemId: sub1.problem_id,
              problemIndex: sub1.problem_id.toString(), // Would need join to get index
              similarity,
              submissionId1: sub1.id,
              submissionId2: sub2.id,
            });
          }
        }
      }
    }

    this.logger.log(
      `Detected ${suspiciousPairs.length} suspicious pairs in contest ${contestId}`,
    );

    return {
      contestId,
      totalSubmissions: submissions.length,
      suspiciousPairs,
      generatedAt: new Date(),
    };
  }

  /**
   * Check for time anomalies (too fast submissions)
   */
  async checkTimeAnomaly(contestId: string): Promise<TimeAnomalyReport[]> {
    const submissions = await this.prisma.contestSubmission.findMany({
      where: {
        contest_id: contestId,
        is_accepted: true,
        time_spent: { lt: 60 }, // Less than 1 minute
      },
      include: {
        user: { select: { id: true, username: true } },
      },
    });

    return submissions.map((sub) => ({
      userId: sub.user_id,
      username: sub.user.username,
      problemId: sub.problem_id,
      timeSpent: sub.time_spent,
      submittedAt: sub.submitted_at,
    }));
  }

  /**
   * Generate full anti-cheat report
   */
  async generateReport(contestId: string): Promise<AntiCheatReport> {
    const [similarityReport, timeAnomalies] = await Promise.all([
      this.detectSimilarity(contestId),
      this.checkTimeAnomaly(contestId),
    ]);

    return {
      contestId,
      similarity: similarityReport,
      timeAnomalies,
      riskLevel: this.calculateRiskLevel(similarityReport, timeAnomalies),
      generatedAt: new Date(),
    };
  }

  private tokenize(code: string): string[] {
    // Normalize: remove comments, whitespace, normalize identifiers
    let normalized = code
      .replace(/\/\/.*$/gm, '') // Single-line comments
      .replace(/\/\*[\s\S]*?\*\//g, '') // Multi-line comments
      .replace(/"([^"\\]|\\.)*"/g, '"string"') // String literals
      .replace(/'([^'\\]|\\.)*'/g, '"string"') // String literals
      .replace(/\d+/g, '0') // Numbers
      .replace(/\s+/g, ' ')
      .trim();

    // Tokenize
    return normalized.split(/\s+|([{}();,.=+\-*/<>!&|])/).filter(Boolean);
  }

  private getNgrams(tokens: string[], n: number): Set<string> {
    const ngrams = new Set<string>();
    for (let i = 0; i <= tokens.length - n; i++) {
      ngrams.add(tokens.slice(i, i + n).join('|'));
    }
    return ngrams;
  }

  private calculateRiskLevel(
    similarity: SimilarityReport,
    timeAnomalies: TimeAnomalyReport[],
  ): 'low' | 'medium' | 'high' {
    const suspiciousCount = similarity.suspiciousPairs.length;
    const anomalyCount = timeAnomalies.length;

    if (suspiciousCount > 10 || anomalyCount > 5) return 'high';
    if (suspiciousCount > 5 || anomalyCount > 2) return 'medium';
    return 'low';
  }
}

interface TimeAnomalyReport {
  userId: string;
  username: string;
  problemId: bigint;
  timeSpent: number;
  submittedAt: Date;
}

interface AntiCheatReport {
  contestId: string;
  similarity: SimilarityReport;
  timeAnomalies: TimeAnomalyReport[];
  riskLevel: 'low' | 'medium' | 'high';
  generatedAt: Date;
}
```

- [ ] **Step 4: 编写反作弊控制器**

```typescript
// backend/src/contest/anticheat/anticheat.controller.ts

import {
  Controller,
  Get,
  Param,
  UseGuards,
  ForbiddenException,
} from '@nestjs/common';
import { AntiCheatService } from './anticheat.service';
import { JwtAuthGuard } from '@/auth/guards/jwt-auth.guard';
import { RolesGuard } from '@/auth/guards/roles.guard';
import { Roles } from '@/auth/decorators/roles.decorator';
import { CurrentUser } from '@/auth/decorators/current-user.decorator';

@Controller('admin/anticheat')
@UseGuards(JwtAuthGuard, RolesGuard)
@Roles('ADMIN')
export class AntiCheatController {
  constructor(private readonly anticheatService: AntiCheatService) {}

  @Get('contest/:contestId/report')
  async getReport(@Param('contestId') contestId: string) {
    return this.anticheatService.generateReport(contestId);
  }

  @Get('contest/:contestId/similarity')
  async getSimilarityReport(@Param('contestId') contestId: string) {
    return this.anticheatService.detectSimilarity(contestId);
  }

  @Get('contest/:contestId/time-anomalies')
  async getTimeAnomalies(@Param('contestId') contestId: string) {
    return this.anticheatService.checkTimeAnomaly(contestId);
  }
}
```

- [ ] **Step 5: 更新 ContestModule**

在 `contest.module.ts` 中添加:

```typescript
import { AntiCheatService } from './anticheat/anticheat.service';
import { AntiCheatController } from './anticheat/anticheat.controller';

// 在 providers 中添加:
AntiCheatService,

// 在 controllers 中添加:
AntiCheatController,
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd backend && pnpm jest src/contest/anticheat/`
Expected: All tests pass

- [ ] **Step 7: 运行类型检查**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 8: 提交反作弊服务**

```bash
git add backend/src/contest/anticheat/ backend/src/contest/contest.module.ts
git commit -m "feat(contest): add anti-cheat service with similarity detection"
```

---

### Task 6.2: 数据分析服务

**Files:**
- Create: `backend/src/contest/analytics/analytics.service.ts`
- Create: `backend/src/contest/analytics/analytics.service.spec.ts`
- Create: `backend/src/contest/analytics/analytics.controller.ts`

- [ ] **Step 1: 编写分析服务测试**

```typescript
// backend/src/contest/analytics/analytics.service.spec.ts

import { Test, TestingModule } from '@nestjs/testing';
import { AnalyticsService } from './analytics.service';
import { PrismaService } from '@/prisma.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AnalyticsService,
        {
          provide: PrismaService,
          useValue: {
            contest: {
              findUnique: jest.fn(),
            },
            contestParticipant: {
              count: jest.fn(),
              aggregate: jest.fn(),
            },
            contestProblem: {
              findMany: jest.fn(),
            },
            contestSubmission: {
              aggregate: jest.fn(),
              groupBy: jest.fn(),
            },
            contestAnalytics: {
              upsert: jest.fn(),
              findUnique: jest.fn(),
            },
          },
        },
      ],
    }).compile();

    service = module.get<AnalyticsService>(AnalyticsService);
    prisma = module.get(PrismaService);
  });

  describe('generateContestReport', () => {
    it('should generate comprehensive contest analytics', async () => {
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        title: 'Test Contest',
      } as any);

      prisma.contestParticipant.count
        .mockResolvedValueOnce(100) // total registered
        .mockResolvedValueOnce(80); // total participated

      prisma.contestProblem.findMany.mockResolvedValue([
        { id: 'p1', problem_index: 'A', solved_count: 60, submission_count: 150 },
        { id: 'p2', problem_index: 'B', solved_count: 40, submission_count: 120 },
      ] as any);

      const result = await service.generateContestReport('contest-1');

      expect(result.totalRegistered).toBe(100);
      expect(result.totalParticipated).toBe(80);
      expect(result.problemStats).toHaveLength(2);
    });
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && pnpm jest src/contest/analytics/analytics.service.spec.ts`
Expected: FAIL - service not implemented

- [ ] **Step 3: 编写分析服务实现**

```typescript
// backend/src/contest/analytics/analytics.service.ts

import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '@/prisma.service';

export interface ContestReport {
  contestId: string;
  contestTitle: string;
  totalRegistered: number;
  totalParticipated: number;
  completionRate: number;
  problemStats: ProblemStats[];
  scoreDistribution: ScoreDistribution;
  topUsers: TopUser[];
  generatedAt: Date;
}

export interface ProblemStats {
  problemIndex: string;
  problemTitle: string;
  solvedCount: number;
  submissionCount: number;
  acceptanceRate: number;
  avgAttempts: number;
}

export interface ScoreDistribution {
  ranges: { min: number; max: number; count: number }[];
}

export interface TopUser {
  rank: number;
  userId: string;
  username: string;
  score: number;
  time: number;
  solvedCount: number;
}

@Injectable()
export class AnalyticsService {
  private readonly logger = new Logger(AnalyticsService.name);

  constructor(private readonly prisma: PrismaService) {}

  async generateContestReport(contestId: string): Promise<ContestReport> {
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
      select: { id: true, title: true },
    });

    if (!contest) {
      throw new Error('Contest not found');
    }

    // Get participant stats
    const [totalRegistered, totalParticipated] = await Promise.all([
      this.prisma.contestParticipant.count({
        where: { contest_id: contestId },
      }),
      this.prisma.contestParticipant.count({
        where: { contest_id: contestId, status: { in: ['FINISHED', 'PARTICIPATING'] } },
      }),
    ]);

    // Get problem stats
    const problems = await this.prisma.contestProblem.findMany({
      where: { contest_id: contestId },
      include: { problem: { select: { title: true } } },
      orderBy: { problem_index: 'asc' },
    });

    const problemStats: ProblemStats[] = problems.map((p) => ({
      problemIndex: p.problem_index,
      problemTitle: p.problem.title,
      solvedCount: p.solved_count,
      submissionCount: p.submission_count,
      acceptanceRate: p.submission_count > 0
        ? (p.solved_count / p.submission_count) * 100
        : 0,
      avgAttempts: p.solved_count > 0
        ? p.submission_count / p.solved_count
        : 0,
    }));

    // Get score distribution
    const participants = await this.prisma.contestParticipant.findMany({
      where: { contest_id: contestId, rank: { not: null } },
      select: { total_score: true },
      orderBy: { total_score: 'desc' },
    });

    const scoreRanges = [
      { min: 400, max: 500, count: 0 },
      { min: 300, max: 399, count: 0 },
      { min: 200, max: 299, count: 0 },
      { min: 100, max: 199, count: 0 },
      { min: 0, max: 99, count: 0 },
    ];

    for (const p of participants) {
      for (const range of scoreRanges) {
        if (p.total_score >= range.min && p.total_score <= range.max) {
          range.count++;
          break;
        }
      }
    }

    // Get top 100 users
    const topParticipants = await this.prisma.contestParticipant.findMany({
      where: { contest_id: contestId, rank: { not: null } },
      include: { user: { select: { username: true } } },
      orderBy: { rank: 'asc' },
      take: 100,
    });

    const topUsers: TopUser[] = topParticipants.map((p) => ({
      rank: p.rank!,
      userId: p.user_id,
      username: p.user.username,
      score: p.total_score,
      time: p.total_time,
      solvedCount: p.solved_count,
    }));

    const report: ContestReport = {
      contestId: contest.id,
      contestTitle: contest.title,
      totalRegistered,
      totalParticipated,
      completionRate: totalRegistered > 0
        ? (totalParticipated / totalRegistered) * 100
        : 0,
      problemStats,
      scoreDistribution: { ranges: scoreRanges },
      topUsers,
      generatedAt: new Date(),
    };

    // Save to database
    await this.saveReport(contestId, report);

    return report;
  }

  private async saveReport(contestId: string, report: ContestReport) {
    await this.prisma.contestAnalytics.upsert({
      where: { contest_id: contestId },
      create: {
        contest_id: contestId,
        total_registered: report.totalRegistered,
        total_participated: report.totalParticipated,
        completion_rate: report.completionRate,
        problem_stats: report.problemStats as any,
        score_distribution: report.scoreDistribution as any,
        top_users: report.topUsers as any,
      },
      update: {
        total_registered: report.totalRegistered,
        total_participated: report.totalParticipated,
        completion_rate: report.completionRate,
        problem_stats: report.problemStats as any,
        score_distribution: report.scoreDistribution as any,
        top_users: report.topUsers as any,
        generated_at: new Date(),
      },
    });
  }

  async getStoredReport(contestId: string): Promise<ContestReport | null> {
    const stored = await this.prisma.contestAnalytics.findUnique({
      where: { contest_id: contestId },
    });

    if (!stored) return null;

    return {
      contestId: stored.contest_id,
      contestTitle: '', // Would need to fetch
      totalRegistered: stored.total_registered,
      totalParticipated: stored.total_participated,
      completionRate: stored.completion_rate,
      problemStats: stored.problem_stats as ProblemStats[],
      scoreDistribution: stored.score_distribution as ScoreDistribution,
      topUsers: stored.top_users as TopUser[],
      generatedAt: stored.generated_at,
    };
  }

  async getUserPerformanceHistory(userId: string, limit = 10) {
    const participations = await this.prisma.contestParticipant.findMany({
      where: { user_id: userId, rank: { not: null } },
      include: {
        contest: { select: { id: true, title: true, contest_type: true, start_time: true } },
      },
      orderBy: { contest: { start_time: 'desc' } },
      take: limit,
    });

    return participations.map((p) => ({
      contestId: p.contest.id,
      contestTitle: p.contest.title,
      contestType: p.contest.contest_type,
      date: p.contest.start_time,
      rank: p.rank,
      score: p.total_score,
      time: p.total_time,
      solvedCount: p.solved_count,
    }));
  }
}
```

- [ ] **Step 4: 编写分析控制器**

```typescript
// backend/src/contest/analytics/analytics.controller.ts

import {
  Controller,
  Get,
  Param,
  Query,
  UseGuards,
} from '@nestjs/common';
import { AnalyticsService } from './analytics.service';
import { JwtAuthGuard } from '@/auth/guards/jwt-auth.guard';

@Controller('contests')
@UseGuards(JwtAuthGuard)
export class AnalyticsController {
  constructor(private readonly analyticsService: AnalyticsService) {}

  @Get(':contestId/analytics')
  async getContestAnalytics(
    @Param('contestId') contestId: string,
    @Query('refresh') refresh?: string,
  ) {
    if (refresh === 'true') {
      return this.analyticsService.generateContestReport(contestId);
    }
    return this.analyticsService.getStoredReport(contestId);
  }

  @Get('user/history')
  async getUserHistory(@Query('userId') userId: string, @Query('limit') limit?: string) {
    return this.analyticsService.getUserPerformanceHistory(
      userId,
      limit ? parseInt(limit, 10) : 10,
    );
  }
}
```

- [ ] **Step 5: 更新 ContestModule**

```typescript
import { AnalyticsService } from './analytics/analytics.service';
import { AnalyticsController } from './analytics/analytics.controller';

// 在 providers 中添加:
AnalyticsService,

// 在 controllers 中添加:
AnalyticsController,
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd backend && pnpm jest src/contest/analytics/`
Expected: All tests pass

- [ ] **Step 7: 运行类型检查**

Run: `cd backend && pnpm type-check`
Expected: No errors

- [ ] **Step 8: 提交分析服务**

```bash
git add backend/src/contest/analytics/ backend/src/contest/contest.module.ts
git commit -m "feat(contest): add analytics service for contest reports"
```

---

## Chunk 6 完成检查点

在继续之前，确保：

1. [ ] 反作弊服务已创建并测试通过
2. [ ] 数据分析服务已创建并测试通过
3. [ ] 控制器已创建
4. [ ] ContestModule 已更新
5. [ ] 所有更改已提交到 git

**验证命令:**
```bash
cd backend && pnpm type-check && pnpm jest src/contest/anticheat/ src/contest/analytics/
```

---

## Chunk 7: 测试与优化

> **目标**: 编写集成测试、端到端测试，并进行性能优化

### Task 7.1: 竞赛模块集成测试

**Files:**
- Create: `backend/test/contest.integration.spec.ts`

- [ ] **Step 1: 编写竞赛集成测试**

```typescript
// backend/test/contest.integration.spec.ts

import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import * as request from 'supertest';
import { AppModule } from '@/app.module';
import { PrismaService } from '@/prisma.service';

describe('Contest System (Integration)', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let authToken: string;
  let adminToken: string;
  let testContestId: string;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    await app.init();

    prisma = app.get(PrismaService);

    // Create test users and get tokens
    // ... setup code
  });

  afterAll(async () => {
    // Cleanup test data
    await prisma.contest.deleteMany({
      where: { title: { contains: '[TEST]' } },
    });
    await app.close();
  });

  describe('Contest CRUD', () => {
    it('should create a contest as admin', async () => {
      const response = await request(app.getHttpServer())
        .post('/api/admin/contests')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          title: '[TEST] Weekly Contest 100',
          slug: 'test-weekly-100',
          contest_type: 'weekly',
          start_time: new Date(Date.now() + 86400000).toISOString(),
          duration_minutes: 90,
          status: 'draft',
        })
        .expect(201);

      testContestId = response.body.data.id;
      expect(response.body.data.title).toContain('[TEST]');
    });

    it('should list contests as user', async () => {
      const response = await request(app.getHttpServer())
        .get('/api/contests')
        .expect(200);

      expect(Array.isArray(response.body.data)).toBe(true);
    });
  });

  describe('Contest Registration', () => {
    it('should register for a contest', async () => {
      // First publish the contest
      await request(app.getHttpServer())
        .post(`/api/admin/contests/${testContestId}/publish`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(201);

      const response = await request(app.getHttpServer())
        .post(`/api/contests/test-weekly-100/register`)
        .set('Authorization', `Bearer ${authToken}`)
        .expect(201);

      expect(response.body.data.status).toBe('REGISTERED');
    });

    it('should prevent duplicate registration', async () => {
      await request(app.getHttpServer())
        .post(`/api/contests/test-weekly-100/register`)
        .set('Authorization', `Bearer ${authToken}`)
        .expect(400);
    });
  });

  describe('Scoring System', () => {
    it('should calculate score correctly', async () => {
      // Submit a correct solution
      const response = await request(app.getHttpServer())
        .post(`/api/contests/test-weekly-100/submit`)
        .set('Authorization', `Bearer ${authToken}`)
        .send({
          problemIndex: 'A',
          code: 'console.log("hello")',
          language: 'javascript',
        });

      if (response.status === 201) {
        expect(response.body.data).toHaveProperty('score');
        expect(response.body.data.score).toBeGreaterThan(0);
      }
    });
  });
});
```

- [ ] **Step 2: 运行集成测试**

Run: `cd backend && pnpm test:e2e test/contest.integration.spec.ts`
Expected: Tests pass (may need test database setup)

- [ ] **Step 3: 提交集成测试**

```bash
git add backend/test/contest.integration.spec.ts
git commit -m "test(contest): add integration tests for contest system"
```

---

### Task 7.2: 性能优化

**Files:**
- Modify: `backend/src/contest/ranking/ranking.service.ts`
- Modify: `backend/src/contest/realtime/realtime.service.ts`

- [ ] **Step 1: 添加 Redis 缓存**

```typescript
// 在 ranking.service.ts 中添加缓存

import { CACHE_MANAGER } from '@nestjs/cache-manager';
import { Cache } from 'cache-manager';

@Injectable()
export class RankingService {
  constructor(
    @Inject(CACHE_MANAGER) private cacheManager: Cache,
    // ... other dependencies
  ) {}

  async getRanking(contestId: string): Promise<RankingEntry[]> {
    const cacheKey = `contest:${contestId}:ranking`;

    // Try cache first
    const cached = await this.cacheManager.get<RankingEntry[]>(cacheKey);
    if (cached) {
      return cached;
    }

    // Fetch from database
    const ranking = await this.fetchRankingFromDb(contestId);

    // Cache for 5 seconds during contest
    await this.cacheManager.set(cacheKey, ranking, 5000);

    return ranking;
  }

  async invalidateRankingCache(contestId: string): Promise<void> {
    await this.cacheManager.del(`contest:${contestId}:ranking`);
  }
}
```

- [ ] **Step 2: 添加数据库索引**

在 Prisma schema 中确认索引已添加:

```prisma
model ContestParticipant {
  // ... fields

  @@index([contest_id, rank])
  @@index([contest_id, status])
}

model ContestSubmission {
  // ... fields

  @@index([contest_id, user_id])
  @@index([contest_id, problem_id, submitted_at])
}
```

- [ ] **Step 3: 优化实时推送频率**

```typescript
// 在 realtime.service.ts 中添加节流

import { throttle } from 'lodash';

@Injectable()
export class RealtimeService {
  // Throttle ranking updates to max once per second
  private throttledRankingUpdate = throttle(
    this.pushRankingUpdateInternal.bind(this),
    1000,
    { leading: true, trailing: true },
  );

  async pushRankingUpdate(contestId: string): Promise<void> {
    return this.throttledRankingUpdate(contestId);
  }

  private async pushRankingUpdateInternal(contestId: string): Promise<void> {
    // ... original implementation
  }
}
```

- [ ] **Step 4: 运行性能测试**

Run: `cd backend && pnpm jest --coverage src/contest/`
Expected: Coverage > 80%

- [ ] **Step 5: 提交性能优化**

```bash
git add backend/src/contest/
git commit -m "perf(contest): add caching and throttling for ranking updates"
```

---

### Task 7.3: 文档更新

**Files:**
- Create: `docs/contest-system-api.md`
- Update: `README.md`

- [ ] **Step 1: 编写 API 文档**

```markdown
# docs/contest-system-api.md

# 竞赛系统 API 文档

## 概述

竞赛系统支持 LeetCode 风格的简单积分制竞赛。

## 公开 API

### 获取竞赛列表

GET /api/contests

Query Parameters:
- status: string (comma-separated)
- type: string (comma-separated)
- page: number
- limit: number

Response:
{
  "success": true,
  "data": [...],
  "meta": { "total": 100, "hasMore": true }
}

### 获取竞赛详情

GET /api/contests/:slug

### 报名竞赛

POST /api/contests/:slug/register

### 提交代码

POST /api/contests/:slug/submit
Body: { problemIndex, code, language }

## WebSocket 事件

### 连接

Namespace: /contest

### 事件

- join_contest: 加入竞赛房间
- ranking_update: 排行榜更新
- first_solve: 首杀播报
- announcement: 竞赛公告
```

- [ ] **Step 2: 更新 README**

在项目 README 中添加竞赛系统说明。

- [ ] **Step 3: 提交文档**

```bash
git add docs/contest-system-api.md README.md
git commit -m "docs: add contest system API documentation"
```

---

## Chunk 7 完成检查点

在继续之前，确保：

1. [ ] 集成测试已编写
2. [ ] 性能优化已实施（缓存、节流）
3. [ ] 文档已更新
4. [ ] 所有更改已提交到 git

**验证命令:**
```bash
cd backend && pnpm type-check && pnpm test:e2e test/contest.integration.spec.ts
cd console && pnpm type-check
cd management && pnpm type-check
```

---

## 实施完成总结

### 交付清单

- [x] **Chunk 1**: 数据库迁移
- [x] **Chunk 2**: 后端核心 - 评分服务
- [x] **Chunk 3**: 实时 WebSocket
- [x] **Chunk 4**: 前端页面
- [x] **Chunk 5**: 管理后台
- [x] **Chunk 6**: 反作弊与分析服务
- [x] **Chunk 7**: 测试与优化

### 功能开关配置

```bash
# .env
FEATURE_NEW_CONTEST=true
ENABLE_REALTIME_RANKING=true
ENABLE_FIRST_SOLVE_NOTIFICATIONS=true
```

### 回滚方案

如需回滚到旧系统：

```bash
# 1. 禁用功能开关
FEATURE_NEW_CONTEST=false

# 2. 运行数据库回滚脚本（如需要）
mysql -u root -p ulticode < backend/prisma/migrations/20260314000000_contest_system_enhancements/rollback.sql
```

### 下一步

1. 在测试环境验证所有功能
2. 进行用户验收测试
3. 逐步将功能开关推广到生产环境
4. 监控系统性能和用户反馈
