import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Contest } from './contest.entity';
import { GlobalRanking } from './global-ranking.entity';

export interface ContestStats {
  total_participants: number;
  total_contests: number;
}

@Injectable()
export class ContestService {
  constructor(
    @InjectRepository(Contest)
    private contestsRepository: Repository<Contest>,
    @InjectRepository(GlobalRanking)
    private globalRankingRepository: Repository<GlobalRanking>,
  ) {}

  async findAll(): Promise<Contest[]> {
    return this.contestsRepository.find();
  }

  async findOne(id: string): Promise<Contest | null> {
    return this.contestsRepository.findOne({
      where: { id },
      relations: ['problems'],
    });
  }

  async findUpcoming(): Promise<Contest[]> {
    return this.contestsRepository.find({
      where: { status: 'upcoming' },
      order: { start_time: 'ASC' },
    });
  }

  async findRunning(): Promise<Contest[]> {
    return this.contestsRepository.find({
      where: { status: 'running' },
      order: { start_time: 'ASC' },
    });
  }

  async findPast(): Promise<Contest[]> {
    return this.contestsRepository.find({
      where: { status: 'finished' },
      order: { start_time: 'DESC' },
    });
  }

  async getGlobalRanking(): Promise<GlobalRanking[]> {
    return this.globalRankingRepository.find({
      order: { global_rank: 'ASC' },
      take: 10,
    });
  }

  getStats(): Promise<ContestStats> {
    // Mock stats for now or calculate from DB
    return Promise.resolve({
      total_participants: 12500,
      total_contests: 50,
    });
  }
}
