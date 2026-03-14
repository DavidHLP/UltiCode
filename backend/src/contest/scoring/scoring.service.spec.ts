import { Test, TestingModule } from '@nestjs/testing';
import { ScoringService } from './scoring.service';
import { PrismaService } from '../../prisma.service';

describe('ScoringService', () => {
  let service: ScoringService;
  let prisma: jest.Mocked<PrismaService>;

  const mockRule = {
    id: 'rule-1',
    name: 'Test Rule',
    description: null,
    base_score_per_problem: 100,
    time_bonus_per_minute: 1,
    wrong_answer_penalty: 5,
    time_limit_penalty: 0,
    first_solve_bonus: 10,
    full_score_bonus: 0,
    is_default: false,
    is_active: true,
    created_at: new Date(),
    updated_at: new Date(),
  };

  const mockProblem = {
    id: 'cp-1',
    contest_id: 'contest-1',
    problem_id: BigInt(1),
    problem_index: 'A',
    label: 'A',
    score: 100,
    base_score: 100,
    time_bonus: 1,
    penalty_per_wrong: null,
    solved_count: 0,
    submission_count: 0,
  };

  const mockSubmission = {
    id: 'sub-1',
    submission_id: 'submission-1',
    contest_id: 'contest-1',
    contest_problem_id: 'cp-1',
    participant_id: 'participant-1',
    virtual_session_id: null,
    submitted_at: new Date(),
    time_from_start: 300, // 5 minutes in seconds
    is_accepted: true,
  };

  const mockParticipant = {
    id: 'participant-1',
    contest_id: 'contest-1',
    user_id: 'user-1',
    status: 'PARTICIPATING' as const,
    registered_at: new Date(),
    started_at: new Date(),
    finished_at: null,
    checked_in_at: null,
    is_virtual: false,
    final_rank: null,
    total_penalty: 0,
    total_score: 0,
    total_time: 0,
    total_attempts: 0,
    attempt_count: 0,
    last_solve_time: null,
    virtual_session_id: null,
  };

  // Helper to create a fresh module with feature flag enabled
  function createModuleWithFeatureFlagEnabled() {
    // Store original env
    const originalEnv = process.env.FEATURE_NEW_CONTEST;

    // Set env and reset modules to re-evaluate feature flags
    process.env.FEATURE_NEW_CONTEST = 'true';
    jest.resetModules();

    // Re-import modules after reset using require
    const { ScoringService: FreshScoringService } = require('./scoring.service');
    const { PrismaService: FreshPrismaService } = require('../../prisma.service');

    const mockPrisma = {
      contest: {
        findUnique: jest.fn(),
      },
      contestSubmission: {
        findMany: jest.fn(),
      },
      contestParticipant: {
        findMany: jest.fn(),
        update: jest.fn(),
        updateMany: jest.fn(),
      },
      contestProblem: {
        findFirst: jest.fn(),
      },
      $transaction: jest.fn((fn: any) => fn(mockPrisma)),
    };

    return Test.createTestingModule({
      providers: [
        FreshScoringService,
        { provide: FreshPrismaService, useValue: mockPrisma },
      ],
    })
      .compile()
      .then((module) => {
        // Restore original env
        process.env.FEATURE_NEW_CONTEST = originalEnv;

        return {
          service: module.get<ScoringService>(FreshScoringService),
          prisma: module.get(FreshPrismaService),
        };
      });
  }

  beforeEach(async () => {
    const mockPrisma = {
      contest: {
        findUnique: jest.fn(),
      },
      contestSubmission: {
        findMany: jest.fn(),
      },
      contestParticipant: {
        findMany: jest.fn(),
        update: jest.fn(),
        updateMany: jest.fn(),
      },
      contestProblem: {
        findFirst: jest.fn(),
      },
      $transaction: jest.fn((fn) => fn(mockPrisma)),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ScoringService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<ScoringService>(ScoringService);
    prisma = module.get(PrismaService);
  });

  describe('calculateProblemScore', () => {
    it('should calculate score for accepted submission', () => {
      const result = service.calculateProblemScore(
        mockProblem as any,
        mockSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.base_score).toBe(100);
      expect(result.time_bonus).toBe(5); // 5 minutes * 1 per minute
      expect(result.total_score).toBe(105);
    });

    it('should add first solve bonus', () => {
      const result = service.calculateProblemScore(
        mockProblem as any,
        mockSubmission as any,
        mockRule as any,
        true,
      );

      expect(result.total_score).toBe(115); // 100 + 5 + 10
    });

    it('should return 0 for wrong answer', () => {
      const wrongSubmission = { ...mockSubmission, is_accepted: false };

      const result = service.calculateProblemScore(
        mockProblem as any,
        wrongSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.total_score).toBe(0);
      expect(result.penalty).toBe(5); // wrong_answer_penalty
    });

    it('should use problem-specific base score when defined', () => {
      const problemWithCustomScore = {
        ...mockProblem,
        base_score: 150,
      };

      const result = service.calculateProblemScore(
        problemWithCustomScore as any,
        mockSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.base_score).toBe(150);
      expect(result.total_score).toBe(155); // 150 + 5 minutes
    });

    it('should use problem-specific time bonus when defined', () => {
      const problemWithTimeBonus = {
        ...mockProblem,
        time_bonus: 2, // 2 points per minute instead of 1
      };

      const result = service.calculateProblemScore(
        problemWithTimeBonus as any,
        mockSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.time_bonus).toBe(10); // 5 minutes * 2
      expect(result.total_score).toBe(110);
    });

    it('should return 0 time bonus for non-accepted submission', () => {
      const wrongSubmission = { ...mockSubmission, is_accepted: false };

      const result = service.calculateProblemScore(
        mockProblem as any,
        wrongSubmission as any,
        mockRule as any,
        false,
      );

      expect(result.time_bonus).toBe(0);
    });
  });

  describe('getRankingSnapshot', () => {
    it('should return ranking list sorted by rank', async () => {
      const mockParticipants = [
        {
          id: 'p1',
          contest_id: 'contest-1',
          user_id: 'user-1',
          status: 'PARTICIPATING',
          final_rank: 1,
          total_score: 300,
          total_time: 600,
          total_penalty: 0,
          user: { id: 'user-1', username: 'alice', avatar: null },
        },
        {
          id: 'p2',
          contest_id: 'contest-1',
          user_id: 'user-2',
          status: 'PARTICIPATING',
          final_rank: 2,
          total_score: 250,
          total_time: 700,
          total_penalty: 0,
          user: { id: 'user-2', username: 'bob', avatar: null },
        },
      ];

      prisma.contestParticipant.findMany.mockResolvedValue(
        mockParticipants as any,
      );

      const result = await service.getRankingSnapshot('contest-1');

      expect(result).toHaveLength(2);
      expect(result[0].rank).toBe(1);
      expect(result[0].username).toBe('alice');
      expect(result[0].score).toBe(300);
      expect(result[1].rank).toBe(2);
      expect(result[1].username).toBe('bob');
    });

    it('should respect limit parameter', async () => {
      prisma.contestParticipant.findMany.mockResolvedValue([]);

      await service.getRankingSnapshot('contest-1', 50);

      expect(prisma.contestParticipant.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          take: 50,
        }),
      );
    });

    it('should only return participants with a rank', async () => {
      prisma.contestParticipant.findMany.mockResolvedValue([]);

      await service.getRankingSnapshot('contest-1');

      expect(prisma.contestParticipant.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            final_rank: { not: null },
          }),
        }),
      );
    });
  });

  describe('updateContestRanking', () => {
    it('should skip when feature flag is disabled', async () => {
      // By default, feature flag is disabled (FEATURE_NEW_CONTEST is not 'true')
      await service.updateContestRanking('contest-1');

      expect(prisma.contest.findUnique).not.toHaveBeenCalled();
    });

    it('should warn if contest not found', async () => {
      const { service: serviceWithFlag, prisma: prismaWithFlag } =
        await createModuleWithFeatureFlagEnabled();

      (prismaWithFlag.contest.findUnique as jest.Mock).mockResolvedValue(null);

      await serviceWithFlag.updateContestRanking('non-existent');

      expect(prismaWithFlag.contest.findUnique).toHaveBeenCalledWith({
        where: { id: 'non-existent' },
        include: {
          scoring_rule: true,
          problems: true,
        },
      });
    });

    it('should warn if contest has no scoring rule', async () => {
      const { service: serviceWithFlag, prisma: prismaWithFlag } =
        await createModuleWithFeatureFlagEnabled();

      (prismaWithFlag.contest.findUnique as jest.Mock).mockResolvedValue({
        id: 'contest-1',
        scoring_rule: null,
        problems: [],
      } as any);

      await serviceWithFlag.updateContestRanking('contest-1');

      // Should not proceed to fetch submissions
      expect(prismaWithFlag.contestSubmission.findMany).not.toHaveBeenCalled();
    });

    it('should calculate and update rankings correctly', async () => {
      const { service: serviceWithFlag, prisma: prismaWithFlag } =
        await createModuleWithFeatureFlagEnabled();

      const mockContest = {
        id: 'contest-1',
        scoring_rule: mockRule,
        problems: [mockProblem],
      };

      const mockSubmissions = [
        {
          ...mockSubmission,
          participant_id: 'participant-1',
          is_accepted: true,
          time_from_start: 300, // 5 minutes
        },
        {
          ...mockSubmission,
          id: 'sub-2',
          participant_id: 'participant-2',
          is_accepted: true,
          time_from_start: 600, // 10 minutes
        },
      ];

      const mockParticipants = [
        {
          id: 'participant-1',
          contest_id: 'contest-1',
          user_id: 'user-1',
        },
        {
          id: 'participant-2',
          contest_id: 'contest-1',
          user_id: 'user-2',
        },
      ];

      (prismaWithFlag.contest.findUnique as jest.Mock).mockResolvedValue(
        mockContest as any,
      );
      (prismaWithFlag.contestSubmission.findMany as jest.Mock).mockResolvedValue(
        mockSubmissions as any,
      );
      (prismaWithFlag.contestParticipant.findMany as jest.Mock).mockResolvedValue(
        mockParticipants as any,
      );
      (prismaWithFlag.contestParticipant.update as jest.Mock).mockResolvedValue(
        {} as any,
      );

      await serviceWithFlag.updateContestRanking('contest-1');

      // Verify transaction was called
      expect(prismaWithFlag.$transaction).toHaveBeenCalled();
    });
  });
});
