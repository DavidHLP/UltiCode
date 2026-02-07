import { Test, TestingModule } from '@nestjs/testing';
import { ViewController } from './view.controller';
import { ViewService } from './view.service';
import { ViewTargetType } from '@prisma/client';

describe('ViewController', () => {
  let controller: ViewController;
  let viewService: jest.Mocked<ViewService>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ViewController],
      providers: [
        {
          provide: ViewService,
          useValue: {
            recordView: jest.fn(),
          },
        },
      ],
    }).compile();

    controller = module.get<ViewController>(ViewController);
    viewService = module.get(ViewService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('recordView', () => {
    it('should record a view', async () => {
      viewService.recordView.mockResolvedValue({ counted: true } as never);

      const result = await controller.recordView(
        {
          targetType: ViewTargetType.SOLUTION,
          targetId: 'solution-123',
          userId: 'user-123',
        },
        '127.0.0.1',
      );

      expect(result).toEqual({ counted: true });
      expect(viewService.recordView).toHaveBeenCalledWith(
        ViewTargetType.SOLUTION,
        'solution-123',
        'user-123',
        '127.0.0.1',
      );
    });
  });

  describe('recordSolutionView', () => {
    it('should record a solution view', async () => {
      viewService.recordView.mockResolvedValue({ counted: true } as never);

      const result = await controller.recordSolutionView(
        'solution-123',
        '127.0.0.1',
        'user-123',
      );

      expect(result).toEqual({ counted: true });
      expect(viewService.recordView).toHaveBeenCalledWith(
        ViewTargetType.SOLUTION,
        'solution-123',
        'user-123',
        '127.0.0.1',
      );
    });
  });

  describe('recordForumView', () => {
    it('should record a forum post view', async () => {
      viewService.recordView.mockResolvedValue({ counted: true } as never);

      const result = await controller.recordForumView(
        'post-123',
        '127.0.0.1',
        'user-123',
      );

      expect(result).toEqual({ counted: true });
      expect(viewService.recordView).toHaveBeenCalledWith(
        ViewTargetType.FORUM_POST,
        'post-123',
        'user-123',
        '127.0.0.1',
      );
    });
  });
});
