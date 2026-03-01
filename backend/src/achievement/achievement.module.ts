import { Module } from '@nestjs/common';
import {
  AdminAchievementController,
  AchievementController,
} from './achievement.controller';
import { AchievementService } from './achievement.service';
import { AchievementTriggerService } from './achievement-trigger.service';
import { PrismaService } from '../prisma.service';
import { NotificationModule } from '../notification/notification.module';

@Module({
  imports: [NotificationModule],
  controllers: [AdminAchievementController, AchievementController],
  providers: [AchievementService, AchievementTriggerService, PrismaService],
  exports: [AchievementService, AchievementTriggerService],
})
export class AchievementModule {}
