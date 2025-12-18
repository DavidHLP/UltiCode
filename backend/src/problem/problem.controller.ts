import { Controller, Get, Param, Query } from '@nestjs/common';
import { ProblemService } from './problem.service';
import { Problem } from './problem.entity';
import { SubmissionService } from '../submission/submission.service';

@Controller('problems')
export class ProblemController {
  constructor(
    private readonly problemService: ProblemService,
    private readonly submissionService: SubmissionService,
  ) {}

  @Get()
  findAll(): Promise<Problem[]> {
    return this.problemService.findAll();
  }

  @Get('random')
  getRandom(): Promise<Problem | null> {
    return this.problemService.getRandom();
  }

  @Get(':id')
  findOne(@Param('id') id: string): Promise<Problem | null> {
    return this.problemService.findOne(id);
  }

  @Get(':id/results')
  getProblemResults(@Param('id') id: string, @Query('userId') userId?: string) {
    const numericId = Number(id);
    if (Number.isNaN(numericId)) {
      return null;
    }
    return this.submissionService.getLatestRunResult(numericId, userId);
  }
}
