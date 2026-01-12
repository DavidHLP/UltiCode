import { Test, TestingModule } from '@nestjs/testing';
import { VoteController } from './vote.controller';
import { VoteService } from './vote.service';
import { EdgeOperationTargetType } from '@prisma/client';
import { JwtService } from '@nestjs/jwt';

describe('VoteController', () => {
  let controller: VoteController;
  let voteService: jest.Mocked<VoteService>;

  const mockReq = {
    user: { id: 'user-123' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [VoteController],
      providers: [
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn(),
            verify: jest.fn(),
          },
        },
        {
          provide: VoteService,
          useValue: {
            vote: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(JwtService as any)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<VoteController>(VoteController);
    voteService = module.get(VoteService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('vote', () => {
    it('should submit an upvote', async () => {
      const mockResult = { likes: 5, dislikes: 0, userVote: 1 };

      voteService.vote.mockResolvedValue(mockResult as never);

      const result = await controller.vote(
        {
          targetType: EdgeOperationTargetType.FORUM_POST,
          targetId: 'post-123',
          voteType: 1,
        },
        mockReq as any,
      );

      expect(result).toEqual(mockResult);
      expect(voteService.vote).toHaveBeenCalledWith('user-123', {
        targetType: EdgeOperationTargetType.FORUM_POST,
        targetId: 'post-123',
        voteType: 1,
      });
    });

    it('should submit a downvote', async () => {
      const mockResult = { likes: 2, dislikes: 3, userVote: -1 };

      voteService.vote.mockResolvedValue(mockResult as never);

      const result = await controller.vote(
        {
          targetType: EdgeOperationTargetType.FORUM_POST,
          targetId: 'post-123',
          voteType: -1,
        },
        mockReq as any,
      );

      expect(result).toEqual(mockResult);
      expect(voteService.vote).toHaveBeenCalledWith('user-123', {
        targetType: EdgeOperationTargetType.FORUM_POST,
        targetId: 'post-123',
        voteType: -1,
      });
    });
  });
});
