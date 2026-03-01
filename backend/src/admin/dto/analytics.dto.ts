import {
  IsEnum,
  IsOptional,
  IsDateString,
  IsNumber,
  Min,
  Max,
} from 'class-validator';
import { Type } from 'class-transformer';

export enum AnalyticsReportType {
  USER_ACTIVITY = 'user_activity',
  PROBLEM_COMPLETION = 'problem_completion',
  CONTEST_PARTICIPATION = 'contest_participation',
  REVENUE = 'revenue',
  PERFORMANCE = 'performance',
}

export enum AnalyticsPeriod {
  DAY = 'day',
  WEEK = 'week',
  MONTH = 'month',
  QUARTER = 'quarter',
  YEAR = 'year',
}

export class AnalyticsQueryDto {
  @IsEnum(AnalyticsReportType)
  reportType: AnalyticsReportType;

  @IsOptional()
  @IsEnum(AnalyticsPeriod)
  period?: AnalyticsPeriod;

  @IsOptional()
  @IsDateString()
  startDate?: string;

  @IsOptional()
  @IsDateString()
  endDate?: string;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  @Max(365)
  days?: number;
}

export interface UserActivityReport {
  activeUsersDaily: Array<{ date: string; count: number }>;
  activeUsersWeekly: Array<{ week: string; count: number }>;
  averageSessionDuration: number;
  peakActiveHours: Array<{ hour: number; count: number }>;
  userRetention: {
    day1: number;
    day7: number;
    day30: number;
  };
  topActiveUsers: Array<{
    userId: string;
    username: string;
    loginCount: number;
    lastActive: Date;
  }>;
}

export interface ProblemCompletionReport {
  totalAttempts: number;
  successfulAttempts: number;
  overallCompletionRate: number;
  byDifficulty: Array<{
    difficulty: string;
    total: number;
    completed: number;
    rate: number;
  }>;
  byTag: Array<{
    tagId: string;
    label: string;
    total: number;
    completed: number;
    rate: number;
  }>;
  trendingProblems: Array<{
    problemId: string;
    title: string;
    attempts: number;
    completionRate: number;
  }>;
  hardestProblems: Array<{
    problemId: string;
    title: string;
    difficulty: string;
    completionRate: number;
  }>;
}

export interface ContestParticipationReport {
  totalContests: number;
  totalParticipants: number;
  averageParticipantsPerContest: number;
  participationTrend: Array<{
    date: string;
    contests: number;
    participants: number;
  }>;
  byType: Array<{
    type: string;
    count: number;
    avgParticipants: number;
  }>;
  topContests: Array<{
    contestId: string;
    title: string;
    participants: number;
    completionRate: number;
  }>;
  virtualParticipation: {
    total: number;
    averageCompletionRate: number;
  };
}

export interface RevenueReport {
  totalRevenue: number;
  mrr: number;
  arr: number;
  arpu: number;
  subscriberCount: number;
  churnRate: number;
  revenueTrend: Array<{
    date: string;
    revenue: number;
    newSubscribers: number;
    churned: number;
  }>;
  byPlan: Array<{
    plan: string;
    subscribers: number;
    revenue: number;
  }>;
  conversionRate: number;
}

export interface PerformanceReport {
  systemUptime: number;
  averageResponseTime: number;
  errorRate: number;
  throughput: number;
  resourceUsage: {
    cpu: number;
    memory: number;
    disk: number;
  };
  slowestEndpoints: Array<{
    endpoint: string;
    averageTime: number;
    requestCount: number;
  }>;
  errorBreakdown: Array<{
    errorType: string;
    count: number;
    percentage: number;
  }>;
  cacheHitRate: number;
}
