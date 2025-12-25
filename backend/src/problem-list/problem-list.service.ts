import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository, DataSource } from 'typeorm';
import { EdgeOperationTargetType, EdgeOperationType } from '@prisma/client';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';
import { SubmissionService } from '../submission/submission.service';
import { ProblemListProblemRelation } from './problem-list-problem-relation.entity';
import { UserProblemListCategoryItem } from './user-problem-list-category-item.entity';
import { UserProblemListCategory } from './user-problem-list-category.entity';
import { v4 as uuidv4 } from 'uuid';
import { PrismaService } from '../prisma.service';

const problemListTargetType = 'PROBLEM_LIST' as EdgeOperationTargetType;

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
    @InjectRepository(UserProblemListCategoryItem)
    private categoryItemsRepository: Repository<UserProblemListCategoryItem>,
    @InjectRepository(UserProblemListCategory)
    private categoriesRepository: Repository<UserProblemListCategory>,
    private submissionService: SubmissionService,
    private dataSource: DataSource,
    private prisma: PrismaService,
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
      operation_type: EdgeOperationType.FAVORITE,
      ...(listIds ? { target_id: { in: listIds } } : {}),
    };

    const counts = await this.prisma.edgeOperation.groupBy({
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

    const favorites = await this.prisma.edgeOperation.findMany({
      where: {
        operator_id: userId,
        target_type: problemListTargetType,
        operation_type: EdgeOperationType.FAVORITE,
      },
      select: { target_id: true },
    });

    const savedListIds = new Set(favorites.map((fav) => fav.target_id));
    const savedListIdArray = Array.from(savedListIds);

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
      order: { updated_at: 'DESC' },
    });

    // 4. Get user's categories with their lists
    const categories = await this.categoriesRepository.find({
      where: { user_id: userId },
      relations: ['items', 'items.list'],
      order: { sort_order: 'ASC' },
    });

    const categoryMap = new Map<string, string>();
    categories.forEach((cat) => {
      (cat.items ?? []).forEach((item) => {
        if (item.list_id) {
          categoryMap.set(item.list_id, item.category_id);
        }
      });
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
            categoryId: categoryMap.get(list.id) ?? undefined,
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
            categoryId: categoryMap.get(list.id) ?? undefined,
          },
        ),
      ),
      categories: categories.map((cat) => ({
        id: cat.id,
        name: cat.name,
        sortOrder: cat.sort_order,
        lists: (cat.items ?? [])
          .filter((item) => item.list)
          .map((item) =>
            this.mapList(
              item.list,
              countMap.get(item.list.id) ?? 0,
              favoritesCountMap.get(item.list.id) ?? 0,
              { isSaved: true, categoryId: cat.id },
            ),
          ),
      })),
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
      order: { updated_at: 'DESC' },
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
      const favorite = await this.prisma.edgeOperation.findUnique({
        where: {
          operator_id_operation_type_target_type_target_id: {
            operator_id: userId,
            operation_type: EdgeOperationType.FAVORITE,
            target_type: problemListTargetType,
            target_id: listSummary.id,
          },
        },
      });

      const categoryItem = await this.categoryItemsRepository.findOne({
        where: { user_id: userId, list_id: listSummary.id },
      });

      viewer = {
        isSaved: !!favorite,
        categoryId: categoryItem?.category_id ?? null,
      };

      const categoryRows = await this.categoriesRepository.find({
        where: { user_id: userId },
        order: { sort_order: 'ASC' },
      });

      categories = categoryRows.map((cat) => ({
        id: cat.id,
        name: cat.name,
        sortOrder: cat.sort_order,
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

  // ============================================================================
  // Save/Unsave List
  // ============================================================================

  async saveList(
    userId: string,
    listId: string,
    categoryId?: string,
  ): Promise<void> {
    const list = await this.listsRepository.findOne({ where: { id: listId } });
    if (!list) {
      throw new NotFoundException('List not found');
    }
    if (!list.is_public && list.author_id !== userId) {
      throw new ForbiddenException('This list is private');
    }

    const existingFavorite = await this.prisma.edgeOperation.findUnique({
      where: {
        operator_id_operation_type_target_type_target_id: {
          operator_id: userId,
          operation_type: EdgeOperationType.FAVORITE,
          target_type: problemListTargetType,
          target_id: listId,
        },
      },
    });

    if (!existingFavorite) {
      await this.prisma.edgeOperation.create({
        data: {
          operator_id: userId,
          target_type: problemListTargetType,
          target_id: listId,
          operation_type: EdgeOperationType.FAVORITE,
        },
      });
    }

    if (categoryId !== undefined) {
      if (categoryId) {
        const category = await this.categoriesRepository.findOne({
          where: { id: categoryId, user_id: userId },
        });
        if (!category) {
          throw new NotFoundException('Category not found');
        }
      }

      const existingItem = await this.categoryItemsRepository.findOne({
        where: { user_id: userId, list_id: listId },
      });

      if (categoryId) {
        if (existingItem) {
          existingItem.category_id = categoryId;
          await this.categoryItemsRepository.save(existingItem);
        } else {
          const newItem = this.categoryItemsRepository.create({
            user_id: userId,
            list_id: listId,
            category_id: categoryId,
          });
          await this.categoryItemsRepository.save(newItem);
        }
      } else if (existingItem) {
        await this.categoryItemsRepository.remove(existingItem);
      }
    }
  }

  async unsaveList(userId: string, listId: string): Promise<void> {
    await this.prisma.edgeOperation.deleteMany({
      where: {
        operator_id: userId,
        target_type: problemListTargetType,
        target_id: listId,
        operation_type: EdgeOperationType.FAVORITE,
      },
    });
    await this.categoryItemsRepository.delete({
      user_id: userId,
      list_id: listId,
    });
  }

  async isListSaved(userId: string, listId: string): Promise<boolean> {
    const favorite = await this.prisma.edgeOperation.findUnique({
      where: {
        operator_id_operation_type_target_type_target_id: {
          operator_id: userId,
          operation_type: EdgeOperationType.FAVORITE,
          target_type: problemListTargetType,
          target_id: listId,
        },
      },
    });
    return !!favorite;
  }

  // ============================================================================
  // Category Management
  // ============================================================================

  async getCategories(userId: string): Promise<CategorySummary[]> {
    const countMap = await this.buildProblemCountMap();
    const categories = await this.categoriesRepository.find({
      where: { user_id: userId },
      relations: ['items', 'items.list'],
      order: { sort_order: 'ASC' },
    });

    const listIds = categories.flatMap((cat) =>
      (cat.items ?? []).map((item) => item.list_id),
    );
    const favoritesCountMap = await this.buildFavoritesCountMap(listIds);

    return categories.map((cat) => ({
      id: cat.id,
      name: cat.name,
      sortOrder: cat.sort_order,
      lists: (cat.items ?? [])
        .filter((item) => item.list)
        .map((item) =>
          this.mapList(
            item.list,
            countMap.get(item.list.id) ?? 0,
            favoritesCountMap.get(item.list.id) ?? 0,
            {
              isSaved: true,
              categoryId: cat.id,
            },
          ),
        ),
    }));
  }

  async createCategory(
    userId: string,
    data: { name: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    // Get max sort_order if not provided
    let sortOrder = data.sortOrder;
    if (sortOrder === undefined) {
      const maxResult = await this.categoriesRepository
        .createQueryBuilder('cat')
        .select('MAX(cat.sort_order)', 'maxOrder')
        .where('cat.user_id = :userId', { userId })
        .getRawOne<{ maxOrder: number | null }>();
      sortOrder = (maxResult?.maxOrder ?? -1) + 1;
    }

    const category = this.categoriesRepository.create({
      user_id: userId,
      name: data.name,
      sort_order: sortOrder,
    });
    await this.categoriesRepository.save(category);

    return {
      id: category.id,
      name: category.name,
      sortOrder: category.sort_order,
      lists: [],
    };
  }

  async updateCategory(
    categoryId: string,
    userId: string,
    data: { name?: string; sortOrder?: number },
  ): Promise<CategorySummary> {
    const category = await this.categoriesRepository.findOne({
      where: { id: categoryId, user_id: userId },
      relations: ['items', 'items.list'],
    });
    if (!category) {
      throw new NotFoundException('Category not found');
    }

    if (data.name !== undefined) category.name = data.name;
    if (data.sortOrder !== undefined) category.sort_order = data.sortOrder;

    await this.categoriesRepository.save(category);
    const countMap = await this.buildProblemCountMap();
    const listIds = (category.items ?? []).map((item) => item.list_id);
    const favoritesCountMap = await this.buildFavoritesCountMap(listIds);

    return {
      id: category.id,
      name: category.name,
      sortOrder: category.sort_order,
      lists: (category.items ?? [])
        .filter((item) => item.list)
        .map((item) =>
          this.mapList(
            item.list,
            countMap.get(item.list.id) ?? 0,
            favoritesCountMap.get(item.list.id) ?? 0,
            {
              isSaved: true,
              categoryId: category.id,
            },
          ),
        ),
    };
  }

  async deleteCategory(categoryId: string, userId: string): Promise<void> {
    const category = await this.categoriesRepository.findOne({
      where: { id: categoryId, user_id: userId },
    });
    if (!category) {
      throw new NotFoundException('Category not found');
    }

    // Saved lists in this category will have category_id set to NULL (via onDelete: SET NULL)
    await this.categoriesRepository.remove(category);
  }

  async moveListToCategory(
    userId: string,
    listId: string,
    categoryId: string | null,
  ): Promise<void> {
    const favorite = await this.prisma.edgeOperation.findUnique({
      where: {
        operator_id_operation_type_target_type_target_id: {
          operator_id: userId,
          operation_type: EdgeOperationType.FAVORITE,
          target_type: problemListTargetType,
          target_id: listId,
        },
      },
    });
    if (!favorite) {
      throw new NotFoundException('List is not saved');
    }

    if (categoryId) {
      const category = await this.categoriesRepository.findOne({
        where: { id: categoryId, user_id: userId },
      });
      if (!category) {
        throw new NotFoundException('Category not found');
      }
    }

    const existingItem = await this.categoryItemsRepository.findOne({
      where: { user_id: userId, list_id: listId },
    });

    if (categoryId) {
      if (existingItem) {
        existingItem.category_id = categoryId;
        await this.categoryItemsRepository.save(existingItem);
      } else {
        const item = this.categoryItemsRepository.create({
          user_id: userId,
          list_id: listId,
          category_id: categoryId,
        });
        await this.categoryItemsRepository.save(item);
      }
      return;
    }

    if (existingItem) {
      await this.categoryItemsRepository.remove(existingItem);
    }
  }
}
