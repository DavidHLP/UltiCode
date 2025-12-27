import {
  IsArray,
  IsNotEmpty,
  IsOptional,
  IsString,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';

class RunSubmissionInputDto {
  @IsString()
  @IsNotEmpty()
  name: string;

  @IsString()
  value: string;

  @IsOptional()
  @IsString()
  id?: string;

  @IsOptional()
  @IsString()
  label?: string;
}

class RunSubmissionCaseDto {
  @IsOptional()
  @IsString()
  id?: string;

  @IsOptional()
  @IsString()
  label?: string;

  @IsOptional()
  @IsString()
  output?: string;

  @IsOptional()
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => RunSubmissionInputDto)
  inputs?: RunSubmissionInputDto[];
}

export class RunSubmissionDto {
  @IsString()
  @IsNotEmpty()
  language: string;

  @IsString()
  @IsNotEmpty()
  code: string;

  @IsOptional()
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => RunSubmissionCaseDto)
  testCases?: RunSubmissionCaseDto[];
}
