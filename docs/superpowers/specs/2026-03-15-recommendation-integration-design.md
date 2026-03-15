# 推荐系统集成设计文档

> 日期: 2026-03-15
> 状态: Draft (Revised)
> 作者: Claude Code

## 1. 概述

### 1.1 目标

将现有 Java 推荐服务集成到 NestJS 后端，实现每日 8 点自动为所有用户生成推荐信息，并提供 API 供前端消费。

### 1.2 背景

- 现有 Java 推荐服务 (`/recommendation`) 已实现完整的推荐算法
- **现有后端模块**: `/backend/src/recommendation/` 已有完整的 API 实现
  - `RecommendationService`: 使用 `HttpService` + Nacos 服务发现
  - `RecommendationController`: 已实现 4 种推荐场景的端点
- 前端已定义推荐 API 接口、Store 和类型
- 后端已有调度任务基础设施 (NestJS Schedule + BullMQ)

### 1.3 关键决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 集成方式 | 扩展现有模块 | 保留现有实现，添加缓存和定时任务 |
| 生成策略 | 预计算 + 存储 | 提升响应速度，支持历史查看 |
| 存储方式 | MySQL + Redis | MySQL 存历史，Redis 缓存热数据 |
| 用户范围 | 所有用户 | 覆盖全面，避免遗漏 |
| 场景支持 | 4 种全支持 | 满足不同学习需求 |

## 2. 架构设计

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         每日8点定时任务                              │
│                     (NestJS Scheduler)                              │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    RecommendationScheduler                          │
│  1. 查询所有用户                                                      │
│  2. 分批调用现有 RecommendationService                                │
│  3. 存储结果到 MySQL + Redis                                         │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│     MySQL     │   │     Redis     │   │  Java 服务    │
│ (历史记录)    │   │ (当天缓存)    │   │ (推荐算法)    │
│  Port: 3306   │   │  Port: 6379   │   │  Port: 8080   │
└───────────────┘   └───────────────┘   └───────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        前端请求流程                                  │
│  1. 先查 Redis 缓存                                                  │
│  2. 缓存 miss → 查 MySQL 当天记录                                    │
│  3. 都没有 → 实时调用 Java 服务（降级）                               │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流

```
[定时任务 8:00] → [分批获取用户] → [调用现有 Service] → [存储 MySQL + Redis]

[前端请求] → [查 Redis] → hit → 返回
                  ↓ miss
            [查 MySQL 当天] → hit → 返回
                  ↓ miss
            [实时调用 Java] → [存储结果] → 返回
```

## 3. 数据模型

### 3.1 Prisma Schema

```prisma
// 每日推荐记录
model DailyRecommendation {
  id            String   @id @default(uuid()) @db.VarChar(40)
  user_id       String   @db.VarChar(40)
  problem_id    BigInt   // 注意：Problem.id 是 BigInt
  scenario      String   @db.VarChar(20)  // DAILY, WEAK_POINT, CHALLENGE
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

**重要**: `problem_id` 必须使用 `BigInt` 类型，因为现有 `Problem` 模型的 `id` 是 `BigInt`。

### 3.2 Redis 缓存结构

```
Key: recommendation:{user_id}:{scenario}:{date}
Value: JSON.stringify(RecommendResult)
TTL: 86400 秒 (24 hours)

Example:
recommendation:user123:DAILY:2026-03-15 → {"items":[...],"totalCount":10}
```

**注意**: CacheService 的 TTL 单位是**秒**，不是毫秒。

## 4. 后端实现

### 4.1 目录结构 (扩展现有模块)

```
backend/src/recommendation/
├── recommendation.module.ts       # 现有 - 添加新依赖
├── recommendation.controller.ts   # 现有 - 添加速率限制
├── services/
│   ├── recommendation.service.ts  # 现有 - 扩展缓存逻辑
│   ├── recommendation-cache.service.ts  # 新增 - 缓存管理
│   └── nacos.service.ts           # 现有 - 保持不变
├── recommendation.scheduler.ts    # 新增 - 定时任务
├── dto/
│   └── recommend.dto.ts           # 现有 - 保持不变
└── interfaces/
    └── recommendation.interface.ts # 现有 - 保持不变
