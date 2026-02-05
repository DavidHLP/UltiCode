import { Injectable } from '@nestjs/common';
import { Prisma, BookmarkType } from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import { SubmissionService } from '../../submission/submission.service';
import { ProblemListSummary, ProblemListStats } from '../types';

const problemListTargetType = BookmarkType.PROBLEM_LIST;

@Injectable()
export class ProblemListStatsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly submissionService: SubmissionService,
  ) {}

  /**
   * Build a map of problem list ID to problem count
   */
  async buildProblemCountMap(): Promise<Map<string, number>> {
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

  /**
   * Build a map of problem list ID to favorites count
   */
  async buildFavoritesCountMap(
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

  /**
   * Map a Prisma ProblemList to ProblemListSummary with counts
   */
  mapList(
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

  /**
   * Build statistics from a list of problems
   */
  buildStatsFromProblems(
    listId: string,
    problems: Array<{
      status: string;
    }>,
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

  /**
   * Get statistics for all problem lists
   */
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

  /**
   * Enrich a list with problem and favorites counts
   */
  async enrichListWithCounts(
    list: Prisma.ProblemListGetPayload<Record<string, never>>,
    options?: {
      isSaved?: boolean;
      categoryId?: string;
      includeFavorites?: boolean;
    },
  ): Promise<ProblemListSummary> {
    const problemCount = await this.prisma.problemListProblemRelation.count({
      where: { list_id: list.id },
    });

    let favoritesCount = 0;
    if (options?.includeFavorites !== false) {
      const favoritesCountMap = await this.buildFavoritesCountMap([list.id]);
      favoritesCount = favoritesCountMap.get(list.id) ?? 0;
    }

    return this.mapList(list, problemCount, favoritesCount, options);
  }
}
