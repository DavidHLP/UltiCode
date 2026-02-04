import { IsString, IsOptional, IsBoolean } from 'class-validator';
import { Transform } from 'class-transformer';
import { PaginationDto } from '../../common/dto/pagination.dto';

export class SolutionQueryDto extends PaginationDto {
  @IsString()
  @IsOptional()
  search?: string;

  @IsString()
  @IsOptional()
  problemId?: string;

  @IsString()
  @IsOptional()
  userId?: string;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_published?: boolean;

  @Transform(({ value }: { value: unknown }) => {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return value;
  })
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;

  @IsString()
  @IsOptional()
  override sortBy?: string = 'created_at';
}

export class FlagSolutionDto {
  @IsString()
  reason?: string;
}

export class BulkSolutionActionDto {
  ids: string[];
  action: 'delete' | 'unflag' | 'publish' | 'unpublish';
}
