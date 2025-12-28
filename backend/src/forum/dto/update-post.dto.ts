import {
  IsArray,
  IsBoolean,
  IsEnum,
  IsOptional,
  IsString,
} from 'class-validator';

export class UpdatePostDto {
  @IsString()
  @IsOptional()
  title?: string;

  @IsString()
  @IsOptional()
  excerpt?: string;

  @IsString()
  @IsOptional()
  body?: string;

  @IsArray()
  @IsString({ each: true })
  @IsOptional()
  tags?: string[];

  @IsEnum(['announcement', 'discussion', 'showcase', 'question', 'hiring'])
  @IsOptional()
  flairType?: string;

  @IsString()
  @IsOptional()
  flairLabel?: string;

  @IsArray()
  @IsOptional()
  media?: Record<string, unknown>[];

  @IsBoolean()
  @IsOptional()
  isPinned?: boolean;

  @IsBoolean()
  @IsOptional()
  isLocked?: boolean;
}
