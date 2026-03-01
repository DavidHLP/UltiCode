import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  CreateAchievementDto,
  UpdateAchievementDto,
  AchievementQueryDto,
  AchievementProgress,
} from './achievement.dto';
import { Prisma } from '@prisma/client';
import { NotificationGateway } from '../notification/notification.gateway';

// Predefined achievement criteria types
export enum AchievementType {
  PROBLEMS_SOLVED = 'problems_solved',
  SUBMISSIONS_MADE = 'submissions_made',
  CONTEST_PARTICIPATION = 'contest_participation',
  CONTEST_WINS = 'contest_wins',
  FORUM_POSTS = 'forum_posts',
  SOLUTIONS_WRITTEN = 'solutions_written',
  STREAK_DAYS = 'streak_days',
  RATING_MILESTONE = 'rating_milestone',
  CONTEST_PLACED = 'contest_placed',
  COMMUNITY_CONTRIBUTOR = 'community_contributor',
}

@Injectable()
export class AchievementService {
  private readonly logger = new Logger(AchievementService.name);

  // Default achievements to seed
  private readonly defaultAchievements: CreateAchievementDto[] = [
    {
      key: 'first_solve',
      name: 'First Steps',
      description: 'Solve your first problem',
      category: 'problem_solving',
      tier: 1,
      criteria: { type: AchievementType.PROBLEMS_SOLVED, target: 1 },
      points: 10,
    },
    {
      key: 'ten_solves',
      name: 'Getting Started',
      description: 'Solve 10 problems',
      category: 'problem_solving',
      tier: 1,
      criteria: { type: AchievementType.PROBLEMS_SOLVED, target: 10 },
      points: 25,
    },
    {
      key: 'hundred_solves',
      name: 'Century Club',
      description: 'Solve 100 problems',
      category: 'problem_solving',
      tier: 2,
      criteria: { type: AchievementType.PROBLEMS_SOLVED, target: 100 },
      points: 100,
    },
    {
      key: 'streak_7',
      name: 'Week Warrior',
      description: 'Maintain a 7-day streak',
      category: 'consistency',
      tier: 1,
      criteria: { type: AchievementType.STREAK_DAYS, target: 7 },
      points: 50,
    },
    {
      key: 'streak_30',
      name: 'Monthly Master',
      description: 'Maintain a 30-day streak',
      category: 'consistency',
      tier: 2,
      criteria: { type: AchievementType.STREAK_DAYS, target: 30 },
      points: 200,
    },
    {
      key: 'first_contest',
      name: 'Competitor',
      description: 'Participate in your first contest',
      category: 'contest',
      tier: 1,
      criteria: { type: AchievementType.CONTEST_PARTICIPATION, target: 1 },
      points: 25,
    },
    {
      key: 'contest_winner',
      name: 'Champion',
      description: 'Win a contest',
      category: 'contest',
      tier: 3,
      criteria: { type: AchievementType.CONTEST_WINS, target: 1 },
      points: 500,
    },
    {
      key: 'first_solution',
      name: 'Helper',
      description: 'Write your first solution',
      category: 'community',
      tier: 1,
      criteria: { type: AchievementType.SOLUTIONS_WRITTEN, target: 1 },
      points: 15,
    },
  ];

  constructor(
    private prisma: PrismaService,
    private notificationGateway: NotificationGateway,
  ) {}

  async create(dto: CreateAchievementDto) {
    return this.prisma.achievement.create({
      data: {
        key: dto.key,
        name: dto.name,
        description: dto.description,
        icon: dto.icon,
        category: dto.category,
        tier: dto.tier ?? 1,
        criteria: dto.criteria as Prisma.InputJsonValue,
        points: dto.points ?? 0,
      },
    });
  }

  async findAll(query: AchievementQueryDto) {
    const where: Prisma.AchievementWhereInput = {};

    if (query.category) {
      where.category = query.category;
    }

    where.is_active = true;

    const [total, items] = await Promise.all([
      this.prisma.achievement.count({ where }),
      this.prisma.achievement.findMany({
        where,
        orderBy: [{ category: 'asc' }, { tier: 'asc' }],
        skip: ((query.page ?? 1) - 1) * (query.limit ?? 20),
        take: query.limit ?? 20,
      }),
    ]);

    return { total, page: query.page, limit: query.limit, items };
  }

