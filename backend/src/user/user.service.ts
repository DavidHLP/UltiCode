import {
  Injectable,
  Optional,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import type { User, Prisma } from '@prisma/client';
import { UserRole } from '@prisma/client';
import { AuditService } from '../admin/services/audit.service';
import { ChangePasswordDto } from './dto/change-password.dto';
import * as bcrypt from 'bcrypt';
import { CacheService } from '../cache/cache.service';

interface PaginationOptions {
  page?: number;
  limit?: number;
}

// Re-export User type from Prisma for backward compatibility
export type { User } from '@prisma/client';

// Export UserRole for backward compatibility (it's an enum, so it's both type and value)
export { UserRole };

// Re-export User type for controllers that need it
export type UserWithRank = User & { rank: number | null };

@Injectable()
export class UserService {
  constructor(
    private prisma: PrismaService,
    @Optional() private auditService: AuditService,
    private cacheService: CacheService,
  ) {}

  findAll(
    where?: Prisma.UserWhereInput,
    options?: PaginationOptions,
  ): Promise<User[]> {
    const prismaOptions: Prisma.UserFindManyArgs = { where: where || {} };

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
    return this.prisma.user.count({ where: where || {} });
  }

  async getProfileWithRank(id: string): Promise<UserWithRank | null> {
    const user = await this.prisma.user.findUnique({
      where: { id },
      include: {
        globalRanking: {
          select: { global_rank: true },
        },
      },
    });

    if (!user) return null;

    const { globalRanking, ...userWithoutRanking } = user;
    return {
      ...userWithoutRanking,
      rank: globalRanking?.global_rank ?? null,
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
    const cacheKey = `user_stats:${userId}`;
    const cached = await this.cacheService.get(cacheKey);
    if (cached) {
      return cached;
    }

    // 1. Get solved problems count grouped by difficulty using database aggregation
    const solvedByDifficulty = await this.prisma.problem.groupBy({
      by: ['difficulty'],
      where: {
        submissions: {
          some: {
            user_id: userId,
            status: 'Accepted',
          },
        },
      },
      _count: {
        id: true,
      },
    });

    // Get total problem counts per difficulty (for progress calculation)
    const [problemCounts, solvedSubmissionsForDates] = await Promise.all([
      this.prisma.problem.groupBy({
        by: ['difficulty'],
        _count: {
          id: true,
        },
      }),
      // Fetch submission dates only for streak and heatmap calculation
      this.prisma.submission.findMany({
        where: {
          user_id: userId,
          status: 'Accepted',
        },
        distinct: ['problem_id'],
        select: {
          created_at: true,
        },
      }),
    ]);

    const stats = {
      Easy: { count: 0, total: 0 },
      Medium: { count: 0, total: 0 },
      Hard: { count: 0, total: 0 },
    };

    solvedByDifficulty.forEach((group) => {
      if (stats[group.difficulty]) {
        stats[group.difficulty].count = group._count.id;
      }
    });

    problemCounts.forEach((group) => {
      if (stats[group.difficulty]) {
        stats[group.difficulty].total = group._count.id;
      }
    });

    // 2. Calculate Streak using database-aggregated dates
    const distinctDates = new Set(
      solvedSubmissionsForDates.map(
        (s) => s.created_at.toISOString().split('T')[0],
      ),
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

    // 3. Activity Heatmap Data (Last 365 days) - use raw SQL for efficient grouping
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);

    const heatmapRaw = await this.prisma.$queryRaw<
      Array<{ date: string; count: bigint }>
    >`
      SELECT DATE(created_at) as date, COUNT(*) as count
      FROM Submission
      WHERE user_id = ${userId}
        AND created_at >= ${oneYearAgo}
      GROUP BY DATE(created_at)
      ORDER BY date
    `;

    const heatmapData: { date: string; level: number }[] = [];
    heatmapRaw.forEach((row) => {
      const count = Number(row.count);
      let level = 0;
      if (count > 0) level = 1;
      if (count > 2) level = 2;
      if (count > 5) level = 3;
      if (count > 9) level = 4;

      heatmapData.push({ date: row.date, level });
    });

    const result = {
      stats,
      streak: currentStreak,
      totalSolved: solvedSubmissionsForDates.length,
      heatmap: heatmapData,
    };

    // Cache for 5 minutes
    await this.cacheService.set(cacheKey, result, 300);
    return result;
  }

  async changePassword(
    userId: string,
    changePasswordDto: ChangePasswordDto,
    performerId?: string,
  ): Promise<{ message: string }> {
    const hashedPassword = await bcrypt.hash(changePasswordDto.newPassword, 10);

    await this.prisma.$transaction(async (tx) => {
      // Verify user exists and has password
      const user = await tx.user.findUnique({
        where: { id: userId },
      });

      if (!user) {
        throw new NotFoundException('User not found');
      }

      if (!user.password) {
        throw new BadRequestException('User has no password set');
      }

      // Verify current password
      const isCurrentPasswordValid = await bcrypt.compare(
        changePasswordDto.currentPassword,
        user.password,
      );

      if (!isCurrentPasswordValid) {
        throw new BadRequestException('Current password is incorrect');
      }

      // Update password
      await tx.user.update({
        where: { id: userId },
        data: { password: hashedPassword },
      });

      // Create audit log within transaction
      if (this.auditService) {
        await tx.auditLog.create({
          data: {
            performer_id: performerId || userId,
            action: 'CHANGE_PASSWORD',
            entity_type: 'USER',
            entity_id: userId,
            user_id: userId,
          },
        });
      }
    });

    return { message: 'Password changed successfully' };
  }

  async getUserSkills(userId: string) {
    const cacheKey = `user_skills:${userId}`;
    const cached = await this.cacheService.get(cacheKey);
    if (cached) {
      return cached;
    }

    // Get solved problems with their tags
    const solvedProblemsWithTags = await this.prisma.problem.findMany({
      where: {
        submissions: {
          some: {
            user_id: userId,
            status: 'Accepted',
          },
        },
      },
      include: {
        tagRelations: {
          include: {
            tag: {
              select: {
                id: true,
                label: true,
                slug: true,
              },
            },
          },
        },
      },
    });

    // Aggregate skills by tag
    const skillsMap = new Map<
      string,
      { tagName: string; tagSlug: string; count: number }
    >();

    for (const problem of solvedProblemsWithTags) {
      for (const tagRelation of problem.tagRelations) {
        const tag = tagRelation.tag;
        const existing = skillsMap.get(tag.id);
        if (existing) {
          existing.count++;
        } else {
          skillsMap.set(tag.id, {
            tagName: tag.label,
            tagSlug: tag.slug || tag.id,
            count: 1,
          });
        }
      }
    }

    // Convert to array and sort by count
    const skills = Array.from(skillsMap.values())
      .sort((a, b) => b.count - a.count)
      .slice(0, 12); // Top 12 skills for the radar chart

    const result = {
      skills,
      totalSolved: solvedProblemsWithTags.length,
    };

    // Cache for 5 minutes
    await this.cacheService.set(cacheKey, result, 300);
    return result;
  }
}
