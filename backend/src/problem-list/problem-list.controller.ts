import {
  Controller,
  Get,
  Param,
  Query,
  Post,
  Delete,
  Patch,
  Body,
} from '@nestjs/common';
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

  @Get('user/:userId')
  getListsByUser(
    @Param('userId') userId: string,
  ): Promise<ProblemListSummary[]> {
    return this.problemListService.getListsByUserId(userId);
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

  @Post(':id/fork')
  async forkList(
    @Param('id') id: string,
    @Query('userId') userId: string, // In real app, get from AuthGuard
  ): Promise<{ id: string }> {
    const newListId = await this.problemListService.forkList(id, userId);
    return { id: newListId };
  }

  @Delete(':id')
  async deleteList(
    @Param('id') id: string,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.deleteList(id, userId);
  }

  @Patch(':id')
  async updateList(
    @Param('id') id: string,
    @Query('userId') userId: string,
    @Body() body: { name?: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    return this.problemListService.updateList(id, userId, body);
  }

  @Post(':id/problems')
  async addProblem(
    @Param('id') id: string,
    @Query('userId') userId: string,
    @Body() body: { problemId: number },
  ): Promise<void> {
    return this.problemListService.addProblem(id, userId, body.problemId);
  }

  @Delete(':id/problems/:problemId')
  async removeProblem(
    @Param('id') id: string,
    @Param('problemId') problemId: number,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.removeProblem(id, userId, problemId);
  }

  @Post()
  async createList(
    @Query('userId') userId: string,
    @Body() body: { name: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    return this.problemListService.createList(userId, body);
  }
}
