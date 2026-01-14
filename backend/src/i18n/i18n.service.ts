import { Injectable, Logger, ConflictException } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
  TranslatableEntity,
  TRANSLATABLE_ENTITIES,
  matchSupportedLocale,
} from './i18n.constants';
import { Prisma } from '@prisma/client';
import { BulkUpsertOptions, BulkUpsertResult } from './dto/translation.dto';

@Injectable()
export class I18nService {
  private readonly logger = new Logger(I18nService.name);

  constructor(private prisma: PrismaService) {}

  /**
   * Parse Accept-Language header and return best matching locale
   * @param header Accept-Language header value
   * @returns Best matching supported locale
   */
  parseAcceptLanguage(header: string | undefined): SupportedLocale {
    if (!header) return DEFAULT_LOCALE;

    const languages = header.split(',').map((lang) => {
      const [code, qValue] = lang.trim().split(';q=');
      return {
        code: code.trim(),
        quality: qValue ? parseFloat(qValue) : 1.0,
      };
    });

    // Sort by quality (highest first)
    languages.sort((a, b) => b.quality - a.quality);

    for (const { code } of languages) {
      const matched = matchSupportedLocale(code);
      if (matched) return matched;
    }

    return DEFAULT_LOCALE;
  }

  /**
   * Get translations for a single entity with fallback
   * @param entityType Type of entity (PROBLEM, CONTEST, etc.)
   * @param entityId Entity ID
   * @param locale Target locale
   * @returns Map of field names to translated content
   */
  async getTranslations(
    entityType: TranslatableEntity,
    entityId: string | number | bigint,
    locale: SupportedLocale,
  ): Promise<Map<string, string>> {
    const id = String(entityId);

    const translations = await this.prisma.translation.findMany({
      where: {
        entity_type: entityType,
        entity_id: id,
        locale,
      },
      select: { field_name: true, content: true },
    });

    const result = new Map<string, string>();
    translations.forEach((t) => result.set(t.field_name, t.content));

    // Fallback for missing fields
    if (locale !== FALLBACK_LOCALE) {
      const entityConfig = TRANSLATABLE_ENTITIES[entityType];
      const missingFields = entityConfig.fields.filter((f) => !result.has(f));

      if (missingFields.length > 0) {
        const fallbackTranslations = await this.prisma.translation.findMany({
          where: {
            entity_type: entityType,
            entity_id: id,
            locale: FALLBACK_LOCALE,
            field_name: { in: [...missingFields] },
          },
        });
        fallbackTranslations.forEach((t) => {
          if (!result.has(t.field_name)) {
            result.set(t.field_name, t.content);
          }
        });
      }
    }

    return result;
  }

  /**
   * Get translations for multiple entities (batch) - optimized for lists
   * @param entityType Type of entity
   * @param entityIds Array of entity IDs
   * @param locale Target locale
   * @returns Map of entity ID to field translations
   */
  async getBatchTranslations(
    entityType: TranslatableEntity,
    entityIds: (string | number | bigint)[],
    locale: SupportedLocale,
  ): Promise<Map<string, Map<string, string>>> {
    if (entityIds.length === 0) {
      return new Map();
    }

    const ids = entityIds.map(String);

    // Fetch translations for both target locale and fallback in single query
    const translations = await this.prisma.translation.findMany({
      where: {
        entity_type: entityType,
        entity_id: { in: ids },
        locale: { in: [locale, FALLBACK_LOCALE] },
      },
    });

    // Initialize result map with empty maps for each entity
    const result = new Map<string, Map<string, string>>();
    for (const id of ids) {
      result.set(id, new Map());
    }

    // First pass: add fallback translations
    translations
      .filter((t) => t.locale === FALLBACK_LOCALE)
      .forEach((t) => {
        const entityMap = result.get(t.entity_id);
        if (entityMap && !entityMap.has(t.field_name)) {
          entityMap.set(t.field_name, t.content);
        }
      });

    // Second pass: override with requested locale (if available)
    translations
      .filter((t) => t.locale === locale)
      .forEach((t) => {
        const entityMap = result.get(t.entity_id);
        if (entityMap) {
          entityMap.set(t.field_name, t.content);
        }
      });

    return result;
  }

  /**
   * Apply translations to an entity object
   * @param entity Original entity object
   * @param translations Map of field names to translated content
   * @param fields List of fields to translate
   * @returns Entity with translated fields
   */
  applyTranslations<T extends object>(
    entity: T,
    translations: Map<string, string>,
    fields: readonly string[],
  ): T {
    const updates: Record<string, string> = {};
    for (const field of fields) {
      const translation = translations.get(field);
      if (translation !== undefined && field in entity) {
        updates[field] = translation;
      }
    }
    return Object.assign({}, entity, updates);
  }

