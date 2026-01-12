import { Test, TestingModule } from '@nestjs/testing';
import { ContestService } from './contest.service';
import { PrismaService } from '../prisma.service';
import { I18nService } from '../i18n/i18n.service';
import { RankingService } from './ranking.service';
import { NotFoundException } from '@nestjs/common';

describe('ContestService', () => {
  let service: ContestService;
  let prisma: jest.Mocked<PrismaService>;
  let _i18nService: jest.Mocked<I18nService>;
  let _rankingService: jest.Mocked<RankingService>;

  const mockContest = {
    id: 'contest-123',
    title: 'Weekly Contest',
    slug: 'weekly-contest-1',
    start_time: new Date('2026-01-15T10:00:00Z'),
    duration_minutes: 120,
    status: 'upcoming',
    is_visible: true,
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ContestService,
        {
          provide: PrismaService,
          useValue: {
            contest: {
              findMany: jest.fn(),
              findUnique: jest.fn(),
              count: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
              delete: jest.fn(),
            },
            contestParticipant: {
              findFirst: jest.fn(),
              findMany: jest.fn(),
              create: jest.fn(),
              delete: jest.fn(),
              updateMany: jest.fn(),
              count: jest.fn(),
            },
            virtualContestSession: {
              findFirst: jest.fn(),
              create: jest.fn(),
              findUnique: jest.fn(),
            },
            globalRanking: {
              findMany: jest.fn(),
            },
            contestRanking: {
              findMany: jest.fn(),
            },
            $transaction: jest.fn((callback) => callback({})),
          },
        },
        {
          provide: I18nService,
          useValue: {
            getBatchTranslations: jest.fn().mockResolvedValue(new Map()),
            getTranslations: jest.fn().mockResolvedValue(new Map()),
            applyTranslations: jest.fn().mockImplementation((obj) => obj),
          },
        },
        {
          provide: RankingService,
          useValue: {
            finalizeVirtualRanking: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<ContestService>(ContestService);
    prisma = module.get(PrismaService);
    _i18nService = module.get(I18nService);
    _rankingService = module.get(RankingService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return paginated contests', async () => {
      (prisma.contest.findMany as jest.Mock).mockResolvedValue([
        mockContest,
      ] as never);
      (prisma.contest.count as jest.Mock).mockResolvedValue(1);

      const result = await service.findAll();

      expect(result).toHaveProperty('items');
      expect(result).toHaveProperty('total');
      expect(result.items).toHaveLength(1);
    });
  });

  describe('findOne', () => {
    it('should return a contest with problems', async () => {
      const contestWithProblems = {
        ...mockContest,
        problems: [],
      };

      (prisma.contest.findUnique as jest.Mock).mockResolvedValue(
        contestWithProblems as never,
      );

      const result = await service.findOne('contest-123');

      expect(result).toBeDefined();
      expect(prisma.contest.findUnique).toHaveBeenCalledWith({
        where: { id: 'contest-123' },
        include: expect.any(Object),
      });
    });

    it('should throw NotFoundException when contest not found', async () => {
      (prisma.contest.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(service.findOne('non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('findUpcoming', () => {
    it('should return upcoming contests', async () => {
      (prisma.contest.findMany as jest.Mock).mockResolvedValue([
        mockContest,
      ] as never);

      const result = await service.findUpcoming();

      expect(result).toHaveLength(1);
      expect(prisma.contest.findMany).toHaveBeenCalledWith({
        where: { status: 'upcoming', is_visible: true },
        orderBy: { start_time: 'asc' },
      });
    });
  });

  describe('findRunning', () => {
    it('should return running contests', async () => {
      const runningContest = { ...mockContest, status: 'running' };
      (prisma.contest.findMany as jest.Mock).mockResolvedValue([
        runningContest,
      ] as never);

      const result = await service.findRunning();

      expect(result).toHaveLength(1);
      expect(prisma.contest.findMany).toHaveBeenCalledWith({
        where: { status: 'running', is_visible: true },
        orderBy: { start_time: 'asc' },
      });
    });
  });

  describe('getStats', () => {
    it('should return contest statistics', async () => {
      (prisma.contest.count as jest.Mock).mockResolvedValue(10);
      (prisma.contestParticipant.count as jest.Mock).mockResolvedValue(100);

      const result = await service.getStats();

      expect(result).toEqual({
        total_contests: 10,
        total_participants: 100,
      });
    });
  });

  describe('registerForContest', () => {
    it('should register user for contest', async () => {
      (prisma.contest.findUnique as jest.Mock).mockResolvedValue(
        mockContest as never,
      );
      (prisma.contestParticipant.findFirst as jest.Mock).mockResolvedValue(
        null,
      );
      (prisma.contestParticipant.create as jest.Mock).mockResolvedValue(
        {} as never,
      );
      (prisma.contest.update as jest.Mock).mockResolvedValue({} as never);

      await service.registerForContest('contest-123', 'user-123');

      expect(prisma.contestParticipant.create).toHaveBeenCalled();
    });
  });

  describe('createContest', () => {
    it('should create a new contest', async () => {
      const createDto = {
        title: 'New Contest',
        slug: 'new-contest',
        contest_type: 'weekly' as const,
        start_time: new Date().toISOString(),
        duration_minutes: 120,
        is_rated: true,
        problems: [],
      };

      (prisma.contest.create as jest.Mock).mockResolvedValue(
        mockContest as never,
      );

      const result = await service.createContest(createDto, 'user-123');

      expect(result).toEqual(mockContest);
      expect(prisma.contest.create).toHaveBeenCalled();
    });
  });
});
