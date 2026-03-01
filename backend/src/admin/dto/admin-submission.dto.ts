import {
  IsOptional,
  IsString,
  IsInt,
  Min,
  IsIn,
  IsBoolean,
} from 'class-validator';
import { Type } from 'class-transformer';

export class AdminSubmissionQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  limit?: number = 20;

  @IsOptional()
  @IsString()
  userId?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  problemId?: number;

  @IsOptional()
  @IsString()
  status?: string;

  @IsOptional()
  @IsString()
  language?: string;

  @IsOptional()
  @IsString()
  startDate?: string;

  @IsOptional()
  @IsString()
  endDate?: string;

  @IsOptional()
  @IsString()
  search?: string;

  @IsOptional()
  @IsIn(['created_at', 'runtime', 'memory', 'status'])
  sortBy?: string = 'created_at';

  @IsOptional()
  @IsIn(['asc', 'desc'])
  sortOrder?: 'asc' | 'desc' = 'desc';
}

export class RejudgeSubmissionDto {
  @IsOptional()
  @IsBoolean()
  notifyUser?: boolean = false;
}

export class BatchRejudgeDto {
  @IsString({ each: true })
  ids: string[];

  @IsOptional()
  @IsBoolean()
  notifyUsers?: boolean = false;
}

export interface AdminSubmissionListItem {
  id: string;
  problemId: number;
  problemTitle: string;
  problemSlug: string;
  userId: string;
  username: string;
  language: string;
  status: string;
  runtime: number;
  memory: number;
  createdAt: Date;
  codeLength: number;
}

export interface AdminSubmissionDetail extends AdminSubmissionListItem {
  code: string;
  notes: string | null;
  runtimePercentile: number | null;
  memoryPercentile: number | null;
  testDetails: unknown;
  memoryDistBinsMb: unknown;
  runtimeDistBinsMs: unknown;
}

export interface SubmissionListResponse {
  data: AdminSubmissionListItem[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

export interface RejudgeResult {
  submissionId: string;
  success: boolean;
  oldStatus: string;
  newStatus?: string;
  error?: string;
}

export interface BatchRejudgeResponse {
  results: RejudgeResult[];
  total: number;
  successful: number;
  failed: number;
}
