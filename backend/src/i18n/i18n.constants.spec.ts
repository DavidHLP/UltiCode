import { describe, it, expect } from '@jest/globals';
import {
  matchSupportedLocale,
  SUPPORTED_LOCALES,
  parseAcceptLanguageHeader,
  parseAcceptLanguageHeaderWithMatch,
  DEFAULT_LOCALE,
} from './i18n.constants';

describe('matchSupportedLocale', () => {
  describe('exact matches', () => {
    it('should match zh-CN exactly', () => {
      expect(matchSupportedLocale('zh-CN')).toBe('zh-CN');
    });

    it('should match en-US exactly', () => {
      expect(matchSupportedLocale('en-US')).toBe('en-US');
    });

    it('should be case-permissive via variant mapping and base code fallback', () => {
      // 'zh-cn' is not an exact match, but 'zh' in LOCALE_VARIANT_MAP maps to 'zh-CN'
      expect(matchSupportedLocale('zh-cn')).toBe('zh-CN');
      // 'EN-US' is not an exact match, but 'en' in LOCALE_VARIANT_MAP maps to 'en-US'
      expect(matchSupportedLocale('EN-US')).toBe('en-US');
    });
  });

  describe('variant mappings - Chinese Simplified', () => {
    it('should map zh to zh-CN', () => {
      expect(matchSupportedLocale('zh')).toBe('zh-CN');
    });

    it('should map zh-Hans to zh-CN', () => {
      expect(matchSupportedLocale('zh-Hans')).toBe('zh-CN');
    });

    it('should map zh-Hans-CN to zh-CN', () => {
      expect(matchSupportedLocale('zh-Hans-CN')).toBe('zh-CN');
    });

    it('should map zh-Hans-SG to zh-CN', () => {
      expect(matchSupportedLocale('zh-Hans-SG')).toBe('zh-CN');
    });

    it('should map zh-SG to zh-CN', () => {
      expect(matchSupportedLocale('zh-SG')).toBe('zh-CN');
    });
  });

  describe('variant mappings - English', () => {
    it('should map en to en-US', () => {
      expect(matchSupportedLocale('en')).toBe('en-US');
    });

    it('should map en-GB to en-US', () => {
      expect(matchSupportedLocale('en-GB')).toBe('en-US');
    });

    it('should map en-AU to en-US', () => {
      expect(matchSupportedLocale('en-AU')).toBe('en-US');
    });

    it('should map en-CA to en-US', () => {
      expect(matchSupportedLocale('en-CA')).toBe('en-US');
    });

    it('should map en-IN to en-US', () => {
      expect(matchSupportedLocale('en-IN')).toBe('en-US');
    });

    it('should map en-NZ to en-US', () => {
      expect(matchSupportedLocale('en-NZ')).toBe('en-US');
    });

    it('should map en-ZA to en-US', () => {
      expect(matchSupportedLocale('en-ZA')).toBe('en-US');
    });

    it('should map en-IE to en-US', () => {
      expect(matchSupportedLocale('en-IE')).toBe('en-US');
    });
  });

  describe('language code fallback', () => {
    it('should map zh-TW to zh-CN via base code fallback', () => {
      expect(matchSupportedLocale('zh-TW')).toBe('zh-CN');
    });

    it('should map zh-HK to zh-CN via base code fallback', () => {
      expect(matchSupportedLocale('zh-HK')).toBe('zh-CN');
    });

    it('should map zh-Hant to zh-CN via base code fallback', () => {
      expect(matchSupportedLocale('zh-Hant')).toBe('zh-CN');
    });

    it('should handle case-insensitive base code matching', () => {
      expect(matchSupportedLocale('ZH-CN')).toBe('zh-CN');
      expect(matchSupportedLocale('EN-gb')).toBe('en-US');
    });
  });

  describe('unsupported locales', () => {
    it('should return null for ja-JP', () => {
      expect(matchSupportedLocale('ja-JP')).toBeNull();
    });

    it('should return null for fr-FR', () => {
      expect(matchSupportedLocale('fr-FR')).toBeNull();
    });

    it('should return null for de-DE', () => {
      expect(matchSupportedLocale('de-DE')).toBeNull();
    });

    it('should return null for ko-KR', () => {
      expect(matchSupportedLocale('ko-KR')).toBeNull();
    });

    it('should return null for es-ES', () => {
      expect(matchSupportedLocale('es-ES')).toBeNull();
    });
  });

  describe('edge cases', () => {
    it('should handle empty string', () => {
      expect(matchSupportedLocale('')).toBeNull();
    });

    it('should handle whitespace only', () => {
      expect(matchSupportedLocale('   ')).toBeNull();
    });

    it('should handle undefined', () => {
      expect(matchSupportedLocale(undefined)).toBeNull();
    });

    it('should trim whitespace around valid locale', () => {
      expect(matchSupportedLocale('  zh-CN  ')).toBe('zh-CN');
      expect(matchSupportedLocale('  en-GB  ')).toBe('en-US');
    });

    it('should handle locale with extra hyphens', () => {
      // Should extract base code and match
      expect(matchSupportedLocale('zh-CN-extra')).toBe('zh-CN');
    });
  });

  describe('SUPPORTED_LOCALES', () => {
    it('should have exactly two supported locales', () => {
      expect(SUPPORTED_LOCALES).toHaveLength(2);
    });

    it('should include zh-CN', () => {
      expect(SUPPORTED_LOCALES).toContain('zh-CN');
    });

    it('should include en-US', () => {
      expect(SUPPORTED_LOCALES).toContain('en-US');
    });
  });
});

