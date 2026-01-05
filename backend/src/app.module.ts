import { BullModule } from '@nestjs/bullmq';
import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ScheduleModule } from '@nestjs/schedule';
import { ThrottlerModule } from '@nestjs/throttler';
// import { APP_GUARD } from '@nestjs/core';
// import { ThrottlerGuard } from '@nestjs/throttler';
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

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
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
    BullModule.forRoot({
      connection: {
        host: process.env.REDIS_HOST || 'localhost',
        port: parseInt(process.env.REDIS_PORT || '6379'),
        password: process.env.REDIS_PASSWORD || '123456',
      },
    }),
    TypeOrmModule.forRoot({
      type: 'mysql',
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '3306'),
      username: process.env.DB_USERNAME || 'root',
      password: process.env.DB_PASSWORD || '',
      database: process.env.DB_NAME || 'ulticode',
      entities: [__dirname + '/**/*.entity{.ts,.js}'],
      synchronize: false,
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
  ],
  controllers: [AppController],
  providers: [
    AppService,
    // Global rate limiting disabled - only auth endpoints have rate limits via @Throttle decorators
    // {
    //   provide: APP_GUARD,
    //   useClass: ThrottlerGuard,
    // },
  ],
})
export class AppModule {}
