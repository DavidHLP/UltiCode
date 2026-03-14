import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsString,
  IsOptional,
  IsEnum,
  IsNumber,
  IsBoolean,
  IsArray,
  Min,
  MaxLength,
} from 'class-validator';
import { Type } from 'class-transformer';
import { RecommendScenario } from '../interfaces/recommendation.interface';

/**
 * DTO for recommendation request
 */
export class GetRecommendationsDto {
  @ApiProperty({
    description: 'User identifier',
    example: 'user123',
  })
  @IsString()
  @MaxLength(100)
  userId: string;

  @ApiPropertyOptional({
    description: 'Number of recommendations to return',
    default: 10,
    minimum: 1,
    maximum: 50,
  })
  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  size?: number = 10;

  @ApiPropertyOptional({
    description: 'Recommendation scenario',
    enum: RecommendScenario,
    default: RecommendScenario.DAILY,
  })
  @IsOptional()
  @IsEnum(RecommendScenario)
  scenario?: RecommendScenario = RecommendScenario.DAILY;

  @ApiPropertyOptional({
    description:
      'Source problem ID for SIMILAR scenario (required when scenario is SIMILAR)',
    example: 123,
  })
  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  sourceProblemId?: number;

  @ApiPropertyOptional({
    description: 'Target tags for filtering recommendations',
    example: ['array', 'dynamic-programming'],
    type: [String],
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  targetTags?: string[];

  @ApiPropertyOptional({
    description: 'Whether to include already solved problems',
    default: false,
  })
  @IsOptional()
  @Type(() => Boolean)
  @IsBoolean()
  includeSolved?: boolean = false;
}

/**
 * DTO for a single recommendation item in response
 */
export class RecommendItemDto {
  @ApiProperty({
    description: 'Unique identifier of the problem',
    example: 123,
  })
  problemId: number;

  @ApiProperty({
    description: 'URL-friendly slug for the problem',
    example: 'two-sum',
  })
  slug: string;

  @ApiProperty({
    description: 'Display title of the problem',
    example: 'Two Sum',
  })
  title: string;

  @ApiProperty({
    description: 'Difficulty level',
    example: 'Easy',
    enum: ['Easy', 'Medium', 'Hard'],
  })
  difficulty: string;

  @ApiProperty({
    description: 'Recommendation score (0.0 to 1.0)',
    example: 0.95,
  })
  score: number;

  @ApiProperty({
    description: 'Tags associated with the problem',
    example: ['array', 'hash-table'],
    type: [String],
  })
  tags: string[];

  @ApiProperty({
    description: 'Reason for recommendation',
    example: 'Based on your recent practice history',
  })
  reason: string;
}

/**
 * DTO for recommendation result in response
 */
export class RecommendResultDto {
  @ApiProperty({
    description: 'List of recommended items',
    type: [RecommendItemDto],
  })
  items: RecommendItemDto[];

  @ApiProperty({
    description: 'Total count of available recommendations',
    example: 25,
  })
  totalCount: number;

  @ApiProperty({
    description: 'The scenario used',
    enum: RecommendScenario,
  })
  scenario: RecommendScenario;

  @ApiProperty({
    description: 'Timestamp when recommendations were generated',
    example: '2024-01-15T10:30:00',
  })
  generatedAt: string;
}

/**
 * DTO for the full recommendation response
 */
export class RecommendResponseDto {
  @ApiProperty({
    description: 'Whether the request was successful',
    example: true,
  })
  success: boolean;

  @ApiProperty({
    description: 'Response code (0 for success)',
    example: 0,
  })
  code: number;

  @ApiProperty({
    description: 'Human-readable message',
    example: 'Success',
  })
  message: string;

  @ApiProperty({
    description: 'The recommendation result',
    type: RecommendResultDto,
    nullable: true,
  })
  data: RecommendResultDto | null;
}
