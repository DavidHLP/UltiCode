import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository, DataSource } from 'typeorm';
import { BookmarkType } from '@prisma/client';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';
import { SubmissionService } from '../submission/submission.service';
import { ProblemListProblemRelation } from './problem-list-problem-relation.entity';
import { BookmarkService } from '../bookmark/bookmark.service';
import { v4 as uuidv4 } from 'uuid';
import { PrismaService } from '../prisma.service';

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
  isSaved?: boolean; // Whether current user saved this list
  categoryId?: string; // User's category for this list
}

export interface CategorySummary {
  id: string;
  name: string;
  sortOrder: number;
  lists: ProblemListSummary[];
}

export interface UserProblemListsResponse {
  myLists: ProblemListSummary[]; // Lists created by user
  savedLists: ProblemListSummary[]; // Lists saved by user (from others)
  featured: ProblemListSummary[]; // Featured lists
  categories: CategorySummary[]; // User's custom categories
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

@Injectable()
export class ProblemListService {
  constructor(
    @InjectRepository(ProblemList)
    private listsRepository: Repository<ProblemList>,
    @InjectRepository(Problem)
    private problemsRepository: Repository<Problem>,
    @InjectRepository(ProblemListProblemRelation)
    private relationsRepository: Repository<ProblemListProblemRelation>,
    private submissionService: SubmissionService,
    private dataSource: DataSource,
    private prisma: PrismaService,
    private bookmarkService: BookmarkService,
  ) {}

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private async buildProblemCountMap(): Promise<Map<string, number>> {
    const counts = await this.relationsRepository
      .createQueryBuilder('relation')
      .select('relation.list_id', 'listId')
      .addSelect('COUNT(relation.problem_id)', 'count')
      .groupBy('relation.list_id')
      .getRawMany<{ listId: string; count: string }>();

    const countMap = new Map<string, number>();
    counts.forEach((item) => {
      countMap.set(item.listId, Number(item.count));
    });
    return countMap;
  }

  private async buildFavoritesCountMap(
    listIds?: string[],
  ): Promise<Map<string, number>> {
    if (listIds && listIds.length === 0) {
      return new Map();
    }

    const where = {
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
    list: ProblemList,
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
      // Prefer non-default folder for categoryId display
      if (!folderMap.has(item.target_id) || !item.folder.is_default) {
        folderMap.set(item.target_id, item.folder_id);
      }
    });

    // 1. Get lists created by user
    const myLists = await this.listsRepository.find({
      where: { author_id: userId },
      order: { updated_at: 'DESC' },
    });

    // 2. Get user's saved lists (from other authors)
    const savedLists =
      savedListIdArray.length > 0
        ? await this.listsRepository.find({
            where: { id: In(savedListIdArray) },
            order: { updated_at: 'DESC' },
          })
        : [];

    const savedFromOthers = savedLists.filter(
      (list) => list.author_id !== userId,
    );

    // 3. Get featured lists
    const featuredLists = await this.listsRepository.find({
      where: { is_featured: true, is_public: true },
      order: { banner_order: 'ASC', updated_at: 'DESC' },
    });

    // 4. Get user's bookmark folders (replaces old categories)
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
            ? await this.listsRepository.find({ where: { id: In(listIds) } })
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

  // For backward compatibility - returns all lists grouped
  async findAll(): Promise<UserProblemListsResponse> {
    // Return empty response for anonymous users
    return {
      myLists: [],
      savedLists: [],
      featured: await this.getFeaturedLists(),
      categories: [],
    };
  }

