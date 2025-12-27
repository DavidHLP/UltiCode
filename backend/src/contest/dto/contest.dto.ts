import {
  IsString,
  IsEnum,
  IsDateString,
  IsInt,
  IsBoolean,
  IsOptional,
  IsArray,
  ValidateNested,
  Min,
  Max,
  MaxLength,
} from 'class-validator';
import { Type } from 'class-transformer';
import { ContestType } from '@prisma/client';

export class CreateContestProblemDto {
  @IsInt()
  problem_id: number;

  @IsString()
  @MaxLength(10)
  problem_index: string;

  @IsInt()
  @Min(0)
  score: number;
}

export class CreateContestDto {
  @IsString()
  @MaxLength(120)
  title: string;

  @IsString()
  @MaxLength(120)
  slug: string;

  @IsEnum(ContestType)
  contest_type: ContestType;

  @IsDateString()
  start_time: string;

  @IsInt()
  @Min(1)
  @Max(600)
  duration_minutes: number;

  @IsBoolean()
  is_rated: boolean;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsString()
  @MaxLength(255)
  cover_image?: string;

  @IsOptional()
  @IsString()
  rules?: string;

  @IsOptional()
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CreateContestProblemDto)
  problems?: CreateContestProblemDto[];
}

export class UpdateContestDto {
  @IsOptional()
  @IsString()
  @MaxLength(120)
  title?: string;

  @IsOptional()
  @IsString()
  @MaxLength(120)
  slug?: string;

  @IsOptional()
  @IsEnum(ContestType)
  contest_type?: ContestType;

  @IsOptional()
  @IsDateString()
  start_time?: string;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(600)
  duration_minutes?: number;

  @IsOptional()
  @IsBoolean()
  is_rated?: boolean;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsString()
  @MaxLength(255)
  cover_image?: string;

  @IsOptional()
  @IsString()
  rules?: string;

  @IsOptional()
  @IsBoolean()
  is_visible?: boolean;
}

export class ContestQueryDto {
  @IsOptional()
  @IsString()
  status?: string;

  @IsOptional()
  @IsString()
  type?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  limit?: number = 10;
}
