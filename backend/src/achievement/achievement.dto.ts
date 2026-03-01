import { IsString, IsOptional, IsInt, Min, IsBoolean } from 'class-validator';

export class CreateAchievementDto {
  @IsString()
  key: string;

  @IsString()
  name: string;

  @IsString()
  description: string;

  @IsString()
  @IsOptional()
  icon?: string;

  @IsString()
  category: string;

  @IsInt()
  @Min(1)
  @IsOptional()
  tier?: number;

  criteria: Record<string, unknown>;

  @IsInt()
  @Min(0)
  @IsOptional()
  points?: number;
}

export class UpdateAchievementDto {
  @IsString()
  @IsOptional()
  name?: string;

  @IsString()
  @IsOptional()
  description?: string;

  @IsString()
  @IsOptional()
  icon?: string;

  @IsString()
  @IsOptional()
  category?: string;

  @IsInt()
  @Min(1)
  @IsOptional()
  tier?: number;

  criteria?: Record<string, unknown>;

  @IsInt()
  @Min(0)
  @IsOptional()
  points?: number;

  @IsBoolean()
  @IsOptional()
  is_active?: boolean;
}

export class AchievementQueryDto {
  @IsString()
  @IsOptional()
  category?: string;

  @IsInt()
  @Min(1)
  @IsOptional()
  page?: number = 1;

  @IsInt()
  @Min(1)
  @IsOptional()
  limit?: number = 20;
}

export interface AchievementProgress {
  achievementId: string;
  key: string;
  name: string;
  description: string;
  icon?: string;
  category: string;
  tier: number;
  points: number;
  earned: boolean;
  earnedAt?: Date;
  progress: number;
  target: number;
}
