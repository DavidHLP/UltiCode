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
      relations: ['detail', 'tagRelations', 'tagRelations.tag'],
    });
  }
}
