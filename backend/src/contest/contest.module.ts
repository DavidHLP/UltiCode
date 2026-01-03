import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { ContestService } from './contest.service';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';
import { ContestSchedulerService } from './contest-scheduler.service';
import { ContestProcessor } from './contest.processor';
import { ContestController, RankingController } from './contest.controller';
import { PrismaService } from '../prisma.service';

@Module({
  imports: [
    BullModule.registerQueue({
      name: 'contest',
    }),
  ],
  providers: [
    ContestService,
    RankingService,
    RatingService,
    ContestSchedulerService,
    ContestProcessor,
    PrismaService,
  ],
  controllers: [ContestController, RankingController],
  exports: [ContestService, RankingService, RatingService],
})
export class ContestModule {}
