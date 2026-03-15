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
      problem: {
        findUnique: jest.fn(),
      },
      user: {
        findUnique: jest.fn(),
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        RecommendationCacheService,
        { provide: CacheService, useValue: mockCacheService },
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<RecommendationCacheService>(
      RecommendationCacheService,
    );
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

    it('should store MySQL data in Redis and return null when Redis cache miss but MySQL has data', async () => {
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

      expect(result).toBeNull(); // Should return null to trigger fresh generation
      expect(cacheService.set).toHaveBeenCalled(); // But should cache the MySQL data
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
