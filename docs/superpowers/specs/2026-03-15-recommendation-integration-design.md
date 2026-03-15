# 推荐系统集成设计文档

> 日期: 2026-03-15
> 状态: Draft
> 作者: Claude Code

## 1. 概述

### 1.1 目标

将现有 Java 推荐服务集成到 NestJS 后端，实现每日 8 点自动为所有用户生成推荐信息，并提供 API 供前端消费。

### 1.2 背景

- 现有 Java 推荐服务 (`/recommendation`) 已实现完整的推荐算法
- 前端已定义推荐 API 接口和类型
- 后端已有调度任务基础设施 (NestJS Schedule + BullMQ)

### 1.3 关键决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 集成方式 | 调用 Java 服务 | 复用已有算法，避免重复实现 |
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
│  2. 分批调用 Java 推荐服务                                            │
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
[定时任务 8:00] → [分批获取用户] → [调用 Java 服务] → [存储 MySQL + Redis]

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
  problem_id    Int
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

### 3.2 Redis 缓存结构

```
Key: recommendation:{user_id}:{scenario}:{date}
Value: JSON.stringify(RecommendResult)
TTL: 24 hours

Example:
recommendation:user123:DAILY:2026-03-15 → {"items":[...],"totalCount":10}
```

## 4. 后端实现

### 4.1 目录结构

```
backend/src/recommendation/
├── recommendation.module.ts       # 模块定义
├── recommendation.controller.ts   # API 端点
├── recommendation.service.ts      # 核心业务逻辑
├── recommendation.scheduler.ts    # 定时任务 (每日8点)
├── java-recommend.client.ts       # Java 服务调用客户端
├── dto/
│   ├── recommend-request.dto.ts
│   └── recommend-response.dto.ts
└── interfaces/
    └── java-service.interface.ts
```

### 4.2 核心接口

#### Java 服务客户端

```typescript
// java-recommend.client.ts
@Injectable()
export class JavaRecommendClient {
  private readonly baseUrl = process.env.JAVA_RECOMMEND_URL || 'http://localhost:8080';

  async getRecommendations(request: RecommendRequest): Promise<RecommendResult> {
    const response = await fetch(`${this.baseUrl}/api/recommend`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    return response.json();
  }

  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseUrl}/api/recommend/health`);
      return response.ok;
    } catch {
      return false;
    }
  }
}
```

#### 定时任务

```typescript
// recommendation.scheduler.ts
@Injectable()
export class RecommendationScheduler {
  private readonly logger = new Logger(RecommendationScheduler.name);
  private readonly BATCH_SIZE = 50;

  constructor(
    private readonly prisma: PrismaService,
    private readonly javaClient: JavaRecommendClient,
    private readonly cacheManager: Cache,
  ) {}

  @Cron('0 8 * * *', { timeZone: 'Asia/Shanghai' })
  async generateDailyRecommendations() {
    this.logger.log('Starting daily recommendation generation...');

    // 1. 获取所有用户
    const users = await this.prisma.user.findMany({
      select: { id: true },
      where: { status: 'active' },
    });

    this.logger.log(`Found ${users.length} users to process`);

    // 2. 分批处理
    const batches = this.chunk(users, this.BATCH_SIZE);
    for (let i = 0; i < batches.length; i++) {
      this.logger.log(`Processing batch ${i + 1}/${batches.length}`);
      await this.processBatch(batches[i]);
    }

    this.logger.log('Daily recommendation generation completed');
  }

  private async processBatch(users: { id: string }[]) {
    for (const user of users) {
      try {
        await this.generateUserRecommendations(user.id);
      } catch (error) {
        this.logger.error(`Failed to generate recommendations for user ${user.id}:`, error);
      }
    }
  }

  private async generateUserRecommendations(userId: string) {
    const scenarios: RecommendScenario[] = ['DAILY', 'WEAK_POINT', 'CHALLENGE'];
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    for (const scenario of scenarios) {
      const result = await this.javaClient.getRecommendations({
        userId,
        size: 10,
        scenario,
        includeSolved: false,
      });

      // 存储 MySQL
      await this.storeToMySQL(userId, scenario, result, today);

      // 缓存 Redis
      await this.cacheToRedis(userId, scenario, result, today);
    }
  }

  private async storeToMySQL(userId: string, scenario: string, result: RecommendResult, date: Date) {
    // 删除当天旧记录
    await this.prisma.dailyRecommendation.deleteMany({
      where: {
        user_id: userId,
        scenario,
        generated_at: { gte: date },
      },
    });

    // 插入新记录
    await this.prisma.dailyRecommendation.createMany({
      data: result.items.map(item => ({
        user_id: userId,
        problem_id: item.problemId,
        scenario,
        score: item.score,
        reason: item.reason,
        tags: item.tags,
        generated_at: date,
      })),
    });
  }

