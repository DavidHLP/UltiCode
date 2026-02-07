import { Test, TestingModule } from '@nestjs/testing';
import { SubmissionService } from './submission.service';
import { PrismaService } from '../prisma.service';
import { JudgeService } from './judge.service';
import { I18nService } from '../i18n/i18n.service';
import { NotFoundException } from '@nestjs/common';
import { getQueueToken } from '@nestjs/bullmq';
import { SubmissionCrudService } from './services/submission-crud.service';
import { SubmissionQueryService } from './services/submission-query.service';
import { SubmissionExecutionService } from './services/submission-execution.service';

describe('SubmissionService', () => {
  let service: SubmissionService;
  let crudService: jest.Mocked<SubmissionCrudService>;
  let queryService: jest.Mocked<SubmissionQueryService>;
  let executionService: jest.Mocked<SubmissionExecutionService>;

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
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SubmissionService,
        {
          provide: PrismaService,
          useValue: {},
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
            translateEntities: jest
              .fn()
              .mockImplementation((_entityType, entities, _locale) =>
                Promise.resolve(entities),
              ),
            translateEntity: jest
              .fn()
              .mockImplementation((_entityType, entity, _locale) =>
                Promise.resolve(entity),
              ),
          },
        },
        {
          provide: SubmissionCrudService,
          useValue: {
            create: jest.fn().mockResolvedValue(mockSubmission as any),
            updateSubmissionAfterJudging: jest
              .fn()
              .mockResolvedValue(mockSubmission as any),
          },
        },
        {
          provide: SubmissionQueryService,
          useValue: {
            findAll: jest.fn().mockResolvedValue([mockSubmission as any]),
            findBest: jest.fn().mockResolvedValue(mockSubmission as any),
            findOne: jest.fn().mockResolvedValue(mockSubmission as any),
            getProblemStatusMap: jest.fn().mockResolvedValue(new Map()),
            getDailyActivity: jest.fn().mockResolvedValue([]),
            getStatusDefinitions: jest.fn().mockResolvedValue([]),
            getLatestRunResult: jest.fn().mockResolvedValue(null),
            decorateSubmission: jest.fn().mockReturnValue(mockSubmission),
          },
        },
        {
          provide: SubmissionExecutionService,
          useValue: {
            run: jest.fn().mockResolvedValue({
              id: 'run-123',
              cases: [],
              passed_cases: 0,
              total_cases: 0,
            } as any),
          },
        },
      ],
    }).compile();

    service = module.get<SubmissionService>(SubmissionService);
    crudService = module.get(SubmissionCrudService);
    queryService = module.get(SubmissionQueryService);
    executionService = module.get(SubmissionExecutionService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return array of submissions', async () => {
      queryService.findAll.mockResolvedValue([mockSubmission] as any);

      const result = await service.findAll();

      expect(result).toHaveLength(1);
      expect(queryService.findAll).toHaveBeenCalled();
    });
  });

  describe('findBest', () => {
    it('should return best submission', async () => {
      queryService.findBest.mockResolvedValue(mockSubmission as any);

      const result = await service.findBest(1, 'user-123');

      expect(result).toBeDefined();
      expect(queryService.findBest).toHaveBeenCalledWith(1, 'user-123');
    });
  });

  describe('getProblemStatusMap', () => {
    it('should return problem status map', async () => {
      const mockMap = new Map([
        [1, { status: 'solved' as const, completed_time: new Date() }],
      ]);
      queryService.getProblemStatusMap.mockResolvedValue(mockMap as any);

      const result = await service.getProblemStatusMap('user-123');

      expect(result).toBeInstanceOf(Map);
      expect(result.has(1)).toBe(true);
    });
  });

  describe('getDailyActivity', () => {
    it('should return daily activity', async () => {
      queryService.getDailyActivity.mockResolvedValue(['2026-01-01']);

      const result = await service.getDailyActivity('user-123', 2026);

      expect(result).toEqual(['2026-01-01']);
    });
  });

  describe('getStatusDefinitions', () => {
    it('should return status definitions', async () => {
      queryService.getStatusDefinitions.mockResolvedValue([] as any);

      const result = await service.getStatusDefinitions();

      expect(Array.isArray(result)).toBe(true);
    });
  });

  describe('findOne', () => {
    it('should return a submission by id', async () => {
      queryService.findOne.mockResolvedValue(mockSubmission as any);

      const result = await service.findOne('sub-123');

      expect(result).toBeDefined();
      expect(queryService.findOne).toHaveBeenCalledWith('sub-123', undefined);
    });

    it('should throw NotFoundException for non-existent submission', async () => {
      queryService.findOne.mockRejectedValue(
        new NotFoundException('Submission not found'),
      );

      await expect(service.findOne('non-existent')).rejects.toThrow(
        NotFoundException,
      );
    });
  });

  describe('getLatestRunResult', () => {
    it('should return latest run result', async () => {
      queryService.getLatestRunResult.mockResolvedValue({
        id: 'run-123',
        submissionId: 'sub-123',
        problemId: 1,
        userId: 'user-123',
        verdict: 'Accepted',
        runtime: '100 ms',
        memory: '50 MB',
        cases: [],
        passed_cases: 0,
        total_cases: 0,
        error_message: null,
      } as any);

      const result = await service.getLatestRunResult(1, 'user-123');

      expect(result).toBeDefined();
      expect(queryService.getLatestRunResult).toHaveBeenCalledWith(
        1,
        'user-123',
      );
    });
  });

  describe('create', () => {
    it('should create a new submission', async () => {
      crudService.create.mockResolvedValue(mockSubmission as any);

      const result = await service.create('user-123', 1, {
        language: 'javascript',
        code: 'function test() {}',
      });

      expect(result).toBeDefined();
      expect(crudService.create).toHaveBeenCalledWith('user-123', 1, {
        language: 'javascript',
        code: 'function test() {}',
      });
    });
  });

  describe('updateSubmissionAfterJudging', () => {
    it('should update submission after judging', async () => {
      const updatedSubmission = { ...mockSubmission, status: 'Accepted' };
      crudService.updateSubmissionAfterJudging.mockResolvedValue(
        updatedSubmission as any,
      );
      queryService.decorateSubmission.mockReturnValue(updatedSubmission as any);

      const result = await service.updateSubmissionAfterJudging('sub-123', {
        verdict: 'Accepted',
        runtime: 100,
        memory: 50,
        cases: [],
      });

      expect(result).toBeDefined();
      expect(crudService.updateSubmissionAfterJudging).toHaveBeenCalled();
    });
  });

  describe('run', () => {
    it('should run code', async () => {
      executionService.run.mockResolvedValue({
        id: 'run-123',
        submissionId: 'run-123',
        problemId: 1,
        userId: 'user-123',
        verdict: 'Accepted',
        runtime: '100 ms',
        memory: '50 MB',
        cases: [],
        passed_cases: 0,
        total_cases: 0,
        error_message: null,
      } as any);

      const result = await service.run(1, {
        language: 'javascript',
        code: 'function test() {}',
      });

      expect(result).toBeDefined();
      expect(executionService.run).toHaveBeenCalled();
    });
  });
});
