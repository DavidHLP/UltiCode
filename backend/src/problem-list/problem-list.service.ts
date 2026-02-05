import { Injectable } from '@nestjs/common';
import { BookmarkType } from '@prisma/client';
import { PrismaService } from '../prisma.service';
import { ProblemListCrudService } from './services/problem-list-crud.service';
import { ProblemListRelationService } from './services/problem-list-relation.service';
import { ProblemListBookmarkService } from './services/problem-list-bookmark.service';
import { ProblemListCategoryService } from './services/problem-list-category.service';
import { ProblemListStatsService } from './services/problem-list-stats.service';
import {
  ProblemListSummary,
  CategorySummary,
  UserProblemListsResponse,
  ProblemListDetailResponse,
  ProblemListProblem,
} from './types';
import { SupportedLocale, DEFAULT_LOCALE } from '../i18n/i18n.constants';

const problemListTargetType = BookmarkType.PROBLEM_LIST;

@Injectable()
export class ProblemListService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly crudService: ProblemListCrudService,
    private readonly relationService: ProblemListRelationService,
    private readonly bookmarkService: ProblemListBookmarkService,
    private readonly categoryService: ProblemListCategoryService,
    private readonly statsService: ProblemListStatsService,
  ) {}

  // ============================================================================
  // Main API: Aggregated Queries
  // ============================================================================

  /**
   * Get complete problem lists data for a user
   * Aggregates my lists, saved lists, featured lists, and categories
   */
  async getUserProblemLists(userId: string): Promise<UserProblemListsResponse> {
    const countMap = await this.statsService.buildProblemCountMap();

    const bookmarkItems = await this.prisma.bookmark.findMany({
      where: {
        target_type: problemListTargetType,
        folder: { user_id: userId },
      },
      include: { folder: true },
    });

    const savedListIds = new Set(bookmarkItems.map((item) => item.target_id));
    const savedListIdArray = Array.from(savedListIds);

    const folderMap = new Map<string, string>();
    bookmarkItems.forEach((item) => {
      if (!folderMap.has(item.target_id) || !item.folder.is_default) {
        folderMap.set(item.target_id, item.folder_id);
      }
    });

    const myLists = await this.prisma.problemList.findMany({
      where: { author_id: userId },
      orderBy: { updated_at: 'desc' },
    });

    const savedLists =
      savedListIdArray.length > 0
        ? await this.prisma.problemList.findMany({
            where: { id: { in: savedListIdArray } },
            orderBy: { updated_at: 'desc' },
          })
        : [];

    const savedFromOthers = savedLists.filter(
      (list) => list.author_id !== userId,
    );

    const featuredLists = await this.prisma.problemList.findMany({
      where: { is_featured: true, is_public: true },
      orderBy: [{ banner_order: 'asc' }, { updated_at: 'desc' }],
    });

    const favoriteCountIds = Array.from(
      new Set([
        ...myLists.map((list) => list.id),
        ...savedLists.map((list) => list.id),
        ...featuredLists.map((list) => list.id),
      ]),
    );
    const favoritesCountMap =
      await this.statsService.buildFavoritesCountMap(favoriteCountIds);

    const categoryResponses: CategorySummary[] =
      await this.categoryService.getCategories(userId);

    return {
      myLists: myLists.map((list) =>
        this.statsService.mapList(
          list,
          countMap.get(list.id) ?? 0,
          favoritesCountMap.get(list.id) ?? 0,
          { isSaved: savedListIds.has(list.id) },
        ),
      ),
      savedLists: savedFromOthers.map((list) =>
        this.statsService.mapList(
          list,
          countMap.get(list.id) ?? 0,
          favoritesCountMap.get(list.id) ?? 0,
          {
            isSaved: true,
            categoryId: folderMap.get(list.id) ?? undefined,
          },
        ),
      ),
      featured: featuredLists.map((list) =>
        this.statsService.mapList(
          list,
          countMap.get(list.id) ?? 0,
          favoritesCountMap.get(list.id) ?? 0,
          {
            isSaved: savedListIds.has(list.id),
            categoryId: folderMap.get(list.id) ?? undefined,
          },
        ),
      ),
      categories: categoryResponses,
    };
  }

  /**
   * Get problem lists overview for anonymous users
   */
  async findAll(
    _locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<UserProblemListsResponse> {
    return {
      myLists: [],
      savedLists: [],
      featured: await this.crudService.getFeaturedLists(),
      categories: [],
    };
  }

  /**
   * Get detailed overview of a specific problem list
   */
  async getListOverview(
    listId: string,
    userId?: string,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ProblemListDetailResponse> {
    let listSummary = await this.crudService.getListById(listId);
    if (!listSummary) {
      listSummary = await this.crudService.getDefaultList();
    }

    if (!listSummary) {
      return { list: null, problems: [], stats: null };
    }

    const problems = await this.relationService.getProblemsByListId(
      listSummary.id,
      userId ?? undefined,
      locale,
    );
    const stats = this.statsService.buildStatsFromProblems(
      listSummary.id,
      problems,
    );

    let viewer: ProblemListDetailResponse['viewer'] | undefined;
    let categories: ProblemListDetailResponse['categories'] | undefined;

    if (userId) {
      const folderIds = await this.prisma.bookmark
        .findMany({
          where: {
            target_type: problemListTargetType,
            target_id: listSummary.id,
            folder: { user_id: userId },
          },
          select: { folder_id: true },
        })
        .then((items) => items.map((item) => item.folder_id));

      const isSaved = folderIds.length > 0;
      const folders = await this.prisma.bookmarkFolder.findMany({
        where: { user_id: userId },
      });
      const nonDefaultFolderId = folderIds.find((id) => {
        const folder = folders.find((folder) => folder.id === id);
        return folder && !folder.is_default;
      });

      viewer = {
        isSaved,
        categoryId: nonDefaultFolderId ?? (isSaved ? folderIds[0] : null),
      };

      categories = folders.map((folder) => ({
        id: folder.id,
        name: folder.name,
        sortOrder: folder.sort_order,
      }));
    }

    return {
      list: listSummary,
      problems,
      stats,
      viewer,
      categories,
    };
  }

  // ============================================================================
  // CRUD Operations (delegated to ProblemListCrudService)
  // ============================================================================

  async createList(
    userId: string,
    data: { name: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    return this.crudService.createList(userId, data);
  }

  async updateList(
    listId: string,
    userId: string,
    data: { name?: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    return this.crudService.updateList(listId, userId, data);
  }

  async deleteList(listId: string, userId: string): Promise<void> {
    return this.crudService.deleteList(listId, userId);
  }

  async forkList(listId: string, userId: string): Promise<string> {
    return this.crudService.forkList(listId, userId);
  }

  async getListById(listId: string): Promise<ProblemListSummary | null> {
    return this.crudService.getListById(listId);
  }

  async getListsByUserId(userId: string): Promise<ProblemListSummary[]> {
    return this.crudService.getListsByUserId(userId);
  }

  async getFeaturedLists(): Promise<ProblemListSummary[]> {
    return this.crudService.getFeaturedLists();
  }

  async getDefaultList(): Promise<ProblemListSummary | null> {
    return this.crudService.getDefaultList();
  }

  // ============================================================================
  // Problem Relations (delegated to ProblemListRelationService)
  // ============================================================================

  async addProblem(
    listId: string,
    userId: string,
    problemId: number,
  ): Promise<void> {
    return this.relationService.addProblem(listId, userId, problemId);
  }

  async removeProblem(
    listId: string,
    userId: string,
    problemId: number,
  ): Promise<void> {
    return this.relationService.removeProblem(listId, userId, problemId);
  }

  async batchAddProblemToLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
    return this.relationService.batchAddProblemToLists(
      userId,
      problemId,
      listIds,
    );
  }

  async batchRemoveProblemFromLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
    return this.relationService.batchRemoveProblemFromLists(
      userId,
      problemId,
      listIds,
    );
  }

  async getUserListsForProblem(
    userId: string,
    problemId: number,
  ): Promise<
    Array<
      ProblemListSummary & {
        containsProblem: boolean;
        canEdit: boolean;
      }
    >
  > {
    return this.relationService.getUserListsForProblem(userId, problemId);
  }

  async getProblemListIds(problemId: number): Promise<string[]> {
    return this.relationService.getProblemListIds(problemId);
  }

  async getProblemsByListId(
    listId: string,
    userId?: string,
    locale?: SupportedLocale,
  ): Promise<ProblemListProblem[]> {
    return this.relationService.getProblemsByListId(
      listId,
      userId,
      locale ?? DEFAULT_LOCALE,
    );
  }

  // ============================================================================
  // Stats (delegated to ProblemListStatsService)
  // ============================================================================

  async getStats(userId?: string) {
    return this.statsService.getStats(userId);
  }

  // ============================================================================
  // Bookmarks (delegated to ProblemListBookmarkService)
  // ============================================================================

  async saveList(
    userId: string,
    listId: string,
    collectionId?: string,
  ): Promise<void> {
    return this.bookmarkService.saveList(userId, listId, collectionId);
  }

  async unsaveList(userId: string, listId: string): Promise<void> {
    return this.bookmarkService.unsaveList(userId, listId);
  }

  async isListSaved(userId: string, listId: string): Promise<boolean> {
    return this.bookmarkService.isListSaved(userId, listId);
  }

  // ============================================================================
  // Categories (delegated to ProblemListCategoryService)
  // ============================================================================

  async getCategories(userId: string): Promise<CategorySummary[]> {
    return this.categoryService.getCategories(userId);
  }

  async createCategory(
    userId: string,
    data: { name: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    return this.categoryService.createCategory(userId, data);
  }

  async updateCategory(
    categoryId: string,
    userId: string,
    data: { name?: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    return this.categoryService.updateCategory(categoryId, userId, data);
  }

  async deleteCategory(categoryId: string, userId: string): Promise<void> {
    return this.categoryService.deleteCategory(categoryId, userId);
  }

  async moveListToCategory(
    userId: string,
    listId: string,
    folderId: string | null,
  ): Promise<void> {
    return this.categoryService.moveListToCategory(userId, listId, folderId);
  }
}
