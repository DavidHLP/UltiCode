import { IsString, IsOptional, IsIn } from 'class-validator';
import { PaginationDto } from '../../common/dto/pagination.dto';

export enum TagType {
  PROBLEM = 'PROBLEM',
  FORUM = 'FORUM',
}

export class TagQueryDto extends PaginationDto {
  @IsOptional()
  @IsString()
  search?: string;

  @IsOptional()
  @IsIn(['PROBLEM', 'FORUM'])
  type?: TagType;

  @IsOptional()
  @IsString()
  override sortBy?: string = 'usage_count';
}

export class CreateTagDto {
  @IsString()
  name: string;

  @IsOptional()
  @IsString()
  slug?: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsString()
  color?: string;

  @IsString()
  @IsIn(['PROBLEM', 'FORUM'])
  type: TagType;
}

export class UpdateTagDto {
  @IsOptional()
  @IsString()
  name?: string;

  @IsOptional()
  @IsString()
  slug?: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsString()
  color?: string;

  @IsOptional()
  @IsIn(['PROBLEM', 'FORUM'])
  type?: TagType;
}

export class MergeTagDto {
  @IsString()
  targetTagId: string;

  @IsString()
  @IsIn(['PROBLEM', 'FORUM'])
  type: TagType;
}
