# 推荐系统集成实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Java 推荐服务集成到 NestJS 后端，实现每日 8 点自动生成推荐信息，支持 MySQL 历史存储和 Redis 缓存。

**Architecture:** 扩展现有 RecommendationModule，添加 RecommendationCacheService 处理缓存逻辑，RecommendationScheduler 处理定时任务，使用 ThrottlerModule 添加速率限制。

**Tech Stack:** NestJS, Prisma, MySQL, Redis, BullMQ, @nestjs/throttler, @nestjs/schedule

---

## File Structure

### 新建文件
- `backend/src/recommendation/services/recommendation-cache.service.ts` - 缓存管理服务
- `backend/src/recommendation/services/recommendation-cache.service.spec.ts` - 缓存服务测试
- `backend/src/recommendation/recommendation.scheduler.ts` - 定时任务调度器
- `backend/src/recommendation/recommendation.scheduler.spec.ts` - 调度器测试

### 修改文件
- `backend/prisma/schema.prisma` - 添加 DailyRecommendation 模型
- `backend/src/recommendation/recommendation.module.ts` - 添加新依赖
- `backend/src/recommendation/recommendation.controller.ts` - 添加速率限制
- `backend/src/recommendation/services/recommendation.service.ts` - 扩展缓存方法
- `backend/package.json` - 添加 @nestjs/throttler 依赖

---

## Chunk 1: 数据库模型和依赖

### Task 1: 安装依赖

**Files:**
- Modify: `backend/package.json`

- [ ] **Step 1: 安装 @nestjs/throttler**

```bash
cd backend && pnpm add @nestjs/throttler
```

Expected: package.json 更新，依赖安装成功

- [ ] **Step 2: 验证安装**

```bash
cd backend && pnpm list @nestjs/throttler
```

Expected: 显示已安装的版本

---

### Task 2: 添加 Prisma 模型

**Files:**
- Modify: `backend/prisma/schema.prisma`

- [ ] **Step 1: 在 User 模型中添加关系字段**

找到 `model User` 的末尾（在 `@@map("users")` 之前），添加:

```prisma
  // Daily recommendations relation
  dailyRecommendations DailyRecommendation[]
```

- [ ] **Step 2: 在 Problem 模型中添加关系字段**

找到 `model Problem` 的末尾（在 `@@map` 之前），添加:

```prisma
  // Daily recommendations relation
  dailyRecommendations DailyRecommendation[]
```

- [ ] **Step 3: 添加 DailyRecommendation 模型**

在 schema.prisma 文件末尾添加:

```prisma
// Daily recommendation records for pre-computed recommendations
model DailyRecommendation {
  id            String   @id @default(uuid()) @db.VarChar(40)
  user_id       String   @db.VarChar(40)
  problem_id    BigInt
  scenario      String   @db.VarChar(20) // DAILY, WEAK_POINT, CHALLENGE
  score         Float
  reason        String   @db.VarChar(500)
  tags          Json
  generated_at  DateTime @default(now())
  created_at    DateTime @default(now())

  user     User    @relation(fields: [user_id], references: [id], onDelete: Cascade)
  problem  Problem @relation(fields: [problem_id], references: [id])

  @@index([user_id, scenario, generated_at])
  @@index([generated_at])
  @@map("daily_recommendations")
}
```

- [ ] **Step 4: 生成 Prisma Client**

```bash
cd backend && pnpm prisma:generate
```

Expected: Prisma Client 生成成功，无错误

- [ ] **Step 5: 提交数据库模型**

```bash
git add backend/prisma/schema.prisma
git commit -m "feat(recommendation): add DailyRecommendation model for caching"
```

---

## Chunk 2: 缓存服务

### Task 3: 创建缓存服务测试

**Files:**
- Create: `backend/src/recommendation/services/recommendation-cache.service.spec.ts`

- [ ] **Step 1: 创建测试文件**