  private async cacheToRedis(userId: string, scenario: string, result: RecommendResult, date: Date) {
    const cacheKey = `recommendation:${userId}:${scenario}:${date.toISOString().split('T')[0]}`;
    await this.cacheManager.set(cacheKey, result, 24 * 60 * 60 * 1000); // 24 hours
  }

  private chunk<T>(array: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += size) {
      chunks.push(array.slice(i, i + size));
    }
    return chunks;
  }
}
```

#### 服务层

```typescript
// recommendation.service.ts
@Injectable()
export class RecommendationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly javaClient: JavaRecommendClient,
    private readonly cacheManager: Cache,
  ) {}

  async getDaily(userId: string, size = 10): Promise<RecommendResult> {
    return this.getRecommendations(userId, 'DAILY', size);
  }

  async getWeakPoints(userId: string, size = 10, tags?: string[]): Promise<RecommendResult> {
    // 如果指定了 tags，实时调用 Java 服务
    if (tags && tags.length > 0) {
      return this.getRealtimeRecommendations({
        userId,
        size,
        scenario: 'WEAK_POINT',
        targetTags: tags,
      });
    }
    return this.getRecommendations(userId, 'WEAK_POINT', size);
  }

  async getChallenge(userId: string, size = 5): Promise<RecommendResult> {
    return this.getRecommendations(userId, 'CHALLENGE', size);
  }

  async getSimilar(problemId: number, size = 5): Promise<RecommendResult> {
    // 相似题目始终实时计算
    return this.javaClient.getRecommendations({
      size,
      scenario: 'SIMILAR',
      sourceProblemId: problemId,
    });
  }

  private async getRecommendations(
    userId: string,
    scenario: RecommendScenario,
    size: number,
  ): Promise<RecommendResult> {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const dateStr = today.toISOString().split('T')[0];

    // 1. 查 Redis 缓存
    const cacheKey = `recommendation:${userId}:${scenario}:${dateStr}`;
    const cached = await this.cacheManager.get<RecommendResult>(cacheKey);
    if (cached) {
      return cached;
    }

    // 2. 查 MySQL 当天记录
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
      // 回填缓存
      await this.cacheManager.set(cacheKey, result, 24 * 60 * 60 * 1000);
      return result;
    }

    // 3. 实时调用 Java 服务（降级）
    return this.getRealtimeRecommendations({ userId, size, scenario });
  }

  private async getRealtimeRecommendations(request: RecommendRequest): Promise<RecommendResult> {
    const result = await this.javaClient.getRecommendations(request);

    // 存储结果（非 SIMILAR 场景）
    if (request.scenario !== 'SIMILAR' && request.userId) {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      await this.storeAndCache(request.userId, request.scenario!, result, today);
    }

    return result;
  }

  private mapDbRecordsToResult(records: any[], scenario: string): RecommendResult {
    return {
      items: records.map(r => ({
        problemId: r.problem_id,
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

#### 控制器

```typescript
// recommendation.controller.ts
@Controller('recommendations')
@UseGuards(JwtAuthGuard)
export class RecommendationController {
  constructor(private readonly recommendationService: RecommendationService) {}

  @Get('daily')
  async getDaily(
    @CurrentUser() user: JwtPayload,
    @Query('size', DefaultValuePipe(10)) size: number,
    @Query('includeSolved', DefaultValuePipe(false)) includeSolved: boolean,
  ) {
    const result = await this.recommendationService.getDaily(user.sub, size);
    return this.successResponse(result);
  }

  @Get('weak-points')
  async getWeakPoints(
    @CurrentUser() user: JwtPayload,
    @Query('size', DefaultValuePipe(10)) size: number,
    @Query('tags') tags?: string,
  ) {
    const tagList = tags ? tags.split(',') : undefined;
    const result = await this.recommendationService.getWeakPoints(user.sub, size, tagList);
    return this.successResponse(result);
  }

  @Get('challenge')
  async getChallenge(
    @CurrentUser() user: JwtPayload,
    @Query('size', DefaultValuePipe(5)) size: number,
  ) {
    const result = await this.recommendationService.getChallenge(user.sub, size);
    return this.successResponse(result);
  }

  @Get('similar/:problemId')
  async getSimilar(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Query('size', DefaultValuePipe(5)) size: number,
  ) {
    const result = await this.recommendationService.getSimilar(problemId, size);
    return this.successResponse(result);
  }

  @Get('health')
  @Public()
  async healthCheck() {
    const status = await this.recommendationService.healthCheck();
    return { status: status ? 'UP' : 'DOWN' };
  }

  private successResponse(data: any) {
    return {
      success: true,
      code: 200,
      message: 'success',
      data,
    };
  }
}
```

## 5. 前端集成

### 5.1 Store 更新

```typescript
// console/src/stores/recommendation.ts
import { defineStore } from 'pinia';
import { recommendationApi } from '@/api/recommendation';
import type { RecommendResult, RecommendScenario } from '@/types/recommendation';

export const useRecommendationStore = defineStore('recommendation', {
  state: () => ({
    daily: null as RecommendResult | null,
    weakPoints: null as RecommendResult | null,
    challenge: null as RecommendResult | null,
    similar: null as RecommendResult | null,
    loading: {
      daily: false,
      weakPoints: false,
      challenge: false,
      similar: false,
    },
    error: null as string | null,
  }),

  actions: {
    async fetchDaily(size = 10) {
      this.loading.daily = true;
      try {
        this.daily = await recommendationApi.getDaily(size);
      } catch (e) {
        this.error = '获取每日推荐失败';
      } finally {
        this.loading.daily = false;
      }
    },

    async fetchWeakPoints(size = 10, tags?: string[]) {
      this.loading.weakPoints = true;
      try {
        this.weakPoints = await recommendationApi.getWeakPoints(size, tags);
      } catch (e) {
        this.error = '获取薄弱点推荐失败';
      } finally {
        this.loading.weakPoints = false;
      }
    },

    async fetchChallenge(size = 5) {
      this.loading.challenge = true;
      try {
        this.challenge = await recommendationApi.getChallenge(size);
      } catch (e) {
        this.error = '获取挑战推荐失败';
      } finally {
        this.loading.challenge = false;
      }
    },

    async fetchSimilar(problemId: number, size = 5) {
      this.loading.similar = true;
      try {
        this.similar = await recommendationApi.getSimilar(problemId, size);
      } catch (e) {
        this.error = '获取相似题目失败';
      } finally {
        this.loading.similar = false;
      }
    },
  },
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
  recommendationStore.fetchDaily();
});
</script>

<template>
  <div class="daily-recommendations">
    <h2>每日推荐</h2>
    <div v-if="recommendationStore.loading.daily" class="loading">
      加载中...
    </div>
    <div v-else-if="recommendationStore.daily" class="recommendation-list">
      <ProblemCard
        v-for="item in recommendationStore.daily.items"
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
JAVA_RECOMMEND_URL=http://localhost:8080
RECOMMENDATION_CACHE_TTL=86400000  # 24 hours in ms
```

### 6.2 模块注册

```typescript
// backend/src/app.module.ts
import { RecommendationModule } from './recommendation/recommendation.module';

@Module({
  imports: [
    // ... existing imports
    RecommendationModule,
  ],
})
export class AppModule {}
```

## 7. 错误处理

### 7.1 降级策略

| 场景 | 降级方案 |
|------|----------|
| Java 服务不可用 | 返回热门题目列表 |
| Redis 不可用 | 直接查 MySQL |
| MySQL 不可用 | 返回错误，前端显示友好提示 |

### 7.2 日志记录

```typescript
// 所有关键操作记录日志
this.logger.log(`Generating recommendations for user ${userId}`);
this.logger.error(`Failed to call Java service: ${error.message}`);
this.logger.warn(`Cache miss for user ${userId}, falling back to DB`);
```

## 8. 测试计划

### 8.1 单元测试

- [ ] JavaRecommendClient - 服务调用和错误处理
- [ ] RecommendationService - 缓存逻辑和数据转换
- [ ] RecommendationScheduler - 定时任务执行

### 8.2 集成测试

- [ ] API 端点响应正确
- [ ] 缓存命中/未命中场景
- [ ] Java 服务调用失败降级

### 8.3 E2E 测试

- [ ] 前端首页加载每日推荐
- [ ] 题目详情页显示相似题目
- [ ] 薄弱点页面正常工作

## 9. 部署检查清单

- [ ] Java 推荐服务运行在 8080 端口
- [ ] Redis 服务可用
- [ ] 数据库迁移已执行
- [ ] 环境变量已配置
- [ ] 定时任务时区设置正确 (Asia/Shanghai)

## 10. 未来优化

1. **增量更新**: 只为有新提交的用户重新生成推荐
2. **个性化参数**: 允许用户自定义推荐偏好
3. **A/B 测试**: 支持不同推荐策略的效果对比
4. **监控告警**: 推荐生成失败时发送通知
