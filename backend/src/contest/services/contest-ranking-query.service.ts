import { Injectable } from '@nestjs/common';
import { ContestTieBreaker, Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma.service';
import {
  ContestRankingQueryDto,
  PaginatedResult,
  ContestRankingEntry,
  ProblemResultEntry,
} from '../dto';
import { RankingHelperService } from './ranking-helper.service';

type ProblemStatsSnapshot = Record<
  string,
  {
    problemId: number;
    isSolved: boolean;
    score: number;
    attempts: number;
    solveTime: number | null;
    penaltyTime: number;
  }
>;

@Injectable()
export class ContestRankingQueryService {
  constructor(
    private prisma: PrismaService,
    private helperService: RankingHelperService,
  ) {}

  async getContestRanking(
    contestId: string,
    query: ContestRankingQueryDto,
  ): Promise<PaginatedResult<ContestRankingEntry>> {
    const page = Number(query.page || 1);
    const limit = Number(query.limit || 50);
    const include_virtual = query.include_virtual !== false;
    const skip = (page - 1) * limit;

    const where = {
      contest_id: contestId,
      ...(include_virtual ? {} : { is_virtual: false }),
    };

    const [rankings, total] = await Promise.all([
      this.prisma.contestRanking.findMany({
        where,
        skip,
        take: limit,
        orderBy: [{ rank: 'asc' }],
        include: {
          user: {
            select: {
              id: true,
              username: true,
              avatar: true,
            },
          },
          problemResults: {
            include: {
              contestProblem: {
                select: {
                  problem_index: true,
                  problem_id: true,
                },
              },
            },
            orderBy: {
              contestProblem: {
                problem_index: 'asc',
              },
            },
          },
        },
      }),
      this.prisma.contestRanking.count({ where }),
    ]);

    const items: ContestRankingEntry[] = rankings.map((r) => {
      let problemResults: ProblemResultEntry[] = [];

      if (r.problem_stats_snapshot) {
        const snapshot =
          r.problem_stats_snapshot as unknown as ProblemStatsSnapshot;
        problemResults = Object.keys(snapshot).map((index) => ({
          problemIndex: index,
          problemId: snapshot[index].problemId,
          isSolved: snapshot[index].isSolved,
          score: snapshot[index].score,
          attempts: snapshot[index].attempts,
          wrongAttempts: snapshot[index].attempts,
          solveTime: snapshot[index].solveTime,
          penaltyTime: snapshot[index].penaltyTime,
        }));
        problemResults.sort((a, b) =>
          a.problemIndex.localeCompare(b.problemIndex),
        );
      } else {
        problemResults = r.problemResults.map((pr) => ({
          problemIndex: pr.contestProblem.problem_index,
          problemId: Number(pr.contestProblem.problem_id),
          isSolved: pr.is_solved,
          score: pr.score,
          attempts: pr.attempts,
          wrongAttempts: pr.attempts,
          solveTime: pr.first_solve_time,
          penaltyTime: pr.penalty_time,
        }));
      }

      return {
        rank: r.rank,
        userId: r.user_id,
        username: r.user.username,
        avatar: r.user.avatar,
        totalScore: r.total_score,
        totalPenalty: r.total_penalty,
        finishTime: r.finish_time ?? null,
        finish_time: r.finish_time ?? null,
        totalAttempts: r.total_attempts ?? 0,
        total_attempts: r.total_attempts ?? 0,
        solvedCount: r.solved_count,
        ratingBefore: r.rating_before,
        ratingAfter: r.rating_after,
        ratingChange: r.rating_change,
        isVirtual: r.is_virtual,
        problemResults,
      };
    });

    return {
      items,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
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
    const contestConfig = await this.helperService.getContestConfig(
      contestId,
      this.prisma,
    );
    const orderBy: Prisma.ContestParticipantOrderByWithRelationInput[] = [
      { total_score: 'desc' },
      { total_penalty: 'asc' },
    ];

    if (contestConfig.tieBreaker === ContestTieBreaker.LAST_SOLVE_TIME) {
      orderBy.push({ last_solve_time: 'asc' });
    }
    if (contestConfig.tieBreaker === ContestTieBreaker.TOTAL_ATTEMPTS) {
      orderBy.push({ total_attempts: 'asc' });
    }

    const participants = await this.prisma.contestParticipant.findMany({
      where: {
        contest_id: contestId,
        status: { in: ['STARTED', 'FINISHED'] },
      },
      orderBy,
      take: limit,
      include: {
        user: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
        problemResults: {
          include: {
            contestProblem: {
              select: {
                problem_index: true,
                problem_id: true,
              },
            },
          },
        },
      },
    });

    let currentRank = 1;
    return participants.map((p, index) => {
      if (index > 0) {
        const prev = participants[index - 1];
        if (!this.helperService.isSameRank(contestConfig.tieBreaker, prev, p)) {
          currentRank = index + 1;
        }
      }

      const finishTime =
        p.last_solve_time ?? this.helperService.getFinishTime(p.problemResults);

      return {
        rank: currentRank,
        userId: p.user_id,
        username: p.user.username,
        avatar: p.user.avatar,
        totalScore: p.total_score,
        totalPenalty: p.total_penalty,
        finishTime,
        finish_time: finishTime,
        totalAttempts: p.total_attempts ?? 0,
        total_attempts: p.total_attempts ?? 0,
        solvedCount: p.problemResults.filter((r) => r.is_solved).length,
        problemResults: p.problemResults.map((pr) => ({
          problemIndex: pr.contestProblem.problem_index,
          problemId: Number(pr.contestProblem.problem_id),
          isSolved: pr.is_solved,
          score: pr.score,
          attempts: pr.attempts,
          wrongAttempts: pr.attempts,
          solveTime: pr.first_solve_time,
          penaltyTime: pr.penalty_time,
        })),
      };
    });
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
    const rankings = await this.prisma.contestRanking.findMany({
      where: { user_id: userId },
      include: {
        contest: {
          include: {
            _count: {
              select: { rankings: { where: { is_virtual: false } } },
            },
          },
        },
      },
      orderBy: {
        contest: {
          start_time: 'desc',
        },
      },
    });

    return rankings.map((r) => ({
      contestId: r.contest_id,
      contestTitle: r.contest.title,
      contestDate: r.contest.start_time,
      rank: r.rank,
      totalParticipants: r.contest._count.rankings,
      score: r.total_score,
      solvedCount: r.solved_count,
      ratingBefore: r.rating_before,
      ratingAfter: r.rating_after,
      ratingChange: r.rating_change,
      isVirtual: r.is_virtual,
    }));
  }
}