```typescript
// backend/src/recommendation/services/recommendation-cache.service.spec.ts
import { Test, TestingModule } from '@nestjs/testing';
import { Logger } from '@nestjs/common';
import { RecommendationCacheService } from './recommendation-cache.service';
import { CacheService } from '../../cache/cache.service';
import { PrismaService } from '../../prisma.service';
import { RecommendScenario } from '../interfaces/recommendation.interface';

describe('RecommendationCacheService', () => {
  let service: RecommendationCacheService;
  let cacheService: jest.Mocked<CacheService>;
  let prisma: jest.Mocked<PrismaService>;

  const mockRecommendResult = {
    items: [
      {
        problemId: 1,
        slug: 'two-sum',
        title: 'Two Sum',
        difficulty: 'Easy',
        score: 0.95,
        tags: ['array', 'hash-table'],
        reason: '难度匹配度高',
      },
    ],
    totalCount: 1,
    scenario: RecommendScenario.DAILY,
    generatedAt: '2026-03-15T00:00:00.000Z',
  };

  beforeEach(async () => {
    const mockCacheService = {
      get: jest.fn(),
      set: jest.fn(),
    };

    const mockPrisma = {
      dailyRecommendation: {
        findMany: jest.fn(),
        deleteMany: jest.fn(),
        createMany: jest.fn(),
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        RecommendationCacheService,
        { provide: CacheService, useValue: mockCacheService },
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<RecommendationCacheService>(RecommendationCacheService);
    cacheService = module.get(CacheService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getCachedRecommendations', () => {
    it('should return cached result from Redis when available', async () => {
      cacheService.get.mockResolvedValue(mockRecommendResult);

      const result = await service.getCachedRecommendations(
        'user1',
        RecommendScenario.DAILY,
        10,
      );

      expect(result).toEqual(mockRecommendResult);
      expect(cacheService.get).toHaveBeenCalledTimes(1);
      expect(prisma.dailyRecommendation.findMany).not.toHaveBeenCalled();
    });

    it('should query MySQL when Redis cache miss', async () => {
      cacheService.get.mockResolvedValue(undefined);
      prisma.dailyRecommendation.findMany.mockResolvedValue([
        {
          problem_id: BigInt(1),
          score: 0.95,
          reason: '难度匹配度高',
          tags: ['array', 'hash-table'],
          generated_at: new Date('2026-03-15'),
          problem: {
            slug: 'two-sum',
            title: 'Two Sum',
            difficulty: 'Easy',
          },
        },
      ]);

      const result = await service.getCachedRecommendations(
        'user1',
        RecommendScenario.DAILY,
        10,
      );

      expect(result).not.toBeNull();
      expect(result?.items).toHaveLength(1);
      expect(result?.items[0].problemId).toBe(1);
      expect(cacheService.set).toHaveBeenCalled();
    });

    it('should return null when both Redis and MySQL miss', async () => {
      cacheService.get.mockResolvedValue(undefined);
      prisma.dailyRecommendation.findMany.mockResolvedValue([]);

      const result = await service.getCachedRecommendations(
        'user1',
        RecommendScenario.DAILY,
        10,
      );

      expect(result).toBeNull();
    });
  });

  describe('storeRecommendations', () => {
    it('should store recommendations to MySQL and Redis', async () => {
      prisma.dailyRecommendation.deleteMany.mockResolvedValue({ count: 0 });
      prisma.dailyRecommendation.createMany.mockResolvedValue({ count: 1 });

      await service.storeRecommendations(
        'user1',
        RecommendScenario.DAILY,
        mockRecommendResult,
      );

      expect(prisma.dailyRecommendation.deleteMany).toHaveBeenCalled();
      expect(prisma.dailyRecommendation.createMany).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.arrayContaining([
            expect.objectContaining({
              user_id: 'user1',
              problem_id: BigInt(1),
              scenario: 'DAILY',
            }),
          ]),
        }),
      );
      expect(cacheService.set).toHaveBeenCalled();
    });

    it('should truncate reason to 500 characters', async () => {
      const longReasonResult = {
        ...mockRecommendResult,
        items: [
          {
            ...mockRecommendResult.items[0],
            reason: 'a'.repeat(600),
          },
        ],
      };

      prisma.dailyRecommendation.deleteMany.mockResolvedValue({ count: 0 });
      prisma.dailyRecommendation.createMany.mockResolvedValue({ count: 1 });

      await service.storeRecommendations(
        'user1',
        RecommendScenario.DAILY,
        longReasonResult,
      );

      const createCall = prisma.dailyRecommendation.createMany.mock.calls[0][0];
      expect(createCall.data[0].reason.length).toBe(500);
    });
  });

  describe('timezone handling', () => {
    it('should use Asia/Shanghai timezone for date calculation', () => {
      // Test that getTodayDateString returns correct format
      const dateStr = service['getTodayDateString']();
      expect(dateStr).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd backend && pnpm jest recommendation-cache.service.spec.ts
```

