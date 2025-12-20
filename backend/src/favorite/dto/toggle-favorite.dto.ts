import { IsEnum, IsNotEmpty, IsString } from 'class-validator';
import { FavoriteTargetType } from '@prisma/client';

export class ToggleFavoriteDto {
  @IsEnum(FavoriteTargetType)
  @IsNotEmpty()
  targetType: FavoriteTargetType;

  @IsString()
  @IsNotEmpty()
  targetId: string;
}
