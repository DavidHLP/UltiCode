/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import {
  BadRequestException,
  Controller,
  Get,
  Param,
  ParseIntPipe,
  Post,
  Body,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { SubmissionService } from './submission.service';
import { AuthGuard } from '../auth/auth.guard';
import { CreateSubmissionDto } from './dto/create-submission.dto';
import { RunSubmissionDto } from './dto/run-submission.dto';

interface AuthenticatedRequest extends Request {
  user?: { id: string };
}

@Controller('submissions')
export class SubmissionController {
  constructor(private readonly submissionService: SubmissionService) {}

  @Get('status/map')
  async getStatusMap(
    @Query('userId') userId: string,
    @Req() req?: AuthenticatedRequest,
  ) {
    const effectiveUserId = userId || req?.user?.id;
    if (!effectiveUserId) {
      throw new BadRequestException('userId is required');
    }
    const map =
      await this.submissionService.getProblemStatusMap(effectiveUserId);
    // Convert Map to object for JSON response
    return Object.fromEntries(map);
  }

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
    @Req() req?: AuthenticatedRequest,
  ) {
    const effectiveUserId = userId || req?.user?.id;
    if (best === 'true' && problemId) {
      if (!effectiveUserId) {
        throw new BadRequestException('userId is required for best submission');
      }
      return this.submissionService.findBest(
        parseInt(problemId),
        effectiveUserId,
      );
    }
    return this.submissionService.findAll(
      problemId ? parseInt(problemId) : null,
      effectiveUserId,
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
    @Req() req?: AuthenticatedRequest,
  ) {
    const uid = userId || req?.user?.id;
    return this.submissionService.findAll(
      problemId,
      uid,
      skip ? parseInt(skip) : 0,
      take ? parseInt(take) : 10,
    );
  }

  @Get('best')
  @UseGuards(AuthGuard)
  async findBest(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Query('userId') userId?: string,
    @Req() req?: AuthenticatedRequest,
  ) {
    const uid = userId || req?.user?.id;
    if (!uid) {
      throw new BadRequestException('userId is required for best submission');
    }
    return this.submissionService.findBest(problemId, uid);
  }

  @Post('run')
  async run(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: RunSubmissionDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.submissionService.run(problemId, dto, req?.user?.id);
  }

  @Post()
  @UseGuards(AuthGuard)
  async create(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: CreateSubmissionDto,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    return this.submissionService.create(userId, problemId, dto);
  }
}
