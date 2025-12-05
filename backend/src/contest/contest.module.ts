import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ContestService } from './contest.service';
import { ContestController } from './contest.controller';
import { Contest } from './contest.entity';
import { ContestProblem } from './contest-problem.entity';
import { ContestParticipant } from './contest-participant.entity';
import { ContestRanking } from './contest-ranking.entity';
import { GlobalRanking } from './global-ranking.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Contest,
      ContestProblem,
      ContestParticipant,
      ContestRanking,
      GlobalRanking,
    ]),
  ],
  providers: [ContestService],
  controllers: [ContestController],
  exports: [ContestService],
})
export class ContestModule {}
