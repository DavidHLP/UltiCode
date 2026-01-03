import { Injectable } from '@nestjs/common';
import { ContestScoringMode, ContestTieBreaker, Prisma } from '@prisma/client';
import { v4 as uuid } from 'uuid';
import { PrismaService } from '../prisma.service';
import {
  ContestRankingQueryDto,
  GlobalRankingQueryDto,
  PaginatedResult,
  ContestRankingEntry,
  GlobalRankingEntry,
  ProblemResultEntry,
} from './dto';

interface SnapshotEntry {
  problemId: number;
  isSolved: boolean;
  score: number;
  attempts: number;
  solveTime: number | null;
  penaltyTime: number;
}

type ProblemStatsSnapshot = Record<string, SnapshotEntry>;

type PrismaClient = Prisma.TransactionClient | PrismaService;

type TieBreakerSource = {
  total_score: number;
  total_penalty: number;
  last_solve_time?: number | null;
  finish_time?: number | null;
  total_attempts?: number | null;
};

type ContestConfig = {
  penaltyPerWrong: number;
  scoringMode: ContestScoringMode;
  tieBreaker: ContestTieBreaker;
};

@Injectable()
export class RankingService {
  constructor(private prisma: PrismaService) {}

  private async getContestConfig(
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

  private getFinishTime(
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

  private getTieBreakerValue(
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

  private isSameRank(
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

  /**
   * Get paginated contest rankings with problem results
   */
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
      // Use snapshot if available, otherwise map from problemResults
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
        // Sort by index to maintain consistency
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

  /**
   * Get paginated global rankings
   */
  async getGlobalRanking(
    query: GlobalRankingQueryDto,
  ): Promise<PaginatedResult<GlobalRankingEntry>> {
    const page = Number(query.page || 1);
    const limit = Number(query.limit || 50);
    const { country } = query;
    const skip = (page - 1) * limit;

    const where = {
      ...(country ? { country } : {}),
    };

    const [rankings, total] = await Promise.all([
      this.prisma.globalRanking.findMany({
        where,
        skip,
        take: limit,
        orderBy: [{ global_rank: 'asc' }],
      }),
      this.prisma.globalRanking.count({ where }),
    ]);

    const items: GlobalRankingEntry[] = rankings.map((r) => ({
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

    return {
      items,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  /**
   * Update participant score and ranking during live contest
   */
  async updateContestProblemResult(
    participantId: string,
    contestProblemId: string,
    isAccepted: boolean,
    solveTime: number,
    score: number,
    prisma?: Prisma.TransactionClient,
  ): Promise<void> {
    const prismaClient: PrismaClient = prisma ?? this.prisma;
    const participant = await prismaClient.contestParticipant.findUnique({
      where: { id: participantId },
      include: {
        problemResults: true,
      },
    });

    if (!participant) return;

    const contestConfig = await this.getContestConfig(
      participant.contest_id,
      prismaClient,
    );
    const contestProblem = await prismaClient.contestProblem.findUnique({
      where: { id: contestProblemId },
      select: { penalty_per_wrong: true },
    });
    const penaltyPerWrong =
      contestProblem?.penalty_per_wrong ?? contestConfig.penaltyPerWrong;

    // Find or create problem result
    const problemResult = await prismaClient.contestProblemResult.findFirst({
      where: {
        participant_id: participantId,
        contest_problem_id: contestProblemId,
      },
    });

    if (problemResult) {
      // Update existing result
      if (isAccepted && !problemResult.is_solved) {
        // First successful solve
        await prismaClient.contestProblemResult.update({
          where: { id: problemResult.id },
          data: {
            is_solved: true,
            score,
            first_solve_time: solveTime,
            penalty_time: solveTime + problemResult.attempts * penaltyPerWrong,
          },
        });
      } else if (!isAccepted && !problemResult.is_solved) {
        // Wrong attempt
        await prismaClient.contestProblemResult.update({
          where: { id: problemResult.id },
          data: {
            attempts: { increment: 1 },
          },
        });
      }
    } else {
      // Create new result
      await prismaClient.contestProblemResult.create({
        data: {
          contest_id: participant.contest_id,
          contest_problem_id: contestProblemId,
          user_id: participant.user_id,
          participant_id: participantId,
          is_solved: isAccepted,
          score: isAccepted ? score : 0,
          attempts: isAccepted ? 0 : 1,
          first_solve_time: isAccepted ? solveTime : null,
          penalty_time: isAccepted ? solveTime : 0,
        },
      });
    }

    // Recalculate participant total score and penalty
    const allResults = await prismaClient.contestProblemResult.findMany({
      where: { participant_id: participantId },
    });

    const totalScore =
      contestConfig.scoringMode === ContestScoringMode.ICPC
        ? allResults.filter((r) => r.is_solved).length
        : allResults.reduce((sum, r) => sum + (r.is_solved ? r.score : 0), 0);
    const totalPenalty = allResults.reduce((sum, r) => sum + r.penalty_time, 0);
    const finishTime = this.getFinishTime(allResults);

    await prismaClient.contestParticipant.update({
      where: { id: participantId },
      data: {
        total_score: totalScore,
        total_penalty: totalPenalty,
        last_solve_time: finishTime,
      },
    });

    if (participant.is_virtual && participant.virtual_session_id) {
      await prismaClient.virtualContestSession.update({
        where: { id: participant.virtual_session_id },
        data: {
          total_score: totalScore,
          total_penalty: totalPenalty,
        },
      });
    }
  }

  /**
   * Calculate and store final ranking for a virtual participant
   * Rank is calculated against real participants
   */
  async finalizeVirtualRanking(participantId: string): Promise<void> {
    const participant = await this.prisma.contestParticipant.findUnique({
      where: { id: participantId },
      include: {
        problemResults: true,
        user: {
          select: {
            username: true,
            avatar: true,
          },
        },
      },
    });

    if (!participant || !participant.is_virtual) return;

    const contestConfig = await this.getContestConfig(
      participant.contest_id,
      this.prisma,
    );
    const finishTime = this.getFinishTime(participant.problemResults);
    const totalAttempts = participant.total_attempts ?? 0;

    const tieBreakerClause =
      contestConfig.tieBreaker === ContestTieBreaker.LAST_SOLVE_TIME
        ? finishTime === null
          ? { finish_time: { not: null } }
          : { finish_time: { lt: finishTime } }
        : contestConfig.tieBreaker === ContestTieBreaker.TOTAL_ATTEMPTS
          ? { total_attempts: { lt: totalAttempts } }
          : null;

    // Calculate rank against real participants
    // Count how many real participants have better score/penalty
    const betterParticipantsCount = await this.prisma.contestRanking.count({
      where: {
        contest_id: participant.contest_id,
        is_virtual: false,
        OR: [
          { total_score: { gt: participant.total_score } },
          {
            AND: [
              { total_score: participant.total_score },
              { total_penalty: { lt: participant.total_penalty } },
            ],
          },
          ...(tieBreakerClause
            ? [
                {
                  AND: [
                    { total_score: participant.total_score },
                    { total_penalty: participant.total_penalty },
                    tieBreakerClause,
                  ],
                },
              ]
            : []),
        ],
      },
    });

    const rank = betterParticipantsCount + 1;

    // Update participant with final rank
    await this.prisma.contestParticipant.update({
      where: { id: participant.id },
      data: { final_rank: rank },
    });

    // Get current rating (for completeness, though virtual usually doesn't affect rating)
    const globalRanking = await this.prisma.globalRanking.findUnique({
      where: { user_id: participant.user_id },
    });
    const currentRating = globalRanking?.rating ?? 1500;

    // Generate snapshot
    const problemStatsSnapshot: ProblemStatsSnapshot = {};
    for (const pr of participant.problemResults) {
      // Need to get problem index, let's include it in the initial query
      const contestProblem = await this.prisma.contestProblem.findUnique({
        where: { id: pr.contest_problem_id },
      });
      if (contestProblem) {
        problemStatsSnapshot[contestProblem.problem_index] = {
          problemId: Number(contestProblem.problem_id),
          isSolved: pr.is_solved,
          score: pr.score,
          attempts: pr.attempts,
          solveTime: pr.first_solve_time,
          penaltyTime: pr.penalty_time,
        };
      }
    }

    // Create or update contest ranking
    const existingRanking = await this.prisma.contestRanking.findFirst({
      where: {
        contest_id: participant.contest_id,
        user_id: participant.user_id,
        is_virtual: true,
      },
    });

    if (existingRanking) {
      await this.prisma.contestRanking.update({
        where: { id: existingRanking.id },
        data: {
          rank,
          total_score: participant.total_score,
          total_penalty: participant.total_penalty,
          finish_time: finishTime,
          total_attempts: totalAttempts,
          solved_count: participant.problemResults.filter((r) => r.is_solved)
            .length,
          rating_before: currentRating,
          problem_stats_snapshot:
            problemStatsSnapshot as unknown as Prisma.InputJsonValue,
        },
      });

      // Link problem results
      await this.prisma.contestProblemResult.updateMany({
        where: { participant_id: participant.id },
        data: { ranking_id: existingRanking.id },
      });
    } else {
      const newRanking = await this.prisma.contestRanking.create({
        data: {
          id: uuid(),
          contest_id: participant.contest_id,
          user_id: participant.user_id,
          rank,
          total_score: participant.total_score,
          total_penalty: participant.total_penalty,
          finish_time: finishTime,
          total_attempts: totalAttempts,
          solved_count: participant.problemResults.filter((r) => r.is_solved)
            .length,
          rating_before: currentRating,
          rating_after: currentRating,
          rating_change: 0,
          is_virtual: true,
          problem_stats_snapshot:
            problemStatsSnapshot as unknown as Prisma.InputJsonValue,
        },
      });

      // Link problem results
      await this.prisma.contestProblemResult.updateMany({
        where: { participant_id: participant.id },
        data: { ranking_id: newRanking.id },
      });
    }
  }

  /**
   * Calculate and store final rankings for a contest
   */
  async finalizeContestRanking(contestId: string): Promise<void> {
    const contestConfig = await this.getContestConfig(contestId, this.prisma);
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

    // Get all participants sorted by score (desc), then penalty (asc)
    const participants = await this.prisma.contestParticipant.findMany({
      where: {
        contest_id: contestId,
        status: 'FINISHED',
      },
      orderBy,
      include: {
        problemResults: {
          include: {
            contestProblem: true,
          },
        },
        user: {
          include: {
            globalRanking: true,
          },
        },
      },
    });

    // Assign ranks and create ContestRanking entries
    await this.prisma.$transaction(async (tx) => {
      let currentRank = 1;
      for (let i = 0; i < participants.length; i++) {
        const p = participants[i];

        if (i > 0) {
          const prev = participants[i - 1];
          if (!this.isSameRank(contestConfig.tieBreaker, prev, p)) {
            currentRank = i + 1;
          }
        }
        const rank = currentRank;
        const finishTime =
          p.last_solve_time ?? this.getFinishTime(p.problemResults);
        const totalAttempts = p.total_attempts ?? 0;

        // Generate snapshot
        const problemStatsSnapshot: ProblemStatsSnapshot = {};
        for (const pr of p.problemResults) {
          problemStatsSnapshot[pr.contestProblem.problem_index] = {
            problemId: Number(pr.contestProblem.problem_id),
            isSolved: pr.is_solved,
            score: pr.score,
            attempts: pr.attempts,
            solveTime: pr.first_solve_time,
            penaltyTime: pr.penalty_time,
          };
        }

        // Update participant with final rank
        await tx.contestParticipant.update({
          where: { id: p.id },
          data: { final_rank: rank },
        });

        // Get current rating
        const currentRating = p.user.globalRanking?.rating ?? 1500;

        // Create or update contest ranking
        const existingRanking = await tx.contestRanking.findFirst({
          where: {
            contest_id: contestId,
            user_id: p.user_id,
            is_virtual: p.is_virtual,
          },
        });

        if (existingRanking) {
          await tx.contestRanking.update({
            where: { id: existingRanking.id },
            data: {
              rank,
              total_score: p.total_score,
              total_penalty: p.total_penalty,
              finish_time: finishTime,
              total_attempts: totalAttempts,
              solved_count: p.problemResults.filter((r) => r.is_solved).length,
              rating_before: currentRating,
              problem_stats_snapshot:
                problemStatsSnapshot as unknown as Prisma.InputJsonValue,
            },
          });

          // Link problem results to ranking
          await tx.contestProblemResult.updateMany({
            where: { participant_id: p.id },
            data: { ranking_id: existingRanking.id },
          });
        } else {
          const newRanking = await tx.contestRanking.create({
            data: {
              id: uuid(),
              contest_id: contestId,
              user_id: p.user_id,
              rank,
              total_score: p.total_score,
              total_penalty: p.total_penalty,
              finish_time: finishTime,
              total_attempts: totalAttempts,
              solved_count: p.problemResults.filter((r) => r.is_solved).length,
              rating_before: currentRating,
              rating_after: currentRating,
              rating_change: 0,
              is_virtual: p.is_virtual,
              problem_stats_snapshot:
                problemStatsSnapshot as unknown as Prisma.InputJsonValue,
            },
          });

          // Link problem results to ranking
          await tx.contestProblemResult.updateMany({
            where: { participant_id: p.id },
            data: { ranking_id: newRanking.id },
          });
        }
      }

      // Update contest participant count
      await tx.contest.update({
        where: { id: contestId },
        data: {
          participant_count: participants.length,
        },
      });
    });
  }

  /**
   * Get user's contest history
   */
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

  /**
   * Get live ranking during an ongoing contest (calculated on-the-fly)
   */
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
    const contestConfig = await this.getContestConfig(contestId, this.prisma);
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
        if (!this.isSameRank(contestConfig.tieBreaker, prev, p)) {
          currentRank = index + 1;
        }
      }

      const finishTime =
        p.last_solve_time ?? this.getFinishTime(p.problemResults);

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
}
