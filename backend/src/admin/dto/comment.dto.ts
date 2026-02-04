import {
  IsString,
  IsOptional,
  IsBoolean,
  IsEnum,
  IsArray,
} from 'class-validator';
import { Transform } from 'class-transformer';
import { PaginationDto } from '../../common/dto/pagination.dto';

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
