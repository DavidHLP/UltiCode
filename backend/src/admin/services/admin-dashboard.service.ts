import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../../prisma.service';
import { ChartPeriod, ChartMetric } from '../dto/dashboard.dto';

@Injectable()
export class AdminDashboardService {
  constructor(
    private prisma: PrismaService,
    private configService: ConfigService,
  ) {}

  /**
   * Get overall dashboard statistics
   * Real-time data from the database
   */
  async getDashboardStats() {
    const now = new Date();
    const todayStart = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate(),
    );
    const weekStart = new Date(todayStart);
    weekStart.setDate(weekStart.getDate() - 7);
    const monthStart = new Date(todayStart);
    monthStart.setDate(monthStart.getDate() - 30);

    const [
      totalUsers,
      activeUsers,
      bannedUsers,
      usersByRole,
      problemStats,
      contestStats,
      submissionStats,
      solutionStats,
      forumStats,
    ] = await Promise.all([
      // User stats
      this.prisma.user.count(),
      this.prisma.user.count({ where: { is_active: true } }),
      this.prisma.user.count({ where: { is_banned: true } }),
      this.getUsersByRole(),
      // Problem stats
      this.getProblemStats(),
      // Contest stats
      this.getContestStats(now),
      // Submission stats
      this.getSubmissionStats(todayStart, weekStart, monthStart),
      // Solution stats
      this.getSolutionStats(),
      // Forum stats
      this.getForumStats(),
    ]);

    // Active users by time period (based on last_login_at)
    const [activeToday, activeWeek, activeMonth] = await Promise.all([
      this.prisma.user.count({
        where: {
          is_active: true,
          last_login_at: { gte: todayStart },
        },
      }),
      this.prisma.user.count({
        where: {
          is_active: true,
          last_login_at: { gte: weekStart },
        },
      }),
      this.prisma.user.count({
        where: {
          is_active: true,
          last_login_at: { gte: monthStart },
        },
      }),
    ]);

    // Get process uptime
    const uptime = process.uptime();

