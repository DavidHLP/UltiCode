import { Injectable } from '@nestjs/common';
import { ContestScoringMode, ContestTieBreaker, Prisma } from '@prisma/client';
import { v4 as uuid } from 'uuid';
import { PrismaService } from '../../prisma.service';
import { RankingHelperService } from './ranking-helper.service';

type SnapshotEntry = {
  problemId: number;
  isSolved: boolean;
  score: number;
  attempts: number;
  solveTime: number | null;
  penaltyTime: number;
};

type ProblemStatsSnapshot = Record<string, SnapshotEntry>;

type PrismaTransactionClient = Prisma.TransactionClient | PrismaService;

@Injectable()
export class ContestRankingCalcService {
  constructor(
    private prisma: PrismaService,
    private helperService: RankingHelperService,
  ) {}

  async updateContestProblemResult(
    participantId: string,
    contestProblemId: string,
    isAccepted: boolean,
    solveTime: number,
    score: number,
    prisma?: Prisma.TransactionClient,
  ): Promise<void> {
    const prismaClient: PrismaTransactionClient = prisma ?? this.prisma;
    const participant = await prismaClient.contestParticipant.findUnique({
      where: { id: participantId },
      include: {
        problemResults: true,
      },
    });

    if (!participant) return;

    const contestConfig = await this.helperService.getContestConfig(
      participant.contest_id,
      prismaClient,
    );
    const contestProblem = await prismaClient.contestProblem.findUnique({
      where: { id: contestProblemId },
      select: { penalty_per_wrong: true },
    });
    const penaltyPerWrong =
      contestProblem?.penalty_per_wrong ?? contestConfig.penaltyPerWrong;

    const problemResult = await prismaClient.contestProblemResult.findFirst({
      where: {
        participant_id: participantId,
        contest_problem_id: contestProblemId,
      },
    });

    if (problemResult) {
      if (isAccepted && !problemResult.is_solved) {
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
        await prismaClient.contestProblemResult.update({
          where: { id: problemResult.id },
          data: {
            attempts: { increment: 1 },
          },
        });
      }
    } else {
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

    const allResults = await prismaClient.contestProblemResult.findMany({
      where: { participant_id: participantId },
    });

    const totalScore =
      contestConfig.scoringMode === ContestScoringMode.ICPC
        ? allResults.filter((r) => r.is_solved).length
        : allResults.reduce((sum, r) => sum + (r.is_solved ? r.score : 0), 0);
    const totalPenalty = allResults.reduce((sum, r) => sum + r.penalty_time, 0);
    const finishTime = this.helperService.getFinishTime(allResults);

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

    const contestConfig = await this.helperService.getContestConfig(
      participant.contest_id,
      this.prisma,
    );
    const finishTime = this.helperService.getFinishTime(
      participant.problemResults,
    );
    const totalAttempts = participant.total_attempts ?? 0;

    const tieBreakerClause =
      contestConfig.tieBreaker === ContestTieBreaker.LAST_SOLVE_TIME
        ? finishTime === null
          ? { finish_time: { not: null } }
          : { finish_time: { lt: finishTime } }
        : contestConfig.tieBreaker === ContestTieBreaker.TOTAL_ATTEMPTS
          ? { total_attempts: { lt: totalAttempts } }
          : null;

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

    await this.prisma.contestParticipant.update({
      where: { id: participant.id },
      data: { final_rank: rank },
    });

    const globalRanking = await this.prisma.globalRanking.findUnique({
      where: { user_id: participant.user_id },
    });
    const currentRating = globalRanking?.rating ?? 1500;

    const problemStatsSnapshot: ProblemStatsSnapshot = {};
    for (const pr of participant.problemResults) {
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

      await this.prisma.contestProblemResult.updateMany({
        where: { participant_id: participant.id },
        data: { ranking_id: existingRanking.id },
      });
    } else {
      const rankingId: string = uuid();
      const newRanking = await this.prisma.contestRanking.create({
        data: {
          id: rankingId,
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

      await this.prisma.contestProblemResult.updateMany({
        where: { participant_id: participant.id },
        data: { ranking_id: newRanking.id },
      });
    }
  }

  async finalizeContestRanking(contestId: string): Promise<void> {
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

    await this.prisma.$transaction(async (tx) => {
      let currentRank = 1;
      for (let i = 0; i < participants.length; i++) {
        const p = participants[i];

        if (i > 0) {
          const prev = participants[i - 1];
          if (
            !this.helperService.isSameRank(contestConfig.tieBreaker, prev, p)
          ) {
            currentRank = i + 1;
          }
        }
        const rank = currentRank;
        const finishTime =
          p.last_solve_time ??
          this.helperService.getFinishTime(p.problemResults);
        const totalAttempts = p.total_attempts ?? 0;

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

        await tx.contestParticipant.update({
          where: { id: p.id },
          data: { final_rank: rank },
        });

        const currentRating = p.user.globalRanking?.rating ?? 1500;

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

          await tx.contestProblemResult.updateMany({
            where: { participant_id: p.id },
            data: { ranking_id: existingRanking.id },
          });
        } else {
          const rankingId: string = uuid();
          const newRanking = await tx.contestRanking.create({
            data: {
              id: rankingId,
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

          await tx.contestProblemResult.updateMany({
            where: { participant_id: p.id },
            data: { ranking_id: newRanking.id },
          });
        }
      }

      await tx.contest.update({
        where: { id: contestId },
        data: {
          participant_count: participants.length,
        },
      });
    });
  }
}
