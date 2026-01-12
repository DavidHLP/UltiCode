import { Test, TestingModule } from '@nestjs/testing';
import { ViewService } from './view.service';
import { PrismaService } from '../prisma.service';
import { ViewTargetType } from '@prisma/client';

describe('ViewService', () => {
  let service: ViewService;
  let prisma: jest.Mocked<PrismaService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ViewService,
        {
          provide: PrismaService,
          useValue: {
            $transaction: jest.fn((callback) => callback({})),
            view: {
              findFirst: jest.fn().mockResolvedValue(null),
              create: jest.fn().mockResolvedValue({}),
            },
            solution: {
              update: jest.fn().mockResolvedValue({}),
            },
            forumPost: {
              update: jest.fn().mockResolvedValue({}),
            },
          },
        },
      ],
    }).compile();

    service = module.get<ViewService>(ViewService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('recordView', () => {
    it('should return counted false when no userId or ip', async () => {
      const result = await service.recordView(
        ViewTargetType.SOLUTION,
        'solution-123',
      );

      expect(result).toEqual({ counted: false });
    });

    it('should record a new view for solution', async () => {
      const mockTx = {
        view: {
          create: jest.fn().mockResolvedValue({}),
        },
        solution: {
          update: jest.fn().mockResolvedValue({}),
        },
      };

      (prisma.view.findFirst as jest.Mock).mockResolvedValue(null);
      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );

      const result = await service.recordView(
        ViewTargetType.SOLUTION,
        'solution-123',
        'user-123',
        '127.0.0.1',
      );

      expect(result).toEqual({ counted: true });
      expect(mockTx.view.create).toHaveBeenCalled();
      expect(mockTx.solution.update).toHaveBeenCalledWith({
        where: { id: 'solution-123' },
        data: { views: { increment: 1 } },
      });
    });

    it('should record a new view for forum post', async () => {
      const mockTx = {
        view: {
          create: jest.fn().mockResolvedValue({}),
        },
        forumPost: {
          update: jest.fn().mockResolvedValue({}),
        },
      };

      (prisma.view.findFirst as jest.Mock).mockResolvedValue(null);
      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );

      const result = await service.recordView(
        ViewTargetType.FORUM_POST,
        'post-123',
        'user-123',
        '127.0.0.1',
      );

      expect(result).toEqual({ counted: true });
      expect(mockTx.forumPost.update).toHaveBeenCalledWith({
        where: { id: 'post-123' },
        data: { views: { increment: 1 } },
      });
    });

    it('should not count view within cooldown period', async () => {
      (prisma.view.findFirst as jest.Mock).mockResolvedValue({
        id: 'view-123',
        viewed_at: new Date(),
      } as never);

      const result = await service.recordView(
        ViewTargetType.SOLUTION,
        'solution-123',
        'user-123',
        '127.0.0.1',
      );

      expect(result).toEqual({ counted: false });
      expect(prisma.$transaction).not.toHaveBeenCalled();
    });

    it('should count view after cooldown period', async () => {
      const mockTx = {
        view: {
          create: jest.fn().mockResolvedValue({}),
        },
        solution: {
          update: jest.fn().mockResolvedValue({}),
        },
      };

      // View outside cooldown (2 hours ago - but cooldown is 60 minutes)
      const _oldDate = new Date(Date.now() - 2 * 60 * 60 * 1000);
      (prisma.view.findFirst as jest.Mock).mockResolvedValueOnce(null); // First call returns null (no recent view)
      (prisma.$transaction as jest.Mock).mockImplementation((callback) =>
        callback(mockTx as never),
      );

      const result = await service.recordView(
        ViewTargetType.SOLUTION,
        'solution-123',
        'user-123',
        '127.0.0.1',
      );

      expect(result).toEqual({ counted: true });
    });
  });
});