describe('parseAcceptLanguageHeader', () => {
  describe('basic parsing', () => {
    it('should parse single language without quality', () => {
      expect(parseAcceptLanguageHeader('zh-CN')).toEqual([
        { code: 'zh-CN', quality: 1.0 },
      ]);
    });

    it('should parse multiple languages with quality values', () => {
      expect(parseAcceptLanguageHeader('zh-CN,zh;q=0.9,en;q=0.8')).toEqual([
        { code: 'zh-CN', quality: 1.0 },
        { code: 'zh', quality: 0.9 },
        { code: 'en', quality: 0.8 },
      ]);
    });
  });

  describe('sorting', () => {
    it('should sort by quality descending', () => {
      expect(parseAcceptLanguageHeader('en;q=0.5,zh-CN;q=0.9')).toEqual([
        { code: 'zh-CN', quality: 0.9 },
        { code: 'en', quality: 0.5 },
      ]);
    });

    it('should maintain order when qualities are equal', () => {
      expect(parseAcceptLanguageHeader('zh-CN,en;q=1.0')).toEqual([
        { code: 'zh-CN', quality: 1.0 },
        { code: 'en', quality: 1.0 },
      ]);
    });
  });

  describe('edge cases', () => {
    it('should return empty array for empty string', () => {
      expect(parseAcceptLanguageHeader('')).toEqual([]);
    });

    it('should trim whitespace', () => {
      expect(parseAcceptLanguageHeader(' zh-CN , en;q=0.8 ')).toEqual([
        { code: 'zh-CN', quality: 1.0 },
        { code: 'en', quality: 0.8 },
      ]);
    });

    it('should handle quality values with varying precision', () => {
      expect(parseAcceptLanguageHeader('zh;q=0.9,en;q=0.85')).toEqual([
        { code: 'zh', quality: 0.9 },
        { code: 'en', quality: 0.85 },
      ]);
    });
  });
});

describe('parseAcceptLanguageHeaderWithMatch', () => {
  describe('matching', () => {
    it('should return zh-CN for exact match', () => {
      expect(parseAcceptLanguageHeaderWithMatch('zh-CN')).toBe('zh-CN');
    });

    it('should return en-US for en-GB variant', () => {
      expect(parseAcceptLanguageHeaderWithMatch('en-GB')).toBe('en-US');
    });

    it('should respect quality values', () => {
      expect(parseAcceptLanguageHeaderWithMatch('en;q=0.5,zh-CN;q=0.9')).toBe(
        'zh-CN',
      );
    });

    it('should match first supported locale in preference list', () => {
      expect(parseAcceptLanguageHeaderWithMatch('fr-FR,zh-CN,en;q=0.8')).toBe(
        'zh-CN',
      );
    });
  });

  describe('fallback', () => {
    it('should return DEFAULT_LOCALE for unsupported languages', () => {
      expect(parseAcceptLanguageHeaderWithMatch('fr-FR,fr;q=0.9')).toBe(
        DEFAULT_LOCALE,
      );
    });

    it('should return DEFAULT_LOCALE for undefined header', () => {
      expect(parseAcceptLanguageHeaderWithMatch(undefined)).toBe(
        DEFAULT_LOCALE,
      );
    });

    it('should return DEFAULT_LOCALE for empty string', () => {
      expect(parseAcceptLanguageHeaderWithMatch('')).toBe(DEFAULT_LOCALE);
    });

    it('should try all preferences before falling back', () => {
      expect(parseAcceptLanguageHeaderWithMatch('ja-JP,ko-KR,de-DE')).toBe(
        DEFAULT_LOCALE,
      );
    });
  });
});
