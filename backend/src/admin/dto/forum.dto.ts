import { IsString, IsOptional, IsBoolean } from 'class-validator';
import { ModeratedQueryDto } from '../../common/dto/moderation.dto';
import { IsQueryBoolean } from '../../common/decorators/boolean-transform.decorator';

export class ForumPostQueryDto extends ModeratedQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  communityId?: string;

  @IsString()
  @IsOptional()
  authorId?: string;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_pinned?: boolean;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_locked?: boolean;

  @IsString()
  @IsOptional()
  sortBy?: string = 'created_at';
}

export class ForumCommentQueryDto extends ModeratedQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  postId?: string;

  @IsString()
  @IsOptional()
  authorId?: string;
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
