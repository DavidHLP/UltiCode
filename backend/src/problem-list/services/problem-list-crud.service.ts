import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { v4 as uuidv4 } from 'uuid';
import { PrismaService } from '../../prisma.service';
import { ProblemListStatsService } from './problem-list-stats.service';
import { ProblemListSummary, PrismaClient } from '../types';

@Injectable()
export class ProblemListCrudService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly statsService: ProblemListStatsService,
  ) {}

  /**
   * Create a new problem list
   */
  async createList(
    userId: string,
    data: { name: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    const newListId: string = uuidv4();
    const newList = await this.prisma.problemList.create({
      data: {
        id: newListId,
        name: data.name,
        description: data.description ?? '',
        author_id: userId,
        is_public: data.isPublic ?? false,
        is_featured: false,
        created_at: new Date(),
        updated_at: new Date(),
      },
    });

    return this.statsService.mapList(newList, 0, 0);
  }

  /**
   * Update a problem list
   */
  async updateList(
    listId: string,
    userId: string,
    data: { name?: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    const list = await this.prisma.problemList.findUnique({
      where: { id: listId },
    });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (list.author_id !== userId) {
      throw new ForbiddenException(
        'You do not have permission to edit this list',
      );
    }

    const updateData: Prisma.ProblemListUpdateInput = {};
    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined)
      updateData.description = data.description;
    if (data.isPublic !== undefined) updateData.is_public = data.isPublic;
    updateData.updated_at = new Date();

    const updated = await this.prisma.problemList.update({
      where: { id: listId },
      data: updateData,
    });

    return this.statsService.enrichListWithCounts(updated);
  }

  /**
   * Delete a problem list
   */
  async deleteList(listId: string, userId: string): Promise<void> {
    const list = await this.prisma.problemList.findUnique({
      where: { id: listId },
    });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (list.author_id !== userId) {
      throw new ForbiddenException(
        'You do not have permission to delete this list',
      );
    }

    await this.prisma.problemList.delete({
      where: { id: listId },
    });
  }

  /**
   * Fork (copy) a problem list
   */
  async forkList(listId: string, userId: string): Promise<string> {
    const originalList = await this.prisma.problemList.findUnique({
      where: { id: listId },
    });
    if (!originalList) {
      throw new NotFoundException('List not found');
    }

    const relations = await this.prisma.problemListProblemRelation.findMany({
      where: { list_id: listId },
    });

    const newListId: string = uuidv4();

    await this.prisma.$transaction(async (tx) => {
      await tx.problemList.create({
        data: {
          id: newListId,
          name: `${originalList.name} (Copy)`,
          description: originalList.description,
          author_id: userId,
          is_public: false,
          is_featured: false,
          created_at: new Date(),
          updated_at: new Date(),
        },
      });

      await tx.problemListProblemRelation.createMany({
        data: relations.map((r) => ({
          list_id: newListId,
          problem_id: r.problem_id,
          sort_order: r.sort_order,
        })),
      });
    });

    return newListId;
  }

  /**
   * Get a problem list by ID
   */
  async getListById(listId: string): Promise<ProblemListSummary | null> {
    const list = await this.prisma.problemList.findUnique({
      where: { id: listId },
    });
    if (!list) return null;

    return this.statsService.enrichListWithCounts(list);
  }

  /**
   * Get all problem lists by user ID
   */
  async getListsByUserId(userId: string): Promise<ProblemListSummary[]> {
    const lists = await this.prisma.problemList.findMany({
      where: { author_id: userId },
      orderBy: { updated_at: 'desc' },
    });

    const countMap = await this.statsService.buildProblemCountMap();
    const favoritesCountMap = await this.statsService.buildFavoritesCountMap(
      lists.map((list) => list.id),
    );

    return lists.map((list) =>
      this.statsService.mapList(
        list,
        countMap.get(list.id) ?? 0,
        favoritesCountMap.get(list.id) ?? 0,
      ),
    );
  }

  /**
   * Get all featured problem lists
   */
  async getFeaturedLists(): Promise<ProblemListSummary[]> {
    const countMap = await this.statsService.buildProblemCountMap();
    const lists = await this.prisma.problemList.findMany({
      where: { is_featured: true, is_public: true },
      orderBy: [{ banner_order: 'asc' }, { updated_at: 'desc' }],
    });
    const favoritesCountMap = await this.statsService.buildFavoritesCountMap(
      lists.map((list) => list.id),
    );
    return lists.map((list) =>
      this.statsService.mapList(
        list,
        countMap.get(list.id) ?? 0,
        favoritesCountMap.get(list.id) ?? 0,
      ),
    );
  }

  /**
   * Get the default public problem list
   */
  async getDefaultList(): Promise<ProblemListSummary | null> {
    const list = await this.prisma.problemList.findFirst({
      where: { is_public: true },
      orderBy: { created_at: 'asc' },
    });
    if (!list) return null;

    return this.statsService.enrichListWithCounts(list);
  }

  /**
   * Update list in transaction (for internal use)
   */
  async updateListInTransaction(
    listId: string,
    data: Prisma.ProblemListUpdateInput,
    tx: PrismaClient,
  ): Promise<void> {
    await tx.problemList.update({
      where: { id: listId },
      data,
    });
  }
}
