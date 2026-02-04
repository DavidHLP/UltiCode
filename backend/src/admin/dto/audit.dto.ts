import { IsString, IsOptional, IsDateString, IsEnum } from 'class-validator';
import {
  PaginationDto,
  PaginationExportDto,
} from '../../common/dto/pagination.dto';

export class AuditLogQueryDto extends PaginationDto {
  @IsString()
  @IsOptional()
  performerId?: string;

  @IsString()
  @IsOptional()
  userId?: string;

  @IsString()
  @IsOptional()
  entityType?: string;

  @IsString()
  @IsOptional()
  entityId?: string;

  @IsString()
  @IsOptional()
  action?: string;

  @IsDateString()
  @IsOptional()
  startDate?: string;

  @IsDateString()
  @IsOptional()
  endDate?: string;

  @IsString()
  @IsOptional()
  override sortBy?: string = 'created_at';
}

export class AuditLogExportDto extends PaginationExportDto {
  @IsString()
  @IsOptional()
  performerId?: string;

  @IsString()
  @IsOptional()
  userId?: string;

  @IsString()
  @IsOptional()
  entityType?: string;

  @IsString()
  @IsOptional()
  entityId?: string;

  @IsString()
  @IsOptional()
  action?: string;

  @IsDateString()
  @IsOptional()
  startDate?: string;

  @IsDateString()
  @IsOptional()
  endDate?: string;

  @IsString()
  @IsOptional()
  override sortBy?: string = 'created_at';

  @IsEnum(['csv', 'json'])
  @IsOptional()
  format?: 'csv' | 'json' = 'csv';
}

export class AuditStatsDto {
  @IsDateString()
  @IsOptional()
  startDate?: string;

  @IsDateString()
  @IsOptional()
  endDate?: string;

  @IsString()
  @IsOptional()
  performerId?: string;
}