  async getFeaturedLists(): Promise<ProblemListSummary[]> {
    const countMap = await this.buildProblemCountMap();
    const lists = await this.listsRepository.find({
      where: { is_featured: true, is_public: true },
      order: { banner_order: 'ASC', updated_at: 'DESC' },
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
    const list = await this.listsRepository.findOne({
      where: { is_public: true },
      order: { created_at: 'ASC' },
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
    const list = await this.listsRepository.findOne({
      where: { id: listId },
    });
    if (!list) return null;
    const count = await this.relationsRepository.count({
      where: { list_id: listId },
    });
    const favoritesCountMap = await this.buildFavoritesCountMap([list.id]);
    return this.mapList(list, count, favoritesCountMap.get(list.id) ?? 0);
  }

  // ============================================================================
  // Stats
  // ============================================================================

  async getStats(userId?: string): Promise<ProblemListStats[]> {
    const lists = await this.listsRepository.find();
    const relations = await this.relationsRepository.find();

    const problemIds = Array.from(
      new Set(relations.map((rel) => Number(rel.problem_id))),
    );

    const problems =
      problemIds.length > 0
        ? await this.problemsRepository.findBy({ id: In(problemIds) })
        : [];
    const problemMap = new Map<number, Problem>();
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
  ): Promise<ProblemListProblem[]> {
    const relations = await this.relationsRepository.find({
      where: { list_id: listId },
      order: { sort_order: 'ASC', added_at: 'ASC' },
      relations: [
        'problem',
        'problem.tagRelations',
        'problem.tagRelations.tag',
      ],
    });

    if (relations.length === 0) {
      return [];
    }

    const ids = relations.map((r) => Number(r.problem_id));
    const statusMap = userId
      ? await this.submissionService.getProblemStatusMap(userId, ids)
      : null;

    return relations
      .map((rel) => rel.problem)
      .filter((problem): problem is Problem => Boolean(problem))
      .map((problem) => ({
        id: Number(problem.id),
        slug: problem.slug,
        title: problem.title,
        difficulty: problem.difficulty,
        acceptanceRate: Number(problem.acceptance_rate),
        status: statusMap
          ? (statusMap.get(Number(problem.id))?.status ?? 'todo')
          : problem.status,
        isPremium: problem.is_premium,
        hasSolution: problem.has_solution,
        completedTime: statusMap
          ? (statusMap.get(Number(problem.id))?.completed_time ?? null)
          : (problem.completed_time ?? null),
        tags: problem.tagRelations?.map((rel) => rel.tag.label) ?? [],
      }));
  }

  // ============================================================================
  // List Overview (Aggregated)
  // ============================================================================

  async getListOverview(
    listId: string,
    userId?: string,
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
    );
    const stats = this.buildStatsFromProblems(listSummary.id, problems);

    let viewer: ProblemListDetailResponse['viewer'] | undefined;
    let categories: ProblemListDetailResponse['categories'] | undefined;

    if (userId) {
      // Check if this list is in any of user's bookmark folders
      const folderIds = await this.bookmarkService.getBookmarkFolders(
        userId,
        problemListTargetType,
        listSummary.id,
      );

      const isSaved = folderIds.length > 0;
      // Get the first non-default folder as categoryId
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
    const newListId = uuidv4();
    const newList = this.listsRepository.create({
      id: newListId,
      name: data.name,
      description: data.description ?? '',
      author_id: userId,
      is_public: data.isPublic ?? false,
      is_featured: false,
      created_at: new Date(),
      updated_at: new Date(),
    });

    await this.listsRepository.save(newList);
    return this.mapList(newList, 0, 0);
  }

  async updateList(
    listId: string,
    userId: string,
    data: { name?: string; description?: string; isPublic?: boolean },
  ): Promise<ProblemListSummary> {
    const list = await this.listsRepository.findOne({ where: { id: listId } });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (list.author_id !== userId) {
      throw new ForbiddenException(
        'You do not have permission to edit this list',
      );
    }

    if (data.name !== undefined) list.name = data.name;
    if (data.description !== undefined)
      list.description = data.description ?? '';
    if (data.isPublic !== undefined) list.is_public = data.isPublic;
    list.updated_at = new Date();

    await this.listsRepository.save(list);

    const count = await this.relationsRepository.count({
      where: { list_id: listId },
    });
    const favoritesCountMap = await this.buildFavoritesCountMap([list.id]);
    return this.mapList(list, count, favoritesCountMap.get(list.id) ?? 0);
  }

  async deleteList(listId: string, userId: string): Promise<void> {
    const list = await this.listsRepository.findOne({ where: { id: listId } });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (list.author_id !== userId) {
      throw new ForbiddenException(
        'You do not have permission to delete this list',
      );
    }

    await this.listsRepository.remove(list);
  }

  async forkList(listId: string, userId: string): Promise<string> {
    const originalList = await this.listsRepository.findOne({
      where: { id: listId },
    });
    if (!originalList) {
      throw new NotFoundException('List not found');
    }

    const relations = await this.relationsRepository.find({
      where: { list_id: listId },
    });

    const newListId = uuidv4();
    const newList = this.listsRepository.create({
      id: newListId,
      name: `${originalList.name} (Copy)`,
      description: originalList.description,
      author_id: userId,
      is_public: false,
      is_featured: false,
      created_at: new Date(),
      updated_at: new Date(),
    });

    await this.dataSource.transaction(async (manager) => {
      await manager.save(newList);
      const newRelations = relations.map((r) =>
        this.relationsRepository.create({
          list_id: newListId,
          problem_id: r.problem_id,
          sort_order: r.sort_order,
        }),
      );
      await manager.save(newRelations);
    });

    return newListId;
  }

  async getListsByUserId(userId: string): Promise<ProblemListSummary[]> {
    const lists = await this.listsRepository.find({
      where: { author_id: userId },
      order: { updated_at: 'DESC' },
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
    const list = await this.listsRepository.findOne({ where: { id: listId } });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (list.author_id !== userId) {
      throw new ForbiddenException(
        'You do not have permission to edit this list',
      );
    }

    const problem = await this.problemsRepository.findOne({
      where: { id: problemId },
    });
    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    const exists = await this.relationsRepository.findOne({
      where: { list_id: listId, problem_id: problemId },
    });
    if (exists) return;

    const count = await this.relationsRepository.count({
      where: { list_id: listId },
    });

    const relation = this.relationsRepository.create({
      list_id: listId,
      problem_id: problemId,
      sort_order: count,
    });
    await this.relationsRepository.save(relation);
  }

  async removeProblem(
    listId: string,
    userId: string,
    problemId: number,
  ): Promise<void> {
    const list = await this.listsRepository.findOne({ where: { id: listId } });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (list.author_id !== userId) {
      throw new ForbiddenException(
        'You do not have permission to edit this list',
      );
    }

    await this.relationsRepository.delete({
      list_id: listId,
      problem_id: problemId,
    });
  }

  async batchAddProblemToLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
    // Verify problem exists
    const problem = await this.problemsRepository.findOne({
      where: { id: problemId },
    });
    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

    // Verify all lists exist and user has permission to edit them
    const lists = await this.listsRepository.find({
      where: { id: In(listIds) },
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

    // Add problem to all lists
    await this.dataSource.transaction(async (manager) => {
      for (const listId of listIds) {
        // Check if already exists
        const exists = await manager.findOne(ProblemListProblemRelation, {
          where: { list_id: listId, problem_id: problemId },
        });
        if (exists) continue;

        // Get current max sort order
        const count = await manager.count(ProblemListProblemRelation, {
          where: { list_id: listId },
        });

        const relation = manager.create(ProblemListProblemRelation, {
          list_id: listId,
          problem_id: problemId,
          sort_order: count,
        });
        await manager.save(relation);
      }
    });
  }

  async batchRemoveProblemFromLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
    // Verify lists exist and user has permission
    const lists = await this.listsRepository.find({
      where: { id: In(listIds) },
    });

    for (const list of lists) {
      if (list.author_id !== userId) {
        throw new ForbiddenException(
          `You do not have permission to edit list: ${list.name}`,
        );
      }
    }

    // Remove from all lists
    await this.relationsRepository.delete({
      list_id: In(listIds),
      problem_id: problemId,
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
    const myLists = await this.listsRepository.find({
      where: { author_id: userId },
      order: { updated_at: 'DESC' },
    });

    // Get problem count map
    const countMap = await this.buildProblemCountMap();
    const favoritesCountMap = await this.buildFavoritesCountMap(
      myLists.map((l) => l.id),
    );

    // Check which lists contain this problem
    const relations = await this.relationsRepository.find({
      where: {
        list_id: In(myLists.map((l) => l.id)),
        problem_id: problemId,
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
      canEdit: true, // User is the author
    }));
  }

  async getProblemListIds(problemId: number): Promise<string[]> {
    const relations = await this.relationsRepository.find({
      where: { problem_id: problemId },
      select: ['list_id'],
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
    const list = await this.listsRepository.findOne({ where: { id: listId } });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (!list.is_public && list.author_id !== userId) {
      throw new ForbiddenException('This list is private');
    }

    // If folderId is provided, add to that folder
    // Otherwise, add to default folder (favorites)
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
    // Remove from all user's bookmark folders
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
  // Category Management (now delegates to BookmarkService)
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
          ? await this.listsRepository.find({ where: { id: In(listIds) } })
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
        ? await this.listsRepository.find({ where: { id: In(listIds) } })
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
    // Check if list is saved in any folder
    const isSaved = await this.isListSaved(userId, listId);
    if (!isSaved) {
      throw new NotFoundException('List is not saved');
    }

    if (folderId) {
      // Add to the specified folder
      await this.bookmarkService.addBookmark(userId, folderId, {
        targetId: listId,
        targetType: problemListTargetType,
      });
    } else {
      // Remove from all non-default folders, keep in default
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
