import { Test, TestingModule } from '@nestjs/testing';
import { ProblemService } from './problem.service';
import { PrismaService } from '../prisma.service';
import { I18nService } from '../i18n/i18n.service';
import { SubscriptionService } from '../subscription/subscription.service';

describe('ProblemService', () => {
  let service: ProblemService;
  let prisma: jest.Mocked<PrismaService>;
  let i18nService: jest.Mocked<I18nService>;
  let subscriptionService: jest.Mocked<SubscriptionService>;

  const mockProblem = {
    id: BigInt(1),
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'Easy',
    acceptance_rate: 0.65,
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
  };

  const mockMediumProblem = {
    ...mockProblem,
    id: BigInt(2),
    title: 'Add Two Numbers',
    slug: 'add-two-numbers',
    difficulty: 'Medium',
  };

  const mockHardProblem = {
    ...mockProblem,
    id: BigInt(3),
    title: 'Merge K Sorted Lists',
    slug: 'merge-k-sorted-lists',
    difficulty: 'Hard',
  };

  const mockPremiumProblem = {
    ...mockProblem,
    id: BigInt(4),
    title: 'Premium Problem',
    slug: 'premium-problem',
    difficulty: 'Hard',
    is_premium: true,
  };

  const mockProblems = [
    mockProblem,
    mockMediumProblem,
    mockHardProblem,
    mockPremiumProblem,
  ];

  const mockProblemWithDetail = {
    ...mockProblem,
    detail: {
      id: 'detail-1',
      problem_id: BigInt(1),
      slug: 'two-sum',
      summary: 'Given an array of integers...',
      companies: null,
      likes: 0,
      dislikes: 0,
      difficulty_rating: 1500,
      updated_at: new Date(),
      follow_up: null,
      constraints_json: ['1 <= nums.length <= 10^4'],
      hints: ['Use a hash map'],
      interactions: null,
    },
    tagRelations: [
      {
        problem_id: BigInt(1),
        tag_id: 'tag-array',
        tag: {
          id: 'tag-array',
          label: 'Array',
          slug: 'array',
          color: null,
          description: null,
          usage_count: 0,
          created_at: new Date(),
          updated_at: new Date(),
        },
      },
    ],
    languages: [
      {
        id: 'lang-python',
        problem_id: BigInt(1),
        label: 'Python',
        value: 'python3',
        style: null,
        starter_code: 'class Solution:',
      },
    ],
    examples: [
      {
        id: 'ex-1',
        problem_id: BigInt(1),
        example_order: 0,
        input_text: 'nums = [2,7,11,15], target = 9',
        output_text: '[0,1]',
        explanation: 'Because nums[0] + nums[1] == 9',
        inputs: null,
      },
    ],
  };

  const mockPrismaService = {
    problem: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      count: jest.fn(),
    },
    problemTag: {
      findMany: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProblemService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: I18nService,
          useValue: {
            getBatchTranslations: jest.fn().mockResolvedValue(new Map()),
            getTranslations: jest.fn().mockResolvedValue(new Map()),
            applyTranslations: jest
              .fn()
              .mockImplementation((obj, _trans, _fields) => obj),
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
          provide: SubscriptionService,
          useValue: {
            hasPremiumAccess: jest.fn().mockResolvedValue({
              hasAccess: false,
              subscription: null,
            }),
          },
        },
      ],
    }).compile();

    service = module.get<ProblemService>(ProblemService);
    prisma = module.get(PrismaService);
    i18nService = module.get(I18nService);
    subscriptionService = module.get(SubscriptionService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return paginated result with default page and limit', async () => {
      (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
      (prisma.problem.count as jest.Mock).mockResolvedValue(1);

      const result = await service.findAll();

      expect(result).toEqual({
        items: [mockProblem],
        total: 1,
        page: 1,
        limit: 20,
        totalPages: 1,
      });
      expect(prisma.problem.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          skip: 0,
          take: 20,
          orderBy: { id: 'asc' },
        }),
      );
    });

    it('should return paginated result with custom page and limit', async () => {
      (prisma.problem.findMany as jest.Mock).mockResolvedValue(mockProblems);
      (prisma.problem.count as jest.Mock).mockResolvedValue(5);

      const result = await service.findAll({ page: 2, limit: 10 });

      expect(result).toEqual({
        items: mockProblems,
        total: 5,
        page: 2,
        limit: 10,
        totalPages: 1,
      });
      expect(prisma.problem.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          skip: 10,
          take: 10,
        }),
      );
    });

    it('should return empty paginated result when no problems found', async () => {
      (prisma.problem.findMany as jest.Mock).mockResolvedValue([]);
      (prisma.problem.count as jest.Mock).mockResolvedValue(0);

      const result = await service.findAll();

      expect(result).toEqual({
        items: [],
        total: 0,
        page: 1,
        limit: 20,
        totalPages: 0,
      });
    });

    describe('with difficulty filter', () => {
      it('should filter by Easy difficulty', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const result = await service.findAll({ difficulty: 'Easy' });

        expect(prisma.problem.findMany).toHaveBeenCalledWith(
          expect.objectContaining({
            where: { difficulty: 'Easy' },
          }),
        );
        expect(result.items).toHaveLength(1);
      });

      it('should filter by Medium difficulty', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([
          mockMediumProblem,
        ]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const result = await service.findAll({ difficulty: 'Medium' });

        expect(prisma.problem.findMany).toHaveBeenCalledWith(
          expect.objectContaining({
            where: { difficulty: 'Medium' },
          }),
        );
        expect(result.items).toHaveLength(1);
      });

      it('should filter by Hard difficulty', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([
          mockHardProblem,
        ]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const result = await service.findAll({ difficulty: 'Hard' });

        expect(prisma.problem.findMany).toHaveBeenCalledWith(
          expect.objectContaining({
            where: { difficulty: 'Hard' },
          }),
        );
        expect(result.items).toHaveLength(1);
      });
    });

    describe('with category filter', () => {
      it('should filter by algorithms category', async () => {
        const problemWithTag = {
          ...mockProblem,
          tagRelations: [
            {
              tag_id: 'tag-1',
              problem_id: BigInt(1),
              tag: {
                id: 'tag-1',
                label: 'Algorithms',
                slug: 'algorithms',
                color: null,
                description: null,
                usage_count: 0,
                created_at: new Date(),
                updated_at: new Date(),
              },
            },
          ],
        };
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([
          problemWithTag,
        ]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);
        (prisma.problemTag.findMany as jest.Mock).mockResolvedValue([
          problemWithTag.tagRelations[0].tag,
        ]);
        (i18nService.translateEntities as jest.Mock).mockResolvedValue([
          problemWithTag,
        ]);

        const result = await service.findAll({ category: 'algorithms' });

        expect(result.items).toHaveLength(1);
      });

      it('should filter by database category', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        await service.findAll({ category: 'database' });

        expect(prisma.problem.findMany).toHaveBeenCalled();
      });

      it('should return all problems when category is "all"', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue(mockProblems);
        (prisma.problem.count as jest.Mock).mockResolvedValue(4);

        await service.findAll({ category: 'all' });

        expect(prisma.problem.findMany).toHaveBeenCalled();
      });

      it('should handle invalid category gracefully', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue(mockProblems);
        (prisma.problem.count as jest.Mock).mockResolvedValue(4);

        const result = await service.findAll({ category: 'invalid' });

        // Should return all problems when category doesn't match
        expect(result.items).toHaveLength(4);
      });
    });

    describe('with search', () => {
      it('should search by problem title (case-insensitive)', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const result = await service.findAll({ search: 'Two' });

        expect(prisma.problem.findMany).toHaveBeenCalledWith(
          expect.objectContaining({
            where: expect.objectContaining({
              OR: expect.arrayContaining([
                { title: { contains: 'Two', mode: 'insensitive' } },
              ]),
            }),
          }),
        );
        expect(result.items).toHaveLength(1);
      });

      it('should search by problem ID', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const result = await service.findAll({ search: '1' });

        expect(result.items).toHaveLength(1);
      });

      it('should return empty when search matches nothing', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(0);

        const result = await service.findAll({ search: 'nonexistent' });

        expect(result).toEqual({
          items: [],
          total: 0,
          page: 1,
          limit: 20,
          totalPages: 0,
        });
      });

      it('should handle special characters in search', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(0);

        const result = await service.findAll({ search: '%test_' });

        expect(result).toBeDefined();
      });
    });

    describe('with i18n', () => {
      it('should apply translations for default locale', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const translatedProblem = {
          ...mockProblem,
          title: '两数之和',
        };

        (prisma.problemTag.findMany as jest.Mock).mockResolvedValue([]);
        (i18nService.translateEntities as jest.Mock).mockResolvedValue([
          translatedProblem,
        ]);

        await service.findAll({}, 'en-US');

        expect(i18nService.translateEntities).toHaveBeenCalledWith(
          'PROBLEM',
          [mockProblem],
          'en-US',
        );
      });

      it('should translate problem tags', async () => {
        const problemWithTag = {
          ...mockProblem,
          tagRelations: [
            {
              problem_id: BigInt(1),
              tag_id: 'tag-1',
              tag: {
                id: 'tag-1',
                label: 'Array',
                slug: 'array',
                color: null,
                description: null,
                usage_count: 0,
                created_at: new Date(),
                updated_at: new Date(),
              },
            },
          ],
        };
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([
          problemWithTag,
        ]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const translatedTag = {
          id: 'tag-1',
          label: '数组',
          slug: 'array',
          color: null,
          description: null,
          usage_count: 0,
          created_at: new Date(),
          updated_at: new Date(),
        };

        (prisma.problemTag.findMany as jest.Mock).mockResolvedValue([
          problemWithTag.tagRelations[0].tag,
        ]);
        (i18nService.translateEntities as jest.Mock)
          .mockResolvedValueOnce([problemWithTag])
          .mockResolvedValueOnce([translatedTag]);

        await service.findAll({}, 'zh-CN');

        expect(i18nService.translateEntities).toHaveBeenCalledWith(
          'PROBLEM_TAG',
          [problemWithTag.tagRelations[0].tag],
          'zh-CN',
        );
      });

      it('should handle missing translations gracefully', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        (prisma.problemTag.findMany as jest.Mock).mockResolvedValue([]);
        (i18nService.translateEntities as jest.Mock).mockResolvedValue([
          mockProblem,
        ]);

        const result = await service.findAll({}, 'zh-CN');

        expect(result.items).toHaveLength(1);
      });

      it('should not translate when problems array is empty', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(0);

        await service.findAll({}, 'zh-CN');

        expect(i18nService.translateEntities).not.toHaveBeenCalled();
      });
    });

    describe('with multiple filters', () => {
      it('should apply difficulty and search filters together', async () => {
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const result = await service.findAll({
          difficulty: 'Easy',
          search: 'Two',
        });

        expect(result.items).toHaveLength(1);
      });

      it('should apply all filters together', async () => {
        const problemWithTag = {
          ...mockProblem,
          tagRelations: [
            {
              problem_id: BigInt(1),
              tag_id: 'tag-algorithms',
              tag: {
                id: 'tag-algorithms',
                label: 'Algorithms',
                slug: 'algorithms',
                color: null,
                description: null,
                usage_count: 0,
                created_at: new Date(),
                updated_at: new Date(),
              },
            },
          ],
        };
        (prisma.problem.findMany as jest.Mock).mockResolvedValue([
          problemWithTag,
        ]);
        (prisma.problem.count as jest.Mock).mockResolvedValue(1);

        const result = await service.findAll({
          category: 'algorithms',
          difficulty: 'Easy',
          search: 'Two',
          page: 1,
          limit: 10,
        });

        expect(result.items).toHaveLength(1);
      });
    });
  });

  describe('findOne', () => {
    it('should return problem by id', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
        mockProblemWithDetail,
      );

      const result = await service.findOne('1');

      expect(result).toEqual(mockProblemWithDetail);
      expect(prisma.problem.findFirst).toHaveBeenCalledWith({
        where: { id: BigInt(1) },
        include: {
          detail: true,
          tagRelations: {
            include: {
              tag: true,
            },
          },
          languages: true,
          examples: true,
        },
      });
    });

    it('should return problem by slug', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
        mockProblemWithDetail,
      );

      const result = await service.findOne('two-sum');

      expect(result).toEqual(mockProblemWithDetail);
      expect(prisma.problem.findFirst).toHaveBeenCalledWith({
        where: { slug: 'two-sum' },
        include: expect.anything(),
      });
    });

    it('should return null when problem not found', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue(null);

      const result = await service.findOne('999');

      expect(result).toBeNull();
    });

    describe('with i18n', () => {
      it('should translate problem title and detail', async () => {
        (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
          mockProblemWithDetail,
        );

        const translatedProblem = {
          ...mockProblemWithDetail,
          title: '两数之和',
          detail: {
            ...mockProblemWithDetail.detail,
            summary: '给定一个整数数组...',
          },
        };

        (i18nService.translateEntity as jest.Mock)
          .mockResolvedValueOnce(translatedProblem)
          .mockResolvedValueOnce(translatedProblem.detail);

        await service.findOne('1', 'zh-CN');

        expect(i18nService.translateEntity).toHaveBeenCalledWith(
          'PROBLEM',
          mockProblemWithDetail,
          'zh-CN',
        );
      });

      it('should translate tags for a single problem', async () => {
        (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
          mockProblemWithDetail,
        );

        const translatedTag = {
          id: 'tag-array',
          label: '数组',
          slug: 'array',
          color: null,
          description: null,
          usage_count: 0,
          created_at: new Date(),
          updated_at: new Date(),
        };

        (i18nService.translateEntity as jest.Mock).mockResolvedValue(
          mockProblemWithDetail,
        );
        (i18nService.translateEntities as jest.Mock).mockResolvedValue([
          translatedTag,
        ]);

        await service.findOne('1', 'zh-CN');

        expect(i18nService.translateEntities).toHaveBeenCalledWith(
          'PROBLEM_TAG',
          [mockProblemWithDetail.tagRelations[0].tag],
          'zh-CN',
        );
      });

      it('should translate examples', async () => {
        (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
          mockProblemWithDetail,
        );

        const translatedExample = {
          ...mockProblemWithDetail.examples[0],
          explanation: '因为 nums[0] + nums[1] == 9',
        };

        (i18nService.translateEntity as jest.Mock).mockResolvedValue(
          mockProblemWithDetail,
        );
        (i18nService.translateEntities as jest.Mock).mockResolvedValue([
          translatedExample,
        ]);

        await service.findOne('1', 'zh-CN');

        expect(i18nService.translateEntities).toHaveBeenCalledWith(
          'PROBLEM_EXAMPLE',
          mockProblemWithDetail.examples,
          'zh-CN',
        );
      });

      it('should parse JSON fields after translation', async () => {
        const problemWithStringJson = {
          ...mockProblemWithDetail,
          detail: {
            ...mockProblemWithDetail.detail,
            constraints_json: '["1 <= nums.length <= 10^4"]' as any,
            hints: '["Use a hash map"]' as any,
          },
        };

        (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
          problemWithStringJson,
        );

        const translatedDetail = {
          ...problemWithStringJson.detail,
          constraints_json: '["1 <= nums.length <= 10^4"]' as any,
          hints: '["Use a hash map"]' as any,
        };

        (i18nService.translateEntity as jest.Mock)
          .mockResolvedValueOnce(problemWithStringJson)
          .mockResolvedValueOnce(translatedDetail);

        const result = await service.findOne('1', 'en-US');

        expect(result?.detail?.constraints_json).toEqual([
          '1 <= nums.length <= 10^4',
        ]);
        expect(result?.detail?.hints).toEqual(['Use a hash map']);
      });

      it('should handle translation parse failures gracefully', async () => {
        const problemWithStringJson = {
          ...mockProblemWithDetail,
          detail: {
            ...mockProblemWithDetail.detail,
            constraints_json: 'invalid json' as any,
          },
        };

        (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
          problemWithStringJson,
        );

        const translatedDetail = {
          ...problemWithStringJson.detail,
          constraints_json: 'invalid json' as any,
        };

        (i18nService.translateEntity as jest.Mock)
          .mockResolvedValueOnce(problemWithStringJson)
          .mockResolvedValueOnce(translatedDetail);

        const result = await service.findOne('1', 'en-US');

        // Should keep the original value if parsing fails
        expect(result?.detail?.constraints_json).toBe('invalid json');
      });
    });
  });

  describe('findOneWithPremiumCheck', () => {
    it('should return full problem for non-premium content', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue(
        mockProblemWithDetail,
      );

      const result = await service.findOneWithPremiumCheck(
        '1',
        'user-123',
        'USER',
      );

      expect(result).toEqual(mockProblemWithDetail);
      expect(subscriptionService.hasPremiumAccess).not.toHaveBeenCalled();
    });

    it('should return full problem for premium content with valid subscription', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue({
        ...mockProblemWithDetail,
        ...mockPremiumProblem,
      });

      (subscriptionService.hasPremiumAccess as jest.Mock).mockResolvedValue({
        hasAccess: true,
        subscription: { id: 'sub-1' },
      });

      const result = await service.findOneWithPremiumCheck(
        '4',
        'user-123',
        'USER',
      );

      expect(result).toBeDefined();
    });

    it('should return teaser for premium content without subscription', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue({
        ...mockProblemWithDetail,
        ...mockPremiumProblem,
      });

      (subscriptionService.hasPremiumAccess as jest.Mock).mockResolvedValue({
        hasAccess: false,
        subscription: null,
      });

      const result = await service.findOneWithPremiumCheck(
        '4',
        'user-123',
        'USER',
      );

      expect(result).toEqual({
        id: mockPremiumProblem.id,
        slug: mockPremiumProblem.slug,
        title: mockPremiumProblem.title,
        difficulty: mockPremiumProblem.difficulty,
        is_premium: true,
        acceptance_rate: mockPremiumProblem.acceptance_rate,
      });
      expect(result).not.toHaveProperty('detail');
      expect(result).not.toHaveProperty('examples');
      expect(result).not.toHaveProperty('languages');
    });

    it('should return null when problem not found', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue(null);

      const result = await service.findOneWithPremiumCheck(
        '999',
        'user-123',
        'USER',
      );

      expect(result).toBeNull();
    });

    it('should handle admin users bypassing premium check', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue({
        ...mockProblemWithDetail,
        ...mockPremiumProblem,
      });

      (subscriptionService.hasPremiumAccess as jest.Mock).mockResolvedValue({
        hasAccess: true,
        subscription: null,
      });

      const result = await service.findOneWithPremiumCheck(
        '4',
        'admin-123',
        'ADMIN',
      );

      expect(result).toBeDefined();
    });
  });

  describe('getRandom', () => {
    it('should return a random problem', async () => {
      (prisma.problem.count as jest.Mock).mockResolvedValue(100);
      (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);

      const result = await service.getRandom();

      expect(result).toEqual(mockProblem);
      expect(prisma.problem.count).toHaveBeenCalled();
      expect(prisma.problem.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          take: 1,
        }),
      );
    });

    it('should return null when no problems exist', async () => {
      // Clear previous calls
      jest.clearAllMocks();
      (prisma.problem.count as jest.Mock).mockResolvedValue(0);

      const result = await service.getRandom();

      expect(result).toBeNull();
      expect(prisma.problem.findMany).not.toHaveBeenCalled();
    });
  });

  describe('findAdjacent', () => {
    it('should return adjacent problem slugs', async () => {
      (prisma.problem.findUnique as jest.Mock)
        .mockResolvedValueOnce({ slug: 'prev-problem' })
        .mockResolvedValueOnce({ slug: 'next-problem' });

      const result = await service.findAdjacent(2);

      expect(result).toEqual({
        prev: 'prev-problem',
        next: 'next-problem',
      });
    });

    it('should return null when no adjacent problems', async () => {
      (prisma.problem.findUnique as jest.Mock)
        .mockResolvedValueOnce(null)
        .mockResolvedValueOnce(null);

      const result = await service.findAdjacent(999);

      expect(result).toEqual({
        prev: null,
        next: null,
      });
    });

    it('should handle boundary conditions (first problem)', async () => {
      (prisma.problem.findUnique as jest.Mock)
        .mockResolvedValueOnce(null) // no previous
        .mockResolvedValueOnce({ slug: 'problem-2' }); // next exists

      const result = await service.findAdjacent(1);

      expect(result).toEqual({
        prev: null,
        next: 'problem-2',
      });
    });

    it('should handle boundary conditions (last problem)', async () => {
      (prisma.problem.findUnique as jest.Mock)
        .mockResolvedValueOnce({ slug: 'problem-99' }) // previous exists
        .mockResolvedValueOnce(null); // no next

      const result = await service.findAdjacent(100);

      expect(result).toEqual({
        prev: 'problem-99',
        next: null,
      });
    });
  });

  describe('Error scenarios', () => {
    it('should handle NaN ID conversion gracefully', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue(null);

      const result = await service.findOne('invalid');

      expect(result).toBeNull();
    });

    it('should handle malformed slug', async () => {
      (prisma.problem.findFirst as jest.Mock).mockResolvedValue(null);

      const result = await service.findOne('malformed-slug-@#$');

      expect(result).toBeNull();
    });

    it('should handle translation service failures gracefully', async () => {
      (prisma.problem.findMany as jest.Mock).mockResolvedValue([mockProblem]);
      (prisma.problem.count as jest.Mock).mockResolvedValue(1);

      (i18nService.translateEntities as jest.Mock).mockRejectedValue(
        new Error('Translation service error'),
      );

      await expect(service.findAll({}, 'zh-CN')).rejects.toThrow(
        'Translation service error',
      );
    });
  });
});
