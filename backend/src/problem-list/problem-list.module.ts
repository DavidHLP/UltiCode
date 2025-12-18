import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ProblemListService } from './problem-list.service';
import { ProblemListController } from './problem-list.controller';
import { ProblemList } from './problem-list.entity';
import { ProblemListGroup } from './problem-list-group.entity';
import { Problem } from '../problem/problem.entity';

@Module({
  imports: [TypeOrmModule.forFeature([ProblemList, ProblemListGroup, Problem])],
  providers: [ProblemListService],
  controllers: [ProblemListController],
  exports: [ProblemListService],
})
export class ProblemListModule {}
