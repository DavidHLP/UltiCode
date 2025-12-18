import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { Submission, Prisma } from '@prisma/client';

@Injectable()
export class SubmissionService {
  constructor(private prisma: PrismaService) {}

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
    return submissions;
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
      },
      take: 1,
    });

    return submission;
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

    return submission;
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
      inputs?: string;
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
      error_message: null,
    };
  }
}
