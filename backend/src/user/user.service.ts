import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import type { User, Prisma } from '@prisma/client';
import { UserRole } from '@prisma/client';

interface PaginationOptions {
  page?: number;
  limit?: number;
}

interface LikeObject {
  constructor: { name: string };
  parameter: string;
}

interface WhereCondition {
  [key: string]: unknown;
}

// Re-export User type from Prisma for backward compatibility
export type { User } from '@prisma/client';

// Export UserRole for backward compatibility (it's an enum, so it's both type and value)
export { UserRole };

// Re-export User type for controllers that need it
export type UserWithRank = User & { rank: number | null };

@Injectable()
export class UserService {
  constructor(private prisma: PrismaService) {}

  findAll(
    where?: Prisma.UserWhereInput,
    options?: PaginationOptions,
  ): Promise<User[]> {
    const prismaWhere: Prisma.UserWhereInput = {};
    if (where) {
      if (Array.isArray(where)) {
        // Handle TypeORM OR conditions - convert to Prisma format
        // For search queries with Like, we need to extract the actual values
        const orConditions = where.map((w: WhereCondition) => {
          const condition: WhereCondition = {};
          for (const [key, value] of Object.entries(w)) {
            // Handle TypeORM Like objects
            if (
              value &&
              typeof value === 'object' &&
              'constructor' in value &&
              (value as LikeObject).constructor?.name === 'Like'
            ) {
              condition[key] = (value as LikeObject).parameter;
            } else {
              condition[key] = value;
            }
          }
          return condition;
        });
        prismaWhere.OR = orConditions as Prisma.UserWhereInput['OR'];
      } else {
        Object.assign(prismaWhere, where);
      }
    }

    const prismaOptions: Prisma.UserFindManyArgs = { where: prismaWhere };

    if (options?.page && options?.limit) {
      const skip = (options.page - 1) * options.limit;
      prismaOptions.skip = skip;
      prismaOptions.take = options.limit;
      // Order by joined_at desc by default for consistent pagination
      prismaOptions.orderBy = { joined_at: 'desc' as const };
    }

    return this.prisma.user.findMany(prismaOptions);
  }

  async count(where?: Prisma.UserWhereInput): Promise<number> {
    const prismaWhere: Prisma.UserWhereInput = {};
    if (where) {
      if (Array.isArray(where)) {
        const orConditions = where.map((w: WhereCondition) => {
          const condition: WhereCondition = {};
          for (const [key, value] of Object.entries(w)) {
            if (
              value &&
              typeof value === 'object' &&
              'constructor' in value &&
              (value as LikeObject).constructor?.name === 'Like'
            ) {
              condition[key] = (value as LikeObject).parameter;
            } else {
              condition[key] = value;
            }
          }
          return condition;
        });
        prismaWhere.OR = orConditions as Prisma.UserWhereInput['OR'];
      } else {
        Object.assign(prismaWhere, where);
      }
    }

    return this.prisma.user.count({ where: prismaWhere });
  }

  async getProfileWithRank(id: string): Promise<UserWithRank | null> {
    const user = await this.prisma.user.findUnique({
      where: { id },
    });
    if (!user) return null;

    const rankRecord = await this.prisma.globalRanking.findUnique({
      where: { user_id: id },
    });

    return {
      ...user,
      rank: rankRecord?.global_rank ?? null,
    };
  }

  findOne(id: string): Promise<User | null> {
    return this.prisma.user.findUnique({
      where: { id },
    });
  }

  async remove(id: string): Promise<void> {
    await this.prisma.user.delete({
      where: { id },
    });
  }

  findByUsername(username: string): Promise<User | null> {
    return this.prisma.user.findUnique({
      where: { username },
    });
  }

  findByEmail(email: string): Promise<User | null> {
    return this.prisma.user.findFirst({
      where: { email },
    });
  }

  async create(userData: Prisma.UserCreateInput): Promise<User> {
    return this.prisma.user.create({
      data: userData,
    });
  }

  async update(id: string, userData: Prisma.UserUpdateInput): Promise<User> {
    return this.prisma.user.update({
      where: { id },
      data: userData,
    });
  }

  async getUserStats(userId: string) {
    // 1. Get solved problems count grouped by difficulty
    const solvedSubmissions = await this.prisma.submission.findMany({
      where: {
        user_id: userId,
        status: 'Accepted',
      },
      distinct: ['problem_id'], // Count unique problems
      select: {
        problem: {
          select: {
            difficulty: true,
          },
        },
        created_at: true,
      },
    });

    const stats = {
      Easy: { count: 0, total: 0 },
      Medium: { count: 0, total: 0 },
      Hard: { count: 0, total: 0 },
    };

    solvedSubmissions.forEach((sub) => {
      const diff = sub.problem.difficulty;
      if (stats[diff]) {
        stats[diff].count++;
      }
    });

    // Get total counts per difficulty
    const problemCounts = await this.prisma.problem.groupBy({
      by: ['difficulty'],
      _count: {
        id: true,
      },
    });

    problemCounts.forEach((group) => {
      if (stats[group.difficulty]) {
        stats[group.difficulty].total = group._count.id;
      }
    });

    // 2. Calculate Streak
    // Get all accepted submissions dates, unique per day
    const distinctDates = new Set(
      solvedSubmissions.map((s) => s.created_at.toISOString().split('T')[0]),
    );

    let currentStreak = 0;
    const today = new Date().toISOString().split('T')[0];
    const yesterdayDate = new Date();
    yesterdayDate.setDate(yesterdayDate.getDate() - 1);
    const yesterday = yesterdayDate.toISOString().split('T')[0];

    // Check if user solved something today or yesterday to maintain streak
    if (distinctDates.has(today) || distinctDates.has(yesterday)) {
      let streak = 0;
      const checkDate = new Date();
      // Start checking from today if solved, else yesterday
      if (!distinctDates.has(today)) {
        checkDate.setDate(checkDate.getDate() - 1);
      }

      while (true) {
        const dateStr = checkDate.toISOString().split('T')[0];
        if (distinctDates.has(dateStr)) {
          streak++;
          checkDate.setDate(checkDate.getDate() - 1);
        } else {
          break;
        }
      }
      currentStreak = streak;
    }

    // 3. Activity Heatmap Data (Last 365 days)
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);

    // Map to daily counts
    const dailyActivity = new Map<string, number>();

    const activityRaw = await this.prisma.submission.findMany({
      where: {
        user_id: userId,
        created_at: { gte: oneYearAgo },
      },
      select: { created_at: true },
    });

    activityRaw.forEach((sub) => {
      const date = sub.created_at.toISOString().split('T')[0];
      dailyActivity.set(date, (dailyActivity.get(date) || 0) + 1);
    });

    const heatmapData: { date: string; level: number }[] = [];
    // Convert map to array format expected by frontend
    dailyActivity.forEach((count, date) => {
      // Simple level logic: 0=0, 1-2=1, 3-5=2, 6-9=3, 10+=4
      let level = 0;
      if (count > 0) level = 1;
      if (count > 2) level = 2;
      if (count > 5) level = 3;
      if (count > 9) level = 4;

      heatmapData.push({ date, level });
    });

    return {
      stats,
      streak: currentStreak,
      totalSolved: solvedSubmissions.length,
      heatmap: heatmapData,
    };
  }
}
