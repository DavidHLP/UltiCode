import { Test, TestingModule } from '@nestjs/testing';
import { SolutionService } from './solution.service';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import { SolutionCrudService } from './services/solution-crud.service';
import { SolutionQueryService } from './services/solution-query.service';
import { SolutionCommentService } from './services/solution-comment.service';

describe('SolutionService', () => {
  let service: SolutionService;
  let crudService: jest.Mocked<SolutionCrudService>;
  let queryService: jest.Mocked<SolutionQueryService>;
  let commentService: jest.Mocked<SolutionCommentService>;

  const mockSolution = {
    id: 'solution-123',
    problem_id: BigInt(1),
    user_id: 'user-123',
    title: 'Two Sum Solution',
    content: 'This is my solution',
    summary: 'This is my solution',
    language: 'javascript',
    tags: ['algorithms'],
    created_at: new Date(),
    views: 0,
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SolutionService,
        {
          provide: PrismaService,
          useValue: {},
        },
        {
          provide: VoteService,
          useValue: {
            getVoteCountsBatch: jest.fn().mockResolvedValue(new Map()),
            getUserVotesBatch: jest.fn().mockResolvedValue(new Map()),
            getUserVote: jest.fn().mockResolvedValue(0),
          },
        },
        {
          provide: SolutionCrudService,
          useValue: {
            create: jest.fn().mockResolvedValue(mockSolution as any),
            update: jest.fn().mockResolvedValue(mockSolution as any),
            delete: jest.fn().mockResolvedValue({ success: true }),
          },
        },
        {
          provide: SolutionQueryService,
          useValue: {
            findOne: jest.fn().mockResolvedValue(mockSolution as any),
            findByProblemId: jest.fn().mockResolvedValue({
              items: [mockSolution],
              total: 1,
              sortOptions: [],
            }),
            findAllByUser: jest.fn().mockResolvedValue({
              items: [mockSolution],
              total: 1,
              sortOptions: [],
            }),
          },
        },
        {
          provide: SolutionCommentService,
          useValue: {
            findComments: jest.fn().mockResolvedValue([]),
            createComment: jest
              .fn()
              .mockResolvedValue({ id: 'comment-123' } as any),
            updateComment: jest.fn().mockResolvedValue({} as any),
            deleteComment: jest.fn().mockResolvedValue({ success: true }),
          },
        },
      ],
    }).compile();

    service = module.get<SolutionService>(SolutionService);
    crudService = module.get(SolutionCrudService);
    queryService = module.get(SolutionQueryService);
    commentService = module.get(SolutionCommentService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('create', () => {
    it('should create a new solution', async () => {
      crudService.create.mockResolvedValue(mockSolution as any);

      const result = await service.create('1', 'user-123', {
        title: 'Two Sum Solution',
        content: 'This is my solution',
        language: 'javascript',
      });

      expect(result).toBeDefined();
      expect(crudService.create).toHaveBeenCalledWith('1', 'user-123', {
        title: 'Two Sum Solution',
        content: 'This is my solution',
        language: 'javascript',
      });
    });
  });

  describe('findByProblemId', () => {
    it('should return solutions for a problem', async () => {
      queryService.findByProblemId.mockResolvedValue({
        items: [mockSolution] as any,
        total: 1,
        sortOptions: [],
      });

      const result = await service.findByProblemId('1', 'user-123');

      expect(result).toHaveProperty('items');
      expect(result.items).toHaveLength(1);
    });
  });

  describe('findAllByUser', () => {
    it('should return solutions for a user', async () => {
      queryService.findAllByUser.mockResolvedValue({
        items: [mockSolution] as any,
        total: 1,
        sortOptions: [],
      });

      const result = await service.findAllByUser('user-123');

      expect(result).toHaveProperty('items');
      expect(result.items).toHaveLength(1);
    });
  });

  describe('findOne', () => {
    it('should return a solution by id', async () => {
      queryService.findOne.mockResolvedValue(mockSolution as any);

      const result = await service.findOne('solution-123');

      expect(result).toBeDefined();
      expect(queryService.findOne).toHaveBeenCalledWith(
        'solution-123',
        undefined,
      );
    });
  });

  describe('findComments', () => {
    it('should return comments for a solution', async () => {
      commentService.findComments.mockResolvedValue([]);

      const result = await service.findComments('solution-123');

      expect(result).toBeDefined();
      expect(commentService.findComments).toHaveBeenCalledWith(
        'solution-123',
        undefined,
      );
    });
  });

  describe('createComment', () => {
    it('should create a new comment', async () => {
      commentService.createComment.mockResolvedValue({
        id: 'comment-123',
      } as any);

      const result = await service.createComment(
        'solution-123',
        {
          content: 'Great solution!',
          parentId: undefined,
        },
        'user-456',
      );

      expect(result).toBeDefined();
      expect(commentService.createComment).toHaveBeenCalled();
    });
  });

  describe('delete', () => {
    it('should delete a solution', async () => {
      crudService.delete.mockResolvedValue({ success: true });

      const result = await service.delete('solution-123', 'user-123');

      expect(result).toEqual({ success: true });
    });
  });

  describe('update', () => {
    it('should update a solution', async () => {
      crudService.update.mockResolvedValue(mockSolution as any);

      const result = await service.update('solution-123', 'user-123', {
        title: 'Updated Title',
        content: 'Updated content',
        language: 'typescript',
        tags: ['algorithms'],
      });

      expect(result).toBeDefined();
      expect(crudService.update).toHaveBeenCalled();
    });
  });

  describe('updateComment', () => {
    it('should update a comment', async () => {
      commentService.updateComment.mockResolvedValue({} as any);

      const result = await service.updateComment(
        'comment-123',
        'Updated content',
        'user-123',
      );

      expect(result).toBeDefined();
      expect(commentService.updateComment).toHaveBeenCalledWith(
        'comment-123',
        'Updated content',
        'user-123',
      );
    });
  });

  describe('deleteComment', () => {
    it('should delete a comment', async () => {
      commentService.deleteComment.mockResolvedValue({ success: true });

      const result = await service.deleteComment('comment-123', 'user-123');

      expect(result).toEqual({ success: true });
    });
  });
});
