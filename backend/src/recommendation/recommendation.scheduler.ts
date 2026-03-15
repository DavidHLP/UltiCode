import { Injectable, Logger } from '@nestjs/common';
import { Cron } from '@nestjs/schedule';
import { PrismaService } from '../prisma.service';
import { RecommendationService } from './services/recommendation.service';
import { RecommendationCacheService } from './services/recommendation-cache.service';
import { RecommendScenario } from './interfaces/recommendation.interface';

interface GenerateDailyRecommendationsResult {
  totalUsers: number;
  successCount: number;
  failedUsers: string[];
}

@Injectable()
export class RecommendationScheduler {
  private readonly logger = new Logger(RecommendationScheduler.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly recommendationService: RecommendationService,
    private readonly recommendationCacheService: RecommendationCacheService,
  ) {}

  @Cron('0 8 * * *', {
    timeZone: 'Asia/Shanghai',
  })
  async generateDailyRecommendations(): Promise<GenerateDailyRecommendationsResult> {
    this.logger.log('Starting daily recommendations generation');

    try {
      // Get all active users
      const activeUsers = await this.prisma.user.findMany({
        where: {
          is_active: true,
          is_banned: false,
        },
        select: {
          id: true,
        },
      });

      this.logger.log(`Found ${activeUsers.length} active users`);

      if (activeUsers.length === 0) {
        this.logger.log(
          'No active users found, skipping recommendation generation',
        );
        return {
          totalUsers: 0,
          successCount: 0,
          failedUsers: [],
        };
      }

      const failedUsers: string[] = [];
      let successCount = 0;

      // Process users one by one to track failures
      for (const user of activeUsers) {
        try {
          let userSuccess = true;
          
          // Generate recommendations for each scenario
          const scenarios = [RecommendScenario.DAILY, RecommendScenario.WEAK_POINT, RecommendScenario.CHALLENGE];

          for (const scenario of scenarios) {
            try {
              const result = await this.recommendationService.getRecommendations({
                userId: user.id,
                scenario,
              });

              if (!result?.success || !result?.data) {
                throw new Error(result?.message || 'Failed to generate recommendations');
              }

              // Cache the recommendations
              await this.recommendationCacheService.storeRecommendations(
                user.id,
                scenario,
                result.data,
              );
            } catch (error) {
              this.logger.error(`Failed to generate recommendations for user ${user.id}, scenario ${scenario}:`, error);
              userSuccess = false;
              break;
            }
          }

          if (userSuccess) {
            successCount++;
          } else {
            failedUsers.push(user.id);
          }
        } catch (error) {
          this.logger.error(`Failed to generate recommendations for user ${user.id}:`, error);
          failedUsers.push(user.id);
        }
      }

      this.logger.log(
        `Daily recommendations generation completed. Success: ${successCount}, Failure: ${failedUsers.length}, Total: ${activeUsers.length}`,
      );

      return {
        totalUsers: activeUsers.length,
        successCount,
        failedUsers,
      };
    } catch (error) {
      this.logger.error('Failed to generate daily recommendations:', error);
      throw error;
    }
  }

  private async processBatchWithRetry(
    batch: { id: string }[],
    batchNumber: number,
    maxRetries: number = 3,
  ): Promise<{
    successCount: number;
    failureCount: number;
    processedCount: number;
  }> {
    let retryCount = 0;
    let successCount = 0;
    let failureCount = 0;
    let processedCount = 0;

    while (retryCount <= maxRetries) {
      try {
        // Generate recommendations for all users in batch
        for (const user of batch) {
          let userSuccess = true;
          
          // Generate recommendations for each scenario
          const scenarios = [RecommendScenario.DAILY, RecommendScenario.WEAK_POINT, RecommendScenario.CHALLENGE];

          for (const scenario of scenarios) {
            try {
              const result = await this.recommendationService.getRecommendations({
                userId: user.id,
                scenario,
              });

              if (!result?.success || !result?.data) {
                throw new Error(result?.message || 'Failed to generate recommendations');
              }

              // Cache the recommendations
              await this.recommendationCacheService.storeRecommendations(
                user.id,
                scenario,
                result.data,
              );
            } catch (error) {
              this.logger.error(`Failed to generate recommendations for user ${user.id}, scenario ${scenario}:`, error);
              userSuccess = false;
              break;
            }
          }
          
          if (userSuccess) {
            successCount++;
          } else {
            failureCount++;
          }
        }

        processedCount += batch.length;
        this.logger.log(`Successfully processed batch ${batchNumber}`);
        return { successCount, failureCount, processedCount };
      } catch (error) {
        retryCount++;
        if (retryCount > maxRetries) {
          this.logger.error(
            `Batch ${batchNumber} failed after ${maxRetries} retries:`,
            error,
          );
          failureCount += batch.length;
          processedCount += batch.length;
          return { successCount, failureCount, processedCount };
        }

        // Exponential backoff: 1s, 2s, 4s
        const delay = Math.pow(2, retryCount) * 1000;
        this.logger.warn(
          `Batch ${batchNumber} failed (attempt ${retryCount}/${maxRetries}), retrying in ${delay}ms...`,
        );
        await this.sleep(delay);
      }
    }

    return { successCount, failureCount, processedCount };
  }

  private async generateUserRecommendations(userId: string) {
    const scenarios = [RecommendScenario.DAILY, RecommendScenario.WEAK_POINT, RecommendScenario.CHALLENGE];

    const results = await Promise.allSettled(
      scenarios.map(async (scenario) => {
        return this.recommendationService.getRecommendations({
          userId,
          scenario,
        });
      }),
    );

    const recommendations = results.map((result, index) => ({
      scenario: scenarios[index],
      success: result.status === 'fulfilled',
      error:
        result.status === 'rejected' ? (result.reason as Error).message : null,
    }));

    return recommendations;
  }

  private chunk<T>(array: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += size) {
      chunks.push(array.slice(i, i + size));
    }
    return chunks;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async triggerManually(): Promise<{ success: boolean; message: string; error?: string }> {
    this.logger.log('Manually triggering recommendations generation');

    try {
      const result = await this.generateDailyRecommendations();
      this.logger.log(
        'Manual recommendations generation completed successfully',
      );
      return {
        success: true,
        message: 'Recommendations generated successfully',
      };
    } catch (error) {
      this.logger.error('Manual recommendations generation failed:', error);
      return {
        success: false,
        message: 'Failed to generate recommendations',
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  }
}
