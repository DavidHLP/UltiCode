import { Injectable } from '@nestjs/common';
import { ContestStatus, ContestType } from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import { ContestQueryDto } from '../dto';
import { ContestTimingService } from './contest-timing.service';
import { I18nService } from '../../i18n/i18n.service';
import { SupportedLocale, DEFAULT_LOCALE } from '../../i18n/i18n.constants';
import { CacheService } from '../../cache/cache.service';

export interface ContestStats {
  total_participants: number;
  total_contests: number;
}

export interface ContestWithTiming {
  id: string;
  title: string;
  slug: string;
  status: string;
  start_time: Date;
  end_time: Date;
  [key: string]: unknown;
}

@Injectable()
export class ContestQueryService {
  /** Cache TTL constants */
  private static readonly CACHE_TTL_CONTEST = 120; // 2 minutes
  private static readonly CACHE_TTL_LIST = 60; // 1 minute

  constructor(
    private prisma: PrismaService,
    private readonly timingService: ContestTimingService,
    private readonly i18nService: I18nService,
    private readonly cacheService: CacheService,
  ) {}

  async findAll(
    query?: ContestQueryDto,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ) {
    const page = Number(query?.page || 1);
    const limit = Number(query?.limit || 10);
    const { status, type } = query || {};
    const skip = (page - 1) * limit;

    // Try cache first
    const cacheKey = `contests:list:${locale}:${status || 'all'}:${type || 'all'}:${page}:${limit}`;
    const cached = await this.cacheService.get<typeof result>(cacheKey);
    if (cached) {
      return cached;
    }

    const where = {
      is_visible: true,
      ...(status ? { status: status as ContestStatus } : {}),
      ...(type ? { contest_type: type as ContestType } : {}),
    };

    const [contests, total] = await Promise.all([
      this.prisma.contest.findMany({
        where,
        skip,
        take: limit,
        orderBy: { start_time: 'desc' },
      }),
      this.prisma.contest.count({ where }),
    ]);

    const translatedContests = await this.i18nService.translateEntities(
      'CONTEST',
      contests,
      locale,
    );

    const result = {
      items: translatedContests.map((contest) =>
        this.timingService.withTimingFields(contest),
      ),
      total,
      page,
      limit,
    };

    // Cache the result
    await this.cacheService.set(
      cacheKey,
      result,
      ContestQueryService.CACHE_TTL_LIST,
    );

    return result;
  }

  async findOne(id: string, locale: SupportedLocale = DEFAULT_LOCALE) {
    // Try cache first
    const cacheKey = `contest:${id}:${locale}`;
    const cached =
      await this.cacheService.get<
        ReturnType<typeof this.timingService.withTimingFields>
      >(cacheKey);
    if (cached) {
      return cached;
    }

    const contest = await this.prisma.contest.findUnique({
      where: { id },
      include: {
        problems: {
          include: {
            problem: {
              select: {
                id: true,
                title: true,
                slug: true,
                difficulty: true,
                acceptance_rate: true,
              },
            },
          },
          orderBy: { problem_index: 'asc' },
        },
      },
    });

    if (!contest) {
      return null;
    }

    const translatedContest = await this.i18nService.translateEntity(
      'CONTEST',
      contest,
      locale,
    );

    if (contest.status === 'upcoming') {
      const result = this.timingService.withTimingFields({
        ...translatedContest,
        problems: [],
      });
      await this.cacheService.set(
        cacheKey,
        result,
        ContestQueryService.CACHE_TTL_CONTEST,
      );
      return result;
    }

    const problemIds = contest.problems.map((cp) => cp.problem_id);
    const translatedProblems =
      problemIds.length > 0
        ? await this.i18nService.translateEntities(
            'PROBLEM',
            contest.problems.map((cp) => cp.problem),
            locale,
          )
        : contest.problems.map((cp) => cp.problem);

    const problemMap = new Map(
      translatedProblems.map((p) => [String(p.id), p]),
    );

    const result = this.timingService.withTimingFields({
      ...translatedContest,
      problems: translatedContest.problems.map((cp) => {
        const translatedProblem = problemMap.get(String(cp.problem_id));
        return {
          id: cp.id,
          problem_index: cp.problem_index,
          score: cp.score,
          penalty_per_wrong: cp.penalty_per_wrong,
          solved_count: cp.solved_count,
          submission_count: cp.submission_count,
          problem_id: Number(cp.problem_id),
          title: translatedProblem?.title ?? cp.problem.title,
          slug: cp.problem.slug,
          difficulty: cp.problem.difficulty,
          acceptanceRate: Number(cp.problem.acceptance_rate),
        };
      }),
    });

    await this.cacheService.set(
      cacheKey,
      result,
      ContestQueryService.CACHE_TTL_CONTEST,
    );
    return result;
  }

  async findUpcoming(
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ContestWithTiming[]> {
    const cacheKey = `contests:upcoming:${locale}`;
    const cached = await this.cacheService.get<ContestWithTiming[]>(cacheKey);
    if (cached) {
      return cached;
    }

    const contests = await this.prisma.contest.findMany({
      where: { status: 'upcoming', is_visible: true },
      orderBy: { start_time: 'asc' },
    });
    const translatedContests = await this.i18nService.translateEntities(
      'CONTEST',
      contests,
      locale,
    );
    const result = translatedContests.map((contest) =>
      this.timingService.withTimingFields(contest),
    ) as ContestWithTiming[];
    await this.cacheService.set(
      cacheKey,
      result,
      ContestQueryService.CACHE_TTL_LIST,
    );
    return result;
  }

  async findRunning(
    locale: SupportedLocale = DEFAULT_LOCALE,
  ): Promise<ContestWithTiming[]> {
    const cacheKey = `contests:running:${locale}`;
    const cached = await this.cacheService.get<ContestWithTiming[]>(cacheKey);
    if (cached) {
      return cached;
    }

    const contests = await this.prisma.contest.findMany({
      where: { status: 'running', is_visible: true },
      orderBy: { start_time: 'asc' },
    });
    const translatedContests = await this.i18nService.translateEntities(
      'CONTEST',
      contests,
      locale,
    );
    const result = translatedContests.map((contest) =>
      this.timingService.withTimingFields(contest),
    ) as ContestWithTiming[];
    await this.cacheService.set(
      cacheKey,
      result,
      ContestQueryService.CACHE_TTL_LIST,
    );
    return result;
  }

  async findPast(
    page: number = 1,
    limit: number = 10,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ) {
    const skip = (page - 1) * limit;

    const [data, total] = await Promise.all([
      this.prisma.contest.findMany({
        where: { status: 'finished', is_visible: true },
        orderBy: { start_time: 'desc' },
        skip,
        take: limit,
      }),
      this.prisma.contest.count({
        where: { status: 'finished', is_visible: true },
      }),
    ]);

    const translatedData = await this.i18nService.translateEntities(
      'CONTEST',
      data,
      locale,
    );

    return {
      data: translatedData.map((contest) =>
        this.timingService.withTimingFields(contest),
      ),
      total,
      page,
      limit,
    };
  }

  async getStats(): Promise<ContestStats> {
    const [totalContests, totalParticipants] = await Promise.all([
      this.prisma.contest.count({ where: { is_visible: true } }),
      this.prisma.contestParticipant.count(),
    ]);

    return {
      total_contests: totalContests,
      total_participants: totalParticipants,
    };
  }
}
