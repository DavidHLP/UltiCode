import {
  IsArray,
  IsEnum,
  IsNotEmpty,
  IsString,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';
import { TRANSLATABLE_ENTITIES, SUPPORTED_LOCALES } from '../i18n.constants';
import type { TranslatableEntity, SupportedLocale } from '../i18n.constants';

/**
 * Extract entity type values for validation
 */
export const TRANSLATABLE_ENTITY_TYPES = Object.keys(
  TRANSLATABLE_ENTITIES,
) as TranslatableEntity[];

/**
 * DTO for creating a single translation
 */
export class CreateTranslationDto {
  @IsEnum(TRANSLATABLE_ENTITY_TYPES, {
    message: `entityType must be one of: ${TRANSLATABLE_ENTITY_TYPES.join(', ')}`,
  })
  entityType: TranslatableEntity;

  @IsString()
  @IsNotEmpty({ message: 'entityId is required' })
  entityId: string;

  @IsString()
  @IsNotEmpty({ message: 'fieldName is required' })
  fieldName: string;

  @IsEnum(SUPPORTED_LOCALES, {
    message: `locale must be one of: ${SUPPORTED_LOCALES.join(', ')}`,
  })
  locale: SupportedLocale;

  @IsString()
  @IsNotEmpty({ message: 'content is required' })
  content: string;
}

/**
 * DTO for bulk upsert translations
 */
export class BulkUpsertTranslationDto {
  @IsArray({ message: 'translations must be an array' })
  @ValidateNested({ each: true })
  @Type(() => CreateTranslationDto)
  translations: CreateTranslationDto[];
}

/**
 * Options for bulk upsert operation
 */
export interface BulkUpsertOptions {
  /** Whether to skip duplicate translations (default: true) */
  skipDuplicates?: boolean;
  /** Whether to log skipped duplicates (default: true) */
  logSkipped?: boolean;
}

/**
 * Result of bulk upsert operation
 */
export interface BulkUpsertResult {
  /** Number of translations created */
  created: number;
  /** Number of translations skipped (duplicates) */
  skipped: number;
  /** List of duplicate translation identifiers */
  duplicates: string[];
}
