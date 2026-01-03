import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { Submission, Prisma } from '@prisma/client';
import { JudgeService, JudgeTestCase, JudgeResult } from './judge.service';
import { SUBMISSION_STATUS_DEFINITIONS } from './submission-statuses';
import { InjectQueue } from '@nestjs/bullmq';
import { Queue } from 'bullmq';
import { JudgeJobData } from './judge.processor';
import { v4 as uuid } from 'uuid';
import { I18nService } from '../i18n/i18n.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  TRANSLATABLE_ENTITIES,
} from '../i18n/i18n.constants';

type ProblemStatusSummary = {
  status: 'solved' | 'attempted' | 'todo';
  completed_time: Date | null;
};

@Injectable()
export class SubmissionService {
  constructor(
    private prisma: PrismaService,
    private judgeService: JudgeService,
    @InjectQueue('judge_queue') private judgeQueue: Queue<JudgeJobData>,
    private readonly i18nService: I18nService,
  ) {}

  async findAll(
    problemId?: number | null,
    userId?: string,
    skip: number = 0,
    take: number = 10,
  ): Promise<Submission[]> {
    const whereCondition: Prisma.SubmissionWhereInput = {};

    if (problemId) {
      whereCondition.problem_id = problemId;
    }

    if (userId) {
      whereCondition.user_id = userId;
    }

    const submissions = await this.prisma.submission.findMany({
      where: whereCondition,
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
      orderBy: {
        created_at: 'desc',
      },
      skip,
      take,
    });
    return submissions.map((submission) => this.decorateSubmission(submission));
  }

  async findBest(
    problemId: number,
    userId: string,
  ): Promise<Submission | null> {
    const submission = await this.prisma.submission.findFirst({
      where: {
        problem_id: problemId,
        user_id: userId,
        status: 'Accepted',
      },
      orderBy: [{ runtime: 'asc' }, { memory: 'asc' }, { created_at: 'desc' }],
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
      take: 1,
    });

    return submission ? this.decorateSubmission(submission) : null;
  }

