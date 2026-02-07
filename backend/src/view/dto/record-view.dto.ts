import { IsEnum, IsString, IsOptional, IsNotEmpty } from 'class-validator';
import { ViewTargetType } from '@prisma/client';

export class RecordViewDto {
  @IsEnum(ViewTargetType)
  @IsNotEmpty()
  targetType: ViewTargetType;

  @IsString()
  @IsNotEmpty()
  targetId: string;

  @IsOptional()
  @IsString()
  userId?: string;
}
