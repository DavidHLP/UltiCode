import { Injectable, Logger } from '@nestjs/common';
import { CacheService } from '../../cache/cache.service';
import { PrismaService } from '../../prisma.service';
import { DailyRecommendation } from '@prisma/client';
import {
  RecommendResult,
  RecommendScenario,
} from '../interfaces/recommendation.interface';

@Injectable()
export class RecommendationCacheService {
  private readonly logger = new Logger(RecommendationCacheService.name);

  constructor(
    private readonly cacheService: CacheService,
    private readonly prisma: PrismaService,
  ) {}

  /**
   * Get cached recommendations with two-layer cache strategy (Redis -> MySQL -> null)
   *
   * @param userId The user ID
   * @param scenario The recommendation scenario
   * @param size Number of recommendations to fetch (default: 10)
   * @returns cached recommendations or null if not found
   */
  async getCachedRecommendations(
    userId: string,
    scenario: RecommendScenario,
    size: number = 10,
  ): Promise<RecommendResult | null> {
    try {
      // Step 1: Try Redis cache first
      const todayString = this.getTodayDateString();
      const cacheKey = this.buildCacheKey(userId, scenario, todayString);
      const cachedResult =
        await this.cacheService.get<RecommendResult>(cacheKey);

      if (cachedResult) {
        this.logger.debug(`Cache hit for user ${userId}, scenario ${scenario}`);
        return cachedResult;
      }

      // Step 2: If Redis cache miss, try MySQL
      this.logger.debug(
        `Cache miss, querying MySQL for user ${userId}, scenario ${scenario}`,
      );

      const dbRecords = await this.prisma.dailyRecommendation.findMany({
        where: {
          user_id: userId,
          scenario: scenario,
          generated_at: {
            gte: this.getTodayInShanghai(),
            lt: new Date(
              this.getTodayInShanghai().getTime() + 24 * 60 * 60 * 1000,
            ),
          },
        },
        include: {
          problem: true,
        },
        orderBy: {
          score: 'desc',
        },
        take: size,
      });

      if (dbRecords.length > 0) {
        const result = this.mapDbRecordsToResult(dbRecords);

        // Store in Redis for future requests
        await this.cacheService.set(cacheKey, result, 3600); // 1 hour TTL

        this.logger.debug(`Found ${dbRecords.length} recommendations in MySQL`);
        return null; // Return null as per requirement to trigger fresh recommendation generation
      }

      // Step 3: Both cache and MySQL miss
      this.logger.debug(
        `No recommendations found for user ${userId}, scenario ${scenario}`,
      );
      return null;
    } catch (error) {
      this.logger.error(
        `Error getting cached recommendations for user ${userId}:`,
        error,
      );
      return null;
    }
  }

  /**
   * Store recommendations in both MySQL and Redis cache
   *
   * @param userId The user ID
   * @param scenario The recommendation scenario
   * @param recommendations The recommendations to store
   */
  async storeRecommendations(
    userId: string,
    scenario: RecommendScenario,
    recommendations: RecommendResult,
  ): Promise<void> {
    try {
      // Clear old recommendations for today
      const todayString = this.getTodayDateString();
      const todayStart = this.getTodayInShanghai();
      const todayEnd = new Date(todayStart.getTime() + 24 * 60 * 60 * 1000);

      await this.prisma.dailyRecommendation.deleteMany({
        where: {
          user_id: userId,
          scenario: scenario,
          generated_at: {
            gte: todayStart,
            lt: todayEnd,
          },
        },
      });

      // Store new recommendations
      if (recommendations.items.length > 0) {
        const data = recommendations.items.map((item) => ({
          user_id: userId,
          problem_id: BigInt(item.problemId),
          scenario: scenario,
          score: item.score,
          reason: item.reason.substring(0, 500), // Truncate to 500 chars max
          tags: item.tags,
          generated_at: new Date(recommendations.generatedAt),
        }));

        await this.prisma.dailyRecommendation.createMany({
          data,
        });
      }

      // Store in Redis cache
      const cacheKey = this.buildCacheKey(userId, scenario, todayString);
      await this.cacheService.set(cacheKey, recommendations, 3600); // 1 hour TTL

      this.logger.debug(
        `Stored ${recommendations.items.length} recommendations for user ${userId}, scenario ${scenario}`,
      );
    } catch (error) {
      this.logger.error(
        `Error storing recommendations for user ${userId}:`,
        error,
      );
      // Don't throw to allow the main flow to continue
    }
  }

  /**
   * Build cache key for recommendations
   * @param userId User ID
   * @param scenario Recommendation scenario
   * @param dateDateString Date string (YYYY-MM-DD format)
   * @returns Cache key string
   */
  private buildCacheKey(
    userId: string,
    scenario: RecommendScenario,
    dateDateString: string,
  ): string {
    return `recommendation:${userId}:${scenario}:${dateDateString}`;
  }

  /**
   * Get today's date string in YYYY-MM-DD format
   * @returns Today's date string
   */
  private getTodayDateString(): string {
    return new Date().toISOString().split('T')[0];
  }

  /**
   * Get today's date in Asia/Shanghai timezone at 00:00:00
   * @returns Date object representing today in Shanghai
   */
  private getTodayInShanghai(): Date {
    const now = new Date();
    const shanghaiTime = new Date(
      now.toLocaleString('en-US', { timeZone: 'Asia/Shanghai' }),
    );
    return new Date(
      shanghaiTime.getFullYear(),
      shanghaiTime.getMonth(),
      shanghaiTime.getDate(),
    );
  }

  /**
   * Map database records to recommendation result format
   * @param dbRecords Array of daily recommendation records with problem details
   * @returns Recommendation result
   */
  private mapDbRecordsToResult(
    dbRecords: (DailyRecommendation & { problem: any })[],
  ): RecommendResult {
    const validItems = dbRecords
      .filter((record) => record.problem)
      .map((record) => ({
        problemId: Number(record.problem_id),
        slug: record.problem.slug,
        title: record.problem.title,
        difficulty: record.problem.difficulty,
        score: record.score,
        tags: Array.isArray(record.tags)
          ? record.tags.filter((tag): tag is string => typeof tag === 'string')
          : [],
        reason: record.reason || '',
      }));

    return {
      items: validItems,
      totalCount: validItems.length,
      scenario:
        (dbRecords[0]?.scenario as RecommendScenario) ||
        RecommendScenario.DAILY,
      generatedAt: new Date().toISOString(),
    };
  }
}
