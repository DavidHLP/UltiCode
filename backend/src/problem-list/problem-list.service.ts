import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';
import problemListData from '../../prisma/seed/data/problem-lists.data';
import { SubmissionService } from '../submission/submission.service';

export interface ProblemListSummary {
  id: string;
  groupId: string;
  name: string;
  description?: string;
  authorId: string;
  isPublic: boolean;
  createdAt: Date;
  updatedAt: Date;
  problemCount: number;
}

export interface ProblemListGroupSummary {
  id: string;
  name: string;
  sortOrder: number;
  lists: ProblemListSummary[];
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
    @InjectRepository(ProblemListGroup)
    private groupsRepository: Repository<ProblemListGroup>,
    @InjectRepository(ProblemList)
    private listsRepository: Repository<ProblemList>,
    @InjectRepository(Problem)
    private problemsRepository: Repository<Problem>,
    private submissionService: SubmissionService,
  ) {}

  private async buildProblemCountMap(): Promise<Map<string, number>> {
    const relations = problemListData.problem_list_relations ?? [];
    const problemIds = Array.from(
      new Set(relations.map((rel) => rel.problem_id)),
    );
    const problems =
      problemIds.length > 0
        ? await this.problemsRepository.findBy({ id: In(problemIds) })
        : [];
    const validProblemIds = new Set(
      problems.map((problem) => Number(problem.id)),
    );
    const countMap = new Map<string, number>();
    relations.forEach((rel) => {
      if (!validProblemIds.has(rel.problem_id)) return;
      countMap.set(rel.list_id, (countMap.get(rel.list_id) ?? 0) + 1);
    });
    return countMap;
  }

  private mapList(list: ProblemList, problemCount: number): ProblemListSummary {
    return {
      id: list.id,
      groupId: list.group_id,
      name: list.name,
      description: list.description ?? undefined,
      authorId: list.author_id,
      isPublic: list.is_public,
      createdAt: list.created_at,
      updatedAt: list.updated_at,
      problemCount,
    };
  }

  private mapGroup(
    group: ProblemListGroup,
    countMap: Map<string, number>,
  ): ProblemListGroupSummary {
    return {
      id: group.id,
      name: group.name,
      sortOrder: group.sort_order,
      lists: (group.lists ?? []).map((list) =>
        this.mapList(list, countMap.get(list.id) ?? 0),
      ),
    };
  }

  async findAll(): Promise<ProblemListGroupSummary[]> {
    const groups = await this.groupsRepository.find({
      relations: ['lists'],
      order: { sort_order: 'ASC' },
    });
    const countMap = await this.buildProblemCountMap();
    return groups.map((group) => this.mapGroup(group, countMap));
  }

  async getDefaultList(): Promise<ProblemListSummary | null> {
    const list = await this.listsRepository.findOne({
      relations: ['group'],
      where: {},
      order: { created_at: 'ASC' },
    });
    if (list) {
      const countMap = await this.buildProblemCountMap();
      return this.mapList(list, countMap.get(list.id) ?? 0);
    }
    return null;
  }

  async getListById(listId: string): Promise<ProblemListSummary | null> {
    const list = await this.listsRepository.findOne({
      where: { id: listId },
      relations: ['group'],
    });
    if (!list) return null;
    const countMap = await this.buildProblemCountMap();
    return this.mapList(list, countMap.get(list.id) ?? 0);
  }

  async getStats(userId?: string): Promise<ProblemListStats[]> {
    const lists = await this.listsRepository.find();
    const relations = problemListData.problem_list_relations ?? [];
    const listIds = new Set(lists.map((list) => list.id));
    const scopedRelations = relations.filter((rel) => listIds.has(rel.list_id));
    const problemIds = Array.from(
      new Set(scopedRelations.map((rel) => rel.problem_id)),
    );

    const problems =
      problemIds.length > 0
        ? await this.problemsRepository.findBy({ id: In(problemIds) })
        : [];
    const problemMap = new Map<number, Problem>();
    problems.forEach((problem) => problemMap.set(Number(problem.id), problem));
    const validProblemIds = new Set(problemMap.keys());
    const statusMap =
      userId && problemIds.length > 0
        ? await this.submissionService.getProblemStatusMap(userId, problemIds)
        : null;

    const grouped = new Map<string, number[]>();
    scopedRelations.forEach((rel) => {
      if (!validProblemIds.has(rel.problem_id)) return;
      const list = grouped.get(rel.list_id) ?? [];
      list.push(rel.problem_id);
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

  async getProblemsByListId(
    listId: string,
    userId?: string,
  ): Promise<ProblemListProblem[]> {
    const relations = problemListData.problem_list_relations ?? [];
    const listRelations = relations.filter((rel) => rel.list_id === listId);
    const ids = listRelations.map((rel) => rel.problem_id);
    if (ids.length === 0) {
      return [];
    }

    const problems = await this.problemsRepository.find({
      where: { id: In(ids) },
      relations: ['tagRelations', 'tagRelations.tag'],
    });
    const problemMap = new Map<number, Problem>();
    problems.forEach((problem) => problemMap.set(Number(problem.id), problem));
    const validIds = ids.filter((id) => problemMap.has(id));
    const statusMap = userId
      ? await this.submissionService.getProblemStatusMap(userId, validIds)
      : null;

    return validIds
      .map((id) => problemMap.get(id))
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
}
