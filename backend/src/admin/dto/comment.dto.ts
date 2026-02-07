import { IsString, IsOptional, IsEnum, IsArray } from 'class-validator';
import { ModeratedQueryDto } from '../../common/dto/moderation.dto';

export enum CommentType {
  FORUM = 'forum',
  SOLUTION = 'solution',
}

export class CommentQueryDto extends ModeratedQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsEnum(CommentType)
  @IsOptional()
  type?: CommentType;

  @IsString()
  @IsOptional()
  sortBy?: string = 'created_at';
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
