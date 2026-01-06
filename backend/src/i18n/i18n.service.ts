import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
  TranslatableEntity,
  TRANSLATABLE_ENTITIES,
  matchSupportedLocale,
} from './i18n.constants';

@Injectable()
export class I18nService {
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
   * Bulk upsert translations (for seeding/admin)
   * @param translations Array of translation data
   */
  async bulkUpsertTranslations(
    translations: Array<{
      entityType: TranslatableEntity;
      entityId: string | number | bigint;
      fieldName: string;
      locale: SupportedLocale;
      content: string;
    }>,
  ): Promise<void> {
    // Use createMany with skipDuplicates for efficiency
    await this.prisma.translation.createMany({
      data: translations.map((t) => ({
        entity_type: t.entityType,
        entity_id: String(t.entityId),
        field_name: t.fieldName,
        locale: t.locale,
        content: t.content,
      })),
      skipDuplicates: true,
    });
  }
}
