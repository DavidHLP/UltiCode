import { Test, TestingModule } from '@nestjs/testing';
import { ContestSchedulerService } from './contest-scheduler.service';
import { PrismaService } from '../prisma.service';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';
import { Queue } from 'bullmq';

describe('ContestSchedulerService', () => {
  let service: ContestSchedulerService;
  let prisma: jest.Mocked<PrismaService>;
  let _rankingService: jest.Mocked<RankingService>;
  let contestQueue: jest.Mocked<Queue>;

  const mockContest = {
    id: 'contest-123',
    title: 'Weekly Contest',
    start_time: new Date(),
    duration_minutes: 120,
    status: 'upcoming',
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ContestSchedulerService,
        {
          provide: PrismaService,
          useValue: {
            contest: {
              findMany: jest.fn().mockResolvedValue([]),
              update: jest.fn(),
              findUnique: jest.fn().mockResolvedValue(mockContest),
            },
            contestParticipant: {
              count: jest.fn().mockResolvedValue(0),
            },
            virtualContestSession: {
              findMany: jest.fn().mockResolvedValue([]),
              update: jest.fn(),
            },
          },
        },
        {
          provide: RankingService,
          useValue: {
            finalizeVirtualRanking: jest.fn(),
          },
        },
        {
          provide: RatingService,
          useValue: {},
        },
        {
          provide: 'BullMQ_contest',
          useValue: {
            add: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<ContestSchedulerService>(ContestSchedulerService);
    prisma = module.get(PrismaService);
    _rankingService = module.get(RankingService);
    contestQueue = module.get('BullMQ_contest');
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('updateContestStatuses', () => {
    it('should update contest statuses', async () => {
      (prisma.contest.findMany as jest.Mock).mockResolvedValue([]);

      await service.updateContestStatuses();

      expect(prisma.contest.findMany).toHaveBeenCalled();
    });
  });

  describe('manuallyFinalizeContest', () => {
    it('should finalize a running contest', async () => {
      (prisma.contest.update as jest.Mock).mockResolvedValue(
        mockContest as never,
      );
      contestQueue.add.mockResolvedValue('job' as never);

      await service.manuallyFinalizeContest('contest-123');

      expect(prisma.contest.update).toHaveBeenCalledWith({
        where: { id: 'contest-123' },
        data: { status: 'finished' },
      });
      expect(contestQueue.add).toHaveBeenCalled();
    });

    it('should throw error for non-existent contest', async () => {
      (prisma.contest.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        service.manuallyFinalizeContest('non-existent'),
      ).rejects.toThrow('Contest not found');
    });
  });
});
