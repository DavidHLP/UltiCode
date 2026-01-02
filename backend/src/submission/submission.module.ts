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

@Module({
  imports: [
    BullModule.registerQueue({
      name: 'judge_queue',
    }),
    ContestModule,
    NotificationModule,
  ],
  controllers: [
    SubmissionController,
    ProblemSubmissionController,
    ContestSubmissionController,
  ],
  providers: [
    SubmissionService,
    ContestSubmissionService,
    PrismaService,
    JudgeService,
    JudgeProcessor,
  ],
  exports: [SubmissionService, ContestSubmissionService],
})
export class SubmissionModule {}
