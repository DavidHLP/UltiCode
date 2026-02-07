import { IsString, IsOptional } from 'class-validator';

export class FindCommentsQueryDto {
  @IsOptional()
  @IsString()
  userId?: string;
}
