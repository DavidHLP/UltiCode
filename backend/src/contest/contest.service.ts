import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  ContestQueryDto,
  CreateContestDto,
  UpdateContestDto,
  ParticipationStatus,
} from './dto';
import { I18nService } from '../i18n/i18n.service';
import { RankingService } from './ranking.service';
import { SupportedLocale, DEFAULT_LOCALE } from '../i18n/i18n.constants';
import { ContestTimingService } from './services/contest-timing.service';
import {
  ContestQueryService,
  ContestStats,
} from './services/contest-query.service';
import { ContestParticipationService } from './services/contest-participation.service';
import { ContestVirtualService } from './services/contest-virtual.service';
import { ContestAdminService } from './services/contest-admin.service';

@Injectable()
export class ContestService {
  constructor(
    private prisma: PrismaService,
    private readonly i18nService: I18nService,
    private readonly rankingService: RankingService,
    private readonly timingService: ContestTimingService,
    private readonly queryService: ContestQueryService,
    private readonly participationService: ContestParticipationService,
    private readonly virtualService: ContestVirtualService,
    private readonly adminService: ContestAdminService,
  ) {}

  async findAll(
    query?: ContestQueryDto,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ) {
    return this.queryService.findAll(query, locale);
  }

  async findOne(id: string, locale: SupportedLocale = DEFAULT_LOCALE) {
    const result = await this.queryService.findOne(id, locale);
    if (!result) {
      throw new NotFoundException(`Contest ${id} not found`);
    }
    return result;
  }

  async findUpcoming(locale: SupportedLocale = DEFAULT_LOCALE) {
    return this.queryService.findUpcoming(locale);
  }

  async findRunning(locale: SupportedLocale = DEFAULT_LOCALE) {
    return this.queryService.findRunning(locale);
  }

  async findPast(
    page: number = 1,
    limit: number = 10,
    locale: SupportedLocale = DEFAULT_LOCALE,
  ) {
    return this.queryService.findPast(page, limit, locale);
  }

  async getStats(): Promise<ContestStats> {
    return this.queryService.getStats();
  }

  async registerForContest(contestId: string, userId: string): Promise<void> {
    return this.participationService.registerForContest(contestId, userId);
  }

  async unregisterFromContest(
    contestId: string,
    userId: string,
  ): Promise<void> {
    return this.participationService.unregisterFromContest(contestId, userId);
  }

  async getParticipationStatus(
    contestId: string,
    userId: string,
  ): Promise<ParticipationStatus> {
    return this.participationService.getParticipationStatus(contestId, userId);
  }

  async getUserContests(
    userId: string,
    type: 'registered' | 'participated' | 'virtual',
  ) {
    const { participants } = await this.participationService.getUserContests(
      userId,
      type,
    );

    return participants.map((p) => ({
      ...this.timingService.withTimingFields(p.contest),
      participationStatus: p.status,
      score: p.total_score,
      rank: p.final_rank,
    }));
  }

  async startVirtualContest(contestId: string, userId: string) {
    return this.virtualService.startVirtualContest(contestId, userId);
  }

  async getVirtualSession(contestId: string, userId: string) {
    return this.virtualService.getVirtualSession(contestId, userId);
  }

  async finishVirtualContest(sessionId: string, userId: string): Promise<void> {
    return this.virtualService.finishVirtualContest(sessionId, userId);
  }

  async createContest(dto: CreateContestDto, userId: string) {
    return this.adminService.createContest(dto, userId);
  }

  async updateContest(id: string, dto: UpdateContestDto) {
    return this.adminService.updateContest(id, dto);
  }

  async deleteContest(id: string): Promise<void> {
    return this.adminService.deleteContest(id);
  }

  async updateContestStatus(
    id: string,
    status: 'upcoming' | 'running' | 'finished',
  ) {
    return this.adminService.updateContestStatus(id, status);
  }

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
    return this.rankingService.getContestRanking(contestId, {
      page: 1,
      limit: 50,
    });
  }
}
