import { Module } from '@nestjs/common';
import { ProblemService } from './problem.service';
import { ProblemController } from './problem.controller';
import { SubmissionModule } from '../submission/submission.module';
import { SubscriptionModule } from '../subscription/subscription.module';
import { PrismaService } from '../prisma.service';

@Module({
  imports: [SubmissionModule, SubscriptionModule],
  providers: [ProblemService, PrismaService],
  controllers: [ProblemController],
  exports: [ProblemService],
})
export class ProblemModule {}
