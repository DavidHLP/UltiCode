import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  ContestRankingQueryDto,
  GlobalRankingQueryDto,
  PaginatedResult,
  ContestRankingEntry,
  GlobalRankingEntry,
  ProblemResultEntry,
} from './dto';

@Injectable()
export class RankingService {
  constructor(private prisma: PrismaService) {}

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

    const items: ContestRankingEntry[] = rankings.map((r) => ({
      rank: r.rank,
      userId: r.user_id,
      username: r.user.username,
      avatar: r.user.avatar,
      totalScore: r.total_score,
      totalPenalty: r.total_penalty,
      solvedCount: r.solved_count,
      ratingBefore: r.rating_before,
      ratingAfter: r.rating_after,
      ratingChange: r.rating_change,
      isVirtual: r.is_virtual,
      problemResults: r.problemResults.map((pr) => ({
        problemIndex: pr.contestProblem.problem_index,
        problemId: Number(pr.contestProblem.problem_id),
        isSolved: pr.is_solved,
        score: pr.score,
        attempts: pr.attempts,
        solveTime: pr.first_solve_time,
        penaltyTime: pr.penalty_time,
      })),
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
  async updateParticipantScore(
    participantId: string,
    contestProblemId: string,
    isAccepted: boolean,
    solveTime: number,
    score: number,
  ): Promise<void> {
    const participant = await this.prisma.contestParticipant.findUnique({
      where: { id: participantId },
      include: {
        problemResults: true,
      },
    });

    if (!participant) return;

    // Find or create problem result
    const problemResult = await this.prisma.contestProblemResult.findFirst({
      where: {
        participant_id: participantId,
        contest_problem_id: contestProblemId,
      },
    });

    if (problemResult) {
      // Update existing result
      if (isAccepted && !problemResult.is_solved) {
        // First successful solve
        await this.prisma.contestProblemResult.update({
          where: { id: problemResult.id },
          data: {
            is_solved: true,
            score,
            first_solve_time: solveTime,
            attempts: { increment: 1 },
            penalty_time: solveTime + problemResult.attempts * 5 * 60, // 5 min penalty per wrong attempt
          },
        });
      } else if (!isAccepted && !problemResult.is_solved) {
        // Wrong attempt
        await this.prisma.contestProblemResult.update({
          where: { id: problemResult.id },
          data: {
            attempts: { increment: 1 },
          },
        });
      }
    } else {
      // Create new result
      await this.prisma.contestProblemResult.create({
        data: {
          contest_id: participant.contest_id,
          contest_problem_id: contestProblemId,
          user_id: participant.user_id,
          participant_id: participantId,
          is_solved: isAccepted,
          score: isAccepted ? score : 0,
          attempts: 1,
          first_solve_time: isAccepted ? solveTime : null,
          penalty_time: isAccepted ? solveTime : 0,
        },
      });
    }

    // Recalculate participant total score and penalty
    const allResults = await this.prisma.contestProblemResult.findMany({
      where: { participant_id: participantId },
    });

    const totalScore = allResults.reduce(
      (sum, r) => sum + (r.is_solved ? r.score : 0),
      0,
    );
    const totalPenalty = allResults.reduce((sum, r) => sum + r.penalty_time, 0);

    await this.prisma.contestParticipant.update({
      where: { id: participantId },
      data: {
        total_score: totalScore,
        total_penalty: totalPenalty,
      },
    });
  }

  /**
   * Calculate and store final rankings for a contest
   */
  async finalizeContestRanking(contestId: string): Promise<void> {
    // Get all participants sorted by score (desc), then penalty (asc)
    const participants = await this.prisma.contestParticipant.findMany({
      where: {
        contest_id: contestId,
        status: 'FINISHED',
      },
      orderBy: [{ total_score: 'desc' }, { total_penalty: 'asc' }],
      include: {
        problemResults: true,
        user: {
          include: {
            globalRanking: true,
          },
        },
      },
    });

    // Assign ranks and create ContestRanking entries
    await this.prisma.$transaction(async (tx) => {
      for (let i = 0; i < participants.length; i++) {
        const p = participants[i];
        const rank = i + 1;

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
              solved_count: p.problemResults.filter((r) => r.is_solved).length,
              rating_before: currentRating,
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
              id: `cr-${contestId}-${p.user_id}`,
              contest_id: contestId,
              user_id: p.user_id,
              rank,
              total_score: p.total_score,
              total_penalty: p.total_penalty,
              solved_count: p.problemResults.filter((r) => r.is_solved).length,
              rating_before: currentRating,
              rating_after: currentRating,
              rating_change: 0,
              is_virtual: p.is_virtual,
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
      solvedCount: number;
      problemResults: ProblemResultEntry[];
    }[]
  > {
    const participants = await this.prisma.contestParticipant.findMany({
      where: {
        contest_id: contestId,
        status: { in: ['STARTED', 'FINISHED'] },
      },
      orderBy: [{ total_score: 'desc' }, { total_penalty: 'asc' }],
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

    return participants.map((p, index) => ({
      rank: index + 1,
      userId: p.user_id,
      username: p.user.username,
      avatar: p.user.avatar,
      totalScore: p.total_score,
      totalPenalty: p.total_penalty,
      solvedCount: p.problemResults.filter((r) => r.is_solved).length,
      problemResults: p.problemResults.map((pr) => ({
        problemIndex: pr.contestProblem.problem_index,
        problemId: Number(pr.contestProblem.problem_id),
        isSolved: pr.is_solved,
        score: pr.score,
        attempts: pr.attempts,
        solveTime: pr.first_solve_time,
        penaltyTime: pr.penalty_time,
      })),
    }));
  }
}
