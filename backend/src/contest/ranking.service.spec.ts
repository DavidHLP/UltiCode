import { Test, TestingModule } from '@nestjs/testing';
import { RankingService } from './ranking.service';
import { PrismaService } from '../prisma.service';

describe('RankingService', () => {
  let service: RankingService;
  let prisma: jest.Mocked<PrismaService>;

  const mockContest = {
    id: 'contest-123',
    penalty_per_wrong: 300,
    scoring_mode: 'SCORE' as const,
    tie_breaker: 'LAST_SOLVE_TIME' as const,
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        RankingService,
        {
          provide: PrismaService,
          useValue: {
            contest: {
              findUnique: jest.fn().mockResolvedValue(mockContest),
              update: jest.fn(),
            },
            contestRanking: {
              findMany: jest.fn(),
              findFirst: jest.fn(),
              count: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
              updateMany: jest.fn(),
            },
            contestParticipant: {
              findMany: jest.fn(),
              findUnique: jest.fn(),
              update: jest.fn(),
            },
            contestProblem: {
              findUnique: jest.fn(),
              findFirst: jest.fn(),
            },
            contestProblemResult: {
              findFirst: jest.fn(),
              findMany: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
              updateMany: jest.fn(),
            },
            virtualContestSession: {
              update: jest.fn(),
            },
            globalRanking: {
              findUnique: jest.fn(),
              findMany: jest.fn(),
              count: jest.fn(),
            },
            user: {
              findUnique: jest.fn(),
            },
            $transaction: jest.fn((callback) => callback({})),
          },
        },
      ],
    }).compile();

    service = module.get<RankingService>(RankingService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getContestRanking', () => {
    it('should return paginated contest rankings', async () => {
      const mockRankings = [
        {
          id: 'rank-1',
          rank: 1,
          user_id: 'user-1',
          total_score: 100,
          total_penalty: 0,
          solved_count: 3,
          rating_before: 1500,
          rating_after: 1550,
          rating_change: 50,
          is_virtual: false,
          user: {
            id: 'user-1',
            username: 'testuser',
            avatar: 'avatar.png',
          },
          problemResults: [],
        },
      ];

      prisma.contestRanking.findMany.mockResolvedValue(mockRankings as never);
      prisma.contestRanking.count.mockResolvedValue(1);

      const result = await service.getContestRanking('contest-123', {});

      expect(result).toHaveProperty('items');
      expect(result).toHaveProperty('total');
      expect(result.items).toHaveLength(1);
    });
  });

  describe('getGlobalRanking', () => {
    it('should return paginated global rankings', async () => {
      const mockRankings = [
        {
          global_rank: 1,
          user_id: 'user-1',
          username: 'testuser',
          avatar: 'avatar.png',
          country: 'US',
          rating: 2500,
          max_rating: 2600,
          rating_title: 'GRANDMASTER',
          max_rating_title: 'INTERNATIONAL_GRANDMASTER',
          contests_attended: 50,
          badge: null,
        },
      ];

      prisma.globalRanking.findMany.mockResolvedValue(mockRankings as never);
      prisma.globalRanking.count.mockResolvedValue(1);

      const result = await service.getGlobalRanking({});

      expect(result).toHaveProperty('items');
      expect(result).toHaveProperty('total');
      expect(result.items).toHaveLength(1);
    });
  });

  describe('getUserContestHistory', () => {
    it('should return user contest history', async () => {
      const mockRankings = [
        {
          contest_id: 'contest-1',
          rank: 5,
          total_score: 80,
          solved_count: 2,
          rating_before: 1500,
          rating_after: 1550,
          rating_change: 50,
          is_virtual: false,
          contest: {
            title: 'Weekly Contest 1',
            start_time: new Date('2026-01-01'),
            _count: {
              rankings: 100,
            },
          },
        },
      ];

      prisma.contestRanking.findMany.mockResolvedValue(mockRankings as never);

      const result = await service.getUserContestHistory('user-123');

      expect(result).toHaveLength(1);
      expect(result[0].contestTitle).toBe('Weekly Contest 1');
    });
  });
});
