import {
  IsOptional,
  IsString,
  IsEnum,
  MaxLength,
  IsInt,
  Min,
  Max,
} from 'class-validator';
import { Type } from 'class-transformer';

export class FindAllProblemsQueryDto {
  @IsOptional()
  @IsString()
  userId?: string;

  @IsOptional()
  @IsEnum(['algorithms', 'database', 'shell', 'concurrency', 'all'], {
    message:
      'category must be one of: algorithms, database, shell, concurrency, all',
  })
  category?: string;

  @IsOptional()
  @IsEnum(['Easy', 'Medium', 'Hard'], {
    message: 'difficulty must be one of: Easy, Medium, Hard',
  })
  difficulty?: string;

  @IsOptional()
  @IsString()
  @MaxLength(100)
  search?: string;

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
  limit?: number = 20;
}
