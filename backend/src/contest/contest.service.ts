import {
  Injectable,
  NotFoundException,
  BadRequestException,
} from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  ContestStatus,
  ContestParticipantStatus,
  ContestType,
} from '@prisma/client';
import { v4 as uuid } from 'uuid';
import {
  ContestQueryDto,
  CreateContestDto,
  UpdateContestDto,
  ParticipationStatus,
} from './dto';
import { I18nService } from '../i18n/i18n.service';
import { RankingService } from './ranking.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  TRANSLATABLE_ENTITIES,
} from '../i18n/i18n.constants';

export interface ContestStats {
  total_participants: number;
  total_contests: number;
}

@Injectable()
export class ContestService {
  constructor(
    private prisma: PrismaService,
    private readonly i18nService: I18nService,
    private readonly rankingService: RankingService,
  ) {}

  private withTimingFields<
    T extends {
      start_time: Date;
      duration_minutes: number;
      status: ContestStatus;
    },
  >(contest: T) {
    const endTime = new Date(
      contest.start_time.getTime() + contest.duration_minutes * 60 * 1000,
    );
    const now = Date.now();
    const startsInSeconds = Math.max(
      0,
      Math.floor((contest.start_time.getTime() - now) / 1000),
    );
    const endsInSeconds = Math.max(
      0,
      Math.floor((endTime.getTime() - now) / 1000),
    );

    return {
      ...contest,
      end_time: endTime,
      starts_in_seconds: startsInSeconds,
      ends_in_seconds: endsInSeconds,
      can_register: contest.status === 'upcoming',
      can_start: contest.status === 'running',
    };
  }

  private async applyContestTranslations<T extends { id: string }>(
    contests: T[],
    locale: SupportedLocale,
  ): Promise<T[]> {
    if (contests.length === 0) return contests;

    const ids = contests.map((c) => c.id);
    const translationsMap = await this.i18nService.getBatchTranslations(
      'CONTEST',
      ids,
      locale,
    );

    return contests.map((contest) => {
      const translations: Map<string, string> =
        translationsMap.get(contest.id) ?? new Map<string, string>();
      return this.i18nService.applyTranslations(
        contest,
        translations,
        TRANSLATABLE_ENTITIES.CONTEST.fields,
      );
    });
  }

  // =========================================================================
  // CONTEST QUERIES
  // =========================================================================

  async findAll(
    query?: ContestQueryDto,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ) {
    const page = Number(query?.page || 1);
    const limit = Number(query?.limit || 10);
    const { status, type } = query || {};
    const skip = (page - 1) * limit;

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

    const translatedContests = await this.applyContestTranslations(
      contests,
      locale,
    );

    return {
      items: translatedContests.map((contest) =>
        this.withTimingFields(contest),
      ),
      total,
      page,
      limit,
    };
  }

  async findOne(id: string, locale: SupportedLocale = DEFAULT_LOCALE) {
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
      throw new NotFoundException(`Contest ${id} not found`);
    }

    // Apply contest translations
    const contestTranslations = await this.i18nService.getTranslations(
      'CONTEST',
      contest.id,
      locale,
    );
    const translatedContest = this.i18nService.applyTranslations(
      contest,
      contestTranslations,
      TRANSLATABLE_ENTITIES.CONTEST.fields,
    );

    if (contest.status === 'upcoming') {
      return this.withTimingFields({
        ...translatedContest,
        problems: [],
      });
    }

    // Apply problem title translations
    const problemIds = contest.problems.map((cp) => cp.problem_id);
    const problemTranslationsMap: Map<
      string,
      Map<string, string>
    > = problemIds.length > 0
      ? await this.i18nService.getBatchTranslations(
          'PROBLEM',
          problemIds,
          locale,
        )
      : new Map<string, Map<string, string>>();

