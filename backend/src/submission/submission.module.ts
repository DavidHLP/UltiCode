import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { SubmissionService } from './submission.service';
import { ContestSubmissionService } from './contest-submission.service';
import {
  SubmissionController,
  ProblemSubmissionController,
  ContestSubmissionController,
} from './submission.controller';
import { PrismaService } from '../prisma.service';
import { JudgeService } from './judge.service';
import { JudgeProcessor } from './judge.processor';
import { ContestModule } from '../contest/contest.module';
import { NotificationModule } from '../notification/notification.module';
import { SubmissionCrudService } from './services/submission-crud.service';
import { SubmissionQueryService } from './services/submission-query.service';
import { SubmissionExecutionService } from './services/submission-execution.service';
import { SandboxModule } from './sandbox/sandbox.module';
import { TestCaseModule } from '../test-case/test-case.module';
import { AchievementModule } from '../achievement/achievement.module';

@Module({
  imports: [
    BullModule.registerQueue({
      name: 'judge_queue',
    }),
    ContestModule,
    NotificationModule,
    SandboxModule,
    TestCaseModule,
    AchievementModule,
  ],
  controllers: [
    SubmissionController,
    ProblemSubmissionController,
    ContestSubmissionController,
  ],
  providers: [
    PrismaService,
    JudgeService,
    JudgeProcessor,
    SubmissionCrudService,
    SubmissionQueryService,
    SubmissionExecutionService,
    SubmissionService,
    ContestSubmissionService,
  ],
  exports: [SubmissionService, ContestSubmissionService],
})
export class SubmissionModule {}
