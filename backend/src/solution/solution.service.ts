import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { SolutionMeta } from './solution-meta.entity';

@Injectable()
export class SolutionService {
  constructor(
    @InjectRepository(SolutionMeta)
    private solutionsRepository: Repository<SolutionMeta>,
  ) {}

  async findAll(): Promise<SolutionMeta[]> {
    return this.solutionsRepository.find({
      relations: ['author', 'badges'],
    });
  }

  async findOne(id: string): Promise<SolutionMeta | null> {
    return this.solutionsRepository.findOne({
      where: { id },
      relations: ['author', 'badges'],
    });
  }

  async findByProblemId(problemId: string): Promise<SolutionMeta[]> {
    // TODO: Filter by problemId when relation is established
    // currently returning empty list as per plan
    return [];
  }
}
