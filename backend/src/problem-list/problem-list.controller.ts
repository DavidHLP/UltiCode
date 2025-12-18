import { Controller, Get, Param } from '@nestjs/common';
import { ProblemListService } from './problem-list.service';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';
import type {
  ProblemListStats,
  ProblemListProblem,
} from './problem-list.service';

@Controller('problem-lists')
export class ProblemListController {
  constructor(private readonly problemListService: ProblemListService) {}

  @Get()
  findAll(): Promise<ProblemListGroup[]> {
    return this.problemListService.findAll();
  }

  @Get('stats')
  getStats(): Promise<ProblemListStats[]> {
    return this.problemListService.getStats();
  }

  @Get(':id/problems')
  getProblems(@Param('id') id: string): Promise<ProblemListProblem[]> {
    return this.problemListService.getProblemsByListId(id);
  }

  @Get(':id')
  getList(): Promise<ProblemList> {
    return this.problemListService.getDefaultList();
  }
}
