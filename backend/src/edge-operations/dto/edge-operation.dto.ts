import { IsEnum, IsNotEmpty, IsString } from 'class-validator';
import { EdgeOperationTargetType, EdgeOperationType } from '@prisma/client';

export class EdgeOperationDto {
  @IsEnum(EdgeOperationType)
  @IsNotEmpty()
  operationType: EdgeOperationType;

  @IsEnum(EdgeOperationTargetType)
  @IsNotEmpty()
  targetType: EdgeOperationTargetType;

  @IsString()
  @IsNotEmpty()
  targetId: string;
}
