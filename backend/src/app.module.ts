import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';
import { ThrottlerModule } from '@nestjs/throttler';
import { validateConfig } from './config/config.schema';
import { APP_GUARD } from '@nestjs/core';
import { CustomThrottlerGuard } from './common/guards/throttle.guard';
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
    ThrottlerModule.forRoot([
      {
        name: 'global',
        ttl: 60000, // 60 seconds
        limit: 1000, // 1000 requests per minute (~16 req/sec) - suitable for SPA with parallel requests
      },
      {
        name: 'strict',
        ttl: 60000, // 60 seconds
        limit: 10, // 10 requests per minute for sensitive endpoints
      },
      {
        name: 'very-strict',
        ttl: 300000, // 5 minutes
        limit: 5, // 5 requests per 5 minutes for auth endpoints
      },
    ]),
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
  ],
  controllers: [AppController],
  providers: [
    AppService,
    // Global rate limiting with custom throttler guard
    {
      provide: APP_GUARD,
      useClass: CustomThrottlerGuard,
    },
  ],
})
export class AppModule {}