  /**
   * Validate field name against translatable entity configuration
   * @param entityType Type of entity
   * @param fieldName Field name to validate
   * @returns True if field is valid for the entity type
   */
  private isValidFieldName(
    entityType: TranslatableEntity,
    fieldName: string,
  ): boolean {
    const entityConfig = TRANSLATABLE_ENTITIES[entityType];
    const fields = entityConfig.fields as readonly string[];
    return fields.includes(fieldName);
  }

  /**
   * Check if translations already exist in the database
   * @param translations Array of translation data to check
   * @returns Array of duplicate translation identifiers (in format: "entityType#entityId:fieldName:locale")
   */
  private async checkForDuplicates(
    translations: Array<{
      entityType: TranslatableEntity;
      entityId: string | number | bigint;
      fieldName: string;
      locale: SupportedLocale;
    }>,
  ): Promise<string[]> {
    if (translations.length === 0) return [];

    const duplicateIds: string[] = [];

    // Check each translation for existence
    for (const translation of translations) {
      const existing = await this.prisma.translation.findUnique({
        where: {
          entity_type_entity_id_field_name_locale: {
            entity_type: translation.entityType,
            entity_id: String(translation.entityId),
            field_name: translation.fieldName,
            locale: translation.locale,
          },
        },
        select: { id: true },
      });

      if (existing) {
        duplicateIds.push(
          `${translation.entityType}#${translation.entityId}:${translation.fieldName}:${translation.locale}`,
        );
      }
    }

    return duplicateIds;
  }

  /**
   * Handle Prisma unique constraint errors with user-friendly messages
   * @param error The error from Prisma
   * @param translation The translation that caused the error
   * @throws ConflictException with user-friendly message
   */
  private handlePrismaError(
    error: any,
    translation: {
      entityType: TranslatableEntity;
      entityId: string | number | bigint;
      fieldName: string;
      locale: SupportedLocale;
    },
  ): never {
    if (
      error instanceof Prisma.PrismaClientKnownRequestError &&
      error.code === 'P2002'
    ) {
      throw new ConflictException(
        `Translation already exists for ${translation.entityType}#${translation.entityId}, field: ${translation.fieldName}, locale: ${translation.locale}`,
      );
    }
    throw error;
  }

  /**
   * Create a translation identifier for logging
   * @param translation Translation data
   * @returns Identifier string
   */
  private getTranslationIdentifier(translation: {
    entityType: TranslatableEntity;
    entityId: string | number | bigint;
    fieldName: string;
    locale: SupportedLocale;
  }): string {
    return `${translation.entityType}#${translation.entityId}:${translation.fieldName}:${translation.locale}`;
  }

  /**
   * Bulk upsert translations (for seeding/admin)
   * @param translations Array of translation data
   * @param options Options for the bulk upsert operation
   * @returns Result with counts of created and skipped translations
   */
  async bulkUpsertTranslations(
    translations: Array<{
      entityType: TranslatableEntity;
      entityId: string | number | bigint;
      fieldName: string;
      locale: SupportedLocale;
      content: string;
    }>,
    options: BulkUpsertOptions = {},
  ): Promise<BulkUpsertResult> {
    const { skipDuplicates = true, logSkipped = true } = options;

    // Early return for empty array
    if (translations.length === 0) {
      return { created: 0, skipped: 0, duplicates: [] };
    }

    // Check for existing translations if not skipping duplicates
    let duplicateIds: string[] = [];
    if (!skipDuplicates) {
      duplicateIds = await this.checkForDuplicates(translations);
      if (duplicateIds.length > 0) {
        throw new ConflictException(
          `Duplicate translations detected: ${duplicateIds.join(', ')}`,
        );
      }
    }

    // Prepare data for insertion
    const data = translations.map((t) => ({
      entity_type: t.entityType,
      entity_id: String(t.entityId),
      field_name: t.fieldName,
      locale: t.locale,
      content: t.content,
    }));

    try {
      // Attempt to create translations
      const result = await this.prisma.translation.createMany({
        data,
        skipDuplicates: skipDuplicates,
      });

      // Get actual duplicate count by comparing
      const actualSkipped = skipDuplicates
        ? translations.length - result.count
        : 0;

      // Log skipped duplicates
      if (logSkipped && actualSkipped > 0) {
        this.logger.warn(
          `Skipped ${actualSkipped} duplicate translations during bulk upsert`,
        );
      }

      return {
        created: result.count,
        skipped: actualSkipped,
        duplicates: duplicateIds,
      };
    } catch (error) {
      // Handle Prisma unique constraint errors
      if (translations.length === 1) {
        this.handlePrismaError(error, translations[0]);
      }
      throw error;
    }
  }
}
