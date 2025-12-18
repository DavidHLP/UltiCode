import { Controller, Get, Query, Post, Body, Param } from '@nestjs/common';
import { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';

import { SolutionService } from './solution.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import { VoteService } from '../vote/vote.service';
import { VoteTargetType } from '@prisma/client';

@Controller('solutions')
export class GlobalSolutionController {
  constructor(
    private readonly solutionService: SolutionService,
    private readonly voteService: VoteService,
  ) {}

  @Get()
  findAllByUser(
    @Query('userId') userId: string,
  ): Promise<SolutionFeedResponse> {
    return this.solutionService.findAllByUser(userId);
  }

  @Get(':id/comments')
  findComments(@Param('id') id: string, @Query('userId') userId?: string) {
    return this.solutionService.findComments(id, userId);
  }

  @Post(':id/comments')
  createComment(
    @Param('id') id: string,
    @Body() dto: CreateSolutionCommentDto,
  ) {
    return this.solutionService.createComment(id, dto);
  }

  @Post(':id/vote')
  voteSolution(
    @Param('id') id: string,
    @Body('userId') userId: string,
    @Body('voteType') voteType: number,
  ) {
    const effectiveUserId = userId || 'u-001';
    return this.voteService.vote(effectiveUserId, {
      targetType: VoteTargetType.SOLUTION,
      targetId: id,
      voteType,
    });
  }

  @Post('comments/:id/vote')
  voteSolutionComment(
    @Param('id') id: string,
    @Body('userId') userId: string,
    @Body('voteType') voteType: number,
  ) {
    const effectiveUserId = userId || 'u-001';
    return this.voteService.vote(effectiveUserId, {
      targetType: VoteTargetType.SOLUTION_COMMENT,
      targetId: id,
      voteType,
    });
  }
}
