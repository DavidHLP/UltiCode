import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { InjectQueue } from '@nestjs/bullmq';
import { Queue } from 'bullmq';
import { PrismaService } from '../prisma.service';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';

@Injectable()
export class ContestSchedulerService {
  private readonly logger = new Logger(ContestSchedulerService.name);

  constructor(
    private prisma: PrismaService,
    private rankingService: RankingService,
    private ratingService: RatingService,
    @InjectQueue('contest') private contestQueue: Queue,
  ) {}

  /**
   * Check and update contest statuses every minute
   * This handles upcoming -> running and running -> finished transitions
   */
  @Cron(CronExpression.EVERY_MINUTE)
  async updateContestStatuses() {
    this.logger.debug('Checking for contest status updates...');

    const now = new Date();

    try {
      // Update upcoming contests to running if start time has passed
      const upcomingContests = await this.prisma.contest.findMany({
        where: {
          status: 'upcoming',
          start_time: {
            lte: now,
          },
        },
      });

      for (const contest of upcomingContests) {
        this.logger.log(`Starting contest: ${contest.title} (${contest.id})`);

        await this.prisma.contest.update({
          where: { id: contest.id },
          data: { status: 'running' },
        });

        // Update participant count
        const participantCount = await this.prisma.contestParticipant.count({
          where: {
            contest_id: contest.id,
            is_virtual: false,
          },
        });

        await this.prisma.contest.update({
          where: { id: contest.id },
          data: { participant_count: participantCount },
        });
      }

      // Update running contests to finished if end time has passed
      const runningContests = await this.prisma.contest.findMany({
        where: {
          status: 'running',
        },
      });

      for (const contest of runningContests) {
        const endTime = new Date(
          contest.start_time.getTime() + contest.duration_minutes * 60 * 1000,
        );

        if (now >= endTime) {
          this.logger.log(
            `Finishing contest: ${contest.title} (${contest.id})`,
          );

          await this.prisma.contest.update({
            where: { id: contest.id },
            data: { status: 'finished' },
          });

          // Queue finalization task
          await this.contestQueue.add('finalize-contest', {
            contestId: contest.id,
          });
        }
      }

      // Check for virtual contest sessions that have ended
      await this.finalizeExpiredVirtualSessions();
    } catch (error) {
      this.logger.error('Error updating contest statuses:', error);
    }
  }

  /**
   * Check for virtual contest sessions that have expired and finalize them
   */
  private async finalizeExpiredVirtualSessions(): Promise<void> {
    const now = new Date();

    const expiredSessions = await this.prisma.virtualContestSession.findMany({
      where: {
        status: 'IN_PROGRESS',
        ends_at: {
          lte: now,
        },
      },
      include: {
        participants: true,
      },
    });

    for (const session of expiredSessions) {
      this.logger.log(`Finalizing expired virtual session ${session.id}...`);

      try {
        // Update session status
        await this.prisma.virtualContestSession.update({
          where: { id: session.id },
          data: {
            status: 'COMPLETED',
            finished_at: now,
          },
        });

        // Update participant status
        for (const participant of session.participants) {
          if (participant.status !== 'FINISHED') {
            await this.prisma.contestParticipant.update({
              where: { id: participant.id },
              data: {
                status: 'FINISHED',
                finished_at: now,
              },
            });
          }
          // Finalize ranking for this participant
          await this.rankingService.finalizeVirtualRanking(participant.id);
        }

        this.logger.log(`Virtual session ${session.id} finalized`);
      } catch (error) {
        this.logger.error(
          `Error finalizing virtual session ${session.id}:`,
          error,
        );
      }
    }
  }

  /**
   * Manual trigger for contest finalization (useful for testing or admin actions)
   */
  async manuallyFinalizeContest(contestId: string): Promise<void> {
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
    });

    if (!contest) {
      throw new Error('Contest not found');
    }

    if (contest.status !== 'running' && contest.status !== 'finished') {
      throw new Error('Can only finalize running or finished contests');
    }

    // Force status to finished
    await this.prisma.contest.update({
      where: { id: contestId },
      data: { status: 'finished' },
    });

    // Queue finalization task
    await this.contestQueue.add('finalize-contest', {
      contestId: contest.id,
    });
  }
}
