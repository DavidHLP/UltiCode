import { Controller, Get, Param } from '@nestjs/common';
import { SolutionService } from './solution.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';

@Controller('problems')
export class SolutionController {
  constructor(private readonly solutionService: SolutionService) {}

  @Get(':id/solutions')
  findSolutions(@Param('id') id: string): Promise<SolutionFeedResponse> {
    return this.solutionService.findByProblemId(id);
  }
}
