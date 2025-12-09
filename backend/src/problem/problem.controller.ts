import { Controller, Get, Param } from '@nestjs/common';
import { ProblemService } from './problem.service';
import { Problem } from './problem.entity';

@Controller('problems')
export class ProblemController {
  constructor(private readonly problemService: ProblemService) {}

  @Get()
  findAll(): Promise<Problem[]> {
    return this.problemService.findAll();
  }

  @Get(':id')
  findOne(@Param('id') id: string): Promise<Problem | null> {
    return this.problemService.findOne(id);
  }
}
