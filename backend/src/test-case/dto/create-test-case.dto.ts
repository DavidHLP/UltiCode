import { IsString, IsBoolean, IsOptional, IsInt, IsJSON, Min } from 'class-validator';

export class CreateTestCaseDto {
  @IsBoolean()
  @IsOptional()
  is_sample?: boolean;

  @IsBoolean()
  @IsOptional()
  is_hidden?: boolean;

  @IsInt()
  @Min(0)
  @IsOptional()
  test_order?: number;

  @IsString()
  input_text: string;

  @IsString()
  output_text: string;

  @IsString()
  @IsOptional()
  explanation?: string;

  @IsOptional()
  constraints?: Record<string, unknown>;
}

export class UpdateTestCaseDto {
  @IsBoolean()
  @IsOptional()
  is_sample?: boolean;

  @IsBoolean()
  @IsOptional()
  is_hidden?: boolean;

  @IsInt()
  @Min(0)
  @IsOptional()
  test_order?: number;

  @IsString()
  @IsOptional()
  input_text?: string;

  @IsString()
  @IsOptional()
  output_text?: string;

  @IsString()
  @IsOptional()
  explanation?: string;

  @IsOptional()
  constraints?: Record<string, unknown>;
}

export class BulkImportTestCaseDto {
  @IsString()
  input_text: string;

  @IsString()
  output_text: string;

  @IsBoolean()
  @IsOptional()
  is_sample?: boolean;

  @IsBoolean()
  @IsOptional()
  is_hidden?: boolean;

  @IsString()
  @IsOptional()
  explanation?: string;
}

export class BulkImportTestCasesDto {
  @IsBoolean()
  @IsOptional()
  replace_existing?: boolean;

  test_cases: BulkImportTestCaseDto[];
}

export class TestCaseQueryDto {
  @IsBoolean()
  @IsOptional()
  is_sample?: boolean;

  @IsBoolean()
  @IsOptional()
  is_hidden?: boolean;

  @IsInt()
  @Min(1)
  @IsOptional()
  page?: number = 1;

  @IsInt()
  @Min(1)
  @IsOptional()
  limit?: number = 20;
}
