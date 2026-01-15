import { IsOptional, IsString, IsEnum, MaxLength } from 'class-validator';

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
}
