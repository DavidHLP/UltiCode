import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Problem } from './problem.entity';
import { ProblemDetail } from './problem-detail.entity';
import { CATEGORY_TAG_MAP } from './constants';

@Injectable()
export class ProblemService {
  constructor(
    @InjectRepository(Problem)
    private problemsRepository: Repository<Problem>,
    @InjectRepository(ProblemDetail)
    private problemDetailsRepository: Repository<ProblemDetail>,
  ) {}

  async findAll(
    filters: {
      category?: string;
      difficulty?: string;
      search?: string;
    } = {},
  ): Promise<Problem[]> {
    const query = this.problemsRepository
      .createQueryBuilder('problem')
      .leftJoinAndSelect('problem.tagRelations', 'tagRelations')
      .leftJoinAndSelect('tagRelations.tag', 'tag');

    if (filters.difficulty) {
      query.andWhere('problem.difficulty = :difficulty', {
        difficulty: filters.difficulty,
      });
    }

    if (filters.search) {
      query.andWhere(
        '(LOWER(problem.title) LIKE LOWER(:search) OR problem.id::text LIKE :search)',
        { search: `%${filters.search}%` },
      );
    }

    if (filters.category && filters.category !== 'all') {
      // Map frontend category to tag labels
      const tagLabel = CATEGORY_TAG_MAP[filters.category];
      if (tagLabel) {
        query.andWhere((qb) => {
          const subQuery = qb
            .subQuery()
            .select('relation.problem_id')
            .from('problem_tag_relations', 'relation')
            .leftJoin('relation.tag', 't')
            .where('t.label = :tagLabel')
            .getQuery();
          return `problem.id IN ${subQuery}`;
        });
        query.setParameter('tagLabel', tagLabel);
      }
    }

    return query.getMany();
  }

  async findOne(idOrSlug: string | number): Promise<Problem | null> {
    if (typeof idOrSlug === 'number' || !isNaN(Number(idOrSlug))) {
      return this.problemsRepository.findOne({
        where: { id: Number(idOrSlug) },
        relations: [
          'detail',
          'tagRelations',
          'tagRelations.tag',
          'languages',
          'examples',
        ],
      });
    }
    return this.problemsRepository.findOne({
      where: { slug: idOrSlug },
      relations: [
        'detail',
        'tagRelations',
        'tagRelations.tag',
        'languages',
        'examples',
      ],
    });
  }

  async getRandom(): Promise<Problem | null> {
    const count = await this.problemsRepository.count();
    if (count === 0) {
      return null;
    }
    const randomIndex = Math.floor(Math.random() * count);
    const problems = await this.problemsRepository.find({
      skip: randomIndex,
      take: 1,
    });
    return problems[0] || null;
  }
  async findAdjacent(
    id: number,
  ): Promise<{ prev: string | null; next: string | null }> {
    const prev = await this.problemsRepository.findOne({
      where: { id: id - 1 },
      select: ['slug'],
    });
    const next = await this.problemsRepository.findOne({
      where: { id: id + 1 },
      select: ['slug'],
    });

    return {
      prev: prev?.slug || null,
      next: next?.slug || null,
    };
  }
}
