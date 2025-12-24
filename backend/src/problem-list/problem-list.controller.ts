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
  UserProblemListsResponse,
  ProblemListSummary,
  ProblemListStats,
  ProblemListProblem,
  CategorySummary,
} from './problem-list.service';

@Controller('problem-lists')
export class ProblemListController {
  constructor(private readonly problemListService: ProblemListService) {}

  // ============================================================================
  // Main API: Get User's Problem Lists (with all sections)
  // ============================================================================

  @Get()
  async findAll(
    @Query('userId') userId?: string,
  ): Promise<UserProblemListsResponse> {
    if (userId) {
      return this.problemListService.getUserProblemLists(userId);
    }
    return this.problemListService.findAll();
  }

  @Get('featured')
  getFeaturedLists(): Promise<ProblemListSummary[]> {
    return this.problemListService.getFeaturedLists();
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

  // ============================================================================
  // Single List Operations
  // ============================================================================

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

  @Post()
  async createList(
    @Query('userId') userId: string,
    @Body() body: { name: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    return this.problemListService.createList(userId, body);
  }

  @Patch(':id')
  async updateList(
    @Param('id') id: string,
    @Query('userId') userId: string,
    @Body() body: { name?: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    return this.problemListService.updateList(id, userId, body);
  }

  @Delete(':id')
  async deleteList(
    @Param('id') id: string,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.deleteList(id, userId);
  }

  @Post(':id/fork')
  async forkList(
    @Param('id') id: string,
    @Query('userId') userId: string,
  ): Promise<{ id: string }> {
    const newListId = await this.problemListService.forkList(id, userId);
    return { id: newListId };
  }

  // ============================================================================
  // Problem Management in List
  // ============================================================================

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

  // ============================================================================
  // Save/Unsave List
  // ============================================================================

  @Post(':id/save')
  async saveList(
    @Param('id') id: string,
    @Query('userId') userId: string,
    @Body() body?: { categoryId?: string },
  ): Promise<void> {
    return this.problemListService.saveList(userId, id, body?.categoryId);
  }

  @Delete(':id/save')
  async unsaveList(
    @Param('id') id: string,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.unsaveList(userId, id);
  }

  @Get(':id/saved')
  async isListSaved(
    @Param('id') id: string,
    @Query('userId') userId: string,
  ): Promise<{ saved: boolean }> {
    const saved = await this.problemListService.isListSaved(userId, id);
    return { saved };
  }

  @Patch(':id/category')
  async moveListToCategory(
    @Param('id') id: string,
    @Query('userId') userId: string,
    @Body() body: { categoryId: string | null },
  ): Promise<void> {
    return this.problemListService.moveListToCategory(
      userId,
      id,
      body.categoryId,
    );
  }

  // ============================================================================
  // Category Management
  // ============================================================================

  @Get('categories/user/:userId')
  getCategories(@Param('userId') userId: string): Promise<CategorySummary[]> {
    return this.problemListService.getCategories(userId);
  }

  @Post('categories')
  createCategory(
    @Query('userId') userId: string,
    @Body() body: { name: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    return this.problemListService.createCategory(userId, body);
  }

  @Patch('categories/:categoryId')
  updateCategory(
    @Param('categoryId') categoryId: string,
    @Query('userId') userId: string,
    @Body() body: { name?: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    return this.problemListService.updateCategory(categoryId, userId, body);
  }

  @Delete('categories/:categoryId')
  deleteCategory(
    @Param('categoryId') categoryId: string,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.deleteCategory(categoryId, userId);
  }
}
