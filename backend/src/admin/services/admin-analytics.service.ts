import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import {
  AnalyticsQueryDto,
  AnalyticsReportType,
  AnalyticsPeriod,
  UserActivityReport,
  ProblemCompletionReport,
  ContestParticipationReport,
  RevenueReport,
  PerformanceReport,
} from '../dto/analytics.dto';

@Injectable()
export class AdminAnalyticsService {
  constructor(private prisma: PrismaService) {}

  async getReport(query: AnalyticsQueryDto) {
    const { reportType, period, startDate, endDate, days } = query;

    const dateRange = this.getDateRange(
      period || AnalyticsPeriod.MONTH,
      days || 30,
      startDate,
      endDate,
    );

    switch (reportType) {
      case AnalyticsReportType.USER_ACTIVITY:
        return this.getUserActivityReport(dateRange);
      case AnalyticsReportType.PROBLEM_COMPLETION:
        return this.getProblemCompletionReport(dateRange);
      case AnalyticsReportType.CONTEST_PARTICIPATION:
        return this.getContestParticipationReport(dateRange);
      case AnalyticsReportType.REVENUE:
        return this.getRevenueReport(dateRange);
      case AnalyticsReportType.PERFORMANCE:
        return this.getPerformanceReport();
      default:
        throw new Error(`Unknown report type: ${String(reportType)}`);
    }
  }

  private getDateRange(
    period: AnalyticsPeriod,
    days: number,
    startDate?: string,
    endDate?: string,
  ): { start: Date; end: Date } {
    const end = endDate ? new Date(endDate) : new Date();
    let start: Date;

    if (startDate) {
      start = new Date(startDate);
    } else {
      const daysBack = this.getDaysFromPeriod(period, days);
      start = new Date(end);
      start.setDate(start.getDate() - daysBack);
    }

    return { start, end };
  }

  private getDaysFromPeriod(
    period: AnalyticsPeriod,
    defaultDays: number,
  ): number {
    switch (period) {
      case AnalyticsPeriod.DAY:
        return 1;
      case AnalyticsPeriod.WEEK:
        return 7;
      case AnalyticsPeriod.MONTH:
        return 30;
      case AnalyticsPeriod.QUARTER:
        return 90;
      case AnalyticsPeriod.YEAR:
        return 365;
      default:
        return defaultDays;
    }
  }

