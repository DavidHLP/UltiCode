import { Controller, Get, Param } from '@nestjs/common';
import { ProblemService } from './problem.service';
import { Problem } from './problem.entity';
import { SolutionService } from '../solution/solution.service';
import { SolutionMeta } from '../solution/solution-meta.entity';

@Controller('problems')
export class ProblemController {
  constructor(
    private readonly problemService: ProblemService,
    private readonly solutionService: SolutionService,
  ) {}

  @Get()
  findAll(): Promise<Problem[]> {
    return this.problemService.findAll();
  }

  @Get(':id')
  findOne(@Param('id') id: string): Promise<Problem | null> {
    return this.problemService.findOne(id);
  }

  @Get(':id/solutions')
  findSolutions(@Param('id') id: string): Promise<SolutionMeta[]> {
    return this.solutionService.findByProblemId(id);
  }
}