    return this.withTimingFields({
      ...translatedContest,
      problems: translatedContest.problems.map((cp) => {
        const problemTranslations: Map<string, string> =
          problemTranslationsMap.get(String(cp.problem_id)) ??
          new Map<string, string>();
        const translatedTitle: string =
          problemTranslations.get('title') ?? cp.problem.title;
        return {
          id: cp.id,
          problem_index: cp.problem_index,
          score: cp.score,
          penalty_per_wrong: cp.penalty_per_wrong,
          solved_count: cp.solved_count,
          submission_count: cp.submission_count,
          problem_id: Number(cp.problem_id),
          title: translatedTitle,
          slug: cp.problem.slug,
          difficulty: cp.problem.difficulty,
          acceptanceRate: Number(cp.problem.acceptance_rate),
        };
      }),
    });
  }

  async findUpcoming(locale: SupportedLocale = DEFAULT_LOCALE) {
    const contests = await this.prisma.contest.findMany({
      where: { status: 'upcoming', is_visible: true },
      orderBy: { start_time: 'asc' },
    });
    const translatedContests = await this.applyContestTranslations(
      contests,
      locale,
    );
    return translatedContests.map((contest) => this.withTimingFields(contest));
  }

  async findRunning(locale: SupportedLocale = DEFAULT_LOCALE) {
    const contests = await this.prisma.contest.findMany({
      where: { status: 'running', is_visible: true },
      orderBy: { start_time: 'asc' },
    });
    const translatedContests = await this.applyContestTranslations(
      contests,
      locale,
    );
    return translatedContests.map((contest) => this.withTimingFields(contest));
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

    const translatedData = await this.applyContestTranslations(data, locale);

    return {
      data: translatedData.map((contest) => this.withTimingFields(contest)),
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

  // =========================================================================
  // PARTICIPATION
  // =========================================================================

  async registerForContest(contestId: string, userId: string): Promise<void> {
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    if (contest.status !== 'upcoming') {
      throw new BadRequestException('Can only register for upcoming contests');
    }

    // Check if already registered
    const existing = await this.prisma.contestParticipant.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
        is_virtual: false,
      },
    });

    if (existing) {
      throw new BadRequestException('Already registered for this contest');
    }

    // Create participation record
    const participantId: string = uuid();
    await this.prisma.contestParticipant.create({
      data: {
        id: participantId,
        contest_id: contestId,
        user_id: userId,
        status: 'REGISTERED',
        is_virtual: false,
      },
    });

    // Increment registered count
    await this.prisma.contest.update({
      where: { id: contestId },
      data: { registered_count: { increment: 1 } },
    });
  }

  async unregisterFromContest(
    contestId: string,
    userId: string,
  ): Promise<void> {
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    if (contest.status !== 'upcoming') {
      throw new BadRequestException(
        'Can only unregister from upcoming contests',
      );
    }

    const participant = await this.prisma.contestParticipant.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
        is_virtual: false,
      },
    });

    if (!participant) {
      throw new BadRequestException('Not registered for this contest');
    }

    await this.prisma.contestParticipant.delete({
      where: { id: participant.id },
    });

    await this.prisma.contest.update({
      where: { id: contestId },
      data: { registered_count: { decrement: 1 } },
    });
  }

  async getParticipationStatus(
    contestId: string,
    userId: string,
  ): Promise<ParticipationStatus> {
    const participant = await this.prisma.contestParticipant.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
        is_virtual: false,
      },
    });

    if (!participant) {
      return {
        isRegistered: false,
        status: null,
        participantId: null,
        virtualSessionId: null,
        startedAt: null,
        finishedAt: null,
        totalScore: 0,
        totalPenalty: 0,
      };
    }

    return {
      isRegistered: true,
      status: participant.status,
      participantId: participant.id,
      virtualSessionId: participant.virtual_session_id,
      startedAt: participant.started_at,
      finishedAt: participant.finished_at,
      totalScore: participant.total_score,
      totalPenalty: participant.total_penalty,
    };
  }

  async getUserContests(
    userId: string,
    type: 'registered' | 'participated' | 'virtual',
  ) {
    const statusMap: Record<string, ContestParticipantStatus[]> = {
      registered: ['REGISTERED'],
      participated: ['STARTED', 'FINISHED'],
      virtual: ['REGISTERED', 'STARTED', 'FINISHED'],
    };

    const participants = await this.prisma.contestParticipant.findMany({
      where: {
        user_id: userId,
        status: { in: statusMap[type] },
        is_virtual: type === 'virtual',
      },
      include: {
        contest: true,
      },
      orderBy: {
        contest: {
          start_time: 'desc',
        },
      },
    });

    return participants.map((p) => ({
      ...this.withTimingFields(p.contest),
      participationStatus: p.status,
      score: p.total_score,
      rank: p.final_rank,
    }));
  }

  // =========================================================================
  // VIRTUAL CONTEST
  // =========================================================================

  async startVirtualContest(contestId: string, userId: string) {
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    if (contest.status !== 'finished') {
      throw new BadRequestException(
        'Can only start virtual contest for finished contests',
      );
    }

    // Check if already has an active virtual session
    const existingSession = await this.prisma.virtualContestSession.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
        status: 'IN_PROGRESS',
      },
      orderBy: { started_at: 'desc' },
    });

    if (existingSession) {
      return existingSession;
    }

    const now = new Date();
    const endsAt = new Date(
      now.getTime() + contest.duration_minutes * 60 * 1000,
    );

    // Create virtual session
    const sessionId: string = uuid();
    const session = await this.prisma.virtualContestSession.create({
      data: {
        id: sessionId,
        contest_id: contestId,
        user_id: userId,
        status: 'IN_PROGRESS',
        started_at: now,
        ends_at: endsAt,
      },
    });

    // Create virtual participant
    const virtualParticipantId: string = uuid();
    await this.prisma.contestParticipant.create({
      data: {
        id: virtualParticipantId,
        contest_id: contestId,
        user_id: userId,
        status: 'STARTED',
        started_at: now,
        is_virtual: true,
        virtual_session_id: session.id,
      },
    });

    return session;
  }

  async getVirtualSession(contestId: string, userId: string) {
    return this.prisma.virtualContestSession.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
      },
      orderBy: { started_at: 'desc' },
    });
  }

  async finishVirtualContest(sessionId: string, userId: string): Promise<void> {
    const session = await this.prisma.virtualContestSession.findUnique({
      where: { id: sessionId },
    });

    if (!session || session.user_id !== userId) {
      throw new NotFoundException('Virtual session not found');
    }

    if (session.status !== 'IN_PROGRESS') {
      throw new BadRequestException('Virtual session is not in progress');
    }

    await this.prisma.$transaction(async (tx) => {
      // Update session
      await tx.virtualContestSession.update({
        where: { id: sessionId },
        data: {
          status: 'COMPLETED',
          finished_at: new Date(),
        },
      });

      // Update participant
      await tx.contestParticipant.updateMany({
        where: { virtual_session_id: sessionId },
        data: {
          status: 'FINISHED',
          finished_at: new Date(),
        },
      });
    });

    // Finalize virtual ranking (outside transaction to avoid deadlocks/long transactions)
    await this.rankingService.finalizeVirtualRanking(
      // We need to find the participant ID. Since we know session ID and user ID...
      // But updateMany doesn't return IDs.
      // We can fetch it first or use the fact that it's unique per user per contest.
      // Ideally we should have fetched it.
      // Let's rely on finding it by session ID in finalizeVirtualRanking?
      // No, finalizeVirtualRanking takes participantId.
      // So we need to fetch participant ID.
      (
        await this.prisma.contestParticipant.findFirst({
          where: { virtual_session_id: sessionId },
        })
      )?.id as string,
    );
  }

  // =========================================================================
  // ADMIN OPERATIONS
  // =========================================================================

  async createContest(dto: CreateContestDto, userId: string) {
    const contestId: string = uuid();
    const contest = await this.prisma.contest.create({
      data: {
        id: contestId,
        title: dto.title,
        slug: dto.slug,
        contest_type: dto.contest_type,
        start_time: new Date(dto.start_time),
        duration_minutes: dto.duration_minutes,
        status: 'upcoming',
        ...(dto.penalty_per_wrong !== undefined && {
          penalty_per_wrong: dto.penalty_per_wrong,
        }),
        ...(dto.scoring_mode !== undefined && {
          scoring_mode: dto.scoring_mode,
        }),
        ...(dto.tie_breaker !== undefined && { tie_breaker: dto.tie_breaker }),
        is_rated: dto.is_rated,
        description: dto.description,
        cover_image: dto.cover_image,
        rules: dto.rules,
        created_by: userId,
      },
    });

    // Create contest problems if provided
    if (dto.problems && dto.problems.length > 0) {
      await this.prisma.contestProblem.createMany({
        data: dto.problems.map((p) => {
          const problemId: string = uuid();
          return {
            id: problemId,
            contest_id: contest.id,
            problem_id: BigInt(p.problem_id),
            problem_index: p.problem_index,
            score: p.score,
            ...(p.penalty_per_wrong !== undefined && {
              penalty_per_wrong: p.penalty_per_wrong,
            }),
          };
        }),
      });
    }

    return contest;
  }

  async updateContest(id: string, dto: UpdateContestDto) {
    const contest = await this.prisma.contest.findUnique({
      where: { id },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    return this.prisma.contest.update({
      where: { id },
      data: {
        ...(dto.title && { title: dto.title }),
        ...(dto.slug && { slug: dto.slug }),
        ...(dto.contest_type && { contest_type: dto.contest_type }),
        ...(dto.start_time && { start_time: new Date(dto.start_time) }),
        ...(dto.duration_minutes !== undefined && {
          duration_minutes: dto.duration_minutes,
        }),
        ...(dto.penalty_per_wrong !== undefined && {
          penalty_per_wrong: dto.penalty_per_wrong,
        }),
        ...(dto.scoring_mode !== undefined && {
          scoring_mode: dto.scoring_mode,
        }),
        ...(dto.tie_breaker !== undefined && { tie_breaker: dto.tie_breaker }),
        ...(dto.is_rated !== undefined && { is_rated: dto.is_rated }),
        ...(dto.description !== undefined && { description: dto.description }),
        ...(dto.cover_image !== undefined && { cover_image: dto.cover_image }),
        ...(dto.rules !== undefined && { rules: dto.rules }),
        ...(dto.is_visible !== undefined && { is_visible: dto.is_visible }),
      },
    });
  }

  async deleteContest(id: string): Promise<void> {
    const contest = await this.prisma.contest.findUnique({
      where: { id },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    await this.prisma.contest.delete({ where: { id } });
  }

  async updateContestStatus(id: string, status: ContestStatus) {
    return this.prisma.contest.update({
      where: { id },
      data: { status },
    });
  }

  // =========================================================================
  // LEGACY METHODS (for backward compatibility)
  // =========================================================================

  async getGlobalRanking() {
    const rankings = await this.prisma.globalRanking.findMany({
      orderBy: { global_rank: 'asc' },
      take: 10,
    });

    return rankings.map((r) => ({
      rank: r.global_rank,
      userId: r.user_id,
      username: r.username,
      avatar: r.avatar,
      country: r.country,
      rating: r.rating,
      maxRating: r.max_rating,
      ratingTitle: r.rating_title,
      maxRatingTitle: r.max_rating_title,
      contestsAttended: r.contests_attended,
      badge: r.badge,
    }));
  }

  async getContestRanking(contestId: string) {
    const rankings = await this.prisma.contestRanking.findMany({
      where: { contest_id: contestId },
      orderBy: { rank: 'asc' },
      take: 50,
      include: {
        user: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
      },
    });

    return rankings.map((r) => ({
      ...r,
      username: r.user.username,
      avatar: r.user.avatar,
    }));
  }
}
