import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ScheduleModule } from '@nestjs/schedule';
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

@Module({
  imports: [
    ScheduleModule.forRoot(),
    TypeOrmModule.forRoot({
      type: 'mysql',
      host: 'localhost',
      port: 3306,
      username: 'root',
      password: '123456',
      database: 'ulticode',
      entities: [__dirname + '/**/*.entity{.ts,.js}'],
      synchronize: false,
    }),
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
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
