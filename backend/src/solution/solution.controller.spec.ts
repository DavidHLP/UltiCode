import { Test, TestingModule } from '@nestjs/testing';
import { SolutionController } from './solution.controller';
import { SolutionService } from './solution.service';
import { CreateSolutionDto } from './dto/create-solution.dto';

describe('SolutionController', () => {
  let controller: SolutionController;
  let solutionService: jest.Mocked<SolutionService>;

  const mockSolution = {
    id: 'solution-123',
    problem_id: '1',
    title: 'Two Sum Solution',
    content: 'This is my solution',
    language: 'javascript',
    tags: ['algorithms'],
  };

  const mockReq = {
    user: { id: 'user-123', username: 'testuser' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [SolutionController],
      providers: [
        {
          provide: SolutionService,
          useValue: {
            findByProblemId: jest.fn(),
            create: jest.fn(),
            findOne: jest.fn(),
            update: jest.fn(),
            delete: jest.fn(),
            findComments: jest.fn(),
            createComment: jest.fn(),
          },
        },
      ],
    }).compile();

    controller = module.get<SolutionController>(SolutionController);
    solutionService = module.get(SolutionService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('findSolutions', () => {
    it('should return solutions for a problem', async () => {
      const mockResponse = {
        items: [mockSolution],
        total: 1,
        sortOptions: [],
      };

      solutionService.findByProblemId.mockResolvedValue(mockResponse as never);

      const result = await controller.findSolutions('1', 'user-123');

      expect(result).toEqual(mockResponse);
      expect(solutionService.findByProblemId).toHaveBeenCalledWith(
        '1',
        'user-123',
      );
    });
  });

  describe('create', () => {
    it('should create a new solution', async () => {
      const createDto: CreateSolutionDto = {
        title: 'Two Sum Solution',
        content: 'This is my solution',
        language: 'javascript',
        tags: ['algorithms'],
      };

      solutionService.create.mockResolvedValue(mockSolution as never);

      const result = await controller.create('1', createDto, mockReq as any);

      expect(result).toEqual(mockSolution);
      expect(solutionService.create).toHaveBeenCalledWith(
        '1',
        'user-123',
        createDto,
      );
    });
  });
});
