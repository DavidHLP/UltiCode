import {
  IsString,
  IsOptional,
  IsEnum,
  IsBoolean,
  MaxLength,
  IsDateString,
  IsArray,
  IsInt,
  Min,
  Max,
} from 'class-validator';
import { Type } from 'class-transformer';
import { PaginationDto } from '../../common/dto/pagination.dto';

export enum ContestType {
  PUBLIC = 'PUBLIC',
  PRIVATE = 'PRIVATE',
  VIRTUAL = 'VIRTUAL',
}

export class CreateContestDto {
  @IsString()
  @MaxLength(120)
  slug: string;

  @IsString()
  @MaxLength(255)
  title: string;

  @IsString()
  @MaxLength(500)
  description?: string;

  @IsEnum(ContestType)
  type: ContestType;

  @IsDateString()
  start_time: string;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  duration: number;

  @IsBoolean()
  @IsOptional()
  is_published?: boolean = false;

  @IsArray()
  @IsString({ each: true })
  @IsOptional()
  problem_ids?: string[];
}

export class UpdateContestDto {
  @IsString()
  @MaxLength(120)
  @IsOptional()
  slug?: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  title?: string;

  @IsString()
  @MaxLength(500)
  @IsOptional()
  description?: string;

  @IsEnum(ContestType)
  @IsOptional()
  type?: ContestType;

  @IsDateString()
  @IsOptional()
  start_time?: string;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @IsOptional()
  duration?: number;

  @IsBoolean()
  @IsOptional()
  is_published?: boolean;
}

export class ContestProblemDto {
  @IsString()
  problem_id: string;

  @Type(() => Number)
  @IsInt()
  @Min(0)
  @Max(1000)
  score?: number;
}

export class ContestQueryDto extends PaginationDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsEnum(ContestType)
  @IsOptional()
  type?: ContestType;

  @IsString()
  @IsOptional()
  status?: 'upcoming' | 'running' | 'finished';

  @IsString()
  @IsOptional()
  override sortBy?: string = 'start_time';
}

export class BulkContestActionDto {
  @IsArray()
  @IsString({ each: true })
  ids: string[];

  @IsEnum(['delete', 'publish', 'unpublish', 'start', 'end'])
  action: 'delete' | 'publish' | 'unpublish' | 'start' | 'end';
}
