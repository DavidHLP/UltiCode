import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Problem } from './problem.entity';
import { ProblemDetail } from './problem-detail.entity';

@Injectable()
export class ProblemService {
  constructor(
    @InjectRepository(Problem)
    private problemsRepository: Repository<Problem>,
    @InjectRepository(ProblemDetail)
    private problemDetailsRepository: Repository<ProblemDetail>,
  ) {}

  async findAll(): Promise<Problem[]> {
    return this.problemsRepository.find();
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
