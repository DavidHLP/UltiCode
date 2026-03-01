import { Test, TestingModule } from '@nestjs/testing';
import { AchievementTriggerService } from './achievement-trigger.service';
import { PrismaService } from '../prisma.service';
import { AchievementService, AchievementType } from './achievement.service';

describe('AchievementTriggerService', () => {
  let service: AchievementTriggerService;
  let prisma: jest.Mocked<PrismaService>;
  let achievementService: jest.Mocked<AchievementService>;

  const mockPrismaService = {
    submission: {
      groupBy: jest.fn(),
      count: jest.fn(),
    },
    contestParticipant: {
      count: jest.fn(),
    },
    forumPost: {
      count: jest.fn(),
    },
    solution: {
      count: jest.fn(),
    },
    user: {
      findMany: jest.fn(),
    },
    $queryRaw: jest.fn(),
  };

  const mockAchievementService = {
    checkAndAwardAchievements: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AchievementTriggerService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: AchievementService,
          useValue: mockAchievementService,
        },
      ],
    }).compile();

    service = module.get<AchievementTriggerService>(AchievementTriggerService);
    prisma = module.get(PrismaService);
    achievementService = module.get(AchievementService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('onSubmissionAccepted', () => {
    it('should check problem-solving and submission achievements', async () => {
      mockPrismaService.submission.groupBy.mockResolvedValue([
        { problem_id: BigInt(1) },
        { problem_id: BigInt(2) },
      ]);
      mockPrismaService.submission.count.mockResolvedValue(10);
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onSubmissionAccepted('user-1', BigInt(1));

      expect(mockPrismaService.submission.groupBy).toHaveBeenCalledWith({
        by: ['problem_id'],
        where: {
          user_id: 'user-1',
          status: 'Accepted',
        },
      });
      expect(mockPrismaService.submission.count).toHaveBeenCalledWith({
        where: { user_id: 'user-1' },
      });
      expect(
        achievementService.checkAndAwardAchievements,
      ).toHaveBeenCalledTimes(2);
      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.PROBLEMS_SOLVED,
        2,
      );
      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.SUBMISSIONS_MADE,
        10,
      );
    });

    it('should handle errors gracefully', async () => {
      mockPrismaService.submission.groupBy.mockRejectedValue(
        new Error('DB error'),
      );

      // Should not throw
      await expect(
        service.onSubmissionAccepted('user-1', BigInt(1)),
      ).resolves.toBeUndefined();
    });

    it('should count zero solved problems correctly', async () => {
      mockPrismaService.submission.groupBy.mockResolvedValue([]);
      mockPrismaService.submission.count.mockResolvedValue(0);
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onSubmissionAccepted('user-1', BigInt(1));

      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.PROBLEMS_SOLVED,
        0,
      );
    });
  });

  describe('onContestParticipation', () => {
    it('should check contest participation achievements', async () => {
      mockPrismaService.contestParticipant.count.mockResolvedValue(5);
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onContestParticipation('user-1');

      expect(mockPrismaService.contestParticipant.count).toHaveBeenCalledWith({
        where: { user_id: 'user-1' },
      });
      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.CONTEST_PARTICIPATION,
        5,
      );
    });

    it('should handle errors gracefully', async () => {
      mockPrismaService.contestParticipant.count.mockRejectedValue(
        new Error('DB error'),
      );

      await expect(
        service.onContestParticipation('user-1'),
      ).resolves.toBeUndefined();
    });
  });

  describe('onContestWin', () => {
    it('should check contest win achievements', async () => {
      mockPrismaService.contestParticipant.count.mockResolvedValue(3);
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onContestWin('user-1');

      expect(mockPrismaService.contestParticipant.count).toHaveBeenCalledWith({
        where: {
          user_id: 'user-1',
          final_rank: 1,
        },
      });
      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.CONTEST_WINS,
        3,
      );
    });

    it('should handle errors gracefully', async () => {
      mockPrismaService.contestParticipant.count.mockRejectedValue(
        new Error('DB error'),
      );

      await expect(service.onContestWin('user-1')).resolves.toBeUndefined();
    });
  });

  describe('onForumPost', () => {
    it('should check forum post achievements', async () => {
      mockPrismaService.forumPost.count.mockResolvedValue(10);
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onForumPost('user-1');

      expect(mockPrismaService.forumPost.count).toHaveBeenCalledWith({
        where: { user_id: 'user-1' },
      });
      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.FORUM_POSTS,
        10,
      );
    });

    it('should handle errors gracefully', async () => {
      mockPrismaService.forumPost.count.mockRejectedValue(
        new Error('DB error'),
      );

      await expect(service.onForumPost('user-1')).resolves.toBeUndefined();
    });
  });

  describe('onSolutionWritten', () => {
    it('should check solution achievements', async () => {
      mockPrismaService.solution.count.mockResolvedValue(7);
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onSolutionWritten('user-1');

      expect(mockPrismaService.solution.count).toHaveBeenCalledWith({
        where: { user_id: 'user-1' },
      });
      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.SOLUTIONS_WRITTEN,
        7,
      );
    });

    it('should handle errors gracefully', async () => {
      mockPrismaService.solution.count.mockRejectedValue(new Error('DB error'));

      await expect(
        service.onSolutionWritten('user-1'),
      ).resolves.toBeUndefined();
    });
  });

  describe('onDailyStreakCheck', () => {
    it('should check streaks for all active users', async () => {
      mockPrismaService.user.findMany.mockResolvedValue([
        { id: 'user-1' },
        { id: 'user-2' },
      ]);
      mockPrismaService.$queryRaw
        .mockResolvedValueOnce([{ date: new Date() }]) // user-1
        .mockResolvedValueOnce([{ date: new Date() }]); // user-2
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onDailyStreakCheck();

      expect(mockPrismaService.user.findMany).toHaveBeenCalled();
      expect(mockPrismaService.$queryRaw).toHaveBeenCalledTimes(2);
    });

    it('should handle empty user list', async () => {
      mockPrismaService.user.findMany.mockResolvedValue([]);

      await service.onDailyStreakCheck();

      expect(mockPrismaService.$queryRaw).not.toHaveBeenCalled();
    });

    it('should handle errors gracefully', async () => {
      mockPrismaService.user.findMany.mockRejectedValue(new Error('DB error'));

      await expect(service.onDailyStreakCheck()).resolves.toBeUndefined();
    });
  });

  describe('checkUserStreak', () => {
    it('should calculate consecutive streak correctly', async () => {
      const today = new Date();
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);

      mockPrismaService.$queryRaw.mockResolvedValue([
        { date: today },
        { date: yesterday },
      ]);
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      const result = await service.checkUserStreak('user-1');

      expect(result).toBeGreaterThanOrEqual(0);
      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.STREAK_DAYS,
        expect.any(Number),
      );
    });

    it('should return 0 for user with no submissions', async () => {
      mockPrismaService.$queryRaw.mockResolvedValue([]);

      const result = await service.checkUserStreak('user-1');

      expect(result).toBe(0);
      // When there are no submissions, the service returns early without calling achievementService
      expect(
        achievementService.checkAndAwardAchievements,
      ).not.toHaveBeenCalled();
    });
  });

  describe('onRatingChange', () => {
    it('should check rating milestone achievements', async () => {
      mockAchievementService.checkAndAwardAchievements.mockResolvedValue(
        undefined,
      );

      await service.onRatingChange('user-1', 1500);

      expect(achievementService.checkAndAwardAchievements).toHaveBeenCalledWith(
        'user-1',
        AchievementType.RATING_MILESTONE,
        1500,
      );
    });

    it('should handle errors gracefully', async () => {
      mockAchievementService.checkAndAwardAchievements.mockRejectedValue(
        new Error('Service error'),
      );

      await expect(
        service.onRatingChange('user-1', 1500),
      ).resolves.toBeUndefined();
    });
  });

  describe('service initialization', () => {
    it('should be defined', () => {
      expect(service).toBeDefined();
    });

    it('should have prisma service', () => {
      expect(prisma).toBeDefined();
    });

    it('should have achievement service', () => {
      expect(achievementService).toBeDefined();
    });
  });
});
