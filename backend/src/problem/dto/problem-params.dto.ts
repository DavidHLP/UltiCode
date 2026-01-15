import { IsOptional, IsString } from 'class-validator';

export class ProblemParamsDto {
  @IsOptional()
  @IsString()
  userId?: string;
}
