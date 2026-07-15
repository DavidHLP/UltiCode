/**
 * Shared i18n translation-completeness verification contracts.
 *
 * Both frontends (console + management) supply their locale trees and source
 * root through {@link I18nCheckOptions}; the engine owns locale consistency,
 * code-to-locale coverage, and dynamic-prefix detection.
 */

export type TranslationObject = Record<string, unknown>

export interface LocaleConsistency {
  zhKeysCount: number
  enKeysCount: number
  missingInEn: string[]
  missingInZh: string[]
  isComplete: boolean
}

export interface MissingKey {
  key: string
  file: string
}

export interface DynamicPrefix {
  prefix: string
  file: string
}

export interface CodeCoverage {
  totalStaticKeys: number
  missingKeys: MissingKey[]
  dynamicPrefixes: DynamicPrefix[]
}

export interface CheckReport {
  localeConsistency: LocaleConsistency
  codeCoverage: CodeCoverage
  summary: {
    passed: boolean
    totalIssues: number
  }
}

export interface I18nCheckOptions {
  /** Primary locale used as the code-coverage reference (zh-CN). */
  zhLocale: TranslationObject
  /** Secondary locale compared for parity (en-US). */
  enLocale: TranslationObject
  /** Absolute or cwd-relative root to scan for `t()` usage. */
  srcDir: string
  /** Directory names to skip while walking {@link I18nCheckOptions.srcDir}. */
  excludeDirs?: string[]
  /**
   * File names to skip while walking. Used to exclude this checker's own
   * adapter (e.g. `check.ts`) whose doc-comment examples are illustrative.
   */
  excludeFiles?: string[]
}