```

### 4.2 核心接口

#### 新增: 缓存服务

```typescript
// services/recommendation-cache.service.ts
import { Injectable, Logger } from '@nestjs/common';
import { CacheService } from '../../cache/cache.service';
import { PrismaService } from '../../prisma.service';
import {
  RecommendResult,
  RecommendScenario,
} from '../interfaces/recommendation.interface';

@Injectable()
export class RecommendationCacheService {
  private readonly logger = new Logger(RecommendationCacheService.name);
  private readonly CACHE_TTL = 86400; // 24 hours in SECONDS

  constructor(
    private readonly cacheService: CacheService,
    private readonly prisma: PrismaService,
  ) {}

  /**
   * 获取缓存的推荐结果
   * 查询顺序: Redis -> MySQL -> null
   */
  async getCachedRecommendations(
    userId: string,
    scenario: RecommendScenario,
    size: number,
  ): Promise<RecommendResult | null> {
    const dateStr = this.getTodayDateString();
    const cacheKey = this.buildCacheKey(userId, scenario, dateStr);

    // 1. 查 Redis
    const cached = await this.cacheService.get<RecommendResult>(cacheKey);
    if (cached) {
      this.logger.debug(`Cache hit for ${cacheKey}`);
      return cached;
    }

    // 2. 查 MySQL
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
      // 回填 Redis
      await this.cacheService.set(cacheKey, result, this.CACHE_TTL);
      return result;
    }

