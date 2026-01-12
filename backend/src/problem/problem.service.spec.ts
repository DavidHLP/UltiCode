import { Test, TestingModule } from '@nestjs/testing';
import { ProblemService } from './problem.service';
import { Repository } from 'typeorm';
import { Problem } from './problem.entity';
import { ProblemDetail } from './problem-detail.entity';
import { I18nService } from '../i18n/i18n.service';

describe('ProblemService', () => {
  let service: ProblemService;
  let problemsRepository: jest.Mocked<Repository<Problem>>;
  let problemDetailsRepository: jest.Mocked<Repository<ProblemDetail>>;
  let i18nService: jest.Mocked<I18nService>;

  const mockProblem = {
    id: 1,
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'Easy',
    acceptance_rate: 0.65,
    tagRelations: [],
  };

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
            getMany: jest.fn(),
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
              .mockImplementation((obj, trans, fields) => obj),
          },
        },
      ],
    }).compile();

    service = module.get<ProblemService>(ProblemService);
    problemsRepository = module.get('ProblemRepository');
    problemDetailsRepository = module.get('ProblemDetailRepository');
    i18nService = module.get(I18nService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return array of problems', async () => {
      problemsRepository.getMany.mockResolvedValue([mockProblem]);

      const result = await service.findAll();

      expect(result).toEqual([mockProblem]);
      expect(problemsRepository.createQueryBuilder).toHaveBeenCalled();
    });
  });

  describe('findOne', () => {
    it('should return problem by id', async () => {
      problemsRepository.findOne.mockResolvedValue(mockProblem);

      const result = await service.findOne('1');

      expect(result).toEqual(mockProblem);
      expect(problemsRepository.findOne).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 1 },
        }),
      );
    });

    it('should return problem by slug', async () => {
      problemsRepository.findOne.mockResolvedValue(mockProblem);

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
      problemsRepository.count.mockResolvedValue(10);
      problemsRepository.find.mockResolvedValue([mockProblem]);

      const result = await service.getRandom();

      expect(result).toEqual(mockProblem);
    });

    it('should return null when no problems exist', async () => {
      problemsRepository.count.mockResolvedValue(0);

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
