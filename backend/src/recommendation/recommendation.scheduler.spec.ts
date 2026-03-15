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
      const mockUsers = [{ id: 'user1' }, { id: 'user2' }, { id: 'user3' }];

      prisma.user.findMany.mockResolvedValue(mockUsers);

      recommendationService.getRecommendations.mockResolvedValue({
        success: true,
        code: 200,
        message: 'success',
        data: {
          items: [
            {
              problemId: 1,
              slug: 'test',
              title: 'Test',
              difficulty: 'Easy',
              score: 0.9,
              tags: [],
              reason: 'test',
            },
          ],
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

      // User1 - all scenarios succeed
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
        .mockResolvedValueOnce({
          success: true,
          code: 200,
          message: 'success',
          data: {
            items: [],
            totalCount: 0,
            scenario: RecommendScenario.WEAK_POINT,
            generatedAt: new Date().toISOString(),
          },
        })
        .mockResolvedValueOnce({
          success: true,
          code: 200,
          message: 'success',
          data: {
            items: [],
            totalCount: 0,
            scenario: RecommendScenario.CHALLENGE,
            generatedAt: new Date().toISOString(),
          },
        })
        // User2 - first scenario fails
        .mockRejectedValueOnce(new Error('Service unavailable'));
      // Remaining calls for user2 don't matter as it will fail fast

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
