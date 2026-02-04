import {
  IsString,
  IsOptional,
  IsBoolean,
  IsEnum,
  IsArray,
} from 'class-validator';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { IsQueryBoolean } from '../../common/decorators/boolean-transform.decorator';

export enum CommentType {
  FORUM = 'forum',
  SOLUTION = 'solution',
}

export class CommentQueryDto extends PaginationDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsEnum(CommentType)
  @IsOptional()
  type?: CommentType;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;

  @IsString()
  @IsOptional()
  override sortBy?: string = 'created_at';
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
