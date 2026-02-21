import { Module } from '@nestjs/common';
import {
  AdminAchievementController,
  AchievementController,
} from './achievement.controller';
import { AchievementService } from './achievement.service';
import { PrismaService } from '../prisma.service';
import { NotificationModule } from '../notification/notification.module';

@Module({
  imports: [NotificationModule],
  controllers: [AdminAchievementController, AchievementController],
  providers: [AchievementService, PrismaService],
  exports: [AchievementService],
})
export class AchievementModule {}
