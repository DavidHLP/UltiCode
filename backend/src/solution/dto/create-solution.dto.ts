import { IsArray, IsOptional, IsString } from 'class-validator';

export class CreateSolutionDto {
  @IsString()
  title: string;

  @IsString()
  content: string;

  @IsString()
  language: string;

  @IsArray()
  @IsString({ each: true })
  @IsOptional()
  tags?: string[];
}
