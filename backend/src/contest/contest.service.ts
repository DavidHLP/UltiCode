import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Contest } from './contest.entity';
import { GlobalRanking } from './global-ranking.entity';
import { ContestRanking } from './contest-ranking.entity';
import { User } from '../user/user.entity';

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
    @InjectRepository(ContestRanking)
    private contestRankingRepository: Repository<ContestRanking>,
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

  async findPast(
    page: number = 1,
    limit: number = 10,
  ): Promise<{ data: Contest[]; total: number }> {
    const [data, total] = await this.contestsRepository.findAndCount({
      where: { status: 'finished' },
      order: { start_time: 'DESC' },
      skip: (page - 1) * limit,
      take: limit,
    });

    return { data, total };
  }

  async getGlobalRanking(): Promise<GlobalRanking[]> {
    return this.globalRankingRepository.find({
      order: { global_rank: 'ASC' },
      take: 10,
    });
  }

  async getContestRanking(contestId: string): Promise<ContestRanking[]> {
    const rankings = await this.contestRankingRepository
      .createQueryBuilder('ranking')
      .leftJoinAndMapOne(
        'ranking.user',
        User,
        'user',
        'user.id = ranking.user_id',
      )
      .where('ranking.contest_id = :contestId', { contestId })
      .orderBy('ranking.rank', 'ASC')
      .take(50)
      .getMany();

    return rankings.map((r) => {
      const ranking = r as ContestRanking & { user?: User; avatar?: string };
      return {
        ...ranking,
        avatar: ranking.user?.avatar,
      };
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