  async getProblemStatusMap(
    userId: string,
    problemIds?: number[],
  ): Promise<Map<number, ProblemStatusSummary>> {
    const whereCondition: Prisma.SubmissionWhereInput = {
      user_id: userId,
    };

    if (problemIds && problemIds.length > 0) {
      whereCondition.problem_id = { in: problemIds };
    }

    const submissions = await this.prisma.submission.findMany({
      where: whereCondition,
      select: {
        problem_id: true,
        status: true,
        created_at: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    const statusMap = new Map<number, ProblemStatusSummary>();

    for (const submission of submissions) {
      const problemId = Number(submission.problem_id);
      const existing = statusMap.get(problemId);
      const isAccepted = submission.status === 'Accepted';

      if (isAccepted) {
        if (!existing || existing.status !== 'solved') {
          statusMap.set(problemId, {
            status: 'solved',
            completed_time: submission.created_at,
          });
        }
        continue;
      }

      if (!existing) {
        statusMap.set(problemId, { status: 'attempted', completed_time: null });
      }
    }

    return statusMap;
  }

  async getDailyActivity(userId: string, year: number): Promise<string[]> {
    const startOfYear = new Date(year, 0, 1);
    const endOfYear = new Date(year + 1, 0, 1);

    const submissions = await this.prisma.submission.findMany({
      where: {
        user_id: userId,
        status: 'Accepted',
        created_at: {
          gte: startOfYear,
          lt: endOfYear,
        },
      },
      select: {
        created_at: true,
      },
    });

    const activeDates = new Set<string>();
    submissions.forEach((sub) => {
      const date = sub.created_at.toISOString().split('T')[0];
      activeDates.add(date);
    });

    return Array.from(activeDates);
  }

  async getStatusDefinitions(locale: SupportedLocale = DEFAULT_LOCALE) {
    try {
      const statuses = await this.prisma.submissionStatus.findMany({
        orderBy: { sort_order: 'asc' },
      });

      if (statuses.length === 0) {
        return SUBMISSION_STATUS_DEFINITIONS;
      }

      // Apply translations to each status
      const statusIds = statuses.map((s) => s.key);
      const translationsMap = await this.i18nService.getBatchTranslations(
        'SUBMISSION_STATUS',
        statusIds,
        locale,
      );

      return statuses.map((status) => {
        const translations: Map<string, string> =
          translationsMap.get(status.key) ?? new Map<string, string>();
        return this.i18nService.applyTranslations(
          status,
          translations,
          TRANSLATABLE_ENTITIES.SUBMISSION_STATUS.fields,
        );
      });
    } catch (error) {
      const err = error as { code?: string; message?: string };
      if (err?.code === 'P2021') {
        return SUBMISSION_STATUS_DEFINITIONS;
      }
      throw error;
    }
  }

  async findOne(id: string): Promise<Submission> {
    const submission = await this.prisma.submission.findUnique({
      where: { id },
      include: {
        user: {
          select: {
            id: true,
            username: true,
            avatar: true,
          },
        },
      },
    });

    if (!submission) {
      throw new NotFoundException(`Submission with ID ${id} not found`);
    }

    return this.decorateSubmission(submission);
  }

  async getLatestRunResult(problemId: number, userId?: string) {
    const submission = await this.prisma.submission.findFirst({
      where: {
        problem_id: problemId,
        ...(userId ? { user_id: userId } : {}),
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    if (!submission) {
      return null;
    }

    interface TestDetail {
      status?: string;
      time?: number;
      memory?: number;
      detail?: string;
      output?: string;
      expectedOutput?: string;
      inputs?: { id?: string; label?: string; name: string; value: string }[];
    }

    const testDetails = (Array.isArray(submission.test_details)
      ? submission.test_details
      : []) as unknown as TestDetail[];

    const cases = testDetails.map((detail, index) => ({
      id: `case-${index + 1}`,
      runId: `run-${submission.id}`,
      submissionTestId: `${submission.id}-${index + 1}`,
      testCaseId: `${problemId}-${index + 1}`,
      caseLabel: `Case ${index + 1}`,
      status: detail.status ?? submission.status,
      runtime:
        detail.time !== undefined
          ? `${detail.time} ms`
          : `${submission.runtime} ms`,
      memory:
        detail.memory !== undefined
          ? `${detail.memory} MB`
          : `${submission.memory} MB`,
      detail: detail.detail,
      output: detail.output,
      expectedOutput: detail.expectedOutput,
      inputs: detail.inputs,
    }));

    const passedCases = cases.filter(
      (item) => item.status === 'Accepted',
    ).length;

    return {
      id: `run-${submission.id}`,
      submissionId: submission.id,
      problemId,
      userId: submission.user_id,
      verdict: submission.status,
      runtime: `${submission.runtime} ms`,
      memory: `${submission.memory} MB`,
      cases,
      passed_cases: passedCases,
      total_cases: cases.length,
      error_message:
        submission.status === 'Compile Error'
          ? (testDetails[0]?.detail ?? null)
          : null,
    };
  }

  async create(
    userId: string,
    problemId: number,
    data: { language: string; code: string },
  ): Promise<Submission> {
    const newSubmissionId = uuid();
    const created = await this.prisma.submission.create({
      data: {
        id: newSubmissionId,
        user_id: userId,
        problem_id: problemId,
        language: data.language,
        code: data.code,
        status: 'Pending', // Initial status
        runtime: 0,
        memory: 0,
        runtime_percentile: null,
        memory_percentile: null,
        test_details: Prisma.DbNull,
      },
    });

    // Add job to the queue for judging
    await this.judgeQueue.add('judge', { submissionId: newSubmissionId });

    return this.decorateSubmission(created);
  }

  async updateSubmissionAfterJudging(
    submissionId: string,
    judgeResult: JudgeResult,
  ): Promise<Submission> {
    const testDetailsJson =
      judgeResult.cases as unknown as Prisma.InputJsonValue;

    const updated = await this.prisma.submission.update({
      where: { id: submissionId },
      data: {
        status: judgeResult.verdict,
        runtime: judgeResult.runtime,
        memory: judgeResult.memory,
        test_details: testDetailsJson,
      },
      include: {
        problem: {
          select: {
            id: true,
            title: true,
            slug: true,
          },
        },
      },
    });
    return this.decorateSubmission(updated);
  }

  async run(
    problemId: number,
    data: {
      language: string;
      code: string;
      testCases?: JudgeTestCase[];
    },
    userId?: string,
  ) {
    const testCases =
      data.testCases && data.testCases.length > 0
        ? this.normalizeTestCases(data.testCases)
        : await this.buildTestCasesFromExamples(problemId);
    const judgeResult = this.judgeService.judge(
      data.language,
      data.code,
      testCases,
    );

    const runId = `run-${problemId}-${Date.now()}`;
    const cases = judgeResult.cases.map((detail, index) => ({
      id: `${runId}-case-${index + 1}`,
      runId,
      submissionTestId: `${runId}-test-${index + 1}`,
      testCaseId: testCases[index]?.id ?? `${problemId}-${index + 1}`,
      caseLabel: testCases[index]?.label ?? `Case ${index + 1}`,
      status: detail.status,
      runtime: `${detail.time} ms`,
      memory: `${detail.memory} MB`,
      detail: detail.detail,
      output: detail.output,
      expectedOutput: detail.expectedOutput,
      inputs: detail.inputs ?? [],
    }));

    return {
      id: runId,
      submissionId: runId,
      problemId,
      userId: userId ?? 'anonymous',
      verdict: judgeResult.compileError ? 'Compile Error' : judgeResult.verdict,
      runtime: `${judgeResult.runtime} ms`,
      memory: `${judgeResult.memory} MB`,
      cases,
      passed_cases: cases.filter((item) => item.status === 'Accepted').length,
      total_cases: cases.length,
      error_message: judgeResult.compileError ?? null,
    };
  }

  private normalizeTestCases(testCases: JudgeTestCase[]): JudgeTestCase[] {
    return testCases.map((testCase, index) => {
      const caseId = testCase.id || `case-${index + 1}`;
      const inputs = Array.isArray(testCase.inputs) ? testCase.inputs : [];
      return {
        id: caseId,
        label: testCase.label || `Case ${index + 1}`,
        inputs: inputs.map((input, inputIndex) => ({
          id: input.id ?? `${caseId}-input-${inputIndex}`,
          name: input.name,
          value: input.value,
          label: input.label ?? input.name,
        })),
        output: testCase.output ?? '',
      };
    });
  }

  private async buildTestCasesFromExamples(
    problemId: number,
  ): Promise<JudgeTestCase[]> {
    const examples = await this.prisma.problemExample.findMany({
      where: { problem_id: problemId },
      orderBy: { example_order: 'asc' },
    });

    return examples.map((example, index) => {
      const inputs = Array.isArray(example.inputs)
        ? (example.inputs as { name: string; value: string }[])
        : [];
      return {
        id: example.id,
        label: `Case ${index + 1}`,
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

  decorateSubmission<T extends Submission & { test_details?: unknown }>(
    submission: T,
  ) {
    const testDetails = Array.isArray(submission.test_details)
      ? (submission.test_details as Array<Record<string, unknown>>)
      : [];
    const tests = testDetails.map((detail, index) => ({
      id: `test-${submission.id}-${index + 1}`,
      status: (detail.status as string) ?? submission.status,
      runtime:
        typeof detail.time === 'number' ? detail.time : submission.runtime,
      memory:
        typeof detail.memory === 'number' ? detail.memory : submission.memory,
    }));

    const failureDetail = testDetails.find(
      (detail) => detail.status && detail.status !== 'Accepted',
    );
    const compileError =
      failureDetail?.status === 'Compile Error'
        ? (failureDetail.detail as string | undefined)
        : undefined;
    const errorDetail = failureDetail?.detail as string | undefined;
    const failureInputs = Array.isArray(failureDetail?.inputs)
      ? (failureDetail?.inputs as Array<{
          name: string;
          value: string;
        }>)
      : [];

    const formattedInput =
      failureInputs.length > 0
        ? failureInputs
            .map((input) => `${input.name} = ${input.value}`)
            .join(', ')
        : undefined;

    return {
      ...submission,
      tests,
      compiler_error: compileError,
      error_detail: errorDetail,
      input: formattedInput,
      output: failureDetail?.output as string | undefined,
      expected_output: failureDetail?.expectedOutput as string | undefined,
    };
  }
}
