import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { BookmarkType } from '@prisma/client';
import { SubmissionService } from '../submission/submission.service';
import { BookmarkService } from '../bookmark/bookmark.service';
import { v4 as uuidv4 } from 'uuid';
import { PrismaService } from '../prisma.service';
import { I18nService } from '../i18n/i18n.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  TRANSLATABLE_ENTITIES,
} from '../i18n/i18n.constants';

const problemListTargetType = BookmarkType.PROBLEM_LIST;

// ============================================================================
// Types
// ============================================================================

export interface ProblemListSummary {
  id: string;
  name: string;
  description?: string;
  authorId: string;
  isPublic: boolean;
  isFeatured: boolean;
  bannerTag?: string;
  bannerIcon?: string;
  bannerTheme?: string;
  bannerOrder?: number;
  createdAt: Date;
  updatedAt: Date;
  problemCount: number;
  favoritesCount: number;
  isSaved?: boolean;
  categoryId?: string;
}

export interface CategorySummary {
  id: string;
  name: string;
  sortOrder: number;
  lists: ProblemListSummary[];
}

export interface UserProblemListsResponse {
  myLists: ProblemListSummary[];
  savedLists: ProblemListSummary[];
  featured: ProblemListSummary[];
  categories: CategorySummary[];
}

export interface ProblemListDetailResponse {
  list: ProblemListSummary | null;
  problems: ProblemListProblem[];
  stats: ProblemListStats | null;
  viewer?: {
    isSaved: boolean;
    categoryId: string | null;
  };
  categories?: Array<{
    id: string;
    name: string;
    sortOrder: number;
  }>;
}

export interface ProblemListStats {
  listId: string;
  totalCount: number;
  solvedCount: number;
  attemptedCount: number;
  todoCount: number;
  progress: number;
}

export interface ProblemListProblem {
  id: number;
  slug: string;
  title: string;
  difficulty: string;
  acceptanceRate: number;
  status: string;
  isPremium: boolean;
  hasSolution: boolean;
  completedTime?: Date | null;
  tags: string[];
}

// Helper function to convert Prisma ProblemList to TypeORM-compatible format
function _convertProblemListFromPrisma(
  list: Prisma.ProblemListGetPayload<Record<string, never>>,
): ProblemListSummary {
  return {
    id: list.id,
    name: list.name,
    description: list.description ?? undefined,
    authorId: list.author_id,
    isPublic: list.is_public,
    isFeatured: list.is_featured,
    bannerTag: list.banner_tag ?? undefined,
    bannerIcon: list.banner_icon ?? undefined,
    bannerTheme: list.banner_theme ?? undefined,
    bannerOrder: list.banner_order ?? undefined,
    createdAt: list.created_at,
    updatedAt: list.updated_at,
    problemCount: 0,
    favoritesCount: 0,
  };
}

@Injectable()
export class ProblemListService {
  constructor(
    private prisma: PrismaService,
    private submissionService: SubmissionService,
    private bookmarkService: BookmarkService,
    private readonly i18nService: I18nService,
  ) {}

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private async buildProblemCountMap(): Promise<Map<string, number>> {
    const counts = await this.prisma.problemListProblemRelation.groupBy({
      by: ['list_id'],
      _count: { problem_id: true },
    });

    const countMap = new Map<string, number>();
    counts.forEach((item) => {
      countMap.set(item.list_id, item._count.problem_id);
    });
    return countMap;
  }

  private async buildFavoritesCountMap(
    listIds?: string[],
  ): Promise<Map<string, number>> {
    if (listIds && listIds.length === 0) {
      return new Map();
    }

    const where: Prisma.BookmarkWhereInput = {
      target_type: problemListTargetType,
      folder: { is_default: true },
      ...(listIds ? { target_id: { in: listIds } } : {}),
    };

    const counts = await this.prisma.bookmark.groupBy({
      by: ['target_id'],
      where,
      _count: true,
    });

    const countMap = new Map<string, number>();
    counts.forEach((item) => {
      countMap.set(item.target_id, item._count);
    });
    return countMap;
  }

