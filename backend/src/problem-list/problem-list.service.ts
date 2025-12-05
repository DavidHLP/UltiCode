import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';
import problemsData from '../seed-data/problems';
import problemListsData from '../seed-data/problem-lists';

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
  ) {}

  async findAll(): Promise<ProblemListGroup[]> {
    return this.groupsRepository.find({
      relations: ['lists'],
      order: { sort_order: 'ASC' },
    });
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
    const fallback = problemListsData.problem_lists[0];
    return {
      ...fallback,
      created_at: new Date(fallback.created_at),
      updated_at: new Date(fallback.updated_at),
      group: null,
    } as unknown as ProblemList;
  }

  getDefaultProblems(): ProblemListProblem[] {
    return problemsData.problems.map((p) => ({
      ...p,
      completed_time:
        'completed_time' in p && p.completed_time
          ? new Date(p.completed_time as string)
          : null,
    }));
  }

  getStats(): ProblemListStats[] {
    const totalProblems = problemsData.problems.length || 1;
    return problemListsData.problem_lists.map((list) => {
      const solved = Math.floor(totalProblems * 0.3);
      const attempted = Math.floor(totalProblems * 0.2);
      const todo = totalProblems - solved - attempted;
      const progress = Math.round((solved / totalProblems) * 100);
      return {
        listId: list.id,
        totalCount: totalProblems,
        solvedCount: solved,
        attemptedCount: attempted,
        todoCount: todo,
        progress,
      };
    });
  }
}
