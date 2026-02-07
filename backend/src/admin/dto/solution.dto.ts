import { IsString, IsOptional, IsBoolean } from 'class-validator';
import { ModeratedQueryDto } from '../../common/dto/moderation.dto';
import { IsQueryBoolean } from '../../common/decorators/boolean-transform.decorator';

export class SolutionQueryDto extends ModeratedQueryDto {
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
  is_published?: boolean;

  @IsString()
  @IsOptional()
  sortBy?: string = 'created_at';
}

export class FlagSolutionDto {
  @IsString()
  reason?: string;
}

export class BulkSolutionActionDto {
  ids: string[];
  action: 'delete' | 'unflag' | 'publish' | 'unpublish';
}
