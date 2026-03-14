import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';
import { validateConfig } from './config/config.schema';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { UserModule } from './user/user.module';
import { ProblemModule } from './problem/problem.module';
import { SolutionModule } from './solution/solution.module';
import { ContestModule } from './contest/contest.module';
import { ForumModule } from './forum/forum.module';
import { ProblemListModule } from './problem-list/problem-list.module';
import { SubmissionModule } from './submission/submission.module';
import { AuthModule } from './auth/auth.module';
import { VoteModule } from './vote/vote.module';
import { EdgeOperationsModule } from './edge-operations/edge-operations.module';
import { ProblemNoteModule } from './problem/note/note.module';
import { BookmarkModule } from './bookmark/bookmark.module';
import { ViewModule } from './view/view.module';
import { I18nModule } from './i18n/i18n.module';
import { NotificationModule } from './notification/notification.module';
import { AdminModule } from './admin/admin.module';
import { SubscriptionModule } from './subscription/subscription.module';
import { CustomCacheModule } from './cache/cache.module';
import { TestCaseModule } from './test-case/test-case.module';
import { SearchModule } from './search/search.module';
import { AchievementModule } from './achievement/achievement.module';
import { MonitoringModule } from './monitoring/monitoring.module';
import { BackupModule } from './backup/backup.module';
import { EmailModule } from './email/email.module';
import { RecommendationModule } from './recommendation/recommendation.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
      validate: (config: Record<string, unknown>) => {
        validateConfig(config);
        return config;
      },
    }),
    ScheduleModule.forRoot(),
    BullModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        connection: {
          host: configService.get<string>('REDIS_HOST', 'localhost'),
          port: configService.get<number>('REDIS_PORT', 6379),
          password: configService.get<string>('REDIS_PASSWORD', ''),
        },
      }),
    }),
    AdminModule,
    UserModule,
    ProblemModule,
    SolutionModule,
    VoteModule,
    EdgeOperationsModule,
    ProblemNoteModule,
    ViewModule,
    ContestModule,
    ForumModule,
    ProblemListModule,
    SubmissionModule,
    AuthModule,
    BookmarkModule,
    I18nModule,
    NotificationModule,
    SubscriptionModule,
    CustomCacheModule,
    TestCaseModule,
    SearchModule,
    AchievementModule,
    MonitoringModule,
    BackupModule,
    EmailModule,
    RecommendationModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
