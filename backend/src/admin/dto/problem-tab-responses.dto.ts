// backend/src/admin/dto/problem-tab-responses.dto.ts
import { ApiProperty } from '@nestjs/swagger';

export class ProblemHeaderResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  title: string;

  @ApiProperty()
  slug: string;

  @ApiProperty({ enum: ['EASY', 'MEDIUM', 'HARD'] })
  difficulty: string;

  @ApiProperty({ enum: ['solved', 'attempted', 'todo'] })
  status: string;

  @ApiProperty()
  is_premium: boolean;

  @ApiProperty()
  is_published: boolean;

  @ApiProperty({ required: false, nullable: true })
  published_at: Date | null;
}

export class ProblemExampleDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  input: string;

  @ApiProperty()
  output: string;

  @ApiProperty({ required: false, nullable: true })
  explanation?: string;

  @ApiProperty()
  order: number;
}

export class ProblemTagDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  label: string;
}

class ProblemDetailForDescription {
  @ApiProperty({ required: false, nullable: true })
  summary?: string;

  @ApiProperty({ required: false, nullable: true })
  content?: string;

  @ApiProperty({ required: false, type: [String] })
  constraints_json?: string[];

  @ApiProperty({ required: false, type: [String] })
  hints?: string[];
}

export class ProblemDescriptionResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  title: string;

  @ApiProperty()
  slug: string;

  @ApiProperty()
  difficulty: string;

  @ApiProperty({ enum: ['solved', 'attempted', 'todo'] })
  status: string;

  @ApiProperty()
  is_premium: boolean;

  @ApiProperty()
  is_published: boolean;

  @ApiProperty({ required: false, nullable: true })
  detail?: ProblemDetailForDescription;

  @ApiProperty({ type: [ProblemTagDto] })
  tags: ProblemTagDto[];

  @ApiProperty({ type: [ProblemExampleDto], required: false })
  examples?: ProblemExampleDto[];

  @ApiProperty()
  created_at: Date;

  @ApiProperty()
  updated_at: Date;

  @ApiProperty({ required: false, nullable: true })
  published_at?: Date;
}

class ProblemLanguageDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  language: string;

  @ApiProperty()
  value: string;

  @ApiProperty({ required: false, nullable: true })
  style?: string;

  @ApiProperty()
  starter_code: string;
}

export class ProblemCodeResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ type: [ProblemLanguageDto], required: false })
  languages?: ProblemLanguageDto[];
}

class ProblemDetailForCases {
  @ApiProperty({ required: false, type: [String] })
  constraints_json?: string[];

  @ApiProperty({ required: false, type: [String] })
  hints?: string[];
}

export class ProblemCasesResponseDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ type: [ProblemExampleDto], required: false })
  examples?: ProblemExampleDto[];

  @ApiProperty({ required: false, nullable: true })
  detail?: ProblemDetailForCases;

  @ApiProperty({ type: [ProblemTagDto], required: false })
  tags?: ProblemTagDto[];
}
