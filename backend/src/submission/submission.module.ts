import { Module } from '@nestjs/common';
import { SubmissionService } from './submission.service';
import {
  SubmissionController,
  ProblemSubmissionController,
} from './submission.controller';
import { PrismaService } from '../prisma.service';

@Module({
  controllers: [SubmissionController, ProblemSubmissionController],
  providers: [SubmissionService, PrismaService],
  exports: [SubmissionService],
})
export class SubmissionModule {}
