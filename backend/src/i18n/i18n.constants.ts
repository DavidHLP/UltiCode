/**
 * I18n Constants and Configuration
 * Defines supported locales and translatable entity configurations
 */

export const SUPPORTED_LOCALES = ['en-US', 'zh-CN'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: SupportedLocale = 'en-US';
export const FALLBACK_LOCALE: SupportedLocale = 'en-US';
export const LOCALE_HEADER_KEY = 'x-locale';

export function matchSupportedLocale(locale?: string): SupportedLocale | null {
  if (!locale) return null;
  const trimmed = locale.trim();
  if (!trimmed) return null;

  if (SUPPORTED_LOCALES.includes(trimmed as SupportedLocale)) {
    return trimmed as SupportedLocale;
  }

  const partial = SUPPORTED_LOCALES.find((supported) =>
    supported.toLowerCase().startsWith(trimmed.toLowerCase().split('-')[0]),
  );

  return partial ?? null;
}

/**
 * Entity types that support translations
 * Each entity defines which fields can be translated and the ID type
 */
export const TRANSLATABLE_ENTITIES = {
  PROBLEM: {
    fields: ['title'] as const,
    idType: 'bigint' as const,
  },
  PROBLEM_DETAIL: {
    fields: ['summary', 'follow_up', 'constraints_json', 'hints'] as const,
    idType: 'string' as const,
  },
  PROBLEM_TAG: {
    fields: ['label'] as const,
    idType: 'string' as const,
  },
  PROBLEM_EXAMPLE: {
    fields: ['explanation'] as const,
    idType: 'string' as const,
  },
  CONTEST: {
    fields: ['title', 'description', 'rules'] as const,
    idType: 'string' as const,
  },
  SUBMISSION_STATUS: {
    fields: ['label', 'description', 'suggestion'] as const,
    idType: 'string' as const,
  },
} as const;

export type TranslatableEntity = keyof typeof TRANSLATABLE_ENTITIES;

export type TranslatableFields<E extends TranslatableEntity> =
  (typeof TRANSLATABLE_ENTITIES)[E]['fields'][number];
