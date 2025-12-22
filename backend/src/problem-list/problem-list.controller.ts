import { Controller, Get, Param, Query } from '@nestjs/common';
import { ProblemListService } from './problem-list.service';
import type {
  ProblemListGroupSummary,
  ProblemListSummary,
  ProblemListStats,
  ProblemListProblem,
} from './problem-list.service';

@Controller('problem-lists')
export class ProblemListController {
  constructor(private readonly problemListService: ProblemListService) {}

  @Get()
  findAll(): Promise<ProblemListGroupSummary[]> {
    return this.problemListService.findAll();
  }

  @Get('stats')
  getStats(@Query('userId') userId?: string): Promise<ProblemListStats[]> {
    return this.problemListService.getStats(userId);
  }

  @Get(':id/problems')
  getProblems(
    @Param('id') id: string,
    @Query('userId') userId?: string,
  ): Promise<ProblemListProblem[]> {
    return this.problemListService.getProblemsByListId(id, userId);
  }

  @Get(':id')
  async getList(@Param('id') id: string): Promise<ProblemListSummary | null> {
    const list = await this.problemListService.getListById(id);
    if (list) {
      return list;
    }
    return this.problemListService.getDefaultList();
  }
}
