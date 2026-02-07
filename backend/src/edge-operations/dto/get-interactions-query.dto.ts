import { IsString, IsOptional } from 'class-validator';

export class GetInteractionsQueryDto {
  @IsOptional()
  @IsString()
  userId?: string;
}
