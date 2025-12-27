import {
  IsOptional,
  IsBoolean,
  IsInt,
  IsString,
  Min,
  Max,
} from 'class-validator';
import { Type } from 'class-transformer';

export class ContestRankingQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  limit?: number = 50;

  @IsOptional()
  @Type(() => Boolean)
  @IsBoolean()
  include_virtual?: boolean = true;
}

export class GlobalRankingQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  limit?: number = 50;

  @IsOptional()
  @IsString()
  country?: string;
}

export interface PaginatedResult<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

export interface ContestRankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar: string | null;
  totalScore: number;
  totalPenalty: number;
  solvedCount: number;
  ratingBefore: number;
  ratingAfter: number;
  ratingChange: number;
  isVirtual: boolean;
  problemResults: ProblemResultEntry[];
}

export interface ProblemResultEntry {
  problemIndex: string;
  problemId: number;
  isSolved: boolean;
  score: number;
  attempts: number;
  solveTime: number | null;
  penaltyTime: number;
}

export interface GlobalRankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar: string | null;
  country: string | null;
  rating: number;
  maxRating: number;
  ratingTitle: string;
  maxRatingTitle: string;
  contestsAttended: number;
  badge: string | null;
}
