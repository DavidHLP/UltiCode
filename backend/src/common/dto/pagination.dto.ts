import { IsString, IsOptional, IsInt, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';
import type { PaginationParams, SortParams } from '@ulticode/shared-types';

/**
 * Base pagination DTO for query parameters
 * Provides standard pagination fields with validation
 * Extends shared PaginationParams and SortParams for type consistency
 */
export class PaginationDto implements PaginationParams, SortParams {
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

  @IsString()
  @IsOptional()
  sortBy?: string;

  @IsString()
  @IsOptional()
  sortOrder?: 'asc' | 'desc' = 'desc';
}

/**
 * Extended pagination DTO for export operations
 * Allows higher limit values (up to 500) for data export
 */
export class PaginationExportDto extends PaginationDto {
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(500)
  @IsOptional()
  override limit?: number = 50;
}

/**
 * Standard paginated result interface
 * Used to return paginated data with metadata
 * Note: Backend uses 'items' while shared types use 'data'
 * This keeps backward compatibility within the backend
 */
export interface PaginatedResult<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

// Re-export shared types for convenience
export type { PaginationParams, SortParams } from '@ulticode/shared-types';
