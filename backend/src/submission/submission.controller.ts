/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import {
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
import { ContestSubmissionService } from './contest-submission.service';
import { AuthGuard } from '../auth/auth.guard';
import { CreateSubmissionDto } from './dto/create-submission.dto';
import { RunSubmissionDto } from './dto/run-submission.dto';
import { Locale } from '../i18n/i18n.decorator';
import type { SupportedLocale } from '../i18n/i18n.constants';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('submissions')
export class SubmissionController {
  constructor(private readonly submissionService: SubmissionService) {}

  @Get('status/map')
  @UseGuards(AuthGuard)
  async getStatusMap(@Req() req: AuthenticatedRequest) {
    const userId = req.user.id;
    const map = await this.submissionService.getProblemStatusMap(userId);
    // Convert Map to object for JSON response
    return Object.fromEntries(map);
  }

  @Get('statuses')
  async getStatuses(@Locale() locale?: string) {
    return this.submissionService.getStatusDefinitions(
      locale as SupportedLocale,
    );
  }

  @Get('calendar')
  @UseGuards(AuthGuard)
  async getDailyActivity(
    @Query('year') year: string,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    const yearInt = year ? parseInt(year) : new Date().getFullYear();
    return this.submissionService.getDailyActivity(userId, yearInt);
  }

  @Get(':id')
  @UseGuards(AuthGuard)
  async findOne(@Param('id') id: string, @Req() req: AuthenticatedRequest) {
    return this.submissionService.findOne(id, req.user.id);
  }

  @Get()
  @UseGuards(AuthGuard)
  async findAllByUser(
    @Query('problemId') problemId?: string,
    @Query('best') best?: string,
    @Query('skip') skip?: string,
    @Query('take') take?: string,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    if (best === 'true' && problemId) {
      return this.submissionService.findBest(parseInt(problemId), userId);
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
  @UseGuards(AuthGuard)
  async findAll(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Query('skip') skip?: string,
    @Query('take') take?: string,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    return this.submissionService.findAll(
      problemId,
      userId,
      skip ? parseInt(skip) : 0,
      take ? parseInt(take) : 10,
    );
  }

  @Get('best')
  @UseGuards(AuthGuard)
  async findBest(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    return this.submissionService.findBest(problemId, userId);
  }

  @Post('run')
  @UseGuards(AuthGuard)
  async run(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: RunSubmissionDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.submissionService.run(problemId, dto, req.user.id);
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

/**
 * Contest-specific submission endpoints
 */
@Controller('contest/:contestId/problems/:problemId/submissions')
export class ContestSubmissionController {
  constructor(
    private readonly contestSubmissionService: ContestSubmissionService,
  ) {}

  @Get()
  @UseGuards(AuthGuard)
  async getContestSubmissions(
    @Param('contestId') contestId: string,
    @Param('problemId', ParseIntPipe) problemId: number,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    return this.contestSubmissionService.getContestSubmissions(
      contestId,
      userId,
      problemId,
    );
  }

  @Post()
  @UseGuards(AuthGuard)
  async submitInContest(
    @Param('contestId') contestId: string,
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: CreateSubmissionDto,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    return this.contestSubmissionService.submitInContest(
      contestId,
      problemId,
      userId,
      dto,
    );
  }
}