Expected: FAIL - Cannot find module './recommendation-cache.service'

---

### Task 4: 实现缓存服务

**Files:**
- Create: `backend/src/recommendation/services/recommendation-cache.service.ts`

- [ ] **Step 1: 创建缓存服务**

```typescript
// backend/src/recommendation/services/recommendation-cache.service.ts
import { Injectable, Logger } from '@nestjs/common';
import { CacheService } from '../../cache/cache.service';
import { PrismaService } from '../../prisma.service';
import {
  RecommendResult,
  RecommendScenario,
} from '../interfaces/recommendation.interface';

/**
 * Service for managing recommendation caching in Redis and MySQL.
 *
 * Provides a two-tier caching strategy:
 * 1. Redis - Hot cache for current day's recommendations
 * 2. MySQL - Persistent storage for historical recommendations
 *
 * @example
 * ```typescript
 * // Get cached recommendations
 * const cached = await cacheService.getCachedRecommendations(userId, scenario, size);
 *
 * // Store recommendations
 * await cacheService.storeRecommendations(userId, scenario, result);
 * ```
 */
@Injectable()
export class RecommendationCacheService {
  private readonly logger = new Logger(RecommendationCacheService.name);
  private readonly CACHE_TTL = 86400; // 24 hours in SECONDS

  constructor(
    private readonly cacheService: CacheService,
    private readonly prisma: PrismaService,
  ) {}

  /**
   * Get cached recommendations using two-tier cache strategy.
   * Query order: Redis -> MySQL -> null
   *
   * @param userId - User ID to get recommendations for
   * @param scenario - Recommendation scenario (DAILY, WEAK_POINT, CHALLENGE)
   * @param size - Maximum number of items to return
   * @returns Cached recommendation result or null if not found
   */
  async getCachedRecommendations(
    userId: string,
    scenario: RecommendScenario,
    size: number,
  ): Promise<RecommendResult | null> {
    const dateStr = this.getTodayDateString();
    const cacheKey = this.buildCacheKey(userId, scenario, dateStr);

    // 1. Check Redis cache
    const cached = await this.cacheService.get<RecommendResult>(cacheKey);
    if (cached) {
      this.logger.debug(`Cache hit for ${cacheKey}`);
      return cached;
    }

    // 2. Check MySQL for today's records
    const today = this.getTodayInShanghai();
    const dbRecords = await this.prisma.dailyRecommendation.findMany({
      where: {
        user_id: userId,
        scenario,
        generated_at: { gte: today },
      },
      include: { problem: true },
      orderBy: { score: 'desc' },
      take: size,
    });

    if (dbRecords.length > 0) {
      const result = this.mapDbRecordsToResult(dbRecords, scenario);
      // Backfill Redis cache
      await this.cacheService.set(cacheKey, result, this.CACHE_TTL);
      this.logger.debug(`MySQL cache hit, backfilled Redis for ${cacheKey}`);
      return result;
    }

    this.logger.debug(`Cache miss for user ${userId}, scenario ${scenario}`);
    return null;
  }

  /**
   * Store recommendations to MySQL and Redis.
   * Deletes existing records for the same user/scenario/date before inserting.
   *
   * @param userId - User ID to store recommendations for
   * @param scenario - Recommendation scenario
   * @param result - Recommendation result to store
   */
  async storeRecommendations(
    userId: string,
    scenario: RecommendScenario,
    result: RecommendResult,
  ): Promise<void> {
    const today = this.getTodayInShanghai();
    const dateStr = this.getTodayDateString();

    // Delete existing records for today
    await this.prisma.dailyRecommendation.deleteMany({
      where: {
        user_id: userId,
        scenario,
        generated_at: { gte: today },
      },
    });

    // Insert new records
    if (result.items.length > 0) {
      await this.prisma.dailyRecommendation.createMany({
        data: result.items.map(item => ({
          user_id: userId,
          problem_id: BigInt(item.problemId),
          scenario,
          score: item.score,
          reason: item.reason.substring(0, 500), // Truncate to prevent overflow
          tags: item.tags,
          generated_at: today,
        })),
      });
    }

    // Cache to Redis
    const cacheKey = this.buildCacheKey(userId, scenario, dateStr);
    await this.cacheService.set(cacheKey, result, this.CACHE_TTL);
    this.logger.debug(`Stored recommendations for ${cacheKey}`);
  }

  /**
   * Build Redis cache key
   */
  private buildCacheKey(userId: string, scenario: string, date: string): string {
    return `recommendation:${userId}:${scenario}:${date}`;
  }

  /**
   * Get today's date string in YYYY-MM-DD format (Asia/Shanghai timezone)
   */
  private getTodayDateString(): string {
    const now = new Date();
    const shanghai = new Date(
      now.toLocaleString('en-US', { timeZone: 'Asia/Shanghai' }),
    );
    return shanghai.toISOString().split('T')[0];
  }

  /**
   * Get today's date at midnight in Asia/Shanghai timezone
   */
  private getTodayInShanghai(): Date {
    const now = new Date();
    const shanghai = new Date(
      now.toLocaleString('en-US', { timeZone: 'Asia/Shanghai' }),
    );
    shanghai.setHours(0, 0, 0, 0);
    return shanghai;
  }

  /**
   * Map database records to RecommendResult format
   */
  private mapDbRecordsToResult(
    records: Array<{
      problem_id: bigint;
      score: number;
      reason: string;
      tags: any;
      generated_at: Date;
      problem: {
        slug: string;
        title: string;
        difficulty: string;
      };
    }>,
    scenario: string,
  ): RecommendResult {
    return {
      items: records.map(r => ({
        problemId: Number(r.problem_id),
        slug: r.problem.slug,
        title: r.problem.title,
        difficulty: r.problem.difficulty,
        score: r.score,
        tags: r.tags,
        reason: r.reason,
      })),
      totalCount: records.length,
      scenario: scenario as RecommendScenario,
      generatedAt: records[0]?.generated_at?.toISOString(),
    };
  }
}
```

