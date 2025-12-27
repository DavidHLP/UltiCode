import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { SubmissionService } from './submission.service';
import { CreateSubmissionDto } from './dto/create-submission.dto';
import { Submission } from '@prisma/client';

@Injectable()
export class ContestSubmissionService {
  constructor(
    private prisma: PrismaService,
    private submissionService: SubmissionService,
  ) {}

  /**
   * Submit code in a contest context
   * Handles both regular and virtual contests
   */
  async submitInContest(
    contestId: string,
    problemId: number,
    userId: string,
    dto: CreateSubmissionDto,
  ): Promise<Submission> {
    // 1. Validate contest and problem exist
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
      include: {
        problems: {
          where: { problem_id: problemId },
        },
      },
    });

    if (!contest) {
      throw new NotFoundException('Contest not found');
    }

    if (contest.problems.length === 0) {
      throw new NotFoundException('Problem not found in this contest');
    }

    const contestProblem = contest.problems[0];

    // 2. Check if user is registered for this contest
    const participant = await this.prisma.contestParticipant.findFirst({
      where: {
        contest_id: contestId,
        user_id: userId,
      },
      orderBy: {
        registered_at: 'desc',
      },
      include: {
        virtualSession: true,
      },
    });

    if (!participant) {
      throw new ForbiddenException('You must register for this contest first');
    }

    // 3. Validate submission timing based on contest type
    const now = new Date();
    let timeFromStart: number;
    let virtualSessionId: string | null = null;

    if (participant.is_virtual && participant.virtualSession) {
      // Virtual contest submission
      virtualSessionId = participant.virtual_session_id;
      const session = participant.virtualSession;

      if (session.status !== 'IN_PROGRESS') {
        throw new BadRequestException(
          'Virtual contest session is not in progress',
        );
      }

      if (!session.started_at || !session.ends_at) {
        throw new BadRequestException('Invalid virtual contest session');
      }

      if (now > session.ends_at) {
        throw new BadRequestException(
          'Virtual contest has ended. Cannot submit code.',
        );
      }

      timeFromStart = Math.floor(
        (now.getTime() - session.started_at.getTime()) / 1000,
      );
    } else {
      // Regular contest submission
      if (contest.status !== 'running') {
        throw new BadRequestException(
          `Contest is ${contest.status}. Can only submit during running contests.`,
        );
      }

      const contestEndTime = new Date(
        contest.start_time.getTime() + contest.duration_minutes * 60 * 1000,
      );

      if (now > contestEndTime) {
        throw new BadRequestException('Contest has ended. Cannot submit code.');
      }

      if (now < contest.start_time) {
        throw new BadRequestException(
          'Contest has not started yet. Cannot submit code.',
        );
      }

      timeFromStart = Math.floor(
        (now.getTime() - contest.start_time.getTime()) / 1000,
      );
    }

    // 4. Update participant status to STARTED if not already
    if (participant.status === 'REGISTERED') {
      await this.prisma.contestParticipant.update({
        where: { id: participant.id },
        data: {
          status: 'STARTED',
          started_at: now,
        },
      });
    }

    // 5. Create regular submission and judge it
    const submission = await this.submissionService.create(
      userId,
      problemId,
      dto,
    );

    // 6. Create ContestSubmission record
    await this.prisma.contestSubmission.create({
      data: {
        submission_id: submission.id,
        contest_id: contestId,
        contest_problem_id: contestProblem.id,
        participant_id: participant.id,
        virtual_session_id: virtualSessionId,
        submitted_at: now,
        time_from_start: timeFromStart,
        is_accepted: submission.status === 'Accepted',
      },
    });

    // 7. Update ContestProblemResult if submission is accepted
    if (submission.status === 'Accepted') {
      await this.updateProblemResult(
        contestId,
        contestProblem.id,
        userId,
        participant.id,
        timeFromStart,
        contestProblem.score,
      );
    } else {
      // Increment attempts even if not accepted
      await this.incrementProblemAttempts(
        contestId,
        contestProblem.id,
        userId,
        participant.id,
      );
    }

    return submission;
  }

  /**
   * Update or create ContestProblemResult when submission is accepted
   */
  private async updateProblemResult(
    contestId: string,
    contestProblemId: string,
    userId: string,
    participantId: string,
    solveTime: number,
    score: number,
  ): Promise<void> {
    // Find existing result
    const existingResult = await this.prisma.contestProblemResult.findFirst({
      where: {
        contest_id: contestId,
        contest_problem_id: contestProblemId,
        participant_id: participantId,
      },
    });

    if (existingResult && existingResult.is_solved) {
      // Already solved, don't update
      return;
    }

    const attempts = existingResult ? existingResult.attempts + 1 : 1;
    // Penalty: 5 minutes per wrong attempt before acceptance
    const penaltyFromAttempts = existingResult
      ? (existingResult.attempts - 1) * 5 * 60
      : 0;
    const totalPenalty = solveTime + penaltyFromAttempts;

    if (existingResult) {
      // Update existing result
      await this.prisma.contestProblemResult.update({
        where: { id: existingResult.id },
        data: {
          is_solved: true,
          score,
          attempts,
          first_solve_time: solveTime,
          penalty_time: totalPenalty,
        },
      });
    } else {
      // Create new result
      await this.prisma.contestProblemResult.create({
        data: {
          contest_id: contestId,
          contest_problem_id: contestProblemId,
          user_id: userId,
          participant_id: participantId,
          is_solved: true,
          score,
          attempts,
          first_solve_time: solveTime,
          penalty_time: totalPenalty,
        },
      });
    }

    // Recalculate participant's total score and penalty
    await this.recalculateParticipantScore(participantId);
  }

  /**
   * Increment attempts for a problem result (for wrong submissions)
   */
  private async incrementProblemAttempts(
    contestId: string,
    contestProblemId: string,
    userId: string,
    participantId: string,
  ): Promise<void> {
    const existingResult = await this.prisma.contestProblemResult.findFirst({
      where: {
        contest_id: contestId,
        contest_problem_id: contestProblemId,
        participant_id: participantId,
      },
    });

    if (existingResult) {
      await this.prisma.contestProblemResult.update({
        where: { id: existingResult.id },
        data: {
          attempts: existingResult.attempts + 1,
        },
      });
    } else {
      await this.prisma.contestProblemResult.create({
        data: {
          contest_id: contestId,
          contest_problem_id: contestProblemId,
          user_id: userId,
          participant_id: participantId,
          is_solved: false,
          score: 0,
          attempts: 1,
          penalty_time: 0,
        },
      });
    }
  }

  /**
   * Recalculate participant's total score and penalty from all problem results
   */
  private async recalculateParticipantScore(
    participantId: string,
  ): Promise<void> {
    const problemResults = await this.prisma.contestProblemResult.findMany({
      where: { participant_id: participantId },
    });

    const totalScore = problemResults.reduce(
      (sum, result) => sum + result.score,
      0,
    );
    const totalPenalty = problemResults.reduce(
      (sum, result) => sum + result.penalty_time,
      0,
    );

    await this.prisma.contestParticipant.update({
      where: { id: participantId },
      data: {
        total_score: totalScore,
        total_penalty: totalPenalty,
      },
    });
  }

  /**
   * Get contest submissions for a user
   */
  async getContestSubmissions(
    contestId: string,
    userId?: string,
    problemId?: number,
  ): Promise<any[]> {
    const submissions = await this.prisma.contestSubmission.findMany({
      where: {
        contest_id: contestId,
        ...(userId && {
          participant: {
            user_id: userId,
          },
        }),
        ...(problemId && {
          contestProblem: {
            problem_id: problemId,
          },
        }),
      },
      include: {
        submission: {
          include: {
            user: {
              select: {
                id: true,
                username: true,
                avatar: true,
              },
            },
            problem: {
              select: {
                id: true,
                title: true,
                slug: true,
              },
            },
          },
        },
        contestProblem: {
          select: {
            problem_index: true,
            score: true,
          },
        },
      },
      orderBy: {
        submitted_at: 'desc',
      },
    });

    return submissions.map((cs) => ({
      ...cs.submission,
      contest_info: {
        time_from_start: cs.time_from_start,
        problem_index: cs.contestProblem.problem_index,
        score: cs.contestProblem.score,
        is_accepted: cs.is_accepted,
      },
    }));
  }
}
