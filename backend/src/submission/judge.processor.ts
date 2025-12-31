import { Processor, WorkerHost, OnWorkerEvent } from '@nestjs/bullmq';
import { Job } from 'bullmq';
import { Logger } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { JudgeService } from './judge.service';
import { ContestSubmissionService } from './contest-submission.service';
import { SubmissionService } from './submission.service'; // Import SubmissionService

export interface JudgeJobData {
  submissionId: string;
}

@Processor('judge_queue')
export class JudgeProcessor extends WorkerHost {
  private readonly logger = new Logger(JudgeProcessor.name);

  constructor(
    private prisma: PrismaService,
    private judgeService: JudgeService,
    private submissionService: SubmissionService, // Inject SubmissionService
    private contestSubmissionService: ContestSubmissionService,
  ) {
    super();
  }

  async process(job: Job<JudgeJobData, any, string>): Promise<any> {
    this.logger.log(
      `Processing job ${job.id} for submission ${job.data.submissionId}`,
    );
    const { submissionId } = job.data;

    // Fetch submission with necessary relations
    const submission = await this.prisma.submission.findUnique({
      where: { id: submissionId },
      include: {
        problem: {
          include: {
            examples: true,
          },
        },
        contestSubmissions: {
          include: {
            participant: true,
            contestProblem: true, // Include contestProblem to get score
          },
        },
      },
    });

    if (!submission) {
      this.logger.error(`Submission ${submissionId} not found.`);
      throw new Error(`Submission ${submissionId} not found.`);
    }

    // Mark submission as Judging
    await this.prisma.submission.update({
      where: { id: submission.id },
      data: { status: 'Judging' },
    });

    try {
      // Build test cases from problem examples
      const testCases = submission.problem.examples.map((example) => {
        const inputs = Array.isArray(example.inputs)
          ? (example.inputs as { name: string; value: string }[])
          : [];
        return {
          id: example.id,
          label: `Case ${example.example_order + 1}`,
          inputs: inputs.map((input, inputIndex) => ({
            id: `${example.id}-input-${inputIndex}`,
            name: input.name,
            value: input.value,
            label: input.name,
          })),
          output: example.output_text,
        };
      });

      // Perform judging
      const judgeResult = this.judgeService.judge(
        submission.language,
        submission.code,
        testCases,
      );

      // Update submission with results using the new method
      await this.submissionService.updateSubmissionAfterJudging(
        submission.id,
        judgeResult,
      );

      // Refetch the updated submission with its contestSubmissions relation
      const updatedSubmission = await this.prisma.submission.findUnique({
        where: { id: submission.id },
        include: {
          contestSubmissions: {
            include: {
              participant: true,
              contestProblem: true,
            },
          },
        },
      });

      if (!updatedSubmission) {
        throw new Error(`Refetched submission ${submission.id} not found.`);
      }

      this.logger.log(
        `Submission ${submission.id} finished with verdict: ${updatedSubmission.status}`,
      );

      // If it's a contest submission, update contest results
      if (updatedSubmission.contestSubmissions.length > 0) {
        const contestSubmission = updatedSubmission.contestSubmissions[0];
        // Ensure contestProblem relation is loaded for score
        if (!contestSubmission.contestProblem) {
          throw new Error(
            `ContestProblem not found for contestSubmission ${contestSubmission.id}`,
          );
        }
        await this.contestSubmissionService.processContestSubmissionResult({
          submissionId: updatedSubmission.id,
          contestId: contestSubmission.contest_id,
          contestProblemId: contestSubmission.contest_problem_id,
          userId: updatedSubmission.user_id,
          participantId: contestSubmission.participant_id,
          isAccepted: updatedSubmission.status === 'Accepted',
          solveTime: contestSubmission.time_from_start,
          score: contestSubmission.contestProblem.score,
        });
      }

      return { status: updatedSubmission.status };
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : 'Unknown error';
      const errorStack = error instanceof Error ? error.stack : undefined;

      this.logger.error(
        `Error judging submission ${submission.id}: ${errorMessage}`,
        errorStack,
      );
      await this.prisma.submission.update({
        where: { id: submission.id },
        data: {
          status: 'System Error', // Or a more specific error like 'Judging Error'
          notes: `Judging failed: ${errorMessage}`,
        },
      });
      throw error; // Re-throw to mark job as failed
    }
  }

  @OnWorkerEvent('completed')
  onCompleted(job: Job<JudgeJobData, any, string>) {
    this.logger.log(
      `Job ${job.id} completed for submission ${job.data.submissionId}`,
    );
    // Potentially send WebSocket notification here
  }

  @OnWorkerEvent('failed')
  onFailed(job: Job<JudgeJobData, any, string>, err: Error) {
    this.logger.error(
      `Job ${job.id} failed for submission ${job.data.submissionId}: ${err.message}`,
      err.stack,
    );
    // Potentially send WebSocket notification here about failure
  }
}
