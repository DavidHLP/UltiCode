import { Processor, WorkerHost, OnWorkerEvent } from '@nestjs/bullmq';
import { Job } from 'bullmq';
import { Logger } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { JudgeService } from './judge.service';
import { ContestSubmissionService } from './contest-submission.service';
import { SubmissionService } from './submission.service';
import { NotificationService } from '../notification/notification.service';
import { NotificationGateway } from '../notification/notification.gateway';
import { NotificationEvent } from '../notification/notification.events';
import { TestCaseService } from '../test-case/test-case.service';
import { AchievementTriggerService } from '../achievement/achievement-trigger.service';
import { NotificationCategory, NotificationType } from '@prisma/client';

export interface JudgeJobData {
  submissionId: string;
}

@Processor('judge_queue')
export class JudgeProcessor extends WorkerHost {
  private readonly logger = new Logger(JudgeProcessor.name);

  constructor(
    private prisma: PrismaService,
    private judgeService: JudgeService,
    private submissionService: SubmissionService,
    private contestSubmissionService: ContestSubmissionService,
    private notificationService: NotificationService,
    private notificationGateway: NotificationGateway,
    private testCaseService: TestCaseService,
    private achievementTriggerService: AchievementTriggerService,
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

    // Send real-time notification that judging has started
    this.notificationGateway.sendToUser(
      submission.user_id,
      NotificationEvent.SUBMISSION_STARTED,
      {
        submissionId: submission.id,
        problemId: submission.problem_id.toString(),
        problemSlug: submission.problem?.slug,
        status: 'Judging',
      },
    );

    try {
      // Try to get test cases from TestCase table first
      let testCases = await this.buildTestCasesFromTable(submission.problem_id);

      // Fallback to problem examples if no test cases exist
      if (testCases.length === 0) {
        testCases = this.buildTestCasesFromExamples(
          submission.problem.examples,
        );
      }

      // Perform judging
      const judgeResult = await this.judgeService.judge(
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

      try {
        const problemTitle = submission.problem?.title || 'your problem';
        const problemSlug = submission.problem?.slug;
        const isAccepted = updatedSubmission.status === 'Accepted';
        const title = isAccepted ? 'Submission Accepted' : 'Submission Result';
        const body = `Your submission for "${problemTitle}" is ${updatedSubmission.status}.`;

        await this.notificationService.createNotification({
          userId: updatedSubmission.user_id,
          type: NotificationType.SUBMISSION,
          category: NotificationCategory.COMMUNICATION,
          title,
          body,
          link: problemSlug ? `/problems/${problemSlug}` : undefined,
          metadata: {
            submissionId: updatedSubmission.id,
            status: updatedSubmission.status,
            problemId: submission.problem_id.toString(),
            problemSlug,
            language: updatedSubmission.language,
          },
        });

        // Send real-time WebSocket notification with submission result
        this.notificationGateway.sendSubmissionResult(
          updatedSubmission.user_id,
          {
            submissionId: updatedSubmission.id,
            problemId: submission.problem_id.toString(),
            problemSlug: problemSlug || '',
            status: updatedSubmission.status,
            runtime: updatedSubmission.runtime,
            memory: updatedSubmission.memory,
          },
        );
      } catch (error: unknown) {
        this.logger.warn(
          `Failed to create notification for submission ${updatedSubmission.id}: ${
            error instanceof Error ? error.message : 'Unknown error'
          }`,
        );
      }

      // Trigger achievement checks if submission is accepted
      if (updatedSubmission.status === 'Accepted') {
        // Don't await - run in background to not delay response
        this.achievementTriggerService
          .onSubmissionAccepted(
            updatedSubmission.user_id,
            submission.problem_id,
          )
          .catch((err) => {
            this.logger.warn(
              `Failed to trigger achievement check for submission ${updatedSubmission.id}: ${err}`,
            );
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
          status: 'System Error',
          notes: `Judging failed: ${errorMessage}`,
        },
      });

      // Send real-time notification about the error
      this.notificationGateway.sendSubmissionResult(submission.user_id, {
        submissionId: submission.id,
        problemId: submission.problem_id.toString(),
        problemSlug: submission.problem?.slug || '',
        status: 'System Error',
        runtime: 0,
        memory: 0,
      });

      throw error; // Re-throw to mark job as failed
    }
  }

  private async buildTestCasesFromTable(problemId: bigint) {
    const dbTestCases =
      await this.testCaseService.getTestCasesForJudging(problemId);

    return dbTestCases.map((tc, index) => {
      let inputs: { name: string; value: string }[] = [];
      try {
        // Parse input_text as JSON array if possible
        const parsed = JSON.parse(tc.input_text);
        if (Array.isArray(parsed)) {
          inputs = parsed.map((value, i) => ({
            name: `arg${i}`,
            value: typeof value === 'string' ? value : JSON.stringify(value),
          }));
        } else {
          inputs = [{ name: 'input', value: tc.input_text }];
        }
      } catch {
        inputs = [{ name: 'input', value: tc.input_text }];
      }

      return {
        id: tc.id,
        label: `Case ${index + 1}`,
        inputs,
        output: tc.output_text,
      };
    });
  }

  private buildTestCasesFromExamples(
    examples: {
      id: string;
      example_order: number;
      inputs: unknown;
      output_text: string;
    }[],
  ) {
    return examples.map((example) => {
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
