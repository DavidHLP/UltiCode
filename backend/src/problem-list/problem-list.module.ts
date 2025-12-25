import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ProblemListService } from './problem-list.service';
import { ProblemListController } from './problem-list.controller';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';
import { SubmissionModule } from '../submission/submission.module';
import { ProblemListProblemRelation } from './problem-list-problem-relation.entity';
import { UserProblemListCategoryItem } from './user-problem-list-category-item.entity';
import { UserProblemListCategory } from './user-problem-list-category.entity';
import { PrismaService } from '../prisma.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ProblemList,
      ProblemListProblemRelation,
      UserProblemListCategoryItem,
      UserProblemListCategory,
      Problem,
    ]),
    SubmissionModule,
  ],
  providers: [ProblemListService, PrismaService],
  controllers: [ProblemListController],
  exports: [ProblemListService],
})
export class ProblemListModule {}