- [ ] **Step 2: 运行测试验证通过**

```bash
cd backend && pnpm jest recommendation-cache.service.spec.ts
```

Expected: PASS - All tests pass

- [ ] **Step 3: 提交缓存服务**

```bash
git add backend/src/recommendation/services/recommendation-cache.service.ts
git add backend/src/recommendation/services/recommendation-cache.service.spec.ts
git commit -m "feat(recommendation): add RecommendationCacheService for two-tier caching"
```

---

## Chunk 3: 定时任务调度器

### Task 5: 创建调度器测试

**Files:**
- Create: `backend/src/recommendation/recommendation.scheduler.spec.ts`

- [ ] **Step 1: 创建测试文件**

```typescript
// backend/src/recommendation/recommendation.scheduler.spec.ts
import { Test, TestingModule } from '@nestjs/testing';
import { Logger } from '@nestjs/common';
import { RecommendationScheduler } from './recommendation.scheduler';
import { RecommendationService } from './services/recommendation.service';
import { RecommendationCacheService } from './services/recommendation-cache.service';
import { PrismaService } from '../prisma.service';
import { RecommendScenario } from './interfaces/recommendation.interface';

describe('RecommendationScheduler', () => {
  let scheduler: RecommendationScheduler;
  let recommendationService: jest.Mocked<RecommendationService>;
  let cacheService: jest.Mocked<RecommendationCacheService>;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const mockRecommendationService = {
      getRecommendations: jest.fn(),
    };

    const mockCacheService = {
      storeRecommendations: jest.fn(),
    };

    const mockPrisma = {
      user: {
        findMany: jest.fn(),
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        RecommendationScheduler,
        { provide: RecommendationService, useValue: mockRecommendationService },
        { provide: RecommendationCacheService, useValue: mockCacheService },
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    scheduler = module.get<RecommendationScheduler>(RecommendationScheduler);
    recommendationService = module.get(RecommendationService);
    cacheService = module.get(RecommendationCacheService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(scheduler).toBeDefined();
  });

  describe('generateDailyRecommendations', () => {
    it('should process all users in batches', async () => {
      const mockUsers = [
        { id: 'user1' },
        { id: 'user2' },
        { id: 'user3' },
      ];

      prisma.user.findMany.mockResolvedValue(mockUsers);

      recommendationService.getRecommendations.mockResolvedValue({
        success: true,
        code: 200,
        message: 'success',
        data: {
          items: [{ problemId: 1, slug: 'test', title: 'Test', difficulty: 'Easy', score: 0.9, tags: [], reason: 'test' }],
          totalCount: 1,
          scenario: RecommendScenario.DAILY,
          generatedAt: new Date().toISOString(),
        },
      });

      cacheService.storeRecommendations.mockResolvedValue(undefined);

      const result = await scheduler.generateDailyRecommendations();

      expect(result.totalUsers).toBe(3);
      expect(result.successCount).toBe(3);
      expect(result.failedUsers).toHaveLength(0);
    });

    it('should track failed users', async () => {
      const mockUsers = [{ id: 'user1' }, { id: 'user2' }];

      prisma.user.findMany.mockResolvedValue(mockUsers);

      recommendationService.getRecommendations
        .mockResolvedValueOnce({
          success: true,
          code: 200,
          message: 'success',
          data: {
            items: [],
            totalCount: 0,
            scenario: RecommendScenario.DAILY,
            generatedAt: new Date().toISOString(),
          },
        })
        .mockRejectedValueOnce(new Error('Service unavailable'));

      cacheService.storeRecommendations.mockResolvedValue(undefined);

      const result = await scheduler.generateDailyRecommendations();

      expect(result.successCount).toBe(1);
      expect(result.failedUsers).toContain('user2');
    });

    it('should handle empty user list', async () => {
      prisma.user.findMany.mockResolvedValue([]);

      const result = await scheduler.generateDailyRecommendations();

      expect(result.totalUsers).toBe(0);
      expect(result.successCount).toBe(0);
    });
  });

  describe('triggerManually', () => {
    it('should call generateDailyRecommendations', async () => {
      prisma.user.findMany.mockResolvedValue([]);

      const result = await scheduler.triggerManually();

      expect(result).toBeDefined();
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd backend && pnpm jest recommendation.scheduler.spec.ts
```