    return null;
  }

  /**
   * 存储推荐结果到 MySQL 和 Redis
   */
  async storeRecommendations(
    userId: string,
    scenario: RecommendScenario,
    result: RecommendResult,
  ): Promise<void> {
    const today = this.getTodayInShanghai();
    const dateStr = this.getTodayDateString();

    // 删除当天旧记录
    await this.prisma.dailyRecommendation.deleteMany({
      where: {
        user_id: userId,
        scenario,
        generated_at: { gte: today },
      },
    });

    // 插入新记录
    if (result.items.length > 0) {
      await this.prisma.dailyRecommendation.createMany({
        data: result.items.map(item => ({
          user_id: userId,
          problem_id: BigInt(item.problemId),
          scenario,
          score: item.score,
          reason: item.reason.substring(0, 500), // 限制长度
          tags: item.tags,
          generated_at: today,
        })),
      });
    }

    // 缓存到 Redis
    const cacheKey = this.buildCacheKey(userId, scenario, dateStr);
    await this.cacheService.set(cacheKey, result, this.CACHE_TTL);
  }

  private buildCacheKey(userId: string, scenario: string, date: string): string {
    return `recommendation:${userId}:${scenario}:${date}`;
  }

  private getTodayDateString(): string {
    const now = new Date();
    // 使用 Asia/Shanghai 时区
    const shanghai = new Date(
      now.toLocaleString('en-US', { timeZone: 'Asia/Shanghai' }),
    );
    return shanghai.toISOString().split('T')[0];
  }

  private getTodayInShanghai(): Date {
    const now = new Date();
    const shanghai = new Date(
      now.toLocaleString('en-US', { timeZone: 'Asia/Shanghai' }),
    );
    shanghai.setHours(0, 0, 0, 0);
    return shanghai;
  }

  private mapDbRecordsToResult(
    records: any[],
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

#### 新增: 定时任务

```typescript
// recommendation.scheduler.ts
import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { PrismaService } from '../prisma.service';
import { RecommendationService } from './services/recommendation.service';
import { RecommendationCacheService } from './services/recommendation-cache.service';
import { RecommendScenario } from './interfaces/recommendation.interface';

interface GenerationResult {
  totalUsers: number;
  successCount: number;
  failedUsers: string[];
}

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
   * 每日 8:00 AM (Asia/Shanghai) 生成推荐
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
      // 1. 获取所有活跃用户
      const users = await this.prisma.user.findMany({
        select: { id: true },
        where: { status: 'active' },
      });

      result.totalUsers = users.length;
      this.logger.log(`Found ${users.length} users to process`);

      // 2. 分批处理
      const batches = this.chunk(users, this.BATCH_SIZE);
      for (let i = 0; i < batches.length; i++) {
        this.logger.log(`Processing batch ${i + 1}/${batches.length}`);
        const batchResult = await this.processBatchWithRetry(batches[i]);
        result.successCount += batchResult.success;
        result.failedUsers.push(...batchResult.failed);
      }

      // 3. 记录失败用户（可配置告警）
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
            await this.sleep(1000 * attempt); // 递增延迟
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

  private async generateUserRecommendations(userId: string): Promise<void> {
    const scenarios: RecommendScenario[] = [
      RecommendScenario.DAILY,
      RecommendScenario.WEAK_POINT,
      RecommendScenario.CHALLENGE,
    ];

    // 并行生成三种场景的推荐
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

  private chunk<T>(array: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += size) {
      chunks.push(array.slice(i, i + size));
    }
    return chunks;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * 手动触发（用于测试或管理员操作）
   */
  async triggerManually(): Promise<GenerationResult> {
    return this.generateDailyRecommendations();
  }
}
```

#### 修改: 扩展现有 RecommendationService

```typescript
// services/recommendation.service.ts - 扩展方法
// 在现有类中添加以下方法

@Injectable()
export class RecommendationService {
  // ... 现有代码保持不变 ...

  constructor(
    // ... 现有依赖 ...
    private readonly cacheService: RecommendationCacheService, // 新增
  ) {}

  /**
   * 获取带缓存的每日推荐
   */
  async getDailyRecommendationsWithCache(
    userId: string,
    size = 10,
    includeSolved = false,
  ): Promise<RecommendResponse<RecommendResult>> {
    // 1. 尝试获取缓存
    const cached = await this.cacheService.getCachedRecommendations(
      userId,
      RecommendScenario.DAILY,
      size,
    );

    if (cached) {
      return { success: true, code: 200, message: 'success', data: cached };
    }

    // 2. 实时调用
    const response = await this.getDailyRecommendations(
      userId,
      size,
      includeSolved,
    );

    // 3. 缓存结果
    if (response.success && response.data) {
      await this.cacheService.storeRecommendations(
        userId,
        RecommendScenario.DAILY,
        response.data,
      );
    }

    return response;
  }

  // 类似地为其他场景添加缓存版本...
}
```

#### 修改: 控制器添加速率限制

```typescript
// recommendation.controller.ts - 添加速率限制
import { Throttle, ThrottlerGuard } from '@nestjs/throttler';
import { UseGuards } from '@nestjs/common';

@ApiTags('recommendations')
@Controller('recommendations')
@UseGuards(ThrottlerGuard)
export class RecommendationController {
  // ... 现有代码 ...

  /**
   * 相似题目接口 - 限制更严格（实时调用 Java 服务）
   */
  @Get('similar/:problemId')
  @Throttle({ default: { limit: 10, ttl: 60000 } }) // 每分钟 10 次
  @UseGuards(OptionalJwtAuthGuard)
  // ... 其他装饰器 ...
  async getSimilarProblems(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Req() req: RequestWithOptionalUser,
    @Query('size') size?: number,
  ): Promise<RecommendResponseDto> {
    // ... 现有实现 ...
  }

  // 其他端点使用默认限制
}
```

## 5. 前端集成

### 5.1 Store (保持现有结构)

现有的 `/console/src/stores/recommendation.ts` 已经实现完整，无需修改：

```typescript
// console/src/stores/recommendation.ts - 现有实现保持不变
export const useRecommendationStore = defineStore("recommendation", {
  state: () => ({
    daily: [] as RecommendItem[],      // 保持数组结构
    weakPoints: [] as RecommendItem[],
    challenge: [] as RecommendItem[],
    similar: [] as RecommendItem[],
    loading: false,
    error: null as string | null,
  }),
  // ... actions 保持不变 ...
});
```

### 5.2 使用示例

```vue
<!-- 首页每日推荐组件 -->
<script setup lang="ts">
import { onMounted } from 'vue';
import { useRecommendationStore } from '@/stores/recommendation';

const recommendationStore = useRecommendationStore();

onMounted(() => {
  recommendationStore.loadDaily();
});
</script>

<template>
  <div class="daily-recommendations">
    <h2>每日推荐</h2>
    <div v-if="recommendationStore.loading" class="loading">
      加载中...
    </div>
    <div v-else class="recommendation-list">
      <ProblemCard
        v-for="item in recommendationStore.daily"
        :key="item.problemId"
        :problem="item"
      />
    </div>
  </div>
</template>
```

## 6. 配置

### 6.1 环境变量

```bash
# backend/.env
# 现有配置保持不变
RECOMMENDATION_TIMEOUT=5000

# 新增: 缓存配置
RECOMMENDATION_CACHE_TTL=86400  # 24 hours in SECONDS
```

### 6.2 模块更新

```typescript
// recommendation/recommendation.module.ts
import { RecommendationCacheService } from './services/recommendation-cache.service';
import { RecommendationScheduler } from './recommendation.scheduler';
import { CacheModule } from '../cache/cache.module';

@Module({
  imports: [
    // ... 现有 imports
    CacheModule,
  ],
  controllers: [RecommendationController],
  providers: [
    RecommendationService,
    NacosNamingService,
    RecommendationCacheService, // 新增
    RecommendationScheduler,    // 新增
  ],
  exports: [RecommendationService],
})
export class RecommendationModule {}
```

## 7. 错误处理

### 7.1 降级策略

| 场景 | 降级方案 |
|------|----------|
| Java 服务不可用 | 返回热门题目列表（需要实现热门题目查询） |
| Redis 不可用 | 直接查 MySQL |
| MySQL 不可用 | 实时调用 Java 服务（无缓存） |
| 全部不可用 | 返回 503 错误，前端显示友好提示 |

### 7.2 日志记录

```typescript
// 所有关键操作记录日志
this.logger.log(`Starting daily recommendation generation`);
this.logger.debug(`Cache hit for user ${userId}`);
this.logger.warn(`Cache miss, falling back to Java service`);
this.logger.error(`Failed to generate recommendations: ${error.message}`);
```

### 7.3 监控告警

- 失败率超过 10% 时记录警告日志
- 失败率超过 50% 时记录错误日志
- 可配置外部告警（邮件、Slack 等）

## 8. 安全考虑

### 8.1 输入验证

```typescript
// 验证 Java 服务响应
private validateRecommendationResponse(data: unknown): RecommendResult | null {
  if (!data || typeof data !== 'object') return null;

  const result = data as Record<string, unknown>;
  if (!Array.isArray(result.items)) return null;

  // 验证每个 item
  for (const item of result.items) {
    if (typeof item.problemId !== 'number') return null;
    if (typeof item.score !== 'number') return null;
    // 限制 reason 长度
    if (typeof item.reason === 'string' && item.reason.length > 500) {
      item.reason = item.reason.substring(0, 500);
    }
  }

  return result as RecommendResult;
}
```

### 8.2 速率限制

| 端点 | 限制 | 理由 |
|------|------|------|
| `/recommendations/similar/:id` | 10/分钟 | 实时调用 Java 服务 |
| `/recommendations/*` | 60/分钟 | 默认限制 |

## 9. 测试计划

### 9.1 单元测试

- [ ] RecommendationCacheService - 缓存逻辑和数据转换
- [ ] RecommendationScheduler - 定时任务和批处理
- [ ] 时区处理 - 确保 Asia/Shanghai 一致性

### 9.2 集成测试

- [ ] API 端点响应正确（带缓存和不带缓存）
- [ ] 缓存命中/未命中场景
- [ ] Java 服务调用失败降级
- [ ] 速率限制生效

### 9.3 E2E 测试

- [ ] 前端首页加载每日推荐
- [ ] 题目详情页显示相似题目
- [ ] 薄弱点页面正常工作

## 10. 部署检查清单

- [ ] Java 推荐服务运行在 8080 端口
- [ ] Redis 服务可用
- [ ] 数据库迁移已执行: `pnpm prisma:migrate dev --name add_daily_recommendations`
- [ ] 环境变量已配置
- [ ] 定时任务时区设置正确 (Asia/Shanghai)
- [ ] Nacos 服务发现正常（如果使用）

## 11. 数据库迁移

```bash
cd backend
pnpm prisma:migrate dev --name add_daily_recommendations
```

迁移文件会创建 `daily_recommendations` 表，包含：
- `id`: UUID 主键
- `user_id`: 外键关联 User
- `problem_id`: BigInt 外键关联 Problem
- `scenario`: 推荐场景
- `score`: 推荐分数
- `reason`: 推荐理由
- `tags`: 标签 JSON
- `generated_at`: 生成日期（用于区分每天记录）
- `created_at`: 创建时间

## 12. 未来优化

1. **增量更新**: 只为有新提交的用户重新生成推荐
2. **个性化参数**: 允许用户自定义推荐偏好
3. **A/B 测试**: 支持不同推荐策略的效果对比
4. **监控告警**: 推荐生成失败时发送通知
5. **冷启动优化**: 新用户首次登录时按需生成
