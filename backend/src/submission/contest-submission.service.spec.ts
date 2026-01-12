import { Test, TestingModule } from '@nestjs/testing';
import { ContestSubmissionService } from './contest-submission.service';
import { PrismaService } from '../prisma.service';
import { SubmissionService } from './submission.service';
import { RankingService } from '../contest/ranking.service';

describe('ContestSubmissionService', () => {
  let service: ContestSubmissionService;
  let prisma: jest.Mocked<PrismaService>;
  let submissionService: jest.Mocked<SubmissionService>;
  let rankingService: jest.Mocked<RankingService>;

  const mockContest = {
    id: 'contest-123',
    title: 'Weekly Contest',
    status: 'running',
    start_time: new Date(Date.now() - 60 * 60 * 1000), // Started 1 hour ago
    duration_minutes: 120,
    problems: [
      {
        id: 'cp-1',
        problem_id: 1,
        score: 100,
      },
    ],
  };

  const mockParticipant = {
    id: 'participant-123',
    user_id: 'user-123',
    contest_id: 'contest-123',
    status: 'STARTED',
    is_virtual: false,
    started_at: new Date('2026-01-15T10:00:00Z'),
    virtualSession: null,
  };

  const mockSubmission = {
    id: 'sub-123',
    user_id: 'user-123',
    problem_id: 1,
    language: 'javascript',
    code: 'function solution() {}',
    status: 'Pending',
    runtime: 0,
    memory: 0,
    created_at: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ContestSubmissionService,
        {
          provide: PrismaService,
          useValue: {
            contest: {
              findUnique: jest.fn(),
            },
            contestParticipant: {
              findFirst: jest.fn(),
              update: jest.fn(),
            },
            contestSubmission: {
              create: jest.fn(),
              findMany: jest.fn(),
              findFirst: jest.fn(),
              update: jest.fn(),
            },
            contestProblem: {
              update: jest.fn(),
            },
            contestProblemResult: {
              findFirst: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
            },
            $transaction: jest.fn((callback) => callback({})),
          },
        },
        {
          provide: SubmissionService,
          useValue: {
            create: jest.fn(),
            decorateSubmission: jest.fn().mockImplementation((sub) => sub),
          },
        },
        {
          provide: RankingService,
          useValue: {
            updateContestProblemResult: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<ContestSubmissionService>(ContestSubmissionService);
    prisma = module.get(PrismaService);
    submissionService = module.get(SubmissionService);
    rankingService = module.get(RankingService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('submitInContest', () => {
    it('should create a contest submission successfully', async () => {
      (prisma.contest.findUnique as jest.Mock).mockResolvedValue(
        mockContest as never,
      );
      (prisma.contestParticipant.findFirst as jest.Mock).mockResolvedValue(
        mockParticipant as never,
      );
      (prisma.contestParticipant.update as jest.Mock).mockResolvedValue(
        {} as never,
      );
      submissionService.create.mockResolvedValue(mockSubmission as never);
      (prisma.contestSubmission.create as jest.Mock).mockResolvedValue(
        {} as never,
      );

      const result = await service.submitInContest(
        'contest-123',
        1,
        'user-123',
        {
          language: 'javascript',
          code: 'function solution() {}',
        },
      );

      expect(result).toEqual(mockSubmission);
      expect(submissionService.create).toHaveBeenCalledWith('user-123', 1, {
        language: 'javascript',
        code: 'function solution() {}',
      });
      expect(prisma.contestSubmission.create).toHaveBeenCalled();
    });
  });

  describe('processContestSubmissionResult', () => {
    it('should process contest submission result', async () => {
      const params = {
        submissionId: 'sub-123',
        contestId: 'contest-123',
        contestProblemId: 'cp-1',
        userId: 'user-123',
        participantId: 'participant-123',
        isAccepted: true,
        solveTime: 100,
        score: 100,
      };

      (prisma.contestSubmission.findFirst as jest.Mock).mockResolvedValue({
        id: 'cs-123',
      } as never);
      (prisma.contestSubmission.update as jest.Mock).mockResolvedValue(
        {} as never,
      );
      (prisma.contestProblemResult.findFirst as jest.Mock).mockResolvedValue(
        null,
      );
      (prisma.contestProblem.update as jest.Mock).mockResolvedValue(
        {} as never,
      );
      (prisma.contestParticipant.update as jest.Mock).mockResolvedValue(
        {} as never,
      );
      rankingService.updateContestProblemResult.mockResolvedValue(undefined);

      await service.processContestSubmissionResult(params);

      expect(prisma.contestSubmission.update).toHaveBeenCalled();
      expect(rankingService.updateContestProblemResult).toHaveBeenCalledWith(
        'participant-123',
        'cp-1',
        true,
        100,
        100,
        expect.any(Object),
      );
    });
  });

  describe('getContestSubmissions', () => {
    it('should return contest submissions', async () => {
      const mockContestSubmissions = [
        {
          id: 'cs-123',
          contest_id: 'contest-123',
          submission_id: 'sub-123',
          contest_problem_id: 'cp-1',
          participant_id: 'participant-123',
          time_from_start: 100,
          is_accepted: true,
          submitted_at: new Date(),
          submission: mockSubmission,
          contestProblem: {
            problem_index: 'A',
            score: 100,
          },
        },
      ];

      (prisma.contestSubmission.findMany as jest.Mock).mockResolvedValue(
        mockContestSubmissions as never,
      );

      const result = await service.getContestSubmissions(
        'contest-123',
        'user-123',
        1,
      );

      expect(result).toHaveLength(1);
      expect(prisma.contestSubmission.findMany).toHaveBeenCalledWith({
        where: expect.any(Object),
        include: expect.any(Object),
        orderBy: { submitted_at: 'desc' },
      });
    });
  });
});
