import { IsString, IsOptional, IsBoolean } from 'class-validator';
import { PaginationDto } from '../../common/dto/pagination.dto';
import { IsQueryBoolean } from '../../common/decorators/boolean-transform.decorator';

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

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_published?: boolean;

  @IsQueryBoolean()
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