  private async getUserActivityReport(dateRange: {
    start: Date;
    end: Date;
  }): Promise<UserActivityReport> {
    // Daily active users based on last_login_at
    const dailyActiveQuery = this.prisma.$queryRaw<
      Array<{ date: Date; count: bigint }>
    >`
      SELECT DATE(last_login_at) as date, COUNT(DISTINCT id) as count
      FROM users
      WHERE last_login_at >= ${dateRange.start} AND last_login_at <= ${dateRange.end}
      GROUP BY DATE(last_login_at)
      ORDER BY date ASC
    `;

    // Get peak active hours
    const peakHoursQuery = this.prisma.$queryRaw<
      Array<{ hour: number; count: bigint }>
    >`
      SELECT HOUR(last_login_at) as hour, COUNT(*) as count
      FROM users
      WHERE last_login_at >= ${dateRange.start} AND last_login_at <= ${dateRange.end}
      GROUP BY HOUR(last_login_at)
      ORDER BY count DESC
      LIMIT 24
    `;

    // Get top active users based on submissions
    const topUsers = await this.prisma.user.findMany({
      where: {
        is_active: true,
        last_login_at: { gte: dateRange.start, lte: dateRange.end },
      },
      select: {
        id: true,
        username: true,
        _count: {
          select: { submissions: true },
        },
        last_login_at: true,
      },
      orderBy: {
        submissions: { _count: 'desc' },
      },
      take: 10,
    });

    // Calculate retention
    const totalUsers = await this.prisma.user.count({
      where: { is_active: true },
    });

    const usersActiveDay1 = await this.prisma.user.count({
      where: {
        is_active: true,
        last_login_at: {
          gte: new Date(Date.now() - 24 * 60 * 60 * 1000),
        },
      },
    });

    const usersActiveDay7 = await this.prisma.user.count({
      where: {
        is_active: true,
        last_login_at: {
          gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
        },
      },
    });

    const usersActiveDay30 = await this.prisma.user.count({
      where: {
        is_active: true,
        last_login_at: {
          gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
        },
      },
    });

    const [dailyActive, peakHours] = await Promise.all([
      dailyActiveQuery,
      peakHoursQuery,
    ]);

    // Format daily active users
    const activeUsersDaily = dailyActive.map((item) => ({
      date: item.date.toISOString().split('T')[0],
      count: Number(item.count),
    }));

    // Calculate weekly aggregation
    const weeklyMap = new Map<string, number>();
    for (const item of activeUsersDaily) {
      const date = new Date(item.date);
      const weekStart = new Date(date);
      weekStart.setDate(date.getDate() - date.getDay());
      const weekKey = weekStart.toISOString().split('T')[0];
      weeklyMap.set(weekKey, (weeklyMap.get(weekKey) || 0) + item.count);
    }
    const activeUsersWeekly = Array.from(weeklyMap.entries())
      .map(([week, count]) => ({ week, count }))
      .sort((a, b) => a.week.localeCompare(b.week));

    // Format peak hours
    const peakActiveHours = peakHours.map((item) => ({
      hour: item.hour,
      count: Number(item.count),
    }));

    return {
      activeUsersDaily,
      activeUsersWeekly,
      averageSessionDuration: 0,
      peakActiveHours,
      userRetention: {
        day1: totalUsers > 0 ? (usersActiveDay1 / totalUsers) * 100 : 0,
        day7: totalUsers > 0 ? (usersActiveDay7 / totalUsers) * 100 : 0,
        day30: totalUsers > 0 ? (usersActiveDay30 / totalUsers) * 100 : 0,
      },
      topActiveUsers: topUsers.map((user) => ({
        userId: user.id,
        username: user.username,
        loginCount: user._count.submissions,
        lastActive: user.last_login_at || new Date(),
      })),
    };
  }

