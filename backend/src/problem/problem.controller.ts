import { Controller, Get, Param, Query, Req, UseGuards } from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiParam,
} from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import type { Request } from 'express';
import { ProblemService, Problem } from './problem.service';
import { SubmissionService } from '../submission/submission.service';
import { Locale } from '../i18n/i18n.decorator';
import type { SupportedLocale } from '../i18n/i18n.constants';
import { FindAllProblemsQueryDto, ProblemParamsDto } from './dto';
import { AuthGuard } from '../auth/auth.guard';
import { Public } from '../auth/auth.decorator';
import { PaginatedResult } from '../contest/dto/ranking.dto';

interface AuthenticatedRequest extends Request {
  user?: { id: string; role?: string };
}

@ApiTags('problems')
@Controller('problems')
@UseGuards(AuthGuard)
@ApiBearerAuth()
export class ProblemController {
  constructor(
    private readonly problemService: ProblemService,
    private readonly submissionService: SubmissionService,
  ) {}

  @Get()
  @Public()
  @ApiOperation({
    summary: 'Get all problems',
    description: 'Retrieve paginated list of problems with optional filters',
  })
  @ApiResponse({ status: 200, description: 'List of problems with pagination' })
  @Throttle({ short: { limit: 100, ttl: 60000 } })
  async findAll(
    @Query() query: FindAllProblemsQueryDto,
    @Req() req?: AuthenticatedRequest,
    @Locale() locale?: string,
  ): Promise<PaginatedResult<Problem>> {
    const { userId, category, difficulty, search, page, limit } = query;
    const paginatedResult = await this.problemService.findAll(
      {
        category,
        difficulty,
        search,
        page,
        limit,
      },
      locale as SupportedLocale,
    );
    const effectiveUserId = userId || req?.user?.id;
    if (!effectiveUserId) {
      return paginatedResult;
    }
    const problemIds = paginatedResult.items.map((problem) =>
      Number(problem.id),
    );
    if (problemIds.length === 0) {
      return paginatedResult;
    }
    const statusMap = await this.submissionService.getProblemStatusMap(
      effectiveUserId,
      problemIds,
    );
    return {
      ...paginatedResult,
      items: paginatedResult.items.map((problem) => {
        const entry = statusMap.get(Number(problem.id));
        return {
          ...problem,
          status: entry?.status ?? 'todo',
          completed_time: entry ? entry.completed_time : null,
        };
      }),
    };
  }

  @Get('random')
  @ApiOperation({
    summary: 'Get random problem',
    description: 'Get a randomly selected problem',
  })
  @ApiResponse({ status: 200, description: 'Random problem' })
  @ApiResponse({ status: 404, description: 'No problems available' })
  @Throttle({ short: { limit: 100, ttl: 60000 } })
  getRandom(): Promise<Problem | null> {
    return this.problemService.getRandom();
  }

  @Get(':id')
  @ApiOperation({
    summary: 'Get problem by ID',
    description: 'Retrieve a specific problem with its details',
  })
  @ApiParam({ name: 'id', description: 'Problem ID or slug', type: String })
  @ApiResponse({ status: 200, description: 'Problem details' })
  @ApiResponse({ status: 404, description: 'Problem not found' })
  @ApiResponse({
    status: 403,
    description: 'Premium problem - subscription required',
  })
  @Throttle({ strict: { limit: 10, ttl: 60000 } })
  async findOne(
    @Param('id') id: string | number,
    @Query() query: ProblemParamsDto,
    @Req() req: AuthenticatedRequest,
    @Locale() locale?: string,
  ): Promise<Problem | object | null> {
    // AuthGuard ensures req.user is defined, but we need to handle optional query.userId
    const effectiveUserId = query.userId || req.user?.id;
    const userRole = req.user?.role;

    if (!effectiveUserId) {
      return null;
    }

    const problem = await this.problemService.findOneWithPremiumCheck(
      String(id),
      effectiveUserId,
      userRole,
      locale as SupportedLocale,
    );

    if (!problem) {
      return null;
    }

    // If problem is a teaser (premium without access), return as-is
    if (
      'is_premium' in problem &&
      (problem as { is_premium: boolean }).is_premium &&
      !('detail' in problem)
    ) {
      return problem;
    }

    // Add status tracking for full access
    const statusMap = await this.submissionService.getProblemStatusMap(
      effectiveUserId,
      [Number((problem as Problem).id)],
    );
    const entry = statusMap.get(Number((problem as Problem).id));
    return {
      ...problem,
      status: entry?.status ?? 'todo',
      completed_time: entry ? entry.completed_time : null,
    };
  }

  @Get(':id/results')
  @Throttle({ strict: { limit: 10, ttl: 60000 } })
  getProblemResults(
    @Param('id') id: string | number,
    @Query() query: ProblemParamsDto,
  ) {
    return this.submissionService.getLatestRunResult(Number(id), query.userId);
  }

  @Get(':id/adjacent')
  @Throttle({ short: { limit: 100, ttl: 60000 } })
  getAdjacent(@Param('id') id: string | number) {
    return this.problemService.findAdjacent(Number(id));
  }
}
