import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Job } from 'bullmq';
import { Logger } from '@nestjs/common';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';
import { PrismaService } from '../prisma.service';

interface FinalizeContestJobData {
  contestId: string;
}

@Processor('contest')
export class ContestProcessor extends WorkerHost {
  private readonly logger = new Logger(ContestProcessor.name);

  constructor(
    private prisma: PrismaService,
    private rankingService: RankingService,
    private ratingService: RatingService,
  ) {
    super();
  }

  async process(job: Job<FinalizeContestJobData, any, string>): Promise<any> {
    switch (job.name) {
      case 'finalize-contest':
        return await this.handleFinalizeContest(job.data.contestId);
      default:
        this.logger.warn(`Unknown job name: ${job.name}`);
    }
  }

  private async handleFinalizeContest(contestId: string): Promise<void> {
    try {
      this.logger.log(`Finalizing contest ${contestId} (background job)...`);

      // Update all participants who haven't finished yet
      const participants = await this.prisma.contestParticipant.findMany({
        where: {
          contest_id: contestId,
          status: 'STARTED',
          is_virtual: false,
        },
      });

      for (const participant of participants) {
        await this.prisma.contestParticipant.update({
          where: { id: participant.id },
          data: {
            status: 'FINISHED',
            finished_at: new Date(),
          },
        });
      }

      // Finalize rankings
      await this.rankingService.finalizeContestRanking(contestId);

      // Calculate and apply ratings
      const contest = await this.prisma.contest.findUnique({
        where: { id: contestId },
      });

      if (contest?.is_rated) {
        this.logger.log(`Calculating ratings for contest ${contestId}...`);
        const ratingChanges =
          await this.ratingService.calculateContestRatings(contestId);
        await this.ratingService.applyContestRatings(contestId, ratingChanges);
        await this.ratingService.recalculateGlobalRankings();
        this.logger.log(`Ratings calculated for ${ratingChanges.length} users`);
      }

      this.logger.log(`Contest ${contestId} finalized successfully`);
    } catch (error) {
      this.logger.error(`Error finalizing contest ${contestId}:`, error);
      throw error;
    }
  }
}
