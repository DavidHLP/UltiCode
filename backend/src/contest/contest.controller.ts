import { Controller, Get, Param, Query } from '@nestjs/common';
import { ContestService } from './contest.service';
import { Contest } from './contest.entity';
import { GlobalRanking } from './global-ranking.entity';
import { ContestRanking } from './contest-ranking.entity';
import type { ContestStats } from './contest.service';

@Controller('contest')
export class ContestController {
  constructor(private readonly contestService: ContestService) {}

  @Get('list')
  findAll(): Promise<Contest[]> {
    return this.contestService.findAll();
  }

  @Get('upcoming')
  findUpcoming(): Promise<Contest[]> {
    return this.contestService.findUpcoming();
  }

  @Get('running')
  findRunning(): Promise<Contest[]> {
    return this.contestService.findRunning();
  }

  @Get('past')
  findPast(
    @Query('page') page: number = 1,
    @Query('limit') limit: number = 10,
  ): Promise<{ data: Contest[]; total: number }> {
    return this.contestService.findPast(Number(page), Number(limit));
  }

  @Get('global-ranking')
  getGlobalRanking(): Promise<GlobalRanking[]> {
    return this.contestService.getGlobalRanking();
  }

  @Get('stats')
  getStats(): Promise<ContestStats> {
    return this.contestService.getStats();
  }

  @Get(':id/ranking')
  getContestRanking(@Param('id') id: string): Promise<ContestRanking[]> {
    return this.contestService.getContestRanking(id);
  }

  @Get(':id')
  findOne(@Param('id') id: string): Promise<Contest | null> {
    return this.contestService.findOne(id);
  }
}
