import { Test, TestingModule } from '@nestjs/testing';
import { ProblemController } from './problem.controller';
import { ProblemService } from './problem.service';
import { SubmissionService } from '../submission/submission.service';
import { Problem } from './problem.entity';
import { FindAllProblemsQueryDto, ProblemParamsDto } from './dto';
import { JwtService } from '@nestjs/jwt';
import { Reflector } from '@nestjs/core';
import { ModuleRef } from '@nestjs/core';
import { UserService } from '../user/user.service';
import { TokenBlacklistService } from '../auth/token-blacklist.service';
import { AuthGuard } from '../auth/auth.guard';
import { PaginatedResult } from '../contest/dto/ranking.dto';

describe('ProblemController', () => {
  let controller: ProblemController;
  let problemService: jest.Mocked<ProblemService>;
  let submissionService: jest.Mocked<SubmissionService>;

  const mockProblem = {
    id: 1,
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'Easy',
    is_premium: false,
    acceptance_rate: 0.5,
    status: 'todo',
    has_solution: false,
    completed_time: null,
    tagRelations: [],
    detail: { id: 'detail-1' } as any,
    examples: [],
    languages: [],
  } as Problem;

  const mockRequest = {
    user: { id: 'test-user-id', role: 'USER' },
  } as any;

  const mockPaginatedResult: PaginatedResult<Problem> = {
    items: [mockProblem],
    total: 1,
    page: 1,
    limit: 20,
    totalPages: 1,
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ProblemController],
      providers: [
        {
          provide: ProblemService,
          useValue: {
            findAll: jest.fn(),
            findOne: jest.fn(),
            findOneWithPremiumCheck: jest.fn(),
            getRandom: jest.fn(),
            findAdjacent: jest.fn(),
          },
        },
        {
          provide: SubmissionService,
          useValue: {
            getProblemStatusMap: jest.fn(),
            getLatestRunResult: jest.fn(),
          },
        },
        {
          provide: JwtService,
          useValue: {
            verifyAsync: jest.fn().mockResolvedValue({ sub: 'test-user-id' }),
          },
        },
        {
          provide: Reflector,
          useValue: {
            getAllAndOverride: jest.fn((key, _context) => {
              // Make @Public() decorator return true
              if (key === 'isPublic') {
                return true;
              }
              return false;
            }),
          },
        },
        {
          provide: UserService,
          useValue: {
            findOne: jest.fn().mockResolvedValue({ id: 'test-user-id' }),
          },
        },
        {
          provide: TokenBlacklistService,
          useValue: {
            isBlacklisted: jest.fn().mockResolvedValue(false),
          },
        },
        {
          provide: ModuleRef,
          useValue: {
            get: jest.fn((token) => {
              if (token === UserService) {
                return {
                  findOne: jest.fn().mockResolvedValue({ id: 'test-user-id' }),
                };
              }
              if (token === TokenBlacklistService) {
                return { isBlacklisted: jest.fn().mockResolvedValue(false) };
              }
              return {};
            }),
          },
        },
        AuthGuard,
      ],
    }).compile();

    controller = module.get<ProblemController>(ProblemController);
    problemService = module.get(ProblemService);
    submissionService = module.get(SubmissionService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('findAll', () => {
    it('should return paginated result of problems', async () => {
      problemService.findAll.mockResolvedValue(mockPaginatedResult);
      submissionService.getProblemStatusMap.mockResolvedValue(new Map());

      const query = new FindAllProblemsQueryDto();
      const result = await controller.findAll(query);

      expect(result).toEqual(mockPaginatedResult);
      expect(problemService.findAll).toHaveBeenCalled();
    });
  });

  describe('getRandom', () => {
    it('should return a random problem', async () => {
      problemService.getRandom.mockResolvedValue(mockProblem);

      const result = await controller.getRandom();

      expect(result).toEqual(mockProblem);
      expect(problemService.getRandom).toHaveBeenCalled();
    });
  });

  describe('findOne', () => {
    it('should return a problem by id', async () => {
      problemService.findOneWithPremiumCheck.mockResolvedValue(mockProblem);
      submissionService.getProblemStatusMap.mockResolvedValue(
        new Map([[1, { status: 'todo', completed_time: null }]]),
      );

      const query = new ProblemParamsDto();
      const result = await controller.findOne(1, query, mockRequest);

      expect(result).toBeDefined();
      expect(problemService.findOneWithPremiumCheck).toHaveBeenCalledWith(
        '1',
        'test-user-id',
        'USER',
        undefined,
      );
    });
  });

  describe('getProblemResults', () => {
    it('should return problem results', async () => {
      const mockResults = {
        id: 'run-1',
        submissionId: 'sub-1',
        problemId: 1,
        userId: 'user-123',
        verdict: 'Accepted',
        runtime: '100 ms',
        memory: '50 MB',
        cases: [],
        passed_cases: 0,
        total_cases: 0,
        error_message: null,
      };

      submissionService.getLatestRunResult.mockResolvedValue(mockResults);

      const query = new ProblemParamsDto();
      const result = await controller.getProblemResults(1, query);

      expect(result).toEqual(mockResults);
      expect(submissionService.getLatestRunResult).toHaveBeenCalledWith(
        1,
        undefined,
      );
    });

    it('should return problem results with valid id', async () => {
      const query = new ProblemParamsDto();
      const mockResults = {
        id: 'run-2',
        submissionId: 'sub-2',
        problemId: 2,
        userId: 'user-456',
        verdict: 'Wrong Answer',
        runtime: '150 ms',
        memory: '60 MB',
        cases: [],
        passed_cases: 0,
        total_cases: 0,
        error_message: null,
      };
      submissionService.getLatestRunResult.mockResolvedValue(mockResults);

      const result = await controller.getProblemResults(2, query);
      expect(result).toEqual(mockResults);
    });
  });

  describe('getAdjacent', () => {
    it('should return adjacent problems', async () => {
      const adjacent = {
        prev: 'prev-problem',
        next: 'next-problem',
      };

      problemService.findAdjacent.mockResolvedValue(adjacent);

      const result = await controller.getAdjacent(2);

      expect(result).toEqual(adjacent);
      expect(problemService.findAdjacent).toHaveBeenCalledWith(2);
    });
  });
});
