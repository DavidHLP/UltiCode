import { IsEnum, IsString, IsNotEmpty } from 'class-validator';
import { BookmarkType } from '@prisma/client';

export class QuickFavoriteDto {
  @IsEnum(BookmarkType)
  targetType: BookmarkType;

  @IsString()
  @IsNotEmpty()
  targetId: string;
}
