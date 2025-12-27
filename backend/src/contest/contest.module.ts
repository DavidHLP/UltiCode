import { Module } from '@nestjs/common';
import { ContestService } from './contest.service';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';
import { ContestController, RankingController } from './contest.controller';
import { PrismaService } from '../prisma.service';

@Module({
  providers: [ContestService, RankingService, RatingService, PrismaService],
  controllers: [ContestController, RankingController],
  exports: [ContestService, RankingService, RatingService],
})
export class ContestModule {}
