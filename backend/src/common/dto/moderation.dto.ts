import { IsString, IsOptional, IsBoolean } from 'class-validator';
import { PaginationDto } from './pagination.dto';
import { IsQueryBoolean } from '../decorators/boolean-transform.decorator';

// 标记状态查询
export class FlaggedQueryDto {
  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;
}

// 删除状态查询
export class DeletedQueryDto {
  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;
}

// 组合查询 DTO - 继承 PaginationDto
export class ModeratedQueryDto extends PaginationDto {
  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_flagged?: boolean;

  @IsQueryBoolean()
  @IsBoolean()
  @IsOptional()
  is_deleted?: boolean;
}

// 标记操作 DTO
export class FlagEntityDto {
  @IsString()
  @IsOptional()
  reason?: string;
}
