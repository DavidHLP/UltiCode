import { IsString, IsOptional, IsIn } from 'class-validator';
import { Transform } from 'class-transformer';

export enum TagType {
  PROBLEM = 'PROBLEM',
  FORUM = 'FORUM',
}

export class TagQueryDto {
  @IsOptional()
  @IsString()
  search?: string;

  @IsOptional()
  @IsIn(['PROBLEM', 'FORUM'])
  type?: TagType;

  @IsOptional()
  @Transform(({ value }) => parseInt(value))
  page?: number = 1;

  @IsOptional()
  @Transform(({ value }) => parseInt(value))
  limit?: number = 20;

  @IsOptional()
  @IsString()
  sortBy?: string = 'usage_count';

  @IsOptional()
  @IsIn(['asc', 'desc'])
  sortOrder?: 'asc' | 'desc' = 'desc';
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
