import { Test, TestingModule } from '@nestjs/testing';
import { SolutionService } from './solution.service';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import { BadRequestException } from '@nestjs/common';

describe('SolutionService', () => {
  let service: SolutionService;
  let prisma: jest.Mocked<PrismaService>;
  let voteService: jest.Mocked<VoteService>;

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
    author: {
      id: 'user-123',
      username: 'testuser',
      name: 'Test User',
      avatar: null,
    },
    comments: [],
    problem: {
      id: BigInt(1),
      slug: 'two-sum',
      title: 'Two Sum',
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SolutionService,
        {
          provide: PrismaService,
          useValue: {
            submission: {
              findFirst: jest.fn(),
            },
            solution: {
              findMany: jest.fn(),
              findFirst: jest.fn(),
              findUnique: jest.fn(),
              create: jest.fn(),
              update: jest.fn(),
              delete: jest.fn(),
              count: jest.fn(),
            },
            solutionComment: {
              findMany: jest.fn(),
              create: jest.fn(),
              findUnique: jest.fn(),
              update: jest.fn(),
              delete: jest.fn(),
            },
            problem: {
              update: jest.fn(),
            },
            edgeOperation: {
              deleteMany: jest.fn(),
            },
            $transaction: jest.fn((callback) => {
              const tx = {
                solution: {
                  create: jest.fn().mockResolvedValue({}),
                  update: jest.fn().mockResolvedValue({}),
                  delete: jest.fn().mockResolvedValue({}),
                },
                problem: {
                  update: jest.fn().mockResolvedValue({}),
                },
                edgeOperation: {
                  deleteMany: jest.fn().mockResolvedValue({}),
                  create: jest.fn().mockResolvedValue({}),
                },
              };
              return callback(tx as never);
            }),
          },
        },
        {
          provide: VoteService,
          useValue: {
            getVoteCountsBatch: jest.fn().mockResolvedValue(new Map()),
            getUserVotesBatch: jest.fn().mockResolvedValue(new Map()),
          },
        },
      ],
    }).compile();

    service = module.get<SolutionService>(SolutionService);
    prisma = module.get(PrismaService);
    voteService = module.get(VoteService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('create', () => {
    it('should create a new solution', async () => {
      (prisma.submission.findFirst as jest.Mock).mockResolvedValue({
        status: 'Accepted',
      } as never);
      (prisma.solution.findFirst as jest.Mock).mockResolvedValue(null);
      (prisma.solution.create as jest.Mock).mockResolvedValue(
        mockSolution as never,
      );
      (prisma.problem.update as jest.Mock).mockResolvedValue({} as never);

      const result = await service.create('1', 'user-123', {
        title: 'Two Sum Solution',
        content: 'This is my solution',
        language: 'javascript',
      });

      expect(result).toBeDefined();
      expect(prisma.solution.create).toHaveBeenCalled();
    });

    it('should throw BadRequestException when no accepted submission exists', async () => {
      (prisma.submission.findFirst as jest.Mock).mockResolvedValue(null);

      await expect(
        service.create('1', 'user-123', {
          title: 'Two Sum Solution',
          content: 'This is my solution',
          language: 'javascript',
        }),
      ).rejects.toThrow(BadRequestException);
    });
  });

  describe('findByProblemId', () => {
    it('should return solutions for a problem', async () => {
      (prisma.solution.findMany as jest.Mock).mockResolvedValue([
        mockSolution,
      ] as never);
      voteService.getVoteCountsBatch.mockResolvedValue(new Map());
      voteService.getUserVotesBatch.mockResolvedValue(new Map());

      const result = await service.findByProblemId('1', 'user-123');

      expect(result).toHaveProperty('items');
      expect(result.items).toHaveLength(1);
    });
  });

  describe('findOne', () => {
    it('should return a solution by id', async () => {
      (prisma.solution.findUnique as jest.Mock).mockResolvedValue(
        mockSolution as never,
      );
      voteService.getVoteCountsBatch.mockResolvedValue(
        new Map([['solution-123', { likes: 5, dislikes: 0 }]]),
      );

      const result = await service.findOne('solution-123');

      expect(result).toBeDefined();
      expect(prisma.solution.findUnique).toHaveBeenCalledWith({
        where: { id: 'solution-123' },
        include: expect.any(Object),
      });
    });

    it('should return null for non-existent solution', async () => {
      (prisma.solution.findUnique as jest.Mock).mockResolvedValue(null);

      const result = await service.findOne('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('findComments', () => {
    it('should return comments for a solution', async () => {
      const mockComments = [
        {
          id: 'comment-123',
          solution_id: 'solution-123',
          content: 'Great solution!',
          parent_id: null,
          created_at: new Date(),
          author: {
            id: 'user-456',
            username: 'commenter',
            avatar: 'avatar.png',
          },
        },
      ];

      (prisma.solutionComment.findMany as jest.Mock).mockResolvedValue(
        mockComments as never,
      );
      voteService.getVoteCountsBatch.mockResolvedValue(new Map());
      voteService.getUserVotesBatch.mockResolvedValue(new Map());

      const result = await service.findComments('solution-123');

      expect(result).toHaveLength(1);
      expect(result[0].body).toBe('Great solution!');
    });
  });

  describe('createComment', () => {
    it('should create a new comment', async () => {
      const mockComment = {
        id: 'comment-123',
        content: 'Great solution!',
        author: {
          id: 'user-456',
          username: 'commenter',
          avatar: 'avatar.png',
        },
      };

      (prisma.solutionComment.create as jest.Mock).mockResolvedValue(
        mockComment as never,
      );

      const result = await service.createComment(
        'solution-123',
        {
          content: 'Great solution!',
          parentId: undefined,
        },
        'user-456',
      );

      expect(result).toBeDefined();
      expect(prisma.solutionComment.create).toHaveBeenCalled();
    });
  });

  describe('delete', () => {
    it('should delete a solution by owner', async () => {
      (prisma.solution.findUnique as jest.Mock).mockResolvedValue({
        ...mockSolution,
        user_id: 'user-123',
      } as never);
      (prisma.solutionComment.findMany as jest.Mock).mockResolvedValue([]);
      (prisma.solution.count as jest.Mock).mockResolvedValue(0);
      (prisma.edgeOperation.deleteMany as jest.Mock).mockResolvedValue(
        {} as never,
      );
      (prisma.solution.delete as jest.Mock).mockResolvedValue({} as never);
      (prisma.problem.update as jest.Mock).mockResolvedValue({} as never);

      const result = await service.delete('solution-123', 'user-123');

      expect(result).toEqual({ success: true });
    });
  });

  describe('update', () => {
    it('should update a solution by owner', async () => {
      (prisma.solution.findUnique as jest.Mock).mockResolvedValue({
        ...mockSolution,
        user_id: 'user-123',
      } as never);
      (prisma.solution.update as jest.Mock).mockResolvedValue(
        mockSolution as never,
      );

      const result = await service.update('solution-123', 'user-123', {
        title: 'Updated Title',
        content: 'Updated content',
        language: 'typescript',
        tags: ['algorithms'],
      });

      expect(result).toBeDefined();
      expect(prisma.solution.update).toHaveBeenCalled();
    });
  });
});
