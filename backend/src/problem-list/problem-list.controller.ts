import { Controller, Get } from '@nestjs/common';
import { ProblemListService } from './problem-list.service';
import { ProblemListGroup } from './problem-list-group.entity';
import { ProblemList } from './problem-list.entity';

@Controller('problem-lists')
export class ProblemListController {
  constructor(private readonly problemListService: ProblemListService) {}

  @Get()
  findAll(): Promise<ProblemListGroup[]> {
    return this.problemListService.findAll();
  }

  @Get(':id')
  getList(): Promise<ProblemList> {
    return this.problemListService.getDefaultList();
  }
}
