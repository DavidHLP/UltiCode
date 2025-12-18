import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ContestService } from './contest.service';
import { ContestController } from './contest.controller';
import { ContestRestController } from './contest-rest.controller';
import { Contest } from './contest.entity';
import { ContestProblem } from './contest-problem.entity';
import { ContestParticipant } from './contest-participant.entity';
import { ContestRanking } from './contest-ranking.entity';
import { GlobalRanking } from './global-ranking.entity';
import { User } from '../user/user.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Contest,
      ContestProblem,
      ContestParticipant,
      ContestRanking,
      GlobalRanking,
      User,
    ]),
  ],
  providers: [ContestService],
  controllers: [ContestController, ContestRestController],
  exports: [ContestService],
})
export class ContestModule {}
