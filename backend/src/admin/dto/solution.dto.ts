import {
  IsString,
  IsOptional,
  IsBoolean,
  IsInt,
  Min,
  Max,
} from 'class-validator';
import { Type } from 'class-transformer';

export class SolutionQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  problemId?: string;

  @IsString()
  @IsOptional()
  userId?: string;

  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @IsBoolean()
  @IsOptional()
  is_published?: boolean;

  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @IsOptional()
  page?: number = 1;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  @IsOptional()
  limit?: number = 20;

  @IsString()
  @IsOptional()
  sortBy?: string = 'created_at';

  @IsString()
  @IsOptional()
  sortOrder?: 'asc' | 'desc' = 'desc';
}

export class FlagSolutionDto {
  @IsString()
  reason?: string;
}

export class BulkSolutionActionDto {
  ids: string[];
  action: 'delete' | 'unflag' | 'publish' | 'unpublish';
}