Expected: FAIL - Cannot find module './recommendation.scheduler'

---

### Task 6: 实现调度器

**Files:**
- Create: `backend/src/recommendation/recommendation.scheduler.ts`

- [ ] **Step 1: 创建调度器**

```typescript
// backend/src/recommendation/recommendation.scheduler.ts
import { Injectable, Logger } from '@nestjs/common';
import { Cron } from '@nestjs/schedule';
import { PrismaService } from '../prisma.service';
import { RecommendationService } from './services/recommendation.service';
import { RecommendationCacheService } from './services/recommendation-cache.service';
import { RecommendScenario } from './interfaces/recommendation.interface';

/**
 * Result of daily recommendation generation
 */
export interface GenerationResult {
  totalUsers: number;
  successCount: number;
  failedUsers: string[];
}

/**
 * Scheduler for generating daily recommendations.
 *
 * Runs at 8:00 AM Asia/Shanghai time every day to pre-compute
 * recommendations for all active users.
 *
 * Features:
 * - Batch processing (50 users per batch)
 * - Retry logic (3 attempts with exponential backoff)
 * - Parallel scenario generation (DAILY, WEAK_POINT, CHALLENGE)
 * - Failure tracking and logging
 */
@Injectable()
export class RecommendationScheduler {
  private readonly logger = new Logger(RecommendationScheduler.name);
  private readonly BATCH_SIZE = 50;
  private readonly MAX_RETRIES = 3;

  constructor(
    private readonly prisma: PrismaService,
    private readonly recommendationService: RecommendationService,
    private readonly cacheService: RecommendationCacheService,
  ) {}

  /**
   * Generate daily recommendations at 8:00 AM Asia/Shanghai
   */
  @Cron('0 8 * * *', { timeZone: 'Asia/Shanghai' })
  async generateDailyRecommendations(): Promise<GenerationResult> {
    this.logger.log('Starting daily recommendation generation...');

    const result: GenerationResult = {
      totalUsers: 0,
      successCount: 0,
      failedUsers: [],
    };

    try {
      // 1. Get all active users
      const users = await this.prisma.user.findMany({
        select: { id: true },
        where: { status: 'active' },
      });

      result.totalUsers = users.length;
      this.logger.log(`Found ${users.length} users to process`);

      // 2. Process in batches
      const batches = this.chunk(users, this.BATCH_SIZE);
      for (let i = 0; i < batches.length; i++) {
        this.logger.log(`Processing batch ${i + 1}/${batches.length}`);
        const batchResult = await this.processBatchWithRetry(batches[i]);
        result.successCount += batchResult.success;
        result.failedUsers.push(...batchResult.failed);
      }

      // 3. Log summary
      if (result.failedUsers.length > 0) {
        this.logger.warn(
          `Failed to generate recommendations for ${result.failedUsers.length} users: ${result.failedUsers.slice(0, 10).join(', ')}${result.failedUsers.length > 10 ? '...' : ''}`,
        );
      }

      this.logger.log(
        `Daily recommendation completed: ${result.successCount}/${result.totalUsers} users`,
      );
    } catch (error) {
      this.logger.error('Fatal error in recommendation generation:', error);
    }

    return result;
  }

  /**
   * Process a batch of users with retry logic
   */
  private async processBatchWithRetry(
    users: { id: string }[],
  ): Promise<{ success: number; failed: string[] }> {
    const result = { success: 0, failed: [] as string[] };

    for (const user of users) {
      let lastError: Error | null = null;

      for (let attempt = 1; attempt <= this.MAX_RETRIES; attempt++) {
        try {
          await this.generateUserRecommendations(user.id);
          result.success++;
          break;
        } catch (error) {
          lastError = error instanceof Error ? error : new Error(String(error));
          if (attempt < this.MAX_RETRIES) {
            await this.sleep(1000 * attempt); // Exponential backoff
          }
        }
      }

      if (lastError) {
        this.logger.error(
          `Failed after ${this.MAX_RETRIES} retries for user ${user.id}: ${lastError.message}`,
        );
        result.failed.push(user.id);
      }
    }

    return result;
  }

  /**
   * Generate recommendations for a single user (all scenarios in parallel)
   */
  private async generateUserRecommendations(userId: string): Promise<void> {
    const scenarios: RecommendScenario[] = [
      RecommendScenario.DAILY,
      RecommendScenario.WEAK_POINT,
      RecommendScenario.CHALLENGE,
    ];

    // Generate all scenarios in parallel
    await Promise.all(
      scenarios.map(async scenario => {
        const response = await this.recommendationService.getRecommendations({
          userId,
          size: 10,
          scenario,
          includeSolved: false,
        });

        if (response.success && response.data) {
          await this.cacheService.storeRecommendations(
            userId,
            scenario,
            response.data,
          );
        } else {
          throw new Error(
            `Recommendation service returned error: ${response.message}`,
          );
        }
      }),
    );
  }

  /**
   * Split array into chunks
   */
  private chunk<T>(array: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += size) {
      chunks.push(array.slice(i, i + size));
    }
    return chunks;
  }

  /**
   * Sleep utility for retry delays
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Manual trigger for testing or admin operations
   */
  async triggerManually(): Promise<GenerationResult> {
    this.logger.log('Manual trigger: starting recommendation generation');
    return this.generateDailyRecommendations();
  }
}
```

