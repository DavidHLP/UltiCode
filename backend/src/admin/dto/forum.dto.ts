import { IsString, IsOptional, IsBoolean } from 'class-validator';
import { Transform } from 'class-transformer';
import { PaginationDto } from '../../common/dto/pagination.dto';

export class ForumPostQueryDto extends PaginationDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  communityId?: string;

  @IsString()
  @IsOptional()
  authorId?: string;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_pinned?: boolean;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_locked?: boolean;

  @IsString()
  @IsOptional()
  override sortBy?: string = 'created_at';
}

export class ForumCommentQueryDto extends PaginationDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  postId?: string;

  @IsString()
  @IsOptional()
  authorId?: string;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;
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
