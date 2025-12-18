/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { Controller, Get, Param, Query, ParseIntPipe } from '@nestjs/common';
import { SubmissionService } from './submission.service';

@Controller('submissions')
export class SubmissionController {
  constructor(private readonly submissionService: SubmissionService) {}

  @Get(':id')
  async findOne(@Param('id') id: string) {
    return this.submissionService.findOne(id);
  }

  @Get()
  async findAllByUser(
    @Query('userId') userId: string,
    @Query('problemId') problemId?: string,
    @Query('best') best?: string,
    @Query('skip') skip?: string,
    @Query('take') take?: string,
  ) {
    if (best === 'true' && problemId) {
      const uid = userId || 'user-1';
      return this.submissionService.findBest(parseInt(problemId), uid);
    }
    return this.submissionService.findAll(
      problemId ? parseInt(problemId) : null,
      userId,
      skip ? parseInt(skip) : 0,
      take ? parseInt(take) : 10,
    );
  }
}

@Controller('problems/:problemId/submissions')
export class ProblemSubmissionController {
  constructor(private readonly submissionService: SubmissionService) {}

  @Get()
  async findAll(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Query('userId') userId?: string,
    @Query('skip') skip?: string,
    @Query('take') take?: string,
  ) {
    // Default to 'u-001' if no user ID is provided, simulating "current user"
    const uid = userId || 'u-001';
    return this.submissionService.findAll(
      problemId,
      uid,
      skip ? parseInt(skip) : 0,
      take ? parseInt(take) : 10,
    );
  }

  @Get('best')
  async findBest(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Query('userId') userId?: string,
  ) {
    // TODO: In a real app, userId should come from the request user (guard)
    // For now we allow passing it or default to a test user
    const uid = userId || 'user-1';
    return this.submissionService.findBest(problemId, uid);
  }
}
