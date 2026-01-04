import {
  IsString,
  IsOptional,
  IsBoolean,
  IsInt,
  Min,
  Max,
} from 'class-validator';
import { Type } from 'class-transformer';

export class ForumPostQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  communityId?: string;

  @IsString()
  @IsOptional()
  authorId?: string;

  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @IsBoolean()
  @IsOptional()
  is_pinned?: boolean;

  @IsBoolean()
  @IsOptional()
  is_locked?: boolean;

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

export class ForumCommentQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  postId?: string;

  @IsString()
  @IsOptional()
  authorId?: string;

  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

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
}

export class ModerationActionDto {
  @IsString()
  @IsOptional()
  reason?: string;
}

export enum BulkForumAction {
  DELETE = 'delete',
  HIDE = 'hide',
  SHOW = 'show',
  PIN = 'pin',
  UNPIN = 'unpin',
  LOCK = 'lock',
  UNLOCK = 'unlock',
  UNFLAG = 'unflag',
}

export class BulkForumActionDto {
  ids: string[];
  action: BulkForumAction;
}
