import { Module } from '@nestjs/common';
import { ReportController } from './controllers/report.controller';
import { ModerationQueueController } from './controllers/queue.controller';
import { AppealController } from './controllers/appeal.controller';
import { ReportService } from './services/report.service';
import { ModerationQueueService } from './services/queue.service';
import { ModerationActionService } from './services/action.service';
import { AppealService } from './services/appeal.service';
import { PrismaService } from '../prisma.service';
import { AdminModule } from '../admin/admin.module';

@Module({
  imports: [AdminModule],
  controllers: [ReportController, ModerationQueueController, AppealController],
  providers: [
    ReportService,
    ModerationQueueService,
    ModerationActionService,
    AppealService,
    PrismaService,
  ],
  exports: [
    ReportService,
    ModerationQueueService,
    ModerationActionService,
    AppealService,
  ],
})
export class ModerationModule {}
