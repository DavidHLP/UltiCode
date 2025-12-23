import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository, DataSource } from 'typeorm';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';
import { SubmissionService } from '../submission/submission.service';
import { ProblemListProblemRelation } from './problem-list-problem-relation.entity';
import { v4 as uuidv4 } from 'uuid';

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
    @InjectRepository(ProblemListProblemRelation)
    private relationsRepository: Repository<ProblemListProblemRelation>,
    private submissionService: SubmissionService,
    private dataSource: DataSource,
  ) {}

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
    const count = await this.relationsRepository.count({
      where: { list_id: listId },
    });
    return this.mapList(list, count);
  }

  async getStats(userId?: string): Promise<ProblemListStats[]> {
    const lists = await this.listsRepository.find();

    // Fetch all relations
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

    // Extract unique problem IDs
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

  // --- New Methods ---

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
      group_id: 'group-created', // Default group for created lists
      name: `${originalList.name} (Copy)`,
      description: originalList.description,
      author_id: userId,
      is_public: false, // Private by default
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

    // Relations will be deleted by CASCADE
    await this.listsRepository.remove(list);
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
    return this.mapList(list, count);
  }

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
    if (exists) return; // Already added

    const count = await this.relationsRepository.count({
      where: { list_id: listId },
    });

    const relation = this.relationsRepository.create({
      list_id: listId,
      problem_id: problemId,
      sort_order: count, // Append to end
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
}
