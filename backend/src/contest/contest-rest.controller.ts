import {
  Controller,
  Get,
  Query,
  UseFilters,
  UseInterceptors,
} from '@nestjs/common';
import { ContestService } from './contest.service';
import { ResponseInterceptor } from '../common/interceptors/response.interceptor';
import { GlobalExceptionFilter } from '../common/filters/global-exception.filter';

@UseInterceptors(ResponseInterceptor)
@UseFilters(GlobalExceptionFilter)
@Controller()
export class ContestRestController {
  constructor(private readonly contestService: ContestService) {}

  @Get('contests')
  async findContests(
    @Query('status') status?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string,
  ) {
    const normalized = status?.toLowerCase();
    if (!normalized) {
      const items = await this.contestService.findAll();
      return { items, total: items.length };
    }

    if (normalized === 'upcoming') {
      const items = await this.contestService.findUpcoming();
      return { items, total: items.length };
    }

    if (normalized === 'running') {
      const items = await this.contestService.findRunning();
      return { items, total: items.length };
    }

    if (normalized === 'finished' || normalized === 'past') {
      const result = await this.contestService.findPast(
        page ? Number(page) : 1,
        limit ? Number(limit) : 10,
      );
      return { items: result.data, total: result.total };
    }

    const items = await this.contestService.findAll();
    return { items, total: items.length };
  }

  @Get('rankings')
  async findRankings(
    @Query('scope') scope?: string,
    @Query('contestId') contestId?: string,
  ) {
    const normalized = scope?.toLowerCase();
    if (normalized === 'contest' && contestId) {
      return this.contestService.getContestRanking(contestId);
    }
    return this.contestService.getGlobalRanking();
  }
}
