import { Test, TestingModule } from '@nestjs/testing';
import { SubmissionController } from './submission.controller';
import { SubmissionService } from './submission.service';
import { ContestSubmissionService } from './contest-submission.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { AuthGuard } from '../auth/auth.guard';

describe('SubmissionController', () => {
  let submissionController: SubmissionController;
  let submissionService: jest.Mocked<SubmissionService>;

  const mockSubmission = {
    id: 'sub-123',
    user_id: 'user-123',
    problem_id: 1,
    status: 'Accepted',
    runtime: 100,
    memory: 50,
  };

  const mockReq = {
    user: { id: 'user-123' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [SubmissionController],
      providers: [
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn(),
            verify: jest.fn(),
          },
        },
        {
          provide: Reflector,
          useValue: {
            get: jest.fn(),
            getAll: jest.fn(),
          },
        },
        {
          provide: ModuleRef,
          useValue: {
            get: jest.fn(),
          },
        },
        {
          provide: SubmissionService,
          useValue: {
            findAll: jest.fn(),
            findOne: jest.fn(),
            findBest: jest.fn(),
            getProblemStatusMap: jest.fn(),
            getDailyActivity: jest.fn(),
            getStatusDefinitions: jest.fn(),
            create: jest.fn(),
            run: jest.fn(),
          },
        },
        {
          provide: ContestSubmissionService,
          useValue: {
            getContestSubmissions: jest.fn(),
            submitInContest: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    submissionController =
      module.get<SubmissionController>(SubmissionController);
    submissionService = module.get(SubmissionService);
  });

  it('should be defined', () => {
    expect(submissionController).toBeDefined();
  });

  describe('findAllByUser', () => {
    it('should return all submissions for user', async () => {
      submissionService.findAll.mockResolvedValue([mockSubmission] as never);

      const result = await submissionController.findAllByUser(
        {},
        mockReq as any,
      );

      expect(result).toEqual([mockSubmission]);
    });

    it('should return best submission when best query param is true', async () => {
      submissionService.findBest.mockResolvedValue(mockSubmission as never);

      const result = await submissionController.findAllByUser(
        { problemId: 1, best: 'true' },
        mockReq as any,
      );

      expect(result).toEqual(mockSubmission);
      expect(submissionService.findBest).toHaveBeenCalledWith(1, 'user-123');
    });
  });

  describe('findOne', () => {
    it('should return a submission by id', async () => {
      submissionService.findOne.mockResolvedValue(mockSubmission as never);

      const result = await submissionController.findOne(
        'sub-123',
        mockReq as any,
      );

      expect(result).toEqual(mockSubmission);
    });
  });

  describe('getStatusMap', () => {
    it('should return problem status map', async () => {
      const mockMap = new Map([
        [1, { status: 'solved' as const, completed_time: new Date() }],
      ]);
      submissionService.getProblemStatusMap.mockResolvedValue(mockMap);

      const result = await submissionController.getStatusMap(mockReq as any);

      expect(result).toBeDefined();
      expect(submissionService.getProblemStatusMap).toHaveBeenCalledWith(
        'user-123',
      );
    });
  });

  describe('getStatuses', () => {
    it('should return status definitions', async () => {
      const mockStatuses = [
        { key: 'Accepted', label: 'Accepted', color: 'green' },
      ];
      submissionService.getStatusDefinitions.mockResolvedValue(
        mockStatuses as never,
      );

      const result = await submissionController.getStatuses();

      expect(result).toEqual(mockStatuses);
    });
  });

  describe('getDailyActivity', () => {
    it('should return daily activity for year', async () => {
      const mockActivity = ['2026-01-01', '2026-01-02'];
      submissionService.getDailyActivity.mockResolvedValue(mockActivity);

      const result = await submissionController.getDailyActivity(
        { year: 2026 },
        mockReq as any,
      );

      expect(result).toEqual(mockActivity);
      expect(submissionService.getDailyActivity).toHaveBeenCalledWith(
        'user-123',
        2026,
      );
    });
  });
});
