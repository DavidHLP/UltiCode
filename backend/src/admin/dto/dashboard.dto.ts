import {
  IsOptional,
  IsEnum,
  IsInt,
  Min,
  Max,
  IsDateString,
} from 'class-validator';
import { Type } from 'class-transformer';

export enum ChartPeriod {
  HOUR = 'hour',
  DAY = 'day',
  WEEK = 'week',
  MONTH = 'month',
  YEAR = 'year',
}

export enum ChartMetric {
  USERS = 'users',
  SUBMISSIONS = 'submissions',
  PROBLEMS = 'problems',
  CONTESTS = 'contests',
  SOLUTIONS = 'solutions',
  FORUM_POSTS = 'forum_posts',
}

export class ChartQueryDto {
  @IsEnum(ChartPeriod)
  @IsOptional()
  period?: ChartPeriod = ChartPeriod.DAY;

  @IsEnum(ChartMetric)
  @IsOptional()
  metric?: ChartMetric = ChartMetric.USERS;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(365)
  @IsOptional()
  days?: number = 30;

  @IsDateString()
  @IsOptional()
  startDate?: string;

  @IsDateString()
  @IsOptional()
  endDate?: string;
}

export class DashboardStatsResponse {
  users: {
    total: number;
    active: number;
    activeToday: number;
    activeWeek: number;
    activeMonth: number;
    banned: number;
    byRole: Record<string, number>;
  };
  problems: {
    total: number;
    published: number;
    unpublished: number;
    byDifficulty: Record<string, number>;
    byStatus: Record<string, number>;
  };
  contests: {
    total: number;
    upcoming: number;
    running: number;
    finished: number;
  };
  submissions: {
    total: number;
    today: number;
    week: number;
    month: number;
    acceptanceRate: number;
  };
  solutions: {
    total: number;
    published: number;
    flagged: number;
  };
  forum: {
    posts: number;
    comments: number;
    communities: number;
    flaggedPosts: number;
    flaggedComments: number;
  };
  system: {
    uptime: number;
    version: string;
  };
}
