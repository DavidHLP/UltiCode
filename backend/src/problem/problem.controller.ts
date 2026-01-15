import {
  Controller,
  Get,
  Param,
  Query,
  Req,
  ParseIntPipe,
} from '@nestjs/common';
import type { Request } from 'express';
import { ProblemService } from './problem.service';
import { Problem } from './problem.entity';
import { SubmissionService } from '../submission/submission.service';
import { Locale } from '../i18n/i18n.decorator';
import type { SupportedLocale } from '../i18n/i18n.constants';
import { FindAllProblemsQueryDto, ProblemParamsDto } from './dto';

interface AuthenticatedRequest extends Request {
  user?: { id: string };
}

@Controller('problems')
export class ProblemController {
  constructor(
    private readonly problemService: ProblemService,
    private readonly submissionService: SubmissionService,
  ) {}

  @Get()
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
    @Param('id', ParseIntPipe) id: number,
    @Query() query: ProblemParamsDto,
    @Req() req?: AuthenticatedRequest,
    @Locale() locale?: string,
  ): Promise<Problem | null> {
    const problem = await this.problemService.findOne(
      String(id),
      locale as SupportedLocale,
    );
    const effectiveUserId = query.userId || req?.user?.id;
    if (!problem || !effectiveUserId) {
      return problem;
    }
    const statusMap = await this.submissionService.getProblemStatusMap(
      effectiveUserId,
      [Number(problem.id)],
    );
    const entry = statusMap.get(Number(problem.id));
    return {
      ...problem,
      status: entry?.status ?? 'todo',
      completed_time: entry ? entry.completed_time : null,
    };
  }

  @Get(':id/results')
  getProblemResults(
    @Param('id', ParseIntPipe) id: number,
    @Query() query: ProblemParamsDto,
  ) {
    return this.submissionService.getLatestRunResult(id, query.userId);
  }

  @Get(':id/adjacent')
  getAdjacent(@Param('id', ParseIntPipe) id: number) {
    return this.problemService.findAdjacent(id);
  }
}
