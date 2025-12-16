import { Controller, Get, Param, Query } from '@nestjs/common';
import { SolutionService } from './solution.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';

@Controller('problems')
export class SolutionController {
  constructor(private readonly solutionService: SolutionService) {}

  @Get(':id/solutions')
  @Get(':id/solutions')
  findSolutions(
    @Param('id') id: string,
    @Query('userId') userId?: string,
  ): Promise<SolutionFeedResponse> {
    return this.solutionService.findByProblemId(id, userId);
  }
}
