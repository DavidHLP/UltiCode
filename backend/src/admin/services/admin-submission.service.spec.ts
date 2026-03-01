import { Test, TestingModule } from '@nestjs/testing';
import { AdminSubmissionService } from './admin-submission.service';
import { PrismaService } from '../../prisma.service';
import { getQueueToken } from '@nestjs/bullmq';

describe('AdminSubmissionService', () => {
  let service: AdminSubmissionService;
  let prisma: jest.Mocked<PrismaService>;
  let mockQueue: { add: jest.Mock };

  const mockPrismaService = {
    submission: {
      findMany: jest.fn(),
      count: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      groupBy: jest.fn(),
      aggregate: jest.fn(),
    },
    submissionStatus: {
      findMany: jest.fn(),
    },
  };

  beforeEach(async () => {
    mockQueue = { add: jest.fn().mockResolvedValue({ id: '1' }) };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AdminSubmissionService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: getQueueToken('judge_queue'),
          useValue: mockQueue,
        },
      ],
    }).compile();

    service = module.get<AdminSubmissionService>(AdminSubmissionService);
    prisma = module.get(PrismaService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('findAll', () => {
    it('should return paginated submissions', async () => {
      const mockSubmissions = [
        {
          id: '1',
          problem_id: BigInt(1),
          user_id: 'user-1',
          language: 'javascript',
          status: 'ACCEPTED',
          runtime: 100,
          memory: 1024,
          code: 'console.log("hello")',
          created_at: new Date(),
          user: { username: 'testuser' },
          problem: { title: 'Two Sum', slug: 'two-sum' },
        },
      ];

      mockPrismaService.submission.findMany.mockResolvedValue(mockSubmissions);
      mockPrismaService.submission.count.mockResolvedValue(1);

      const result = await service.findAll({ page: 1, limit: 20 });

      expect(result).toHaveProperty('data');
      expect(result).toHaveProperty('total');
      expect(result).toHaveProperty('page');
      expect(result).toHaveProperty('limit');
      expect(result).toHaveProperty('totalPages');
      expect(mockPrismaService.submission.findMany).toHaveBeenCalled();
    });

    it('should filter by userId', async () => {
      mockPrismaService.submission.findMany.mockResolvedValue([]);
      mockPrismaService.submission.count.mockResolvedValue(0);

      await service.findAll({ page: 1, limit: 20, userId: 'user-1' });

      const callArgs = mockPrismaService.submission.findMany.mock.calls[0][0];
      expect(callArgs.where).toHaveProperty('user_id', 'user-1');
    });

    it('should filter by problemId', async () => {
      mockPrismaService.submission.findMany.mockResolvedValue([]);
      mockPrismaService.submission.count.mockResolvedValue(0);

      await service.findAll({ page: 1, limit: 20, problemId: 123 });

      const callArgs = mockPrismaService.submission.findMany.mock.calls[0][0];
      expect(callArgs.where).toHaveProperty('problem_id');
    });

    it('should filter by status', async () => {
      mockPrismaService.submission.findMany.mockResolvedValue([]);
      mockPrismaService.submission.count.mockResolvedValue(0);

      await service.findAll({ page: 1, limit: 20, status: 'ACCEPTED' });

      const callArgs = mockPrismaService.submission.findMany.mock.calls[0][0];
      expect(callArgs.where).toHaveProperty('status', 'ACCEPTED');
    });

    it('should filter by language', async () => {
      mockPrismaService.submission.findMany.mockResolvedValue([]);
      mockPrismaService.submission.count.mockResolvedValue(0);

      await service.findAll({ page: 1, limit: 20, language: 'python' });

      const callArgs = mockPrismaService.submission.findMany.mock.calls[0][0];
      expect(callArgs.where).toHaveProperty('language', 'python');
    });

    it('should calculate total pages correctly', async () => {
      mockPrismaService.submission.findMany.mockResolvedValue([]);
      mockPrismaService.submission.count.mockResolvedValue(50);

      const result = await service.findAll({ page: 1, limit: 20 });

      expect(result.totalPages).toBe(3);
    });
  });

  describe('findOne', () => {
    it('should return submission details', async () => {
      const mockSubmission = {
        id: '1',
        problem_id: BigInt(1),
        user_id: 'user-1',
        language: 'javascript',
        status: 'ACCEPTED',
        runtime: 100,
        memory: 1024,
        code: 'console.log("hello")',
        created_at: new Date(),
        user: { id: 'user-1', username: 'testuser', email: 'test@example.com' },
        problem: { id: BigInt(1), title: 'Two Sum', slug: 'two-sum' },
      };

      mockPrismaService.submission.findUnique.mockResolvedValue(mockSubmission);

      const result = await service.findOne('1');

      expect(result).toBeDefined();
      expect(mockPrismaService.submission.findUnique).toHaveBeenCalledWith({
        where: { id: '1' },
        select: expect.any(Object),
      });
    });

    it('should return null if submission not found', async () => {
      mockPrismaService.submission.findUnique.mockResolvedValue(null);

      const result = await service.findOne('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('getStatistics', () => {
    it('should return submission statistics', async () => {
      // Mock the 5 calls in Promise.all: count, groupBy (status), groupBy (language), count (24h), count (pending)
      mockPrismaService.submission.count
        .mockResolvedValueOnce(100) // total count
        .mockResolvedValueOnce(50) // last24h count
        .mockResolvedValueOnce(10); // pending count

      mockPrismaService.submission.groupBy
        .mockResolvedValueOnce([
          { status: 'ACCEPTED', _count: { id: 60 } },
          { status: 'WRONG_ANSWER', _count: { id: 40 } },
        ])
        .mockResolvedValueOnce([
          { language: 'javascript', _count: { id: 50 } },
          { language: 'python', _count: { id: 30 } },
        ]);

      const result = await service.getStatistics();

      expect(result).toBeDefined();
      expect(result).toHaveProperty('total');
      expect(result).toHaveProperty('last24h');
      expect(result).toHaveProperty('byStatus');
      expect(result).toHaveProperty('byLanguage');
      expect(result.total).toBe(100);
      expect(result.byStatus).toHaveLength(2);
      expect(result.byLanguage).toHaveLength(2);
    });
  });

  describe('getStatuses', () => {
    it('should return available status options', async () => {
      mockPrismaService.submissionStatus.findMany.mockResolvedValue([
        { key: 'ACCEPTED', label: 'Accepted', category: 'success' },
        { key: 'WRONG_ANSWER', label: 'Wrong Answer', category: 'error' },
      ]);

      const result = await service.getStatuses();

      expect(Array.isArray(result)).toBe(true);
      expect(result.length).toBeGreaterThan(0);
    });
  });

  describe('getLanguages', () => {
    it('should return available language options', async () => {
      mockPrismaService.submission.groupBy.mockResolvedValue([
        { language: 'javascript', _count: { id: 50 } },
        { language: 'python', _count: { id: 30 } },
      ]);

      const result = await service.getLanguages();

      expect(Array.isArray(result)).toBe(true);
    });
  });

  describe('service initialization', () => {
    it('should be defined', () => {
      expect(service).toBeDefined();
    });

    it('should have prisma service', () => {
      expect(prisma).toBeDefined();
    });
  });
});