  private async getProblemCompletionReport(dateRange: {
    start: Date;
    end: Date;
  }): Promise<ProblemCompletionReport> {
    // Get total attempts and successful
    const [totalAttempts, successfulAttempts] = await Promise.all([
      this.prisma.submission.count({
        where: {
          created_at: { gte: dateRange.start, lte: dateRange.end },
        },
      }),
      this.prisma.submission.count({
        where: {
          created_at: { gte: dateRange.start, lte: dateRange.end },
          status: 'ACCEPTED',
        },
      }),
    ]);

    // Get problems with their completion stats
    const problemsWithStats = await this.prisma.problem.findMany({
      where: { is_deleted: false },
      select: {
        id: true,
        title: true,
        difficulty: true,
        _count: {
          select: {
            submissions: true,
            solutions: true,
          },
        },
        tagRelations: {
          select: {
            tag: {
              select: { id: true, label: true },
            },
          },
        },
      },
    });

    // Calculate by difficulty
    const difficultyStats = new Map<
      string,
      { total: number; completed: number }
    >();
    for (const problem of problemsWithStats) {
      const diff = problem.difficulty;
      if (!difficultyStats.has(diff)) {
        difficultyStats.set(diff, { total: 0, completed: 0 });
      }
      const stats = difficultyStats.get(diff)!;
      stats.total += problem._count.submissions;
      stats.completed += problem._count.solutions;
    }

    const byDifficulty = Array.from(difficultyStats.entries()).map(
      ([difficulty, stats]) => ({
        difficulty,
        total: stats.total,
        completed: stats.completed,
        rate: stats.total > 0 ? (stats.completed / stats.total) * 100 : 0,
      }),
    );

    // Calculate by tag
    const tagStats = new Map<
      string,
      { label: string; total: number; completed: number }
    >();
    for (const problem of problemsWithStats) {
      for (const tagRel of problem.tagRelations) {
        const tag = tagRel.tag;
        if (!tagStats.has(tag.id)) {
          tagStats.set(tag.id, { label: tag.label, total: 0, completed: 0 });
        }
        const stats = tagStats.get(tag.id)!;
        stats.total += problem._count.submissions;
        stats.completed += problem._count.solutions;
      }
    }

    const byTag = Array.from(tagStats.entries())
      .map(([tagId, stats]) => ({
        tagId,
        label: stats.label,
        total: stats.total,
        completed: stats.completed,
        rate: stats.total > 0 ? (stats.completed / stats.total) * 100 : 0,
      }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 20);

    // Trending problems (most attempts in period)
    const trendingSubmissions = await this.prisma.submission.groupBy({
      by: ['problem_id'],
      where: {
        created_at: { gte: dateRange.start, lte: dateRange.end },
      },
      _count: {
        id: true,
      },
      orderBy: {
        _count: { id: 'desc' },
      },
      take: 10,
    });

    const trendingProblemIds = trendingSubmissions.map((s) => s.problem_id);
    const trendingProblemsData = await this.prisma.problem.findMany({
      where: { id: { in: trendingProblemIds } },
      select: { id: true, title: true },
    });

    const trendingProblems = await Promise.all(
      trendingSubmissions.map(async (s) => {
        const problem = trendingProblemsData.find((p) => p.id === s.problem_id);
        const accepted = await this.prisma.submission.count({
          where: {
            problem_id: s.problem_id,
            created_at: { gte: dateRange.start, lte: dateRange.end },
            status: 'ACCEPTED',
          },
        });
        const attemptCount = s._count.id;
        return {
          problemId: s.problem_id.toString(),
          title: problem?.title || 'Unknown',
          attempts: attemptCount,
          completionRate:
            attemptCount > 0 ? (accepted / attemptCount) * 100 : 0,
        };
      }),
    );

    // Hardest problems
    const hardestProblems = problemsWithStats
      .filter((p) => p._count.submissions > 10)
      .map((p) => ({
        problemId: p.id.toString(),
        title: p.title,
        difficulty: p.difficulty,
        completionRate:
          p._count.submissions > 0
            ? (p._count.solutions / p._count.submissions) * 100
            : 0,
      }))
      .sort((a, b) => a.completionRate - b.completionRate)
      .slice(0, 10);

    return {
      totalAttempts,
      successfulAttempts,
      overallCompletionRate:
        totalAttempts > 0 ? (successfulAttempts / totalAttempts) * 100 : 0,
      byDifficulty,
      byTag,
      trendingProblems,
      hardestProblems,
    };
  }

  private async getContestParticipationReport(dateRange: {
    start: Date;
    end: Date;
  }): Promise<ContestParticipationReport> {
    const contests = await this.prisma.contest.findMany({
      where: {
        created_at: { gte: dateRange.start, lte: dateRange.end },
      },
      select: {
        id: true,
        title: true,
        contest_type: true,
        _count: {
          select: { participants: true },
        },
      },
    });

    const totalContests = contests.length;
    const totalParticipants = contests.reduce(
      (sum, c) => sum + c._count.participants,
      0,
    );
    const averageParticipantsPerContest =
      totalContests > 0 ? totalParticipants / totalContests : 0;

    // Participation trend (simplified)
    const contestsByDate = await this.prisma.contest.groupBy({
      by: ['created_at'],
      where: {
        created_at: { gte: dateRange.start, lte: dateRange.end },
      },
      _count: true,
    });

    const participationTrend = contestsByDate.map((item) => ({
      date: item.created_at.toISOString().split('T')[0],
      contests: item._count,
      participants: 0,
    }));

    // By contest type
    const typeMap = new Map<string, { count: number; participants: number }>();
    for (const contest of contests) {
      const type = contest.contest_type || 'public';
      if (!typeMap.has(type)) {
        typeMap.set(type, { count: 0, participants: 0 });
      }
      const stats = typeMap.get(type)!;
      stats.count++;
      stats.participants += contest._count.participants;
    }

    const byType = Array.from(typeMap.entries()).map(([type, stats]) => ({
      type,
      count: stats.count,
      avgParticipants: stats.count > 0 ? stats.participants / stats.count : 0,
    }));

    // Top contests
    const topContests = contests
      .sort((a, b) => b._count.participants - a._count.participants)
      .slice(0, 10)
      .map((c) => ({
        contestId: c.id,
        title: c.title,
        participants: c._count.participants,
        completionRate: 0,
      }));

    // Virtual participation
    const virtualParticipants = await this.prisma.contestParticipant.count({
      where: {
        is_virtual: true,
        registered_at: { gte: dateRange.start, lte: dateRange.end },
      },
    });

    return {
      totalContests,
      totalParticipants,
      averageParticipantsPerContest,
      participationTrend,
      byType,
      topContests,
      virtualParticipation: {
        total: virtualParticipants,
        averageCompletionRate: 0,
      },
    };
  }

  private async getRevenueReport(_dateRange: {
    start: Date;
    end: Date;
  }): Promise<RevenueReport> {
    // Get subscription statistics
    const subscriptions = await this.prisma.subscription.findMany({
      select: {
        id: true,
        plan: true,
        status: true,
        stripe_price_id: true,
        created_at: true,
        stripe_current_period_end: true,
      },
    });

    const activeSubscriptions = subscriptions.filter(
      (s) => s.status === 'ACTIVE',
    );
    const subscriberCount = activeSubscriptions.length;

    // Mock pricing
    const planPrices: Record<string, number> = {
      premium_monthly: 9.99,
      premium_yearly: 99.99,
    };

    // Calculate MRR
    let mrr = 0;
    for (const sub of activeSubscriptions) {
      const priceId = sub.stripe_price_id || '';
      if (priceId.includes('yearly')) {
        mrr += planPrices['premium_yearly'] / 12;
      } else {
        mrr += planPrices['premium_monthly'];
      }
    }

    const arr = mrr * 12;

    // Get total users for ARPU
    const totalUsers = await this.prisma.user.count();
    const arpu = totalUsers > 0 ? arr / totalUsers : 0;

    // Revenue by plan
    const planMap = new Map<string, { subscribers: number; revenue: number }>();
    for (const sub of activeSubscriptions) {
      const plan = sub.plan || 'FREE';
      if (!planMap.has(plan)) {
        planMap.set(plan, { subscribers: 0, revenue: 0 });
      }
      const stats = planMap.get(plan)!;
      stats.subscribers++;
      const price = planPrices[plan] || 0;
      if (plan.includes('yearly')) {
        stats.revenue += price / 12;
      } else {
        stats.revenue += price;
      }
    }

    const byPlan = Array.from(planMap.entries()).map(([plan, stats]) => ({
      plan,
      subscribers: stats.subscribers,
      revenue: stats.revenue,
    }));

    // Churn rate
    const canceledCount = subscriptions.filter(
      (s) => s.status === 'CANCELLED',
    ).length;
    const churnRate =
      subscriptions.length > 0
        ? (canceledCount / subscriptions.length) * 100
        : 0;

    const conversionRate =
      totalUsers > 0 ? (subscriberCount / totalUsers) * 100 : 0;

    return {
      totalRevenue: arr,
      mrr,
      arr,
      arpu,
      subscriberCount,
      churnRate,
      revenueTrend: [],
      byPlan,
      conversionRate,
    };
  }

  private async getPerformanceReport(): Promise<PerformanceReport> {
    const uptime = process.uptime();

    const memUsage = process.memoryUsage();
    const totalMemory = memUsage.heapTotal + memUsage.external;
    const usedMemory = memUsage.heapUsed;

    const last24h = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const submissionsLast24h = await this.prisma.submission.count({
      where: { created_at: { gte: last24h } },
    });

    const errorSubmissions = await this.prisma.submission.count({
      where: {
        created_at: { gte: last24h },
        status: 'SYSTEM_ERROR',
      },
    });

    const errorRate =
      submissionsLast24h > 0
        ? (errorSubmissions / submissionsLast24h) * 100
        : 0;

    return {
      systemUptime: uptime,
      averageResponseTime: 0,
      errorRate,
      throughput: submissionsLast24h,
      resourceUsage: {
        cpu: 0,
        memory: totalMemory > 0 ? (usedMemory / totalMemory) * 100 : 0,
        disk: 0,
      },
      slowestEndpoints: [],
      errorBreakdown: [],
      cacheHitRate: 0,
    };
  }
}
