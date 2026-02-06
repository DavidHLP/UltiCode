import { Test, TestingModule } from '@nestjs/testing';
import { ContestService } from './contest.service';
import { PrismaService } from '../prisma.service';
import { I18nService } from '../i18n/i18n.service';
import { RankingService } from './ranking.service';
import { NotFoundException } from '@nestjs/common';
import { ContestTimingService } from './services/contest-timing.service';
import { ContestQueryService } from './services/contest-query.service';
import { ContestParticipationService } from './services/contest-participation.service';
import { ContestVirtualService } from './services/contest-virtual.service';
import { ContestAdminService } from './services/contest-admin.service';

describe('ContestService', () => {
  let service: ContestService;
  let queryService: jest.Mocked<ContestQueryService>;
  let participationService: jest.Mocked<ContestParticipationService>;
  let adminService: jest.Mocked<ContestAdminService>;

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
            getContestRanking: jest.fn().mockResolvedValue({
              items: [],
              total: 0,
              page: 1,
              limit: 50,
              totalPages: 0,
            }),
          },
        },
        {
          provide: ContestTimingService,
          useValue: {
            withTimingFields: jest
              .fn()
              .mockImplementation((contest) => contest),
            applyContestTranslations: jest
              .fn()
              .mockResolvedValue([mockContest] as never),
          },
        },
        {
          provide: ContestQueryService,
          useValue: {
            findAll: jest.fn().mockResolvedValue({
              items: [mockContest],
              total: 1,
              page: 1,
              limit: 10,
            }),
            findOne: jest.fn().mockResolvedValue(mockContest),
            findUpcoming: jest.fn().mockResolvedValue([mockContest]),
            findRunning: jest.fn().mockResolvedValue([mockContest]),
            findPast: jest.fn().mockResolvedValue({
              data: [mockContest],
              total: 1,
              page: 1,
              limit: 10,
            }),
            getStats: jest.fn().mockResolvedValue({
              total_contests: 10,
              total_participants: 100,
            }),
          },
        },
        {
          provide: ContestParticipationService,
          useValue: {
            registerForContest: jest.fn().mockResolvedValue(undefined),
            unregisterFromContest: jest.fn().mockResolvedValue(undefined),
            getParticipationStatus: jest.fn().mockResolvedValue({
              isRegistered: false,
              status: null,
              participantId: null,
              virtualSessionId: null,
              startedAt: null,
              finishedAt: null,
              totalScore: 0,
              totalPenalty: 0,
            }),
            getUserContests: jest.fn().mockResolvedValue({
              participants: [{ ...mockContest, status: 'REGISTERED' }],
              statusMap: {},
            }),
          },
        },
        {
          provide: ContestVirtualService,
          useValue: {
            startVirtualContest: jest.fn().mockResolvedValue(mockContest),
            getVirtualSession: jest.fn().mockResolvedValue(mockContest),
            finishVirtualContest: jest.fn().mockResolvedValue(undefined),
          },
        },
        {
          provide: ContestAdminService,
          useValue: {
            createContest: jest.fn().mockResolvedValue(mockContest),
            updateContest: jest.fn().mockResolvedValue(mockContest),
            deleteContest: jest.fn().mockResolvedValue(undefined),
            updateContestStatus: jest.fn().mockResolvedValue(mockContest),
          },
        },
      ],
    }).compile();

    service = module.get<ContestService>(ContestService);
    queryService = module.get(ContestQueryService);
    participationService = module.get(ContestParticipationService);
    adminService = module.get(ContestAdminService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should delegate to ContestQueryService', async () => {
      const mockResult = {
        items: [mockContest],
        total: 1,
        page: 1,
        limit: 10,
      };

      (queryService.findAll as jest.Mock).mockResolvedValue(mockResult);

      const result = await service.findAll();

      expect(queryService.findAll).toHaveBeenCalledWith(undefined, 'zh-CN');
      expect(result).toEqual(mockResult);
    });
  });

  describe('findOne', () => {
    it('should delegate to ContestQueryService', async () => {
      (queryService.findOne as jest.Mock).mockResolvedValue(mockContest);

      const result = await service.findOne('contest-123');

      expect(queryService.findOne).toHaveBeenCalledWith('contest-123', 'zh-CN');
      expect(result).toEqual(mockContest);
    });

    it('should throw NotFoundException when contest not found', async () => {
      (queryService.findOne as jest.Mock).mockResolvedValue(null);

      await expect(service.findOne('non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('findUpcoming', () => {
    it('should delegate to ContestQueryService', async () => {
      (queryService.findUpcoming as jest.Mock).mockResolvedValue([mockContest]);

      const result = await service.findUpcoming();

      expect(queryService.findUpcoming).toHaveBeenCalledWith('zh-CN');
      expect(result).toEqual([mockContest]);
    });
  });

  describe('findRunning', () => {
    it('should delegate to ContestQueryService', async () => {
      (queryService.findRunning as jest.Mock).mockResolvedValue([mockContest]);

      const result = await service.findRunning();

      expect(queryService.findRunning).toHaveBeenCalledWith('zh-CN');
      expect(result).toEqual([mockContest]);
    });
  });

  describe('getStats', () => {
    it('should delegate to ContestQueryService', async () => {
      const mockStats = {
        total_contests: 10,
        total_participants: 100,
      };

      (queryService.getStats as jest.Mock).mockResolvedValue(mockStats);

      const result = await service.getStats();

      expect(result).toEqual(mockStats);
    });
  });

  describe('registerForContest', () => {
    it('should delegate to ContestParticipationService', async () => {
      await service.registerForContest('contest-123', 'user-123');

      expect(participationService.registerForContest).toHaveBeenCalledWith(
        'contest-123',
        'user-123',
      );
    });
  });

  describe('createContest', () => {
    it('should delegate to ContestAdminService', async () => {
      const createDto = {
        title: 'New Contest',
        slug: 'new-contest',
        contest_type: 'weekly' as const,
        start_time: new Date().toISOString(),
        duration_minutes: 120,
        is_rated: true,
        problems: [],
      };

      (adminService.createContest as jest.Mock).mockResolvedValue(mockContest);

      const result = await service.createContest(createDto, 'user-123');

      expect(adminService.createContest).toHaveBeenCalledWith(
        createDto,
        'user-123',
      );
      expect(result).toEqual(mockContest);
    });
  });
});
