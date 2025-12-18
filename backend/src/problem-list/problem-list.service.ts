import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';
import problemListData from '../../prisma/seed/data/problem-lists.data';

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
  acceptance_rate: number;
  status: string;
  is_premium: boolean;
  has_solution: boolean;
  completed_time?: Date | null;
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
  ) {}

  async findAll(): Promise<ProblemListGroup[]> {
    const groups = await this.groupsRepository.find({
      relations: ['lists'],
      order: { sort_order: 'ASC' },
    });
    const relations = problemListData.problem_list_relations ?? [];
    const countMap = new Map<string, number>();
    relations.forEach((rel) => {
      countMap.set(rel.list_id, (countMap.get(rel.list_id) ?? 0) + 1);
    });

    return groups.map((group) => ({
      ...group,
      lists: (group.lists ?? []).map((list) => ({
        ...list,
        problem_count: countMap.get(list.id) ?? 0,
      })),
    }));
  }

  async getDefaultList(): Promise<ProblemList> {
    // Return the first list as a simple mock
    const list = await this.listsRepository.findOne({
      relations: ['group'],
      where: {},
      order: { created_at: 'ASC' },
    });
    if (list) {
      return list;
    }
    // fallback to first from seed data if table empty
    return null as unknown as ProblemList;
  }

  async getListById(listId: string): Promise<ProblemList | null> {
    const list = await this.listsRepository.findOne({
      where: { id: listId },
      relations: ['group'],
    });
    return list ?? null;
  }

  async getStats(): Promise<ProblemListStats[]> {
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

    const grouped = new Map<string, number[]>();
    scopedRelations.forEach((rel) => {
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
        if (problem.status === 'solved') solvedCount += 1;
        else if (problem.status === 'attempted') attemptedCount += 1;
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

  async getProblemsByListId(listId: string): Promise<ProblemListProblem[]> {
    const relations = problemListData.problem_list_relations ?? [];
    const listRelations = relations.filter((rel) => rel.list_id === listId);
    const ids = listRelations.map((rel) => rel.problem_id);
    if (ids.length === 0) {
      return [];
    }

    const problems = await this.problemsRepository.findBy({ id: In(ids) });
    const problemMap = new Map<number, Problem>();
    problems.forEach((problem) => problemMap.set(Number(problem.id), problem));

    return ids
      .map((id) => problemMap.get(id))
      .filter((problem): problem is Problem => Boolean(problem))
      .map((problem) => ({
        id: Number(problem.id),
        slug: problem.slug,
        title: problem.title,
        difficulty: problem.difficulty,
        acceptance_rate: Number(problem.acceptance_rate),
        status: problem.status,
        is_premium: problem.is_premium,
        has_solution: problem.has_solution,
        completed_time: problem.completed_time ?? null,
      }));
  }
}
