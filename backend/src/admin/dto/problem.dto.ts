import {
  IsString,
  IsOptional,
  IsEnum,
  IsBoolean,
  IsInt,
  IsArray,
  Min,
  Max,
  MaxLength,
  ValidateNested,
} from 'class-validator';
import { Type, Transform } from 'class-transformer';

export enum Difficulty {
  EASY = 'EASY',
  MEDIUM = 'MEDIUM',
  HARD = 'HARD',
}

export enum ProblemStatus {
  SOLVED = 'solved',
  ATTEMPTED = 'attempted',
  TODO = 'todo',
}

export enum ExportFormat {
  JSON = 'json',
  CSV = 'csv',
}

export class CreateProblemDto {
  @IsString()
  @MaxLength(120)
  slug: string;

  @IsString()
  @MaxLength(255)
  title: string;

  @IsEnum(Difficulty)
  difficulty: Difficulty;

  @IsEnum(ProblemStatus)
  @IsOptional()
  status?: ProblemStatus = ProblemStatus.TODO;

  @IsBoolean()
  @IsOptional()
  is_premium?: boolean = false;

  @IsBoolean()
  @IsOptional()
  is_published?: boolean = false;

  @IsString()
  @MaxLength(40)
  @IsOptional()
  published_by?: string;

  @IsString()
  @IsOptional()
  summary?: string;

  @IsString()
  @IsOptional()
  content?: string;

  @IsArray()
  @IsOptional()
  examples?: Array<{
    id?: string;
    input: string;
    output: string;
    explanation?: string;
    order?: number;
  }>;

  @IsArray()
  @IsOptional()
  constraints?: string[];

  @IsArray()
  @IsOptional()
  hints?: string[];

  @IsArray()
  @IsOptional()
  languages?: string[];

  @IsArray()
  @IsOptional()
  tags?: string[];
}

export class UpdateProblemDto {
  @IsString()
  @MaxLength(120)
  @IsOptional()
  slug?: string;

  @IsString()
  @MaxLength(255)
  @IsOptional()
  title?: string;

  @IsEnum(Difficulty)
  @IsOptional()
  difficulty?: Difficulty;

  @IsEnum(ProblemStatus)
  @IsOptional()
  status?: ProblemStatus;

  @IsBoolean()
  @IsOptional()
  is_premium?: boolean;

  @IsBoolean()
  @IsOptional()
  has_solution?: boolean;

  @IsString()
  @IsOptional()
  summary?: string;

  @IsString()
  @IsOptional()
  content?: string;

  @IsArray()
  @IsOptional()
  examples?: Array<{
    id?: string;
    input: string;
    output: string;
    explanation?: string;
    order?: number;
  }>;

  @IsArray()
  @IsOptional()
  constraints?: string[];

  @IsArray()
  @IsOptional()
  hints?: string[];

  @IsArray()
  @IsOptional()
  languages?: string[];

  @IsArray()
  @IsOptional()
  tags?: string[];
}

export class PublishProblemDto {
  @IsBoolean()
  is_published: true;
}

export class ProblemQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsEnum(Difficulty)
  @IsOptional()
  difficulty?: Difficulty;

  @IsEnum(ProblemStatus)
  @IsOptional()
  status?: ProblemStatus;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_published?: boolean;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;

  @IsString()
  @IsOptional()
  tag?: string;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @IsOptional()
  page?: number = 1;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  @IsOptional()
  limit?: number = 20;

  @IsString()
  @IsOptional()
  sortBy?: string = 'id';

  @IsString()
  @IsOptional()
  sortOrder?: 'asc' | 'desc' = 'desc';
}

export class ExportProblemsQueryDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsEnum(Difficulty)
  @IsOptional()
  difficulty?: Difficulty;

  @IsEnum(ProblemStatus)
  @IsOptional()
  status?: ProblemStatus;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_published?: boolean;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;

  @IsString()
  @IsOptional()
  tag?: string;

  @IsEnum(ExportFormat, { message: 'format must be either json or csv' })
  @IsOptional()
  format?: ExportFormat;
}

export class BulkProblemActionDto {
  @IsArray()
  @IsString({ each: true })
  ids: string[];

  @IsEnum(['publish', 'unpublish', 'delete', 'restore'])
  action: 'publish' | 'unpublish' | 'delete' | 'restore';
}

export class ImportProblemDto {
  @IsString()
  @MaxLength(120)
  slug: string;

  @IsString()
  @MaxLength(255)
  title: string;

  @IsEnum(Difficulty)
  difficulty: Difficulty;

  @IsEnum(ProblemStatus)
  @IsOptional()
  status?: ProblemStatus = ProblemStatus.TODO;

  @IsBoolean()
  @IsOptional()
  is_premium?: boolean = false;

  @IsBoolean()
  @IsOptional()
  has_solution?: boolean = false;

  @IsBoolean()
  @IsOptional()
  is_published?: boolean = false;

  @IsString()
  @IsOptional()
  summary?: string;

  @IsArray()
  @IsOptional()
  examples?: Array<{
    id?: string;
    input: string;
    output: string;
    explanation?: string;
    order?: number;
  }>;

  @IsArray()
  @IsOptional()
  constraints?: string[];

  @IsArray()
  @IsOptional()
  hints?: string[];

  @IsArray()
  @IsOptional()
  languages?: Array<{
    label: string;
    value: string;
    starter_code: string;
  }>;

  @IsArray()
  @IsOptional()
  tags?: string[];
}

export class ImportProblemsDto {
  @IsArray()
  @ValidateNested({ each: true })
  problems: ImportProblemDto[];

  @IsEnum(['skip', 'update', 'create_new'])
  @IsOptional()
  onConflict?: 'skip' | 'update' | 'create_new' = 'skip';
}
