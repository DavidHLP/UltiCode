import { Controller, Get, Param, Query, ParseIntPipe } from '@nestjs/common';
import { SubmissionService } from './submission.service';

@Controller('submissions')
export class SubmissionController {
  constructor(private readonly submissionService: SubmissionService) {}

  @Get(':id')
  async findOne(@Param('id') id: string) {
    return this.submissionService.findOne(id);
  }
}

@Controller('problems/:problemId/submissions')
export class ProblemSubmissionController {
  constructor(private readonly submissionService: SubmissionService) {}

  @Get()
  async findAll(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Query('skip') skip?: string,
    @Query('take') take?: string,
  ) {
    return this.submissionService.findAll(
      problemId,
      skip ? parseInt(skip) : 0,
      take ? parseInt(take) : 10,
    );
  }
}
