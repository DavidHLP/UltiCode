import { Test, TestingModule } from '@nestjs/testing';
import { AchievementService, AchievementType } from './achievement.service';
import { PrismaService } from '../prisma.service';
import { NotificationGateway } from '../notification/notification.gateway';

describe('AchievementService', () => {
  let service: AchievementService;
  let prisma: jest.Mocked<PrismaService>;
  let notificationGateway: jest.Mocked<NotificationGateway>;

  const mockAchievement = {
    id: 'clx123',
    key: 'first_solve',
    name: 'First Steps',
    description: 'Solve your first problem',
    icon: null,
    category: 'problem_solving',
    tier: 1,
    criteria: { type: 'problems_solved', target: 1 },
    points: 10,
    is_active: true,
    created_at: new Date(),
    updated_at: new Date(),
  };

  const mockPrismaService = {
    achievement: {
      create: jest.fn().mockResolvedValue(mockAchievement),
      findMany: jest.fn().mockResolvedValue([mockAchievement]),
      findUnique: jest.fn().mockResolvedValue(mockAchievement),
      update: jest.fn().mockResolvedValue(mockAchievement),
      delete: jest.fn().mockResolvedValue(mockAchievement),
      count: jest.fn().mockResolvedValue(1),
    },
    userAchievement: {
      findMany: jest.fn().mockResolvedValue([]),
      findUnique: jest.fn().mockResolvedValue(null),
      create: jest.fn().mockResolvedValue({
        id: 'ua1',
        user_id: 'user1',
        achievement_id: 'clx123',
        earned_at: new Date(),
      }),
    },
  };

  const mockNotificationGateway = {
    sendBadgeEarned: jest.fn(),
    sendToUser: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AchievementService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: NotificationGateway,
          useValue: mockNotificationGateway,
        },
      ],
    }).compile();

    service = module.get<AchievementService>(AchievementService);
    prisma = module.get(PrismaService);
    notificationGateway = module.get(NotificationGateway);

    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('create', () => {
    it('should create an achievement', async () => {
      const dto = {
        key: 'test_achievement',
        name: 'Test',
        description: 'A test achievement',
        category: 'test',
        criteria: { type: 'test', target: 1 },
      };

      await service.create(dto);

      expect(prisma.achievement.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            key: 'test_achievement',
          }),
        }),
      );
    });
  });

  describe('findAll', () => {
    it('should return paginated achievements', async () => {
      const result = await service.findAll({ page: 1, limit: 20 });

      expect(result.items).toHaveLength(1);
      expect(result.total).toBe(1);
    });
  });

  describe('getUserAchievements', () => {
    it('should return user achievements with progress', async () => {
      const result = await service.getUserAchievements('user1');

      expect(result).toHaveLength(1);
      expect(result[0].key).toBe('first_solve');
      expect(result[0].earned).toBe(false);
    });
  });

  describe('checkAndAwardAchievements', () => {
    it('should award achievement when criteria is met', async () => {
      mockPrismaService.userAchievement.findUnique.mockResolvedValueOnce(null);

      const awarded = await service.checkAndAwardAchievements(
        'user1',
        AchievementType.PROBLEMS_SOLVED,
        1,
      );

      expect(awarded).toHaveLength(1);
      expect(prisma.userAchievement.create).toHaveBeenCalled();
      expect(notificationGateway.sendBadgeEarned).toHaveBeenCalled();
    });

    it('should not award if already earned', async () => {
      mockPrismaService.userAchievement.findUnique.mockResolvedValueOnce({
        id: 'ua1',
        user_id: 'user1',
        achievement_id: 'clx123',
        earned_at: new Date(),
        progress: null,
      });

      const awarded = await service.checkAndAwardAchievements(
        'user1',
        AchievementType.PROBLEMS_SOLVED,
        1,
      );

      expect(awarded).toHaveLength(0);
      expect(prisma.userAchievement.create).not.toHaveBeenCalled();
    });
  });

  describe('getUserPoints', () => {
    it('should return total achievement points', async () => {
      mockPrismaService.userAchievement.findMany.mockResolvedValueOnce([
        {
          achievement: { points: 10 },
        },
        {
          achievement: { points: 25 },
        },
      ] as unknown as { achievement: { points: number } }[]);

      const points = await service.getUserPoints('user1');

      expect(points).toBe(35);
    });
  });
});
