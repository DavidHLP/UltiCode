import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma.service';

export interface ProblemStats {
  problemIndex: string;
  problemTitle: string;
  solvedCount: number;
  submissionCount: number;
  acceptanceRate: number;
  avgAttempts: number;
}

export interface ScoreDistributionRange {
  min: number;
  max: number;
  count: number;
}

export interface TopUser {
  rank: number;
  userId: string;
  username: string;
  score: number;
  time: number | null;
  solvedCount: number;
}

export interface ContestReport {
  contestId: string;
  contestTitle: string;
  totalRegistered: number;
  totalParticipated: number;
  completionRate: number;
  problemStats: ProblemStats[];
  scoreDistribution: ScoreDistributionRange[];
  topUsers: TopUser[];
  generatedAt: Date;
}

export interface UserPerformanceEntry {
  contestId: string;
  contestTitle: string;
  contestDate: Date;
  rank: number;
  totalParticipants: number;
  score: number;
  solvedCount: number;
  ratingBefore: number;
  ratingAfter: number;
  ratingChange: number;
  isVirtual: boolean;
}

@Injectable()
export class AnalyticsService {
  private readonly logger = new Logger(AnalyticsService.name);
  private readonly DEFAULT_HISTORY_LIMIT = 20;
  private readonly SCORE_DISTRIBUTION_RANGES = 5;

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Generate comprehensive contest analytics report
   * @param contestId Contest ID to analyze
   * @returns Complete contest report or null if contest not found
   */
  async generateContestReport(
    contestId: string,
  ): Promise<ContestReport | null> {
    this.logger.log(`Generating analytics report for contest: ${contestId}`);

    // Get contest info
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
      select: {
        id: true,
        title: true,
        registered_count: true,
        participant_count: true,
      },
    });

    if (!contest) {
      this.logger.warn(`Contest not found: ${contestId}`);
      return null;
    }

    // Get participant counts
    const [totalRegistered, totalParticipated] = await Promise.all([
      this.prisma.contestParticipant.count({
        where: { contest_id: contestId },
      }),
      this.prisma.contestParticipant.count({
        where: {
          contest_id: contestId,
          status: { in: ['STARTED', 'FINISHED'] },
        },
      }),
    ]);

    // Calculate completion rate
    const completionRate =
      totalRegistered > 0 ? totalParticipated / totalRegistered : 0;

    // Get problem stats
    const problemStats = await this.getProblemStats(contestId);

    // Get all rankings for score distribution and top users
    const rankings = await this.prisma.contestRanking.findMany({
      where: { contest_id: contestId, is_virtual: false },
      include: {
        user: {
          select: { id: true, username: true },
        },
      },
      orderBy: { rank: 'asc' },
    });

    // Calculate score distribution
    const scoreDistribution = this.calculateScoreDistribution(rankings);

    // Get top users (top 10)
    const topUsers: TopUser[] = rankings.slice(0, 10).map((r) => ({
      rank: r.rank,
      userId: r.user_id,
      username: r.user.username,
      score: r.total_score,
      time: r.finish_time,
      solvedCount: r.solved_count,
    }));

    const report: ContestReport = {
      contestId: contest.id,
      contestTitle: contest.title,
      totalRegistered,
      totalParticipated,
      completionRate: Math.round(completionRate * 1000) / 1000, // Round to 3 decimals
      problemStats,
      scoreDistribution,
      topUsers,
      generatedAt: new Date(),
    };

    // Store the report in the database
    await this.storeReport(contestId, report);

    this.logger.log(`Analytics report generated for contest: ${contestId}`);
    return report;
  }

  /**
   * Get stored analytics from database
   * @param contestId Contest ID
   * @returns Stored report or null if not found
   */
  async getStoredReport(contestId: string): Promise<ContestReport | null> {
    const analytics = await this.prisma.contestAnalytics.findUnique({
      where: { contest_id: contestId },
      include: {
        contest: {
          select: { title: true },
        },
      },
    });

    if (!analytics) {
      return null;
    }

    return {
      contestId: analytics.contest_id,
      contestTitle: analytics.contest.title,
      totalRegistered: analytics.total_registered,
      totalParticipated: analytics.total_participated,
      completionRate: analytics.completion_rate,
      problemStats:
        (analytics.problem_stats as unknown as ProblemStats[]) || [],
      scoreDistribution:
        (analytics.score_distribution as unknown as ScoreDistributionRange[]) ||
        [],
      topUsers: (analytics.top_users as unknown as TopUser[]) || [],
      generatedAt: analytics.generated_at,
    };
  }

  /**
   * Get user's contest performance history
   * @param userId User ID
   * @param limit Maximum number of entries to return
   * @returns Array of performance entries
   */
  async getUserPerformanceHistory(
    userId: string,
    limit: number = this.DEFAULT_HISTORY_LIMIT,
  ): Promise<UserPerformanceEntry[]> {
    const rankings = await this.prisma.contestRanking.findMany({
      where: { user_id: userId },
      take: limit,
      orderBy: {
        contest: {
          start_time: 'desc',
        },
      },
      include: {
        contest: {
          select: {
            title: true,
            start_time: true,
            _count: {
              select: { rankings: { where: { is_virtual: false } } },
            },
          },
        },
      },
    });

    return rankings.map((r) => ({
      contestId: r.contest_id,
      contestTitle: r.contest.title,
      contestDate: r.contest.start_time,
      rank: r.rank,
      totalParticipants: r.contest._count.rankings,
      score: r.total_score,
      solvedCount: r.solved_count,
      ratingBefore: r.rating_before,
      ratingAfter: r.rating_after,
      ratingChange: r.rating_change,
      isVirtual: r.is_virtual,
    }));
  }

  /**
   * Get problem statistics for a contest
   */
  private async getProblemStats(contestId: string): Promise<ProblemStats[]> {
    const problems = await this.prisma.contestProblem.findMany({
      where: { contest_id: contestId },
      include: {
        problem: {
          select: { id: true, title: true },
        },
      },
      orderBy: { problem_index: 'asc' },
    });

    return problems.map((p) => {
      const acceptanceRate =
        p.submission_count > 0 ? p.solved_count / p.submission_count : 0;

      const avgAttempts =
        p.solved_count > 0 ? p.submission_count / p.solved_count : 0;

      return {
        problemIndex: p.problem_index,
        problemTitle: p.problem.title,
        solvedCount: p.solved_count,
        submissionCount: p.submission_count,
        acceptanceRate: Math.round(acceptanceRate * 1000) / 1000,
        avgAttempts: Math.round(avgAttempts * 10) / 10,
      };
    });
  }

  /**
   * Calculate score distribution ranges
   */
  private calculateScoreDistribution(
    rankings: { total_score: number }[],
  ): ScoreDistributionRange[] {
    if (rankings.length === 0) {
      return [];
    }

    const scores = rankings.map((r) => r.total_score);
    const maxScore = Math.max(...scores);
    const minScore = Math.min(...scores);
    const rangeSize = Math.ceil(
      (maxScore - minScore + 1) / this.SCORE_DISTRIBUTION_RANGES,
    );

    const distribution: ScoreDistributionRange[] = [];

    for (let i = 0; i < this.SCORE_DISTRIBUTION_RANGES; i++) {
      const rangeMin = minScore + i * rangeSize;
      const rangeMax =
        i === this.SCORE_DISTRIBUTION_RANGES - 1
          ? maxScore
          : minScore + (i + 1) * rangeSize - 1;

      const count = scores.filter((s) => s >= rangeMin && s <= rangeMax).length;

      if (count > 0) {
        distribution.push({
          min: rangeMin,
          max: rangeMax,
          count,
        });
      }
    }

    return distribution;
  }

  /**
   * Store the report in the database
   */
  private async storeReport(
    contestId: string,
    report: ContestReport,
  ): Promise<void> {
    await this.prisma.contestAnalytics.upsert({
      where: { contest_id: contestId },
      create: {
        contest_id: contestId,
        total_registered: report.totalRegistered,
        total_participated: report.totalParticipated,
        completion_rate: report.completionRate,
        problem_stats: report.problemStats as unknown as Prisma.InputJsonValue,
        score_distribution:
          report.scoreDistribution as unknown as Prisma.InputJsonValue,
        time_distribution: Prisma.JsonNull,
        top_users: report.topUsers as unknown as Prisma.InputJsonValue,
        generated_at: report.generatedAt,
      },
      update: {
        total_registered: report.totalRegistered,
        total_participated: report.totalParticipated,
        completion_rate: report.completionRate,
        problem_stats: report.problemStats as unknown as Prisma.InputJsonValue,
        score_distribution:
          report.scoreDistribution as unknown as Prisma.InputJsonValue,
        top_users: report.topUsers as unknown as Prisma.InputJsonValue,
        generated_at: report.generatedAt,
      },
    });
  }
}
