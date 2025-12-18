import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';

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
    return null as unknown as ProblemList;
  }

  async getStats(): Promise<ProblemListStats[]> {
    const lists = await this.listsRepository.find();
    return lists.map((list) => ({
      listId: list.id,
      totalCount: 0,
      solvedCount: 0,
      attemptedCount: 0,
      todoCount: 0,
      progress: 0,
    }));
  }

  getProblemsByListId(_listId: string): Promise<ProblemListProblem[]> {
    return Promise.resolve([]);
  }
}
