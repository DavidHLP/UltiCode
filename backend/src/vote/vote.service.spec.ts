import { Test, TestingModule } from '@nestjs/testing';
import { VoteService } from './vote.service';
import { PrismaService } from '../prisma.service';
import { EdgeOperationTargetType, EdgeOperationType } from '@prisma/client';

describe('VoteService', () => {
  let service: VoteService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        VoteService,
        {
          provide: PrismaService,
          useValue: {
            $transaction: jest.fn((callback) => callback({})),
            edgeOperation: {
              findFirst: jest.fn().mockResolvedValue(null),
              findUnique: jest.fn().mockResolvedValue(null),
              findMany: jest.fn().mockResolvedValue([]),
              create: jest.fn().mockResolvedValue({}),
              update: jest.fn().mockResolvedValue({}),
              delete: jest.fn().mockResolvedValue({}),
              groupBy: jest.fn().mockResolvedValue([]),
            },
          },
        },
      ],
    }).compile();

    service = module.get<VoteService>(VoteService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('vote', () => {
    it('should create a new upvote', async () => {
      const mockTx = {
        edgeOperation: {
          findFirst: jest.fn().mockResolvedValue(null),
          create: jest.fn().mockResolvedValue({}),
          delete: jest.fn().mockResolvedValue({}),
          update: jest.fn().mockResolvedValue({}),
          groupBy: jest.fn().mockResolvedValue([]),
        },
      };

      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );
      mockTx.edgeOperation.groupBy.mockResolvedValue([
        { operation_type: EdgeOperationType.VOTE_UP, _count: 5 },
      ] as never);

      const result = await service.vote('user-123', {
        targetType: EdgeOperationTargetType.FORUM_POST,
        targetId: 'post-123',
        voteType: 1,
      });

      expect(result).toEqual({ likes: 5, dislikes: 0, userVote: 1 });
    });

    it('should toggle off existing vote', async () => {
      const mockExistingVote = {
        id: 'vote-123',
        operation_type: EdgeOperationType.VOTE_UP,
      };

      const mockTx = {
        edgeOperation: {
          findFirst: jest.fn().mockResolvedValue(mockExistingVote),
          delete: jest.fn().mockResolvedValue({}),
          groupBy: jest.fn().mockResolvedValue([]),
        },
      };

      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );
      mockTx.edgeOperation.groupBy.mockResolvedValue([] as never);

      const result = await service.vote('user-123', {
        targetType: EdgeOperationTargetType.FORUM_POST,
        targetId: 'post-123',
        voteType: 1,
      });

      expect(result).toEqual({ likes: 0, dislikes: 0, userVote: 0 });
      expect(mockTx.edgeOperation.delete).toHaveBeenCalled();
    });

    it('should change vote from up to down', async () => {
      const mockExistingVote = {
        id: 'vote-123',
        operation_type: EdgeOperationType.VOTE_UP,
      };

      const mockTx = {
        edgeOperation: {
          findFirst: jest.fn().mockResolvedValue(mockExistingVote),
          update: jest.fn().mockResolvedValue({}),
          groupBy: jest.fn().mockResolvedValue([]),
        },
      };

      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );
      mockTx.edgeOperation.groupBy.mockResolvedValue([
        { operation_type: EdgeOperationType.VOTE_DOWN, _count: 3 },
      ] as never);

      const result = await service.vote('user-123', {
        targetType: EdgeOperationTargetType.FORUM_POST,
        targetId: 'post-123',
        voteType: -1,
      });

      expect(result).toEqual({ likes: 0, dislikes: 3, userVote: -1 });
      expect(mockTx.edgeOperation.update).toHaveBeenCalled();
    });
  });

  describe('getVoteCounts', () => {
    it('should return vote counts', async () => {
      (prisma.edgeOperation.groupBy as jest.Mock).mockResolvedValue([
        { operation_type: EdgeOperationType.VOTE_UP, _count: 5 },
        { operation_type: EdgeOperationType.VOTE_DOWN, _count: 2 },
      ] as never);

      const result = await service.getVoteCounts(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
      );

      expect(result).toEqual({ likes: 5, dislikes: 2 });
    });

    it('should return zero counts when no votes', async () => {
      (prisma.edgeOperation.groupBy as jest.Mock).mockResolvedValue([]);

      const result = await service.getVoteCounts(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
      );

      expect(result).toEqual({ likes: 0, dislikes: 0 });
    });
  });

  describe('getVoteCountsBatch', () => {
    it('should return vote counts for multiple targets', async () => {
      (prisma.edgeOperation.groupBy as jest.Mock).mockResolvedValue([
        {
          target_id: 'post-1',
          operation_type: EdgeOperationType.VOTE_UP,
          _count: 5,
        },
        {
          target_id: 'post-1',
          operation_type: EdgeOperationType.VOTE_DOWN,
          _count: 1,
        },
        {
          target_id: 'post-2',
          operation_type: EdgeOperationType.VOTE_UP,
          _count: 3,
        },
      ] as never);

      const result = await service.getVoteCountsBatch(
        EdgeOperationTargetType.FORUM_POST,
        ['post-1', 'post-2'],
      );

      expect(result.get('post-1')).toEqual({ likes: 5, dislikes: 1 });
      expect(result.get('post-2')).toEqual({ likes: 3, dislikes: 0 });
    });

    it('should initialize all targets with zero counts', async () => {
      (prisma.edgeOperation.groupBy as jest.Mock).mockResolvedValue([]);

      const result = await service.getVoteCountsBatch(
        EdgeOperationTargetType.FORUM_POST,
        ['post-1', 'post-2', 'post-3'],
      );

      expect(result.get('post-1')).toEqual({ likes: 0, dislikes: 0 });
      expect(result.get('post-2')).toEqual({ likes: 0, dislikes: 0 });
      expect(result.get('post-3')).toEqual({ likes: 0, dislikes: 0 });
    });
  });

  describe('getUserVotesBatch', () => {
    it('should return user votes for multiple targets', async () => {
      (prisma.edgeOperation.findMany as jest.Mock).mockResolvedValue([
        { target_id: 'post-1', operation_type: EdgeOperationType.VOTE_UP },
        { target_id: 'post-2', operation_type: EdgeOperationType.VOTE_DOWN },
      ] as never);

      const result = await service.getUserVotesBatch(
        'user-123',
        EdgeOperationTargetType.FORUM_POST,
        ['post-1', 'post-2', 'post-3'],
      );

      expect(result.get('post-1')).toBe(1);
      expect(result.get('post-2')).toBe(-1);
      expect(result.get('post-3')).toBeUndefined();
    });
  });

  describe('getUserVote', () => {
    it('should return user vote for target', async () => {
      (prisma.edgeOperation.findFirst as jest.Mock).mockResolvedValue({
        operation_type: EdgeOperationType.VOTE_UP,
      } as never);

      const result = await service.getUserVote(
        'user-123',
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(1);
    });

    it('should return 0 when no vote exists', async () => {
      (prisma.edgeOperation.findFirst as jest.Mock).mockResolvedValue(null);

      const result = await service.getUserVote(
        'user-123',
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(0);
    });

    it('should return -1 for downvote', async () => {
      (prisma.edgeOperation.findFirst as jest.Mock).mockResolvedValue({
        operation_type: EdgeOperationType.VOTE_DOWN,
      } as never);

      const result = await service.getUserVote(
        'user-123',
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
      );

      expect(result).toBe(-1);
    });
  });
});
