import { IsOptional, IsBoolean, IsString } from 'class-validator';
import { Type } from 'class-transformer';
import {
  PaginationDto,
  PaginatedResult as SharedPaginatedResult,
} from '../../common/dto/pagination.dto';

// Re-export PaginatedResult for backwards compatibility
export type PaginatedResult<T> = SharedPaginatedResult<T>;

export class ContestRankingQueryDto extends PaginationDto {
  @IsOptional()
  @Type(() => Boolean)
  @IsBoolean()
  include_virtual?: boolean = true;
}

export class GlobalRankingQueryDto extends PaginationDto {
  @IsOptional()
  @IsString()
  country?: string;
}

export interface ContestRankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar: string | null;
  totalScore: number;
  totalPenalty: number;
  finishTime: number | null;
  finish_time?: number | null;
  totalAttempts: number;
  total_attempts?: number;
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
  wrongAttempts: number;
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
