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
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiParam,
} from '@nestjs/swagger';
import type { Request } from 'express';
import { SubmissionService } from './submission.service';
import { ContestSubmissionService } from './contest-submission.service';
import { AuthGuard } from '../auth/auth.guard';
import { CreateSubmissionDto } from './dto/create-submission.dto';
import { RunSubmissionDto } from './dto/run-submission.dto';
import {
  GetDailyActivityQueryDto,
  FindAllSubmissionsQueryDto,
  ProblemSubmissionsQueryDto,
} from './dto/submission-query.dto';
import { Locale } from '../i18n/i18n.decorator';
import type { SupportedLocale } from '../i18n/i18n.constants';
import { ThrottleSubmission } from '../common/guards/throttle.guard';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@ApiTags('submissions')
@Controller('submissions')
@ApiBearerAuth()
export class SubmissionController {
  constructor(private readonly submissionService: SubmissionService) {}

  @Get('status/map')
  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Get problem status map',
    description: 'Get submission status for all problems',
  })
  @ApiResponse({ status: 200, description: 'Map of problem ID to status' })
  async getStatusMap(@Req() req: AuthenticatedRequest) {
    const userId = req.user.id;
    const map = await this.submissionService.getProblemStatusMap(userId);
    // Convert Map to object for JSON response
    return Object.fromEntries(map);
  }

  @Get('statuses')
  @ApiOperation({
    summary: 'Get status definitions',
    description: 'Get all submission status definitions',
  })
  @ApiResponse({ status: 200, description: 'List of status definitions' })
  async getStatuses(@Locale() locale?: string) {
    return this.submissionService.getStatusDefinitions(
      locale as SupportedLocale,
    );
  }

  @Get('calendar')
  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Get daily activity',
    description: 'Get submission activity calendar for a year',
  })
  @ApiResponse({ status: 200, description: 'Daily submission activity' })
  async getDailyActivity(
    @Query() query: GetDailyActivityQueryDto,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    const yearInt = query.year ?? new Date().getFullYear();
    return this.submissionService.getDailyActivity(userId, yearInt);
  }

  @Get('history')
  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Get submission history',
    description: 'Get user submission history',
  })
  @ApiResponse({ status: 200, description: 'Submission history' })
  async getSubmissionHistory(@Req() req: AuthenticatedRequest) {
    const userId = req.user.id;
    return this.submissionService.getSubmissionHistory(userId);
  }

  @Get('learning-progress')
  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Get learning progress',
    description: 'Get user learning progress statistics',
  })
  @ApiResponse({ status: 200, description: 'Learning progress data' })
  async getLearningProgress(@Req() req: AuthenticatedRequest) {
    const userId = req.user.id;
    return this.submissionService.getLearningProgress(userId);
  }

  @Get(':id')
  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Get submission by ID',
    description: 'Retrieve a specific submission',
  })
  @ApiParam({ name: 'id', description: 'Submission ID', type: String })
  @ApiResponse({ status: 200, description: 'Submission details' })
  @ApiResponse({ status: 404, description: 'Submission not found' })
  async findOne(@Param('id') id: string, @Req() req: AuthenticatedRequest) {
    return this.submissionService.findOne(id, req.user.id);
  }

  @Get()
  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Get user submissions',
    description: 'Retrieve paginated submissions for the authenticated user',
  })
  @ApiResponse({ status: 200, description: 'List of submissions' })
  async findAllByUser(
    @Query() query: FindAllSubmissionsQueryDto,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    if (query.best === 'true' && query.problemId) {
      return this.submissionService.findBest(query.problemId, userId);
    }
    return this.submissionService.findAll(
      query.problemId ?? null,
      userId,
      query.skip ?? 0,
      query.take ?? 10,
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
    @Query() query: ProblemSubmissionsQueryDto,
    @Req() req: AuthenticatedRequest,
  ) {
    const userId = req.user.id;
    return this.submissionService.findAll(
      problemId,
      userId,
      query.skip ?? 0,
      query.take ?? 10,
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
  @ThrottleSubmission()
  async run(
    @Param('problemId', ParseIntPipe) problemId: number,
    @Body() dto: RunSubmissionDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.submissionService.run(problemId, dto, req.user.id);
  }

  @Post()
  @UseGuards(AuthGuard)
  @ThrottleSubmission()
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
  @ThrottleSubmission()
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