  async findOne(id: string) {
    return this.prisma.achievement.findUnique({
      where: { id },
    });
  }

  async findByKey(key: string) {
    return this.prisma.achievement.findUnique({
      where: { key },
    });
  }

  async update(id: string, dto: UpdateAchievementDto) {
    return this.prisma.achievement.update({
      where: { id },
      data: {
        name: dto.name,
        description: dto.description,
        icon: dto.icon,
        category: dto.category,
        tier: dto.tier,
        criteria: dto.criteria as Prisma.InputJsonValue,
        points: dto.points,
        is_active: dto.is_active,
      },
    });
  }

  async remove(id: string) {
    await this.prisma.achievement.delete({
      where: { id },
    });
    return { success: true };
  }

  // Get user's achievement progress
  async getUserAchievements(userId: string): Promise<AchievementProgress[]> {
    const achievements = await this.prisma.achievement.findMany({
      where: { is_active: true },
      orderBy: [{ category: 'asc' }, { tier: 'asc' }],
    });

    const earnedMap = new Map(
      (
        await this.prisma.userAchievement.findMany({
          where: { user_id: userId },
        })
      ).map((ua) => [ua.achievement_id, ua]),
    );

    return achievements.map((a) => {
      const earned = earnedMap.get(a.id);
      const criteria = a.criteria as { type: string; target: number };

      return {
        achievementId: a.id,
        key: a.key,
        name: a.name,
        description: a.description,
        icon: a.icon ?? undefined,
        category: a.category,
        tier: a.tier,
        points: a.points,
        earned: !!earned,
        earnedAt: earned?.earned_at,
        progress: 0, // Will be calculated based on criteria type
        target: criteria.target,
      };
    });
  }

  // Check and award achievements based on user progress
  async checkAndAwardAchievements(
    userId: string,
    type: AchievementType,
    currentValue: number,
  ): Promise<string[]> {
    // Get all active achievements and filter in memory for JSON criteria
    const allAchievements = await this.prisma.achievement.findMany({
      where: {
        is_active: true,
      },
    });

    // Filter achievements matching the criteria type
    const achievements = allAchievements.filter((a) => {
      const criteria = a.criteria as { type?: string; target?: number };
      return criteria.type === type;
    });

    const awardedIds: string[] = [];

    for (const achievement of achievements) {
      const criteria = achievement.criteria as { type: string; target: number };

      if (currentValue >= criteria.target) {
        // Check if already earned
        const existing = await this.prisma.userAchievement.findUnique({
          where: {
            user_id_achievement_id: {
              user_id: userId,
              achievement_id: achievement.id,
            },
          },
        });

        if (!existing) {
          await this.prisma.userAchievement.create({
            data: {
              user_id: userId,
              achievement_id: achievement.id,
            },
          });

          awardedIds.push(achievement.id);

          // Send real-time notification
          this.notificationGateway.sendBadgeEarned(userId, {
            badgeId: achievement.id,
            badgeName: achievement.name,
            badgeDescription: achievement.description,
            earnedAt: new Date().toISOString(),
          });

          this.logger.log(
            `Awarded achievement ${achievement.key} to user ${userId}`,
          );
        }
      }
    }

    return awardedIds;
  }

  // Initialize default achievements
  async seedDefaultAchievements(): Promise<number> {
    let count = 0;

    for (const achievement of this.defaultAchievements) {
      const existing = await this.findByKey(achievement.key);
      if (!existing) {
        await this.create(achievement);
        count++;
      }
    }

    this.logger.log(`Seeded ${count} default achievements`);
    return count;
  }

  // Get user's total achievement points
  async getUserPoints(userId: string): Promise<number> {
    const achievements = await this.prisma.userAchievement.findMany({
      where: { user_id: userId },
      include: { achievement: true },
    });

    return achievements.reduce(
      (sum, ua) => sum + (ua.achievement?.points ?? 0),
      0,
    );
  }
}
