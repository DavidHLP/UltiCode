import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';

import { SolutionService } from './solution.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import { VoteService } from '../vote/vote.service';
import { VoteTargetType } from '@prisma/client';
import { AuthGuard } from '../auth/auth.guard';
import type { Request } from 'express';

interface AuthenticatedRequest extends Request {
  user?: { id: string };
}

@Controller('solutions')
export class GlobalSolutionController {
  constructor(
    private readonly solutionService: SolutionService,
    private readonly voteService: VoteService,
  ) {}

  @Get()
  findAllByUser(
    @Query('userId') userId: string,
    @Req() req?: AuthenticatedRequest,
  ): Promise<SolutionFeedResponse> {
    const effectiveUserId = userId || req?.user?.id;
    if (!effectiveUserId) {
      throw new BadRequestException('userId is required');
    }
    return this.solutionService.findAllByUser(effectiveUserId);
  }

  @Get(':id/comments')
  findComments(@Param('id') id: string, @Query('userId') userId?: string) {
    return this.solutionService.findComments(id, userId);
  }

  @Post(':id/comments')
  @UseGuards(AuthGuard)
  createComment(
    @Param('id') id: string,
    @Body() dto: CreateSolutionCommentDto,
    @Req() req: AuthenticatedRequest,
  ) {
    const user = req.user;
    if (!user) {
      throw new BadRequestException('user not found');
    }
    return this.solutionService.createComment(id, dto, user.id);
  }

  @Post(':id/vote')
  @UseGuards(AuthGuard)
  voteSolution(
    @Param('id') id: string,
    @Body('voteType') voteType: number,
    @Req() req: AuthenticatedRequest,
  ) {
    const user = req.user;
    if (!user) {
      throw new BadRequestException('user not found');
    }
    return this.voteService.vote(user.id, {
      targetType: VoteTargetType.SOLUTION,
      targetId: id,
      voteType,
    });
  }

  @Post('comments/:id/vote')
  @UseGuards(AuthGuard)
  voteSolutionComment(
    @Param('id') id: string,
    @Body('voteType') voteType: number,
    @Req() req: AuthenticatedRequest,
  ) {
    const user = req.user;
    if (!user) {
      throw new BadRequestException('user not found');
    }
    return this.voteService.vote(user.id, {
      targetType: VoteTargetType.SOLUTION_COMMENT,
      targetId: id,
      voteType,
    });
  }
}