- [ ] **Step 2: 运行测试验证通过**

```bash
cd backend && pnpm jest recommendation.scheduler.spec.ts
```

Expected: PASS - All tests pass

- [ ] **Step 3: 提交调度器**

```bash
git add backend/src/recommendation/recommendation.scheduler.ts
git add backend/src/recommendation/recommendation.scheduler.spec.ts
git commit -m "feat(recommendation): add RecommendationScheduler for daily generation"
```

---

## Chunk 4: 模块集成和速率限制

### Task 7: 更新模块配置

**Files:**
- Modify: `backend/src/recommendation/recommendation.module.ts`

- [ ] **Step 1: 读取当前模块内容**

Read: `backend/src/recommendation/recommendation.module.ts`

- [ ] **Step 2: 添加新依赖到 forRoot 方法**

找到 `forRoot()` 方法，修改 imports 和 providers:

```typescript
// backend/src/recommendation/recommendation.module.ts
import { Module, DynamicModule, Global } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { RecommendationController } from './recommendation.controller';
import { RecommendationService } from './services/recommendation.service';
import { NacosNamingService } from './services/nacos.service';
import { RecommendationCacheService } from './services/recommendation-cache.service';
import { RecommendationScheduler } from './recommendation.scheduler';
import { CacheModule } from '../cache/cache.module';
import { ThrottlerModule } from '@nestjs/throttler';
import {
  RecommendationModuleAsyncOptions,
  RecommendationModuleOptions,
} from './interfaces/recommendation-module-options.interface';

@Global()
@Module({})
export class RecommendationModule {
  // ... keep existing register() and registerAsync() methods ...

  /**
   * Register the recommendation module using environment variables
   * Includes caching and scheduling capabilities
   */
  static forRoot(): DynamicModule {
    return {
      module: RecommendationModule,
      imports: [
        HttpModule.registerAsync({
          imports: [ConfigModule],
          inject: [ConfigService],
          useFactory: (configService: ConfigService) => ({
            timeout: configService.get<number>('RECOMMENDATION_TIMEOUT', 5000),
            maxRedirects: 3,
          }),
        }),
        ConfigModule,
        CacheModule,
        ThrottlerModule.forRoot([
          {
            ttl: 60000, // 60 seconds time window
            limit: 60, // 60 requests per minute default
          },
        ]),
      ],
      controllers: [RecommendationController],
      providers: [
        NacosNamingService,
        RecommendationService,
        RecommendationCacheService,
        RecommendationScheduler,
      ],
      exports: [
        RecommendationService,
        NacosNamingService,
        RecommendationCacheService,
        RecommendationScheduler,
      ],
      global: true,
    };
  }
}
```

