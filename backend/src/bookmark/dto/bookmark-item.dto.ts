import {
  IsString,
  IsNotEmpty,
  IsOptional,
  IsEnum,
  IsInt,
  Min,
  IsArray,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';
import { BookmarkType } from '@prisma/client';

export class AddBookmarkDto {
  @IsString()
  @IsNotEmpty()
  targetId: string;

  @IsEnum(BookmarkType)
  targetType: BookmarkType;

  @IsOptional()
  @IsString()
  note?: string;
}

export class UpdateBookmarkDto {
  @IsOptional()
  @IsString()
  note?: string;

  @IsOptional()
  @IsInt()
  @Min(0)
  sortOrder?: number;
}

export class BatchAddBookmarksDto {
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => AddBookmarkDto)
  items: AddBookmarkDto[];
}

export class ReorderFoldersDto {
  @IsArray()
  @IsString({ each: true })
  folderIds: string[];
}
