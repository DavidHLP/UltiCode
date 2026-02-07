import { Test, TestingModule } from '@nestjs/testing';
import { EdgeOperationsController } from './edge-operations.controller';
import { EdgeOperationsService } from './edge-operations.service';
import { EdgeOperationTargetType, EdgeOperationType } from '@prisma/client';
import { JwtService } from '@nestjs/jwt';

describe('EdgeOperationsController', () => {
  let controller: EdgeOperationsController;
  let edgeOperationsService: jest.Mocked<EdgeOperationsService>;

  const mockReq = {
    user: { id: 'user-123' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [EdgeOperationsController],
      providers: [
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn(),
            verify: jest.fn(),
          },
        },
        {
          provide: EdgeOperationsService,
          useValue: {
            getInteractions: jest.fn(),
            operate: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(JwtService as any)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<EdgeOperationsController>(EdgeOperationsController);
    edgeOperationsService = module.get(EdgeOperationsService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('getInteractions', () => {
    it('should return interactions for target', async () => {
      const mockResponse = {
        likes: 5,
        dislikes: 2,
        favorites: 0,
        viewer: { vote: 1 },
      };

      edgeOperationsService.getInteractions.mockResolvedValue(
        mockResponse as never,
      );

      const result = await controller.getInteractions(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
        { userId: 'user-123' },
      );

      expect(result).toEqual(mockResponse);
      expect(edgeOperationsService.getInteractions).toHaveBeenCalledWith(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
        'user-123',
      );
    });

    it('should return interactions without userId', async () => {
      const mockResponse = {
        likes: 5,
        dislikes: 2,
        favorites: 0,
        viewer: { vote: 0 },
      };

      edgeOperationsService.getInteractions.mockResolvedValue(
        mockResponse as never,
      );

      const result = await controller.getInteractions(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
        {},
      );

      expect(result).toEqual(mockResponse);
      expect(edgeOperationsService.getInteractions).toHaveBeenCalledWith(
        EdgeOperationTargetType.FORUM_POST,
        'post-123',
        undefined,
      );
    });
  });

  describe('operate', () => {
    it('should perform edge operation', async () => {
      const mockResponse = {
        likes: 6,
        dislikes: 2,
        favorites: 0,
        viewer: { vote: 1 },
      };

      edgeOperationsService.operate.mockResolvedValue(mockResponse as never);

      const result = await controller.operate(
        {
          operationType: EdgeOperationType.VOTE_UP,
          targetType: EdgeOperationTargetType.FORUM_POST,
          targetId: 'post-123',
        },
        mockReq as any,
      );

      expect(result).toEqual(mockResponse);
      expect(edgeOperationsService.operate).toHaveBeenCalledWith('user-123', {
        operationType: EdgeOperationType.VOTE_UP,
        targetType: EdgeOperationTargetType.FORUM_POST,
        targetId: 'post-123',
      });
    });
  });
});
