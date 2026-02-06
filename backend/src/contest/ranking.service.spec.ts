import { Test, TestingModule } from '@nestjs/testing';
import { RankingService } from './ranking.service';
import { PrismaService } from '../prisma.service';
import { RankingHelperService } from './services/ranking-helper.service';
import { GlobalRankingQueryService } from './services/global-ranking-query.service';
import { ContestRankingCalcService } from './services/contest-ranking-calc.service';
import { ContestRankingQueryService } from './services/contest-ranking-query.service';

describe('RankingService', () => {
  let service: RankingService;
  let globalRankingQuery: jest.Mocked<GlobalRankingQueryService>;
  let contestRankingCalc: jest.Mocked<ContestRankingCalcService>;
  let contestRankingQuery: jest.Mocked<ContestRankingQueryService>;

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
        {
          provide: RankingHelperService,
          useValue: {
            getContestConfig: jest.fn().mockResolvedValue({
              penaltyPerWrong: 300,
              scoringMode: 'SCORE' as const,
              tieBreaker: 'LAST_SOLVE_TIME' as const,
            }),
            getFinishTime: jest.fn().mockReturnValue(null),
            getTieBreakerValue: jest.fn().mockReturnValue(100),
            isSameRank: jest.fn().mockReturnValue(false),
          },
        },
        {
          provide: GlobalRankingQueryService,
          useValue: {
            getGlobalRanking: jest.fn().mockResolvedValue({
              items: [],
              total: 0,
              page: 1,
              limit: 50,
              totalPages: 0,
            }),
          },
        },
        {
          provide: ContestRankingCalcService,
          useValue: {
            updateContestProblemResult: jest.fn().mockResolvedValue(undefined),
            finalizeVirtualRanking: jest.fn().mockResolvedValue(undefined),
            finalizeContestRanking: jest.fn().mockResolvedValue(undefined),
          },
        },
        {
          provide: ContestRankingQueryService,
          useValue: {
            getContestRanking: jest.fn().mockResolvedValue({
              items: [],
              total: 0,
              page: 1,
              limit: 50,
              totalPages: 0,
            }),
            getLiveRanking: jest.fn().mockResolvedValue([]),
            getUserContestHistory: jest.fn().mockResolvedValue([]),
          },
        },
      ],
    }).compile();

    service = module.get<RankingService>(RankingService);
    globalRankingQuery = module.get(GlobalRankingQueryService);
    contestRankingCalc = module.get(ContestRankingCalcService);
    contestRankingQuery = module.get(ContestRankingQueryService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getContestRanking', () => {
    it('should delegate to ContestRankingQueryService', async () => {
      const mockResult = {
        items: [
          {
            rank: 1,
            userId: 'user-1',
            username: 'testuser',
            avatar: 'avatar.png',
            totalScore: 100,
            totalPenalty: 0,
            finishTime: null,
            totalAttempts: 0,
            solvedCount: 3,
            ratingBefore: 1500,
            ratingAfter: 1550,
            ratingChange: 50,
            isVirtual: false,
            problemResults: [],
          },
        ],
        total: 1,
        page: 1,
        limit: 50,
        totalPages: 1,
      };

      (contestRankingQuery.getContestRanking as jest.Mock).mockResolvedValue(
        mockResult,
      );

      const result = await service.getContestRanking('contest-123', {});

      expect(contestRankingQuery.getContestRanking).toHaveBeenCalledWith(
        'contest-123',
        {},
      );
      expect(result).toEqual(mockResult);
    });
  });

  describe('getGlobalRanking', () => {
    it('should delegate to GlobalRankingQueryService', async () => {
      const mockResult = {
        items: [
          {
            rank: 1,
            userId: 'user-1',
            username: 'testuser',
            avatar: 'avatar.png',
            country: 'US',
            rating: 2500,
            maxRating: 2600,
            ratingTitle: 'GRANDMASTER',
            maxRatingTitle: 'INTERNATIONAL_GRANDMASTER',
            contestsAttended: 50,
            badge: null,
          },
        ],
        total: 1,
        page: 1,
        limit: 50,
        totalPages: 1,
      };

      (globalRankingQuery.getGlobalRanking as jest.Mock).mockResolvedValue(
        mockResult,
      );

      const result = await service.getGlobalRanking({});

      expect(globalRankingQuery.getGlobalRanking).toHaveBeenCalledWith({});
      expect(result).toEqual(mockResult);
    });
  });

  describe('getUserContestHistory', () => {
    it('should delegate to ContestRankingQueryService', async () => {
      const mockResult = [
        {
          contestId: 'contest-1',
          contestTitle: 'Weekly Contest 1',
          contestDate: new Date('2026-01-01'),
          rank: 5,
          totalParticipants: 100,
          score: 80,
          solvedCount: 2,
          ratingBefore: 1500,
          ratingAfter: 1550,
          ratingChange: 50,
          isVirtual: false,
        },
      ];

      (
        contestRankingQuery.getUserContestHistory as jest.Mock
      ).mockResolvedValue(mockResult);

      const result = await service.getUserContestHistory('user-123');

      expect(contestRankingQuery.getUserContestHistory).toHaveBeenCalledWith(
        'user-123',
      );
      expect(result).toEqual(mockResult);
    });
  });

  describe('updateContestProblemResult', () => {
    it('should delegate to ContestRankingCalcService', async () => {
      await service.updateContestProblemResult(
        'participant-1',
        'problem-1',
        true,
        100,
        10,
      );

      expect(
        contestRankingCalc.updateContestProblemResult,
      ).toHaveBeenCalledWith(
        'participant-1',
        'problem-1',
        true,
        100,
        10,
        undefined,
      );
    });
  });

  describe('finalizeVirtualRanking', () => {
    it('should delegate to ContestRankingCalcService', async () => {
      await service.finalizeVirtualRanking('participant-1');

      expect(contestRankingCalc.finalizeVirtualRanking).toHaveBeenCalledWith(
        'participant-1',
      );
    });
  });

  describe('finalizeContestRanking', () => {
    it('should delegate to ContestRankingCalcService', async () => {
      await service.finalizeContestRanking('contest-1');

      expect(contestRankingCalc.finalizeContestRanking).toHaveBeenCalledWith(
        'contest-1',
      );
    });
  });

  describe('getLiveRanking', () => {
    it('should delegate to ContestRankingQueryService', async () => {
      const mockResult = [
        {
          rank: 1,
          userId: 'user-1',
          username: 'testuser',
          avatar: 'avatar.png',
          totalScore: 100,
          totalPenalty: 0,
          finishTime: null,
          totalAttempts: 0,
          solvedCount: 3,
          problemResults: [],
        },
      ];

      (contestRankingQuery.getLiveRanking as jest.Mock).mockResolvedValue(
        mockResult,
      );

      const result = await service.getLiveRanking('contest-1', 50);

      expect(contestRankingQuery.getLiveRanking).toHaveBeenCalledWith(
        'contest-1',
        50,
      );
      expect(result).toEqual(mockResult);
    });
  });
});
