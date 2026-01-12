import { Test, TestingModule } from '@nestjs/testing';
import { ProblemController } from './problem.controller';
import { ProblemService } from './problem.service';
import { SubmissionService } from '../submission/submission.service';
import { Problem } from './problem.entity';

describe('ProblemController', () => {
  let controller: ProblemController;
  let problemService: jest.Mocked<ProblemService>;
  let submissionService: jest.Mocked<SubmissionService>;

  const mockProblem = {
    id: 1,
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'Easy',
  } as Problem;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ProblemController],
      providers: [
        {
          provide: ProblemService,
          useValue: {
            findAll: jest.fn(),
            findOne: jest.fn(),
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
    it('should return array of problems', async () => {
      problemService.findAll.mockResolvedValue([mockProblem]);
      submissionService.getProblemStatusMap.mockResolvedValue(new Map());

      const result = await controller.findAll();

      expect(result).toEqual([mockProblem]);
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
      problemService.findOne.mockResolvedValue(mockProblem);

      const result = await controller.findOne('1');

      expect(result).toEqual(mockProblem);
      expect(problemService.findOne).toHaveBeenCalledWith('1', undefined);
    });
  });

  describe('getProblemResults', () => {
    it('should return problem results', async () => {
      const mockResults = {
        latestRuns: [],
        problemStats: { accepted: 0, total: 0 },
      };

      submissionService.getLatestRunResult.mockResolvedValue(
        mockResults as never,
      );

      const result = await controller.getProblemResults('1');

      expect(result).toEqual(mockResults);
      expect(submissionService.getLatestRunResult).toHaveBeenCalledWith(
        1,
        undefined,
      );
    });

    it('should return null for invalid id', async () => {
      const result = await controller.getProblemResults('invalid');

      expect(result).toBeNull();
    });
  });

  describe('getAdjacent', () => {
    it('should return adjacent problems', async () => {
      const adjacent = {
        prev: 'prev-problem',
        next: 'next-problem',
      };

      problemService.findAdjacent.mockResolvedValue(adjacent);

      const result = await controller.getAdjacent('2');

      expect(result).toEqual(adjacent);
      expect(problemService.findAdjacent).toHaveBeenCalledWith(2);
    });
  });
});
