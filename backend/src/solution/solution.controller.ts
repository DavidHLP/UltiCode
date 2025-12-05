import { Controller, Get, Param } from '@nestjs/common';
import { SolutionService } from './solution.service';
import { SolutionMeta } from './solution-meta.entity';

@Controller('solutions')
export class SolutionController {
  constructor(private readonly solutionService: SolutionService) {}

  @Get()
  findAll(): Promise<SolutionMeta[]> {
    return this.solutionService.findAll();
  }

  @Get(':id')
  findOne(@Param('id') id: string): Promise<SolutionMeta | null> {
    return this.solutionService.findOne(id);
  }
}
