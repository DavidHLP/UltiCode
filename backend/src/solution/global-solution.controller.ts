import { Controller, Get, Query } from '@nestjs/common';
import { SolutionService } from './solution.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';

@Controller('solutions')
export class GlobalSolutionController {
  constructor(private readonly solutionService: SolutionService) {}

  @Get()
  findAllByUser(
    @Query('userId') userId: string,
  ): Promise<SolutionFeedResponse> {
    return this.solutionService.findAllByUser(userId);
  }
}
