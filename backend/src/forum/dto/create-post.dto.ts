import {
  IsArray,
  IsEnum,
  IsNotEmpty,
  IsOptional,
  IsString,
} from 'class-validator';

export class CreatePostDto {
  @IsString()
  @IsNotEmpty()
  title: string;

  @IsString()
  @IsOptional()
  excerpt?: string;

  @IsString()
  @IsOptional()
  body?: string;

  @IsString()
  @IsNotEmpty()
  communityId: string;

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
}
