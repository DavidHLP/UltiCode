import { IsString, IsNotEmpty, IsOptional } from 'class-validator';

export class CreateForumCommentDto {
  @IsString()
  @IsNotEmpty()
  body: string;

  @IsOptional()
  @IsString()
  parentId?: string | null;
}