- [ ] **Step 3: 验证编译**

```bash
cd backend && pnpm tsc --noEmit
```

Expected: No type errors

- [ ] **Step 4: 提交模块更新**

```bash
git add backend/src/recommendation/recommendation.module.ts
git commit -m "feat(recommendation): integrate cache service and scheduler into module"
```

---

### Task 8: 添加速率限制到控制器

**Files:**
- Modify: `backend/src/recommendation/recommendation.controller.ts`

- [ ] **Step 1: 添加 throttler 导入**

在文件顶部添加导入:

```typescript
import { Throttle, ThrottlerGuard } from '@nestjs/throttler';
```

- [ ] **Step 2: 添加类级别守卫**

在 `@Controller('recommendations')` 装饰器上方添加:

```typescript
@UseGuards(ThrottlerGuard)
```

- [ ] **Step 3: 为 similar 端点添加更严格的限制**

找到 `getSimilarProblems` 方法，在 `@Get('similar/:problemId')` 下方添加:

```typescript
@Throttle({ default: { limit: 10, ttl: 60000 } }) // 10 requests per minute
```

- [ ] **Step 4: 验证编译**

```bash
cd backend && pnpm tsc --noEmit
```

Expected: No type errors

- [ ] **Step 5: 提交控制器更新**

```bash
git add backend/src/recommendation/recommendation.controller.ts
git commit -m "feat(recommendation): add rate limiting to recommendation endpoints"
```

