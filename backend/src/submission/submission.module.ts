import { Module } from '@nestjs/common';
import { SubmissionService } from './submission.service';
import { ContestSubmissionService } from './contest-submission.service';
import {
  SubmissionController,
  ProblemSubmissionController,
  ContestSubmissionController,
} from './submission.controller';
import { PrismaService } from '../prisma.service';
import { JudgeService } from './judge.service';

@Module({
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
  ],
  exports: [SubmissionService, ContestSubmissionService],
})
export class SubmissionModule {}
