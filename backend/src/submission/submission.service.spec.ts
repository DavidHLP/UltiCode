import { Test, TestingModule } from '@nestjs/testing';
import { SubmissionService } from './submission.service';
import { PrismaService } from '../prisma.service';
import { JudgeService } from './judge.service';
import { Queue } from 'bullmq';
import { I18nService } from '../i18n/i18n.service';
import { NotFoundException } from '@nestjs/common';
import { getQueueToken } from '@nestjs/bullmq';

describe('SubmissionService', () => {
  let service: SubmissionService;
  let prisma: jest.Mocked<PrismaService>;
  let _judgeService: jest.Mocked<JudgeService>;
  let judgeQueue: jest.Mocked<Queue>;

  const mockSubmission = {
    id: 'sub-123',
    user_id: 'user-123',
    problem_id: 1,
    language: 'javascript',
    code: 'function solution(a, b) { return a + b; }',
    status: 'Accepted',
    runtime: 100,
    memory: 50,
    test_details: null,
    created_at: new Date(),
    user: {
      id: 'user-123',
      username: 'testuser',
      avatar: 'avatar.png',
    },
    problem: {
      id: 1,
      title: 'Two Sum',
      slug: 'two-sum',
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SubmissionService,
        {
          provide: PrismaService,
          useValue: {
            submission: {
              findMany: jest.fn(),
              findFirst: jest.fn(),
              findUnique: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
            },
            submissionStatus: {
              findMany: jest.fn(),
            },
            problemExample: {
              findMany: jest.fn(),
            },
          },
        },
        {
          provide: JudgeService,
          useValue: {
            judge: jest.fn(),
          },
        },
        {
          provide: getQueueToken('judge_queue'),
          useValue: {
            add: jest.fn(),
          },
        },
        {
          provide: I18nService,
          useValue: {
            getBatchTranslations: jest.fn().mockResolvedValue(new Map()),
            applyTranslations: jest.fn().mockImplementation((obj) => obj),
          },
        },
      ],
    }).compile();

    service = module.get<SubmissionService>(SubmissionService);
    prisma = module.get(PrismaService);
    _judgeService = module.get(JudgeService);
    judgeQueue = module.get(getQueueToken('judge_queue'));
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return array of submissions', async () => {
      (prisma.submission.findMany as jest.Mock).mockResolvedValue([
        mockSubmission,
      ] as never);

      const result = await service.findAll();

      expect(result).toHaveLength(1);
      expect(prisma.submission.findMany).toHaveBeenCalled();
    });
  });

  describe('findOne', () => {
    it('should return a submission by id', async () => {
      (prisma.submission.findUnique as jest.Mock).mockResolvedValue(
        mockSubmission as never,
      );

      const result = await service.findOne('sub-123');

      expect(result).toBeDefined();
      expect(prisma.submission.findUnique).toHaveBeenCalledWith({
        where: { id: 'sub-123' },
        include: expect.any(Object),
      });
    });

    it('should throw NotFoundException for non-existent submission', async () => {
      (prisma.submission.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(service.findOne('non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('getProblemStatusMap', () => {
    it('should return problem status map', async () => {
      const mockSubmissions = [
        {
          problem_id: BigInt(1),
          status: 'Accepted',
          created_at: new Date(),
        },
        {
          problem_id: BigInt(2),
          status: 'Wrong Answer',
          created_at: new Date(),
        },
      ];

      (prisma.submission.findMany as jest.Mock).mockResolvedValue(
        mockSubmissions as never,
      );

      const result = await service.getProblemStatusMap('user-123');

      expect(result).toBeInstanceOf(Map);
      expect(result.has(1)).toBe(true);
      expect(result.get(1)?.status).toBe('solved');
    });
  });

  describe('create', () => {
    it('should create a new submission and add to queue', async () => {
      (prisma.submission.create as jest.Mock).mockResolvedValue(
        mockSubmission as never,
      );
      judgeQueue.add.mockResolvedValue('job' as never);

      const result = await service.create('user-123', 1, {
        language: 'javascript',
        code: 'function test() {}',
      });

      expect(result).toBeDefined();
      expect(prisma.submission.create).toHaveBeenCalled();
      expect(judgeQueue.add).toHaveBeenCalled();
    });
  });

  describe('getStatusDefinitions', () => {
    it('should return status definitions', async () => {
      (prisma.submissionStatus.findMany as jest.Mock).mockResolvedValue([]);

      const result = await service.getStatusDefinitions();

      expect(Array.isArray(result)).toBe(true);
    });
  });
});