  private mapList(
    list: Prisma.ProblemListGetPayload<Record<string, never>>,
    problemCount: number,
    favoritesCount: number,
    options?: { isSaved?: boolean; categoryId?: string },
  ): ProblemListSummary {
    return {
      id: list.id,
      name: list.name,
      description: list.description ?? undefined,
      authorId: list.author_id,
      isPublic: list.is_public,
      isFeatured: list.is_featured,
      bannerTag: list.banner_tag ?? undefined,
      bannerIcon: list.banner_icon ?? undefined,
      bannerTheme: list.banner_theme ?? undefined,
      bannerOrder: list.banner_order ?? undefined,
      createdAt: list.created_at,
      updatedAt: list.updated_at,
      problemCount,
      favoritesCount,
      isSaved: options?.isSaved,
      categoryId: options?.categoryId,
    };
  }

  private buildStatsFromProblems(
    listId: string,
    problems: ProblemListProblem[],
  ): ProblemListStats {
    let solvedCount = 0;
    let attemptedCount = 0;
    let todoCount = 0;

    problems.forEach((problem) => {
      if (problem.status === 'solved') solvedCount += 1;
      else if (problem.status === 'attempted') attemptedCount += 1;
      else todoCount += 1;
    });

    const totalCount = problems.length;
    const progress =
      totalCount === 0 ? 0 : Math.round((solvedCount / totalCount) * 100);

    return {
      listId,
      totalCount,
      solvedCount,
      attemptedCount,
      todoCount,
      progress,
    };
  }

  // ============================================================================
  // Main API: Get User's Problem Lists
  // ============================================================================

