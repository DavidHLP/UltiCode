import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma.service';
import {
  ContestRankingQueryDto,
  GlobalRankingQueryDto,
  PaginatedResult,
  ContestRankingEntry,
  GlobalRankingEntry,
  ProblemResultEntry,
} from './dto';
import { RankingHelperService } from './services/ranking-helper.service';
import { GlobalRankingQueryService } from './services/global-ranking-query.service';
import { ContestRankingCalcService } from './services/contest-ranking-calc.service';
import { ContestRankingQueryService } from './services/contest-ranking-query.service';

@Injectable()
export class RankingService {
  constructor(
    private prisma: PrismaService,
    private readonly helperService: RankingHelperService,
    private readonly globalRankingQuery: GlobalRankingQueryService,
    private readonly contestRankingCalc: ContestRankingCalcService,
    private readonly contestRankingQuery: ContestRankingQueryService,
  ) {}

  async getContestRanking(
    contestId: string,
    query: ContestRankingQueryDto,
  ): Promise<PaginatedResult<ContestRankingEntry>> {
    return this.contestRankingQuery.getContestRanking(contestId, query);
  }

  async getGlobalRanking(
    query: GlobalRankingQueryDto,
  ): Promise<PaginatedResult<GlobalRankingEntry>> {
    return this.globalRankingQuery.getGlobalRanking(query);
  }

  async getLiveRanking(
    contestId: string,
    limit: number = 100,
  ): Promise<
    {
      rank: number;
      userId: string;
      username: string;
      avatar: string | null;
      totalScore: number;
      totalPenalty: number;
      finishTime: number | null;
      totalAttempts: number;
      total_attempts?: number;
      solvedCount: number;
      problemResults: ProblemResultEntry[];
    }[]
  > {
    return this.contestRankingQuery.getLiveRanking(contestId, limit);
  }

  async getUserContestHistory(userId: string): Promise<
    {
      contestId: string;
      contestTitle: string;
      contestDate: Date;
      rank: number;
      totalParticipants: number;
      score: number;
      solvedCount: number;
      ratingBefore: number;
      ratingAfter: number;
      ratingChange: number;
      isVirtual: boolean;
    }[]
  > {
    return this.contestRankingQuery.getUserContestHistory(userId);
  }

  async updateContestProblemResult(
    participantId: string,
    contestProblemId: string,
    isAccepted: boolean,
    solveTime: number,
    score: number,
    prisma?: Prisma.TransactionClient,
  ): Promise<void> {
    return this.contestRankingCalc.updateContestProblemResult(
      participantId,
      contestProblemId,
      isAccepted,
      solveTime,
      score,
      prisma,
    );
  }

  async finalizeVirtualRanking(participantId: string): Promise<void> {
    return this.contestRankingCalc.finalizeVirtualRanking(participantId);
  }

  async finalizeContestRanking(contestId: string): Promise<void> {
    return this.contestRankingCalc.finalizeContestRanking(contestId);
  }
}
