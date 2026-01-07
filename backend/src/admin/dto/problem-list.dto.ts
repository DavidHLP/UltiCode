import {
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  Min,
  IsArray,
  ValidateNested,
  IsNotEmpty,
} from 'class-validator';
import { Type } from 'class-transformer';

export class CreateProblemListDto {
  @IsString()
  @IsNotEmpty()
  name: string;

  @IsString()
  @IsOptional()
  description?: string;

  @IsString()
  @IsOptional()
  slug?: string;

  @IsBoolean()
  @IsOptional()
  is_public?: boolean;

  @IsBoolean()
  @IsOptional()
  is_featured?: boolean;

  @IsString()
  @IsOptional()
  banner_tag?: string;

  @IsString()
  @IsOptional()
  banner_icon?: string;

  @IsString()
  @IsOptional()
  banner_theme?: string;

  @IsInt()
  @IsOptional()
  banner_order?: number;

  @IsString()
  @IsOptional()
  author_id?: string;
}

export class UpdateProblemListDto {
  @IsString()
  @IsOptional()
  name?: string;

  @IsString()
  @IsOptional()
  description?: string;

  @IsString()
  @IsOptional()
  slug?: string;

  @IsBoolean()
  @IsOptional()
  is_public?: boolean;

  @IsBoolean()
  @IsOptional()
  is_featured?: boolean;

  @IsString()
  @IsOptional()
  banner_tag?: string;

  @IsString()
  @IsOptional()
  banner_icon?: string;

  @IsString()
  @IsOptional()
  banner_theme?: string;

  @IsInt()
  @IsOptional()
  banner_order?: number;
}

export class ProblemListQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsBoolean()
  @IsOptional()
  @Type(() => Boolean)
  is_featured?: boolean;

  @IsBoolean()
  @IsOptional()
  @Type(() => Boolean)
  is_public?: boolean;

  @IsInt()
  @IsOptional()
  @Type(() => Number)
  @Min(1)
  page?: number;

  @IsInt()
  @IsOptional()
  @Type(() => Number)
  @Min(1)
  limit?: number;

  @IsString()
  @IsOptional()
  sortBy?: string;

  @IsString()
  @IsOptional()
  sortOrder?: 'asc' | 'desc';
}

export class UpdateProblemListProblemsDto {
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => ProblemListProblemItemDto)
  problems: ProblemListProblemItemDto[];
}

export class ProblemListProblemItemDto {
  @IsInt()
  problem_id: number;

  @IsInt()
  sort_order: number;
}
