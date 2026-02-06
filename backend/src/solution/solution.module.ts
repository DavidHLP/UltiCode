import { Module } from '@nestjs/common';
import { SolutionService } from './solution.service';
import { SolutionController } from './solution.controller';
import { SolutionTopicController } from './solution-topic.controller';
import { GlobalSolutionController } from './global-solution.controller';
import { PrismaService } from '../prisma.service';
import { VoteModule } from '../vote/vote.module';
import { AuthModule } from '../auth/auth.module';
import { SolutionCrudService } from './services/solution-crud.service';
import { SolutionQueryService } from './services/solution-query.service';
import { SolutionCommentService } from './services/solution-comment.service';

@Module({
  imports: [VoteModule, AuthModule],
  providers: [
    PrismaService,
    SolutionCrudService,
    SolutionQueryService,
    SolutionCommentService,
    SolutionService,
  ],
  controllers: [
    SolutionController,
    SolutionTopicController,
    GlobalSolutionController,
  ],
  exports: [SolutionService],
})
export class SolutionModule {}
