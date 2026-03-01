import {
  IsString,
  IsOptional,
  IsInt,
  Min,
  Max,
  MaxLength,
} from 'class-validator';
import { Type } from 'class-transformer';

export class VersionQueryDto {
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @IsOptional()
  page?: number = 1;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  @IsOptional()
  limit?: number = 20;
}

export class RollbackVersionDto {
  @IsString()
  @IsOptional()
  @MaxLength(500)
  reason?: string;
}

export class CreateVersionNoteDto {
  @IsString()
  @MaxLength(500)
  note: string;
}
