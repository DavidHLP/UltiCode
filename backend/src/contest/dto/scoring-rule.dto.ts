import { ApiProperty, ApiPropertyOptional, PartialType } from '@nestjs/swagger';
import { IsString, IsOptional, IsInt, Min, IsBoolean } from 'class-validator';

export class CreateScoringRuleDto {
  @ApiProperty({ description: '规则名称', example: '标准周赛规则' })
  @IsString()
  name: string;

  @ApiPropertyOptional({ description: '规则描述' })
  @IsOptional()
  @IsString()
  description?: string;

  @ApiProperty({ description: '每题基础分', default: 100 })
  @IsInt()
  @Min(0)
  base_score_per_problem: number;

  @ApiProperty({ description: '每分钟时间奖励', default: 1 })
  @IsInt()
  @Min(0)
  time_bonus_per_minute: number;

  @ApiProperty({ description: '错误答案惩罚(秒)', default: 5 })
  @IsInt()
  @Min(0)
  wrong_answer_penalty: number;

  @ApiPropertyOptional({ description: '超时惩罚', default: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  time_limit_penalty?: number;

  @ApiProperty({ description: '首杀奖励', default: 10 })
  @IsInt()
  @Min(0)
  first_solve_bonus: number;

  @ApiPropertyOptional({ description: '满分奖励', default: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  full_score_bonus?: number;

  @ApiPropertyOptional({ description: '是否为默认规则', default: false })
  @IsOptional()
  @IsBoolean()
  is_default?: boolean;
}

export class UpdateScoringRuleDto extends PartialType(CreateScoringRuleDto) {}

export class ScoringRuleResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  name: string;

  @ApiPropertyOptional()
  description?: string;

  @ApiProperty()
  base_score_per_problem: number;

  @ApiProperty()
  time_bonus_per_minute: number;

  @ApiProperty()
  wrong_answer_penalty: number;

  @ApiProperty()
  time_limit_penalty: number;

  @ApiProperty()
  first_solve_bonus: number;

  @ApiProperty()
  full_score_bonus: number;

  @ApiProperty()
  is_default: boolean;

  @ApiProperty()
  is_active: boolean;

  @ApiProperty()
  created_at: Date;

  @ApiProperty()
  updated_at: Date;
}