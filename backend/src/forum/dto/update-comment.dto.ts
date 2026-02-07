import { IsString, IsNotEmpty } from 'class-validator';

export class UpdateForumCommentDto {
  @IsString()
  @IsNotEmpty()
  body: string;
}
