import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ProblemService } from './problem.service';
import { ProblemController } from './problem.controller';
import { SolutionModule } from '../solution/solution.module';
import { Problem } from './problem.entity';
import { ProblemDetail } from './problem-detail.entity';
import { ProblemTag } from './problem-tag.entity';
import { ProblemTagRelation } from './problem-tag-relation.entity';
import { ProblemLanguage } from './problem-language.entity';
import { ProblemExample } from './problem-example.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Problem,
      ProblemDetail,
      ProblemTag,
      ProblemTagRelation,
      ProblemLanguage,
      ProblemExample,
    ]),
    SolutionModule,
  ],
  providers: [ProblemService],
  controllers: [ProblemController],
  exports: [ProblemService],
})
export class ProblemModule {}
