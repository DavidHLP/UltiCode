import { Module } from '@nestjs/common';
import { SolutionService } from './solution.service';
import { SolutionController } from './solution.controller';
import { SolutionTopicController } from './solution-topic.controller';
import { GlobalSolutionController } from './global-solution.controller';
import { PrismaService } from '../prisma.service';
import { VoteModule } from '../vote/vote.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [VoteModule, AuthModule],
  providers: [SolutionService, PrismaService],
  controllers: [
    SolutionController,
    SolutionTopicController,
    GlobalSolutionController,
  ],
  exports: [SolutionService],
})
export class SolutionModule {}
