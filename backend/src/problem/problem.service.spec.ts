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
  let _i18nService: jest.Mocked<I18nService>;

  const mockProblem = {
    id: 1,
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'Easy',
    acceptance_rate: 0.65,
    tagRelations: [],
  };

  const mockProblems = [
    mockProblem,
    {
      id: 2,
      title: 'Add Two',
      slug: 'add-two',
      difficulty: 'Medium',
      acceptance_rate: 0.5,
      tagRelations: [],
    },
  ];

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ProblemService,
        {
          provide: 'ProblemRepository',
          useValue: {
            createQueryBuilder: jest.fn().mockReturnThis(),
            leftJoinAndSelect: jest.fn().mockReturnThis(),
            andWhere: jest.fn().mockReturnThis(),
            orderBy: jest.fn().mockReturnThis(),
            skip: jest.fn().mockReturnThis(),
            take: jest.fn().mockReturnThis(),
            getMany: jest.fn(),
            getCount: jest.fn(),
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
    _i18nService = module.get(I18nService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return paginated result with default page and limit', async () => {
      (problemsRepository as any).getMany.mockResolvedValue([mockProblem]);
      (problemsRepository as any).getCount.mockResolvedValue(1);

      const result = await service.findAll();

      expect(result).toEqual({
        items: [mockProblem],
        total: 1,
        page: 1,
        limit: 20,
        totalPages: 1,
      });
      expect(problemsRepository.createQueryBuilder).toHaveBeenCalled();
      expect((problemsRepository as any).orderBy).toHaveBeenCalledWith(
        'problem.id',
        'ASC',
      );
      expect((problemsRepository as any).skip).toHaveBeenCalledWith(0);
      expect((problemsRepository as any).take).toHaveBeenCalledWith(20);
    });

    it('should return paginated result with custom page and limit', async () => {
      (problemsRepository as any).getMany.mockResolvedValue(mockProblems);
      (problemsRepository as any).getCount.mockResolvedValue(5);

      const result = await service.findAll({ page: 2, limit: 10 });

      expect(result).toEqual({
        items: mockProblems,
        total: 5,
        page: 2,
        limit: 10,
        totalPages: 1,
      });
      expect((problemsRepository as any).skip).toHaveBeenCalledWith(10);
      expect((problemsRepository as any).take).toHaveBeenCalledWith(10);
    });

    it('should return empty paginated result when no problems found', async () => {
      (problemsRepository as any).getMany.mockResolvedValue([]);
      (problemsRepository as any).getCount.mockResolvedValue(0);

      const result = await service.findAll();

      expect(result).toEqual({
        items: [],
        total: 0,
        page: 1,
        limit: 20,
        totalPages: 0,
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
  });
});
