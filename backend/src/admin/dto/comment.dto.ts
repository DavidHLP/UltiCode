import {
  IsString,
  IsOptional,
  IsBoolean,
  IsInt,
  Min,
  Max,
  IsEnum,
  IsArray,
} from 'class-validator';
import { Type, Transform } from 'class-transformer';

export enum CommentType {
  FORUM = 'forum',
  SOLUTION = 'solution',
}

export class CommentQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsEnum(CommentType)
  @IsOptional()
  type?: CommentType;

  @Transform(({ value }): boolean | string => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value as string;
  })
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @Transform(({ value }): boolean | string => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value as string;
  })
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

export class FlagCommentDto {
  @IsString()
  reason: string;
}

export class BulkCommentActionDto {
  @IsArray()
  @IsString({ each: true })
  ids: string[];

  @IsEnum(CommentType)
  type: CommentType;

  @IsString()
  action: 'delete' | 'unflag';
}
