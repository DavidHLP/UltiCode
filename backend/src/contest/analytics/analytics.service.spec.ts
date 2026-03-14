import { Test, TestingModule } from '@nestjs/testing';
import {
  AnalyticsService,
  ContestReport,
  ProblemStats,
  ScoreDistributionRange,
  UserPerformanceEntry,
} from './analytics.service';
import { PrismaService } from '../../prisma.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let prisma: jest.Mocked<PrismaService>;

  const mockPrismaService = {
    contest: {
      findUnique: jest.fn(),
    },
    contestParticipant: {
      count: jest.fn(),
    },
    contestProblem: {
      findMany: jest.fn(),
    },
    contestRanking: {
      findMany: jest.fn(),
      count: jest.fn(),
    },
    contestAnalytics: {
      findUnique: jest.fn(),
      upsert: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AnalyticsService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<AnalyticsService>(AnalyticsService);
    prisma = module.get(PrismaService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('generateContestReport', () => {
    it('should return null when contest not found', async () => {
      prisma.contest.findUnique.mockResolvedValue(null);

      const result = await service.generateContestReport('non-existent');

      expect(result).toBeNull();
    });

    it('should generate comprehensive contest report', async () => {
      // Mock contest data
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        title: 'Test Contest',
        registered_count: 100,
        participant_count: 80,
      } as any);

      // Mock participant counts
      prisma.contestParticipant.count
        .mockResolvedValueOnce(100) // total registered
        .mockResolvedValueOnce(80); // total participated

      // Mock contest problems
      prisma.contestProblem.findMany.mockResolvedValue([
        {
          id: 'cp-1',
          problem_index: 'A',
          score: 100,
          solved_count: 50,
          submission_count: 150,
          problem: { id: 1n, title: 'Problem A' },
        },
        {
          id: 'cp-2',
          problem_index: 'B',
          score: 150,
          solved_count: 30,
          submission_count: 200,
          problem: { id: 2n, title: 'Problem B' },
        },
      ] as any);

      // Mock rankings for score distribution and top users
      prisma.contestRanking.findMany.mockResolvedValue([
        {
          id: 'r1',
          rank: 1,
          user_id: 'user-1',
          total_score: 250,
          total_penalty: 300,
          solved_count: 2,
          finish_time: 7200,
          user: { id: 'user-1', username: 'top_user' },
        },
        {
          id: 'r2',
          rank: 2,
          user_id: 'user-2',
          total_score: 200,
          total_penalty: 450,
          solved_count: 2,
          finish_time: 8000,
          user: { id: 'user-2', username: 'second_user' },
        },
        {
          id: 'r3',
          rank: 3,
          user_id: 'user-3',
          total_score: 150,
          total_penalty: 600,
          solved_count: 1,
          finish_time: 5400,
          user: { id: 'user-3', username: 'third_user' },
        },
      ] as any);

      prisma.contestRanking.count.mockResolvedValue(80);

      // Mock upsert
      prisma.contestAnalytics.upsert.mockResolvedValue({
        id: 'analytics-1',
        contest_id: 'contest-1',
        total_registered: 100,
        total_participated: 80,
        completion_rate: 0.8,
        problem_stats: [],
        score_distribution: [],
        time_distribution: null,
        top_users: [],
        generated_at: new Date(),
      } as any);

      const result = await service.generateContestReport('contest-1');

      expect(result).not.toBeNull();
      expect(result?.contestId).toBe('contest-1');
      expect(result?.contestTitle).toBe('Test Contest');
      expect(result?.totalRegistered).toBe(100);
      expect(result?.totalParticipated).toBe(80);
      expect(result?.completionRate).toBe(0.8);
      expect(result?.problemStats).toHaveLength(2);
      expect(result?.topUsers).toHaveLength(3);
      expect(result?.generatedAt).toBeInstanceOf(Date);
    });

    it('should calculate problem stats correctly', async () => {
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        title: 'Test Contest',
        registered_count: 100,
        participant_count: 80,
      } as any);

      prisma.contestParticipant.count
        .mockResolvedValueOnce(100)
        .mockResolvedValueOnce(80);

      prisma.contestProblem.findMany.mockResolvedValue([
        {
          id: 'cp-1',
          problem_index: 'A',
          score: 100,
          solved_count: 50,
          submission_count: 150,
          problem: { id: 1n, title: 'Problem A' },
        },
      ] as any);

      prisma.contestRanking.findMany.mockResolvedValue([]);
      prisma.contestRanking.count.mockResolvedValue(80);
      prisma.contestAnalytics.upsert.mockResolvedValue({} as any);

      const result = await service.generateContestReport('contest-1');

      const problemStat = result?.problemStats[0];
      expect(problemStat?.problemIndex).toBe('A');
      expect(problemStat?.problemTitle).toBe('Problem A');
      expect(problemStat?.solvedCount).toBe(50);
      expect(problemStat?.submissionCount).toBe(150);
      // Acceptance rate = solved / submissions = 50 / 150 = 0.333
      expect(problemStat?.acceptanceRate).toBeCloseTo(0.333, 2);
    });

    it('should calculate completion rate correctly', async () => {
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        title: 'Test Contest',
      } as any);

      prisma.contestParticipant.count
        .mockResolvedValueOnce(200) // registered
        .mockResolvedValueOnce(150); // participated

      prisma.contestProblem.findMany.mockResolvedValue([]);
      prisma.contestRanking.findMany.mockResolvedValue([]);
      prisma.contestRanking.count.mockResolvedValue(150);
      prisma.contestAnalytics.upsert.mockResolvedValue({} as any);

      const result = await service.generateContestReport('contest-1');

      // completion rate = participated / registered = 150 / 200 = 0.75
      expect(result?.completionRate).toBe(0.75);
    });

    it('should handle zero registered users', async () => {
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        title: 'Test Contest',
      } as any);

      prisma.contestParticipant.count
        .mockResolvedValueOnce(0) // registered
        .mockResolvedValueOnce(0); // participated

      prisma.contestProblem.findMany.mockResolvedValue([]);
      prisma.contestRanking.findMany.mockResolvedValue([]);
      prisma.contestRanking.count.mockResolvedValue(0);
      prisma.contestAnalytics.upsert.mockResolvedValue({} as any);

      const result = await service.generateContestReport('contest-1');

      expect(result?.completionRate).toBe(0);
    });

    it('should calculate score distribution correctly', async () => {
      prisma.contest.findUnique.mockResolvedValue({
        id: 'contest-1',
        title: 'Test Contest',
      } as any);

      prisma.contestParticipant.count
        .mockResolvedValueOnce(100)
        .mockResolvedValueOnce(100);

      prisma.contestProblem.findMany.mockResolvedValue([]);

      // Create rankings with varied scores for distribution testing
      const rankings = [];
      for (let i = 0; i < 100; i++) {
        rankings.push({
          id: `r${i}`,
          rank: i + 1,
          user_id: `user-${i}`,
          total_score: i * 3, // Scores from 0 to 297
          total_penalty: 300,
          solved_count: 1,
          finish_time: 7200,
          user: { id: `user-${i}`, username: `user${i}` },
        });
      }

      prisma.contestRanking.findMany.mockResolvedValue(rankings as any);
      prisma.contestRanking.count.mockResolvedValue(100);
      prisma.contestAnalytics.upsert.mockResolvedValue({} as any);

      const result = await service.generateContestReport('contest-1');

      expect(result?.scoreDistribution).toBeDefined();
      expect(result?.scoreDistribution.length).toBeGreaterThan(0);

      // Verify distribution sums to total participants
      const totalInDistribution = result?.scoreDistribution.reduce(
        (sum, range) => sum + range.count,
        0,
      );
      expect(totalInDistribution).toBe(100);
    });
  });

  describe('getStoredReport', () => {
    it('should return null when no stored analytics exist', async () => {
      prisma.contestAnalytics.findUnique.mockResolvedValue(null);

      const result = await service.getStoredReport('contest-1');

      expect(result).toBeNull();
    });

    it('should return stored analytics report', async () => {
      const mockAnalytics = {
        id: 'analytics-1',
        contest_id: 'contest-1',
        total_registered: 100,
        total_participated: 80,
        completion_rate: 0.8,
        problem_stats: [
          {
            problemIndex: 'A',
            problemTitle: 'Problem A',
            solvedCount: 50,
            submissionCount: 150,
            acceptanceRate: 0.333,
            avgAttempts: 3,
          },
        ],
        score_distribution: [
          { min: 0, max: 100, count: 20 },
          { min: 101, max: 200, count: 30 },
        ],
        time_distribution: null,
        top_users: [
          {
            rank: 1,
            userId: 'user-1',
            username: 'top',
            score: 250,
            time: 7200,
            solvedCount: 2,
          },
        ],
        generated_at: new Date('2024-01-01T12:00:00Z'),
        contest: { title: 'Test Contest' },
      };

      prisma.contestAnalytics.findUnique.mockResolvedValue(
        mockAnalytics as any,
      );

      const result = await service.getStoredReport('contest-1');

      expect(result).not.toBeNull();
      expect(result?.contestId).toBe('contest-1');
      expect(result?.contestTitle).toBe('Test Contest');
      expect(result?.totalRegistered).toBe(100);
      expect(result?.totalParticipated).toBe(80);
      expect(result?.completionRate).toBe(0.8);
      expect(result?.problemStats).toHaveLength(1);
      expect(result?.scoreDistribution).toHaveLength(2);
      expect(result?.topUsers).toHaveLength(1);
    });
  });

  describe('getUserPerformanceHistory', () => {
    it('should return empty array when user has no contest history', async () => {
      prisma.contestRanking.findMany.mockResolvedValue([]);

      const result = await service.getUserPerformanceHistory('user-1');

      expect(result).toEqual([]);
    });

    it('should return user performance history with default limit', async () => {
      const mockRankings = [
        {
          contest_id: 'contest-1',
          rank: 5,
          total_score: 200,
          solved_count: 3,
          rating_before: 1500,
          rating_after: 1520,
          rating_change: 20,
          is_virtual: false,
          contest: {
            title: 'Contest 1',
            start_time: new Date('2024-01-01'),
            _count: { rankings: 100 },
          },
        },
        {
          contest_id: 'contest-2',
          rank: 10,
          total_score: 150,
          solved_count: 2,
          rating_before: 1520,
          rating_after: 1510,
          rating_change: -10,
          is_virtual: false,
          contest: {
            title: 'Contest 2',
            start_time: new Date('2024-01-15'),
            _count: { rankings: 80 },
          },
        },
      ];

      prisma.contestRanking.findMany.mockResolvedValue(mockRankings as any);

      const result = await service.getUserPerformanceHistory('user-1');

      expect(result).toHaveLength(2);
      expect(result[0].contestId).toBe('contest-1');
      expect(result[0].contestTitle).toBe('Contest 1');
      expect(result[0].rank).toBe(5);
      expect(result[0].score).toBe(200);
      expect(result[0].solvedCount).toBe(3);
      expect(result[0].ratingBefore).toBe(1500);
      expect(result[0].ratingAfter).toBe(1520);
      expect(result[0].ratingChange).toBe(20);
      expect(result[0].totalParticipants).toBe(100);
      expect(result[0].isVirtual).toBe(false);
    });

    it('should respect limit parameter', async () => {
      prisma.contestRanking.findMany.mockResolvedValue([]);

      await service.getUserPerformanceHistory('user-1', 5);

      expect(prisma.contestRanking.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          take: 5,
        }),
      );
    });

    it('should sort history by contest date descending', async () => {
      const mockRankings = [
        {
          contest_id: 'contest-1',
          rank: 1,
          total_score: 100,
          solved_count: 1,
          rating_before: 1500,
          rating_after: 1510,
          rating_change: 10,
          is_virtual: false,
          contest: {
            title: 'Contest 1',
            start_time: new Date('2024-01-01'),
            _count: { rankings: 50 },
          },
        },
        {
          contest_id: 'contest-2',
          rank: 2,
          total_score: 200,
          solved_count: 2,
          rating_before: 1510,
          rating_after: 1520,
          rating_change: 10,
          is_virtual: false,
          contest: {
            title: 'Contest 2',
            start_time: new Date('2024-02-01'),
            _count: { rankings: 60 },
          },
        },
      ];

      prisma.contestRanking.findMany.mockResolvedValue(mockRankings as any);

      await service.getUserPerformanceHistory('user-1');

      expect(prisma.contestRanking.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          orderBy: {
            contest: {
              start_time: 'desc',
            },
          },
        }),
      );
    });
  });
});
