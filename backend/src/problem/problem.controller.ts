import { Controller, Get, Param, Query, Req, UseGuards } from '@nestjs/common';
import type { Request } from 'express';
import { ProblemService } from './problem.service';
import { Problem } from './problem.entity';
import { SubmissionService } from '../submission/submission.service';
import { Locale } from '../i18n/i18n.decorator';
import type { SupportedLocale } from '../i18n/i18n.constants';
import { FindAllProblemsQueryDto, ProblemParamsDto } from './dto';
import { AuthGuard } from '../auth/auth.guard';
import { Public } from '../auth/auth.decorator';

interface AuthenticatedRequest extends Request {
  user?: { id: string; role?: string };
}

@Controller('problems')
@UseGuards(AuthGuard)
export class ProblemController {
  constructor(
    private readonly problemService: ProblemService,
    private readonly submissionService: SubmissionService,
  ) {}

  @Get()
  @Public()
  async findAll(
    @Query() query: FindAllProblemsQueryDto,
    @Req() req?: AuthenticatedRequest,
    @Locale() locale?: string,
  ): Promise<Problem[]> {
    const { userId, category, difficulty, search } = query;
    const problems = await this.problemService.findAll(
      {
        category,
        difficulty,
        search,
      },
      locale as SupportedLocale,
    );
    const effectiveUserId = userId || req?.user?.id;
    if (!effectiveUserId) {
      return problems;
    }
    const problemIds = problems.map((problem) => Number(problem.id));
    if (problemIds.length === 0) {
      return [];
    }
    const statusMap = await this.submissionService.getProblemStatusMap(
      effectiveUserId,
      problemIds,
    );
    return problems.map((problem) => {
      const entry = statusMap.get(Number(problem.id));
      return {
        ...problem,
        status: entry?.status ?? 'todo',
        completed_time: entry ? entry.completed_time : null,
      };
    });
  }

  @Get('random')
  getRandom(): Promise<Problem | null> {
    return this.problemService.getRandom();
  }

  @Get(':id')
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
  getProblemResults(
    @Param('id') id: string | number,
    @Query() query: ProblemParamsDto,
  ) {
    return this.submissionService.getLatestRunResult(Number(id), query.userId);
  }

  @Get(':id/adjacent')
  getAdjacent(@Param('id') id: string | number) {
    return this.problemService.findAdjacent(Number(id));
  }
}