    return {
      users: {
        total: totalUsers,
        active: activeUsers,
        activeToday,
        activeWeek,
        activeMonth,
        banned: bannedUsers,
        byRole: usersByRole,
      },
      problems: problemStats,
      contests: contestStats,
      submissions: submissionStats,
      solutions: solutionStats,
      forum: forumStats,
      system: {
        uptime,
        version: this.configService.get<string>('npm_package_version', '1.0.0'),
      },
    };
  }

  /**
   * Get chart data for time-series visualization
   */
  async getChartStats(params: {
    period: ChartPeriod;
    metric: ChartMetric;
    days: number;
    startDate?: Date;
    endDate?: Date;
  }) {
    const { period, metric, days, startDate, endDate } = params;

    const start =
      startDate || new Date(Date.now() - days * 24 * 60 * 60 * 1000);
    const end = endDate || new Date();

    const data = await this.getChartDataByMetric(metric, start, end, period);

    return {
      metric,
      period,
      data,
      startDate: start,
      endDate: end,
    };
  }

  /**
   * Get user distribution by role
   */
  private async getUsersByRole(): Promise<Record<string, number>> {
    const usersByRole = await this.prisma.user.groupBy({
      by: ['role'],
      _count: true,
    });

    return usersByRole.reduce(
      (acc, item) => {
        acc[item.role] = item._count;
        return acc;
      },
      {} as Record<string, number>,
    );
  }

  /**
   * Get problem statistics
   */
  private async getProblemStats() {
    const [total, published, unpublished, byDifficulty, byStatus] =
      await Promise.all([
        this.prisma.problem.count({ where: { is_deleted: false } }),
        this.prisma.problem.count({
          where: { is_published: true, is_deleted: false },
        }),
        this.prisma.problem.count({
          where: { is_published: false, is_deleted: false },
        }),
        this.prisma.problem.groupBy({
          by: ['difficulty'],
          where: { is_deleted: false },
          _count: true,
        }),
        this.prisma.problem.groupBy({
          by: ['status'],
          where: { is_deleted: false },
          _count: true,
        }),
      ]);

    return {
      total,
      published,
      unpublished,
      byDifficulty: byDifficulty.reduce(
        (acc, item) => {
          acc[item.difficulty] = item._count;
          return acc;
        },
        {} as Record<string, number>,
      ),
      byStatus: byStatus.reduce(
        (acc, item) => {
          acc[item.status] = item._count;
          return acc;
        },
        {} as Record<string, number>,
      ),
    };
  }

  /**
   * Get contest statistics
   */
  private async getContestStats(_now: Date) {
    const [total, upcoming, running, finished] = await Promise.all([
      this.prisma.contest.count(),
      this.prisma.contest.count({
        where: { status: 'upcoming' },
      }),
      this.prisma.contest.count({
        where: { status: 'running' },
      }),
      this.prisma.contest.count({
        where: { status: 'finished' },
      }),
    ]);

    return { total, upcoming, running, finished };
  }

  /**
   * Get submission statistics
   */
  private async getSubmissionStats(
    todayStart: Date,
    weekStart: Date,
    monthStart: Date,
  ) {
    const [total, today, week, month, accepted, totalForRate] =
      await Promise.all([
        this.prisma.submission.count(),
        this.prisma.submission.count({
          where: { created_at: { gte: todayStart } },
        }),
        this.prisma.submission.count({
          where: { created_at: { gte: weekStart } },
        }),
        this.prisma.submission.count({
          where: { created_at: { gte: monthStart } },
        }),
        this.prisma.submission.count({
          where: { status: 'ACCEPTED' },
        }),
        this.prisma.submission.count(),
      ]);

    return {
      total,
      today,
      week,
      month,
      acceptanceRate: totalForRate > 0 ? (accepted / totalForRate) * 100 : 0,
    };
  }

  /**
   * Get solution statistics
   */
  private async getSolutionStats() {
    const [total, published, flagged] = await Promise.all([
      this.prisma.solution.count({ where: { is_deleted: false } }),
      this.prisma.solution.count({
        where: { is_published: true, is_deleted: false },
      }),
      this.prisma.solution.count({
        where: { is_flagged: true, is_deleted: false },
      }),
    ]);

    return { total, published, flagged };
  }

  /**
   * Get forum statistics
   */
  private async getForumStats() {
    const [posts, comments, communities, flaggedPosts, flaggedComments] =
      await Promise.all([
        this.prisma.forumPost.count({ where: { is_deleted: false } }),
        this.prisma.forumComment.count(), // No is_deleted on comments
        this.prisma.forumCommunity.count(),
        this.prisma.forumPost.count({
          where: { is_flagged: true, is_deleted: false },
        }),
        this.prisma.forumComment.count(), // No is_flagged on comments
      ]);

    return {
      posts,
      comments,
      communities,
      flaggedPosts,
      flaggedComments,
    };
  }

  /**
   * Get chart data based on metric type
   */
  private async getChartDataByMetric(
    metric: ChartMetric,
    startDate: Date,
    endDate: Date,
    _period: ChartPeriod,
  ) {
    switch (metric) {
      case ChartMetric.USERS:
        return this.prisma.user.groupBy({
          by: ['joined_at'], // User uses joined_at, not created_at
          where: {
            joined_at: { gte: startDate, lte: endDate },
          },
          _count: true,
          orderBy: { joined_at: 'asc' },
        });

      case ChartMetric.SUBMISSIONS:
        return this.prisma.submission.groupBy({
          by: ['created_at'],
          where: {
            created_at: { gte: startDate, lte: endDate },
          },
          _count: true,
          orderBy: { created_at: 'asc' },
        });

      case ChartMetric.PROBLEMS:
        // Problems don't have created_at in Schema? Schema says: NO created_at on Problem!
        // It has published_at.
        return this.prisma.problem.groupBy({
          by: ['published_at'],
          where: {
            published_at: { gte: startDate, lte: endDate },
            is_deleted: false,
          },
          _count: true,
          orderBy: { published_at: 'asc' },
        });

      case ChartMetric.CONTESTS:
        return this.prisma.contest.groupBy({
          by: ['created_at'],
          where: {
            created_at: { gte: startDate, lte: endDate },
          },
          _count: true,
          orderBy: { created_at: 'asc' },
        });

      case ChartMetric.SOLUTIONS:
        return this.prisma.solution.groupBy({
          by: ['created_at'],
          where: {
            created_at: { gte: startDate, lte: endDate },
            is_deleted: false,
          },
          _count: true,
          orderBy: { created_at: 'asc' },
        });

      case ChartMetric.FORUM_POSTS:
        return this.prisma.forumPost.groupBy({
          by: ['created_at'],
          where: {
            created_at: { gte: startDate, lte: endDate },
            is_deleted: false,
          },
          _count: true,
          orderBy: { created_at: 'asc' },
        });

      default:
        return [];
    }
  }

  private getGroupByField(_period: ChartPeriod): string {
    return 'created_at';
  }

  private getOrderByField(_period: ChartPeriod): string {
    return 'created_at';
  }
}
