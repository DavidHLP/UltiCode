import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { SubmissionService } from '../../submission/submission.service';
import { I18nService } from '../../i18n/i18n.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  TRANSLATABLE_ENTITIES,
} from '../../i18n/i18n.constants';
import { ProblemListSummary, ProblemListProblem, PrismaClient } from '../types';
import { ProblemListStatsService } from './problem-list-stats.service';

@Injectable()
export class ProblemListRelationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly submissionService: SubmissionService,
    private readonly i18nService: I18nService,
    private readonly statsService: ProblemListStatsService,
  ) {}

  /**
   * Add a problem to a list
   */
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

  /**
   * Remove a problem from a list
   */
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

  /**
   * Batch add a problem to multiple lists
   */
  async batchAddProblemToLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
    const problem = await this.prisma.problem.findUnique({
      where: { id: BigInt(problemId) },
    });
    if (!problem) {
      throw new NotFoundException('Problem not found');
    }

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

    await this.prisma.$transaction(async (tx) => {
      for (const listId of listIds) {
        const exists = await tx.problemListProblemRelation.findUnique({
          where: {
            list_id_problem_id: {
              list_id: listId,
              problem_id: BigInt(problemId),
            },
          },
        });
        if (exists) continue;

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

  /**
   * Batch remove a problem from multiple lists
   */
  async batchRemoveProblemFromLists(
    userId: string,
    problemId: number,
    listIds: string[],
  ): Promise<void> {
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

    await this.prisma.problemListProblemRelation.deleteMany({
      where: {
        list_id: { in: listIds },
        problem_id: BigInt(problemId),
      },
    });
  }

  /**
   * Get all user lists that contain a specific problem
   */
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
    const myLists = await this.prisma.problemList.findMany({
      where: { author_id: userId },
      orderBy: { updated_at: 'desc' },
    });

    const countMap = await this.statsService.buildProblemCountMap();
    const favoritesCountMap = await this.statsService.buildFavoritesCountMap(
      myLists.map((l) => l.id),
    );

    const relations = await this.prisma.problemListProblemRelation.findMany({
      where: {
        list_id: { in: myLists.map((l) => l.id) },
        problem_id: BigInt(problemId),
      },
    });

    const listsWithProblem = new Set(relations.map((r) => r.list_id));

    return myLists.map((list) => ({
      ...this.statsService.mapList(
        list,
        countMap.get(list.id) ?? 0,
        favoritesCountMap.get(list.id) ?? 0,
      ),
      containsProblem: listsWithProblem.has(list.id),
      canEdit: true,
    }));
  }

  /**
   * Get all list IDs that contain a specific problem
   */
  async getProblemListIds(problemId: number): Promise<string[]> {
    const relations = await this.prisma.problemListProblemRelation.findMany({
      where: { problem_id: BigInt(problemId) },
      select: { list_id: true },
    });
    return relations.map((r) => r.list_id);
  }

  /**
   * Get all problems in a list
   */
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

  /**
   * Add problems to a list within a transaction
   */
  async addProblemsToListInTransaction(
    listId: string,
    problemIds: number[],
    tx: PrismaClient,
  ): Promise<void> {
    const count = await tx.problemListProblemRelation.count({
      where: { list_id: listId },
    });

    await tx.problemListProblemRelation.createMany({
      data: problemIds.map((problemId, index) => ({
        list_id: listId,
        problem_id: BigInt(problemId),
        sort_order: count + index,
      })),
    });
  }
}
