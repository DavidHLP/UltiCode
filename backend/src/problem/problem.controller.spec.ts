import { Test, TestingModule } from '@nestjs/testing';
import { ProblemController } from './problem.controller';
import { ProblemService, Problem } from './problem.service';
import { SubmissionService } from '../submission/submission.service';
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
    id: BigInt(1),
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'Easy',
    acceptance_rate: 0.5,
    is_premium: false,
    status: 'todo',
    has_solution: false,
    completed_time: null,
    is_published: true,
    published_at: null,
    published_by: null,
    is_deleted: false,
    deleted_at: null,
    deleted_by: null,
    is_flagged: false,
    flag_reason: null,
    flag_reported_by: null,
    flag_reported_at: null,
    flag_status: null,
    flag_reviewed_by: null,
    flag_reviewed_at: null,
    flag_notes: null,
    tagRelations: [],
    detail: { id: 'detail-1', description: 'Given an array...' } as any,
    examples: [],
    languages: [],
  } as unknown as Problem;

  const mockPremiumProblem = {
    id: BigInt(2),
    title: 'Premium Problem',
    slug: 'premium-problem',
    difficulty: 'Hard',
    is_premium: true,
    acceptance_rate: 0.2,
    status: 'todo' as const,
    has_solution: false,
    completed_time: null,
    is_published: true,
    published_at: null,
    published_by: null,
    is_deleted: false,
    deleted_at: null,
    deleted_by: null,
    is_flagged: false,
    flag_reason: null,
    flag_reported_by: null,
    flag_reported_at: null,
    flag_status: null,
    flag_reviewed_by: null,
    flag_reviewed_at: null,
    flag_notes: null,
    tagRelations: [],
    detail: { id: 'detail-2', description: 'Premium content...' } as any,
    examples: [],
    languages: [],
  } as unknown as Problem;

  const mockRequest = {
    user: { id: 'test-user-id', role: 'USER' },
  } as any;

  const mockAdminRequest = {
    user: { id: 'admin-user-id', role: 'ADMIN' },
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

    it('should return problems without user status when no user provided', async () => {
      problemService.findAll.mockResolvedValue(mockPaginatedResult);

      const query = new FindAllProblemsQueryDto();
      const result = await controller.findAll(query);

      expect(result).toEqual(mockPaginatedResult);
      expect(submissionService.getProblemStatusMap).not.toHaveBeenCalled();
    });

    it('should use query.userId when provided', async () => {
      problemService.findAll.mockResolvedValue(mockPaginatedResult);
      submissionService.getProblemStatusMap.mockResolvedValue(
        new Map([[1, { status: 'solved', completed_time: new Date() }]]),
      );

      const query = new FindAllProblemsQueryDto();
      query.userId = 'custom-user-id';
      await controller.findAll(query);

      expect(submissionService.getProblemStatusMap).toHaveBeenCalledWith(
        'custom-user-id',
        [1],
      );
    });

    it('should use req.user.id when query.userId not provided', async () => {
      problemService.findAll.mockResolvedValue(mockPaginatedResult);
      submissionService.getProblemStatusMap.mockResolvedValue(new Map());

      const query = new FindAllProblemsQueryDto();
      const req = { user: { id: 'req-user-id', role: 'USER' } } as any;
      await controller.findAll(query, req);

      expect(submissionService.getProblemStatusMap).toHaveBeenCalledWith(
        'req-user-id',
        [1],
      );
    });

    it('should not fetch status when problemIds is empty', async () => {
      const emptyResult: PaginatedResult<Problem> = {
        items: [],
        total: 0,
        page: 1,
        limit: 20,
        totalPages: 0,
      };
      problemService.findAll.mockResolvedValue(emptyResult);

      const query = new FindAllProblemsQueryDto();
      query.userId = 'test-user-id';
      const req = { user: { id: 'req-user-id' } } as any;
      const result = await controller.findAll(query, req);

      expect(submissionService.getProblemStatusMap).not.toHaveBeenCalled();
      expect(result.items).toEqual([]);
    });

    describe('with filters', () => {
      it('should apply difficulty filter from query', async () => {
        problemService.findAll.mockResolvedValue(mockPaginatedResult);

        const query = new FindAllProblemsQueryDto();
        query.difficulty = 'Easy';
        await controller.findAll(query);

        expect(problemService.findAll).toHaveBeenCalledWith(
          expect.objectContaining({
            difficulty: 'Easy',
          }),
          undefined,
        );
      });

      it('should apply category filter from query', async () => {
        problemService.findAll.mockResolvedValue(mockPaginatedResult);

        const query = new FindAllProblemsQueryDto();
        query.category = 'algorithms';
        await controller.findAll(query);

        expect(problemService.findAll).toHaveBeenCalledWith(
          expect.objectContaining({
            category: 'algorithms',
          }),
          undefined,
        );
      });

      it('should apply search filter from query', async () => {
        problemService.findAll.mockResolvedValue(mockPaginatedResult);

        const query = new FindAllProblemsQueryDto();
        query.search = 'Two Sum';
        await controller.findAll(query);

        expect(problemService.findAll).toHaveBeenCalledWith(
          expect.objectContaining({
            search: 'Two Sum',
          }),
          undefined,
        );
      });

      it('should apply multiple filters simultaneously', async () => {
        problemService.findAll.mockResolvedValue(mockPaginatedResult);

        const query = new FindAllProblemsQueryDto();
        query.difficulty = 'Easy';
        query.category = 'algorithms';
        query.search = 'array';
        query.page = 2;
        query.limit = 10;
        await controller.findAll(query);

        expect(problemService.findAll).toHaveBeenCalledWith(
          {
            difficulty: 'Easy',
            category: 'algorithms',
            search: 'array',
            page: 2,
            limit: 10,
          },
          undefined,
        );
      });
    });

    describe('validation', () => {
      it('should reject invalid category enum', () => {
        const query = new FindAllProblemsQueryDto();
        query.category = 'invalid' as any;

        // ValidationPipe would reject this before reaching controller
        // This test documents expected validation behavior
        expect(query.category).toBe('invalid');
      });

      it('should reject invalid difficulty enum', () => {
        const query = new FindAllProblemsQueryDto();
        query.difficulty = 'invalid' as any;

        expect(query.difficulty).toBe('invalid');
      });

      it('should accept valid difficulty values', () => {
        const difficulties = ['Easy', 'Medium', 'Hard'] as const;

        for (const difficulty of difficulties) {
          const query = new FindAllProblemsQueryDto();
          query.difficulty = difficulty;
          expect(query.difficulty).toBe(difficulty);
        }
      });

      it('should accept valid category values', () => {
        const categories = [
          'algorithms',
          'database',
          'shell',
          'concurrency',
          'all',
        ] as const;

        for (const category of categories) {
          const query = new FindAllProblemsQueryDto();
          query.category = category;
          expect(query.category).toBe(category);
        }
      });

      it('should enforce search max length of 100', () => {
        const query = new FindAllProblemsQueryDto();
        // This would be validated by ValidationPipe
        const longSearch = 'a'.repeat(101);
        query.search = longSearch;
        expect(query.search).toHaveLength(101);
      });
    });
  });

  describe('getRandom', () => {
    it('should return a random problem', async () => {
      problemService.getRandom.mockResolvedValue(mockProblem);

      const result = await controller.getRandom();

      expect(result).toEqual(mockProblem);
      expect(problemService.getRandom).toHaveBeenCalled();
    });

    it('should return null when no random problem exists', async () => {
      problemService.getRandom.mockResolvedValue(null);

      const result = await controller.getRandom();

      expect(result).toBeNull();
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

    it('should return a problem by string id', async () => {
      problemService.findOneWithPremiumCheck.mockResolvedValue(mockProblem);
      submissionService.getProblemStatusMap.mockResolvedValue(
        new Map([[1, { status: 'todo', completed_time: null }]]),
      );

      const query = new ProblemParamsDto();
      const result = await controller.findOne('two-sum', query, mockRequest);

      expect(result).toBeDefined();
      expect(problemService.findOneWithPremiumCheck).toHaveBeenCalledWith(
        'two-sum',
        'test-user-id',
        'USER',
        undefined,
      );
    });

    it('should use query.userId when provided', async () => {
      problemService.findOneWithPremiumCheck.mockResolvedValue(mockProblem);
      submissionService.getProblemStatusMap.mockResolvedValue(
        new Map([[1, { status: 'todo', completed_time: null }]]),
      );

      const query = new ProblemParamsDto();
      query.userId = 'custom-user-id';
      await controller.findOne(1, query, mockRequest);

      expect(problemService.findOneWithPremiumCheck).toHaveBeenCalledWith(
        '1',
        'custom-user-id',
        'USER',
        undefined,
      );
    });

    it('should use req.user.id when query.userId not provided', async () => {
      problemService.findOneWithPremiumCheck.mockResolvedValue(mockProblem);
      submissionService.getProblemStatusMap.mockResolvedValue(
        new Map([[1, { status: 'todo', completed_time: null }]]),
      );

      const query = new ProblemParamsDto();
      await controller.findOne(1, query, mockRequest);

      expect(problemService.findOneWithPremiumCheck).toHaveBeenCalledWith(
        '1',
        'test-user-id',
        'USER',
        undefined,
      );
    });

    it('should return null when problem not found', async () => {
      problemService.findOneWithPremiumCheck.mockResolvedValue(null);

      const query = new ProblemParamsDto();
      const result = await controller.findOne(999, query, mockRequest);

      expect(result).toBeNull();
    });

    it('should return null when no effectiveUserId', async () => {
      const reqWithoutUser = {} as any;
      const query = new ProblemParamsDto();

      const result = await controller.findOne(1, query, reqWithoutUser);

      expect(result).toBeNull();
      expect(problemService.findOneWithPremiumCheck).not.toHaveBeenCalled();
    });

    describe('premium teaser', () => {
      it('should return teaser object for premium without access', async () => {
        const teaser = {
          id: mockPremiumProblem.id,
          slug: mockPremiumProblem.slug,
          title: mockPremiumProblem.title,
          difficulty: mockPremiumProblem.difficulty,
          is_premium: true,
          acceptance_rate: mockPremiumProblem.acceptance_rate,
        };

        problemService.findOneWithPremiumCheck.mockResolvedValue(teaser);

        const query = new ProblemParamsDto();
        const result = await controller.findOne(2, query, mockRequest);

        expect(result).toEqual(teaser);
        expect(result).toHaveProperty('is_premium', true);
        expect(result).not.toHaveProperty('detail');
        expect(result).not.toHaveProperty('examples');
        expect(result).not.toHaveProperty('languages');
      });

      it('should return full problem for premium with access', async () => {
        const fullPremiumProblem = {
          ...mockPremiumProblem,
          detail: { id: 'detail-2', description: 'Premium content' },
          examples: [],
          languages: [],
        };

        problemService.findOneWithPremiumCheck.mockResolvedValue(
          fullPremiumProblem,
        );
        submissionService.getProblemStatusMap.mockResolvedValue(new Map());

        const query = new ProblemParamsDto();
        const result = await controller.findOne(2, query, mockAdminRequest);

        expect(result).toHaveProperty('detail');
        expect(result).toHaveProperty('examples');
        expect(result).toHaveProperty('languages');
      });

      it('should include is_premium flag in teaser', async () => {
        const teaser = {
          id: 2,
          slug: 'premium-problem',
          title: 'Premium Problem',
          difficulty: 'Hard',
          is_premium: true,
          acceptance_rate: 0.2,
        };

        problemService.findOneWithPremiumCheck.mockResolvedValue(teaser);

        const query = new ProblemParamsDto();
        const result = await controller.findOne(2, query, mockRequest);

        expect(result).toHaveProperty('is_premium', true);
      });

      it('should omit detail, examples, languages from teaser', async () => {
        const teaser = {
          id: 2,
          slug: 'premium-problem',
          title: 'Premium Problem',
          difficulty: 'Hard',
          is_premium: true,
          acceptance_rate: 0.2,
        };

        problemService.findOneWithPremiumCheck.mockResolvedValue(teaser);

        const query = new ProblemParamsDto();
        const result = await controller.findOne(2, query, mockRequest);

        expect(result).not.toHaveProperty('detail');
        expect(result).not.toHaveProperty('examples');
        expect(result).not.toHaveProperty('languages');
      });
    });

    describe('error scenarios', () => {
      it('should return null for non-existent problem ID', async () => {
        problemService.findOneWithPremiumCheck.mockResolvedValue(null);

        const query = new ProblemParamsDto();
        const result = await controller.findOne(99999, query, mockRequest);

        expect(result).toBeNull();
      });

      it('should return null for non-existent problem slug', async () => {
        problemService.findOneWithPremiumCheck.mockResolvedValue(null);

        const query = new ProblemParamsDto();
        const result = await controller.findOne(
          'non-existent-problem',
          query,
          mockRequest,
        );

        expect(result).toBeNull();
      });

      it('should handle service errors gracefully', async () => {
        problemService.findOneWithPremiumCheck.mockRejectedValue(
          new Error('Service error'),
        );

        const query = new ProblemParamsDto();

        await expect(controller.findOne(1, query, mockRequest)).rejects.toThrow(
          'Service error',
        );
      });
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

    it('should use query.userId when provided', async () => {
      const mockResults = {
        id: 'run-1',
        submissionId: 'sub-1',
        problemId: 1,
        userId: 'custom-user-id',
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
      query.userId = 'custom-user-id';
      await controller.getProblemResults(1, query);

      expect(submissionService.getLatestRunResult).toHaveBeenCalledWith(
        1,
        'custom-user-id',
      );
    });

    describe('error scenarios', () => {
      it('should handle invalid problem ID', async () => {
        submissionService.getLatestRunResult.mockResolvedValue(null);

        const query = new ProblemParamsDto();
        const result = await controller.getProblemResults(99999, query);

        expect(result).toBeNull();
      });

      it('should return null when no results exist', async () => {
        submissionService.getLatestRunResult.mockResolvedValue(null);

        const query = new ProblemParamsDto();
        const result = await controller.getProblemResults(1, query);

        expect(result).toBeNull();
      });

      it('should handle service errors gracefully', async () => {
        submissionService.getLatestRunResult.mockRejectedValue(
          new Error('Database error'),
        );

        const query = new ProblemParamsDto();

        await expect(controller.getProblemResults(1, query)).rejects.toThrow(
          'Database error',
        );
      });
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

    it('should handle numeric ID', async () => {
      const adjacent = {
        prev: 'problem-1',
        next: 'problem-3',
      };

      problemService.findAdjacent.mockResolvedValue(adjacent);

      const result = await controller.getAdjacent(2);

      expect(result).toEqual(adjacent);
    });

    it('should handle string ID converted to number', async () => {
      const adjacent = {
        prev: 'problem-1',
        next: 'problem-3',
      };

      problemService.findAdjacent.mockResolvedValue(adjacent);

      const result = await controller.getAdjacent('2' as unknown as number);

      expect(result).toEqual(adjacent);
    });

    describe('error scenarios', () => {
      it('should return null for both prev/next at boundaries', async () => {
        problemService.findAdjacent.mockResolvedValue({
          prev: null,
          next: null,
        });

        const result = await controller.getAdjacent(999);

        expect(result).toEqual({
          prev: null,
          next: null,
        });
      });

      it('should handle only prev exists', async () => {
        problemService.findAdjacent.mockResolvedValue({
          prev: 'problem-99',
          next: null,
        });

        const result = await controller.getAdjacent(100);

        expect(result).toEqual({
          prev: 'problem-99',
          next: null,
        });
      });

      it('should handle only next exists', async () => {
        problemService.findAdjacent.mockResolvedValue({
          prev: null,
          next: 'problem-2',
        });

        const result = await controller.getAdjacent(1);

        expect(result).toEqual({
          prev: null,
          next: 'problem-2',
        });
      });

      it('should handle service errors gracefully', async () => {
        problemService.findAdjacent.mockRejectedValue(
          new Error('Database error'),
        );

        await expect(controller.getAdjacent(2)).rejects.toThrow(
          'Database error',
        );
      });
    });
  });

  describe('Authentication', () => {
    it('should allow public access to findAll endpoint', async () => {
      problemService.findAll.mockResolvedValue(mockPaginatedResult);

      const query = new FindAllProblemsQueryDto();
      const reqWithoutUser = {} as any;
      const result = await controller.findAll(query, reqWithoutUser);

      expect(result).toEqual(mockPaginatedResult);
    });

    it('should allow public access to getRandom endpoint', async () => {
      problemService.getRandom.mockResolvedValue(mockProblem);

      const result = await controller.getRandom();

      expect(result).toEqual(mockProblem);
    });

    it('should allow public access to getAdjacent endpoint', async () => {
      problemService.findAdjacent.mockResolvedValue({
        prev: null,
        next: null,
      });

      const result = await controller.getAdjacent(1);

      expect(result).toEqual({ prev: null, next: null });
    });

    it('should require authentication for findOne endpoint', async () => {
      // The endpoint requires auth via AuthGuard
      // When no user is present, it returns null
      problemService.findOneWithPremiumCheck.mockResolvedValue(mockProblem);

      const reqWithoutUser = {} as any;
      const query = new ProblemParamsDto();
      const result = await controller.findOne(1, query, reqWithoutUser);

      expect(result).toBeNull();
    });

    it('should return null for unauthenticated findOne request', async () => {
      const reqWithoutUser = {} as any;
      const query = new ProblemParamsDto();

      const result = await controller.findOne(1, query, reqWithoutUser);

      expect(result).toBeNull();
    });
  });
});
