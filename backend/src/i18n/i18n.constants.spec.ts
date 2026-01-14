import { describe, it, expect } from '@jest/globals';
import { matchSupportedLocale, SUPPORTED_LOCALES } from './i18n.constants';

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
