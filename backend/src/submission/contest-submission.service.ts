import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { SubmissionService } from './submission.service';
import { SubmissionQueryService } from './services/submission-query.service';
import { CreateSubmissionDto } from './dto/create-submission.dto';
import { Submission } from '@prisma/client';
import { RankingService } from '../contest/ranking.service'; // Import RankingService

@Injectable()
export class ContestSubmissionService {
  constructor(
    private prisma: PrismaService,
    private submissionService: SubmissionService,
    private queryService: SubmissionQueryService,
    private rankingService: RankingService, // Inject RankingService
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
          select: { id: true, score: true }, // Select score
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

    // 5. Create regular submission and enqueue for judging
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
        is_accepted: false, // Initially false, updated after judging
      },
    });

    return submission;
  }

  // New method to process contest submission results after judging
  async processContestSubmissionResult(params: {
    submissionId: string;
    contestId: string;
    contestProblemId: string;
    userId: string;
    participantId: string;
    isAccepted: boolean;
    solveTime: number;
    score: number;
  }): Promise<void> {
    const {
      submissionId,
      contestProblemId,
      participantId,
      isAccepted,
      solveTime,
      score,
    } = params;

    await this.prisma.$transaction(async (tx) => {
      // Find the ContestSubmission record using findFirst since submission_id is not the @id
      const contestSubmissionRecord = await tx.contestSubmission.findFirst({
        where: { submission_id: submissionId },
      });

      if (!contestSubmissionRecord) {
        throw new NotFoundException(
          `ContestSubmission for submission ID ${submissionId} not found.`,
        );
      }

      // Update ContestSubmission record with final status using its actual primary key 'id'
      await tx.contestSubmission.update({
        where: { id: contestSubmissionRecord.id },
        data: {
          is_accepted: isAccepted,
        },
      });

      const existingProblemResult = await tx.contestProblemResult.findFirst({
        where: {
          participant_id: participantId,
          contest_problem_id: contestProblemId,
        },
      });

      await tx.contestProblem.update({
        where: { id: contestProblemId },
        data: {
          submission_count: { increment: 1 },
          ...(isAccepted &&
            !existingProblemResult?.is_solved && {
              solved_count: { increment: 1 },
            }),
        },
      });

      await tx.contestParticipant.update({
        where: { id: participantId },
        data: {
          total_attempts: { increment: 1 },
        },
      });

      // Update participant score and problem result
      await this.rankingService.updateContestProblemResult(
        participantId,
        contestProblemId,
        isAccepted,
        solveTime,
        score,
        tx,
      );
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

    return submissions.map((cs) => {
      const decoratedSubmission = this.queryService.decorateSubmission(
        cs.submission,
      );

      return {
        ...decoratedSubmission,
        contest_info: {
          time_from_start: cs.time_from_start,
          problem_index: cs.contestProblem.problem_index,
          score: cs.is_accepted ? cs.contestProblem.score : 0,
          is_accepted: cs.is_accepted,
        },
      };
    });
  }
}