  async getUserProblemLists(userId: string): Promise<UserProblemListsResponse> {
    const countMap = await this.buildProblemCountMap();

    // Get all bookmarks for this user that are PROBLEM_LIST type
    const bookmarkItems = await this.prisma.bookmark.findMany({
      where: {
        target_type: problemListTargetType,
        folder: { user_id: userId },
      },
      include: { folder: true },
    });

    const savedListIds = new Set(bookmarkItems.map((item) => item.target_id));
    const savedListIdArray = Array.from(savedListIds);

    // Map listId -> folderId for category display
    const folderMap = new Map<string, string>();
    bookmarkItems.forEach((item) => {
      if (!folderMap.has(item.target_id) || !item.folder.is_default) {
        folderMap.set(item.target_id, item.folder_id);
      }
    });

    // 1. Get lists created by user
    const myLists = await this.prisma.problemList.findMany({
      where: { author_id: userId },
      orderBy: { updated_at: 'desc' },
    });

    // 2. Get user's saved lists (from other authors)
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

    // 3. Get featured lists
    const featuredLists = await this.prisma.problemList.findMany({
      where: { is_featured: true, is_public: true },
      orderBy: [{ banner_order: 'asc' }, { updated_at: 'desc' }],
    });

    // 4. Get user's bookmark folders
    const folders = await this.bookmarkService.getUserFolders(userId);

    // Build bookmark items map by folder
    const folderItemsMap = new Map<string, string[]>();
    bookmarkItems.forEach((item) => {
      const list = folderItemsMap.get(item.folder_id) ?? [];
      list.push(item.target_id);
      folderItemsMap.set(item.folder_id, list);
    });

    const favoriteCountIds = Array.from(
      new Set([
        ...myLists.map((list) => list.id),
        ...savedLists.map((list) => list.id),
        ...featuredLists.map((list) => list.id),
      ]),
    );
    const favoritesCountMap =
      await this.buildFavoritesCountMap(favoriteCountIds);

    // Build category response from folders
    const categoryResponses: CategorySummary[] = await Promise.all(
      folders.map(async (folder) => {
        const listIds = folderItemsMap.get(folder.id) ?? [];
        const lists =
          listIds.length > 0
            ? await this.prisma.problemList.findMany({
                where: { id: { in: listIds } },
              })
            : [];
        return {
          id: folder.id,
          name: folder.name,
          sortOrder: folder.sortOrder,
          lists: lists.map((list) =>
            this.mapList(
              list,
              countMap.get(list.id) ?? 0,
              favoritesCountMap.get(list.id) ?? 0,
              { isSaved: true, categoryId: folder.id },
            ),
          ),
        };
      }),
    );

    return {
      myLists: myLists.map((list) =>
        this.mapList(
          list,
          countMap.get(list.id) ?? 0,
          favoritesCountMap.get(list.id) ?? 0,
          { isSaved: savedListIds.has(list.id) },
        ),
      ),
      savedLists: savedFromOthers.map((list) =>
        this.mapList(
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
        this.mapList(
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

  async findAll(
    _locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<UserProblemListsResponse> {
    return {
      myLists: [],
      savedLists: [],
      featured: await this.getFeaturedLists(),
      categories: [],
    };
  }

  async getFeaturedLists(): Promise<ProblemListSummary[]> {
    const countMap = await this.buildProblemCountMap();
    const lists = await this.prisma.problemList.findMany({
      where: { is_featured: true, is_public: true },
      orderBy: [{ banner_order: 'asc' }, { updated_at: 'desc' }],
    });
    const favoritesCountMap = await this.buildFavoritesCountMap(
      lists.map((list) => list.id),
    );
    return lists.map((list) =>
      this.mapList(
        list,
        countMap.get(list.id) ?? 0,
        favoritesCountMap.get(list.id) ?? 0,
      ),
    );
  }

  async getDefaultList(): Promise<ProblemListSummary | null> {
    const list = await this.prisma.problemList.findFirst({
      where: { is_public: true },
      orderBy: { created_at: 'asc' },
    });
    if (list) {
      const countMap = await this.buildProblemCountMap();
      const favoritesCountMap = await this.buildFavoritesCountMap([list.id]);
      return this.mapList(
        list,
        countMap.get(list.id) ?? 0,
        favoritesCountMap.get(list.id) ?? 0,
      );
    }
    return null;
  }

  async getListById(listId: string): Promise<ProblemListSummary | null> {
    const list = await this.prisma.problemList.findUnique({
      where: { id: listId },
    });
    if (!list) return null;
    const count = await this.prisma.problemListProblemRelation.count({
      where: { list_id: listId },
    });
    const favoritesCountMap = await this.buildFavoritesCountMap([list.id]);
    return this.mapList(list, count, favoritesCountMap.get(list.id) ?? 0);
  }

  // ============================================================================
  // Stats
  // ============================================================================

  async getStats(userId?: string): Promise<ProblemListStats[]> {
    const lists = await this.prisma.problemList.findMany();
    const relations = await this.prisma.problemListProblemRelation.findMany();

    const problemIds = Array.from(
      new Set(relations.map((rel) => Number(rel.problem_id))),
    );

    const problems =
      problemIds.length > 0
        ? await this.prisma.problem.findMany({
            where: { id: { in: problemIds } },
          })
        : [];

    const problemMap = new Map<number, (typeof problems)[0]>();
    problems.forEach((problem) => problemMap.set(Number(problem.id), problem));

    const statusMap =
      userId && problemIds.length > 0
        ? await this.submissionService.getProblemStatusMap(userId, problemIds)
        : null;

    const grouped = new Map<string, number[]>();
    relations.forEach((rel) => {
      const pid = Number(rel.problem_id);
      if (!problemMap.has(pid)) return;
      const list = grouped.get(rel.list_id) ?? [];
      list.push(pid);
      grouped.set(rel.list_id, list);
    });

    return lists.map((list) => {
      const ids = grouped.get(list.id) ?? [];
      let solvedCount = 0;
      let attemptedCount = 0;
      let todoCount = 0;

      ids.forEach((id) => {
        const problem = problemMap.get(id);
        if (!problem) return;
        const status = statusMap
          ? (statusMap.get(id)?.status ?? 'todo')
          : problem.status;
        if (status === 'solved') solvedCount += 1;
        else if (status === 'attempted') attemptedCount += 1;
        else todoCount += 1;
      });

      const totalCount = ids.length;
      const progress =
        totalCount === 0 ? 0 : Math.round((solvedCount / totalCount) * 100);

      return {
        listId: list.id,
        totalCount,
        solvedCount,
        attemptedCount,
        todoCount,
        progress,
      };
    });
  }

  // ============================================================================
  // Get Problems by List
  // ============================================================================

  async getProblemsByListId(
    listId: string,
    userId?: string,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ProblemListProblem[]> {
    const relations = await this.prisma.problemListProblemRelation.findMany({
      where: { list_id: listId },
      orderBy: [{ sort_order: 'asc' }, { added_at: 'asc' }],
      include: {
        problem: {
          include: {
            tagRelations: {
              include: {
                tag: true,
              },
            },
          },
        },
      },
    });

    if (relations.length === 0) {
      return [];
    }

    const ids = relations.map((r) => Number(r.problem_id));
    const statusMap = userId
      ? await this.submissionService.getProblemStatusMap(userId, ids)
      : null;

    // Apply problem translations
    const problemIds = relations.map((r) => r.problem_id);
    const problemTranslationsMap = await this.i18nService.getBatchTranslations(
      'PROBLEM',
      problemIds,
      locale,
    );

    return relations.map((rel) => {
      const problem = rel.problem;
      const translations: Map<string, string> =
        problemTranslationsMap.get(String(problem.id)) ??
        new Map<string, string>();
      const translatedProblem = this.i18nService.applyTranslations(
        problem as unknown as Record<string, unknown>,
        translations,
        TRANSLATABLE_ENTITIES.PROBLEM.fields,
      );

      const slug =
        (translatedProblem.slug as string | undefined) ?? problem.slug;
      const title =
        (translatedProblem.title as string | undefined) ?? problem.title;
      const difficulty =
        (translatedProblem.difficulty as string | undefined) ??
        (problem.difficulty as string);
      const status =
        (translatedProblem.status as string | undefined) ??
        (problem.status as string);
      const isPremium =
        (translatedProblem.is_premium as boolean | undefined) ??
        problem.is_premium;
      const hasSolution =
        (translatedProblem.has_solution as boolean | undefined) ??
        problem.has_solution;
      const acceptanceRate =
        (translatedProblem.acceptance_rate as number | undefined) ??
        Number(problem.acceptance_rate);

      return {
        id: Number(translatedProblem.id),
        slug,
        title,
        difficulty,
        acceptanceRate,
        status: statusMap
          ? (statusMap.get(Number(translatedProblem.id))?.status ?? 'todo')
          : status,
        isPremium,
        hasSolution,
        completedTime: statusMap
          ? (statusMap.get(Number(translatedProblem.id))?.completed_time ??
            null)
          : null,
        tags:
          (
            translatedProblem as unknown as {
              tagRelations?: Array<{ tag: { label: string } }>;
            }
          ).tagRelations?.map((rel) => rel.tag.label) ?? [],
      };
    });
  }

  // ============================================================================
  // List Overview (Aggregated)
  // ============================================================================

  async getListOverview(
    listId: string,
    userId?: string,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ProblemListDetailResponse> {
    let listSummary = await this.getListById(listId);
    if (!listSummary) {
      listSummary = await this.getDefaultList();
    }

    if (!listSummary) {
      return { list: null, problems: [], stats: null };
    }

    const problems = await this.getProblemsByListId(
      listSummary.id,
      userId ?? undefined,
      locale,
    );
    const stats = this.buildStatsFromProblems(listSummary.id, problems);

    let viewer: ProblemListDetailResponse['viewer'] | undefined;
    let categories: ProblemListDetailResponse['categories'] | undefined;

    if (userId) {
      const folderIds = await this.bookmarkService.getBookmarkFolders(
        userId,
        problemListTargetType,
        listSummary.id,
      );

      const isSaved = folderIds.length > 0;
      const folders = await this.bookmarkService.getUserFolders(userId);
      const nonDefaultFolderId = folderIds.find((id) => {
        const folder = folders.find((f) => f.id === id);
        return folder && !folder.isDefault;
      });

      viewer = {
        isSaved,
        categoryId: nonDefaultFolderId ?? (isSaved ? folderIds[0] : null),
      };

      categories = folders.map((folder) => ({
        id: folder.id,
        name: folder.name,
        sortOrder: folder.sortOrder,
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
  // List CRUD
  // ============================================================================

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

    return this.mapList(newList, 0, 0);
  }

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

    const count = await this.prisma.problemListProblemRelation.count({
      where: { list_id: listId },
    });
    const favoritesCountMap = await this.buildFavoritesCountMap([list.id]);
    return this.mapList(updated, count, favoritesCountMap.get(list.id) ?? 0);
  }

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

    // Use Prisma transaction instead of DataSource transaction
    await this.prisma.$transaction(async (tx) => {
      const _newList = await tx.problemList.create({
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

      // Copy all relations
      const _newRelations = await tx.problemListProblemRelation.createMany({
        data: relations.map((r) => ({
          list_id: newListId,
          problem_id: r.problem_id,
          sort_order: r.sort_order,
        })),
      });
    });

    return newListId;
  }

  async getListsByUserId(userId: string): Promise<ProblemListSummary[]> {
    const lists = await this.prisma.problemList.findMany({
      where: { author_id: userId },
      orderBy: { updated_at: 'desc' },
    });
    const countMap = await this.buildProblemCountMap();
    const favoritesCountMap = await this.buildFavoritesCountMap(
      lists.map((list) => list.id),
    );
    return lists.map((list) =>
      this.mapList(
        list,
        countMap.get(list.id) ?? 0,
        favoritesCountMap.get(list.id) ?? 0,
      ),
    );
  }

  // ============================================================================
  // Problem Management in List
  // ============================================================================

  async addProblem(
    listId: string,
    userId: string,
    problemId: number,
  ): Promise<void> {
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

    const problem = await this.prisma.problem.findUnique({
      where: { id: BigInt(problemId) },
    });
    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    const exists = await this.prisma.problemListProblemRelation.findUnique({
      where: {
        list_id_problem_id: {
          list_id: listId,
          problem_id: BigInt(problemId),
        },
      },
    });
    if (exists) return;

    const count = await this.prisma.problemListProblemRelation.count({
      where: { list_id: listId },
    });

    await this.prisma.problemListProblemRelation.create({
      data: {
        list_id: listId,
        problem_id: BigInt(problemId),
        sort_order: count,
      },
    });
  }

  async removeProblem(
    listId: string,
    userId: string,
    problemId: number,
  ): Promise<void> {
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

    await this.prisma.problemListProblemRelation.delete({
      where: {
        list_id_problem_id: {
          list_id: listId,
          problem_id: BigInt(problemId),
        },
      },
    });
  }

  async batchAddProblemToLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
    // Verify problem exists
    const problem = await this.prisma.problem.findUnique({
      where: { id: BigInt(problemId) },
    });
    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    // Verify all lists exist and user has permission to edit them
    const lists = await this.prisma.problemList.findMany({
      where: { id: { in: listIds } },
    });

    if (lists.length !== listIds.length) {
      throw new NotFoundException('One or more lists not found');
    }

    for (const list of lists) {
      if (list.author_id !== userId) {
        throw new ForbiddenException(
          `You do not have permission to edit list: ${list.name}`,
        );
      }
    }

    // Use Prisma transaction
    await this.prisma.$transaction(async (tx) => {
      for (const listId of listIds) {
        // Check if already exists
        const exists = await tx.problemListProblemRelation.findUnique({
          where: {
            list_id_problem_id: {
              list_id: listId,
              problem_id: BigInt(problemId),
            },
          },
        });
        if (exists) continue;

        // Get current max sort order
        const count = await tx.problemListProblemRelation.count({
          where: { list_id: listId },
        });

        await tx.problemListProblemRelation.create({
          data: {
            list_id: listId,
            problem_id: BigInt(problemId),
            sort_order: count,
          },
        });
      }
    });
  }

  async batchRemoveProblemFromLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
    // Verify lists exist and user has permission
    const lists = await this.prisma.problemList.findMany({
      where: { id: { in: listIds } },
    });

    for (const list of lists) {
      if (list.author_id !== userId) {
        throw new ForbiddenException(
          `You do not have permission to edit list: ${list.name}`,
        );
      }
    }

    // Remove from all lists
    await this.prisma.problemListProblemRelation.deleteMany({
      where: {
        list_id: { in: listIds },
        problem_id: BigInt(problemId),
      },
    });
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
    // Get all lists user created
    const myLists = await this.prisma.problemList.findMany({
      where: { author_id: userId },
      orderBy: { updated_at: 'desc' },
    });

    // Get problem count map
    const countMap = await this.buildProblemCountMap();
    const favoritesCountMap = await this.buildFavoritesCountMap(
      myLists.map((l) => l.id),
    );

    // Check which lists contain this problem
    const relations = await this.prisma.problemListProblemRelation.findMany({
      where: {
        list_id: { in: myLists.map((l) => l.id) },
        problem_id: BigInt(problemId),
      },
    });

    const listsWithProblem = new Set(relations.map((r) => r.list_id));

    return myLists.map((list) => ({
      ...this.mapList(
        list,
        countMap.get(list.id) ?? 0,
        favoritesCountMap.get(list.id) ?? 0,
      ),
      containsProblem: listsWithProblem.has(list.id),
      canEdit: true,
    }));
  }

  async getProblemListIds(problemId: number): Promise<string[]> {
    const relations = await this.prisma.problemListProblemRelation.findMany({
      where: { problem_id: BigInt(problemId) },
      select: { list_id: true },
    });
    return relations.map((r) => r.list_id);
  }

  // ============================================================================
  // Save/Unsave List
  // ============================================================================

  async saveList(
    userId: string,
    listId: string,
    collectionId?: string,
  ): Promise<void> {
    const list = await this.prisma.problemList.findUnique({
      where: { id: listId },
    });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (!list.is_public && list.author_id !== userId) {
      throw new ForbiddenException('This list is private');
    }

    // If folderId is provided, add to that folder
    if (collectionId) {
      await this.bookmarkService.addBookmark(userId, collectionId, {
        targetId: listId,
        targetType: problemListTargetType,
      });
    } else {
      // Add to default folder
      const defaultFolder =
        await this.bookmarkService.ensureDefaultFolder(userId);
      await this.bookmarkService.addBookmark(userId, defaultFolder.id, {
        targetId: listId,
        targetType: problemListTargetType,
      });
    }
  }

  async unsaveList(userId: string, listId: string): Promise<void> {
    await this.prisma.bookmark.deleteMany({
      where: {
        target_type: problemListTargetType,
        target_id: listId,
        folder: { user_id: userId },
      },
    });
  }

  async isListSaved(userId: string, listId: string): Promise<boolean> {
    const count = await this.prisma.bookmark.count({
      where: {
        target_type: problemListTargetType,
        target_id: listId,
        folder: { user_id: userId },
      },
    });
    return count > 0;
  }

  // ============================================================================
  // Category Management (delegates to BookmarkService)
  // ============================================================================

  async getCategories(userId: string): Promise<CategorySummary[]> {
    const countMap = await this.buildProblemCountMap();
    const folders = await this.bookmarkService.getUserFolders(userId);

    // Get all bookmarks of type PROBLEM_LIST for this user
    const bookmarkItems = await this.prisma.bookmark.findMany({
      where: {
        target_type: problemListTargetType,
        folder: { user_id: userId },
      },
    });

    // Build map of folderId -> listIds
    const folderItemsMap = new Map<string, string[]>();
    bookmarkItems.forEach((item) => {
      const list = folderItemsMap.get(item.folder_id) ?? [];
      list.push(item.target_id);
      folderItemsMap.set(item.folder_id, list);
    });

    const allListIds = bookmarkItems.map((item) => item.target_id);
    const favoritesCountMap = await this.buildFavoritesCountMap(allListIds);

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
          this.mapList(
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

  async updateCategory(
    categoryId: string,
    userId: string,
    data: { name?: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    const folder = await this.bookmarkService.updateFolder(userId, categoryId, {
      name: data.name,
      sortOrder: data.sortOrder,
    });

    // Get lists in this folder
    const countMap = await this.buildProblemCountMap();
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
    const favoritesCountMap = await this.buildFavoritesCountMap(listIds);

    return {
      id: folder.id,
      name: folder.name,
      sortOrder: folder.sortOrder,
      lists: lists.map((list) =>
        this.mapList(
          list,
          countMap.get(list.id) ?? 0,
          favoritesCountMap.get(list.id) ?? 0,
          { isSaved: true, categoryId: folder.id },
        ),
      ),
    };
  }

  async deleteCategory(categoryId: string, userId: string): Promise<void> {
    await this.bookmarkService.deleteFolder(userId, categoryId);
  }

  async moveListToCategory(
    userId: string,
    listId: string,
    folderId: string | null,
  ): Promise<void> {
    const isSaved = await this.isListSaved(userId, listId);
    if (!isSaved) {
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
