import { Injectable, NotFoundException } from '@nestjs/common';
import { BookmarkType } from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import { BookmarkService } from '../../bookmark/bookmark.service';
import { CategorySummary } from '../types';
import { ProblemListStatsService } from './problem-list-stats.service';

const problemListTargetType = BookmarkType.PROBLEM_LIST;

@Injectable()
export class ProblemListCategoryService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly bookmarkService: BookmarkService,
    private readonly statsService: ProblemListStatsService,
  ) {}

  /**
   * Get all categories (folders) for a user
   */
  async getCategories(userId: string): Promise<CategorySummary[]> {
    const countMap = await this.statsService.buildProblemCountMap();
    const folders = await this.bookmarkService.getUserFolders(userId);

    const bookmarkItems = await this.prisma.bookmark.findMany({
      where: {
        target_type: problemListTargetType,
        folder: { user_id: userId },
      },
    });

    const folderItemsMap = new Map<string, string[]>();
    bookmarkItems.forEach((item) => {
      const list = folderItemsMap.get(item.folder_id) ?? [];
      list.push(item.target_id);
      folderItemsMap.set(item.folder_id, list);
    });

    const allListIds = bookmarkItems.map((item) => item.target_id);
    const favoritesCountMap =
      await this.statsService.buildFavoritesCountMap(allListIds);

    const result: CategorySummary[] = [];
    for (const folder of folders) {
      const listIds = folderItemsMap.get(folder.id) ?? [];
      const lists =
        listIds.length > 0
          ? await this.prisma.problemList.findMany({
              where: { id: { in: listIds } },
            })
          : [];

      result.push({
        id: folder.id,
        name: folder.name,
        sortOrder: folder.sortOrder,
        lists: lists.map((list) =>
          this.statsService.mapList(
            list,
            countMap.get(list.id) ?? 0,
            favoritesCountMap.get(list.id) ?? 0,
            { isSaved: true, categoryId: folder.id },
          ),
        ),
      });
    }

    return result;
  }

  /**
   * Create a new category (folder)
   */
  async createCategory(
    userId: string,
    data: { name: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    const folder = await this.bookmarkService.createFolder(userId, {
      name: data.name,
    });

    return {
      id: folder.id,
      name: folder.name,
      sortOrder: folder.sortOrder,
      lists: [],
    };
  }

  /**
   * Update a category (folder)
   */
  async updateCategory(
    categoryId: string,
    userId: string,
    data: { name?: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    const folder = await this.bookmarkService.updateFolder(userId, categoryId, {
      name: data.name,
      sortOrder: data.sortOrder,
    });

    const countMap = await this.statsService.buildProblemCountMap();
    const bookmarkItems = await this.prisma.bookmark.findMany({
      where: {
        folder_id: categoryId,
        target_type: problemListTargetType,
      },
    });

    const listIds = bookmarkItems.map((item) => item.target_id);
    const lists =
      listIds.length > 0
        ? await this.prisma.problemList.findMany({
            where: { id: { in: listIds } },
          })
        : [];
    const favoritesCountMap =
      await this.statsService.buildFavoritesCountMap(listIds);

    return {
      id: folder.id,
      name: folder.name,
      sortOrder: folder.sortOrder,
      lists: lists.map((list) =>
        this.statsService.mapList(
          list,
          countMap.get(list.id) ?? 0,
          favoritesCountMap.get(list.id) ?? 0,
          { isSaved: true, categoryId: folder.id },
        ),
      ),
    };
  }

  /**
   * Delete a category (folder)
   */
  async deleteCategory(categoryId: string, userId: string): Promise<void> {
    await this.bookmarkService.deleteFolder(userId, categoryId);
  }

  /**
   * Move a list to a category (folder)
   */
  async moveListToCategory(
    userId: string,
    listId: string,
    folderId: string | null,
  ): Promise<void> {
    const isSaved = await this.prisma.bookmark.count({
      where: {
        target_type: problemListTargetType,
        target_id: listId,
        folder: { user_id: userId },
      },
    });

    if (isSaved === 0) {
      throw new NotFoundException('List is not saved');
    }

    if (folderId) {
      await this.bookmarkService.addBookmark(userId, folderId, {
        targetId: listId,
        targetType: problemListTargetType,
      });
    } else {
      const folders = await this.bookmarkService.getUserFolders(userId);
      for (const folder of folders) {
        if (!folder.isDefault) {
          await this.bookmarkService.removeBookmarkByTarget(
            userId,
            folder.id,
            problemListTargetType,
            listId,
          );
        }
      }
    }
  }
}
