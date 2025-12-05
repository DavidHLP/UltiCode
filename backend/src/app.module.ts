import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { UserModule } from './user/user.module';
import { ProblemModule } from './problem/problem.module';
import { SolutionModule } from './solution/solution.module';
import { ContestModule } from './contest/contest.module';
import { ForumModule } from './forum/forum.module';
import { ProblemListModule } from './problem-list/problem-list.module';

@Module({
  imports: [
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
    ContestModule,
    ForumModule,
    ProblemListModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
