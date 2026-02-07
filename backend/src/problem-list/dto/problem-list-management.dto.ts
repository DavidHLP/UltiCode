import {
  IsInt,
  IsString,
  IsOptional,
  IsArray,
  IsNotEmpty,
  Min,
} from 'class-validator';
import { Type } from 'class-transformer';

export class AddProblemToListDto {
  @Type(() => Number)
  @IsInt()
  problemId: number;
}

export class BatchAddToListsDto {
  @IsArray()
  @IsString({ each: true })
  listIds: string[];
}

export class SaveListDto {
  @IsOptional()
  @IsString()
  categoryId?: string;
}

export class MoveListToCategoryDto {
  @IsString()
  categoryId: string | null;
}

export class CreateCategoryDto {
  @IsString()
  @IsNotEmpty()
  name: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  sortOrder?: number;
}

export class UpdateCategoryDto {
  @IsOptional()
  @IsString()
  name?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  sortOrder?: number;
}
