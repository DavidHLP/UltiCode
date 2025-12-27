import { IsOptional, IsString } from 'class-validator';

export class CreateSolutionCommentDto {
  @IsString()
  content: string;

  @IsOptional()
  @IsString()
  parentId?: string;
}
