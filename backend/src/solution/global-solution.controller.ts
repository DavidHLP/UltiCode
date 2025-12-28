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
  Delete,
  Patch,
} from '@nestjs/common';
import { CreateSolutionCommentDto } from './dto/create-solution-comment.dto';
import { CreateSolutionDto } from './dto/create-solution.dto';

import { SolutionService } from './solution.service';
import type { SolutionFeedResponse } from './dto/solution-feed.dto';
import { VoteService } from '../vote/vote.service';
import { EdgeOperationTargetType } from '@prisma/client';
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
    @Query('problemId') problemId?: string,
    @Req() req?: AuthenticatedRequest,
  ): Promise<SolutionFeedResponse> {
    const effectiveUserId = userId || req?.user?.id;
    if (!effectiveUserId) {
      throw new BadRequestException('userId is required');
    }
    return this.solutionService.findAllByUser(effectiveUserId, problemId);
  }

  @Get(':id')
  findOne(@Param('id') id: string): Promise<any> {
    return this.solutionService.findOne(id);
  }

  @Delete(':id')
  @UseGuards(AuthGuard)
  delete(
    @Param('id') id: string,
    @Req() req: AuthenticatedRequest,
  ): Promise<any> {
    const user = req.user;
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.solutionService.delete(id, user.id);
  }

  @Patch(':id')
  @UseGuards(AuthGuard)
  update(
    @Param('id') id: string,
    @Body() dto: CreateSolutionDto,
    @Req() req: AuthenticatedRequest,
  ): Promise<any> {
    const user = req.user;
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.solutionService.update(id, user.id, dto);
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
      targetType: EdgeOperationTargetType.SOLUTION,
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
      targetType: EdgeOperationTargetType.SOLUTION_COMMENT,
      targetId: id,
      voteType,
    });
  }

  @Patch('comments/:id')
  @UseGuards(AuthGuard)
  updateComment(
    @Param('id') id: string,
    @Body() dto: CreateSolutionCommentDto,
    @Req() req: AuthenticatedRequest,
  ) {
    const user = req.user;
    if (!user) {
      throw new BadRequestException('user not found');
    }
    return this.solutionService.updateComment(id, dto.content, user.id);
  }

  @Delete('comments/:id')
  @UseGuards(AuthGuard)
  deleteComment(@Param('id') id: string, @Req() req: AuthenticatedRequest) {
    const user = req.user;
    if (!user) {
      throw new BadRequestException('user not found');
    }
    return this.solutionService.deleteComment(id, user.id);
  }
}
