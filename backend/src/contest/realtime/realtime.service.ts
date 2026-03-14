import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
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

/** Throttle interval for ranking updates (1 second) */
const RANKING_THROTTLE_MS = 1000;

/**
 * Real-time service for pushing contest updates via WebSocket
 *
 * Wraps ContestGateway to provide higher-level methods for:
 * - Pushing ranking updates (throttled to max once per second)
 * - First solve notifications
 * - Announcements
 * - Contest status changes
 * - Submission results
 */
@Injectable()
export class RealtimeService implements OnModuleDestroy {
  private readonly logger = new Logger(RealtimeService.name);

  /** Track last push time per contest for throttling */
  private readonly lastRankingPushTime = new Map<string, number>();

  /** Pending ranking updates that need to be pushed */
  private readonly pendingRankingUpdates = new Map<string, boolean>();

  /** Throttle timers for deferred pushes */
  private readonly throttleTimers = new Map<string, NodeJS.Timeout>();

  constructor(
    private readonly gateway: ContestGateway,
    private readonly prisma: PrismaService,
  ) {}

  onModuleDestroy() {
    // Clear all timers on module destroy
    for (const timer of this.throttleTimers.values()) {
      clearTimeout(timer);
    }
    this.throttleTimers.clear();
    this.lastRankingPushTime.clear();
    this.pendingRankingUpdates.clear();
  }

  /**
   * Push ranking update to all connected clients in a contest room
   * Throttled to max once per second per contest to prevent overwhelming clients
   */
  async pushRankingUpdate(contestId: string): Promise<void> {
    if (!isFeatureEnabled('ENABLE_REALTIME_RANKING')) {
      return;
    }

    const now = Date.now();
    const lastPush = this.lastRankingPushTime.get(contestId) ?? 0;
    const timeSinceLastPush = now - lastPush;

    // If we pushed recently, defer this update
    if (timeSinceLastPush < RANKING_THROTTLE_MS) {
      // Mark that there's a pending update
      this.pendingRankingUpdates.set(contestId, true);

      // Set up a timer to push the deferred update if not already set
      if (!this.throttleTimers.has(contestId)) {
        const delay = RANKING_THROTTLE_MS - timeSinceLastPush;
        const timer = setTimeout(() => {
          this.throttleTimers.delete(contestId);
          if (this.pendingRankingUpdates.get(contestId)) {
            this.pendingRankingUpdates.delete(contestId);
            // Push the deferred update (no await - fire and forget)
            this.doPushRankingUpdate(contestId).catch((err) => {
              this.logger.error(
                `Failed to push deferred ranking update for contest ${contestId}:`,
                err,
              );
            });
          }
        }, delay);
        this.throttleTimers.set(contestId, timer);
      }
      return;
    }

    // Enough time has passed, push immediately
    await this.doPushRankingUpdate(contestId);
  }

  /**
   * Internal method to actually perform the ranking push
   */
  private async doPushRankingUpdate(contestId: string): Promise<void> {
    this.lastRankingPushTime.set(contestId, Date.now());

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
    problemIndex: string,
    userId: string,
    timeSpent: number,
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

      // Get problem info from contest problem mapping
      const contestProblem = await this.prisma.contestProblem.findFirst({
        where: {
          contest_id: contestId,
          problem_index: problemIndex,
        },
        include: {
          problem: { select: { id: true, title: true } },
        },
      });

      if (!contestProblem) {
        this.logger.warn(
          `Contest problem not found for contest ${contestId}, index ${problemIndex}`,
        );
        return;
      }

      this.gateway.emitFirstSolve(contestId, {
        contestId,
        problemId: String(contestProblem.problem.id),
        problemTitle: contestProblem.problem.title,
        userId,
        username: user.username,
        solvedAt: new Date(),
      });

      this.logger.log(
        `Pushed first solve notification: ${user.username} solved ${contestProblem.problem.title} in ${timeSpent}s`,
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
  pushSubmissionResult(data: {
    contestId: string;
    userId: string;
    problemIndex: string;
    submissionId: string;
    status: string;
    score: number;
    timeSpent: number;
    isFirstSolve: boolean;
  }): void {
    this.gateway.emitSubmissionResult(data.userId, {
      submissionId: data.submissionId,
      contestId: data.contestId,
      problemId: data.problemIndex,
      userId: data.userId,
      status: data.status,
      score: data.score,
      timeUsed: data.timeSpent,
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
