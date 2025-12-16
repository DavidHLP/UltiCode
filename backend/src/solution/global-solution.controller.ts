import { Controller, Get, Query, Post, Body, Param } from '@nestjs/common';
import { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';

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

  @Get(':id/comments')
  findComments(@Param('id') id: string) {
    return this.solutionService.findComments(id);
  }

  @Post(':id/comments')
  createComment(
    @Param('id') id: string,
    @Body() dto: CreateSolutionCommentDto,
  ) {
    return this.solutionService.createComment(id, dto);
  }
}
