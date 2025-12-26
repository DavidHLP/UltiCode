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
import { CollectionTargetType } from '@prisma/client';

export class AddItemDto {
  @IsString()
  @IsNotEmpty()
  targetId: string;

  @IsEnum(CollectionTargetType)
  targetType: CollectionTargetType;

  @IsOptional()
  @IsString()
  note?: string;
}

export class UpdateItemDto {
  @IsOptional()
  @IsString()
  note?: string;

  @IsOptional()
  @IsInt()
  @Min(0)
  sortOrder?: number;
}

export class BatchAddItemsDto {
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => AddItemDto)
  items: AddItemDto[];
}

export class ReorderCollectionsDto {
  @IsArray()
  @IsString({ each: true })
  collectionIds: string[];
}
