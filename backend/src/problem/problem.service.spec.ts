import { Test, TestingModule } from '@nestjs/testing';
import { ProblemService } from './problem.service';
import { Repository } from 'typeorm';
import { Problem } from './problem.entity';
import { ProblemDetail } from './problem-detail.entity';
import { I18nService } from '../i18n/i18n.service';
import { SubscriptionService } from '../subscription/subscription.service';

describe('ProblemService', () => {
  let service: ProblemService;
  let problemsRepository: jest.Mocked<Repository<Problem>>;
  let _problemDetailsRepository: jest.Mocked<Repository<ProblemDetail>>;
  let i18nService: jest.Mocked<I18nService>;
  let subscriptionService: jest.Mocked<SubscriptionService>;

  const mockProblem = {
    id: 1,
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'Easy',
    acceptance_rate: 0.65,
    is_premium: false,
    tagRelations: [],
  };

  const mockMediumProblem = {
    id: 2,
    title: 'Add Two Numbers',
    slug: 'add-two-numbers',
    difficulty: 'Medium',
    acceptance_rate: 0.5,
    is_premium: false,
    tagRelations: [],
  };

  const mockHardProblem = {
    id: 3,
    title: 'Merge K Sorted Lists',
    slug: 'merge-k-sorted-lists',
    difficulty: 'Hard',
    acceptance_rate: 0.3,
    is_premium: false,
    tagRelations: [],
  };

  const mockPremiumProblem = {
    id: 4,
    title: 'Premium Problem',
    slug: 'premium-problem',
    difficulty: 'Hard',
    acceptance_rate: 0.2,
    is_premium: true,
    tagRelations: [],
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
      description: 'Given an array of integers...',
      constraints_json: ['1 <= nums.length <= 10^4'],
      hints: ['Use a hash map'],
    },
    tagRelations: [
      {
        tag: {
          id: 'tag-array',
          label: 'Array',
        },
      },
    ],
    languages: [
      {
        id: 'lang-python',
        name: 'Python',
      },
    ],
    examples: [
      {
        id: 'ex-1',
        input: 'nums = [2,7,11,15], target = 9',
        output: '[0,1]',
        explanation: 'Because nums[0] + nums[1] == 9',
      },
    ],
  };

  const createMockQueryBuilder = () => {
    const queryBuilder: any = {
      leftJoinAndSelect: jest.fn().mockReturnThis(),
      andWhere: jest.fn().mockReturnThis(),
      orderBy: jest.fn().mockReturnThis(),
      skip: jest.fn().mockReturnThis(),
      take: jest.fn().mockReturnThis(),
      setParameter: jest.fn().mockReturnThis(),
      getMany: jest.fn(),
      getCount: jest.fn(),
      subQuery: jest.fn(() => queryBuilder),
      select: jest.fn(() => queryBuilder),
      from: jest.fn(() => queryBuilder),
      leftJoin: jest.fn(() => queryBuilder),
      where: jest.fn(() => queryBuilder),
      getQuery: jest.fn(() => 'SELECT * FROM problems'),
    };

    return queryBuilder;
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProblemService,
        {
          provide: 'ProblemRepository',
          useValue: {
            createQueryBuilder: jest.fn(() => createMockQueryBuilder()),
            findOne: jest.fn(),
            find: jest.fn(),
            count: jest.fn(),
          },
        },
        {
          provide: 'ProblemDetailRepository',
          useValue: {},
        },
        {
          provide: I18nService,
          useValue: {
            getBatchTranslations: jest.fn().mockResolvedValue(new Map()),
            getTranslations: jest.fn().mockResolvedValue(new Map()),
            applyTranslations: jest
              .fn()
              .mockImplementation((obj, _trans, _fields) => obj),
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
    problemsRepository = module.get('ProblemRepository');
    _problemDetailsRepository = module.get('ProblemDetailRepository');
    i18nService = module.get(I18nService);
    subscriptionService = module.get(SubscriptionService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return paginated result with default page and limit', async () => {
      const mockQB = createMockQueryBuilder();
      mockQB.getMany.mockResolvedValue([mockProblem]);
      mockQB.getCount.mockResolvedValue(1);
      (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
        mockQB,
      );

      const result = await service.findAll();

      expect(result).toEqual({
        items: [mockProblem],
        total: 1,
        page: 1,
        limit: 20,
        totalPages: 1,
      });
      expect(mockQB.orderBy).toHaveBeenCalledWith('problem.id', 'ASC');
      expect(mockQB.skip).toHaveBeenCalledWith(0);
      expect(mockQB.take).toHaveBeenCalledWith(20);
    });

    it('should return paginated result with custom page and limit', async () => {
      const mockQB = createMockQueryBuilder();
      mockQB.getMany.mockResolvedValue(mockProblems);
      mockQB.getCount.mockResolvedValue(5);
      (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
        mockQB,
      );

      const result = await service.findAll({ page: 2, limit: 10 });

      expect(result).toEqual({
        items: mockProblems,
        total: 5,
        page: 2,
        limit: 10,
        totalPages: 1,
      });
      expect(mockQB.skip).toHaveBeenCalledWith(10);
      expect(mockQB.take).toHaveBeenCalledWith(10);
    });

    it('should return empty paginated result when no problems found', async () => {
      const mockQB = createMockQueryBuilder();
      mockQB.getMany.mockResolvedValue([]);
      mockQB.getCount.mockResolvedValue(0);
      (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
        mockQB,
      );

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
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ difficulty: 'Easy' });

        expect(mockQB.andWhere).toHaveBeenCalledWith(
          'problem.difficulty = :difficulty',
          {
            difficulty: 'Easy',
          },
        );
        expect(result.items).toHaveLength(1);
      });

      it('should filter by Medium difficulty', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockMediumProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ difficulty: 'Medium' });

        expect(mockQB.andWhere).toHaveBeenCalledWith(
          'problem.difficulty = :difficulty',
          {
            difficulty: 'Medium',
          },
        );
        expect(result.items).toHaveLength(1);
      });

      it('should filter by Hard difficulty', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockHardProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ difficulty: 'Hard' });

        expect(mockQB.andWhere).toHaveBeenCalledWith(
          'problem.difficulty = :difficulty',
          {
            difficulty: 'Hard',
          },
        );
        expect(result.items).toHaveLength(1);
      });

      it('should return empty when no problems match difficulty', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([]);
        mockQB.getCount.mockResolvedValue(0);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ difficulty: 'Easy' });

        expect(result).toEqual({
          items: [],
          total: 0,
          page: 1,
          limit: 20,
          totalPages: 0,
        });
      });
    });

    describe('with category filter', () => {
      it('should filter by algorithms category', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ category: 'algorithms' });

        expect(mockQB.andWhere).toHaveBeenCalled();
        expect(result.items).toHaveLength(1);
      });

      it('should filter by database category', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        await service.findAll({ category: 'database' });

        expect(mockQB.andWhere).toHaveBeenCalled();
      });

      it('should filter by shell category', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        await service.findAll({ category: 'shell' });

        expect(mockQB.andWhere).toHaveBeenCalled();
      });

      it('should filter by concurrency category', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        await service.findAll({ category: 'concurrency' });

        expect(mockQB.andWhere).toHaveBeenCalled();
      });

      it('should return all problems when category is "all"', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue(mockProblems);
        mockQB.getCount.mockResolvedValue(4);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        await service.findAll({ category: 'all' });

        // Should not add category filter for "all"
        expect(mockQB.andWhere).not.toHaveBeenCalledWith(
          expect.stringContaining('problem.id IN'),
          expect.anything(),
        );
      });

      it('should handle invalid category gracefully', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue(mockProblems);
        mockQB.getCount.mockResolvedValue(4);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ category: 'invalid' });

        // Should not add filter for invalid category
        expect(result.items).toHaveLength(4);
      });
    });

    describe('with search', () => {
      it('should search by problem title (case-insensitive)', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ search: 'Two' });

        expect(mockQB.andWhere).toHaveBeenCalledWith(
          '(LOWER(problem.title) LIKE LOWER(:search) OR CAST(problem.id AS CHAR) LIKE :search)',
          { search: '%Two%' },
        );
        expect(result.items).toHaveLength(1);
      });

      it('should search by problem ID', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ search: '1' });

        expect(mockQB.andWhere).toHaveBeenCalledWith(
          '(LOWER(problem.title) LIKE LOWER(:search) OR CAST(problem.id AS CHAR) LIKE :search)',
          { search: '%1%' },
        );
        expect(result.items).toHaveLength(1);
      });

      it('should return empty when search matches nothing', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([]);
        mockQB.getCount.mockResolvedValue(0);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

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
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([]);
        mockQB.getCount.mockResolvedValue(0);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({ search: '%test_' });

        expect(result).toBeDefined();
      });
    });

    describe('with i18n', () => {
      it('should apply translations for default locale', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const translationsMap = new Map([
          ['1', new Map([['title', '两数之和']])],
        ]);
        (i18nService.getBatchTranslations as jest.Mock).mockResolvedValue(
          translationsMap,
        );

        await service.findAll({}, 'en-US');

        expect(i18nService.getBatchTranslations).toHaveBeenCalledWith(
          'PROBLEM',
          [1],
          'en-US',
        );
        expect(i18nService.applyTranslations).toHaveBeenCalled();
      });

      it('should apply translations for Chinese locale', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const translationsMap = new Map([
          ['1', new Map([['title', '两数之和']])],
        ]);
        (i18nService.getBatchTranslations as jest.Mock).mockResolvedValue(
          translationsMap,
        );

        await service.findAll({}, 'zh-CN');

        expect(i18nService.getBatchTranslations).toHaveBeenCalledWith(
          'PROBLEM',
          [1],
          'zh-CN',
        );
      });

      it('should translate problem tags', async () => {
        const problemWithTag = {
          ...mockProblem,
          tagRelations: [{ tag: { id: 'tag-1', label: 'Array' } }],
        };
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([problemWithTag]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const problemTranslationsMap = new Map([['1', new Map()]]);
        const tagTranslationsMap = new Map([
          ['tag-1', new Map([['label', '数组']])],
        ]);
        (i18nService.getBatchTranslations as jest.Mock)
          .mockResolvedValueOnce(problemTranslationsMap)
          .mockResolvedValueOnce(tagTranslationsMap);

        await service.findAll({}, 'zh-CN');

        expect(i18nService.getBatchTranslations).toHaveBeenCalledWith(
          'PROBLEM_TAG',
          ['tag-1'],
          'zh-CN',
        );
      });

      it('should handle missing translations gracefully', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        (i18nService.getBatchTranslations as jest.Mock).mockResolvedValue(
          new Map(),
        );

        const result = await service.findAll({}, 'zh-CN');

        expect(result.items).toHaveLength(1);
      });

      it('should not translate when problems array is empty', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([]);
        mockQB.getCount.mockResolvedValue(0);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        await service.findAll({}, 'zh-CN');

        expect(i18nService.getBatchTranslations).not.toHaveBeenCalled();
      });
    });

    describe('with multiple filters', () => {
      it('should apply difficulty and search filters together', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

        const result = await service.findAll({
          difficulty: 'Easy',
          search: 'Two',
        });

        expect(mockQB.andWhere).toHaveBeenCalledWith(
          'problem.difficulty = :difficulty',
          {
            difficulty: 'Easy',
          },
        );
        expect(mockQB.andWhere).toHaveBeenCalledWith(
          '(LOWER(problem.title) LIKE LOWER(:search) OR CAST(problem.id AS CHAR) LIKE :search)',
          { search: '%Two%' },
        );
        expect(result.items).toHaveLength(1);
      });

      it('should apply all filters together', async () => {
        const mockQB = createMockQueryBuilder();
        mockQB.getMany.mockResolvedValue([mockProblem]);
        mockQB.getCount.mockResolvedValue(1);
        (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
          mockQB,
        );

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
      problemsRepository.findOne.mockResolvedValue(mockProblem as never);

      const result = await service.findOne('1');

      expect(result).toEqual(mockProblem);
      expect(problemsRepository.findOne).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 1 },
        }),
      );
    });

    it('should return problem by slug', async () => {
      problemsRepository.findOne.mockResolvedValue(mockProblem as never);

      const result = await service.findOne('two-sum');

      expect(result).toEqual(mockProblem);
      expect(problemsRepository.findOne).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { slug: 'two-sum' },
        }),
      );
    });

    it('should return null when problem not found', async () => {
      problemsRepository.findOne.mockResolvedValue(null);

      const result = await service.findOne('999');

      expect(result).toBeNull();
    });

    describe('with i18n', () => {
      it('should translate problem title and detail', async () => {
        problemsRepository.findOne.mockResolvedValue(
          mockProblemWithDetail as never,
        );

        const titleTranslations = new Map([['title', '两数之和']]);
        const detailTranslations = new Map([
          ['description', '给定一个整数数组...'],
        ]);

        (i18nService.getTranslations as jest.Mock)
          .mockResolvedValueOnce(titleTranslations)
          .mockResolvedValueOnce(detailTranslations);

        await service.findOne('1', 'zh-CN');

        expect(i18nService.getTranslations).toHaveBeenCalledWith(
          'PROBLEM',
          1,
          'zh-CN',
        );
        expect(i18nService.applyTranslations).toHaveBeenCalled();
      });

      it('should translate tags for a single problem', async () => {
        problemsRepository.findOne.mockResolvedValue(
          mockProblemWithDetail as never,
        );

        const tagTranslationsMap = new Map([
          ['tag-array', new Map([['label', '数组']])],
        ]);
        (i18nService.getBatchTranslations as jest.Mock).mockResolvedValue(
          tagTranslationsMap,
        );

        await service.findOne('1', 'zh-CN');

        expect(i18nService.getBatchTranslations).toHaveBeenCalledWith(
          'PROBLEM_TAG',
          ['tag-array'],
          'zh-CN',
        );
      });

      it('should translate examples', async () => {
        problemsRepository.findOne.mockResolvedValue(
          mockProblemWithDetail as never,
        );

        const exampleTranslationsMap = new Map([
          ['ex-1', new Map([['explanation', '因为 nums[0] + nums[1] == 9']])],
        ]);
        (i18nService.getBatchTranslations as jest.Mock).mockResolvedValue(
          exampleTranslationsMap,
        );

        await service.findOne('1', 'zh-CN');

        expect(i18nService.getBatchTranslations).toHaveBeenCalledWith(
          'PROBLEM_EXAMPLE',
          ['ex-1'],
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

        problemsRepository.findOne.mockResolvedValue(
          problemWithStringJson as never,
        );

        (i18nService.getTranslations as jest.Mock).mockResolvedValue(
          new Map([
            ['constraints_json', '["1 <= nums.length <= 10^4"]'],
            ['hints', '["Use a hash map"]'],
          ]),
        );

        const result = await service.findOne('1', 'en-US');

        expect(result?.detail.constraints_json).toEqual([
          '1 <= nums.length <= 10^4',
        ]);
        expect(result?.detail.hints).toEqual(['Use a hash map']);
      });

      it('should handle translation parse failures gracefully', async () => {
        const problemWithStringJson = {
          ...mockProblemWithDetail,
          detail: {
            ...mockProblemWithDetail.detail,
            constraints_json: 'invalid json' as any,
          },
        };

        problemsRepository.findOne.mockResolvedValue(
          problemWithStringJson as never,
        );

        (i18nService.getTranslations as jest.Mock).mockResolvedValue(
          new Map([['constraints_json', 'invalid json']]),
        );

        const result = await service.findOne('1', 'en-US');

        // Should keep the original value if parsing fails
        expect(result?.detail.constraints_json).toBe('invalid json');
      });
    });
  });

  describe('findOneWithPremiumCheck', () => {
    it('should return full problem for non-premium content', async () => {
      problemsRepository.findOne.mockResolvedValue(mockProblem as never);

      const result = await service.findOneWithPremiumCheck(
        '1',
        'user-123',
        'USER',
      );

      expect(result).toEqual(mockProblem);
      expect(subscriptionService.hasPremiumAccess).not.toHaveBeenCalled();
    });

    it('should return full problem for premium content with valid subscription', async () => {
      problemsRepository.findOne.mockResolvedValue(mockPremiumProblem as never);

      (subscriptionService.hasPremiumAccess as jest.Mock).mockResolvedValue({
        hasAccess: true,
        subscription: { id: 'sub-1' },
      });

      const result = await service.findOneWithPremiumCheck(
        '4',
        'user-123',
        'USER',
      );

      expect(result).toEqual(mockPremiumProblem);
    });

    it('should return teaser for premium content without subscription', async () => {
      problemsRepository.findOne.mockResolvedValue(mockPremiumProblem as never);

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
      problemsRepository.findOne.mockResolvedValue(null);

      const result = await service.findOneWithPremiumCheck(
        '999',
        'user-123',
        'USER',
      );

      expect(result).toBeNull();
    });

    it('should handle admin users bypassing premium check', async () => {
      problemsRepository.findOne.mockResolvedValue(mockPremiumProblem as never);

      (subscriptionService.hasPremiumAccess as jest.Mock).mockResolvedValue({
        hasAccess: true,
        subscription: null,
      });

      const result = await service.findOneWithPremiumCheck(
        '4',
        'admin-123',
        'ADMIN',
      );

      expect(result).toEqual(mockPremiumProblem);
    });
  });

  describe('getRandom', () => {
    it('should return a random problem', async () => {
      const mockQueryBuilder = {
        orderBy: jest.fn().mockReturnThis(),
        limit: jest.fn().mockReturnThis(),
        getOne: jest.fn().mockResolvedValue(mockProblem),
      } as any;
      problemsRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);

      const result = await service.getRandom();

      expect(result).toEqual(mockProblem);
      expect(mockQueryBuilder.orderBy).toHaveBeenCalledWith('RAND()');
      expect(mockQueryBuilder.limit).toHaveBeenCalledWith(1);
      expect(mockQueryBuilder.getOne).toHaveBeenCalled();
    });

    it('should return null when no problems exist', async () => {
      const mockQueryBuilder = {
        orderBy: jest.fn().mockReturnThis(),
        limit: jest.fn().mockReturnThis(),
        getOne: jest.fn().mockResolvedValue(null),
      } as any;
      problemsRepository.createQueryBuilder.mockReturnValue(mockQueryBuilder);

      const result = await service.getRandom();

      expect(result).toBeNull();
    });
  });

  describe('findAdjacent', () => {
    it('should return adjacent problem slugs', async () => {
      problemsRepository.findOne
        .mockResolvedValueOnce({ slug: 'prev-problem' } as Problem)
        .mockResolvedValueOnce({ slug: 'next-problem' } as Problem);

      const result = await service.findAdjacent(2);

      expect(result).toEqual({
        prev: 'prev-problem',
        next: 'next-problem',
      });
    });

    it('should return null when no adjacent problems', async () => {
      problemsRepository.findOne
        .mockResolvedValueOnce(null)
        .mockResolvedValueOnce(null);

      const result = await service.findAdjacent(999);

      expect(result).toEqual({
        prev: null,
        next: null,
      });
    });

    it('should handle boundary conditions (first problem)', async () => {
      problemsRepository.findOne
        .mockResolvedValueOnce(null) // no previous
        .mockResolvedValueOnce({ slug: 'problem-2' } as Problem); // next exists

      const result = await service.findAdjacent(1);

      expect(result).toEqual({
        prev: null,
        next: 'problem-2',
      });
    });

    it('should handle boundary conditions (last problem)', async () => {
      problemsRepository.findOne
        .mockResolvedValueOnce({ slug: 'problem-99' } as Problem) // previous exists
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
      problemsRepository.findOne.mockResolvedValue(null);

      const result = await service.findOne('invalid');

      expect(result).toBeNull();
    });

    it('should handle malformed slug', async () => {
      problemsRepository.findOne.mockResolvedValue(null);

      const result = await service.findOne('malformed-slug-@#$');

      expect(result).toBeNull();
    });

    it('should handle translation service failures gracefully', async () => {
      const mockQB = createMockQueryBuilder();
      mockQB.getMany.mockResolvedValue([mockProblem]);
      mockQB.getCount.mockResolvedValue(1);
      (problemsRepository.createQueryBuilder as jest.Mock).mockReturnValue(
        mockQB,
      );

      (i18nService.getBatchTranslations as jest.Mock).mockRejectedValue(
        new Error('Translation service error'),
      );

      await expect(service.findAll({}, 'zh-CN')).rejects.toThrow(
        'Translation service error',
      );
    });
  });
});
