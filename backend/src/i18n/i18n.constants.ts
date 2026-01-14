/**
 * I18n Constants and Configuration
 * Defines supported locales and translatable entity configurations
 */

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN';
export const FALLBACK_LOCALE: SupportedLocale = 'zh-CN';
export const LOCALE_HEADER_KEY = 'x-locale';

/**
 * Maps locale variants to their supported locale equivalents.
 *
 * This mapping provides explicit handling for common locale variants.
 * When adding new supported locales (e.g., zh-TW for Traditional Chinese),
 * update this mapping with the appropriate variant codes.
 *
 * @example
 * // When zh-TW support is added:
 * 'zh-TW': 'zh-TW',
 * 'zh-HK': 'zh-TW',
 * 'zh-Hant': 'zh-TW',
 * 'zh-Hant-TW': 'zh-TW',
 */
const LOCALE_VARIANT_MAP: Record<string, SupportedLocale> = {
  // Chinese Simplified variants
  zh: 'zh-CN',
  'zh-Hans': 'zh-CN',
  'zh-Hans-CN': 'zh-CN',
  'zh-Hans-SG': 'zh-CN',
  'zh-SG': 'zh-CN',

  // English variants (all map to en-US)
  en: 'en-US',
  'en-GB': 'en-US',
  'en-AU': 'en-US',
  'en-CA': 'en-US',
  'en-IN': 'en-US',
  'en-NZ': 'en-US',
  'en-ZA': 'en-US',
  'en-IE': 'en-US',
};

/**
 * Matches a locale string to a supported locale using a three-tier approach:
 *
 * 1. **Exact match**: Checks if the locale exactly matches a supported locale
 * 2. **Variant mapping**: Looks up explicit variant mappings (e.g., en-GB → en-US)
 * 3. **Language code fallback**: Extracts base language code for partial match
 *
 * @param locale - The locale string to match (e.g., 'zh-CN', 'en-GB', 'zh-TW')
 * @returns The matched supported locale, or null if no match is found
 *
 * @example
 * matchSupportedLocale('zh-CN')     // → 'zh-CN' (exact match)
 * matchSupportedLocale('en-GB')     // → 'en-US' (variant mapping)
 * matchSupportedLocale('zh-TW')     // → 'zh-CN' (language code fallback)
 * matchSupportedLocale('ja-JP')     // → null (unsupported)
 */
export function matchSupportedLocale(locale?: string): SupportedLocale | null {
  if (!locale) return null;
  const trimmed = locale.trim();
  if (!trimmed) return null;

  // 1. Exact match
  if (SUPPORTED_LOCALES.includes(trimmed as SupportedLocale)) {
    return trimmed as SupportedLocale;
  }

  // 2. Check explicit variant mapping
  if (trimmed in LOCALE_VARIANT_MAP) {
    return LOCALE_VARIANT_MAP[trimmed];
  }

  // 3. Language code fallback (e.g., 'zh' from 'zh-TW')
  const baseCode = trimmed.split('-')[0].toLowerCase();
  const matched = SUPPORTED_LOCALES.find((supported) =>
    supported.toLowerCase().startsWith(baseCode),
  );

  return matched ?? null;
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

/**
 * Represents a language preference from Accept-Language header
 */
export interface LanguagePreference {
  code: string;
  quality: number;
}

/**
 * Parse Accept-Language header into sorted language preference array
 *
 * @param header - Accept-Language header value (e.g., 'zh-CN,zh;q=0.9,en;q=0.8')
 * @returns Sorted array of language preferences (highest quality first)
 *
 * @example
 * parseAcceptLanguageHeader('zh-CN,zh;q=0.9,en;q=0.8')
 * // → [{code: 'zh-CN', quality: 1.0}, {code: 'zh', quality: 0.9}, {code: 'en', quality: 0.8}]
 *
 * @example
 * parseAcceptLanguageHeader('en;q=0.5,zh-CN;q=0.9')
 * // → [{code: 'zh-CN', quality: 0.9}, {code: 'en', quality: 0.5}]
 */
export function parseAcceptLanguageHeader(
  header: string,
): LanguagePreference[] {
  if (!header) return [];

  const languages = header.split(',').map((lang) => {
    const [code, qValue] = lang.trim().split(';q=');
    return {
      code: code.trim(),
      quality: qValue ? parseFloat(qValue) : 1.0,
    };
  });

  // Sort by quality (highest first)
  languages.sort((a, b) => b.quality - a.quality);

  return languages;
}

/**
 * Parse Accept-Language header and return best matching supported locale
 *
 * @param header - Accept-Language header value
 * @returns Best matching supported locale, or DEFAULT_LOCALE if no match
 *
 * @example
 * parseAcceptLanguageHeaderWithMatch('zh-CN,zh;q=0.9,en;q=0.8')
 * // → 'zh-CN'
 *
 * @example
 * parseAcceptLanguageHeaderWithMatch('en-GB,en;q=0.9')
 * // → 'en-US' (via variant mapping)
 *
 * @example
 * parseAcceptLanguageHeaderWithMatch('fr-FR,fr;q=0.9')
 * // → 'zh-CN' (DEFAULT_LOCALE, no match)
 */
export function parseAcceptLanguageHeaderWithMatch(
  header: string | undefined,
): SupportedLocale {
  if (!header) return DEFAULT_LOCALE;

  const languages = parseAcceptLanguageHeader(header);

  for (const { code } of languages) {
    const matched = matchSupportedLocale(code);
    if (matched) return matched;
  }

  return DEFAULT_LOCALE;
}

export type TranslatableFields<E extends TranslatableEntity> =
  (typeof TRANSLATABLE_ENTITIES)[E]['fields'][number];
