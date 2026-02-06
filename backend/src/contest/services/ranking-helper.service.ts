import { Injectable } from '@nestjs/common';
import { ContestScoringMode, ContestTieBreaker, Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma.service';

type PrismaClient = Prisma.TransactionClient | PrismaService;

type TieBreakerSource = {
  total_score: number;
  total_penalty: number;
  last_solve_time?: number | null;
  finish_time?: number | null;
  total_attempts?: number | null;
};

export type ContestConfig = {
  penaltyPerWrong: number;
  scoringMode: ContestScoringMode;
  tieBreaker: ContestTieBreaker;
};

@Injectable()
export class RankingHelperService {
  constructor(private prisma: PrismaService) {}

  async getContestConfig(
    contestId: string,
    prisma: PrismaClient,
  ): Promise<ContestConfig> {
    const contest = await prisma.contest.findUnique({
      where: { id: contestId },
      select: {
        penalty_per_wrong: true,
        scoring_mode: true,
        tie_breaker: true,
      },
    });

    return {
      penaltyPerWrong: contest?.penalty_per_wrong ?? 300,
      scoringMode: contest?.scoring_mode ?? ContestScoringMode.SCORE,
      tieBreaker: contest?.tie_breaker ?? ContestTieBreaker.LAST_SOLVE_TIME,
    };
  }

  getFinishTime(
    results: { is_solved: boolean; first_solve_time: number | null }[],
  ): number | null {
    const solveTimes = results
      .filter((r) => r.is_solved && typeof r.first_solve_time === 'number')
      .map((r) => r.first_solve_time as number);

    if (solveTimes.length === 0) {
      return null;
    }

    return Math.max(...solveTimes);
  }

  getTieBreakerValue(
    tieBreaker: ContestTieBreaker,
    source: {
      last_solve_time?: number | null;
      finish_time?: number | null;
      total_attempts?: number | null;
    },
  ): number | null {
    if (tieBreaker === ContestTieBreaker.NONE) return null;

    if (tieBreaker === ContestTieBreaker.TOTAL_ATTEMPTS) {
      return source.total_attempts ?? Number.MAX_SAFE_INTEGER;
    }

    const value = source.last_solve_time ?? source.finish_time ?? null;
    return value ?? Number.MAX_SAFE_INTEGER;
  }

  isSameRank(
    tieBreaker: ContestTieBreaker,
    prev: TieBreakerSource,
    current: TieBreakerSource,
  ): boolean {
    if (
      prev.total_score !== current.total_score ||
      prev.total_penalty !== current.total_penalty
    ) {
      return false;
    }

    if (tieBreaker === ContestTieBreaker.NONE) {
      return true;
    }

    return (
      this.getTieBreakerValue(tieBreaker, prev) ===
      this.getTieBreakerValue(tieBreaker, current)
    );
  }
}
