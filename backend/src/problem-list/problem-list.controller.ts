import { Controller, Get } from '@nestjs/common';
import { ProblemListService } from './problem-list.service';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';
import { ProblemListProblem, ProblemListStats } from './problem-list.service';

@Controller('problem-lists')
export class ProblemListController {
  constructor(private readonly problemListService: ProblemListService) {}

  @Get()
  findAll(): Promise<ProblemListGroup[]> {
    return this.problemListService.findAll();
  }

  @Get('stats')
  getStats(): ProblemListStats[] {
    return this.problemListService.getStats();
  }

  @Get(':id')
  getList(): Promise<ProblemList> {
    return this.problemListService.getDefaultList();
  }

  @Get(':id/problems')
  getProblems(): ProblemListProblem[] {
    return this.problemListService.getDefaultProblems();
  }
}
