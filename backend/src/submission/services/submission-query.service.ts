import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { Submission, Prisma } from '@prisma/client';
import { I18nService } from '../../i18n/i18n.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  TRANSLATABLE_ENTITIES,
} from '../../i18n/i18n.constants';
import { SUBMISSION_STATUS_DEFINITIONS } from '../submission-statuses';

type ProblemStatusSummary = {
  status: 'solved' | 'attempted' | 'todo';
  completed_time: Date | null;
};

interface TestDetail {
  status?: string;
  time?: number;
  memory?: number;
  detail?: string;
  output?: string;
  expectedOutput?: string;
  inputs?: { id?: string; label?: string; name: string; value: string }[];
}

@Injectable()
export class SubmissionQueryService {
  constructor(
    private readonly prisma: PrismaService,
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

  async findOne(id: string, userId?: string): Promise<Submission> {
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

    if (userId && submission.user_id !== userId) {
      throw new NotFoundException(`Submission with ID ${id} not found`);
    }

    return this.decorateSubmission(submission);
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

  async getSubmissionHistory(userId: string): Promise<{
    monthly: { month: string; count: number; accepted: number }[];
    languages: { language: string; count: number }[];
    totalSubmissions: number;
    totalAccepted: number;
    acceptanceRate: number;
  }> {
    const twelveMonthsAgo = new Date();
    twelveMonthsAgo.setMonth(twelveMonthsAgo.getMonth() - 11);
    twelveMonthsAgo.setDate(1);
    twelveMonthsAgo.setHours(0, 0, 0, 0);

    // Get monthly submission counts
    const monthlySubmissions = await this.prisma.$queryRaw<
      { month: string; count: bigint; accepted: bigint }[]
    >`
      SELECT
        DATE_FORMAT(created_at, '%Y-%m') as month,
        COUNT(*) as count,
        SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) as accepted
      FROM submission
      WHERE user_id = ${userId}
        AND created_at >= ${twelveMonthsAgo}
      GROUP BY DATE_FORMAT(created_at, '%Y-%m')
      ORDER BY month ASC
    `;

    // Get language distribution
    const languageStats = await this.prisma.submission.groupBy({
      by: ['language'],
      where: {
        user_id: userId,
      },
      _count: {
        language: true,
      },
      orderBy: {
        _count: {
          language: 'desc',
        },
      },
      take: 8,
    });

    // Get total counts
    const totalCounts = await this.prisma.submission.aggregate({
      where: {
        user_id: userId,
      },
      _count: {
        _all: true,
      },
    });

    const acceptedCounts = await this.prisma.submission.aggregate({
      where: {
        user_id: userId,
        status: 'Accepted',
      },
      _count: {
        _all: true,
      },
    });

    const totalSubmissions = totalCounts._count._all;
    const totalAccepted = acceptedCounts._count._all;

    // Fill in missing months with zeros
    const months: { month: string; count: number; accepted: number }[] = [];
    const monthlyMap = new Map(
      monthlySubmissions.map((m) => [
        m.month,
        { count: Number(m.count), accepted: Number(m.accepted) },
      ]),
    );

    for (let i = 0; i < 12; i++) {
      const date = new Date();
      date.setMonth(date.getMonth() - i);
      const monthKey = date.toISOString().slice(0, 7);
      const data = monthlyMap.get(monthKey) || { count: 0, accepted: 0 };
      months.unshift({ month: monthKey, ...data });
    }

    return {
      monthly: months,
      languages: languageStats.map((l) => ({
        language: this.formatLanguageName(l.language),
        count: l._count.language,
      })),
      totalSubmissions,
      totalAccepted,
      acceptanceRate:
        totalSubmissions > 0
          ? Math.round((totalAccepted / totalSubmissions) * 100)
          : 0,
    };
  }

  private formatLanguageName(language: string): string {
    const languageNames: Record<string, string> = {
      javascript: 'JavaScript',
      typescript: 'TypeScript',
      python: 'Python',
      java: 'Java',
      cpp: 'C++',
      c: 'C',
      go: 'Go',
      rust: 'Rust',
      csharp: 'C#',
      php: 'PHP',
      ruby: 'Ruby',
      swift: 'Swift',
      kotlin: 'Kotlin',
    };
    return languageNames[language.toLowerCase()] || language;
  }

  async getLearningProgress(userId: string): Promise<{
    weeklyProgress: { week: string; solved: number; timeSpent: number }[];
    difficultyProgress: {
      difficulty: string;
      count: number;
      avgTime: number;
    }[];
    totalProblems: number;
    totalTimeHours: number;
    avgTimePerProblem: number;
    currentStreak: number;
    longestStreak: number;
  }> {
    // Get submissions from the last 12 weeks
    const twelveWeeksAgo = new Date();
    twelveWeeksAgo.setDate(twelveWeeksAgo.getDate() - 84);

    // Get weekly progress
    const weeklyData = await this.prisma.$queryRaw<
      { week: string; solved: bigint; total_time: bigint }[]
    >`
      SELECT
        DATE_FORMAT(created_at, '%Y-%u') as week,
        COUNT(DISTINCT CASE WHEN status = 'Accepted' THEN problem_id END) as solved,
        SUM(COALESCE(runtime, 0)) as total_time
      FROM submission
      WHERE user_id = ${userId}
        AND created_at >= ${twelveWeeksAgo}
      GROUP BY DATE_FORMAT(created_at, '%Y-%u')
      ORDER BY week ASC
    `;

    // Get difficulty progress
    const difficultyData = await this.prisma.$queryRaw<
      { difficulty: string; count: bigint; avg_time: bigint }[]
    >`
      SELECT
        p.difficulty,
        COUNT(DISTINCT s.problem_id) as count,
        AVG(s.runtime) as avg_time
      FROM submission s
      JOIN problem p ON s.problem_id = p.id
      WHERE s.user_id = ${userId}
        AND s.status = 'Accepted'
      GROUP BY p.difficulty
    `;

    // Get total stats
    const totalStats = await this.prisma.submission.aggregate({
      where: {
        user_id: userId,
        status: 'Accepted',
      },
      _count: {
        problem_id: true,
      },
      _sum: {
        runtime: true,
      },
    });

    // Calculate streak
    const streakData = await this.calculateStreak(userId);

    // Fill in missing weeks with zeros
    const weeks: { week: string; solved: number; timeSpent: number }[] = [];
    const weeklyMap = new Map(
      weeklyData.map((w) => [
        w.week,
        { solved: Number(w.solved), timeSpent: Number(w.total_time) / 60000 },
      ]),
    );

    for (let i = 11; i >= 0; i--) {
      const date = new Date();
      date.setDate(date.getDate() - i * 7);
      const weekKey = `${date.getFullYear()}-${String(Math.ceil(date.getDate() / 7)).padStart(2, '0')}`;
      const data = weeklyMap.get(weekKey) || { solved: 0, timeSpent: 0 };
      weeks.push({ week: weekKey, ...data });
    }

    const totalProblems = totalStats._count.problem_id;
    const totalTimeMs = totalStats._sum.runtime || 0;
    const totalTimeHours = totalTimeMs / 3600000;
    const avgTimePerProblem =
      totalProblems > 0 ? totalTimeMs / totalProblems / 60000 : 0;

    return {
      weeklyProgress: weeks,
      difficultyProgress: difficultyData.map((d) => ({
        difficulty: d.difficulty,
        count: Number(d.count),
        avgTime: Number(d.avg_time) / 60000,
      })),
      totalProblems,
      totalTimeHours: Math.round(totalTimeHours * 10) / 10,
      avgTimePerProblem: Math.round(avgTimePerProblem * 10) / 10,
      currentStreak: streakData.current,
      longestStreak: streakData.longest,
    };
  }

  private async calculateStreak(
    userId: string,
  ): Promise<{ current: number; longest: number }> {
    // Get all dates with accepted submissions
    const submissions = await this.prisma.submission.findMany({
      where: {
        user_id: userId,
        status: 'Accepted',
      },
      select: {
        created_at: true,
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    const dates = [
      ...new Set(
        submissions.map((s) => s.created_at.toISOString().split('T')[0]),
      ),
    ].sort((a, b) => b.localeCompare(a));

    if (dates.length === 0) {
      return { current: 0, longest: 0 };
    }

    // Calculate current streak
    let currentStreak = 0;
    const today = new Date().toISOString().split('T')[0];
    const yesterday = new Date(Date.now() - 86400000)
      .toISOString()
      .split('T')[0];

    if (dates[0] === today || dates[0] === yesterday) {
      currentStreak = 1;
      for (let i = 1; i < dates.length; i++) {
        const prevDate = new Date(dates[i - 1]);
        const currDate = new Date(dates[i]);
        const diffDays = Math.floor(
          (prevDate.getTime() - currDate.getTime()) / 86400000,
        );
        if (diffDays === 1) {
          currentStreak++;
        } else {
          break;
        }
      }
    }

    // Calculate longest streak
    let longestStreak = 1;
    let tempStreak = 1;
    for (let i = 1; i < dates.length; i++) {
      const prevDate = new Date(dates[i - 1]);
      const currDate = new Date(dates[i]);
      const diffDays = Math.floor(
        (prevDate.getTime() - currDate.getTime()) / 86400000,
      );
      if (diffDays === 1) {
        tempStreak++;
        longestStreak = Math.max(longestStreak, tempStreak);
      } else {
        tempStreak = 1;
      }
    }

    return { current: currentStreak, longest: longestStreak };
  }

  async getStatusDefinitions(locale: SupportedLocale = DEFAULT_LOCALE) {
    try {
      const statuses = await this.prisma.submissionStatus.findMany({
        orderBy: { sort_order: 'asc' },
      });

      if (statuses.length === 0) {
        return SUBMISSION_STATUS_DEFINITIONS;
      }

      // SubmissionStatus uses 'key' as the ID field, not 'id'
      // Use the original pattern for this special case
      const statusKeys = statuses.map((s) => s.key);
      const translationsMap = await this.i18nService.getBatchTranslations(
        'SUBMISSION_STATUS',
        statusKeys,
        locale,
      );

      return statuses.map((status) => {
        const translations =
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
