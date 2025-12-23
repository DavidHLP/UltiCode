import { IsEnum, IsNotEmpty, IsNumber, IsString } from 'class-validator';
import { EdgeOperationTargetType } from '@prisma/client';

export class VoteDto {
  @IsEnum(EdgeOperationTargetType)
  @IsNotEmpty()
  targetType: EdgeOperationTargetType;

  @IsString()
  @IsNotEmpty()
  targetId: string;

  @IsNumber()
  @IsNotEmpty()
  voteType: number; // 1 or -1
}
