import { Injectable, Logger } from '@nestjs/common';
import { ContestGateway } from './contest.gateway';
import { PrismaService } from '../../prisma.service';
import { isFeatureEnabled } from '../../common/config/feature-flags.config';

/**
 * Ranking item for real-time updates
 */
interface RankingItem {
  rank: number;
  userId: string;
  username: string;
  score: number;
  solvedCount: number;
  penalty: number;
}

/**
 * Real-time service for pushing contest updates via WebSocket
 *
 * Wraps ContestGateway to provide higher-level methods for:
 * - Pushing ranking updates
 * - First solve notifications
 * - Announcements
 * - Contest status changes
 * - Submission results
 */
@Injectable()
export class RealtimeService {
  private readonly logger = new Logger(RealtimeService.name);

  constructor(
    private readonly gateway: ContestGateway,
    private readonly prisma: PrismaService,
  ) {}

  /**
   * Push ranking update to all connected clients in a contest room
   */
  async pushRankingUpdate(contestId: string): Promise<void> {
    if (!isFeatureEnabled('ENABLE_REALTIME_RANKING')) {
      return;
    }

    try {
      const contestRankings = await this.prisma.contestRanking.findMany({
        where: { contest_id: contestId },
        orderBy: [{ rank: 'asc' }],
        take: 100,
        include: {
          user: { select: { id: true, username: true } },
        },
      });

      const rankings: RankingItem[] = contestRankings.map((r) => ({
        rank: r.rank,
        userId: r.user_id,
        username: r.user.username,
        score: r.total_score,
        solvedCount: r.solved_count,
        penalty: r.total_penalty,
      }));

      this.gateway.emitRankingUpdate(contestId, {
        contestId,
        rankings,
        updatedAt: new Date(),
      });
    } catch (error) {
      this.logger.error(
        `Failed to push ranking update for contest ${contestId}:`,
        error,
      );
    }
  }

  /**
   * Push first solve notification
   */
  async pushFirstSolve(
    contestId: string,
    problemId: string,
    problemTitle: string,
    userId: string,
  ): Promise<void> {
    if (!isFeatureEnabled('ENABLE_FIRST_SOLVE_NOTIFICATIONS')) {
      return;
    }

    try {
      const user = await this.prisma.user.findUnique({
        where: { id: userId },
        select: { username: true },
      });

      if (!user) {
        this.logger.warn(
          `User ${userId} not found for first solve notification`,
        );
        return;
      }

      this.gateway.emitFirstSolve(contestId, {
        contestId,
        problemId,
        problemTitle,
        userId,
        username: user.username,
        solvedAt: new Date(),
      });

      this.logger.log(
        `Pushed first solve notification: ${user.username} solved ${problemTitle}`,
      );
    } catch (error) {
      this.logger.error('Failed to push first solve notification:', error);
    }
  }

  /**
   * Push new announcement
   */
  async pushAnnouncement(
    contestId: string,
    announcementId: string,
  ): Promise<void> {
    try {
      const announcement = await this.prisma.contestAnnouncement.findUnique({
        where: { id: announcementId },
      });

      if (!announcement) {
        this.logger.warn(`Announcement ${announcementId} not found`);
        return;
      }

      this.gateway.emitAnnouncement(contestId, {
        id: announcement.id,
        contestId: announcement.contest_id,
        title: announcement.title,
        content: announcement.content,
        createdAt: announcement.created_at,
      });
    } catch (error) {
      this.logger.error('Failed to push announcement:', error);
    }
  }

  /**
   * Push contest status change
   */
  pushContestStatus(
    contestId: string,
    status: 'upcoming' | 'registration' | 'running' | 'ended',
    options?: { startedAt?: Date; endsAt?: Date; message?: string },
  ): void {
    this.gateway.emitContestStatus(contestId, {
      contestId,
      status,
      startedAt: options?.startedAt,
      endsAt: options?.endsAt,
      message: options?.message,
    });
  }

  /**
   * Push submission result to a specific user
   */
  pushSubmissionResult(
    userId: string,
    data: {
      submissionId: string;
      contestId: string;
      problemId: string;
      status: string;
      score: number;
      timeUsed?: number;
      memoryUsed?: number;
    },
  ): void {
    this.gateway.emitSubmissionResult(userId, {
      submissionId: data.submissionId,
      contestId: data.contestId,
      problemId: data.problemId,
      userId,
      status: data.status,
      score: data.score,
      timeUsed: data.timeUsed,
      memoryUsed: data.memoryUsed,
      judgedAt: new Date(),
    });
  }

  /**
   * Get connection statistics
   */
  getStats(): { totalConnections: number } {
    return {
      totalConnections: this.gateway.getConnectionCount(),
    };
  }
}
