import { IsEnum, IsNotEmpty, IsNumber, IsString } from 'class-validator';
import { VoteTargetType } from '@prisma/client';

export class VoteDto {
  @IsEnum(VoteTargetType)
  @IsNotEmpty()
  targetType: VoteTargetType;

  @IsString()
  @IsNotEmpty()
  targetId: string;

  @IsNumber()
  @IsNotEmpty()
  voteType: number; // 1 or -1
}
