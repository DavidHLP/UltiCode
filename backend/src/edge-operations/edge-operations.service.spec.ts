import { Test, TestingModule } from '@nestjs/testing';
import { EdgeOperationsService } from './edge-operations.service';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import { EdgeOperationTargetType, EdgeOperationType } from '@prisma/client';

describe('EdgeOperationsService', () => {
  let service: EdgeOperationsService;
  let prisma: jest.Mocked<PrismaService>;
  let voteService: jest.Mocked<VoteService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        EdgeOperationsService,
        {
          provide: PrismaService,
          useValue: {
            $transaction: jest.fn((callback) => callback({})),
            edgeOperation: {
              findUnique: jest.fn().mockResolvedValue(null),
              create: jest.fn().mockResolvedValue({}),
              delete: jest.fn().mockResolvedValue({}),
            },
            bookmarkFolder: {
              findMany: jest.fn().mockResolvedValue([]),
            },
            problemList: {
              findMany: jest.fn().mockResolvedValue([]),
            },
          },
        },
        {
          provide: VoteService,
          useValue: {
            vote: jest.fn(),
            getVoteCounts: jest.fn(),
            getUserVote: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<EdgeOperationsService>(EdgeOperationsService);
    prisma = module.get(PrismaService);
    voteService = module.get(VoteService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('operate', () => {
    it('should handle vote up operation', async () => {
      voteService.vote.mockResolvedValue({
        likes: 5,
        dislikes: 1,
        userVote: 1,
      } as never);
      prisma.bookmarkFolder.findMany.mockResolvedValue([]);
      prisma.problemList.findMany.mockResolvedValue([]);

      const result = await service.operate('user-123', {
        operationType: EdgeOperationType.VOTE_UP,
        targetType: EdgeOperationTargetType.FORUM_POST,
        targetId: 'post-123',
      });

      expect(result).toEqual({
        likes: 5,
        dislikes: 1,
        favorites: 0,
        viewer: { vote: 1 },
      });
    });

    it('should handle vote down operation', async () => {
      voteService.vote.mockResolvedValue({
        likes: 2,
        dislikes: 3,
        userVote: -1,
      } as never);
      prisma.bookmarkFolder.findMany.mockResolvedValue([]);
      prisma.problemList.findMany.mockResolvedValue([]);

      const result = await service.operate('user-123', {
        operationType: EdgeOperationType.VOTE_DOWN,
        targetType: EdgeOperationTargetType.SOLUTION,
        targetId: 'solution-123',
      });

      expect(result).toEqual({
        likes: 2,
        dislikes: 3,
        favorites: 0,
        viewer: { vote: -1 },
      });
    });

    it('should handle other edge operations', async () => {
      const mockTx = {
        edgeOperation: {
          findUnique: jest.fn().mockResolvedValue(null),
          create: jest.fn().mockResolvedValue({}),
        },
      };

      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );

      voteService.getVoteCounts.mockResolvedValue({ likes: 0, dislikes: 0 });
      voteService.getUserVote.mockResolvedValue(0);
      prisma.bookmarkFolder.findMany.mockResolvedValue([]);
      prisma.problemList.findMany.mockResolvedValue([]);

      const result = await service.operate('user-123', {
        operationType: EdgeOperationType.ANALYZE,
        targetType: EdgeOperationTargetType.PROBLEM,
        targetId: '1',
      });

      expect(result).toEqual({
        likes: 0,
        dislikes: 0,
        favorites: 0,
        viewer: { vote: 0 },
      });
    });
  });

  describe('getInteractions', () => {
    it('should return interactions for target', async () => {
      voteService.getVoteCounts.mockResolvedValue({ likes: 5, dislikes: 2 });
      voteService.getUserVote.mockResolvedValue(1);
      prisma.bookmarkFolder.findMany.mockResolvedValue([]);
      prisma.problemList.findMany.mockResolvedValue([]);

      const result = await service.getInteractions(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
        'user-123',
      );

      expect(result).toEqual({
        likes: 5,
        dislikes: 2,
        favorites: 0,
        viewer: { vote: 1 },
      });
    });

    it('should return interactions without user vote when userId not provided', async () => {
      voteService.getVoteCounts.mockResolvedValue({ likes: 5, dislikes: 2 });
      prisma.bookmarkFolder.findMany.mockResolvedValue([]);
      prisma.problemList.findMany.mockResolvedValue([]);

      const result = await service.getInteractions(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
      );

      expect(result).toEqual({
        likes: 5,
        dislikes: 2,
        favorites: 0,
        viewer: { vote: 0 },
      });
      expect(voteService.getUserVote).not.toHaveBeenCalled();
    });
  });
});
