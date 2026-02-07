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
  ProblemListDetailResponse,
  CategorySummary,
} from './types';
import { Locale } from '../i18n/i18n.decorator';
import type { SupportedLocale } from '../i18n/i18n.constants';
import {
  AddProblemToListDto,
  BatchAddToListsDto,
  SaveListDto,
  MoveListToCategoryDto,
  CreateCategoryDto,
  UpdateCategoryDto,
} from './dto/problem-list-management.dto';

@Controller('problem-lists')
export class ProblemListController {
  constructor(private readonly problemListService: ProblemListService) {}

  // ============================================================================
  // Main API: Aggregated Overview
  // ============================================================================

  @Get('overview')
  async getOverview(
    @Query('userId') userId?: string,
    @Locale() locale?: string,
  ): Promise<UserProblemListsResponse> {
    if (userId) {
      return this.problemListService.getUserProblemLists(userId);
    }
    return this.problemListService.findAll(locale as SupportedLocale);
  }

  // ============================================================================
  // List Overview
  // ============================================================================

  @Get(':id/overview')
  async getListOverview(
    @Param('id') id: string,
    @Query('userId') userId?: string,
    @Locale() locale?: string,
  ): Promise<ProblemListDetailResponse> {
    return this.problemListService.getListOverview(
      id,
      userId,
      locale as SupportedLocale,
    );
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
    @Body() dto: AddProblemToListDto,
  ): Promise<void> {
    return this.problemListService.addProblem(id, userId, dto.problemId);
  }

  @Delete(':id/problems/:problemId')
  async removeProblem(
    @Param('id') id: string,
    @Param('problemId') problemId: number,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.removeProblem(id, userId, problemId);
  }

  @Post('problems/:problemId/batch-add')
  async batchAddProblemToLists(
    @Param('problemId') problemId: number,
    @Query('userId') userId: string,
    @Body() dto: BatchAddToListsDto,
  ): Promise<void> {
    return this.problemListService.batchAddProblemToLists(
      userId,
      problemId,
      dto.listIds,
    );
  }

  @Post('problems/:problemId/batch-remove')
  async batchRemoveProblemFromLists(
    @Param('problemId') problemId: number,
    @Query('userId') userId: string,
    @Body() dto: BatchAddToListsDto,
  ): Promise<void> {
    return this.problemListService.batchRemoveProblemFromLists(
      userId,
      problemId,
      dto.listIds,
    );
  }

  @Get('problems/:problemId/user-lists')
  async getUserListsForProblem(
    @Param('problemId') problemId: number,
    @Query('userId') userId: string,
  ) {
    return this.problemListService.getUserListsForProblem(userId, problemId);
  }

  // ============================================================================
  // Save/Unsave List
  // ============================================================================

  @Post(':id/save')
  async saveList(
    @Param('id') id: string,
    @Query('userId') userId: string,
    @Body() dto?: SaveListDto,
  ): Promise<void> {
    return this.problemListService.saveList(userId, id, dto?.categoryId);
  }

  @Delete(':id/save')
  async unsaveList(
    @Param('id') id: string,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.unsaveList(userId, id);
  }

  @Patch(':id/category')
  async moveListToCategory(
    @Param('id') id: string,
    @Query('userId') userId: string,
    @Body() dto: MoveListToCategoryDto,
  ): Promise<void> {
    return this.problemListService.moveListToCategory(
      userId,
      id,
      dto.categoryId,
    );
  }

  // ============================================================================
  // Category Management
  // ============================================================================

  @Post('categories')
  createCategory(
    @Query('userId') userId: string,
    @Body() dto: CreateCategoryDto,
  ): Promise<CategorySummary> {
    return this.problemListService.createCategory(userId, dto);
  }

  @Patch('categories/:categoryId')
  updateCategory(
    @Param('categoryId') categoryId: string,
    @Query('userId') userId: string,
    @Body() dto: UpdateCategoryDto,
  ): Promise<CategorySummary> {
    return this.problemListService.updateCategory(categoryId, userId, dto);
  }

  @Delete('categories/:categoryId')
  deleteCategory(
    @Param('categoryId') categoryId: string,
    @Query('userId') userId: string,
  ): Promise<void> {
    return this.problemListService.deleteCategory(categoryId, userId);
  }
}