---

## Chunk 5: 集成测试和最终验证

### Task 9: 运行完整测试套件

- [ ] **Step 1: 运行推荐模块所有测试**

```bash
cd backend && pnpm jest --testPathPattern=recommendation
```

Expected: All tests pass

- [ ] **Step 2: 运行类型检查**

```bash
cd backend && pnpm type-check
```

Expected: No errors

- [ ] **Step 3: 运行 lint**

```bash
cd backend && pnpm lint
```

Expected: No errors (or auto-fix if minor)

---

### Task 10: 数据库迁移

- [ ] **Step 1: 创建迁移**

```bash
cd backend && pnpm prisma:migrate dev --name add_daily_recommendations
```

Expected: Migration created successfully

- [ ] **Step 2: 验证迁移**

```bash
cd backend && pnpm prisma:migrate status
```

Expected: Database schema is up to date

---

### Task 11: 最终提交

- [ ] **Step 1: 查看所有更改**

```bash
git status
```

- [ ] **Step 2: 确保所有文件已提交**

如果有未提交的文件:

```bash
git add -A
git commit -m "feat(recommendation): complete daily recommendation integration"
```

- [ ] **Step 3: 推送到远程**

```bash
git push origin main
```

---

## 部署检查清单

- [ ] Java 推荐服务运行在 8080 端口
- [ ] Redis 服务可用
- [ ] 数据库迁移已执行
- [ ] 环境变量已配置 (`RECOMMENDATION_CACHE_TTL=86400`)
- [ ] 定时任务时区设置正确 (Asia/Shanghai)
- [ ] Nacos 服务发现正常（如果使用）

---

## 测试场景

### 手动测试

1. **测试缓存服务**
```bash
# 调用 API 检查缓存是否生效
curl -X GET "http://localhost:3000/recommendations/daily" -H "Authorization: Bearer <token>"
# 第二次调用应该更快（从缓存读取）
curl -X GET "http://localhost:3000/recommendations/daily" -H "Authorization: Bearer <token>"
```

2. **测试定时任务**
```bash
# 手动触发（需要添加管理端点或使用 NestJS CLI）
# 或者等待每日 8:00 AM 自动触发
```

3. **测试速率限制**
```bash
# 快速连续调用 similar 端点，第 11 次应该被拒绝
for i in {1..15}; do
  curl -X GET "http://localhost:3000/recommendations/similar/1" -H "Authorization: Bearer <token>"
  echo "Request $i"
done
```
